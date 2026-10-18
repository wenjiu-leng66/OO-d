# 第一阶段 API 草案差异清单

状态：2026-10-15，成员 1 整理，供成员 6 修改 OpenAPI 时参考。本文只列差异和建议，不改动成员 6 的分支和文件；采纳与否、具体写法由成员 6 决定。

## 对照来源

| 来源 | 位置 | 提交 |
| --- | --- | --- |
| 成员 6 API 草案 | `docs/member6-rbac0-api` 分支的 `api/openapi.yaml`、`docs/testing/member6-rbac0-api-test-cases.md`、`docs/standards/api-contract.md` | `1860a89`（10/11） |
| 需求规格说明书 v1.1 | `docs/rbac-requirements` 分支（PR #14）的 `docs/requirements/req-spec.md`，下文简称“需求” | `fb289f8`（10/15） |
| 第一阶段界面原型 | `claude/rbac0-ui-prototype-19a12f` 分支（PR #15）的 `frontend/src/api/mock.js`、`docs/design/stage1-ui-prototype.md`，下文简称“原型” | `d847f06`（10/15） |
| 数据库 | `feat/member1-rbac0-database` 分支（PR #13）的 `V001__create_core_tables.sql`、`data-dictionary.md`、`system_codes.csv`，下文简称“表结构” | `1e000f7`（10/15） |

草案写于需求规格之前，下面列出的大部分差异都是需求定稿后才出现的，草案本身没有错。原型的模拟后端已经按需求实现了每个接口的权限码、数据范围、版本冲突和错误码，可以当作“需求落到接口上是什么样”的参考实现；字段名、分页参数等细节以 OpenAPI 为准，原型随后对齐。

## 1. 结论摘要

第一阶段共 41 条需求（附录 A）。其中 6 条 IMPORT 是离线脚本，不需要接口；其余 35 条对照结果如下：

| 结果 | 条数 | 需求编号 |
| --- | --- | --- |
| 已覆盖，只需小改 | 4 | LOGIN-03、AUTHZ-01、AUTHZ-02、CAT-02 |
| 部分覆盖，需要补字段、规则或错误码 | 13 | LOGIN-01、LOGIN-02、PORTAL-01、PORTAL-03、USER-01、USER-02、USER-04、USER-07、DEPT-01、ROLE-01、CAT-01、GRANT-01、GRANT-03 |
| 与需求冲突 | 4 | GRANT-02（授权缺数据范围）、GRANT-04（范围合并“待确认”）、GRANT-05（生效时机“待确认”）、CAT-03（缺 `rbac:*` 映射表，授权码口径不同） |
| 缺少接口 | 13 | LOGIN-04、LOGIN-05、PORTAL-02、PORTAL-04、PORTAL-05、USER-03、USER-05、USER-06、DEPT-02、ROLE-02、ROLE-03、ROLE-04、AUTHZ-03（范围规则未写进契约） |
| 无需接口，只需在契约中说明 | 1 | PORTAL-06 |

最需要先改的 8 处：

1. **角色授权没有数据范围**：`PUT /roles/{roleId}/permissions` 只提交 `permissionCodes`，需求 GRANT-02 要求每条授权带全部 / 本部门 / 本人。请求体要改为 `grants: [{code, scope}]`。
2. **删除 `accountType`**：需求第 9 节和第 8 章已定“账号类别得到依据前不做”，草案的查询参数、`UserSummary`、`CreateUserRequest` 都还有。
3. **缺 13 个接口**：修改密码、员工台账列表与编辑、功能点占位页、编辑用户、重置密码、删除用户、部门人员、角色新增 / 编辑 / 启停用 / 删除、角色下的用户。
4. **首次登录改密**（LOGIN-05）：`CurrentUser` 缺 `mustChangePassword`，契约里也没有“未改密时其他接口一律 403 `PASSWORD_CHANGE_REQUIRED`”的规则。
5. **`rbac:*` 映射表**（CAT-03）：需求要求在接口设计阶段写入契约；草案缺 `rbac:user:delete`、`rbac:role:create/update/delete` 四个码，也没有说明 `rbac:*` 码怎样由清单系统 18 的授权得出。
6. **枚举值与表结构不一致**：数据范围草案为 `all/department/self`，表结构和原型为 `ALL/DEPT/SELF`；部门类型草案 3 种，表结构 5 种；用户状态三方各不相同。
7. **登录失败的状态码**：草案对停用账号返回 403，需求 LOGIN-01 要求四种失败提示完全相同，应统一为 401。
8. **把“待确认”改成结论**：数据范围的含义与合并规则（GRANT-04）、授权变更生效时机（GRANT-05）、越权目标返回 403 还是 404、停用后旧会话返回 401 还是 403，需求都已定稿，测试用例文档第 5 节的 7 个问题可以据此关闭（见第 8 节）。

## 2. 通用约定

以下各条影响多个接口，先统一，后面逐条对照时不再重复。

