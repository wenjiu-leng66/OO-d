# 面向功能/过程的后端

由成员 4（王信凯）与成员 5（胡俊朗）共同完成，成员 1 协助。本模块是 RBAC0 的面向功能/过程后端，按 **认证 → 加载用户角色 → 计算有效权限 → 检查约束 → 执行业务** 的过程流水线组织，核心授权逻辑独立实现，与面向对象后端共用同一外部契约。

状态：**已实现并通过集成测试验证。** 47 个集成测试用例全部通过，JaCoCo 指令覆盖率 95%（详见下文「测试与覆盖率」）；若全组统一后需要调整技术选型，见文末「默认假设」。

## 构建、测试与运行

前提：JDK 21、Maven 3.9+。

```powershell
cd backend-procedural

# 运行测试并生成 JaCoCo 覆盖率报告
mvn clean test

# 启动服务
mvn spring-boot:run
```

- 服务默认监听 `http://localhost:8080`。
- Swagger UI（在线接口文档与调试）：`http://localhost:8080/swagger-ui.html`。
- 使用 **H2 内存库**，启动时自动建表并写入合成演示数据，无需安装 MySQL。
- H2 控制台：`http://localhost:8080/h2-console`，JDBC URL 填 `jdbc:h2:mem:rbac`，用户 `sa`，密码留空。

## 演示账号

密码均为 `123456`（仅演示用；正式环境的账号与哈希由成员 1 的导入流程提供）。

| 账号 | 说明 | 角色 / 权限 |
| --- | --- | --- |
| `E001` | 超级管理员 | 拥有全部权限码 |
| `E002` | 部门管理员 | `rbac:department:read`、`rbac:user:read`、`oa:document:read` |
| `E003` | 已停用 | 无法登录（403 `ACCOUNT_DISABLED`） |
| `E004` | 普通员工 | `oa:document:read`、`hr:self:read`（本人范围） |
| `OPS001` | 临时运维 | 无角色，登录后无权限 |

## 接口清单（REST，17 个操作）

资源取自 ER 图实体（users / roles / permissions / resources / departments）；JSON 交互；GET/POST/PUT/DELETE 表达操作；HTTP 状态码表达结果（200/201/204/400/401/403/404/409）。

| # | 方法 | 路径 | 功能 | 所需权限码 | 成功码 |
| --- | --- | --- | --- | --- | --- |
| 1 | POST | `/api/v1/auth/login` | 登录 | 公开 | 200 |
| 2 | POST | `/api/v1/auth/logout` | 退出 | 公开 | 200 |
| 3 | GET | `/api/v1/me` | 当前身份 | 登录即可 | 200 |
| 4 | GET | `/api/v1/me/permissions` | 有效权限 | 登录即可 | 200 |
| 5 | GET | `/api/v1/users` | 用户分页 | `rbac:user:read` | 200 |
| 6 | POST | `/api/v1/users` | 创建用户 | `rbac:user:create` | 201 |
| 7 | GET | `/api/v1/users/{id}` | 用户详情 | `rbac:user:read` | 200 |
| 8 | PUT | `/api/v1/users/{id}/status` | 启停账号 | `rbac:user:update` | 200 |
| 9 | DELETE | `/api/v1/users/{id}` | 删除用户 | `rbac:user:delete` | 204 |
| 10 | GET | `/api/v1/users/{id}/roles` | 用户角色集合 | `rbac:user:read` | 200 |
| 11 | PUT | `/api/v1/users/{id}/roles` | 替换角色（乐观锁） | `rbac:user:assign-role` | 200 |
| 12 | GET | `/api/v1/roles` | 角色分页 | `rbac:role:read` | 200 |
| 13 | GET | `/api/v1/roles/{id}/permissions` | 角色权限集合 | `rbac:role:read` | 200 |
| 14 | PUT | `/api/v1/roles/{id}/permissions` | 替换权限（乐观锁） | `rbac:role:grant` | 200 |
| 15 | GET | `/api/v1/permissions` | 权限目录分页 | `rbac:permission:read` | 200 |
| 16 | GET | `/api/v1/resources` | 资源目录分页 | `rbac:permission:read` | 200 |
| 17 | GET | `/api/v1/departments` | 部门树 | `rbac:department:read` | 200 |

完整契约（请求/响应 Schema、错误包络）：[api/openapi.yaml](../api/openapi.yaml)；启动后可在 Swagger UI 在线调试。

## 接口验证示例

```powershell
# 登录并保存会话 Cookie（Windows 使用 curl.exe 避免 PowerShell 别名）
curl.exe -c cookies.txt -H "Content-Type: application/json" -d '{\"account\":\"E001\",\"password\":\"123456\"}' http://localhost:8080/api/v1/auth/login

# 当前有效权限
curl.exe -b cookies.txt http://localhost:8080/api/v1/me/permissions

# 用户分页
curl.exe -b cookies.txt "http://localhost:8080/api/v1/users?page=1&pageSize=20"

# 创建用户（201 Created）
curl.exe -b cookies.txt -H "Content-Type: application/json" -d '{\"name\":\"测试用户\",\"employeeNo\":\"E900\",\"departmentId\":\"demo-dept-oa\",\"accountType\":\"employee\",\"initialPassword\":\"123456\"}' http://localhost:8080/api/v1/users

# 停用账号
curl.exe -b cookies.txt -X PUT -H "Content-Type: application/json" -d '{\"status\":\"inactive\"}' http://localhost:8080/api/v1/users/<id>/status

# 删除用户（204 No Content）
curl.exe -b cookies.txt -X DELETE http://localhost:8080/api/v1/users/<id>
```

