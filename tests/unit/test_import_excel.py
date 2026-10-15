"""成员 1 导入脚本 database/seeds/import_excel.py 的单元测试。

只用合成数据和标准库, 不需要原始 Excel, 也不需要数据库。xlsx 在临时目录里现场生成, 不进仓库。

运行 (仓库根目录): python tests/unit/test_import_excel.py
"""
import contextlib
import hashlib
import io
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "database" / "seeds"))

import import_excel as ie  # noqa: E402

LEADER, DEPT_MANAGER, PROJECT_MANAGER, STAFF, ADMIN = (
    ie.ROLE_LEADER, ie.ROLE_DEPT_MANAGER, ie.ROLE_PROJECT_MANAGER, ie.ROLE_STAFF, ie.ROLE_ADMIN)

# 合成部门: (部门名称, 部门经理, 上级部门)
DEPTS = [
    ("总公司", "演示甲", ""),
    ("公司领导", "演示甲", "总公司"),
    ("综合办公室", "演示乙", "总公司"),
    ("道路工程分公司", "演示丙", "总公司"),
    ("道路工程分公司-财务部", "演示丁", "道路工程分公司"),
    ("道路工程分公司-第一项目经理部", "演示戊", "道路工程分公司"),
]


def dept_records(rows=DEPTS):
    return [{"row": i, "name": n, "manager": m, "parent": p} for i, (n, m, p) in enumerate(rows, 2)]


def write_xlsx(path, rows):
    """写一个只含解析脚本用到部分的最小 xlsx。None 或空串表示空单元格 (模拟合并单元格的下方格子)。"""
    main = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    def col(i):
        s, i = "", i + 1
        while i:
            i, r = divmod(i - 1, 26)
            s = chr(65 + r) + s
        return s

    xml_rows = []
    for rno, row in enumerate(rows, 1):
        cells = []
        for cno, v in enumerate(row):
            ref = f"{col(cno)}{rno}"
            if v is None or v == "":
                continue
            if isinstance(v, (int, float)):
                cells.append(f'<c r="{ref}"><v>{v}</v></c>')
            else:
                cells.append(f'<c r="{ref}" t="inlineStr"><is><t>{escape(v)}</t></is></c>')
        xml_rows.append(f'<row r="{rno}">{"".join(cells)}</row>')
    with zipfile.ZipFile(path, "w") as z:
        z.writestr("xl/workbook.xml", f'<workbook xmlns="{main}" xmlns:r="{rel}"><sheets>'
                                      f'<sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets></workbook>')
        z.writestr("xl/_rels/workbook.xml.rels",
                   '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
                   f'<Relationship Id="rId1" Type="{rel}/worksheet" Target="worksheets/sheet1.xml"/>'
                   '</Relationships>')
        z.writestr("xl/worksheets/sheet1.xml",
                   f'<worksheet xmlns="{main}"><sheetData>{"".join(xml_rows)}</sheetData></worksheet>')


class NumberCellTest(unittest.TestCase):
    def test_numeric_cells_become_plain_text(self):  # 工号、电话按文本处理
        self.assertEqual(ie._number_text("12345678901.0"), "12345678901")
        self.assertEqual(ie._number_text("1.2345678901E10"), "12345678901")
        self.assertEqual(ie._number_text("3.5"), "3.5")
        self.assertEqual(ie._number_text("E0001"), "E0001")


class GrantScopeTest(unittest.TestCase):
    """Q38: 部门经理、项目经理的管理类动作限本部门 (含下级), 查看、导出为全部; 其他角色按清单。"""

    def test_managers_manage_only_their_department(self):
        for rid in (DEPT_MANAGER, PROJECT_MANAGER):
            self.assertEqual([ie.grant_scope(rid, aid, "ALL") for aid in range(1, 7)],
                             ["ALL", "DEPT", "DEPT", "DEPT", "ALL", "DEPT"])  # 查看 新增 编辑 审批 导出 删除

    def test_other_roles_follow_the_list(self):
        for rid in (ADMIN, LEADER, STAFF):
            self.assertEqual({ie.grant_scope(rid, aid, "ALL") for aid in range(1, 7)}, {"ALL"})
        self.assertEqual(ie.grant_scope(STAFF, 3, "SELF"), "SELF")