| 编号 | 事项 | 草案现状 | 建议修改 | 依据 |
| --- | --- | --- | --- | --- |
| G-01 | 数据范围枚举 | `PermissionScope.scope`、`Permission.scopeOptions` 为 `all / department / self`，并注明“待老师和团队确认” | 改为 `ALL / DEPT / SELF`，删除“待确认”说明，写明含义：ALL 全部；DEPT 用户所在部门及其全部下级部门；SELF 目标记录属于本人 | 需求术语表、GRANT-04、AUTHZ-03；表结构 `ck_rp_scope`；原型 `scopeOptions` |
| G-02 | 用户状态枚举 | `UserStatus` 为 `active / inactive` | 三方须统一一种。建议 `ENABLED / DISABLED`（原型已用，与 `ALL/DEPT/SELF` 同为大写风格）；后端把库里的 1 / 0 转成枚举 | 表结构 `sys_user.status` 1/0；原型 `ENABLED / DISABLED` |
| G-03 | ID 类型 | 全部 `string` | 保留 `string`，在契约开头写明“库内为整数自增，接口统一输出为字符串”，避免前端做数值运算 | 数据字典“主键”一节 |
| G-04 | 分页 | `page` 从 1 起，`pageSize` 默认 20、最大 100；响应 `items / page / pageSize / total` | 保留草案写法。原型用的 `size` 由原型改成 `pageSize` | 需求 USER-01、CAT-01 要求服务端分页 |
| G-05 | 错误包络 | `{error: {code, message, requestId, details}}` | 保留。补一张业务错误码表（第 6 节），每个接口的 4xx 响应引用表中的码 | 需求 6.2 “带稳定的业务错误码” |
| G-06 | 400 与 422 | 只用 400 | 第一阶段只用 400，在契约中写明不使用 422，免得两套后端各选一种 | 需求 6.2 允许 400 / 422 二选一；原型全部用 400 |
| G-07 | 越权目标返回什么 | `NotFound` 写“或按最终防枚举策略不可见”；测试用例 API-007 写 403 或 404 待定 | 有数据范围的对象（用户、员工台账、部门）：范围外与不存在统一返回 403 `OUT_OF_SCOPE`，响应中不带目标的任何字段。没有数据范围的对象（角色）：不存在返回 404 | 需求 PORTAL-05、AUTHZ-03、UC-08 “不返回该员工的任何信息”；原型 `userInScope` |
| G-08 | 首次登录拦截 | 无 | `mustChangePassword = true` 的会话只能调用 `GET /me`、`PUT /me/password`、`POST /auth/logout`，其余受保护接口返回 403 `PASSWORD_CHANGE_REQUIRED` | 需求 LOGIN-05、NFR-SEC-03；原型 `sessionUser` |
| G-09 | 授权变更生效时机 | `/me/permissions` 描述写“待后端联调确认” | 写明：分配 / 撤销角色、修改授权、停用角色或账号后，该用户的**下一次请求**即按新状态鉴权，不需要重新登录 | 需求 GRANT-05、USER-04、ROLE-02 |
| G-10 | 停用账号的旧会话 | 测试用例 API-015 写 401 或 403 待定 | 返回 401 `UNAUTHENTICATED` | 需求 USER-04 验收标准 |
| G-11 | 权限码格式 | `PermissionCode` 正则只要求三段小写 | 收紧为两种形式：清单权限 `^[a-z0-9]{2,16}:f[0-9]{3}:(read\|create\|edit\|approve\|export\|delete)$`，管理后台 `^rbac:[a-z]+:[a-z-]+$`；示例同时给 `hr:f046:edit` 和 `rbac:user:read` | 需求 CAT-02；表结构 `app_system.code`（2–16 位小写字母或数字）、`app_function.code`（f 加三位编号）、`sys_action.code` |
| G-12 | 每个接口的权限码 | 部分接口有 `x-required-permission` | 每个受保护接口都写明权限码，以及按哪个码的数据范围裁剪目标；只要求登录的写 `x-required-permission: none`，便于审查默认拒绝 | 需求 AUTHZ-01、AUTHZ-02 |
| G-13 | 账号类别 | `AccountType` 枚举、查询参数、`UserSummary.accountType`（必填）、`CreateUserRequest.accountType`（必填） | 全部删除 | 需求第 8 章、第 9 节；数据字典 Q11；原型已删除 |
| G-14 | 字段长度 | 多数字符串没有 `maxLength` | 按表结构补上：工号 32、姓名 32、岗位 32、电话 20、邮箱 64、角色编码 32、角色名称 32、角色说明 255 | 表结构 V001 |

## 3. 逐条对照（第一阶段 41 条）

“草案现状”一列：**已覆盖**、**部分**（有接口但缺字段或规则）、**冲突**（与需求结论相反）、**缺少**（没有接口）、**无需接口**。接口路径省略前缀 `/api/v1`。

### 3.1 登录与会话

