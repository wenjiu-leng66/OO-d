# 关键序列图

**负责人**：成员 5（胡俊朗）　**依据**：`backend-procedural/` 实际实现与 `Stage1ApiTest` 断言
**版本**：2026-10-18 集成版（`2954385`）

本文件选取六条最能体现设计取舍的流程，每条都能在 2—3 分钟内讲完。

---

## 1. 登录与首次改密

```mermaid
sequenceDiagram
  autonumber
  participant B as 浏览器
  participant C as AuthController
  participant S as AuthService
  participant A as AuthorizationService
  participant M as UserMapper
  participant DB as MySQL/H2

  B->>C: POST /api/v1/auth/login<br/>{username 或 phone, password}
  C->>S: login(request, session)
  S->>M: findByUsernameOrPhone(identifier)
  M->>DB: SELECT ... FROM sys_user WHERE username=? OR phone=?
  DB-->>M: UserPO
  M-->>S: Optional<UserPO>

  alt 账号不存在 / 密码错 / 已停用
    Note over S: 四种失败返回<b>同一个 401</b><br/>不泄漏账号是否存在
    S-->>C: throw ApiException(401)
    C-->>B: 401 统一错误包络
  else 认证通过
    S->>S: passwordEncoder.matches(raw, hash)
    S->>S: session.changeSessionId()<br/>防会话固定攻击
    S->>S: session.setAttribute(userId, credentialVersion)
    S-->>C: Views（仅安全字段，不含 password_hash）
    C-->>B: 200 + Set-Cookie: JSESSIONID<br/>HttpOnly　SameSite=Lax
  end

  Note over B,A: ── 首次登录或被重置后，must_change_password=1 ──

  B->>C: PATCH /api/v1/me/password
  C->>S: password(session, request)
  S->>A: requireUserId(session)
  A->>M: findById(currentUserId)
  A-->>S: UserPO
  S->>S: matches(oldPassword, hash)
  alt 旧密码错误 / 长度不在 8–128 / 与旧密码相同
    S-->>C: 400
  else 校验通过
    S->>M: updatePassword(PBKDF2 hash)<br/>must_change_password = 0
    S->>S: 凭证版本 +1 → <b>其他旧会话下一次请求即 401</b>
    S-->>C: 200
  end
```

### 设计要点

| 决策 | 原因 |
|---|---|
| 四种失败统一 401 | 避免通过错误码枚举出"哪些工号存在" |
| 登录成功轮换 sessionId | 防会话固定（session fixation） |
| 密码用 PBKDF2 而非 BCrypt | BCrypt 在 **72 字节**处截断，8–128 字符的长密码会被悄悄截短 |
| 改密后凭证版本 +1 | 让其他设备上的旧会话立即失效，而不是等超时 |

---

## 2. 越权请求被拒绝（403）

```mermaid
sequenceDiagram
  autonumber
  participant B as 浏览器<br/>(普通员工 SELF)
  participant C as UserController
  participant A as AuthorizationService
  participant UM as UserMapper
  participant RM as RoleMapper
  participant DM as DepartmentMapper

  B->>C: PATCH /api/v1/users/3<br/>{email}
  C->>A: target(session, "rbac:user:edit", "3")

  Note over A: 第 1 步：认证
  A->>UM: findById(currentUserId)
  UM-->>A: 当前用户
  alt 未登录 / 账号停用 / 凭证版本不符
    A-->>C: 401
  end

  Note over A: 第 2 步：权限码
  A->>RM: 已分配且已启用的角色
  RM-->>A: RolePO 列表
  A->>RM: 角色对应的权限与范围
  RM-->>A: (permissionCode, scope) 列表
  A->>A: 同一码取最宽范围 ALL > DEPT > SELF
  alt 没有 rbac:user:edit
    A-->>C: <b>403 FORBIDDEN</b>
  end

  Note over A: 第 3 步：数据范围
  A->>UM: findById("3") 目标用户
  UM-->>A: 目标用户或空
  alt 范围 = SELF
    A->>A: 目标 id == 本人 id ?
  else 范围 = DEPT
    A->>DM: 本人部门子树（沿 parent_id）
    DM-->>A: 部门 id 集合
    A->>A: 目标部门是否在子树内 ?
  else 范围 = ALL
    A->>A: 通过
  end

  alt 不在范围 或 目标不存在
    Note over A: <b>两种情况都返回 403 OUT_OF_SCOPE</b><br/>且 details 为空，不返回任何对象字段
    A-->>C: 403 OUT_OF_SCOPE
    C-->>B: 403 {"error":{"code":"OUT_OF_SCOPE","details":{}}}
  else 在范围内
    A-->>C: UserPO target
    C->>C: 继续业务（改邮箱）
    C-->>B: 200
  end
```

