# 数据库迁移

小组还没有引入迁移工具，文件名先沿用 Flyway 的格式，以后接入 Flyway 时不用改名：

- 迁移：`V{三位版本号}__{英文描述}.sql`，例如 `V001__create_core_tables.sql`。按版本号顺序，每个文件只执行一次。
- 回滚：每个迁移配一份 `database/rollback/U{同一版本号}__{同一描述}.sql`。Flyway 社区版不执行撤销迁移，回滚放在本目录之外，以免被当成迁移扫描。
- 迁移文件不建库、不切换库、不删表，在已选好的空库或上一版本的库上执行。

| 版本 | 迁移 | 内容 | 回滚 |
| --- | --- | --- | --- |
| 001 | [V001__create_core_tables.sql](V001__create_core_tables.sql) | RBAC0 的 10 张表和视图 `v_user_permission` | `database/rollback/U001__create_core_tables.sql` |

手工执行（仓库根目录）：

```powershell
mysql -uroot -p --default-character-set=utf8mb4 rbac_dev -e "source database/migrations/V001__create_core_tables.sql"
mysql -uroot -p --default-character-set=utf8mb4 rbac_dev -e "source database/rollback/U001__create_core_tables.sql"
```

已应用且被团队使用的迁移文件不直接改写，用新的迁移修正。PR 写明前置版本、数据影响、验证和恢复办法。不要提交数据库密码或真实员工 INSERT 数据。

会改动已有授权数据的迁移（例如 RBAC1 引入角色层级），要证明迁移前后每个用户的有效权限一致：迁移前把 `v_user_permission` 存成快照表，迁移后用 `EXCEPT` 双向比较，两个方向都应为空。