| 需求 | 草案现状 | 建议修改 | 依据 |
| --- | --- | --- | --- |
| LOGIN-01 用工号或手机号登录；四种失败提示相同 | **部分**。`POST /auth/login` 的 `account` 已注明“员工号或手机号”；但停用账号返回 403，与密码错误不同 | 删除 403 响应。用户不存在、密码错误、停用、锁定一律 401，同一个错误码和提示“用户名或密码错误”。错误码草案为 `INVALID_CREDENTIALS`、原型为 `BAD_CREDENTIALS`，任选其一写进错误码表 | 需求 LOGIN-01、NFR-SEC-03；原型 `login` |
| LOGIN-02 登录后激活全部已分配且启用的角色 | **部分**。`LoginResponse` 只有 `user` | `LoginResponse` 与 `GET /me` 返回同一结构（见 LOGIN-05、PORTAL-03），包含 `roles`（含 `enabled`）。说明第一阶段会话激活全部已分配角色，停用的角色不贡献权限 | 需求 LOGIN-02、ROLE-02；原型 `me()` |
| LOGIN-03 退出后会话失效 | **已覆盖**。`POST /auth/logout` 返回 204 | 写明重复退出也返回 204（幂等），退出后用原会话访问受保护接口返回 401 | 需求 LOGIN-03 |
| LOGIN-04 修改本人密码，校验旧密码 | **缺少** | 新增 `PUT /me/password`，请求 `{oldPassword, newPassword}`，只要求登录。旧密码错误 400 `BAD_OLD_PASSWORD`；新密码长度不符或与旧密码相同 400 `VALIDATION`（原型为 8–64 位）。成功后 `mustChangePassword` 置 false，返回 `GET /me` 的结构或 204 | 需求 LOGIN-04、UC-01；原型 `changePassword` |
| LOGIN-05 首次登录及重置后须先改密 | **缺少** | `CurrentUser` 增加必填 `mustChangePassword: boolean`；按 G-08 拦截其他接口。未改密时 `GET /me` 返回的权限为空 | 需求 LOGIN-05、第 9 节；表结构 `sys_user.must_change_password`；原型 `me()`、`sessionUser` |

### 3.2 门户

| 需求 | 草案现状 | 建议修改 | 依据 |
| --- | --- | --- | --- |
| PORTAL-01 菜单只显示有“查看”权限的系统 > 模块 > 功能点 | **部分**。`GET /me/permissions` 只返回权限码，前端拿不到系统、模块、功能点名称 | `GET /me/permissions` 的每条权限带 `systemCode、systemName、moduleName、functionCode、functionName、action`（与视图 `v_user_permission` 的列一致），前端按 `action = read` 生成菜单。另一种做法是新增 `GET /me/menu`，二选一写进契约 | 需求 PORTAL-01；表结构视图 `v_user_permission` |
| PORTAL-02 占位页：功能点名称、描述、6 个按钮可用与否及数据范围 | **缺少** | 新增 `GET /portal/functions/{functionCode}`（如 `f056`），要求该功能点的 `{系统码}:{functionCode}:read`，无权返回 403（满足“直接输入无权页面的地址被拒绝”）。返回 `functionCode、name、description、systemName、moduleName、actions: [{action, granted, scope}]`，`scope` 无权时为 null | 需求 PORTAL-01、PORTAL-02；表结构 `app_function.description` |
| PORTAL-03 我的权限：全部有效权限及每条来自哪个角色 | **部分**。`MyPermissions` 有 `roleIds、permissionCodes、scopes、version`，没有来源角色；`version` 含义不明 | 改为 `grants: [{code, scope, sources: [{roleId, roleName, scope}]}]`，`rbac:*` 码另带 `via`（由哪条 `sys:fNNN` 授权映射而来）。删除 `version` 或写明含义。条数须与后端鉴权使用的有效权限一致 | 需求 PORTAL-03；原型 `computeGrants`、`rbac0-policy.js` 的 `sources` |
| PORTAL-04 员工台账列表，按查看范围显示 | **缺少** | 新增 `GET /employees`，要求 `hr:f046:read`，按其范围裁剪。参数 `keyword、page、pageSize`。每行 `id、username、name、departmentName、position、phone、email、editable`；响应另带 `readScope`、`editScope`，供前端显示范围和置灰按钮 | 需求 PORTAL-04、UC-08；原型 `listEmployees` |
| PORTAL-05 员工台账编辑：只能改电话、邮箱，按编辑范围检查 | **缺少** | 新增 `PATCH /employees/{userId}`，请求 `{phone?, email?}`，`additionalProperties: false`。要求 `hr:f046:edit`；含其他字段 400 `FIELD_NOT_EDITABLE`；目标在范围外或不存在 403 `OUT_OF_SCOPE`，不返回目标字段；手机号重复 409 `DUPLICATE_PHONE`；格式错误 400 `VALIDATION` | 需求 PORTAL-05、AUTHZ-03、UC-08（“修改电话、邮箱以外的字段：400”）；原型 `updateEmployeeContact` |
| PORTAL-06 新增、删除、审批、导出按钮按权限显示，点击为占位 | **无需接口** | 在契约中写明：这四个按钮只按 `hr:f046:create/delete/approve/export` 显示，第一阶段没有对应接口 | 需求 PORTAL-06 |

### 3.3 用户管理