class ParseCellTest(unittest.TestCase):
    def setUp(self):
        ie.warnings.clear()

    def test_split_actions(self):
        self.assertEqual(ie.parse_cell("查看/编辑", "x"), ({1, 3}, "ALL"))
        self.assertEqual(ie.parse_cell("查看、新增，编辑", "x"), ({1, 2, 3}, "ALL"))

    def test_self_applies_to_whole_cell(self):  # Q-09 成员1 建议
        self.assertEqual(ie.parse_cell("查看/编辑本人", "x"), ({1, 3}, "SELF"))

    def test_all_and_none(self):  # Q-10 成员1 建议
        self.assertEqual(ie.parse_cell("全部", "x"), ({1, 2, 3, 4, 5, 6}, "ALL"))
        self.assertEqual(ie.parse_cell("无", "x"), (set(), "ALL"))

    def test_blank_is_denied_and_reported(self):  # Q-08: 确认前拒绝
        self.assertEqual(ie.parse_cell("  ", "第340行"), (set(), "ALL"))
        self.assertIn("Q-08", ie.warnings[-1])

    def test_english_view_is_normalized_and_reported(self):  # Q-07
        self.assertEqual(ie.parse_cell("view/新增", "第117行"), ({1, 2}, "ALL"))
        self.assertIn("Q-07", ie.warnings[-1])

    def test_unknown_action_stops_import(self):
        with self.assertRaises(ie.DataError):
            ie.parse_cell("查看/打印", "x")


class BuildDeptsTest(unittest.TestCase):
    def test_level_type_and_short_name(self):
        depts, by_name = ie.build_depts(dept_records())
        got = {d["name"]: (d["level"], d["type"], d["short_name"]) for d in depts}
        self.assertEqual(got["总公司"], (1, "HQ", "总公司"))
        self.assertEqual(got["公司领导"], (2, "HQ_DEPT", "公司领导"))
        self.assertEqual(got["道路工程分公司"], (2, "BRANCH", "道路工程分公司"))
        self.assertEqual(got["道路工程分公司-财务部"], (3, "BRANCH_DEPT", "财务部"))
        self.assertEqual(got["道路工程分公司-第一项目经理部"], (3, "PROJECT", "第一项目经理部"))
        self.assertEqual(by_name["道路工程分公司-财务部"]["parent_id"], by_name["道路工程分公司"]["id"])

    def test_duplicate_name(self):
        with self.assertRaisesRegex(ie.DataError, "重复"):
            ie.build_depts(dept_records(DEPTS + [("综合办公室", "演示乙", "总公司")]))

    def test_missing_parent(self):
        with self.assertRaisesRegex(ie.DataError, "不存在"):
            ie.build_depts(dept_records(DEPTS + [("某部门", "", "不存在的公司")]))

    def test_parent_cycle(self):
        with self.assertRaisesRegex(ie.DataError, "循环"):
            ie.build_depts(dept_records(DEPTS + [("甲部", "", "乙部"), ("乙部", "", "甲部")]))

    def test_exactly_one_root(self):
        with self.assertRaisesRegex(ie.DataError, "根节点"):
            ie.build_depts(dept_records(DEPTS + [("另一个总公司", "", "")]))