### 设计要点

| 决策 | 原因 |
|---|---|
| **不存在 → 403，而非 404** | 若返回 404，攻击者可用状态码差异**探测**哪些用户 ID 存在 |
| `details` 为空 | 不返回目标对象的任何字段，避免通过错误响应泄漏数据 |
| 先认证 → 再权限码 → 再范围 | 分层短路，越早拒绝成本越低 |
| 查询先裁剪范围再分页 | 否则 `total` 会泄漏范围外的数据量 |

> 对应测试：`ledgerSelfScopeAndUnknownFieldProtection`（第 195–201 行）、
> `departmentUserFiltersCannotEscapeScope`（第 235–242 行）。

---

## 3. 角色授权整组替换与 409 版本冲突

这是全系统**并发最难**的一条路径。

```mermaid
sequenceDiagram
  autonumber
  participant B1 as 管理员A<br/>(version=1)
  participant B2 as 管理员B<br/>(version=1)
  participant C as RoleController
  participant S as RoleService
  participant A as AuthorizationService
  participant RM as RoleMapper
  participant DB as MySQL

  B1->>C: PUT /roles/9/permissions<br/>{grants, version=1}
  B2->>C: PUT /roles/9/permissions<br/>{grants', version=1}

  Note over C,S: 两个请求并发进入

  C->>S: replacePermissions(session, "9", grants)
  S->>A: require(session, "rbac:role:grant")
  A-->>S: 通过

  S->>RM: <b>lockById("9")</b> — SELECT ... FOR UPDATE
  RM->>DB: 行锁
  Note over DB: 第二个请求在此<b>阻塞</b><br/>直到第一个事务提交

  S->>S: 内置角色 / ADMIN 保护检查
  S->>S: 权限码合法性<br/>拒绝未知码、重复码
  S->>S: <b>READ_REQUIRED</b>：非 read 动作<br/>必须同时含该功能点的 read

  S->>RM: compareAndIncrement("9", version=1)
  RM->>DB: UPDATE sys_role<br/>SET permission_version = permission_version + 1<br/>WHERE id=9 AND permission_version=1

  alt 影响行数 = 1（抢到）
    DB-->>RM: 1
    S->>RM: replaceGrants("9", grants)
    RM->>DB: DELETE + INSERT sys_role_permission（同一事务）
    S-->>C: {version: 2, grants}
    C-->>B1: <b>200</b>
  else 影响行数 = 0（已被抢先）
    DB-->>RM: 0
    S->>RM: currentVersion("9")
    RM-->>S: 2
    S-->>C: ApiException.conflict(VERSION_CONFLICT,<br/>{currentVersion: 2})
    C-->>B2: <b>409</b> {"error":{"code":"VERSION_CONFLICT",<br/>"details":{"currentVersion":2}}}
  end

  Note over DB: 事务提交后，第二个请求才拿到行锁<br/>此时再读 version 已是 2，CAS 失败
```

### 设计要点

| 决策 | 原因 |
|---|---|
| `SELECT ... FOR UPDATE` 行锁 | 把并发写**串行化**到同一角色行 |
| **先锁行，再读版本** | 锁内读到的版本一定是最新的，不会用到快照里的过期值 |
| `WHERE permission_version = ?` 条件更新 | CAS 的原子性由数据库保证，不靠应用层判断 |
| 失败返回 `currentVersion` | 前端可据此提示"请刷新"，而不是让用户盲目重试 |
| 整组替换在**同一事务** | 任一步异常全部回滚，不留半套授权 |
| `READ_REQUIRED` 前置校验 | 保证不会出现"能编辑却看不到"的无效授权组合 |

> 对应测试：
> - `simultaneousCasHasExactlyOneWinner` — 两线程并发，断言状态码**恰好 `[200, 409]`**
> - `failedReplacementRollsBackGrantsAndVersion` — mock 写入后抛异常，断言版本未变、授权数为 0
> - `userRoleVersionConflictAndDuplicateValidation` — 重复提交 version=1 → 409 且带 currentVersion=2

### 用户角色替换的额外约束

`UserService.replaceRoles` 比角色授权多一层**锁顺序控制**：