| 需求 | 草案现状 | 建议修改 | 依据 |
| --- | --- | --- | --- |
| USER-01 列表：按部门（含下级）、工号、姓名、角色筛选，分页，只列管理范围内 | **部分**。`GET /users` 有 `keyword、departmentId`，并注明按会话范围裁剪；多了 `accountType` | 删除 `accountType`；增加 `roleId`（建议再加 `status`）；写明 `departmentId` 包含全部下级部门；筛选指向范围外部门时返回空结果而不是 403。范围取 `rbac:user:read` 的数据范围（超级管理员 ALL，部门管理员 DEPT）。`UserSummary` 删除 `accountType`，增加 `phone、email、mustChangePassword、roleIds、roleNames`；`branch` 表中没有，可删除或注明由部门推出 | 需求 USER-01、UC-02；原型 `listUsers`、`publicUser` |
| USER-02 新增：工号唯一，手机号可空、不空时唯一，使用初始密码 | **部分**。`POST /users` 存在；`phone` 必填且 `minLength: 1`，`accountType`、`initialPassword`、`roleIds` 必填，缺 `email` | 必填只留 `employeeNo（或 username，见第 4 节）、name、departmentId`；`phone`、`email`、`position` 可选。删除 `accountType`。删除 `initialPassword`，改为后端使用部署时配置的初始密码，与重置密码一致，新账号 `mustChangePassword = true`。删除 `roleIds`，角色走 `PUT /users/{id}/roles`，免得绕过分配角色的权限和版本号检查。部门须在 `rbac:user:create` 的范围内，否则 403 `OUT_OF_SCOPE`；工号重复 409 `DUPLICATE_USERNAME`，手机号重复 409 `DUPLICATE_PHONE` | 需求 USER-02、USER-05、IMPORT-06（初始密码不进仓库）；原型 `createUser` |
| USER-03 编辑基本信息（姓名、部门、岗位、电话、邮箱） | **缺少** | 新增 `PUT /users/{userId}`，请求上述 5 个字段。要求 `rbac:user:update`；目标用户和新部门都须在范围内，否则 403 `OUT_OF_SCOPE`；手机号重复 409 | 需求 USER-03；原型 `updateUser` |
| USER-04 启用、停用；停用后下一次请求 401 | **部分**。`PATCH /users/{userId}/status` 存在，要求 `rbac:user:update`，有 409 | 状态枚举按 G-02 统一；按 G-10 写明停用后旧会话下一次请求 401；保护规则见 USER-07 | 需求 USER-04；原型 `setUserStatus` |
| USER-05 重置密码为初始密码，之后须改密 | **缺少** | 新增 `POST /users/{userId}/password-reset`，要求 `rbac:user:update`（原型做法，也可另设码，须写进映射表），目标在范围内；成功后 `mustChangePassword = true`。原型还禁止重置自己和 `admin` 的密码，需求没有写，由成员 6 决定是否采纳 | 需求 USER-05、LOGIN-05；原型 `resetPassword` |
| USER-06 删除用户：角色关系一并删除，负责人置空 | **缺少** | 新增 `DELETE /users/{userId}`，要求 `rbac:user:delete`，目标在范围内，返回 204。表结构已用外键级联删除用户-角色、部门负责人 `ON DELETE SET NULL`，按物理删除实现即可 | 需求 USER-06；表结构 `fk_ur_user`、`fk_dept_manager`；原型 `deleteUser`；PR #12 已有 `DELETE /users/{id}` |
| USER-07 不能停用、删除自己或改自己的角色；唯一超级管理员不能停用、删除、改角色 | **部分**。只在状态接口写了 409 “不允许停用当前账号或唯一超级管理员” | 在停用、删除、`PUT /users/{id}/roles` 三处都写明：违反时 409 `PROTECTED_ACCOUNT`，`message` 说明原因 | 需求 USER-07、6.2（违反保护规则 409）；原型 `protectAccount` |

### 3.4 组织架构

| 需求 | 草案现状 | 建议修改 | 依据 |
| --- | --- | --- | --- |
| DEPT-01 部门树（只读）：类型、负责人、上级、员工数 | **部分**。`GET /departments` 返回树，有 `managerUserId、managerName、parentId、parentName、employeeCount`；`type` 只有 `company / branch / department` 三种 | `type` 改为表结构的 5 种：`HQ、HQ_DEPT、BRANCH、BRANCH_DEPT、PROJECT`。写明范围：`rbac:department:read` 为 ALL 时返回整棵树，为 DEPT 时只返回本部门子树。`managerName` 取负责人用户的姓名，不取原始列 `manager_name`。可增加 `totalCount`（含下级人数） | 需求 DEPT-01；表结构 `ck_dept_type`、`manager_user_id`；原型 `listDepartments` |
| DEPT-02 查看某部门及全部下级部门的人员 | **缺少** | 新增 `GET /departments/{departmentId}/members`（建议分页），要求 `rbac:department:read`，部门须在范围内，否则 403 `OUT_OF_SCOPE`。也可以写明复用 `GET /users?departmentId=`，但那样需要 `rbac:user:read`，公司领导等只有部门查看权的人会看不到 | 需求 DEPT-02；原型 `listDepartmentMembers` |

### 3.5 角色管理

