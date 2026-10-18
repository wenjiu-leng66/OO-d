# 成员 5 后端复核记录（2026-10-18 集成版）

**复核人**：胡俊朗（成员 5，后端复核与修复支持）
**复核日期**：2026-10-18
**被测版本**：`29543856a4d429ebf3379a44e22d7b7695af6771`（`main`，Merge PR #18）
**复核范围**：仅 `backend-procedural/` 一期交付。面向对象后端、角色继承及后续阶段功能不纳入本次收口。

> 本记录是**对 2026-10-18 集成版的实际执行结果**。
> 仓库中已有的 [成员4/5 记录](member4-procedural-rbac0-verification.md) 明确标注其为来源分支的历史交付，不适用于集成版，本记录不沿用其中任何数字。

---

## 1. 执行环境

| 项目 | 实际值 |
| --- | --- |
| 操作系统 | Windows（本机），未连接或操作用户服务器 |
| JDK | Oracle JDK 21.0.6（runtime `D:\jdk21`） |
| Maven | Apache Maven 3.9.10（`D:\java\apache-maven-3.9.10`） |
| 依赖来源 | `pom.xml` 配置的阿里云公共仓库 |
| 数据库 | 测试使用 H2（应用默认测试配置）；真实 MySQL 8.4 冒烟见 [一期验证记录](../stage1/verification-record.md) |
| 网络 | GitHub 不可达，仓库已通过本地 `origin/main` 引用快进到上述 SHA，未重新从远端拉取 |

---

## 2. 构建与测试结果

```sh
mvn -f backend-procedural/pom.xml clean verify
```

| 指标 | 结果 |
| --- | --- |
| 构建结论 | **BUILD SUCCESS** |
| 测试用例 | **29 个，0 失败，0 错误，0 跳过** |
| 测试耗时 | 26.33 s |
| 总耗时 | 51.94 s |
| 打包产物 | `target/rbac-procedural-backend-0.1.0-SNAPSHOT.jar`（31.4 MB） |
| 测试类 | `com.municipal.rbac.procedural.Stage1ApiTest` |

### 覆盖率（JaCoCo）

| 维度 | 覆盖率 |
| --- | --- |
| 指令 | **87.1 %** |
| 行 | **88.7 %**（976 / 1100） |
| 分支 | **70.3 %**（336 / 478） |

关键类行覆盖：`UserMapper` 100 %、`DepartmentService` 100 %、`PortalService` 100 %、
`DataInitializer` 97.6 %、`AuthorizationService` 96.2 %、`UserService` 94.0 %、`RoleService` 86.7 %。

---

## 3. 八项重点复核逐项结果

| # | 复核项 | 对应测试方法 | 结论 |
| --- | --- | --- | --- |
| 1 | ALL / DEPT / SELF 范围 | `ledgerSelfScopeAndUnknownFieldProtection`、`managerReadAllEditDepartmentAndChildren`、`businessRoleUnionAndImmediateRevocation` | 通过 |
| 2 | 下级部门 | `managerReadAllEditDepartmentAndChildren`、`departmentUserFiltersCannotEscapeScope` | 通过 |
| 3 | 越权拒绝 | `allProtectedFamiliesRejectAnonymous`、`departmentUserFiltersCannotEscapeScope`、`managementRoleClassificationPreventsCustomPrivilegeEscalation` | 通过 |
| 4 | 权限变化立即生效 | `businessRoleUnionAndImmediateRevocation`、`roleCrudStatusDeleteAndBuiltinGuards`、`disableInvalidatesExistingSessionOnNextRequest` | 通过 |
| 5 | 整组替换原子性 | `failedReplacementRollsBackGrantsAndVersion` | 通过 |
| 6 | 旧版本 409 | `userRoleVersionConflictAndDuplicateValidation`、`simultaneousCasHasExactlyOneWinner` | 通过 |
| 7 | 角色删除 | `roleCrudStatusDeleteAndBuiltinGuards`、`concurrentAssignmentMakesUnconfirmedRoleDeleteRefuse` | 通过 |
| 8 | 管理员保护 | `selfAndUniqueAdminProtection`、`departmentAdminCannotGrantOrRemoveManagementRoles` | 通过 |

### 逐项说明

**① ALL / DEPT / SELF 范围**

