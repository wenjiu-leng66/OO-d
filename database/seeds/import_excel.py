"""
把课程的三份 Excel (部门信息表、应用软件功能权限清单、员工信息表) 清洗后生成 RBAC0 初始数据 SQL,
配合迁移 database/migrations/V001__create_core_tables.sql 使用。

- 只用 Python 标准库 (直接解析 xlsx 里的 XML), 不用装 openpyxl。员工表也可以是 UTF-8 的 CSV。
- 输出含姓名和密码哈希, 默认写到被 Git 忽略的 data/raw/, 不要提交。
- 清洗规则、组织映射和待老师确认的事项见 docs/database/import-rules.md。

用法 (在仓库根目录执行):
    python database/seeds/import_excel.py --dept data/raw/市政公司部门信息表.xlsx \\
        --perm data/raw/市政公司应用软件功能权限清单.xlsx [--emp data/raw/市政公司员工信息表.xlsx]

没有员工表时, 用部门表里的"部门经理"生成用户, 另加 admin 和一个演示普通员工 staff。
"""
import argparse
import csv
import hashlib
import os
import re
import secrets
import sys
import zipfile
import xml.etree.ElementTree as ET
from decimal import Decimal, InvalidOperation

NS = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
DEFAULT_PASSWORD = "123456"  # 演示用, 上线前必须修改
DEFAULT_OUT = os.path.join("data", "raw", "seed_data.sql")

# 与 Spring Security 的 Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8() 一致:
# 16 字节随机盐, 310000 次 HMAC-SHA256, 32 字节输出, 存 hex(盐 + 哈希), 前缀标明算法。
# 后端用 PasswordEncoderFactories.createDelegatingPasswordEncoder() 即可校验。
PBKDF2_ID = "pbkdf2@SpringSecurity_v5_8"
PBKDF2_SALT_BYTES = 16
PBKDF2_ITERATIONS = 310000
PBKDF2_HASH_BYTES = 32

ACTIONS = [  # (id, code, 中文)
    (1, "view", "查看"),
    (2, "add", "新增"),
    (3, "edit", "编辑"),
    (4, "approve", "审批"),
    (5, "export", "导出"),
    (6, "delete", "删除"),
]
ACTION_BY_CN = {cn: aid for aid, _, cn in ACTIONS}
ACTION_BY_CN["view"] = 1  # Q-07: 清单 H117、G310 把"查看"写成了英文 view

ROLE_ADMIN, ROLE_LEADER, ROLE_DEPT_MANAGER, ROLE_PROJECT_MANAGER, ROLE_STAFF = 1, 2, 3, 4, 5
ROLES = [  # (id, code, 名称, 清单中的列名)
    (ROLE_ADMIN, "ADMIN", "系统管理员", "系统管理员"),
    (ROLE_LEADER, "LEADER", "公司领导", "公司领导"),
    (ROLE_DEPT_MANAGER, "DEPT_MANAGER", "部门经理", "部门经理"),
    (ROLE_PROJECT_MANAGER, "PROJECT_MANAGER", "项目经理", "项目经理"),
    (ROLE_STAFF, "STAFF", "普通员工", "普通员工"),
]

SEED_TABLES = ("sys_role_permission", "sys_user_role", "sys_permission", "sys_action", "app_function",
               "app_module", "app_system", "sys_role", "sys_user", "sys_dept")


class DataError(Exception):
    """源表有无法自动处理的问题: 停止导入, 修正源表后重新执行。"""


warnings = []


def warn(msg):
    warnings.append(msg)


# ------------------------------------------------------------------ 读表
def _col_index(ref):
    n = 0
    for ch in re.match(r"[A-Z]+", ref).group():
        n = n * 26 + ord(ch) - 64
    return n - 1


def _number_text(text):
    """数值单元格转文本: 12345678901.0 / 1.2345678901E10 -> 12345678901。工号、电话都按文本处理。"""
    try:
        d = Decimal(text)
    except InvalidOperation:
        return text
    return format(d.to_integral_value(), "f") if d == d.to_integral_value() else text


