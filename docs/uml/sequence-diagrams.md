# 关键序列

以下为真实 Java 后端的 API 调用链，路径均写出 `/api/v1`。调用方可以是Swagger或HTTP客户端；当前Vue原型仍调用内存mock，不能把图中的网络链路误认为已完成前后端联调。失败会短路返回，不继续执行后续写入。

## 1. 登录与首次改密

```mermaid
sequenceDiagram
  actor U as API调用方
  participant C as AuthController
  participant S as AuthService
  participant A as AuthorizationService
  participant M as UserMapper
  participant P as PasswordEncoder
  participant H as HttpSession
  participant MC as MeController
  U->>C: POST /api/v1/auth/login<br/>{account,password}
  C->>C: DTO格式校验
  C->>S: login(body,session)
  S->>H: 清除旧身份及凭据标记
  S->>M: findByAccount(account)
  M-->>S: 用户或空
  S->>P: matches(password,用户摘要或dummyHash)
  alt 不存在、停用或口令不匹配
    S-->>U: 401 INVALID_CREDENTIALS
  else 认证通过
    S->>H: 写入userId及密码摘要标记
    S->>M: 更新最后登录时间
    S-->>C: 本人安全视图、权限与改密标记
    C->>C: request.changeSessionId()
    C-->>U: 200及JSESSIONID
  end
  U->>MC: PATCH /api/v1/me/password<br/>{oldPassword,newPassword}
  MC->>MC: DTO校验新密码8至128字符
  MC->>S: password(session,body)
  S->>A: current(session)
  A->>M: 读取账号并核对会话凭据
  A-->>S: 当前用户，或401
  S->>P: 验证旧密码
  alt 旧密码错误或新旧相同
    S-->>U: 400且不更新
  else 校验通过
    S->>P: encode(newPassword)
    S->>M: 更新摘要，清除mustChangePassword
    S->>H: 更新当前会话的摘要标记
    MC-->>U: 200 success
  end
  Note over A,H: 凭据标记是服务端密码摘要，不是整数版本号；其他旧会话在下一请求核对时失效
```

首次改密前，本人信息/改密/退出仍允许；其他业务入口经 `ready/require` 返回403。登录失败锁定属于后续阶段，本图不声称实现锁定计数。参见 [AuthController](../../backend-procedural/src/main/java/com/municipal/rbac/procedural/controller/AuthController.java)、[MeController](../../backend-procedural/src/main/java/com/municipal/rbac/procedural/controller/MeController.java)、[AuthService](../../backend-procedural/src/main/java/com/municipal/rbac/procedural/service/AuthService.java)。

## 2. 整组替换用户角色

```mermaid
sequenceDiagram
  actor U as 管理API调用方
  participant C as UserController
  participant S as UserService
  participant A as AuthorizationService
  participant R as RoleMapper
  participant M as UserMapper
  U->>C: PUT /api/v1/users/{id}/roles<br/>{roleIds,version}
  C->>S: replaceRoles(session,id,body)
  S->>A: 认证、assign-role权限、范围与目标
  A-->>S: 目标，或401/403
  S->>S: 本人/admin保护、重复ID检查
  S->>M: 读取原已分配角色集合
  M-->>S: before
  loop 原有与目标角色并集，按数字ID升序
    S->>R: lockById(roleId)
    R-->>S: 当前角色，未知角色则400
  end
  S->>S: 检查ADMIN禁授及管理角色增撤边界
  S->>M: compareAndIncrement(id,version)
  alt 旧版本
    S->>M: 读取当前用户角色版本
    S-->>U: 409 VERSION_CONFLICT，集合不变
  else CAS成功
    S->>M: replaceRoles(id,完整roleIds)
    S->>M: 读取更新后的集合和版本
    S-->>U: 200 完整集合及新version
  end
  Note over S,M: READ_COMMITTED事务覆盖版本与集合；写入异常回滚。锁后未重新读取before集合，旧页面依靠CAS拒绝
```

角色行锁与删除流程协调；版本检测保护读取后的编辑意图；统一加锁顺序降低交叉锁冲突，不保证数据库所有死锁场景均被消除。参见 [UserService.replaceRoles](../../backend-procedural/src/main/java/com/municipal/rbac/procedural/service/UserService.java)。

## 3. 台账编辑与范围拒绝

```mermaid
sequenceDiagram
  actor U as API调用方
  participant C as PortalController
  participant L as LedgerService
  participant A as AuthorizationService
  participant M as UserMapper
  participant R as RoleMapper
  participant D as DepartmentMapper
  U->>C: PATCH /api/v1/ledger/employees/{id}<br/>{phone或email}
  C->>L: update(session,id,body)
  L->>A: target(session,hr:f046:edit,id)
  A->>M: 读取当前账号并核对凭据
  break 未登录、停用、凭据失效或未完成首次改密
    A-->>U: 401或403，停止后续检查
  end
  A->>M: 取得启用的已分配角色ID
  A->>R: 各角色直接授权
  A->>A: 同码范围合并、管理码派生、动作权限检查
  break 缺少hr:f046:edit
    A-->>U: 403 FORBIDDEN
  end
  A->>M: 读取目标用户
  opt 范围为DEPT
    A->>D: subtree(本人部门)
  end
  alt 目标不存在或不在范围
    A-->>U: 403 OUT_OF_SCOPE，不返回目标字段
  else 目标可修改
    A-->>L: 目标用户
    L->>L: 非空体、字段白名单、类型及格式校验
    L->>M: 非空电话唯一性检查
    L->>M: updateContact(id,phone,email)
    L-->>U: 200 更新后的台账安全字段
  end
```