```java
// 统一锁顺序：按角色 ID 数值升序，再对目标用户做 CAS
Set<String> lockIds = new TreeSet<>(Comparator.comparingLong(Long::parseLong));
lockIds.addAll(before);
lockIds.addAll(r.roleIds());
for (String roleId : lockIds) roles.lockById(roleId);
```

**为什么必须排序？** 若 A 请求锁 {5,7}、B 请求锁 {7,5}，两边各持一把、互相等待，就形成**死锁**。
统一升序后，任何两个事务的加锁顺序一致，不可能循环等待。

---

## 4. 权限变更即时生效

```mermaid
sequenceDiagram
  autonumber
  participant Admin as 管理员
  participant Staff as 员工（已登录，持旧会话）
  participant C as 任意业务 Controller
  participant A as AuthorizationService
  participant RM as RoleMapper
  participant UM as UserMapper
  participant DB as MySQL

  Note over Staff: 员工当前持有角色 5（hr:f046:read, ALL）

  Admin->>C: PUT /users/4/roles {roleIds:[], version:N}
  C->>UM: compareAndIncrement + replaceRoles
  UM->>DB: 删除 sys_user_role 关联
  Note over DB: <b>无需通知任何会话</b>

  Staff->>C: GET /ledger/employees（同一个旧 Cookie）
  C->>A: require(session, "hr:f046:read")

  Note over A: 关键：<b>每次请求都重新查数据库</b><br/>不缓存、不信任 Cookie 里的权限
  A->>UM: findById(userId) — 账号仍启用？
  A->>RM: 当前已分配且已启用的角色
  RM->>DB: SELECT ... JOIN sys_user_role
  DB-->>RM: 空集
  RM-->>A: 空集
  A->>A: 权限并集 = ∅
  A-->>C: <b>403</b>
  C-->>Staff: 403（下一次请求即生效）
```

### 设计要点

| 决策 | 原因 |
|---|---|
| **每次请求重新查权限** | 不在会话里缓存权限，避免"改完要等超时" |
| 不信任客户端传来的角色/权限 | 请求体里的 roleId 只作为"目标值"，不参与鉴权计算 |
| 角色停用同样即时生效 | 查询条件带 `role.status = 1` |
| 账号停用即时生效 | `findById` 后检查 `status`，返回 401 |

> 对应测试：
> - `businessRoleUnionAndImmediateRevocation` — 移除全部角色后**下一次请求**即 403
> - `roleCrudStatusDeleteAndBuiltinGuards` — 停用角色后下一次请求即 403
> - `disableInvalidatesExistingSessionOnNextRequest` — 停用账号后旧会话下一次请求即 401

**代价**：每个请求多两次查询（用户 + 角色权限）。
这是**用性能换一致性**的明确取舍——在权限系统里，**撤销不及时比多查两次危险得多**。

---

## 5. 用户角色整组替换与防死锁

这是全系统唯一需要**显式控制加锁顺序**的地方。

```mermaid
sequenceDiagram
  autonumber
  participant B as 管理员
  participant C as UserController
  participant S as UserService
  participant A as AuthorizationService
  participant RM as RoleMapper
  participant UM as UserMapper

  B->>C: PUT /users/4/roles<br/>{roleIds, version}
  C->>S: replaceRoles(session, "4", request)

  S->>A: target(session, "rbac:user:assign-role", "4")
  Note over A: 认证 + 权限码 + 范围 + 目标<br/>（同第 2 节）
  A-->>S: 目标用户

  S->>S: protectedAccount(session, target)
  alt 目标是 admin / 是自己
    S-->>C: 409 PROTECTED_ACCOUNT
  end

  S->>S: roleIds 去重检查
  alt 含重复元素
    S-->>C: 400 VALIDATION
  end

  S->>UM: roleIds("4") — 读取替换前的角色
  UM-->>S: before = {5, 7}

  Note over S: <b>关键：合并「原有 + 新的」并升序排序</b>
  S->>S: lockIds = TreeSet(before ∪ new)<br/>按数值升序
  loop 按升序逐个加锁
    S->>RM: lockById(roleId) — SELECT ... FOR UPDATE
    alt 角色不存在
      RM-->>S: 空
      S-->>C: 400 未知角色
    end
  end

  Note over S: 若 A 请求锁 {5,7}、B 请求锁 {7,5}<br/>不排序会各持一把、互相等待 → <b>死锁</b><br/>升序后所有事务加锁顺序一致

  S->>S: 计算 changed = 新增 ∪ 移除

  alt 目标角色含 ADMIN
    S-->>C: 403 不能把超管角色授予他人
  end
  alt 操作者不是超管，且 changed 含管理角色
    S-->>C: 403 只能分配或撤销业务角色
  end

  S->>UM: compareAndIncrement("4", version)
  alt 版本已变
    UM->>UM: currentRoleVersion("4")
    S-->>C: <b>409 VERSION_CONFLICT</b><br/>{currentVersion}
  else 版本匹配
    S->>UM: replaceRoles("4", roleIds)
    UM-->>S: 完成
    S-->>C: 200 {roleIds, version: N+1}
  end
```