def read_xlsx(path, sheet_index=0):
    """返回 [(Excel 行号, [单元格文本...]), ...], 跳过全空行; 合并单元格不展开 (只有左上角有值)。"""
    z = zipfile.ZipFile(path)
    shared = []
    if "xl/sharedStrings.xml" in z.namelist():
        root = ET.fromstring(z.read("xl/sharedStrings.xml"))
        for si in root.findall("m:si", NS):
            shared.append("".join(t.text or "" for t in si.iter("{%s}t" % NS["m"])))
    # 按 workbook.xml 中的顺序找到对应 sheet 文件
    wb = ET.fromstring(z.read("xl/workbook.xml"))
    rels = ET.fromstring(z.read("xl/_rels/workbook.xml.rels"))
    rel_target = {r.get("Id"): r.get("Target") for r in rels}
    sheet = wb.find("m:sheets", NS)[sheet_index]
    rid = sheet.get("{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id")
    target = rel_target[rid].lstrip("/")
    if not target.startswith("xl/"):
        target = "xl/" + target
    ws = ET.fromstring(z.read(target))

    rows = []
    for row in ws.find("m:sheetData", NS):
        vals = {}
        for c in row:
            t = c.get("t")
            v = c.find("m:v", NS)
            if t == "inlineStr":
                text = "".join(x.text or "" for x in c.iter("{%s}t" % NS["m"]))
            elif v is None:
                continue
            elif t == "s":
                text = shared[int(v.text)]
            elif t in (None, "n"):
                text = _number_text(v.text or "")
            else:
                text = v.text or ""
            vals[_col_index(c.get("r"))] = text.strip()
        if any(vals.values()):
            rows.append((int(row.get("r")), [vals.get(i, "") for i in range(max(vals) + 1)]))
    return rows


def read_table(path):
    """xlsx 或 UTF-8 CSV, 返回格式同 read_xlsx。"""
    path = str(path)
    if not path.lower().endswith(".csv"):
        return read_xlsx(path)
    with open(path, encoding="utf-8-sig", newline="") as f:
        return [(no, [c.strip() for c in r]) for no, r in enumerate(csv.reader(f), 1) if any(c.strip() for c in r)]


def find_header(rows, names, what):
    for i, (_, cells) in enumerate(rows[:10]):
        if set(cells) & set(names):
            return i
    raise DataError(f"{what}: 前 10 行找不到包含 {'/'.join(names)} 的表头")


def pad(cells, n):
    return cells + [""] * (n - len(cells))


# ------------------------------------------------------------------ 部门
def build_depts(records):
    """records: [{"row", "name", "manager", "parent"}]。补 id / parent_id / level / type / short_name。"""
    by_name = {}
    for idx, d in enumerate(records, 1):
        if d["name"] in by_name:
            raise DataError(f"部门表第{d['row']}行: 部门名称 {d['name']} 与第{by_name[d['name']]['row']}行重复")
        d["id"] = idx
        by_name[d["name"]] = d
    for d in records:
        if d["parent"] and d["parent"] not in by_name:
            raise DataError(f"部门表第{d['row']}行: 上级部门 {d['parent']} 不存在")
        d["parent_id"] = by_name[d["parent"]]["id"] if d["parent"] else None
        d["short_name"] = d["name"].split("-", 1)[-1]
    for d in records:  # 计算层级与类型, 顺带检查上级关系有没有环
        level, p, seen = 1, d, {d["name"]}
        while p["parent"]:
            p = by_name[p["parent"]]
            if p["name"] in seen:
                raise DataError(f"部门表第{d['row']}行: 上级部门形成循环 ({d['name']})")
            seen.add(p["name"])
            level += 1
        d["level"] = level
        if level == 1:
            d["type"] = "HQ"
        elif level == 2:
            d["type"] = "BRANCH" if d["name"].endswith("分公司") else "HQ_DEPT"
        else:
            d["type"] = "PROJECT" if "项目经理部" in d["name"] else "BRANCH_DEPT"
    roots = [d["name"] for d in records if d["level"] == 1]
    if len(roots) != 1:
        raise DataError(f"部门表应只有一个根节点 (上级部门为空), 实际有 {len(roots)} 个: {roots}")
    return records, by_name


