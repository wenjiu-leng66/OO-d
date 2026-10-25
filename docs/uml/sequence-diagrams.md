# 关键序列图

**负责人**：成员 5（胡俊朗）　**范围**：一期 RBAC0
**依据**：[需求规格](../requirements/req-spec.md)、[领域模型](domain-model.md)、[设计类图](class-responsibility.md)

> **这张图回答什么**：一个用例在运行时，**哪些对象按什么顺序协作**。
> 选取标准：有并发、有失败分支、有安全含义的流程；不为每个接口画图。

---

## 0. 选图依据

| # | 场景 | 对应用例 / 需求 | 为什么选它 | 位置 |
|---|---|---|---|---|
| 1 | 登录与首次改密 | LOGIN-01/02/04/05 | 会话安全与会话固定防护 | **核心** |
| 2 | 分配用户角色 | GRANT-01/03、USER-07 | 版本冲突、保护规则、加锁顺序 | **核心** |
| 3 | 受保护操作与越权拒绝 | AUTHZ-01/02/03、PORTAL-05 | 鉴权流水线与信息不泄漏 | **核心** |
| 4 | 配置角色权限 | GRANT-02 | 整组替换、`READ_REQUIRED`、事务回滚 | **核心** |
| 5 | 权限变更即时生效 | GRANT-05 | 撤销的时效性 | 附录 |
| 6 | 角色删除与确认 | ROLE-03 | 五道保护与二次确认 | 附录 |

讲解时只讲 **1–4**；5、6 留作追问时的补充。

---

## 1. 登录与首次改密（核心）

**前置**：账号已存在。
**后置**：建立会话；`待首次改密` 状态只能改密。

```mermaid
sequenceDiagram
  autonumber
  actor U as 用户
  participant B as 登录界面
  participant C as AuthController
  participant S as AuthService
  participant A as AuthorizationService
  participant M as UserMapper
  participant DB as 数据库

  U->>B: 输入工号或手机号 + 密码
  B->>C: POST /auth/login
  C->>S: login(identifier, password)
  S->>M: 按工号或手机号查找
  M->>DB: SELECT 用户
  DB-->>M: 用户记录
  M-->>S: 用户或空

  alt 用户不存在 / 密码错 / 停用
    Note over S: 四种失败返回同一个 401<br/>不泄漏账号是否存在
    S-->>C: 认证失败
    C-->>B: 401 用户名或密码错误
  else 认证通过
    S->>S: 校验密码哈希
    S->>S: 轮换会话 ID（防会话固定）
    S->>S: 记录用户与凭证版本
    S-->>C: 安全视图（不含密码字段）
    C-->>B: 200 + 会话 Cookie
  end

  Note over B,A: 首次登录或刚被重置密码

  B->>C: PATCH /me/password
  C->>S: changePassword(旧密码, 新密码)
  S->>A: 取当前用户
  A->>M: 按会话中的用户 ID 查询
  M-->>A: 用户
  A-->>S: 用户

  alt 旧密码错误 / 长度不在 8–128 / 与旧密码相同
    S-->>C: 400
  else 校验通过
    S->>M: 更新密码哈希、清除待改密标记
    S->>S: 凭证版本 +1
    S-->>C: 200
  end
```

### 设计要点

| 决策 | 原因 | 需求 |
|---|---|---|
| 四种失败统一 401 | 避免通过错误码枚举出「哪些工号存在」 | LOGIN-01 |
| 登录成功轮换会话 ID | 防会话固定 | LOGIN-02 |
| 改密后凭证版本 +1 | 其他设备上的旧会话下一次请求即失效 | LOGIN-04 |
| 密码用 PBKDF2 | 避免 BCrypt 在 72 字节处截断长密码 | —— |

**测试**：`credentialFailuresAreIndistinguishable`、`firstLoginMustChangeAndChecksOldPassword`、`longPasswordsDoNotAcceptDifferentSuffixBeyondBcryptLimit`。

---

## 2. 分配用户角色（核心）

**前置**：操作者拥有 `rbac:user:assign-role`，目标在数据范围内。
**后置**：用户角色集合被整组替换，版本 +1。