class DeptKeyTest(unittest.TestCase):
    """员工表 (公司, 部门) 映射到部门表全称的三条规则, 见 docs/requirements/questions.md 的数据规则候选。"""

    def test_hq_employee_uses_department_name(self):
        self.assertEqual(ie.dept_key("总公司", "综合办公室", "总公司"), "综合办公室")

    def test_company_equals_department_uses_branch_node(self):
        self.assertEqual(ie.dept_key("道路工程分公司", "道路工程分公司", "总公司"), "道路工程分公司")

    def test_otherwise_branch_dash_department(self):
        self.assertEqual(ie.dept_key("道路工程分公司", "财务部", "总公司"), "道路工程分公司-财务部")

    def test_without_company_column_department_is_full_name(self):
        self.assertEqual(ie.dept_key("", "道路工程分公司-财务部", "总公司"), "道路工程分公司-财务部")


class EmployeesAndManagersTest(unittest.TestCase):
    HEAD = ["name", "username", "company", "department", "position"]

    def setUp(self):
        ie.warnings.clear()
        self.depts, self.by_name = ie.build_depts(dept_records())
        self.by_id = {d["id"]: d for d in self.depts}

    def employees(self, *rows):
        table = [(1, self.HEAD)] + [(i, list(r)) for i, r in enumerate(rows, 2)]
        return ie.build_employees(table, self.by_name, "总公司")

    def test_sample_users_map_to_sample_departments(self):
        """data/samples 里的合成用户都能按三条规则映射到合成部门。"""
        rows = ie.read_table(ROOT / "data/samples/departments.sample.csv")  # name,manager_username,parent_name
        _, by_name = ie.build_depts(dept_records([(cells[0], "", cells[2]) for _, cells in rows[1:]]))
        emps, failed = ie.build_employees(ie.read_table(ROOT / "data/samples/users.sample.csv"), by_name, "总公司")
        self.assertEqual(failed, [])
        self.assertEqual(len(emps), 4)

    def test_failed_rows_are_reported_with_row_numbers(self):
        emps, failed = self.employees(
            ["演示己", "DEMO000005", "道路工程分公司", "财务部", "会计"],
            ["演示己", "DEMO000005", "道路工程分公司", "财务部", "会计"],  # 工号重复
            ["演示庚", "DEMO000006", "不存在的分公司", "财务部", "会计"],  # 部门映射失败
            ["演示辛", "", "总公司", "综合办公室", "文员"],                 # 工号为空
            ["", "DEMO000007", "总公司", "综合办公室", "文员"],             # 姓名为空
            ["演示壬", "admin", "总公司", "综合办公室", "文员"],            # 保留账号
        )
        self.assertEqual([e["username"] for e in emps], ["DEMO000005"])
        self.assertEqual([no for no, _ in failed], [3, 4, 5, 6, 7])
        self.assertIn("第2行", failed[0][1])

    def test_duplicate_phone_fails(self):  # 手机号是登录标识之一, 可以为空, 不为空时唯一
        table = [(1, self.HEAD + ["phone"]),
                 (2, ["演示己", "DEMO000005", "总公司", "综合办公室", "文员", "12300000001"]),
                 (3, ["演示庚", "DEMO000006", "总公司", "综合办公室", "文员", "12300000001"]),
                 (4, ["演示辛", "DEMO000007", "总公司", "综合办公室", "文员", ""]),
                 (5, ["演示壬", "DEMO000008", "总公司", "综合办公室", "文员", ""])]
        emps, failed = ie.build_employees(table, self.by_name, "总公司")
        self.assertEqual([e["phone"] for e in emps], ["12300000001", None, None])
        self.assertEqual(failed, [(3, "手机号与第2行重复")])

    def test_real_employee_table_headers(self):
        """员工表的真实列名: 姓名 / 用户名 / 分公司 / 部门 / 岗位 / 电话号码 (没有性别和邮箱)。"""
        table = [(1, ["姓名", "用户名", "分公司", "部门", "岗位", "电话号码"]),
                 (2, ["演示己", "DEMO000005", "道路工程分公司", "财务部", "会计", "12300000001"])]
        emps, failed = ie.build_employees(table, self.by_name, "总公司")
        self.assertEqual(failed, [])
        self.assertEqual((emps[0]["username"], emps[0]["phone"], emps[0]["email"], emps[0]["gender"]),
                         ("DEMO000005", "12300000001", None, None))
        self.assertEqual(emps[0]["dept_id"], self.by_name["道路工程分公司-财务部"]["id"])

    def test_everyone_in_leader_department_is_leader(self):  # Q41, 与负责人规则叠加
        emps, _ = self.employees(
            ["演示甲", "DEMO_ROOT", "总公司", "公司领导", "董事长"],
            ["演示癸", "DEMO000010", "总公司", "公司领导", "副总经理"],
            ["演示子", "DEMO000011", "总公司", "综合办公室", "副主任"],  # 副职默认普通员工 (Q42)
        )
        users, _ = ie.build_users(self.depts, emps, self.by_id)
        roles = {u["username"]: u["roles"] for u in users}
        self.assertEqual(roles["DEMO_ROOT"], {LEADER})  # 既是负责人, 又在公司领导部门
        self.assertEqual(roles["DEMO000010"], {LEADER})
        self.assertEqual(roles["DEMO000011"], {STAFF})

    def test_managers_link_by_user_id_and_get_roles(self):
        emps, _ = self.employees(
            ["演示甲", "DEMO_ROOT", "总公司", "公司领导", "董事长"],
            ["演示乙", "DEMO000001", "总公司", "综合办公室", "主任"],
            ["演示丙", "DEMO000003", "道路工程分公司", "道路工程分公司", "经理"],
            ["演示丁", "DEMO000002", "道路工程分公司", "财务部", "会计"],
            ["演示戊", "DEMO000004", "道路工程分公司", "第一项目经理部", "项目经理"],
            ["演示己", "DEMO000005", "道路工程分公司", "财务部", "会计"],
        )
        users, unresolved = ie.build_users(self.depts, emps, self.by_id)
        self.assertEqual(unresolved, [])
        uid = {u["username"]: u["id"] for u in users}
        mgr = {d["name"]: d["manager_user_id"] for d in self.depts}
        # Q-12 成员1 建议: 总公司负责人关联到 "公司领导" 部门里的同名用户
        self.assertEqual(mgr["总公司"], uid["DEMO_ROOT"])
        self.assertEqual(mgr["公司领导"], uid["DEMO_ROOT"])
        self.assertEqual(mgr["道路工程分公司"], uid["DEMO000003"])
        roles = {u["username"]: u["roles"] for u in users}
        self.assertEqual(roles["admin"], {ADMIN})
        self.assertEqual(roles["DEMO_ROOT"], {LEADER})
        self.assertEqual(roles["DEMO000001"], {DEPT_MANAGER})
        self.assertEqual(roles["DEMO000003"], {DEPT_MANAGER})
        self.assertEqual(roles["DEMO000002"], {DEPT_MANAGER})
        self.assertEqual(roles["DEMO000004"], {PROJECT_MANAGER})
        self.assertEqual(roles["DEMO000005"], {STAFF})

    def test_same_name_in_managed_department_wins_over_child_department(self):
        emps, _ = self.employees(
            ["演示丙", "DEMO000003", "道路工程分公司", "道路工程分公司", "经理"],
            ["演示丙", "DEMO000008", "道路工程分公司", "财务部", "会计"],
        )
        users, _ = ie.build_users(self.depts, emps, self.by_id)
        self.assertEqual(self.by_name["道路工程分公司"]["manager_user_id"],
                         next(u["id"] for u in users if u["username"] == "DEMO000003"))

    def test_ambiguous_or_unrelated_manager_is_left_empty(self):
        emps, _ = self.employees(
            ["演示乙", "DEMO000001", "总公司", "综合办公室", "主任"],
            ["演示乙", "DEMO000009", "总公司", "综合办公室", "主任"],    # 同部门重名: 无法确定
            ["演示丁", "DEMO000002", "总公司", "综合办公室", "文员"],    # 只有同名, 部门对不上
        )
        users, unresolved = ie.build_users(self.depts, emps, self.by_id)
        self.assertIsNone(self.by_name["综合办公室"]["manager_user_id"])
        self.assertIsNone(self.by_name["道路工程分公司-财务部"]["manager_user_id"])
        self.assertIn("综合办公室", [d["name"] for d, _ in unresolved])
        self.assertTrue(all(u["roles"] in ({STAFF}, {ADMIN}) for u in users))

    def test_without_employee_table_managers_become_users(self):
        users, unresolved = ie.build_users(self.depts, None, self.by_id)
        self.assertEqual(unresolved, [])
        names = [u["username"] for u in users]
        self.assertEqual(names[0], "admin")
        self.assertEqual(names[-1], "staff")
        self.assertEqual(len(users), 2 + 5)  # 6 个部门、5 个不同的负责人; 同名视为同一人
        root = self.by_name["总公司"]["manager_user_id"]
        self.assertEqual(root, self.by_name["公司领导"]["manager_user_id"])
        self.assertEqual(users[root - 1]["roles"], {LEADER})


