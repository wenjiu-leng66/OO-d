# 成员5后端复核记录（历史）

本页保留胡俊朗（成员5）的历史执行与贡献记录，并校正容易被误用的复现前提。来源提交：[f989fc3355a0685130e8b722d22c86046dda7a4f](https://github.com/wenjiu-leng66/RBAC-Permission-System/commit/f989fc3355a0685130e8b722d22c86046dda7a4f)，原分支 `docs/member5-backend-review`。下列执行结果来自作者原报告，本次整理没有重新执行该历史版本，也没有将其数字当作当前版本结果。

## 1. 被测版本和环境

| 项目 | 原报告记录 |
| --- | --- |
| 日期/复核人 | 2026-10-18，胡俊朗 |
| 源码 | `29543856a4d429ebf3379a44e22d7b7695af6771`，PR #18集成版 |
| 范围 | 一期 `backend-procedural/`，不含面向对象后端、继承和后续阶段 |
| 工具 | Windows本机，Oracle JDK21.0.6，Maven3.9.10 |
| 数据库 | Spring上下文/MockMvc/H2集成测试，非服务器MySQL |
| 命令 | `mvn -f backend-procedural/pom.xml clean verify` |
| 网络/部署 | 原报告未连接或操作用户服务器，不提供服务器运行证明 |

## 2. 原报告结果

| 项目 | 原报告数值 |
| --- | --- |
| 构建 | BUILD SUCCESS |
| 后端集成测试 | 29项，0失败/错误/跳过；类为Stage1ApiTest |
| 测试/总耗时 | 26.33秒 / 51.94秒 |
| JaCoCo指令覆盖 | 87.1% |
| JaCoCo行覆盖 | 88.7%（976/1100） |
| JaCoCo分支覆盖 | 70.3%（336/478） |

这些是2026-10-18旧SHA的作者报告值。后续新增隔离测试、注释和代码整合后，应打开新版本生成的Surefire/JaCoCo，不能沿用本表作为当前覆盖率。当前结果入口：[本轮测试报告](test-report-2026-10-27.md)、[一期验证记录](../stage1/verification-record.md)。

## 3. 重点检查与复现前提

| 检查 | 对应历史集成测试 | 说明 |
| --- | --- | --- |
| 台账SELF及字段保护 | ledgerSelfScopeAndUnknownFieldProtection | 本人可见/可改；他人和未知目标同拒绝，非电话/邮箱字段拒绝 |
| 经理查看ALL、编辑DEPT及下级 | managerReadAllEditDepartmentAndChildren | 测试先检查SG000011；随后管理员给DEMO000100追加角色3、5、6再检查下级台账 |
| 范围筛选不可逃逸 | departmentUserFiltersCannotEscapeScope | R06用户管理范围与台账业务权限分别判断 |
| 即时撤权和范围并集 | businessRoleUnionAndImmediateRevocation | 角色5、7组合后的read仍SELF，edit为ALL；撤空后下一请求拒绝 |
| 失败回滚 | failedReplacementRollsBackGrantsAndVersion | 真JDBC替换后注入异常，断言关系与版本回滚；不是纯mock回滚证明 |
| 版本与并发 | userRoleVersionConflictAndDuplicateValidation、simultaneousCasHasExactlyOneWinner | 旧版本409；两个同版本并发提交只能一个成功 |
| 角色删除协调 | roleCrudStatusDeleteAndBuiltinGuards、concurrentAssignmentMakesUnconfirmedRoleDeleteRefuse | 有用户须确认；并发授角不能穿透删除判断 |
| 管理保护 | selfAndUniqueAdminProtection、departmentAdminCannotGrantOrRemoveManagementRoles | 本人/admin、管理角色授撤边界；具体状态依保护/权限检查先后而定 |

`DEMO000100`初始是部门管理员，R06本身没有台账权限。旧报告将追加业务角色后的账号称为“部门经理”容易误读，不能据此声称部门管理员天然可以编辑部门台账。上述测试定位可在[Stage1ApiTest](../../backend-procedural/src/test/java/com/municipal/rbac/procedural/Stage1ApiTest.java)核对。

## 4. 结论的使用边界

原作者在该历史版本上报告八项重点检查未发现可复现失败。该结论不是系统无缺陷证明，不涉及云端业务调用、正式全量Excel、MySQL方言/隔离差异、服务器重启或性能目标。后续发现应另记版本、输入、实际结果与证据，而不是改写本次历史报告为当前“全部通过”。