def load_depts(path):
    rows = read_table(path)
    h = find_header(rows, ["部门名称"], "部门表")
    head = rows[h][1]
    try:
        i_name, i_mgr, i_parent = (head.index(x) for x in ("部门名称", "部门经理", "上级部门"))
    except ValueError:
        raise DataError(f"部门表缺少 部门名称/部门经理/上级部门 列, 表头为 {head}")
    records = []
    for no, r in rows[h + 1:]:
        r = pad(r, len(head))
        if r[i_name]:
            records.append({"row": no, "name": r[i_name], "manager": r[i_mgr], "parent": r[i_parent]})
    return build_depts(records)


# ------------------------------------------------------------------ 功能权限清单
def parse_cell(cell, where):
    """'查看/编辑本人' -> ({1, 3}, 'SELF');  '全部' -> (全部操作, 'ALL');  '无' -> (set(), 'ALL')"""
    cell = cell.strip()
    if cell == "":
        warn(f"{where}: 单元格为空, 按'无'处理, 即不授权 (Q-08, 确认前拒绝)")
        return set(), "ALL"
    if cell == "无":  # Q-10: 只是不授权, 不是显式拒绝
        return set(), "ALL"
    if cell == "全部":  # Q-10: 6 种操作全部授予
        return {a[0] for a in ACTIONS}, "ALL"
    scope = "SELF" if "本人" in cell else "ALL"  # Q-09: "本人"作用于整格, 查看/编辑本人 = 两种操作都只限本人数据
    ids = set()
    for part in re.split(r"[/、,，]", cell):
        part = part.replace("本人", "").strip()
        if part == "view":
            warn(f"{where}: '{cell}' 中的英文 view 已按'查看'处理 (Q-07)")
        if part not in ACTION_BY_CN:
            raise DataError(f"{where}: 无法识别的权限 '{cell}'")
        ids.add(ACTION_BY_CN[part])
    return ids, scope


def load_permissions(path):
    rows = read_table(path)
    h = find_header(rows, ["功能点"], "权限清单")
    head = rows[h][1]
    try:
        i_sys, i_mod, i_fn, i_desc = (head.index(x) for x in ("软件系统", "功能模块", "功能点", "功能说明"))
    except ValueError:
        raise DataError(f"权限清单缺少 软件系统/功能模块/功能点/功能说明 列, 表头为 {head}")
    role_cols = {}
    for rid, _, _, col_name in ROLES:
        if col_name not in head:
            raise DataError(f"权限清单缺少角色列: {col_name}")
        role_cols[rid] = head.index(col_name)

    systems, modules, functions, grants = [], [], [], []
    cur_sys = cur_mod = None
    sys_ids, mod_ids, fn_keys = {}, {}, {}
    for no, r in rows[h + 1:]:
        r = pad(r, len(head))
        if r[i_sys]:  # 合并单元格只有首行有值 -> 向下填充
            cur_sys = r[i_sys]
        if r[i_mod]:
            cur_mod = r[i_mod]
        if not r[i_fn]:
            continue
        if cur_sys is None or cur_mod is None:
            raise DataError(f"权限清单第{no}行: 功能点 {r[i_fn]} 之前没有出现软件系统或功能模块")
        if cur_sys not in sys_ids:
            sys_ids[cur_sys] = len(systems) + 1
            systems.append((sys_ids[cur_sys], cur_sys))
        mkey = (cur_sys, cur_mod)
        if mkey not in mod_ids:
            mod_ids[mkey] = len(modules) + 1
            modules.append((mod_ids[mkey], sys_ids[cur_sys], cur_mod))
        fkey = (mod_ids[mkey], r[i_fn])
        if fkey in fn_keys:
            raise DataError(f"权限清单第{no}行: 功能点 {r[i_fn]} 与第{fn_keys[fkey]}行在同一模块中重复")
        fn_keys[fkey] = no
        fid = len(functions) + 1
        functions.append((fid, mod_ids[mkey], r[i_fn], r[i_desc] or None))
        for rid, col in role_cols.items():
            action_ids, scope = parse_cell(r[col], f"第{no}行[{r[i_fn]}]/{head[col]}")
            for aid in sorted(action_ids):
                grants.append((rid, fid, aid, scope))
    return systems, modules, functions, grants