| 需求 | 草案现状 | 建议修改 | 依据 |
| --- | --- | --- | --- |
| ROLE-01 角色列表、新增、编辑，编码唯一 | **部分**。只有 `GET /roles`；`RoleSummary.code` 可选并注明“是否必填待确认” | `code` 改为必填。`RoleSummary` 增加 `builtin`、`adminRole`（是否管理类角色，后端按规则计算，供部门管理员分配时置灰）、`userCount`、`grantCount`；`version` 改名 `permissionVersion` 以免与用户的角色版本混淆；`description` 改为可空。新增 `POST /roles`（`rbac:role:create`，编码重复 409 `DUPLICATE_ROLE_CODE`）和 `PATCH /roles/{roleId}`（`rbac:role:update`，只改名称和说明，编码不可改） | 需求 ROLE-01；表结构 `sys_role`；数据字典“管理类角色”；原型 `listRoles`、`createRole`、`updateRole` |
| ROLE-02 启用、停用；停用后权限对所有人不生效 | **缺少** | 新增 `PATCH /roles/{roleId}/status`（或在 `PATCH /roles/{roleId}` 中允许 `enabled`），要求 `rbac:role:update`；停用超级管理员角色 409 `PROTECTED_ROLE`；按 G-09 下一次请求生效 | 需求 ROLE-02、ROLE-03；原型 `setRoleEnabled` |
| ROLE-03 内置角色不能删；超级管理员角色不能停用、不能改授权；自建角色删除时有用户需二次确认 | **缺少** | 新增 `DELETE /roles/{roleId}?confirm=true`，要求 `rbac:role:delete`。内置角色 409 `PROTECTED_ROLE`；有用户且未带 `confirm=true` 时 409 `ROLE_HAS_USERS`，`details.userCount` 给出人数；删除后受影响用户的 `roleVersion` 加 1。`PUT /roles/{id}/permissions` 对超级管理员角色返回 409 `PROTECTED_ROLE` | 需求 ROLE-03、UC-04；原型 `deleteRole`、`saveRoleGrants` |
| ROLE-04 查看某角色下有哪些用户 | **缺少** | 新增 `GET /roles/{roleId}/users`，要求 `rbac:role:read`；人数与用户-角色记录数一致。需求没有要求按范围裁剪，原型也未裁剪；若成员 6 认为部门管理员只应看到范围内的用户，在契约中写明 | 需求 ROLE-04；原型 `listRoleUsers` |

### 3.6 权限目录

| 需求 | 草案现状 | 建议修改 | 依据 |
| --- | --- | --- | --- |
| CAT-01 资源目录：关键词、系统、模块筛选和分页；18 / 115 / 359 / 2154 | **部分**。`GET /permissions`（按单条权限分页，筛选 `systemId、moduleId、keyword`）和 `GET /resources`（按 `parentId` 逐层展开，无关键词） | 角色授权页和资源目录页都按“功能点一行、6 个操作”展示。建议新增 `GET /catalog/functions`：参数 `systemCode、moduleId、keyword、page、pageSize`，每行 `functionCode、name、description、systemCode、systemName、moduleId、moduleName、permissions: [{action, code}]`；响应带 `stats: {systems, modules, functions, permissions}`。系统筛选用 `systemCode` 而不是 `systemId`。`/permissions`、`/resources` 可保留，也可删掉以减少后端工作量 | 需求 CAT-01、UC-04（翻页不丢勾选）；原型 `listCatalog` |
| CAT-02 权限码 `系统码:资源:动作`，不随显示名称变化 | **已覆盖**（格式偏宽） | 按 G-11 收紧正则；`Permission.action` 加 6 个枚举值；`resourceCode / resourceName` 改名 `functionCode / functionName`，与表结构一致；删除 `scopeOptions`（每条权限都可选三种范围，表中也没有这一列） | 需求 CAT-02；表结构 `sys_permission.code`、`sys_action` |
| CAT-03 清单“系统管理后台”已实现模块的授权映射为 `rbac:*`，映射表写入契约 | **冲突**。草案只零散使用 8 个 `rbac:*` 码，没有映射表；`PermissionCode` 示例为 `rbac:user:read`，暗示角色授权直接提交 `rbac:*` 码 | 把第 5 节的映射表写进契约，并写明三点：①角色授权在目录上勾选的是 `sys:f339:edit` 这类清单权限码，`rbac:*` 不能直接授予，提交时返回 400 `UNKNOWN_PERMISSION`；②`rbac:*` 是有效权限计算时由清单码映射出来的，数据范围随原授权；③补上 `rbac:user:delete`、`rbac:role:create`、`rbac:role:update`、`rbac:role:delete` | 需求 CAT-03、2.1；数据字典“已定与待统一”第 1 行；原型 `adminMapping`、`computeGrants` |

### 3.7 授权

