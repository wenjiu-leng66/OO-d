# 成员 3：RBAC0 前端 API 使用方需求提案

作者：杨昕桐（成员 3）

关联：[Issue #4](https://github.com/wenjiu-leng66/RBAC-Permission-System/issues/4)、[交互设计](../../docs/design/member3-rbac0-design.md)
状态：**成员 3 的使用方提案，部分内容已由成员 6 整理进第一版 [openapi.yaml](../openapi.yaml)，仍待成员 2、3、4 确认和后端联调。** 本文件继续记录提案来源，不替代权威契约，也不表示接口已经实现。

## 1. 目的与消费边界

前端需要：取得本人的有效权限，读取角色与资源目录，读取并替换用户的角色集合，读取并替换角色的直接权限集合。登录、登出、公共用户页和公共 API 客户端由成员 2 对接；此提案只列角色权限页面消费所需的接口。

所有示例 ID、角色、权限、版本和数量都是合成数据。此提案不表示真实 359 个功能点已导入，也不表示后端实现已存在。

## 2. 共同约定草案

| 项目 | 提议 | 需确认的原因 |
| --- | --- | --- |
| 前缀 | `/api/v1` | 两套后端、前端和QA统一 |
| 格式 | JSON；成功直接返回对象，错误返回 `error` 对象 | 尚无正式包络约定 |
| 认证 | 使用后端验证的会话；具体 Cookie/Token 由成员2/4/6确认 | 本提案不硬编码存储策略 |
| ID | JSON 字符串，如 `"demo-u-001"` | 避免 JS 大整数精度问题；正式 ID 可不同 |
| 页码 | 从1开始；`pageSize`建议默认20、最大100 | 服务端校验并记录正式上限 |
| 筛选 | `keyword`、`systemId`、`moduleId`等；无匹配返回200空数组 | 不能把正常空结果当失败 |
| 排序 | 服务端给定稳定排序，并以ID作为并列时的确定次序 | 翻页避免重复或跳项 |
| 替换集合 | PUT发送完整 `roleIds` 或 `permissionCodes`，不是追加 | 防止漏撤销和误覆盖 |
| 并发 | `version`为整数，GET取得、PUT比较、成功递增 | 防止最后一次保存覆盖别人的修改 |
| 重复元素 | 服务端拒绝重复元素，前端发送前去重 | 避免含糊的集合语义 |
| 未知对象 | 目标用户/角色不存在返回404；载荷中的未知角色/权限返回400 | 区分路径目标与非法选项 |

版本仅表示正在替换的授权集合版本；不建议随不相关姓名变更而触发冲突。会话权限版本、用户角色版本、角色直接权限版本是否分别维护，需要后端明确。

### 认证、管理权限与数据范围

前端不发送可供后端信任的“我有哪些权限”。后端从经过验证的会话计算身份、管理权限、目标范围和允许授出的角色/权限。具备管理码不意味着能任意操作全部对象。

本提案的管理页面以具有管理范围的测试管理员为例；真实部门范围及权限下放策略尚待确认。如果不允许读取某用户或角色，应返回403（或正式约定的防对象枚举策略），不得把其他范围数据返回后再靠前端隐藏。

业务权限的 `all`、`self` 范围不能简化成一个“允许编辑”的前端布尔值。范围合并规则由成员4/6确认，未知或未定义范围按拒绝处理。

### 错误包络提议

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

`details`按错误类别有选择地返回，不能包含密码、令牌或不可见对象的资料。400可包含字段名；409可包含当前版本。具体错误码和是否采用422由成员6最终统一；本提案暂用400表示参数错误，409表示版本/业务规则冲突。

## 3. GET `/api/v1/me/permissions`

**用途：** 登录后取得当前用户自身的有效权限，驱动菜单、路由和按钮。身份资料本身可由公共 `GET /me` 提供，本提案不重复定义。

**认证与权限：** 必须登录，不要求额外的管理权限码；只返回当前会话身份的权限。不能接受 `userId` 查询他人。

**请求：** 无请求体、无用户ID参数。

**200响应示例：**

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

**原型适配：** 公共 API 客户端把 `permissionCodes`写入 UI `permissions: string[]`，并在成功后置 `permissionsLoaded: true`。`scopes`保留用于正式范围展示；精确码判断不完成本人数据校验。原型当前角色形状使用 `permissionCodes`，与这个提案一致。

**空结果：** 登录用户没有角色或有效权限时返回200，`roleIds: []`、`permissionCodes: []`、`scopes: []`；登录仍有效，但受保护操作默认拒绝。

**失败分支：** 400用于不支持/非法查询参数（正式契约可选择忽略未知参数）；401会话缺失或失效；403账号被禁止使用系统等经过确认的账号策略；404仅在会话引用的用户已经不存在时按正式会话策略处理（通常应改为401）。此接口不修改集合，409不适用，后端权限快照必须自身一致。

**待确认：** RBAC0是否用全部已分配角色计算权限；权限改变何时影响已有会话；`version`怎样变化。本例不提前引入DSD的激活角色机制。

## 4. GET `/api/v1/roles`

**用途：** 查询角色选择项与角色信息，为角色分配和角色权限配置提供列表。

**认证与权限：** 必须登录，要求 `rbac:role:read`；按会话可见范围返回角色。角色可见并不表示允许给某用户分配，保存时须重新判断。

**请求示例：**

```http
GET /api/v1/roles?page=1&pageSize=20&keyword=公文
```

**200响应示例：**

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

角色形状与原型的 `{id, permissionCodes, version}`兼容。正式清单规模增长后，可把列表直接权限字段改为详情接口读取，但必须同步适配器和OpenAPI，不能悄悄改变字段。

**空结果：** 无匹配角色返回200，`items: []`、`total: 0`；超出末页建议也返回空数组。保存所需可选角色较多时必须读取完整选项或采用服务端检索，不能只用第一页来判断用户已获角色是否存在。

**失败分支：** 400页码/页大小/筛选非法；401未登录；403没有角色读权限或范围；404本列表无路径对象，一般不适用；409只读查询不适用。网络和5xx需显示可重试错误，不能回退成“无角色”。

## 5. GET `/api/v1/permissions`

**用途：** 分页查询最末级可授权权限，给角色权限编辑器和目录检索使用。

**认证与权限：** 必须登录，要求 `rbac:permission:read`；范围取会话允许看到的资源目录。是否能够授出这些权限还需PUT独立检查。

**请求示例：**

```http
GET /api/v1/permissions?page=1&pageSize=20&systemId=demo-oa&moduleId=demo-document&keyword=公文
```

**200响应示例：**

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

`scopeOptions`是候选可用范围，不表示当前用户被授予全部范围。本阶段角色授权集合只传权限码；如果正式系统需要同时配置每个权限的数据范围，则应升级为独立授权对象契约，不让前端自行猜默认范围。

**空结果：** 返回200空`items`；页面显示无匹配功能。

**失败分支：** 400分页/筛选格式非法，或筛选引用不合法的目录选项；401未登录；403无目录读权限/范围；404不适用于无路径ID的查询（不存在筛选可约定空结果）；409只读查询不适用。

## 6. GET `/api/v1/resources`

**用途：** 提供系统、模块、功能的目录结构/筛选项，与末级权限检索分开，避免一次传送全矩阵。

**认证与权限：** 必须登录，要求 `rbac:permission:read`，按允许目录范围返回。

**请求示例：**

```http
GET /api/v1/resources?parentId=demo-oa&page=1&pageSize=20
```

根目录不带`parentId`；带`parentId`查询直接子项。不是一条请求递归返回359个功能的全部操作。

**200响应示例：**

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

`type`候选值为`system/module/function`；功能节点的操作由permissions接口查询，不把模块节点当权限码。

**空结果：** 合法父目录没有子项返回200空`items`。权限目录全部为空也应显示正常空状态。

**失败分支：** 400参数格式错误；401未登录；403父目录不可见或没有读权限；404指定的`parentId`确实不存在；409只读查询不适用。对象存在性检查与403/404返回次序由成员6确定防枚举策略。

## 7. GET `/api/v1/users/{id}/roles`

**用途：** 选中用户后，读取完整已分配角色集合和集合版本。

**认证与权限：** 必须登录，候选要求`rbac:user:read`及`rbac:role:read`；后端验证目标用户在可读范围内。用户列表公共`GET /users`由成员2消费，但应包含稳定ID以供这里使用。

**请求示例：**

```http
GET /api/v1/users/demo-u-001/roles
```

**200响应示例：**

```json
{
  "userId": "demo-u-001",
  "roleIds": ["demo-r-reader", "demo-r-editor"],
  "version": 7
}
```

返回完整集合，不能只返回当前角色分页中的部分角色。如果某个已获角色不允许编辑，应给出正式的可见/可修改解释，不能前端因为选项缺失而默默撤销。

**空结果：** 目标用户存在但没有角色，返回200，`roleIds: []`与有效版本。

**失败分支：** 400ID格式非法；401未登录；403无读权限/目标用户不可读；404目标用户不存在；409读取接口不适用，结果应是一个一致快照。

## 8. PUT `/api/v1/users/{id}/roles`

**用途：** 替换目标用户的完整角色集合，同时支持分配、保留和撤销。

**认证与权限：** 必须登录，要求`rbac:user:assign-role`；后端检查目标用户、所有目标角色、允许分配范围以及确认后的管理员策略。前端有此权限码并不保证请求一定获准。

**请求示例：**

```http
PUT /api/v1/users/demo-u-001/roles
Content-Type: application/json
```

```json
{ "roleIds": ["demo-r-editor", "demo-r-reviewer"], "version": 7 }
```

如果原集合是`[reader, editor]`，本请求保留editor、分配reviewer、撤销reader。`{ "roleIds": [], "version": 7 }`明确撤销全部角色；漏传`roleIds`不是撤销指令，应返回400。

**200响应示例：**

```json
{
  "userId": "demo-u-001",
  "roleIds": ["demo-r-editor", "demo-r-reviewer"],
  "version": 8
}
```

成功响应是已保存结果，前端以其更新原集合。后端必须在一次原子事务中完成校验与替换，失败时不得“新增成功但撤销失败”。重复元素、版本字段和未知角色应校验。

**失败分支：**

| HTTP | 提议错误码 | 含义/前端处理 |
| --- | --- | --- |
| 400 | `INVALID_ARGUMENT`、`UNKNOWN_ROLE` | 字段缺失、类型错、重复项、未知角色；保留草稿修正 |
| 401 | `UNAUTHENTICATED` | 会话失效，交公共登录流程 |
| 403 | `FORBIDDEN` | 不允许操作该用户或分配该角色；停止保存 |
| 404 | `USER_NOT_FOUND` | 目标用户不存在；返回列表 |
| 409 | `VERSION_CONFLICT` | 旧版本；重新读取后复核，不自动覆盖 |
| 409 | `ASSIGNMENT_CONFLICT` | 仅用于已确认且已实现的业务策略；展示原因 |

RBAC0当前不加入SSD/DSD。未来SSD可以在这里检查分配冲突，但DSD应在会话激活接口检查，不能混为一谈。是否限制自行撤权或最后管理员撤权需要另行确认，提案没有默认启用。

## 9. GET `/api/v1/roles/{id}/permissions`

**用途：** 读取角色的完整直接权限集合，不是当前登录用户的有效权限。

**认证与权限：** 必须登录，候选要求`rbac:role:read`和`rbac:permission:read`；后端检查角色可见范围。

**请求示例：**

```http
GET /api/v1/roles/demo-r-reader/permissions
```

**200响应示例：**

```json
{
  "roleId": "demo-r-reader",
  "permissionCodes": ["oa:document:read"],
  "version": 4
}
```

**空结果：** 角色存在但没有直接权限返回200，`permissionCodes: []`。RBAC0此集合不含继承计算；RBAC1以后需区分“直接授权”“继承得到”“有效权限”，不能把继承权限通过保存变成直接授权。

**失败分支：** 400ID非法；401未登录；403读权限/范围不足；404目标角色不存在；409一致读取不适用。

## 10. PUT `/api/v1/roles/{id}/permissions`

**用途：** 原子替换角色的完整直接权限码集合。

**认证与权限：** 必须登录，要求`rbac:role:grant`；后端检查目标角色、可授出权限集合和管理范围。不能只校验“权限码是否存在”，还应检查调用者能否授出。

**请求示例：**

```http
PUT /api/v1/roles/demo-r-reader/permissions
Content-Type: application/json
```

```json
{
  "permissionCodes": ["oa:document:read", "hr:self:read"],
  "version": 4
}
```

**200响应示例：**

```json
{
  "roleId": "demo-r-reader",
  "permissionCodes": ["oa:document:read", "hr:self:read"],
  "version": 5
}
```

空`permissionCodes: []`是合法撤销全部直接权限；漏字段是400。替换应验证所有元素后一次写入，不允许部分成功。范围配置如果成为正式需求，应先扩充契约，不能在这个请求之外让前端隐式追加范围。

**失败分支：**

| HTTP | 提议错误码 | 含义/前端处理 |
| --- | --- | --- |
| 400 | `INVALID_ARGUMENT`、`UNKNOWN_PERMISSION` | 字段、重复项、未知码错误；保留草稿修正 |
| 401 | `UNAUTHENTICATED` | 会话缺失/失效 |
| 403 | `FORBIDDEN` | 不能修改该角色或授出所选权限 |
| 404 | `ROLE_NOT_FOUND` | 目标角色不存在 |
| 409 | `VERSION_CONFLICT` | 授权已变化；重新读回并复核 |
| 409 | `GRANT_CONFLICT` | 只适用于经过确认并已实现的管理规则，不臆造约束 |

## 11. 前端消费时必须保留的状态

- 区分`loading / success-with-items / success-empty / failed`，加载失败不能当作空权限并声称成功。
- 记录当前用户/角色ID与请求序号；仅当前目标的最新请求可以回填。
- 原授权集合与草稿集合分开保存；PUT成功后才更新“已保存”状态。
- 保存期间阻止重复请求；409需重新读回，不静默覆盖。
- 401清除失效权限并交公共登录；403显示拒绝而不是跳过检查。
- 切换账号时清除旧数据与权限状态。权限读取失败时`permissionsLoaded`不得保持成功状态。
- 服务端改变授权后怎样刷新本人权限，由正式契约决定；不能把原型即时计算当作真实会话更新机制。

## 12. 接口约定与联调清单

| 问题 | 主责 | 当前结论 |
| --- | --- | --- |
| ID、包络、页码、排序和错误码 | 成员6，与成员2/4确认 | 提案待确认 |
| Cookie/Token、401处理与退出 | 成员2/4/6 | 待确认 |
| 管理对象范围、可授出集合、自行/最后管理员策略 | 成员4/6、组长 | 待确认 |
| 目录来源、稳定权限码和清洗歧义 | 成员1/6、老师 | 待确认 |
| 多角色规则、范围合并、权限变化生效时点 | 成员4/6、老师 | 待确认 |
| 并发版本与PUT原子性 | 成员4、成员6验证 | 待实现及实测 |
| 迁移到公共Vue项目的适配器 | 成员2/3 | 公共结构确定后实施 |

成员 6 已把当前可确定内容整理为第一版 OpenAPI 草案；下一步由成员 2、3、4 核对，成员 4 按确认后的契约实现后端，成员 2、3 按同一契约消费，成员 6 验证同一请求的允许、拒绝和状态。**本文件存在，只能说明成员 3 已提出需求，不能说明真实联调、后端安全测试或两套后端一致性已经通过。**
