# 成员 4：面向功能后端 RBAC0 接口草案

作者：王信凯（成员 4，面向功能后端）

关联：[成员 3 使用方需求](member3-rbac0-consumer-needs.md)、[成员 2 前端契约](../../docs/design/member2-frontend-contract.md)、[首批任务](../../docs/planning/first-tasks.md)、[待确认问题](../../docs/requirements/questions.md)

状态：**提案，待成员 2、3、6 及相关成员确认。未修改权威 [openapi.yaml](../openapi.yaml)，其 `paths` 仍为空。** 本文是面向功能后端（`backend-procedural/`）实现 RBAC0 时的外部契约草案，最终以成员 6 写入 OpenAPI 的结论为准。

## 1. 目的与责任边界

本文记录成员 4 作为面向功能后端作者，对 RBAC0 接口的输入输出、认证与失败分支的提议：回应成员 3 的角色、资源目录、用户角色接口需求，回应成员 2 的登录、用户、部门接口需求，并统一成员 6 需要维护的一致性口径。

后端是唯一可信的授权依据。前端返回的 `permissionCodes` 只用于菜单、路由和按钮展示；每次受保护请求都必须由后端从会话重新认证与授权，不能信任请求体或查询参数中自报的用户 ID、角色或权限。

面向功能后端把授权实现组织为一段过程：**认证 → 加载用户角色 → 计算有效权限 → 检查约束 → 执行业务**。本文只描述它的外部契约，内部过程分解见 [设计说明](../../docs/design/member4-procedural-rbac0-design.md)。

所有示例 ID、角色、权限码、版本和数量都是合成数据，不表示真实 359 个功能点或员工表已导入，也不表示后端已实现。

## 2. 共同约定草案

| 项目 | 提议 | 与使用方的一致性说明 |
| --- | --- | --- |
| 前缀 | `/api/v1` | 成员 2、3 同 |
| 格式 | JSON；成功直接返回对象/数组，错误返回 `error` 对象 | 成员 3 同 |
| ID | JSON 字符串，如 `"demo-u-001"` | 避免 JS 大整数精度；正式 ID 可不同 |
| 页码 | 从 1 开始；`pageSize` 默认 20、最大 100 | 成员 2、3 同 |
| 排序 | 服务端稳定排序，以 ID 作为并列时的确定次序 | 翻页不重复、不跳项 |
| 替换集合 | PUT 发送完整 `roleIds` / `permissionCodes` | 非追加，防漏撤销 |
| 并发 | `version` 整数，GET 取得、PUT 比较、成功递增 | 防覆盖他人修改 |
| 重复元素 | 服务端拒绝重复元素 | 避免含糊集合语义 |
| 未知对象 | 路径目标不存在返回 404；载荷中未知角色/权限返回 400 | 区分目标与非法选项 |

### 2.1 认证与会话

- 登录由 `POST /auth/login` 建立会话；会话由后端验证，具体采用 Cookie 还是 Token 待确认（见 §18）。
- 会话保存登录用户 ID 与最小必要身份，不保存客户端可改写的权限快照。
- 每次受保护请求按 **401 → 403 → 业务校验** 的顺序处理：先确认会话有效，再确认操作权限，最后做对象与数据范围检查。
- 退出使旧会话失效；账号停用后，已有会话应立即失效（下一请求 401）。

### 2.2 错误包络

```json
{
  "error": {
    "code": "VERSION_CONFLICT",
    "message": "授权已被其他人修改，请重新读取并复核。",
    "requestId": "demo-request-001",
    "details": { "expectedVersion": 7, "currentVersion": 8 }
  }
}
```

`details` 按错误类别有选择返回，不含密码、令牌或不可见对象的资料。400 可含字段名；409 可含当前版本。

### 2.3 状态码与错误码

