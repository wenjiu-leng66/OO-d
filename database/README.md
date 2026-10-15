# 数据库

主责：成员 1。RBAC0 的表结构已建成迁移 V001，并在一次性 MySQL 8.4 库上验证过建表、导入、约束和回滚。原始数据不在仓库里。

| 位置 | 内容 |
| --- | --- |
| [migrations/](migrations/README.md) | 迁移文件，按版本号顺序执行 |
| `rollback/` | 每个迁移的回滚脚本，手工执行 |
| [seeds/](seeds/README.md) | Excel 导入脚本与导入后核对 SQL |

结构说明见 [数据字典](../docs/database/data-dictionary.md)，清洗与核对规则见 [导入质量规则](../docs/database/import-rules.md)，原始数据的存放规则见 [数据说明](../data/README.md)。

核心概念包括用户、部门、角色、权限、用户角色关系、角色权限关系；后续增加角色层级、约束与会话所需信息。字段与关系先在字典中确认，再通过迁移文件实施。

## 本地初始化

需要 MySQL 8.0.16 及以上（小组定为 8.4）。命令在仓库根目录执行，PowerShell 与 Bash 通用；`rbac_dev` 换成自己的库名。

```powershell
mysql -uroot -p -e "CREATE DATABASE rbac_dev DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
mysql -uroot -p --default-character-set=utf8mb4 rbac_dev -e "source database/migrations/V001__create_core_tables.sql"
python database/seeds/import_excel.py --dept data/raw/市政公司部门信息表.xlsx --perm data/raw/市政公司应用软件功能权限清单.xlsx --emp data/raw/市政公司员工信息表.xlsx
mysql -uroot -p --default-character-set=utf8mb4 rbac_dev -e "source data/raw/seed_data.sql"
mysql -uroot -p --default-character-set=utf8mb4 rbac_dev -e "source database/seeds/verify_import.sql"
```

第三步需要先把三份原始 Excel 放到 `data/raw/`，运行时会提示输入所有导入账号的初始密码；密码不进仓库，首次登录必须修改。1 万个账号的密码哈希约需 2 分钟。

演示账号：admin（超级管理员）、SG000001（董事长，公司领导）、SG000011（综合办公室主任，部门经理）、SG000013（综合办公室干事，普通员工）、SG000281（道路工程分公司第一项目经理部项目经理）。各账号演示的权限见 [导入质量规则](../docs/database/import-rules.md) 第 9 节。不加 `--emp` 时改用部门表的负责人生成 E0001 起的账号和演示员工 staff。

各成员开发使用独立数据库，结构由迁移文件统一。集成库迁移由指定负责人执行，涉及数据损失的操作先备份并评审。