class PasswordHashTest(unittest.TestCase):
    def test_known_answer_matches_spring_security_layout(self):
        # 期望值由 Java 8 的 PBKDF2WithHmacSHA256 按 Spring Pbkdf2PasswordEncoder 的算法独立算出;
        # 输入是测试专用字符串、盐全为 0, 不是任何账号的密码哈希
        self.assertEqual(
            ie.password_hash("known-answer-vector", salt=bytes(16)),
            "{pbkdf2@SpringSecurity_v5_8}00000000000000000000000000000000"
            "3afba403fb3d5cfa50fe48be57ca270eede560eac8dd5a2b65666e24c4115125")

    def test_random_salt(self):
        a, b = ie.password_hash("salt-test"), ie.password_hash("salt-test")
        self.assertNotEqual(a, b)
        hex_part = a[len("{pbkdf2@SpringSecurity_v5_8}"):]
        salt, dk = bytes.fromhex(hex_part[:32]), bytes.fromhex(hex_part[32:])
        self.assertEqual(dk, hashlib.pbkdf2_hmac("sha256", b"salt-test", salt, 310000, 32))


PERM_HEAD = ["软件系统", "功能模块", "功能点", "功能说明", "系统管理员", "公司领导", "部门经理", "项目经理", "普通员工"]
ROW_SEND = ["协同办公", "公文管理", "发文拟稿", "起草公文", "全部", "查看", "查看/审批", "查看", "新增/编辑本人"]
ROW_RECV = [None, None, "收文登记", "登记来文", "全部", "查看", "无", "view/新增", None]  # 系统、模块是合并单元格
ROW_HR = ["人力资源", "员工台账", "员工台账管理", "维护员工档案", "全部", "查看/导出", "查看/编辑", "查看", "查看/编辑本人"]
SYSTEM_CODES = "code,name\noa,协同办公\nhr,人力资源\n"
TEST_PASSWORD = "Init-for-tests"