| 需求 | 草案现状 | 建议修改 | 依据 |
| --- | --- | --- | --- |
| GRANT-01 用户角色整组替换，带版本号，冲突 409 且无部分写入 | **部分**。`GET/PUT /users/{userId}/roles` 已按整组替换、版本号、409 设计，与需求一致 | 补充：要求 `rbac:user:assign-role`，目标在该码范围内；超级管理员角色不能授给任何账号（403）；改自己或 `admin` 的角色 409 `PROTECTED_ACCOUNT`；409 `details` 带 `currentVersion`。`UserRoleAssignment` 可增加每个角色的 `assignable`，供前端置灰 | 需求 GRANT-01、USER-07、UC-03；表结构 `sys_user.role_version`；原型 `assignRoles` |
| GRANT-02 角色授权：每条带数据范围；非查看操作自动带上查看；整组替换带版本号 | **冲突**。`RolePermissionAssignment`、`ReplaceRolePermissionsRequest` 只有 `permissionCodes`，无法保存数据范围 | 请求改为 `{grants: [{code, scope}], version}`，响应 `{roleId, grants, version, readOnly}`。后端校验：未知码 400 `UNKNOWN_PERMISSION`；范围非法 400 `VALIDATION`；勾了某功能点的非查看操作却没勾查看 400 `READ_REQUIRED`（前端自动勾选，后端兜底）；版本不符 409；中途失败整体回滚 | 需求 GRANT-02、UC-04；表结构 `sys_role_permission.data_scope`、`sys_role.permission_version`；原型 `getRoleGrants`、`saveRoleGrants` |
| GRANT-03 部门管理员只能给本部门及下级分配业务角色，不能分配管理类角色，不能改角色授权 | **部分**。`replaceRolePermissions` 描述“后端还必须检查操作者是否允许授出所选权限”，规则未展开 | 写明三条，均返回 403：目标用户在范围外；新增或撤销的角色中含管理类角色（`ADMIN`、`DEPT_ADMIN` 及任何会映射出 `rbac:*` 的角色）；调用 `PUT /roles/{id}/permissions`（部门管理员没有 `rbac:role:grant`）。同时确认部门管理员的授权（原型提议见第 5 节末） | 需求 GRANT-03、2.2；数据字典 `DEPT_ADMIN` 一行；原型 `assignRoles` |
| GRANT-04 多角色取并集，同码取最大范围 | **冲突**。`PermissionScope` 写“范围集合及合并规则仍待老师和团队确认” | 删除“待确认”，写明：并集；同一权限码取 ALL > DEPT > SELF 中最大的；不同权限码互不影响 | 需求 GRANT-04；表结构视图 `v_user_permission` 的 `MAX(FIELD(...))`；原型 `widerScope` |
| GRANT-05 授权变更立即生效 | **冲突**。`/me/permissions` 写生效时机“待后端联调确认”；测试用例第 5 节第 4 问同样未定 | 按 G-09 写明下一次请求生效；前端每次切换页面重新拉取 `/me` | 需求 GRANT-05；原型文档第 4 节 |

### 3.8 鉴权

| 需求 | 草案现状 | 建议修改 | 依据 |
| --- | --- | --- | --- |
| AUTHZ-01 认证 → 权限码 → 数据范围 → 目标对象；401 / 403 | **已覆盖**。`Unauthenticated`、`Forbidden` 已定义，描述中已有不信任前端传入的身份 | 在 `info.description` 写明检查顺序；`Forbidden` 的错误码细分为 `FORBIDDEN`（缺权限码）、`OUT_OF_SCOPE`（目标在范围外）、`PASSWORD_CHANGE_REQUIRED` | 需求 AUTHZ-01；原型 `authorize`、`userInScope` |
| AUTHZ-02 默认拒绝 | **已覆盖** | 按 G-12 让每个接口都声明权限码，审查时能看出没有漏网的接口 | 需求 AUTHZ-02 |
| AUTHZ-03 数据范围检查，改 URL 或请求体 ID 不能绕过 | **缺少**（规则未写进契约） | 写明三种范围的判断：SELF = 目标用户 ID 等于本人；DEPT = 目标所属部门在本人部门子树内；ALL 不限。用户管理类接口的“目标所属部门”即目标用户的部门。越权按 G-07 返回 | 需求 AUTHZ-03、术语表；原型 `covers`、`isWithinDepartment` |

### 3.9 数据导入

| 需求 | 草案现状 | 建议修改 | 依据 |
| --- | --- | --- | --- |
| IMPORT-01 ~ 06 | **无需接口**（离线脚本，需求第 8 章“网页上传 Excel”在范围外） | 契约只需注意三点：部门、资源目录没有增删改接口；ID 由导入决定，接口按字符串输出；接口示例中不出现真实姓名、电话或初始密码 | 需求 3.9、第 8 章、IMPORT-06 |

## 4. 字段与表结构对照

表结构以 PR #13 为准（需求第 9 节“以成员 1 维护的迁移为准，两套后端共用”）。“建议”列中的字段名只是推荐，成员 6 决定后原型和两套后端跟着改。

### 4.1 用户

| 表 `sys_user` | 草案 | 原型 | 建议 |
| --- | --- | --- | --- |
| `id` INT | `id` string | `id` string | `id` string（G-03） |
| `username` VARCHAR(32) 工号 | `employeeNo` | `username` | 二选一。`username` 与表、原型、登录逻辑一致，改动最少；若保留 `employeeNo`，原型改名。补 `maxLength: 32` |
| `real_name` VARCHAR(32) | `name` | `name` | `name`，补长度 |
| `dept_id` INT | `departmentId` | `departmentId` | 一致 |
| —（由部门推出） | `departmentName`、`branch` | `departmentName`、`departmentPath` | `departmentName` 保留；`branch` 删除或改为 `departmentPath` |
| `position` VARCHAR(32) 可空 | `position` 必填 | `position` | 改为可空 |
| `phone` VARCHAR(20) 可空、非空唯一 | 只在 `UserDetail`；创建时必填 | `phone` | 列表与详情都返回；创建、编辑时可空 |
| `email` VARCHAR(64) 可空 | 无 | `email` | 增加 |
| `status` TINYINT 1/0 | `status` active/inactive | `status` ENABLED/DISABLED | 见 G-02 |
| `must_change_password` | 无 | `mustChangePassword` | 增加（LOGIN-05） |
| `role_version` | `UserRoleAssignment.version` | `roleVersion` | 角色接口中叫 `version` 即可；若在 `UserSummary` 中返回，叫 `roleVersion` |
| 无 | `accountType` | 已删除 | 删除（G-13） |
| `gender`、`last_login_at` | 无 | 无 | 第一阶段需求未用到，不必加 |
| `password_hash` | 不返回 | 不返回 | 保持不返回 |