```mermaid
sequenceDiagram
  autonumber
  actor B as 管理员
  participant C as UserController
  participant S as UserService
  participant A as AuthorizationService
  participant R as RoleMapper
  participant M as UserMapper
  participant DB as 数据库

  B->>C: PUT /users/4/roles<br/>{roleIds, version}
  C->>S: replaceRoles(操作者, 目标用户, roleIds, version)
  S->>A: 检查权限码 + 数据范围 + 目标
  A-->>S: 通过

  S->>S: 保护检查（目标是自己 / 唯一 admin）
  alt 受保护账号
    S-->>C: 409 PROTECTED_ACCOUNT
  end

  S->>M: 读取目标用户当前角色
  M-->>S: 原角色集合

  Note over S: 合并「原有 + 新的」角色，按 ID 升序加锁<br/>避免两个事务交叉等待形成死锁
  loop 按升序逐个加锁
    S->>R: 锁定角色行
    R->>DB: SELECT ... FOR UPDATE
    alt 角色不存在
      S-->>C: 400 未知角色
    end
  end

  S->>S: 校验管理类角色的分配权限
  alt 部门管理员尝试分配管理类角色
    S-->>C: 403
  end

  S->>M: 比较并递增角色版本
  M->>DB: UPDATE ... WHERE version = 提交的版本
  alt 影响行数 = 0（已被抢先）
    M-->>S: 冲突
    S-->>C: 409 VERSION_CONFLICT<br/>{currentVersion}
  else 影响行数 = 1
    S->>M: 整组替换用户角色关联
    S-->>C: 200 {roleIds, version + 1}
  end
```

### 设计要点

| 决策 | 原因 | 需求 |
|---|---|---|
| 整组替换 + 版本号 | 避免「先读后写」覆盖别人的修改 | GRANT-01 |
| 先锁行、再读版本 | 锁内读到的版本一定是最新的 | GRANT-01 |
| 按角色 ID 升序加锁 | 消除循环等待，避免死锁 | —— |
| 保护自己与唯一 admin | 防止管理员把自己锁死 | USER-07 |
| 部门管理员不能分配管理类角色 | 防止权限自我放大 | GRANT-03 |

**测试**：`simultaneousCasHasExactlyOneWinner`、`userRoleVersionConflictAndDuplicateValidation`、`selfAndUniqueAdminProtection`、`departmentAdminCannotGrantOrRemoveManagementRoles`。

---

## 3. 受保护操作与越权拒绝（核心）

**前置**：用户已登录。
**后置**：在范围内执行；范围外返回 403，且不返回目标字段。

```mermaid
sequenceDiagram
  autonumber
  actor U as 普通员工
  participant B as 界面
  participant C as UserController
  participant A as AuthorizationService
  participant M as UserMapper
  participant R as RoleMapper
  participant D as DepartmentMapper

  U->>B: 修改用户 3 的邮箱
  B->>C: PATCH /users/3
  C->>A: 检查 rbac:user:edit 与目标 3

  Note over A: 第 1 步：认证
  A->>M: 按会话中的用户 ID 查询
  M-->>A: 当前用户
  alt 未登录 / 已停用 / 凭证版本不符
    A-->>C: 401
  end

  Note over A: 第 2 步：权限码
  A->>R: 已分配且已启用的角色
  R-->>A: 角色列表
  A->>R: 角色的权限与范围
  R-->>A: (权限码, 范围) 列表
  A->>A: 同一权限码取最宽范围
  alt 没有 rbac:user:edit
    A-->>C: 403 FORBIDDEN
  end

  Note over A: 第 3 步：数据范围
  A->>M: 查询目标用户
  M-->>A: 目标用户或空
  alt 范围 = SELF
    A->>A: 目标是本人？
  else 范围 = DEPT
    A->>D: 本人部门子树
    D-->>A: 部门集合
    A->>A: 目标部门在子树内？
  else 范围 = ALL
    A->>A: 通过
  end

  alt 不在范围 或 目标不存在
    Note over A: 两种情况都返回 403 OUT_OF_SCOPE<br/>details 为空
    A-->>C: 403 OUT_OF_SCOPE
    C-->>B: 403（不返回任何对象字段）
  else 在范围内
    A-->>C: 目标用户
    C->>C: 执行业务（修改邮箱）
    C-->>B: 200
  end
```

### 设计要点

| 决策 | 原因 | 需求 |
|---|---|---|
| 先认证 → 再权限码 → 再范围 | 分层短路，越早拒绝成本越低 | AUTHZ-01 |
| 不存在与范围外统一 403 | 防止用状态码差异探测目标是否存在 | AUTHZ-03 |
| `details` 为空 | 不通过错误响应泄漏对象字段 | AUTHZ-03 |
| 查询先裁剪范围再分页 | 否则 `total` 会泄漏范围外的数据量 | USER-01 |

**测试**：`ledgerSelfScopeAndUnknownFieldProtection`、`departmentUserFiltersCannotEscapeScope`。

---

## 4. 配置角色权限与版本冲突（核心）

**前置**：操作者是超级管理员（只有超管能改角色授权）。
**后置**：授权整组替换，版本 +1；失败则完全回滚。