| HTTP | 错误码 | 适用 |
| --- | --- | --- |
| 400 | `INVALID_ARGUMENT` | 字段缺失、类型错、格式错 |
| 400 | `UNKNOWN_ROLE` / `UNKNOWN_PERMISSION` | 载荷中的未知角色/权限 |
| 400 | `DUPLICATE_ITEM` | 集合中有重复元素 |
| 401 | `UNAUTHENTICATED` | 会话缺失或失效 |
| 401 | `INVALID_CREDENTIALS` | 登录凭证错误 |
| 403 | `FORBIDDEN` | 无操作权限或数据范围不足 |
| 403 | `ACCOUNT_DISABLED` | 账号被停用（登录时） |
| 404 | `USER_NOT_FOUND` / `ROLE_NOT_FOUND` / `RESOURCE_NOT_FOUND` | 路径目标不存在 |
| 409 | `VERSION_CONFLICT` | 版本冲突 |
| 409 | `ASSIGNMENT_CONFLICT` / `GRANT_CONFLICT` | 仅用于已确认的管理策略，RBAC0 暂不启用 |

### 2.4 授权模型

- RBAC0 有效权限 = 已分配且启用的角色所授权限的去重并集；不含继承（RBAC1）与约束（RBAC2）。
- 无匹配权限即拒绝（默认拒绝）。
- 业务权限的 `all` / `self` 范围不能简化为一个布尔值；`self` 必须在后端把目标对象与会话身份比对，不能信任 URL 中的 ID。
- 对象存在性检查与 403/404 的返回次序由成员 6 确定防枚举策略；未确认前倾向对无权对象统一返回 403。

## 3. POST `/api/v1/auth/login`

**用途：** 校验凭证、建立会话、返回当前身份与有效权限摘要。

**认证与权限：** 公开。仅校验员工号或手机号与密码；账号停用拒绝登录。

**请求：**

```json
{ "account": "E001", "password": "123456" }
```

`account` 接受员工号或手机号；用户名不作为登录标识。

**200 响应：**

```json
{
  "user": {
    "id": "demo-u-001",
    "name": "演示管理员",
    "employeeNo": "E001",
    "departmentName": "总公司"
  },
  "permissionCodes": ["rbac:role:read", "rbac:user:read", "rbac:user:assign-role"]
}
```

成功后以 Cookie/Token 携带会话，前端后续请求自动带上。

**失败分支：**

| HTTP | 错误码 | 含义/前端处理 |
| --- | --- | --- |
| 400 | `INVALID_ARGUMENT` | 缺少账号或密码字段 |
| 401 | `INVALID_CREDENTIALS` | 账号或密码错误；统一提示，不区分具体哪一项错（防枚举） |
| 403 | `ACCOUNT_DISABLED` | 账号停用；提示联系管理员 |

## 4. POST `/api/v1/auth/logout`

**用途：** 使当前会话失效，删除服务端会话状态。

**认证与权限：** 已登录（或允许幂等退出）。

**请求：** 无请求体。

**200 响应：** 空对象或 `{ "success": true }`；前端无论结果都清理本地会话。

**失败分支：** 401 `UNAUTHENTICATED`（无有效会话时按幂等处理也可返回成功，由成员 6 统一）。

## 5. GET `/api/v1/me`

**用途：** 返回当前登录身份的可展示字段。

**认证与权限：** 必须登录。

**200 响应：**

```json
{
  "id": "demo-u-001",
  "name": "演示管理员",
  "employeeNo": "E001",
  "departmentId": "demo-dept-hq",
  "departmentName": "总公司",
  "accountType": "employee"
}
```

不含 `phone` 等个人字段；是否展示由后端权限决定。

**失败分支：** 401 `UNAUTHENTICATED`；403 `ACCOUNT_DISABLED`（会话期间被停用）。

## 6. GET `/api/v1/me/permissions`

**用途：** 取得当前用户自身有效权限，驱动菜单、路由和按钮。与成员 3 提案一致。

**认证与权限：** 必须登录；不接受 `userId` 查询他人。

**请求：** 无请求体、无用户 ID 参数。

**200 响应：**