# ------------------------------------------------------------------ 员工 & 组织映射
EMP_ALIASES = {  # 员工表的真实列名尚未确认, 按常见写法识别; 英文列名对应 data/samples/users.sample.csv
    "username": ["工号", "员工编号", "员工工号", "编号", "账号", "用户名", "username"],
    "real_name": ["姓名", "员工姓名", "name"],
    "company": ["公司", "分公司", "所属公司", "单位", "所属单位", "company"],
    "dept": ["部门", "所属部门", "部门名称", "department"],
    "position": ["岗位", "职位", "职务", "position"],
    "gender": ["性别", "gender"],
    "phone": ["电话", "手机", "手机号", "手机号码", "联系电话", "phone"],
    "email": ["邮箱", "电子邮箱", "Email", "email"],
}
RESERVED_USERNAMES = {"admin", "staff"}


def dept_key(company, dept, root_name):
    """员工表的 (公司, 部门) -> 部门表里的部门全称。"""
    if not company or company == root_name:  # 没有公司列时部门列应填全称; 总公司员工直接用部门名称
        return dept
    if company == dept:  # 公司 = 部门: 分公司本部人员, 挂在分公司节点上
        return company
    return f"{company}-{dept}"  # 其余: "分公司-部门"


def build_employees(rows, dept_by_name, root_name):
    """rows: read_table 的结果 (含表头)。返回 (导入成功的员工, 失败行 [(行号, 原因)])。"""
    h = find_header(rows, EMP_ALIASES["real_name"], "员工表")
    head = rows[h][1]
    idx = {k: next((head.index(n) for n in names if n in head), None) for k, names in EMP_ALIASES.items()}
    if idx["dept"] is None:
        raise DataError(f"员工表找不到部门列, 表头为 {head}")
    if idx["username"] is None:
        warn("员工表没有工号列, 按顺序生成用户名 E0001、E0002…")

    emps, failed, seen = [], [], {}
    for no, r in rows[h + 1:]:
        r = pad(r, len(head))

        def get(key):
            return r[idx[key]] if idx[key] is not None else ""

        username = get("username") or None
        if not get("real_name"):
            failed.append((no, "姓名为空"))
            continue
        if idx["username"] is not None and not username:
            failed.append((no, "工号为空"))
            continue
        if username in RESERVED_USERNAMES:
            failed.append((no, f"工号 {username} 是系统保留账号"))
            continue
        if username in seen:
            failed.append((no, f"工号 {username} 与第{seen[username]}行重复"))
            continue
        key = dept_key(get("company"), get("dept"), root_name)
        dept = dept_by_name.get(key)
        if dept is None:
            failed.append((no, f"部门 '{key}' 不在部门表中"))
            continue
        gender_text = get("gender")
        gender = {"男": "M", "女": "F", "M": "M", "F": "F"}.get(gender_text)
        if gender_text and gender is None:
            warn(f"员工表第{no}行: 性别 '{gender_text}' 无法识别, 置空")
        if username:
            seen[username] = no
        emps.append({
            "row": no,
            "username": username,
            "real_name": get("real_name"),
            "dept_id": dept["id"],
            "position": get("position") or None,
            "gender": gender,
            "phone": get("phone") or None,
            "email": get("email") or None,
        })
    return emps, failed


def load_employees(path, dept_by_name, root_name):
    return build_employees(read_table(path), dept_by_name, root_name)


# ------------------------------------------------------------------ 用户、负责人与角色
def manager_role(dept):  # Q-11 成员1 建议: 按管理的部门类型给初始角色, 不看岗位文字
    if dept["type"] == "HQ" or dept["name"] == "公司领导":
        return ROLE_LEADER
    if dept["type"] == "PROJECT":
        return ROLE_PROJECT_MANAGER
    return ROLE_DEPT_MANAGER  # 含分公司经理