### 4.2 部门

| 表 `sys_dept` | 草案 `Department` | 原型 | 建议 |
| --- | --- | --- | --- |
| `name`、`short_name` | `name` | `name` | 可增加 `shortName` 用于树节点显示 |
| `dept_type` 5 种 | `type` 3 种 | `type` 5 种 + `typeLabel` | 改为 5 种（DEPT-01） |
| `manager_user_id` | `managerUserId` | `managerUsername` | 一致；原型可补 `managerUserId` |
| `manager_name`（原始数据，仅核对） | `managerName` | `managerName` | 取负责人用户的 `real_name`，不取原始列 |
| `parent_id` | `parentId`、`parentName` | `parentName` | 一致 |
| `level` | 无 | 无 | 可不加 |
| — | `employeeCount` | `employeeCount`、`totalCount` | 建议同时提供本部门人数和含下级人数 |

### 4.3 角色

| 表 `sys_role` | 草案 `RoleSummary` | 原型 | 建议 |
| --- | --- | --- | --- |
| `code` VARCHAR(32) 唯一 | `code` 可选 | `code` | 必填 |
| `name` VARCHAR(32) | `name` | `name` | 补长度 |
| `description` 可空 | 必填 | `description` | 可空 |
| `status` 1/0 | `enabled` boolean | `enabled` boolean | 保留 `enabled` |
| `builtin` | 无 | `builtin` | 增加（ROLE-03） |
| — | 无 | `kind`（admin / business） | 增加 `adminRole` boolean，后端按数据字典的规则计算，不入库 |
| `permission_version` | `version` | `permissionVersion` | 改名 `permissionVersion` |

### 4.4 权限与授权

| 表 | 草案 | 原型 | 建议 |
| --- | --- | --- | --- |
| `sys_permission.code` | `PermissionCode` | `code` | 一致；正则见 G-11 |
| `app_system.code` | `systemId` | `systemCode` | 筛选和展示都用 `systemCode` |
| `app_function.code`、`name`、`description` | `resourceCode`、`resourceName`，无描述 | `id`（即 fNNN）、`name`、`description` | 改名 `functionCode`、`functionName`，增加 `description` |
| `sys_action.code` | `action` 无枚举 | `action` | 加 6 个枚举值 |
| `sys_role_permission.data_scope` | 无（授权只有码） | `scope` | 授权每条带 `scope`（GRANT-02） |
| 无 | `scopeOptions` | 无 | 删除 |

## 5. `rbac:*` 映射表（建议写入契约）

原型提出、需求 CAT-03 要求写入契约的映射。管理后台的接口按右列的码鉴权，数据范围随清单码的授权：

| 清单功能点（系统 `sys`） | 动作 | 映射为 | 用于接口 |
| --- | --- | --- | --- |
| f339 用户管理 | read | `rbac:user:read` | `GET /users`、`GET /users/{id}` |
| | create | `rbac:user:create` | `POST /users` |
| | edit | `rbac:user:update`、`rbac:user:assign-role` | `PUT /users/{id}`、`PATCH /users/{id}/status`、`POST /users/{id}/password-reset`、`PUT /users/{id}/roles` |
| | delete | `rbac:user:delete` | `DELETE /users/{id}` |
| f340 角色管理 | read | `rbac:role:read` | `GET /roles`、`GET /roles/{id}/users`、`GET /roles/{id}/permissions`（另需 `rbac:permission:read`） |
| | create | `rbac:role:create` | `POST /roles` |
| | edit | `rbac:role:update` | `PATCH /roles/{id}`、`PATCH /roles/{id}/status` |
| | delete | `rbac:role:delete` | `DELETE /roles/{id}` |
| f341 权限配置 | read | `rbac:permission:read` | `GET /catalog/functions` 等目录接口 |
| | edit | `rbac:role:grant` | `PUT /roles/{id}/permissions` |
| f342 组织架构管理 | read | `rbac:department:read` | `GET /departments`、`GET /departments/{id}/members` |
| f348、f349 登录 / 操作日志 | read、export | `rbac:log:read`、`rbac:log:export` | 阶段三 |

说明：

- 草案已有 `rbac:user:read/create/update/assign-role`、`rbac:role:read/grant`、`rbac:permission:read`、`rbac:department:read`，新增的是 `rbac:user:delete`、`rbac:role:create/update/delete` 和阶段三的 `rbac:log:*`。
- 测试用例文档第 5 节第 2 问（分配角色用 `assign-role` 还是复用 `update`）：按上表两个码都保留，都来自 f339 的“编辑”，现阶段授予时一起出现，以后需要拆开时不用改接口。
- `rbac:*` 码不在 `sys_permission` 表中，由后端在计算有效权限时映射（与视图 `v_user_permission` 一起放在 NFR-MAINT-02 要求的“一处”）。若成员 6 认为应入库，需与成员 1 商量增加一份迁移。
- 部门管理员（`DEPT_ADMIN`）在 PR #13 中没有授权。原型提议：f339 查看 / 新增 / 编辑 / 删除 = DEPT，f340 查看 = ALL，f342 查看 = DEPT。请成员 6 确认后写入契约，成员 1 据此补种子数据。

## 6. 错误码表（建议）

草案目前只定义了 `UNAUTHENTICATED`、`FORBIDDEN`、`VERSION_CONFLICT`、`INVALID_CREDENTIALS`。原型按需求实际用到的码如下，建议写入契约：

