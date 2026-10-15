# 初始与演示数据

这里只保存合成或得到明确允许的脱敏演示数据及其初始化逻辑。原始 Excel 不在此处。

| 文件 | 用途 |
| --- | --- |
| [import_excel.py](import_excel.py) | 把三份课程 Excel 清洗成 RBAC0 初始数据 SQL，只用 Python 标准库 |
| [verify_import.sql](verify_import.sql) | 导入后的只读核对：行数、断链、缺失、重复导入指纹 |

```powershell
python database/seeds/import_excel.py --dept data/raw/市政公司部门信息表.xlsx --perm data/raw/市政公司应用软件功能权限清单.xlsx [--emp data/raw/市政公司员工信息表.xlsx]
```

- 输出 `data/raw/seed_data.sql` 和核对报告 `data/raw/seed_data_report.txt`。二者含员工姓名和密码哈希，留在被 Git 忽略的 `data/raw/`，不提交。
- 初始密码统一为 `123456`，只存哈希，上线前必须修改；演示账号不用电话号码作密码。
- 重复执行的行为是重置：先清空 10 张表，再按固定 ID 重建。同一份源表导入两次结果相同，但系统里做过的修改会丢失，集成库导入前先备份。
- 角色初始化、清单空白和“本人”等口径按成员 1 的建议实现，仍待老师确认，见 [导入质量规则](../../docs/database/import-rules.md)。
- 单元测试：`python tests/unit/test_import_excel.py`。

没有原始员工表时，可以用合成样例试跑员工表导入：`--emp data/samples/users.sample.csv`。最小合成输入样例见 [数据样例说明](../../data/samples/README.md)。