def manager_title(dept):
    if dept["type"] == "HQ" or dept["name"] == "公司领导":
        return "总经理"
    if dept["type"] == "BRANCH":
        return "分公司经理"
    return "项目经理" if dept["type"] == "PROJECT" else "部门经理"


def link_managers(depts, users, dept_by_id):
    """部门负责人按用户 ID 关联, 不能只看姓名。返回关联不上的 [(部门, 原因)]。

    - 没有员工表时, 用户就是由部门经理生成的, 直接关联到他管理的部门。
    - 有员工表时, 候选人先取"同名且在该部门"的用户; 没有再取"同名且在其直接下级部门"的用户
      (总公司的负责人在"公司领导"部门, Q-12)。恰好一人才关联, 否则置空, 由管理员在系统里指定。
    """
    by_name = {}
    for u in users:
        by_name.setdefault(u["real_name"], []).append(u)
    for d in depts:
        d["manager_user_id"] = None
    for u in users:
        for d in u.get("manages", []):
            d["manager_user_id"] = u["id"]

    def parent_of(u):
        dept = dept_by_id.get(u["dept_id"])
        return dept["parent_id"] if dept else None

    unresolved = []
    for d in depts:
        if not d["manager"] or d["manager_user_id"]:
            continue
        same = [u for u in by_name.get(d["manager"], []) if u["username"] not in RESERVED_USERNAMES]
        cands = [u for u in same if u["dept_id"] == d["id"]] or [u for u in same if parent_of(u) == d["id"]]
        if len(cands) == 1:
            d["manager_user_id"] = cands[0]["id"]
        elif cands:
            unresolved.append((d, f"该部门有 {len(cands)} 个与负责人同名的用户, 无法确定"))
        elif same:
            unresolved.append((d, "有同名用户, 但不在该部门或其直接下级部门"))
        else:
            unresolved.append((d, "员工表中没有与负责人同名的用户"))
    return unresolved


def build_users(depts, emps, dept_by_id):
    """返回 (用户列表, 负责人关联不上的部门)。用户含 id/username/roles, 密码哈希另算。"""
    users = [{"username": "admin", "real_name": "系统管理员", "dept_id": None, "position": "系统管理员",
              "gender": None, "phone": None, "email": None, "roles": {ROLE_ADMIN}}]
    if emps is not None:
        users += emps
    else:
        # 没有员工表: 每位部门经理生成一个用户 (同名视为同一人, 部门取其管理的最后一个部门)
        managed = {}
        for d in depts:
            if d["manager"]:
                managed.setdefault(d["manager"], []).append(d)
        for name, ds in managed.items():
            users.append({"username": None, "real_name": name, "dept_id": ds[-1]["id"],
                          "position": "、".join(dict.fromkeys(manager_title(d) for d in ds)),
                          "gender": None, "phone": None, "email": None, "manages": ds})
        staff_dept = next(d for d in depts if d["type"] == "BRANCH_DEPT")
        users.append({"username": "staff", "real_name": "演示员工", "dept_id": staff_dept["id"], "position": "职员",
                      "gender": None, "phone": None, "email": None})

    seq, seen = 0, set()
    for i, u in enumerate(users, 1):
        u["id"] = i
        u.setdefault("roles", set())
        if not u["username"]:
            seq += 1
            u["username"] = f"E{seq:04d}"
        if u["username"] in seen:
            raise DataError(f"用户名重复: {u['username']}")
        seen.add(u["username"])

    unresolved = link_managers(depts, users, dept_by_id)
    for d in depts:
        if d["manager_user_id"]:
            users[d["manager_user_id"] - 1]["roles"].add(manager_role(d))
    for u in users:
        if not u["roles"]:
            u["roles"] = {ROLE_STAFF}
    return users, unresolved