```json
{
  "userId": "demo-u-001",
  "roleIds": ["demo-r-reader"],
  "permissionCodes": ["oa:document:read", "hr:self:read"],
  "scopes": [
    { "permissionCode": "oa:document:read", "scope": "all" },
    { "permissionCode": "hr:self:read", "scope": "self" }
  ],
  "version": 3
}
```

**空结果：** 无角色或有效权限返回 200，`roleIds: []`、`permissionCodes: []`、`scopes: []`。

**失败分支：** 401 `UNAUTHENTICATED`；403 `ACCOUNT_DISABLED`。此接口不修改集合，409 不适用；后端权限快照必须自身一致。

**待确认：** 权限改变何时影响已有会话、`version` 怎样变化（见 §17）。

## 7. GET `/api/v1/users`

**用途：** 分页查询用户，供用户管理列表使用（成员 2 消费）。

**认证与权限：** 必须登录，要求 `rbac:user:read`；按会话可见范围返回，不能先取全量再靠前端隐藏。

**请求：**

```http
GET /api/v1/users?page=1&pageSize=20&keyword=张三&departmentId=dept-01&accountType=employee
```

**200 响应：**

```json
{
  "items": [
    {
      "id": "demo-u-001",
      "name": "演示管理员",
      "employeeNo": "E001",
      "departmentId": "demo-dept-hq",
      "departmentName": "总公司",
      "branch": "总公司",
      "position": "系统管理员",
      "status": "active",
      "accountType": "employee"
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1
}
```

列表不展开角色与权限，`phone` 是否返回由后端权限决定。

**空结果：** 无匹配返回 200，`items: []`、`total: 0`。

**失败分支：** 400 页码/页大小/筛选非法；401 未登录；403 无读权限或范围不足。

## 8. GET `/api/v1/users/{id}`

**用途：** 用户详情，含角色与有效权限摘要（成员 2 消费）。

**认证与权限：** 必须登录，要求 `rbac:user:read`，目标用户须在可见范围内。

**200 响应：**

```json
{
  "id": "demo-u-001",
  "name": "演示管理员",
  "employeeNo": "E001",
  "departmentId": "demo-dept-hq",
  "departmentName": "总公司",
  "branch": "总公司",
  "position": "系统管理员",
  "status": "active",
  "accountType": "employee",
  "phone": "13800000001",
  "roles": ["超级管理员"],
  "permissionCodes": ["rbac:role:read", "rbac:user:read"]
}
```

**失败分支：** 400 ID 非法；401 未登录；403 无权限/目标不可读；404 用户不存在。

## 9. GET `/api/v1/users/{id}/roles`

**用途：** 读取目标用户完整已分配角色集合与版本（成员 3 消费）。与成员 3 提案一致。

**认证与权限：** 必须登录，候选要求 `rbac:user:read` 与 `rbac:role:read`；后端验证目标用户在可读范围内。

**200 响应：**

```json
{
  "userId": "demo-u-001",
  "roleIds": ["demo-r-reader", "demo-r-editor"],
  "version": 7
}
```

返回完整集合，不因当前角色分页只返回部分；若某角色不可编辑应给出正式解释，不能因选项缺失被默默撤销。

**空结果：** 用户存在但无角色，返回 200，`roleIds: []` 与有效版本。

**失败分支：** 400 ID 非法；401 未登录；403 无权限/不可读；404 用户不存在；409 读取不适用。

## 10. PUT `/api/v1/users/{id}/roles`

**用途：** 原子替换目标用户完整角色集合（分配、保留、撤销）。

**认证与权限：** 必须登录，要求 `rbac:user:assign-role`；后端检查目标用户、所有目标角色、允许分配范围及管理员策略。持有该权限码不保证请求一定获准。

**请求：**

```json
{ "roleIds": ["demo-r-editor", "demo-r-reviewer"], "version": 7 }
```

原集合 `[reader, editor]` 时，本请求保留 editor、分配 reviewer、撤销 reader。`{ "roleIds": [], "version": 7 }` 明确撤销全部角色；漏传 `roleIds` 是 400，不是撤销指令。

**200 响应：**