## 代码结构（Controller → Service → Mapper 三层）

包根：`com.municipal.rbac.procedural`。

| 包/目录 | 职责 |
| --- | --- |
| `controller` | ① HTTP 端点、请求绑定（DTO）、调用服务、返回 VO |
| `service` | ② 过程编排与集中授权（`AuthorizationService`） |
| `mapper` | ③ JdbcTemplate 数据访问，只读写表行、返回 PO |
| `dto` | 请求对象（接收数据），带 Jakarta Validation 校验注解 |
| `vo` | 响应对象（返回数据），密码哈希等内部字段永不下发 |
| `po` | 数据库表行映射（7 张表 → 7 个 PO） |
| `error` | 错误包络 `{error:{code,message,requestId,details}}` 与全局异常处理 |
| `config` | BCrypt 密码编码器 |
| `data` | 合成演示数据初始化 |

调用链示例（用户详情）：`users 表行 --UserMapper--> UserPO --UserService(+部门字典/角色名/有效权限)--> UserDetailVO --JSON--> 前端`。

授权判定集中在 `service/AuthorizationService`：认证取会话 → 计算有效权限（已分配且启用角色的权限码去重并集）→ 检查权限码 / 本人范围，默认拒绝。前端返回的权限只用于展示，后端每次请求重新授权。

## 测试与覆盖率（JaCoCo）

- **测试代码**：`src/test/java/com/municipal/rbac/procedural/`——4 个测试类、47 个用例。
  - `AuthApiTest`（8）：登录成功/手机号登录/错误密码/停用账号/参数校验、`/me`、退出；
  - `AccessControlApiTest`（7）：未登录 401、无权限 403、有效权限计算、404；
  - `UserManagementApiTest`（19）：分页过滤、详情、角色替换（成功/冲突/重复/未知）、创建（成功/重复/未知部门/未知角色/super-admin 保护）、状态变更（含本人/超管保护）、删除（含护拦）；
  - `RolePermissionApiTest`（13）：角色列表、角色权限替换（成功/冲突/未知码）、权限目录、资源目录、部门树。
- **运行方式**：`mvn clean test`，报告自动生成于 `target/site/jacoco/index.html`（HTML）与 `target/site/jacoco/jacoco.xml`（机器可读）。
- **当前结果**：指令覆盖率 **95%**（4,265 / 4,472），行覆盖 95%（653 / 689），类覆盖 51/51；Controller、DTO、VO、PO、config 包均为 100%。

## 默认假设（待全组统一后修改）

| 项目 | 当前默认 | 待统一 |
| --- | --- | --- |
| 语言/框架 | Java 21 + Spring Boot 3.3.5 + Maven | Q-02 |
| 数据访问 | Spring JDBC（JdbcTemplate），Mapper 层 | 与 MySQL/MyBatis 二选一 |
| 数据库 | H2 内存库（`MODE=MySQL`） | MySQL 8.4；切数据源 + 建表方言即可 |
| 会话 | HttpSession（Cookie） | Cookie 还是 JWT（Q） |
| 密码 | BCrypt（仅 `spring-security-crypto`，未启用 Spring Security 自动配置） | 是否引入完整 Spring Security |
| ID | 字符串，`demo-` 前缀合成样例；新建用户 `u-` + UUID | 正式 ID 类型 |
| 并发 | `version` 整数乐观锁（用户角色集、角色权限集各自维护） | 成员 6 统一 |
| 数据范围 | 有效权限已计算 `all`/`self` 合并；业务读写中的「仅本部门」过滤未实现 | 范围合并规则 Q-10 |

## 已知限制

- 未实现部门管理员的「仅本部门」数据范围过滤；本人范围已用于授权判定（`hr:self:read` 演示），业务写操作的归属校验按需扩展。
- 未实现权限变更后既有会话的立即失效策略（每次请求实时重算权限，见 `AuthorizationService`）。
- H2 与 MySQL 在 `BOOLEAN`/`LIMIT ? OFFSET ?` 上需按方言核对后再切换。
- 删除用户为物理删除（事务内先删 `user_roles` 再删 `users`）；如需软删除/审计需后续迭代。

设计与解释见 [详细设计说明书](../docs/design/detailed-design.md)、[功能分解与授权设计](../docs/design/member4-procedural-rbac0-design.md)、[成员 4 解释文档](../docs/guides/member4-procedural-rbac0-explained.md)；数据模型见 [数据字典](../docs/database/data-dictionary.md)。