def password_hash(raw, salt=None):
    salt = secrets.token_bytes(PBKDF2_SALT_BYTES) if salt is None else salt
    dk = hashlib.pbkdf2_hmac("sha256", raw.encode("utf-8"), salt, PBKDF2_ITERATIONS, PBKDF2_HASH_BYTES)
    return "{%s}%s" % (PBKDF2_ID, (salt + dk).hex())


# ------------------------------------------------------------------ SQL 输出
def q(v):
    if v is None:
        return "NULL"
    if isinstance(v, int):
        return str(v)
    return "'" + str(v).replace("\\", "\\\\").replace("'", "''") + "'"


def insert(out, table, cols, rows, batch=200):
    for i in range(0, len(rows), batch):
        out.append(f"INSERT INTO {table} ({', '.join(cols)}) VALUES")
        chunk = rows[i:i + batch]
        out.append(",\n".join("  (" + ", ".join(q(v) for v in r) + ")" for r in chunk) + ";")
    out.append("")


def render_sql(depts, users, systems, modules, functions, perms, grants, perm_id):
    out = [
        "-- 由 database/seeds/import_excel.py 生成, 请勿手改; 改源表后重新生成。",
        "-- 含员工姓名和密码哈希, 不得提交到 Git。执行前先应用迁移 V001__create_core_tables.sql。",
        f"-- 所有账号的初始密码均为 {DEFAULT_PASSWORD}, 上线前必须修改。",
        "-- 重复执行 = 清空下列 10 张表后按固定 ID 重建, 系统里做过的修改会丢失。",
        "SET NAMES utf8mb4;",
        "SET FOREIGN_KEY_CHECKS = 0;",
        *[f"TRUNCATE TABLE {t};" for t in SEED_TABLES],
        "SET FOREIGN_KEY_CHECKS = 1;",
        "",
        "-- 部门 (manager_user_id 在用户插入后回填)",
    ]
    insert(out, "sys_dept", ["id", "name", "short_name", "parent_id", "dept_type", "level", "manager_name", "sort_no"],
           [(d["id"], d["name"], d["short_name"], d["parent_id"], d["type"], d["level"], d["manager"] or None, d["id"])
            for d in depts])
    out.append("-- 用户")
    insert(out, "sys_user", ["id", "username", "password_hash", "real_name", "dept_id", "position",
                             "gender", "phone", "email"],
           [(u["id"], u["username"], u["password_hash"], u["real_name"], u["dept_id"], u["position"],
             u["gender"], u["phone"], u["email"]) for u in users])
    for d in depts:
        if d["manager_user_id"]:
            out.append(f"UPDATE sys_dept SET manager_user_id = {d['manager_user_id']} WHERE id = {d['id']};")
    out.append("")
    out.append("-- 角色")
    insert(out, "sys_role", ["id", "code", "name", "description", "sort_no"],
           [(rid, code, name, f"来自权限清单'{col}'列", rid) for rid, code, name, col in ROLES])
    out.append("-- 用户-角色")
    insert(out, "sys_user_role", ["user_id", "role_id"],
           [(u["id"], r) for u in users for r in sorted(u["roles"])])
    out.append("-- 软件系统 / 功能模块 / 功能点")
    insert(out, "app_system", ["id", "name", "sort_no"], [(i, n, i) for i, n in systems])
    insert(out, "app_module", ["id", "system_id", "name", "sort_no"], [(i, s, n, i) for i, s, n in modules])
    insert(out, "app_function", ["id", "module_id", "name", "description", "sort_no"],
           [(i, m, n, desc, i) for i, m, n, desc in functions])
    out.append("-- 操作 & 权限")
    insert(out, "sys_action", ["id", "code", "name"], ACTIONS)
    insert(out, "sys_permission", ["id", "function_id", "action_id", "code"], perms)
    out.append("-- 角色-权限 (来自权限清单)")
    insert(out, "sys_role_permission", ["role_id", "permission_id", "data_scope"],
           [(rid, perm_id[(fid, aid)], scope) for rid, fid, aid, scope in grants])
    return "\n".join(out)