图按职责展开成功路径；401/403在对应检查处立即返回，未通过认证/权限时不会继续查询目标。电话/邮箱以外字段返回400；缺字段保留旧值，显式null或空文本清空。若检查用户管理资料修改，实际入口另为 `PUT /api/v1/users/{id}`，权限为 `rbac:user:update`。参见 [LedgerService](../../backend-procedural/src/main/java/com/municipal/rbac/procedural/service/LedgerService.java)。

## 4. 角色授权与旧版本冲突

```mermaid
sequenceDiagram
  actor U as 管理API调用方
  participant C as RoleController
  participant S as RoleService
  participant A as AuthorizationService
  participant R as RoleMapper
  participant K as CatalogMapper
  Note over U,R: 两管理员都读取version=1；每次提交均走以下流程
  U->>C: PUT /api/v1/roles/{id}/permissions<br/>{grants,version:1}
  C->>S: replacePermissions(session,id,body)
  S->>A: require(rbac:role:grant)
  S->>R: lockById(id)
  Note over S,R: 其他事务持锁时等待；拿锁后再读取并校验当前角色
  S->>S: ADMIN保护
  S->>K: 合法清单权限白名单
  S->>S: 未知/重复码、同功能read依赖检查
  S->>R: compareAndIncrement(id,1)
  alt 当前version仍为1
    R-->>S: 成功并更新到2
    S->>R: replaceGrants(id,完整grants)
    S-->>U: 200 新集合及version=2
  else 另一事务已提交version=2
    R-->>S: CAS失败
    S->>R: currentVersion(id)
    S-->>U: 409 VERSION_CONFLICT，currentVersion=2
  end
  Note over S,R: 版本与授权在同一READ_COMMITTED事务中；实际写入失败必须全部回滚
```

该图展示同一请求流程的两种结果，不表示两个请求各自都能成功递增。非法参数、保护或权限失败在CAS前拒绝。生产接口没有模拟失败开关，数据库失败回滚由集成测试注入异常验证。参见 [RoleService.replacePermissions](../../backend-procedural/src/main/java/com/municipal/rbac/procedural/service/RoleService.java)。

## 5. 撤权在下一请求生效

```mermaid
sequenceDiagram
  actor Admin as 管理API调用方
  actor Staff as 员工API调用方
  participant UC as UserController
  participant US as UserService
  participant BC as PortalController
  participant LS as LedgerService
  participant A as AuthorizationService
  participant M as UserMapper
  Admin->>UC: PUT /api/v1/users/{id}/roles<br/>完整集合及当前version
  UC->>US: replaceRoles
  US->>A: 操作者权限、范围和保护检查
  US->>M: CAS及关系替换，事务提交
  UC-->>Admin: 200
  Staff->>BC: GET /api/v1/ledger/employees<br/>继续携带原JSESSIONID
  BC->>LS: list(session,筛选及分页)
  LS->>A: require(hr:f046:read)
  A->>M: 查当前账号与启用的已分配角色ID
  M-->>A: 撤权后的角色集合
  A->>A: 重算有效权限
  alt 已无该动作权限
    A-->>Staff: 403
  else 其他启用角色仍提供该码
    A-->>LS: 该码剩余角色合并后的范围
    LS-->>Staff: 按新范围返回结果
  end
```

只移除一个角色时，其他角色仍可能提供相同权限，不能把“撤角色”一律画成403。会话只保存身份与凭据标记，不缓存角色授权快照；停用账号/密码变更则属于401的身份失效。参见 [AuthorizationService](../../backend-procedural/src/main/java/com/municipal/rbac/procedural/service/AuthorizationService.java)。

## 6. 角色删除与确认

```mermaid
sequenceDiagram
  actor U as 管理API调用方
  participant C as RoleController
  participant S as RoleService
  participant A as AuthorizationService
  participant R as RoleMapper
  participant DB as 数据库
  U->>C: DELETE /api/v1/roles/{id}?confirm=false或true
  C->>S: delete(session,id,confirm)
  S->>A: require(rbac:role:delete)
  S->>R: lockById(id)
  R->>DB: SELECT角色 FOR UPDATE
  alt 角色不存在
    S-->>U: 404
  else 内置角色或ADMIN
    S-->>U: 409 PROTECTED_ROLE
  else 可删除角色
    S->>R: lockedUserIds(id)
    R->>DB: 锁定并读取关联用户ID
    alt 有用户且未确认
      S-->>U: 409 ROLE_HAS_USERS
    else 无用户或明确确认
      S->>R: delete(id,关联用户ID集合)
      R->>DB: 先递增各受影响用户role_version
      R->>DB: 删除角色权限、用户角色及角色
      S-->>U: 204 No Content
    end
  end
  Note over S,DB: READ_COMMITTED事务；与授角共用角色锁，失败回滚版本和关联
```

版本更新和删除SQL均由 `RoleMapper.delete` 执行，不经 `UserMapper`。401/403可能在角色锁前发生，不能把所有失败都称为409。参见 [RoleService.delete](../../backend-procedural/src/main/java/com/municipal/rbac/procedural/service/RoleService.java)、[RoleMapper](../../backend-procedural/src/main/java/com/municipal/rbac/procedural/mapper/RoleMapper.java)。
