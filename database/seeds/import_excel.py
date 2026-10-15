"""
把课程的三份 Excel (部门信息表、应用软件功能权限清单、员工信息表) 清洗后生成 RBAC0 初始数据 SQL,
配合迁移 database/migrations/V001__create_core_tables.sql 使用。

- 只用 Python 标准库 (直接解析 xlsx 里的 XML), 不用装 openpyxl。员工表也可以是 UTF-8 的 CSV。
- 输出含姓名和密码哈希, 默认写到被 Git 忽略的 data/raw/, 不要提交。
- 权限码 = 系统短码:功能点编号:动作, 如 hr:f046:edit。系统短码来自同目录的 system_codes.csv,
  功能点编号来自同目录的 function_codes.csv (要提交), 清单行序变化时编号不变。
- 初始密码在运行时输入或用 --initial-password 指定, 不写进仓库; 所有账号首次登录必须修改密码。
- 清洗规则、组织映射、数据范围和它们的依据见 docs/database/import-rules.md。

用法 (在仓库根目录执行):
    python database/seeds/import_excel.py --dept data/raw/市政公司部门信息表.xlsx \\
        --perm data/raw/市政公司应用软件功能权限清单.xlsx [--emp data/raw/市政公司员工信息表.xlsx]

没有员工表时, 用部门表里的"部门经理"生成用户, 另加 admin 和一个演示普通员工 staff。
"""
import argparse
import csv
import getpass
import hashlib
import os
import re
import secrets
import sys
import zipfile
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor
from decimal import Decimal, InvalidOperation

NS = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
DEFAULT_OUT = os.path.join("data", "raw", "seed_data.sql")
SEED_DIR = os.path.dirname(os.path.abspath(__file__))
SYSTEM_CODES_FILE = os.path.join(SEED_DIR, "system_codes.csv")
FUNCTION_CODES_FILE = os.path.join(SEED_DIR, "function_codes.csv")
MIN_PASSWORD_LEN = 6

# 与 Spring Security 的 Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8() 一致:
# 16 字节随机盐, 310000 次 HMAC-SHA256, 32 字节输出, 存 hex(盐 + 哈希), 前缀标明算法。
# 后端用 PasswordEncoderFactories.createDelegatingPasswordEncoder() 即可校验。
PBKDF2_ID = "pbkdf2@SpringSecurity_v5_8"
PBKDF2_SALT_BYTES = 16
PBKDF2_ITERATIONS = 310000
PBKDF2_HASH_BYTES = 32

ACTIONS = [  # (id, 动作码, 中文)
    (1, "read", "查看"),
    (2, "create", "新增"),
    (3, "edit", "编辑"),
    (4, "approve", "审批"),
    (5, "export", "导出"),
    (6, "delete", "删除"),
]
ACTION_BY_CN = {cn: aid for aid, _, cn in ACTIONS}
ACTION_BY_CN["view"] = 1  # Q-07: 清单 H117、G310 把"查看"写成了英文 view

