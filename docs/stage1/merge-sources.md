# 第一阶段合并来源与决定

2026-10-18，在 `integration/stage1` 集成各分支已有成果，保留其 Git 提交和作者历史。下表记录输入版本；最终发布 SHA、合并到主分支和云服务器状态以 [验证记录](verification-record.md) 为准。

| 来源 | 输入 SHA | 内容 |
| --- | --- | --- |
| `origin/main` | `4885ad97348fa9d6075a8fff003075148a6a0bac` | 已合并的基础 Vue 界面、RBAC0 判断模块和协作框架 |
| `origin/docs/rbac-requirements`（PR #14） | `fb289f8fdfd2901b15d9846ddeec628da2d197db` | 需求规格与一期 41 条需求 |
| `origin/feat/member1-rbac0-database`（PR #13） | `1e000f71b09fc2f5b638f59c17427639fbba65be` | 权威数据库迁移、字典与离线导入 |
| `origin/claude/rbac0-ui-prototype-19a12f`（PR #15） | `8dbe07c09c928a870efe276bc6fb4d1788067d36` | 完整门户、台账和管理后台界面原型 |
| `origin/docs/member6-rbac0-api` | `1860a8977f30ae33b6b8cd6a7ddec107d60777b6` | API 与权限拦截测试设计 |
| `origin/docs/api-gap-review`（PR #16） | `d25e6766193b683363ba3ba76d8c7b833e28c79f` | 需求、接口、字段和表结构差异评审 |
| `origin/docs/stage1-task-breakdown` | `2e0893718c11b23c5b31412e12e34e6355153674` | 按接口和成员拆解的一期任务 |
| `origin/feature/procedural-backend`（PR #12） | `ada89e6e17185def1809dc8c076e7c8148d60a0a` | 成员 4、5 已有 Java 基础后端、测试与设计 |

整合处理：

1. 需求规格作为业务范围来源；按用户本次说明，前端真实联调不再作为一期必要条件，完整原型仍须单独运行检查。
2. 数据库结构采用成员 1 的 V001 迁移，不采用 PR #12 独立的 H2 表结构。开发/自动测试可以用兼容库；部署使用 MySQL。原始 Excel 与真实员工资料不进入公开仓库。
3. API 在成员 6 草案基础上吸收差异清单，统一为 0.3.0。删除账号类别；统一 `username`、`ALL/DEPT/SELF`、状态枚举、首次改密、会话、`rbac:*` 映射和授权集合版本。完整路径以 [OpenAPI](../../api/openapi.yaml) 为准，例如改密使用 `PATCH /me/password`，员工台账使用 `/ledger/employees`。
4. 后端沿用成员 4、5 已有 Java/Spring Boot 实现补齐一期能力，修复状态/角色/范围/账号保护，并补充对应测试；面向对象实现留待第二阶段。
5. 原型保留独立模拟演示，不把模拟权限/数据作为服务器后端的真实状态。模拟账号、授权与数据必须标识清楚。
6. 部门管理员增加平台六条授权，不改写原清单五角色 3549 条的统计含义。
7. 部署提供同源访问、MySQL持久化、健康检查和服务器自测步骤。用户后续明确本轮只在仓库完善，不连接或操作其服务器；因此脚本留待用户自行部署，记录不写已上线。

历史设计文档保留便于追溯；其“尚无后端”“待拍板接口”“H2 独立表结构”等是来源分支当时状态，不能用于判断本集成版。