```mermaid
sequenceDiagram
  autonumber
  actor A1 as 管理员 A（version=1）
  actor A2 as 管理员 B（version=1）
  participant C as RoleController
  participant S as RoleService
  participant R as RoleMapper
  participant DB as 数据库

  A1->>C: PUT /roles/9/permissions {grants, version=1}
  A2->>C: PUT /roles/9/permissions {grants', version=1}

  C->>S: replacePermissions(角色 9, grants, version)
  S->>S: 权限码合法性检查（未知码 / 重复码）
  S->>S: READ_REQUIRED 检查（非查看操作必须同时有查看）
  S->>R: 锁定角色行
  R->>DB: SELECT ... FOR UPDATE
  Note over DB: 第二个请求在此阻塞<br/>直到第一个事务提交

  S->>R: 比较并递增授权版本
  R->>DB: UPDATE ... WHERE permission_version = 1
  alt 影响行数 = 1（先到）
    R-->>S: 成功
    S->>R: 整组替换角色授权
    S-->>C: {version: 2, grants}
    C-->>A1: 200
  else 影响行数 = 0（被抢先）
    R-->>S: 冲突
    S->>R: 读取当前版本
    R-->>S: 2
    S-->>C: 409 VERSION_CONFLICT {currentVersion: 2}
    C-->>A2: 409
  end

  Note over DB: 事务提交后第二个请求才拿到行锁<br/>此时版本已是 2，条件更新失败
```

### 设计要点

| 决策 | 原因 | 需求 |
|---|---|---|
| 行锁 + 版本号 CAS | 把并发写串行化，且失败方可感知 | GRANT-02 |
| 整组替换在同一事务 | 任一步失败全部回滚，不留半套授权 | GRANT-02 |
| `READ_REQUIRED` 前置校验 | 避免「能编辑却看不到」的无效授权组合 | GRANT-02 |
| 冲突返回 `currentVersion` | 前端提示刷新，而不是盲目重试 | GRANT-01 |

**测试**：`failedReplacementRollsBackGrantsAndVersion`、`grantValidationVersionAndImmediateChange`、`simultaneousCasHasExactlyOneWinner`。

---

## 5. 权限变更即时生效（附录）

```mermaid
sequenceDiagram
  autonumber
  actor Admin as 管理员
  actor Staff as 员工（持旧会话）
  participant C as 业务 Controller
  participant A as AuthorizationService
  participant R as RoleMapper
  participant M as UserMapper

  Admin->>C: 撤销员工的角色
  C->>M: 删除用户角色关联
  Note over M: 不需要通知任何会话

  Staff->>C: 访问受保护接口（同一个旧 Cookie）
  C->>A: 检查权限码
  Note over A: 每次请求重新查数据库<br/>不缓存、不信任 Cookie 里的权限
  A->>M: 账号仍启用？
  A->>R: 已分配且已启用的角色
  R-->>A: 空集
  A->>A: 权限并集为空
  A-->>C: 403
  C-->>Staff: 403（下一次请求即生效）
```

**设计取舍**：每个请求多两次查询，用性能换一致性。撤销不及时比多查两次危险得多（GRANT-05）。

---

## 6. 角色删除与确认（附录）

```mermaid
sequenceDiagram
  autonumber
  actor B as 管理员
  participant C as RoleController
  participant S as RoleService
  participant R as RoleMapper
  participant M as UserMapper

  B->>C: DELETE /roles/9?confirm=false
  C->>S: delete(角色 9, confirm)

  S->>R: 先锁定角色行
  alt 角色不存在
    S-->>C: 404
  end
  alt 内置角色
    S-->>C: 409 内置角色不可删除
  end
  alt 超级管理员角色
    S-->>C: 409 超管角色不可删除
  end

  S->>R: 查询仍引用该角色的用户
  R-->>S: 用户列表

  alt 有用户 且 confirm = false
    S-->>C: 409 ROLE_HAS_USERS
    Note over B: 前端提示「仍有 N 个用户，确认删除？」
  else 无用户 或 confirm = true
    S->>R: 删除角色与授权关联
    S->>R: 删除用户角色关联
    S->>M: 逐一对受影响用户递增角色版本
    S-->>C: 204
  end
```

**三个容易讲错的地方**：

| 点 | 正确说法 |
|---|---|
| 先加锁再判断 | 避免「判断时没人用，删除时正好被分配」的竞态 |
| `confirm` 是第二道闸 | 有用户不是直接拒绝，而是要求调用方确认 |
| 删除要动用户版本号 | 受影响用户的角色集合变了，旧版本必须失效 |

**测试**：`roleCrudStatusDeleteAndBuiltinGuards`、`concurrentAssignmentMakesUnconfirmedRoleDeleteRefuse`。

---

## 7. 四条核心流程的共性

| 共性 | 含义 |
|---|---|
| 鉴权集中在 `AuthorizationService` | 所有 Controller 走同一条流水线，口径一致 |
| 先裁剪范围再分页 | 避免 `total` 泄漏 |
| 失败信息最小化 | 统一 401、统一 403、`details` 不带对象字段 |
| 事务只在 Service 层 | Controller 无事务注解 |
| 版本号由数据库 CAS | 不靠应用层判断，避免竞态 |
| 需要多行加锁时统一升序 | 消除循环等待，避免死锁 |