ROLE_ADMIN, ROLE_LEADER, ROLE_DEPT_MANAGER, ROLE_PROJECT_MANAGER, ROLE_STAFF, ROLE_DEPT_ADMIN = 1, 2, 3, 4, 5, 6
ROLES = [  # (id, code, 名称, 清单中的列名 (None = 平台自己的角色), 说明); 全部是内置角色, 不能删除
    (ROLE_ADMIN, "ADMIN", "系统管理员", "系统管理员", "超级管理员, 只授予预置账号 admin, 界面上不能授予他人"),
    (ROLE_LEADER, "LEADER", "公司领导", "公司领导", "来自权限清单'公司领导'列"),
    (ROLE_DEPT_MANAGER, "DEPT_MANAGER", "部门经理", "部门经理", "来自权限清单'部门经理'列"),
    (ROLE_PROJECT_MANAGER, "PROJECT_MANAGER", "项目经理", "项目经理", "来自权限清单'项目经理'列"),
    (ROLE_STAFF, "STAFF", "普通员工", "普通员工", "来自权限清单'普通员工'列"),
    (ROLE_DEPT_ADMIN, "DEPT_ADMIN", "部门管理员", None,
     "平台自己的管理角色, 不来自清单; 管理本部门及全部下级部门的用户, 授权在接口设计阶段确定"),
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
        warn(f"{where}: 单元格为空, 按'无'处理, 即不授权 (Q-08)")
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


MANAGE_ACTIONS = {2, 3, 4, 6}  # 新增、编辑、审批、删除; 查看、导出是只读类
DEPT_SCOPED_ROLES = {ROLE_DEPT_MANAGER, ROLE_PROJECT_MANAGER}


def grant_scope(role_id, action_id, cell_scope):
    """一条授权的数据范围 (Q38): 部门经理、项目经理的管理类动作只限本部门及全部下级部门 (DEPT),
    查看、导出为全部; 其他角色按清单原样, 含"本人"的格子为 SELF。"""
    if cell_scope == "SELF":
        return "SELF"
    if role_id in DEPT_SCOPED_ROLES and action_id in MANAGE_ACTIONS:
        return "DEPT"
    return cell_scope


def load_permissions(path, system_codes):
    """返回 (systems [(id, 短码, 名称)], modules [(id, system_id, 名称)], functions [dict], grants)。

    functions 按清单顺序, 每项含 seq (清单中的序号)、row、system、module、module_id、name、desc;
    编号 (id) 由 number_functions 按编号表补上。grants 为 (role_id, seq, action_id, scope)。
    """
    rows = read_table(path)
    h = find_header(rows, ["功能点"], "权限清单")
    head = rows[h][1]
    try:
        i_sys, i_mod, i_fn, i_desc = (head.index(x) for x in ("软件系统", "功能模块", "功能点", "功能说明"))
    except ValueError:
        raise DataError(f"权限清单缺少 软件系统/功能模块/功能点/功能说明 列, 表头为 {head}")
    role_cols = {}
    for rid, _, _, col_name, _ in ROLES:
        if col_name is None:
            continue
        if col_name not in head:
            raise DataError(f"权限清单缺少角色列: {col_name}")
        role_cols[rid] = head.index(col_name)

    systems, modules, functions, grants = [], [], [], []
    cur_sys = cur_mod = None
    sys_seen, mod_ids, fn_keys = set(), {}, {}
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
        if cur_sys not in system_codes:
            raise DataError(f"权限清单第{no}行: 软件系统 {cur_sys} 没有短码, 先在 system_codes.csv 登记")
        sys_id, sys_code = system_codes[cur_sys]
        if cur_sys not in sys_seen:
            sys_seen.add(cur_sys)
            systems.append((sys_id, sys_code, cur_sys))
        mkey = (cur_sys, cur_mod)
        if mkey not in mod_ids:
            mod_ids[mkey] = len(modules) + 1
            modules.append((mod_ids[mkey], sys_id, cur_mod))
        fkey = (mod_ids[mkey], r[i_fn])
        if fkey in fn_keys:
            raise DataError(f"权限清单第{no}行: 功能点 {r[i_fn]} 与第{fn_keys[fkey]}行在同一模块中重复")
        fn_keys[fkey] = no
        seq = len(functions) + 1
        functions.append({"seq": seq, "row": no, "system": cur_sys, "module": cur_mod,
                          "module_id": mod_ids[mkey], "name": r[i_fn], "desc": r[i_desc] or None})
        for rid, col in role_cols.items():
            action_ids, scope = parse_cell(r[col], f"第{no}行[{r[i_fn]}]/{head[col]}")
            for aid in sorted(action_ids):
                grants.append((rid, seq, aid, grant_scope(rid, aid, scope)))
    for name, (sys_id, code) in system_codes.items():
        if name not in sys_seen:
            warn(f"system_codes.csv 中的系统 {code} {name} 在清单里找不到")
    return systems, modules, functions, grants


# ------------------------------------------------------------------ 系统短码与功能点编号
def fcode(num):
    """功能点编号 -> 权限码第二段, 46 -> f046"""
    return f"f{num:03d}"


def load_system_codes(path):
    """system_codes.csv (code,name) -> {系统名称: (app_system.id, 短码)}, id 是文件中的顺序。"""
    rows = read_table(path)
    if not rows or rows[0][1][:2] != ["code", "name"]:
        raise DataError(f"{path}: 表头应为 code,name")
    result, codes = {}, set()
    for sys_id, (no, r) in enumerate(rows[1:], 1):
        code, name = pad(r, 2)[:2]
        if not re.fullmatch(r"[a-z][a-z0-9]{1,15}", code):
            raise DataError(f"{path} 第{no}行: 短码 '{code}' 应为 2~16 位小写字母或数字, 以字母开头")
        if code in codes or name in result:
            raise DataError(f"{path} 第{no}行: 短码 {code} 或系统名称 {name} 重复")
        codes.add(code)
        result[name] = (sys_id, code)
    return result


def load_function_codes(path):
    """function_codes.csv (code,system,module,function) -> {(系统, 模块, 功能点): 编号}; 文件不存在时为空。"""
    if not os.path.exists(path):
        return {}
    rows = read_table(path)
    if not rows or rows[0][1][:4] != ["code", "system", "module", "function"]:
        raise DataError(f"{path}: 表头应为 code,system,module,function")
    known, rows_by_num = {}, {}
    for no, r in rows[1:]:
        r = pad(r, 4)
        m = re.fullmatch(r"f(\d{3,})", r[0])
        if not m:
            raise DataError(f"{path} 第{no}行: 编号 '{r[0]}' 应为 f 加至少 3 位数字")
        num, key = int(m.group(1)), tuple(r[1:4])
        if num in rows_by_num:
            raise DataError(f"{path} 第{no}行: 编号 {r[0]} 与第{rows_by_num[num]}行重复")
        if key in known:
            raise DataError(f"{path} 第{no}行: 功能点 {'/'.join(key)} 重复登记")
        rows_by_num[num] = no
        known[key] = num
    return known


def number_functions(functions, known, allow_new):
    """按编号表给功能点补 id。清单里新出现的功能点: allow_new 时接着最大编号往后编, 否则停止导入。
    返回新登记的功能点。编号表里有、清单里没有的编号保留不复用。"""
    next_num = max(known.values(), default=0) + 1
    new = []
    for f in functions:
        key = (f["system"], f["module"], f["name"])
        if key in known:
            f["id"] = known[key]
        elif allow_new:
            f["id"] = next_num
            next_num += 1
            new.append(f)
        else:
            raise DataError(f"权限清单第{f['row']}行: 功能点 {'/'.join(key)} 不在编号表中。"
                            f"若是新增的功能点, 加 --update-codes 登记编号并提交编号表; 若是改名, 直接改编号表里的名称")
    present = {(f["system"], f["module"], f["name"]) for f in functions}
    for key, num in sorted(known.items(), key=lambda kv: kv[1]):
        if key not in present:
            warn(f"编号表中的 {fcode(num)} {'/'.join(key)} 在清单里找不到 (删除或改名), 编号保留不复用")
    return new


def save_function_codes(path, known, new):
    entries = sorted([(num, key) for key, num in known.items()] +
                     [(f["id"], (f["system"], f["module"], f["name"])) for f in new])
    with open(path, "w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh, lineterminator="\n")
        w.writerow(["code", "system", "module", "function"])
        for num, key in entries:
            w.writerow([fcode(num), *key])


def perm_id(function_id, action_id):
    """权限 ID 由功能点编号和动作算出, 重复导入、清单调整行序时都不变"""
    return (function_id - 1) * len(ACTIONS) + action_id


def build_permissions(systems, modules, functions):
    sys_code = {sid: code for sid, code, _ in systems}
    mod_sys = {mid: sid for mid, sid, _ in modules}
    return [(perm_id(f["id"], aid), f["id"], aid, f"{sys_code[mod_sys[f['module_id']]]}:{fcode(f['id'])}:{acode}")
            for f in sorted(functions, key=lambda f: f["id"]) for aid, acode, _ in ACTIONS]


# ------------------------------------------------------------------ 员工 & 组织映射
EMP_ALIASES = {  # 员工表的真实列名尚未确认, 按常见写法识别; 英文列名对应 data/samples/users.sample.csv
    "username": ["工号", "员工编号", "员工工号", "编号", "账号", "用户名", "username"],
    "real_name": ["姓名", "员工姓名", "name"],
    "company": ["公司", "分公司", "所属公司", "单位", "所属单位", "company"],
    "dept": ["部门", "所属部门", "部门名称", "department"],
    "position": ["岗位", "职位", "职务", "position"],
    "gender": ["性别", "gender"],
    "phone": ["电话", "电话号码", "手机", "手机号", "手机号码", "联系电话", "phone"],
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

    emps, failed, seen, seen_phone = [], [], {}, {}
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
        phone = get("phone") or None
        if phone in seen_phone:  # 手机号是登录标识之一, 不为空时必须唯一
            failed.append((no, f"手机号与第{seen_phone[phone]}行重复"))
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
        if phone:
            seen_phone[phone] = no
        emps.append({
            "row": no,
            "username": username,
            "real_name": get("real_name"),
            "dept_id": dept["id"],
            "position": get("position") or None,
            "gender": gender,
            "phone": phone,
            "email": get("email") or None,
        })
    return emps, failed


def load_employees(path, dept_by_name, root_name):
    return build_employees(read_table(path), dept_by_name, root_name)


# ------------------------------------------------------------------ 用户、负责人与角色
LEADER_DEPT_NAME = "公司领导"


def manager_role(dept):  # Q16: 按管理的部门类型给初始角色, 不看岗位文字
    if dept["type"] == "HQ" or dept["name"] == LEADER_DEPT_NAME:
        return ROLE_LEADER
    if dept["type"] == "PROJECT":
        return ROLE_PROJECT_MANAGER
    return ROLE_DEPT_MANAGER  # 含分公司经理


def manager_title(dept):
    if dept["type"] == "HQ" or dept["name"] == LEADER_DEPT_NAME:
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
    for u in users:  # Q41: "公司领导"部门的人都是公司领导, 与是否登记为负责人无关
        dept = dept_by_id.get(u["dept_id"])
        if dept and dept["name"] == LEADER_DEPT_NAME:
            u["roles"].add(ROLE_LEADER)
    for u in users:  # Q42: 其余人 (含副职) 默认普通员工, 需要时由管理员另加角色
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


def render_sql(depts, users, systems, modules, functions, perms, grants):
    by_seq = {f["seq"]: f for f in functions}
    out = [
        "-- 由 database/seeds/import_excel.py 生成, 请勿手改; 改源表后重新生成。",
        "-- 含员工姓名和密码哈希, 不得提交到 Git。执行前先应用迁移 V001__create_core_tables.sql。",
        "-- 初始密码在导入时指定, 不写在这里; 所有账号首次登录必须修改密码。",
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
    out.append("-- 用户 (must_change_password 取默认值 1: 首次登录必须修改密码)")
    insert(out, "sys_user", ["id", "username", "password_hash", "real_name", "dept_id", "position",
                             "gender", "phone", "email"],
           [(u["id"], u["username"], u["password_hash"], u["real_name"], u["dept_id"], u["position"],
             u["gender"], u["phone"], u["email"]) for u in users])
    for d in depts:
        if d["manager_user_id"]:
            out.append(f"UPDATE sys_dept SET manager_user_id = {d['manager_user_id']} WHERE id = {d['id']};")
    out.append("")
    out.append("-- 角色 (都是内置角色, 不能删除)")
    insert(out, "sys_role", ["id", "code", "name", "description", "sort_no", "builtin"],
           [(rid, code, name, desc, rid, 1) for rid, code, name, _, desc in ROLES])
    out.append("-- 用户-角色")
    insert(out, "sys_user_role", ["user_id", "role_id"],
           [(u["id"], r) for u in users for r in sorted(u["roles"])])
    out.append("-- 软件系统 / 功能模块 / 功能点 (功能点 id 即编号表里的编号)")
    insert(out, "app_system", ["id", "code", "name", "sort_no"], [(i, c, n, i) for i, c, n in systems])
    insert(out, "app_module", ["id", "system_id", "name", "sort_no"], [(i, s, n, i) for i, s, n in modules])
    insert(out, "app_function", ["id", "module_id", "code", "name", "description", "sort_no"],
           [(f["id"], f["module_id"], fcode(f["id"]), f["name"], f["desc"], f["seq"]) for f in functions])
    out.append("-- 动作 & 权限")
    insert(out, "sys_action", ["id", "code", "name"], ACTIONS)
    insert(out, "sys_permission", ["id", "function_id", "action_id", "code"], perms)
    out.append("-- 角色-权限 (来自权限清单)")
    insert(out, "sys_role_permission", ["role_id", "permission_id", "data_scope"],
           [(rid, perm_id(by_seq[seq]["id"], aid), scope) for rid, seq, aid, scope in grants])
    return "\n".join(out)


# ------------------------------------------------------------------ 主流程
def run(dept_path, perm_path, emp_path, out_path, initial_password,
        system_codes_path=SYSTEM_CODES_FILE, function_codes_path=FUNCTION_CODES_FILE, update_codes=False):
    """执行导入, 写出 SQL 和核对报告 (同目录 *_report.txt), 返回各项数量。
    update_codes 为真时, 把清单里新出现的功能点登记进编号表。"""
    warnings.clear()
    if len(initial_password or "") < MIN_PASSWORD_LEN:
        raise DataError(f"初始密码至少 {MIN_PASSWORD_LEN} 位")
    depts, dept_by_name = load_depts(dept_path)
    dept_by_id = {d["id"]: d for d in depts}
    root = next(d for d in depts if d["level"] == 1)
    systems, modules, functions, grants = load_permissions(perm_path, load_system_codes(system_codes_path))
    known = load_function_codes(function_codes_path)
    new_codes = number_functions(functions, known, update_codes)
    if new_codes:
        save_function_codes(function_codes_path, known, new_codes)
    perms = build_permissions(systems, modules, functions)
    emps, failed, emp_rows = None, [], 0
    if emp_path:
        emps, failed = load_employees(emp_path, dept_by_name, root["name"])
        emp_rows = len(emps) + len(failed)
    users, unresolved = build_users(depts, emps, dept_by_id)
    # 每个哈希约 0.1 秒, 1 万个账号串行要十几分钟; hashlib 计算时释放 GIL, 多线程即可并行
    with ThreadPoolExecutor() as pool:
        for u, h in zip(users, pool.map(password_hash, [initial_password] * len(users))):
            u["password_hash"] = h

    out_dir = os.path.dirname(out_path)
    if out_dir:
        os.makedirs(out_dir, exist_ok=True)
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        f.write(render_sql(depts, users, systems, modules, functions, perms, grants).rstrip("\n") + "\n")

    role_count = {code: sum(rid in u["roles"] for u in users) for rid, code, _, _, _ in ROLES}
    with_mgr = [d for d in depts if d["manager"]]
    stats = {"depts": len(depts), "users": len(users), "systems": len(systems), "modules": len(modules),
             "functions": len(functions), "permissions": len(perms), "grants": len(grants),
             "dept_grants": sum(g[3] == "DEPT" for g in grants), "new_codes": len(new_codes)}
    lines = [
        "== 导入核对 ==",
        f"部门表: {len(depts)} 个部门 (根节点 {root['name']}), 其中 {len(with_mgr)} 个填了负责人",
        f"权限清单: 系统 {len(systems)} / 模块 {len(modules)} / 功能点 {len(functions)}; "
        f"权限 {len(perms)} (功能点 x 6 种动作); 角色授权 {len(grants)} 条, "
        f"其中本部门 (DEPT) {sum(g[3] == 'DEPT' for g in grants)} 条、仅本人 (SELF) "
        f"{sum(g[3] == 'SELF' for g in grants)} 条",
        f"功能点编号: 编号表原有 {len(known)} 个, 本次新登记 {len(new_codes)} 个"
        + (f" (已写回 {function_codes_path}, 请提交)" if new_codes else ""),
        (f"员工表: 源数据 {emp_rows} 行 -> 成功 {len(emps)} 行, 失败 {len(failed)} 行" if emp_path
         else "员工表: 未提供, 由部门经理生成用户, 另加 admin 和 staff"),
        f"部门负责人: {len(with_mgr)} 个 -> 已关联用户 ID {len(with_mgr) - len(unresolved)} 个, "
        f"待核对 {len(unresolved)} 个",
        f"用户 {len(users)} 个, 首次登录都必须修改密码; 角色分配: "
        + ", ".join(f"{k} {v}" for k, v in role_count.items()),
        "",
    ]
    lines += [f"[失败] 员工表第{no}行: {reason}" for no, reason in failed]
    lines += [f"[待核对] 部门表第{d['row']}行 {d['name']} 的负责人: {reason}" for d, reason in unresolved]
    lines += [f"[新编号] {fcode(f['id'])} 权限清单第{f['row']}行 {f['system']}/{f['module']}/{f['name']}"
              for f in new_codes]
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
    ap.add_argument("--initial-password",
                    help="所有导入账号的初始密码。不填则运行时提示输入 (推荐, 不会留在命令历史里)")
    ap.add_argument("--update-codes", action="store_true",
                    help="把清单里新出现的功能点登记进 function_codes.csv, 之后要提交编号表")
    args = ap.parse_args()
    password = args.initial_password
    if password is None:
        password = getpass.getpass("所有导入账号的初始密码 (首次登录必须修改): ")
        if getpass.getpass("再输入一次: ") != password:
            sys.exit("两次输入的密码不一致")
    try:
        run(args.dept, args.perm, args.emp, args.out, password, update_codes=args.update_codes)
    except DataError as e:
        sys.exit(f"导入失败: {e}")


if __name__ == "__main__":
    main()