| HTTP | 错误码 | 场景 | 需求 |
| --- | --- | --- | --- |
| 401 | `BAD_CREDENTIALS`（或草案的 `INVALID_CREDENTIALS`，二选一） | 登录失败，四种原因相同 | LOGIN-01 |
| 401 | `UNAUTHENTICATED` | 未登录、已退出、账号被停用后的下一次请求 | LOGIN-03、USER-04 |
| 403 | `PASSWORD_CHANGE_REQUIRED` | 未改初始密码就调用其他接口 | LOGIN-05 |
| 403 | `FORBIDDEN` | 缺少权限码；部门管理员分配管理类角色；把超级管理员角色授给他人 | AUTHZ-01、GRANT-03 |
| 403 | `OUT_OF_SCOPE` | 目标在数据范围外或不存在 | AUTHZ-03、PORTAL-05 |
| 400 | `VALIDATION` | 参数格式、长度、枚举不合法 | 6.2 |
| 400 | `BAD_OLD_PASSWORD` | 改密时旧密码错误 | LOGIN-04 |
| 400 | `FIELD_NOT_EDITABLE` | 员工台账提交电话、邮箱以外的字段 | PORTAL-05 |
| 400 | `UNKNOWN_PERMISSION` | 授权中有未知权限码或 `rbac:*` 码 | GRANT-02、AUTHZ-02 |
| 400 | `READ_REQUIRED` | 授权中某功能点有非查看操作但缺查看 | GRANT-02 |
| 404 | `NOT_FOUND` | 角色不存在（无数据范围的对象） | — |
| 409 | `VERSION_CONFLICT` | 用户角色、角色授权版本不符，`details.currentVersion` | GRANT-01、GRANT-02 |
| 409 | `DUPLICATE_USERNAME`、`DUPLICATE_PHONE` | 工号、手机号重复 | USER-02 |
| 409 | `DUPLICATE_ROLE_CODE` | 角色编码重复 | ROLE-01 |
| 409 | `PROTECTED_ACCOUNT` | 停用、删除、改角色涉及自己或唯一超级管理员 | USER-07 |
| 409 | `PROTECTED_ROLE` | 删除内置角色、停用超级管理员角色、修改其授权 | ROLE-03 |
| 409 | `ROLE_HAS_USERS` | 删除仍有用户的自建角色且未确认，`details.userCount` | ROLE-03 |

## 7. 需要其他成员配合的地方

这些不是 OpenAPI 的问题，顺带记下，由各自负责人处理：

| 事项 | 现状 | 建议 | 涉及 |
| --- | --- | --- | --- |
| 原型分页参数 | 原型用 `size` | OpenAPI 定稿后改为 `pageSize` | 原型（成员 2、3） |
| 原型的超级管理员角色编码 | 原型为 `SUPER_ADMIN`，表结构为 `ADMIN` | 原型改为 `ADMIN` | 原型 |
| 原型对重复元素的处理 | 原型 `assignRoles` 静默去重 | 按草案返回 400（测试用例 API-013） | 原型 |
| PR #12 修改用户状态 | `PUT /users/{id}/status` | 按契约用 `PATCH`（或契约改为 `PUT`），两边一致 | 成员 4、5 |
| PR #12 表结构 | 自带 H2 `schema.sql`，含 `account_type` | 改用 PR #13 的迁移，删除账号类别；对照见数据字典“与 PR #12 表结构的对照” | 成员 4、5 |
| `DEPT_ADMIN` 授权种子 | PR #13 没有 | 成员 6 确认第 5 节末的提议后补种子 | 成员 1 |

## 8. 测试用例文档第 5 节的 7 个问题

成员 6 的测试用例文档列了联调前必须确认的 7 个问题。需求定稿后，大部分已经有结论：

| 问题 | 结论 | 依据 |
| --- | --- | --- |
| 1. Cookie 还是 Token | 需求未定，交给接口设计（需求 6.2）。草案的 HttpOnly Cookie 可以保留，需和成员 4 确认后去掉“草案名称”说明 | 需求 6.2 |
| 2. 分配角色用 `assign-role` 还是 `update` | 两个码都保留，都映射自 f339 编辑 | 第 5 节 |
| 3. 工作台要求登录还是 `rbac:user:read` | 管理概览不是需求条目；原型的管理概览要求 `rbac:user:read`，门户首页只要求登录 | 需求 6.1；原型 `getOverview` |
| 4. 权限变化何时生效 | 下一次请求 | GRANT-05 |
| 5. 越权返回 403 还是 404 | 有数据范围的对象 403 `OUT_OF_SCOPE`，与不存在相同；角色不存在 404 | G-07 |
| 6. 三种范围的含义与合并 | ALL / DEPT（含全部下级）/ SELF，同码取最大 | GRANT-04、AUTHZ-03 |
| 7. 是否禁止改自己、唯一超级管理员、内置角色 | 是，409 `PROTECTED_ACCOUNT` / `PROTECTED_ROLE` | USER-07、ROLE-03 |

测试用例本身（API-001 ~ API-016）与需求一致，可以保留；建议按第 3 节补充员工台账范围（T-09 / PORTAL-05）、首次登录改密、角色授权带范围与 `READ_REQUIRED`、删除用户、角色启停用的用例。