```json
{
  "userId": "demo-u-001",
  "roleIds": ["demo-r-editor", "demo-r-reviewer"],
  "version": 8
}
```

后端必须在一次原子事务内完成校验与替换，失败时不得出现“新增成功但撤销失败”。校验重复元素、版本字段和未知角色。

**失败分支：**

| HTTP | 错误码 | 含义/前端处理 |
| --- | --- | --- |
| 400 | `INVALID_ARGUMENT`、`UNKNOWN_ROLE`、`DUPLICATE_ITEM` | 字段、重复项、未知角色；保留草稿修正 |
| 401 | `UNAUTHENTICATED` | 会话失效，交公共登录流程 |
| 403 | `FORBIDDEN` | 不允许操作该用户或分配该角色；停止保存 |
| 404 | `USER_NOT_FOUND` | 目标用户不存在；返回列表 |
| 409 | `VERSION_CONFLICT` | 旧版本；重新读取后复核，不自动覆盖 |
| 409 | `ASSIGNMENT_CONFLICT` | 仅用于已确认且已实现的管理策略；RBAC0 暂不启用 |

是否限制自行撤权或最后管理员撤权需另行确认，本文没有默认启用。

## 11. GET `/api/v1/departments`

**用途：** 返回部门组织树（成员 2 消费）。

**认证与权限：** 必须登录，要求 `rbac:department:read`；按可见范围返回树。

**200 响应：**

```json
[
  {
    "id": "demo-dept-hq",
    "name": "总公司",
    "manager": "demo-u-001",
    "branch": "总公司",
    "parentId": null,
    "employeeCount": 3,
    "children": []
  }
]
```

**失败分支：** 401 未登录；403 无读权限或范围不足。

## 12. GET `/api/v1/roles`

**用途：** 查询角色选择项与角色信息（成员 3 消费）。与成员 3 提案一致。

**认证与权限：** 必须登录，要求 `rbac:role:read`；按会话可见范围返回。

**请求：**

```http
GET /api/v1/roles?page=1&pageSize=20&keyword=公文
```

**200 响应：**

```json
{
  "items": [
    {
      "id": "demo-r-reader",
      "name": "公文查看员",
      "description": "合成演示角色",
      "permissionCodes": ["oa:document:read"],
      "version": 4
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1
}
```

**空结果：** 无匹配返回 200，`items: []`、`total: 0`。

**失败分支：** 400 页码/页大小/筛选非法；401 未登录；403 无角色读权限或范围。

## 13. GET `/api/v1/roles/{id}/permissions`

**用途：** 读取角色完整直接权限集合（成员 3 消费）。与成员 3 提案一致。

**认证与权限：** 必须登录，候选要求 `rbac:role:read` 与 `rbac:permission:read`；后端检查角色可见范围。

**200 响应：**

```json
{
  "roleId": "demo-r-reader",
  "permissionCodes": ["oa:document:read"],
  "version": 4
}
```

**空结果：** 角色存在但无直接权限返回 200，`permissionCodes: []`。RBAC0 不含继承计算。

**失败分支：** 400 ID 非法；401 未登录；403 权限/范围不足；404 角色不存在。

## 14. PUT `/api/v1/roles/{id}/permissions`

**用途：** 原子替换角色完整直接权限码集合。

**认证与权限：** 必须登录，要求 `rbac:role:grant`；后端检查目标角色、可授出权限集合和管理范围。不能只校验“权限码是否存在”，还要检查调用者能否授出。

**请求：**

```json
{
  "permissionCodes": ["oa:document:read", "hr:self:read"],
  "version": 4
}
```

空 `permissionCodes: []` 是合法撤销全部直接权限；漏字段是 400。校验所有元素后一次写入，不允许部分成功。

**200 响应：**

```json
{
  "roleId": "demo-r-reader",
  "permissionCodes": ["oa:document:read", "hr:self:read"],
  "version": 5
}
```

**失败分支：**