def fn(system, module, name, row=2):
    return {"system": system, "module": module, "name": name, "row": row}


class CodesTest(unittest.TestCase):
    """系统短码表、功能点编号表和权限码 系统码:功能点编号:动作。"""

    def setUp(self):
        ie.warnings.clear()

    def test_system_codes_are_validated(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "codes.csv"
            path.write_text("code,name\noa,协同办公\nhr,人力资源\n", encoding="utf-8")
            self.assertEqual(ie.load_system_codes(path), {"协同办公": (1, "oa"), "人力资源": (2, "hr")})
            for bad in ("code,name\nHR,人力资源\n", "code,name\nhr,人力资源\nhr,财务\n"):
                path.write_text(bad, encoding="utf-8")
                with self.assertRaises(ie.DataError):
                    ie.load_system_codes(path)

    def test_numbers_survive_reordering_and_new_ones_need_update_flag(self):
        known = {("协同办公", "公文管理", "发文拟稿"): 1, ("人力资源", "员工台账", "员工台账管理"): 46}
        fs = [fn("人力资源", "员工台账", "员工台账管理"), fn("协同办公", "公文管理", "发文拟稿")]  # 清单换了顺序
        self.assertEqual(ie.number_functions(fs, known, allow_new=False), [])
        self.assertEqual([f["id"] for f in fs], [46, 1])
        fs.append(fn("协同办公", "公文管理", "收文登记", row=9))
        with self.assertRaisesRegex(ie.DataError, "第9行.*--update-codes"):
            ie.number_functions(fs, known, allow_new=False)
        new = ie.number_functions(fs, known, allow_new=True)
        self.assertEqual([f["id"] for f in new], [47])  # 接着最大编号往后编

    def test_removed_function_keeps_its_number(self):
        known = {("协同办公", "公文管理", "发文拟稿"): 1, ("协同办公", "公文管理", "已删除的功能"): 2}
        fs = [fn("协同办公", "公文管理", "发文拟稿"), fn("协同办公", "公文管理", "收文登记")]
        ie.number_functions(fs, known, allow_new=True)
        self.assertEqual(fs[1]["id"], 3)  # 不复用 f002
        self.assertIn("f002", ie.warnings[-1])

    def test_code_table_round_trip(self):
        known = {("协同办公", "公文管理", "发文拟稿"): 1}
        new = [dict(fn("人力资源", "员工台账", "员工台账管理, 含逗号"), id=46)]
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "function_codes.csv"
            ie.save_function_codes(path, known, new)
            self.assertEqual(path.read_text(encoding="utf-8").splitlines()[:2],
                             ["code,system,module,function", "f001,协同办公,公文管理,发文拟稿"])
            self.assertEqual(ie.load_function_codes(path),
                             {("协同办公", "公文管理", "发文拟稿"): 1, ("人力资源", "员工台账", "员工台账管理, 含逗号"): 46})

    def test_permission_code_and_stable_id(self):
        perms = ie.build_permissions([(4, "hr", "人力资源")], [(1, 4, "员工台账")], [{"id": 46, "module_id": 1}])
        self.assertEqual([p[3] for p in perms],
                         ["hr:f046:read", "hr:f046:create", "hr:f046:edit", "hr:f046:approve", "hr:f046:export",
                          "hr:f046:delete"])
        self.assertEqual(perms[2][:3], (45 * 6 + 3, 46, 3))  # 权限 ID 由编号和动作算出


class EndToEndTest(unittest.TestCase):
    """合成 Excel -> SQL: 合并单元格向下填充、空行、行号、失败行报告、编号稳定、密码不落地。"""

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.tmp = Path(self._tmp.name)
        write_xlsx(self.tmp / "dept.xlsx", [["部门名称", "部门经理", "上级部门"]] + [list(r) for r in DEPTS])
        (self.tmp / "system_codes.csv").write_text(SYSTEM_CODES, encoding="utf-8")
        (self.tmp / "users.csv").write_text(
            "name,username,company,department,position,phone\n"
            "演示甲,DEMO_ROOT,总公司,公司领导,董事长,\n"
            "演示己,DEMO000005,道路工程分公司,财务部,会计,12300000000\n"
            "演示己,DEMO000005,道路工程分公司,财务部,会计,\n", encoding="utf-8")

    def tearDown(self):
        self._tmp.cleanup()

    def run_import(self, perm_rows, update_codes=False, name="seed", password=TEST_PASSWORD):
        write_xlsx(self.tmp / "perm.xlsx", [PERM_HEAD] + perm_rows)
        out = self.tmp / "out" / f"{name}.sql"
        with contextlib.redirect_stdout(io.StringIO()):
            stats = ie.run(str(self.tmp / "dept.xlsx"), str(self.tmp / "perm.xlsx"), str(self.tmp / "users.csv"),
                           str(out), password, system_codes_path=str(self.tmp / "system_codes.csv"),
                           function_codes_path=str(self.tmp / "function_codes.csv"), update_codes=update_codes)
        return (stats, out.read_text(encoding="utf-8"),
                (self.tmp / "out" / f"{name}_report.txt").read_text(encoding="utf-8"))

    def test_generate_seed(self):
        stats, sql, report = self.run_import([ROW_SEND, ROW_RECV, [], ROW_HR], update_codes=True)

        self.assertEqual((stats["systems"], stats["modules"], stats["functions"]), (2, 2, 3))
        self.assertEqual(stats["permissions"], 18)
        self.assertEqual(stats["grants"], 12 + 9 + 13)
        self.assertEqual(stats["users"], 3)
        self.assertEqual(stats["new_codes"], 3)
        # 部门经理 发文拟稿 查看/审批: 查看为全部, 审批限本部门; 项目经理 收文登记 view/新增: 新增限本部门
        self.assertEqual(stats["dept_grants"], 3)
        self.assertIn(f"({DEPT_MANAGER}, {ie.perm_id(1, 1)}, 'ALL')", sql)
        self.assertIn(f"({DEPT_MANAGER}, {ie.perm_id(1, 4)}, 'DEPT')", sql)
        self.assertIn(f"({PROJECT_MANAGER}, {ie.perm_id(2, 2)}, 'DEPT')", sql)
        self.assertIn(f"({STAFF}, {ie.perm_id(1, 2)}, 'SELF')", sql)
        self.assertIn("'oa:f001:read'", sql)
        self.assertIn("'hr:f003:edit'", sql)
        self.assertIn("(6, 'DEPT_ADMIN', '部门管理员'", sql)  # 平台自己的内置角色
        self.assertNotIn(TEST_PASSWORD, sql + report)  # 初始密码只以哈希出现
        self.assertNotIn("USE ", sql)
        self.assertIn("INSERT INTO sys_user (id, username, password_hash, real_name, dept_id", sql)
        self.assertIn("'12300000000'", sql)  # 电话按文本保存
        self.assertIn("员工表第4行", report)
        self.assertIn("第3行[收文登记]/普通员工", report)  # 合并单元格下方的空白授权格, 行号保持原表位置
        self.assertIn("第3行[收文登记]/项目经理", report)  # view
        self.assertEqual((self.tmp / "function_codes.csv").read_text(encoding="utf-8").splitlines()[1:],
                         ["f001,协同办公,公文管理,发文拟稿", "f002,协同办公,公文管理,收文登记",
                          "f003,人力资源,员工台账,员工台账管理"])

    def test_reordered_list_keeps_codes(self):
        self.run_import([ROW_SEND, ROW_RECV, ROW_HR], update_codes=True, name="first")
        stats, sql, _ = self.run_import([ROW_HR, ROW_SEND, ROW_RECV], name="second")
        self.assertEqual(stats["new_codes"], 0)
        self.assertIn(f"({ie.perm_id(3, 3)}, 3, 3, 'hr:f003:edit')", sql)

    def test_unknown_function_without_update_flag_stops(self):
        self.run_import([ROW_SEND], update_codes=True, name="first")
        with self.assertRaisesRegex(ie.DataError, "不在编号表中"):
            self.run_import([ROW_SEND, ROW_RECV], name="second")

    def test_unknown_system_stops(self):
        (self.tmp / "system_codes.csv").write_text("code,name\noa,协同办公\n", encoding="utf-8")
        with self.assertRaisesRegex(ie.DataError, "没有短码"):
            self.run_import([ROW_SEND, ROW_HR], update_codes=True)

    def test_short_initial_password_is_rejected(self):
        with self.assertRaisesRegex(ie.DataError, "初始密码"):
            self.run_import([ROW_SEND], update_codes=True, password="123")


if __name__ == "__main__":
    unittest.main()