# ------------------------------------------------------------------ 主流程
def run(dept_path, perm_path, emp_path, out_path):
    """执行导入, 写出 SQL 和核对报告 (同目录 *_report.txt), 返回各项数量。"""
    warnings.clear()
    depts, dept_by_name = load_depts(dept_path)
    dept_by_id = {d["id"]: d for d in depts}
    root = next(d for d in depts if d["level"] == 1)
    systems, modules, functions, grants = load_permissions(perm_path)
    emps, failed, emp_rows = None, [], 0
    if emp_path:
        emps, failed = load_employees(emp_path, dept_by_name, root["name"])
        emp_rows = len(emps) + len(failed)
    users, unresolved = build_users(depts, emps, dept_by_id)
    for u in users:
        u["password_hash"] = password_hash(DEFAULT_PASSWORD)

    # 每个功能点 × 6 种操作 = 一条权限
    perms, perm_id = [], {}
    for fid, *_ in functions:
        for aid, code, _ in ACTIONS:
            pid = len(perms) + 1
            perm_id[(fid, aid)] = pid
            perms.append((pid, fid, aid, f"F{fid}:{code}"))

    out_dir = os.path.dirname(out_path)
    if out_dir:
        os.makedirs(out_dir, exist_ok=True)
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        f.write(render_sql(depts, users, systems, modules, functions, perms, grants, perm_id).rstrip("\n") + "\n")

    role_count = {code: sum(rid in u["roles"] for u in users) for rid, code, _, _ in ROLES}
    with_mgr = [d for d in depts if d["manager"]]
    stats = {"depts": len(depts), "users": len(users), "systems": len(systems), "modules": len(modules),
             "functions": len(functions), "permissions": len(perms), "grants": len(grants)}
    lines = [
        "== 导入核对 ==",
        f"部门表: {len(depts)} 个部门 (根节点 {root['name']}), 其中 {len(with_mgr)} 个填了负责人",
        f"权限清单: 系统 {len(systems)} / 模块 {len(modules)} / 功能点 {len(functions)}; "
        f"权限 {len(perms)} (功能点 x 6 种操作); 角色授权 {len(grants)} 条, "
        f"其中仅本人 (SELF) {sum(g[3] == 'SELF' for g in grants)} 条",
        (f"员工表: 源数据 {emp_rows} 行 -> 成功 {len(emps)} 行, 失败 {len(failed)} 行" if emp_path
         else "员工表: 未提供, 由部门经理生成用户, 另加 admin 和 staff"),
        f"部门负责人: {len(with_mgr)} 个 -> 已关联用户 ID {len(with_mgr) - len(unresolved)} 个, "
        f"待核对 {len(unresolved)} 个",
        f"用户 {len(users)} 个; 角色分配: " + ", ".join(f"{k} {v}" for k, v in role_count.items()),
        "",
    ]
    lines += [f"[失败] 员工表第{no}行: {reason}" for no, reason in failed]
    lines += [f"[待核对] 部门表第{d['row']}行 {d['name']} 的负责人: {reason}" for d, reason in unresolved]
    lines += [f"[警告] {w}" for w in warnings]
    lines.append(f"已写入 {out_path}")
    report = "\n".join(lines) + "\n"
    with open(os.path.splitext(out_path)[0] + "_report.txt", "w", encoding="utf-8", newline="\n") as f:
        f.write(report)
    print(report, end="")
    return stats


def main():
    ap = argparse.ArgumentParser(description="课程 Excel -> RBAC0 初始数据 SQL")
    ap.add_argument("--dept", required=True, help="部门信息表 .xlsx")
    ap.add_argument("--perm", required=True, help="应用软件功能权限清单 .xlsx")
    ap.add_argument("--emp", help="员工信息表 .xlsx 或 UTF-8 .csv (可选)")
    ap.add_argument("-o", "--out", default=DEFAULT_OUT, help=f"输出 SQL, 默认 {DEFAULT_OUT} (被 Git 忽略)")
    args = ap.parse_args()
    try:
        run(args.dept, args.perm, args.emp, args.out)
    except DataError as e:
        sys.exit(f"导入失败: {e}")


if __name__ == "__main__":
    main()
