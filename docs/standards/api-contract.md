# 第一阶段 API 约定

2026-10-18 集成版，契约版本 `0.3.0`。权威文件为 [OpenAPI](../../api/openapi.yaml)，后端为 `backend-procedural/`。历史接口草案和差异评审保留作者贡献，字段、路径与本文冲突时以当前契约及其测试为准。

## 请求与响应

- 路径前缀 `/api/v1`，JSON。成功直接返回对象或数组；分页为 `{items, page, pageSize, total}`，页码从 1 开始，默认每页 20，最大 100。删除成功为 HTTP 204，无响应体。
- 身份使用服务端 `HttpSession`，Cookie 名 `JSESSIONID`，`HttpOnly`、`SameSite=Lax`；HTTPS 部署启用 `Secure`。浏览器同源发送 Cookie；登录成功轮换会话 ID，退出使旧会话失效。响应不返回令牌。
- 工号字段统一 `username`。ID 一律为字符串。用户状态 `ENABLED / DISABLED`；角色状态 `enabled` 布尔值；部门类型 `HQ / HQ_DEPT / BRANCH / BRANCH_DEPT / PROJECT`。
- 不存在账号类别 `accountType`。创建用户不接受角色或初始密码；角色由独立授权接口维护，初始密码由部署环境配置。
- 失败包络为 `{error: {code, message, requestId, details}}`。`details` 可以为空；不返回密码、哈希、会话或不可见对象资料。
- 参数错误统一 HTTP 400；未登录 401；无权 403；不存在的角色/目录对象 404；版本冲突、唯一键冲突和保护规则 409；服务端失败 500。

## 会话与权限

第一阶段登录后全部已分配且启用的角色生效。每个请求重新检查账号状态、角色及授权；账号停用后旧会话下一次请求返回 401，角色停用/撤销/授权变化下一次请求立即生效。

用户不存在、密码错误、停用、锁定的登录结果统一为 401 `INVALID_CREDENTIALS`，提示“用户名或密码错误”。首次登录/密码重置后的 `mustChangePassword=true` 会话仅能调用 `GET /me`、`PATCH /me/password`、`POST /auth/logout`；其余受保护接口为 403 `PASSWORD_CHANGE_REQUIRED`。改密检查旧密码，新密码为8–128字符且不能与旧密码相同。

鉴权顺序是认证 → 权限码 → 数据范围 → 目标对象；没有授予默认拒绝。前端传来的用户、角色、权限不可作为授权依据。部门、用户及台账目标不存在或在范围外，一律 403 `OUT_OF_SCOPE`，不返回目标字段；列表先范围裁剪再筛选、计数与分页。

| 范围 | 定义 |
| --- | --- |
| `ALL` | 所有目标 |
| `DEPT` | 当前用户所属部门及全部下级部门 |
| `SELF` | 目标记录属于当前用户 |

多角色取权限并集，同一个权限码取 `ALL > DEPT > SELF` 的最大范围。不同权限码分别计算；查看范围不会放大编辑范围。`/me/permissions` 返回的 `scopes[].roles` 标明来源角色，与实际鉴权使用同一结果。

`rbac:department:read=SELF`允许看到本人部门节点，但部门成员接口仍只能返回本人，不能借部门列表看到同事。`rbac:user:create=SELF`不能新建其他人的账号，返回403 OUT_OF_SCOPE；创建用户通常授予ALL或DEPT。

## 清单权限到管理权限的映射

清单权限格式为 `系统短码:fNNN:动作`，动作固定 `read/create/edit/approve/export/delete`。显示名称变化不改变权限码。`rbac:*` 是后端计算出的派生权限，数据范围继承源清单授权；不能直接写入角色授权。