SELF 身份（`SG000013`）请求台账只返回 **1 条**（自己的记录），修改他人记录返回 `403 OUT_OF_SCOPE`；
同一权限码被多个角色授予时**取范围并集**：仅角色 5 时 `hr:f046:read` 为 `SELF`，
追加角色 7 后 `hr:f046:edit` 为 `ALL`，随即可以编辑他人记录。

**② 下级部门**

部门经理（`DEMO000100`）可编辑**下级部门**员工（`/ledger/employees/7` 返回 200），
编辑**上级部门**员工返回 403；列表接口返回的用户其 `departmentId` 全部落在允许集合 `{10,11,12,13,14,15}` 内。

**③ 越权拒绝**

- 所有受保护接口在未登录时返回 401。
- 用 `?departmentId=3` 过滤**无法**越出数据范围（返回 `total = 0`）。
- 直接按 ID 访问范围外用户返回 403；**访问不存在的 ID 同样返回 403 而非 404**，避免通过错误码探测数据是否存在。
- 向范围外部门创建用户返回 403。
- 部门管理员不能授予或移除管理类角色，也不能修改角色权限。

**④ 权限变化立即生效**

移除用户全部角色后，**下一次**请求即返回 403；停用角色后同样立即生效；
停用账号后旧会话在下一次请求即失效，不需要等待会话超时。

**⑤ 整组替换原子性**

测试通过 mock 让 `replaceGrants` 先真实写入、再抛出异常，然后断言：
`sys_role.permission_version` **仍为 1**（未被递增），`sys_role_permission` 记录数为 **0**（已回滚）。
即部分写入不会留下中间状态。

**⑥ 旧版本 409**

- 用 `version = 1` 重复提交返回 `409`，且 `error.details.currentVersion = 2`，前端可据此刷新。
- 并发场景：两个线程通过 `CountDownLatch` 同时用 `version = 1` 提交不同角色集合，
  断言两个响应**恰好是 `[200, 409]`**，即**只有一个赢家**，不存在双写成功。

**⑦ 角色删除**

- 角色仍被用户引用时 `DELETE /roles/{id}` 返回 `409 ROLE_HAS_USERS`；
- 显式 `?confirm=true` 时返回 204，且被解除关联的用户角色版本号递增；
- 内置角色（如 `id=5`）拒绝删除，`id=1` 拒绝停用，均返回 409；
- 并发场景下，若角色在删除确认前被分配，删除请求会被拒绝而不是静默级联。

**⑧ 管理员保护**

内置管理员（`id=1`）不能停用、不能删除、不能修改自身角色，三者均返回 `409 PROTECTED_ACCOUNT`；
部门管理员尝试授予管理角色返回 409/403，无法通过自定义角色绕过管理权限分类。

---

## 4. 发现的缺陷

本轮复核**未发现需要修复的功能缺陷**，29 项测试在目标 SHA 上全部通过，八项重点复核均有对应断言覆盖。

未提交修复 PR 的原因：没有可复现的失败用例。若成员 6 在独立验收中发现问题，
将按 Issue 记录的 SHA、账号类型、复现步骤在本记录基础上补充。

---

## 5. 剩余限制

1. **测试数据库为 H2，不是真实 MySQL。** 方言差异、连接池行为与真实 MySQL 的一致性
   依赖 [一期验证记录](../stage1/verification-record.md) 中的真 MySQL 冒烟结果，本记录不重复主张。
2. **未连接用户服务器。** 按用户 2026-10-18 的要求，本轮只做仓库与本机验证；外网访问、
   服务器重启持久化仍需部署后由成员 6 独立验收。
3. **分支覆盖率 70.3 %**，部分异常分支（如极端输入、部分错误码组合）未被断言覆盖。
4. **未做性能测量。** 数据库纯鉴权 ≤10 ms、列表与授权保存 ≤1 s 的目标属成员 1 的记录范围，
   不在本次后端复核内容中。
5. 本次只验证 `backend-procedural/`；`backend-oo` 与后续阶段功能不计入一期完成度。

---

## 6. 结论

在源码 `29543856a4d429ebf3379a44e22d7b7695af6771` 上，
`backend-procedural/` 的构建、29 项集成测试与八项重点复核**全部通过**，
未发现需要修复的缺陷。

**本结论仅适用于该 SHA 与上述环境**；合并新提交后需在新 SHA 上重新执行本记录中的命令。