### 为什么必须按 ID 升序加锁

| 场景 | 不排序 | 升序后 |
|---|---|---|
| A 请求锁 {5, 7} | A 持有 5，等 7 | A 先锁 5 再锁 7 |
| B 请求锁 {7, 5} | B 持有 7，等 5 | B 也先锁 5 再锁 7 |
| 结果 | **互相等待 → 死锁** | 一方等锁释放，顺序推进 |

代码中的实现：

```java
// UserService.replaceRoles
Set<String> lockIds = new TreeSet<>(Comparator.comparingLong(Long::parseLong));
lockIds.addAll(before);
lockIds.addAll(r.roleIds());
for (String roleId : lockIds) roles.lockById(roleId);
```

> 对应测试：`simultaneousCasHasExactlyOneWinner`（并发断言 `[200, 409]`）、
> `selfAndUniqueAdminProtection`（管理员与自身保护）。

---

## 6. 角色删除与确认机制

```mermaid
sequenceDiagram
  autonumber
  participant B as 管理员
  participant C as RoleController
  participant S as RoleService
  participant A as AuthorizationService
  participant RM as RoleMapper
  participant UM as UserMapper

  B->>C: DELETE /roles/9?confirm=false
  C->>S: delete(session, "9", confirm)

  S->>A: require(session, "rbac:role:delete")
  A-->>S: 通过

  S->>RM: <b>lockById("9")</b> — 先加锁再判断
  alt 角色不存在
    S-->>C: 404 NOT_FOUND
  end

  alt builtin = 1
    S-->>C: <b>409</b> 内置角色不可删除
  end
  alt code = ADMIN
    S-->>C: <b>409</b> 超管角色不可删除
  end

  S->>RM: lockedUserIds("9")
  RM-->>S: 仍引用该角色的用户列表

  alt 有用户 且 confirm = false
    S-->>C: <b>409 ROLE_HAS_USERS</b>
    Note over B: 前端提示"该角色仍有 N 个用户，<br/>确认删除？"
  else 无用户 或 confirm = true
    S->>RM: 删除 sys_role
    S->>RM: 级联删除 sys_role_permission<br/>与 sys_user_role
    S->>UM: <b>逐一对受影响用户递增 role_version</b>
    S-->>C: 204 No Content
  end
```

### 三个容易讲错的地方

| 点 | 正确说法 |
|---|---|
| **先加锁再判断** | 不是「查完再删」，而是先 `FOR UPDATE`，避免判断与删除之间被并发分配 |
| **confirm 是第二道闸** | 有成员不是直接拒绝，而是**要求调用方确认**；确认后才连带清理 |
| **删除要动用户版本号** | 受影响用户的角色集合变了，其手里的旧 `version` 必须失效，否则并发修改会绕过检查 |

> 对应测试：`roleCrudStatusDeleteAndBuiltinGuards`（409 → 204 且用户版本递增）、
> `concurrentAssignmentMakesUnconfirmedRoleDeleteRefuse`（并发分配使未确认删除被拒）。

---

## 7. 六条流程的共性

| 共性 | 含义 |
|---|---|
| 鉴权集中在 `AuthorizationService` | 所有 Controller 走同一条流水线，口径一致 |
| 先裁剪范围再分页 | 避免 `total` 泄漏 |
| 失败信息最小化 | 统一 401、统一 403、`details` 不带对象字段 |
| 事务只在 Service 层 | Controller 无 `@Transactional` |
| 版本号由数据库 CAS | 不靠应用层判断，避免竞态 |
| 需要多行加锁时统一升序 | 消除循环等待，避免死锁 |
| 删除/替换都递增关联版本号 | 让并发的旧版本请求被 409 拒绝 |