| 清单权限 | 派生管理权限 |
| --- | --- |
| `sys:f339:read` | `rbac:user:read` |
| `sys:f339:create` | `rbac:user:create` |
| `sys:f339:edit` | `rbac:user:update`、`rbac:user:assign-role` |
| `sys:f339:delete` | `rbac:user:delete` |
| `sys:f340:read` | `rbac:role:read` |
| `sys:f340:create` | `rbac:role:create` |
| `sys:f340:edit` | `rbac:role:update` |
| `sys:f340:delete` | `rbac:role:delete` |
| `sys:f341:read` | `rbac:permission:read` |
| `sys:f341:edit` | `rbac:role:grant` |
| `sys:f342:read` | `rbac:department:read` |
| `sys:f348:read`、`sys:f349:read` | `rbac:log:read`（阶段三） |
| `sys:f348:export`、`sys:f349:export` | `rbac:log:export`（阶段三） |

部门管理员的六条平台授权：`sys:f339:read/create/edit/delete=DEPT`，`sys:f340:read=ALL`，`sys:f342:read=DEPT`。这六条是清单五角色的 3549 条之外的平台补充；完整导入时合计 3555 条角色权限关系。部门管理员只能给范围内用户增删业务角色，不能增删管理类角色，不能修改角色授权；超级管理员角色不能授给其他人。

## 整组替换与保护

- `PUT /users/{id}/roles`：`{roleIds, version}`，空数组撤销全部；拒绝重复 ID。`version` 来自对应 GET，成功原子替换并递增，不接受旧版本。
- `PUT /roles/{id}/permissions`：`{grants:[{code,scope}], version}`，空数组撤销全部；同一权限码不可重复。只能传清单码。前端勾非查看动作时自动勾查看，后端缺查看返回 `READ_REQUIRED`。成功整体替换并递增角色授权版本，中途失败事务回滚。
- 用户角色版本和角色授权版本彼此独立。保存冲突为 409 `VERSION_CONFLICT`，`details.currentVersion` 告知版本；客户端重新读取后人工确认再提交，不能盲目重试覆盖。
- 禁止停用/删除自己、修改自己角色，禁止停用/删除唯一 `admin` 或修改其角色，409 `PROTECTED_ACCOUNT`。
- 六个内置角色不可删除；`ADMIN` 不可停用或改授权，409 `PROTECTED_ROLE`。删除有用户的自建角色须 `confirm=true`，否则 409 `ROLE_HAS_USERS`；删除后清理关联并更新受影响用户版本。

## 主要错误码

| HTTP | 错误码 | 含义 |
| --- | --- | --- |
| 400 | `VALIDATION` | 格式、长度、枚举、重复 ID 等参数错误 |
| 400 | `BAD_OLD_PASSWORD` | 修改密码时旧密码错误 |
| 400 | `FIELD_NOT_EDITABLE` | 台账提交电话/邮箱以外字段 |
| 400 | `UNKNOWN_PERMISSION`、`READ_REQUIRED` | 非目录权限、直接授予派生码，或缺少查看权限 |
| 401 | `INVALID_CREDENTIALS`、`UNAUTHENTICATED` | 登录失败、会话不可用 |
| 403 | `FORBIDDEN`、`OUT_OF_SCOPE`、`PASSWORD_CHANGE_REQUIRED` | 缺权限、越范围、须先改密 |
| 404 | `NOT_FOUND` | 角色或目录对象不存在 |
| 409 | `VERSION_CONFLICT` | 授权集合版本已变更 |
| 409 | `DUPLICATE_USERNAME`、`DUPLICATE_PHONE`、`DUPLICATE_ROLE_CODE` | 唯一键冲突 |
| 409 | `PROTECTED_ACCOUNT`、`PROTECTED_ROLE`、`ROLE_HAS_USERS` | 保护规则或缺删除确认 |

角色继承、SSD/DSD、基数约束、激活角色切换和审计日志属于后续阶段。离线导入不提供网页接口，部门及目录只读。员工台账新增/删除/审批/导出仅作为界面原型占位，真实读写为 `GET /ledger/employees`、`PATCH /ledger/employees/{id}`。

变更接口时同步 OpenAPI、后端、客户端和测试。实际执行记录见 [一期验证记录](../stage1/verification-record.md)，契约完整不代表验收已执行。
