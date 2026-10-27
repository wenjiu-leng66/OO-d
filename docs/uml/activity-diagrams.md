# 关键决策活动

## 1. 用户对象操作的授权与范围

下图抽象服务层对一个用户目标的检查；认证、权限未通过时立即短路。目录/菜单等无用户目标的查询不执行同一套目标判断，本人信息/改密/退出对强制改密状态有显式例外。

```mermaid
flowchart TD
  S([业务服务收到请求]) --> A{会话账号存在、启用且凭据匹配?}
  A -- 否 --> E401[401 UNAUTHENTICATED]
  A -- 是 --> B{已完成首次改密?}
  B -- 否 --> E403a[403 PASSWORD_CHANGE_REQUIRED]
  B -- 是 --> C{启用的已分配角色提供所需动作权限?}
  C -- 否 --> E403b[403 FORBIDDEN]
  C -- 是 --> T{目标用户存在?}
  T -- 否 --> OUT[403 OUT_OF_SCOPE]
  T -- 是 --> D{该权限码的范围}
  D -- ALL --> OK([返回已授权目标给业务服务])
  D -- DEPT --> E{目标在本人部门及全部下级?}
  D -- SELF --> F{目标ID等于本人ID?}
  D -- 其他 --> OUT
  E -- 否 --> OUT
  E -- 是 --> OK
  F -- 否 --> OUT
  F -- 是 --> OK
```

同码范围取最大值，不同动作独立。`OUT_OF_SCOPE`不返回目标字段，无法区分目标缺失与范围外。业务写入还需额外校验字段、目标迁入部门、唯一性、保护规则与版本，不能把本图通过等同于写入已经完成。来源：[AuthorizationService](../../backend-procedural/src/main/java/com/municipal/rbac/procedural/service/AuthorizationService.java)。

## 2. 角色删除

```mermaid
flowchart TD
  S([DELETE角色请求]) --> A{身份、改密及role:delete权限通过?}
  A -- 否 --> DENY[按原因返回401或403]
  A -- 是 --> LOCK[事务内锁定角色行]
  LOCK --> EXISTS{角色存在?}
  EXISTS -- 否 --> N[404 NOT_FOUND]
  EXISTS -- 是 --> BUILTIN{builtin或ADMIN?}
  BUILTIN -- 是 --> P[409 PROTECTED_ROLE]
  BUILTIN -- 否 --> MEMBERS[锁定并读取引用用户ID]
  MEMBERS --> HAS{有引用用户?}
  HAS -- 否 --> WRITE
  HAS -- 是 --> CONFIRM{confirm=true?}
  CONFIRM -- 否 --> C[409 ROLE_HAS_USERS]
  CONFIRM -- 是 --> WRITE[递增受影响用户role_version]
  WRITE --> DELETE[删除角色权限、用户角色和角色本体]
  DELETE --> SUCCESS([事务提交后204])
  WRITE -. SQL异常 .-> FAIL[回滚版本与关联，返回失败]
  DELETE -. SQL异常 .-> FAIL
```

`RoleService.delete`和用户授角使用同一角色锁；`lockedUserIds`在锁内确定引用集合，`RoleMapper.delete`先更新用户版本再删关系。READ_COMMITTED与CAS/行锁分别承担一致性职责，不能只靠“删除时有外键级联”替代确认及保护。来源：[RoleService](../../backend-procedural/src/main/java/com/municipal/rbac/procedural/service/RoleService.java)、[RoleMapper](../../backend-procedural/src/main/java/com/municipal/rbac/procedural/mapper/RoleMapper.java)。