| HTTP | 错误码 | 含义/前端处理 |
| --- | --- | --- |
| 400 | `INVALID_ARGUMENT`、`UNKNOWN_PERMISSION`、`DUPLICATE_ITEM` | 字段、重复项、未知码；保留草稿修正 |
| 401 | `UNAUTHENTICATED` | 会话缺失/失效 |
| 403 | `FORBIDDEN` | 不能修改该角色或授出所选权限 |
| 404 | `ROLE_NOT_FOUND` | 目标角色不存在 |
| 409 | `VERSION_CONFLICT` | 授权已变化；重新读回并复核 |
| 409 | `GRANT_CONFLICT` | 仅用于已确认并已实现的管理规则，RBAC0 不臆造约束 |

## 15. GET `/api/v1/permissions`

**用途：** 分页查询最末级可授权权限，供角色权限编辑与目录检索（成员 3 消费）。

**认证与权限：** 必须登录，要求 `rbac:permission:read`；范围取会话允许看到的资源目录；能否授出这些权限由 PUT 独立检查。

**请求：**

```http
GET /api/v1/permissions?page=1&pageSize=20&systemId=demo-oa&moduleId=demo-document&keyword=公文
```

**200 响应：**

```json
{
  "items": [
    {
      "code": "oa:document:read",
      "name": "查看公文",
      "systemId": "demo-oa",
      "systemName": "OA办公系统",
      "moduleId": "demo-document",
      "moduleName": "公文管理",
      "resourceCode": "oa:document",
      "resourceName": "公文",
      "action": "read",
      "scopeOptions": ["all", "self"]
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1
}
```

**空结果：** 返回 200 空 `items`。

**失败分支：** 400 分页/筛选格式非法或引用非法目录选项；401 未登录；403 无目录读权限/范围。

## 16. GET `/api/v1/resources`

**用途：** 提供系统、模块、功能的目录结构/筛选项（成员 3 消费）。

**认证与权限：** 必须登录，要求 `rbac:permission:read`，按允许目录范围返回。

**请求：**

```http
GET /api/v1/resources?parentId=demo-oa&page=1&pageSize=20
```

根目录不带 `parentId`；带 `parentId` 查询直接子项，不是一条请求递归返回 359 个功能。

**200 响应：**

```json
{
  "items": [
    {
      "id": "demo-document",
      "name": "公文管理",
      "type": "module",
      "parentId": "demo-oa",
      "hasChildren": true
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1
}
```

**失败分支：** 400 参数格式错误；401 未登录；403 父目录不可见或无读权限；404 指定 `parentId` 不存在。

## 17. 授权时机与会话失效

- 用户角色或角色权限被替换后，受影响账号的既有会话何时重新计算权限，由成员 4、6 确认；候选是“下一次请求重算”或“写入时强制会话失效”。
- 账号停用必须立即让既有会话失效（下一请求 401）。
- 前端不把本地改完样例视为全系统立即撤权；正式语义以契约为准。

## 18. 待确认的决策

| 问题 | 主责 | 当前结论 |
| --- | --- | --- |
| Cookie 还是 Token、401 处理与退出语义 | 成员 2/4/6 | 待确认 |
| ID、包络、页码、排序和错误码正式定稿 | 成员 6，与 2/4 确认 | 本文为提案 |
| 管理对象范围、可授出集合、自行/最后管理员策略 | 成员 4/6、组长 | 待确认 |
| 权限改变对既有会话的生效时点 | 成员 4/6 | 待确认 |
| 目录来源、稳定权限码与清洗歧义 | 成员 1/6、老师 | 待确认 |
| 多角色规则、`all`/`self` 范围合并规则 | 成员 4/6、老师 | 待确认 |
| 对象存在性与 403/404 防枚举次序 | 成员 6 | 待确认 |

有关边界仍以 [需求范围](../../docs/requirements/scope.md)、[问题清单](../../docs/requirements/questions.md) 和 [API 约定草案](../../docs/standards/api-contract.md) 为准。**本文存在只能说明成员 4 已提出接口草案，不能说明真实联调、后端安全测试或两套后端一致性已经通过。**
