# 初始与演示数据

这里只保存合成或得到明确允许的脱敏演示数据及其初始化逻辑。原始 Excel 不在此处。

| 文件 | 用途 |
| --- | --- |
| [import_excel.py](import_excel.py) | 把三份课程 Excel 清洗成 RBAC0 初始数据 SQL，只用 Python 标准库 |
| [system_codes.csv](system_codes.csv) | 18 个软件系统的短码，权限码第一段，如 `hr` |
| [function_codes.csv](function_codes.csv) | 359 个功能点的固定编号，权限码第二段，如 `f046`；只含系统、模块、功能点名称，不含授权和人员 |
| [verify_import.sql](verify_import.sql) | 导入后的只读核对：行数、断链、缺失、权限码、重复导入指纹 |

```powershell
python database/seeds/import_excel.py --dept data/raw/市政公司部门信息表.xlsx --perm data/raw/市政公司应用软件功能权限清单.xlsx [--emp data/raw/市政公司员工信息表.xlsx]
```

- 运行时提示输入所有导入账号的初始密码。密码不进仓库，也不写进生成的 SQL 和报告；导入账号首次登录必须修改密码。
- 给出 `--emp` 时，账号全部来自员工表（用户名 SG000001 起）；不给时用部门表的负责人生成 E0001 起的账号和演示员工 staff。1 万个账号的密码哈希多线程计算，约 2 分钟。
- 输出 `data/raw/seed_data.sql` 和核对报告 `data/raw/seed_data_report.txt`。二者含员工姓名和密码哈希，留在被 Git 忽略的 `data/raw/`，不提交。
- 清单里出现新功能点时脚本会停止，确认后加 `--update-codes` 登记编号，并提交更新后的 `function_codes.csv`。
- 重复执行的行为是重置：先清空 10 张表，再按固定 ID 重建。同一份源表导入两次结果相同，但系统里做过的修改会丢失，集成库导入前先备份。
- 角色初始化、清单空白、“本人”和本部门范围等口径都是组内已定的规则，依据见 [导入质量规则](../../docs/database/import-rules.md)。
- 单元测试：`python tests/unit/test_import_excel.py`。

没有原始员工表时，可以用合成样例试跑员工表导入：`--emp data/samples/users.sample.csv`。最小合成输入样例见 [数据样例说明](../../data/samples/README.md)。
