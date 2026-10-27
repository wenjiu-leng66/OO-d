# 业务概念与关系

本图表达一期 RBAC0 的业务概念与持久化关系，不是 JPA 映射或 `backend-oo/` 的实现类图。数据范围属于“角色—权限授予关系”，不属于用户或整个角色。

```mermaid
classDiagram
  direction LR
  class Department {
    name
    deptType
    parentId
    managerUserId
  }
  class User {
    username
    realName
    status
    mustChangePassword
    roleVersion
  }
  class Role {
    code
    name
    enabled
    builtin
    permissionVersion
  }
  class UserRoleAssignment {
    userId
    roleId
  }
  class RolePermissionGrant {
    roleId
    permissionId
    dataScope
  }
  class Permission {
    code
    functionId
    actionId
  }
  class ApplicationSystem {
    code
    name
  }
  class Module {
    name
  }
  class FunctionPoint {
    code
    name
  }
  class Action {
    code
    name
  }
  class DataScope {
    <<enumeration>>
    ALL
    DEPT
    SELF
  }

  Department "0..1" --> "0..*" Department : 上级到下级
  Department "0..1" -- "0..*" User : 所属部门
  Department "0..*" --> "0..1" User : 负责人
  User "1" -- "0..*" UserRoleAssignment : 分配
  Role "1" -- "0..*" UserRoleAssignment : 被分配
  Role "1" -- "0..*" RolePermissionGrant : 直接授予
  Permission "1" -- "0..*" RolePermissionGrant : 被授予
  ApplicationSystem "1" -- "0..*" Module : 包含
  Module "1" -- "0..*" FunctionPoint : 包含
  FunctionPoint "1" -- "0..*" Permission : 动作组合
  Action "1" -- "0..*" Permission : 动作组合
  RolePermissionGrant --> DataScope : 逐授权保存范围
```

- 部门根的 `parent_id` 可空，用户 `dept_id` 也可空。一个部门最多一个负责人；数据库没有限制同一用户只能负责一个部门。姓名只展示和核对，关系使用稳定 ID。
- `sys_user.status` 只有启用/停用；`must_change_password` 是独立标记。“已停用且仍待改密”合法，不能把待改密画成第三种互斥账号状态。
- `role_version` 管用户的完整角色集合，`permission_version` 管角色的完整授权集合；都不是会话凭据版本。
- 功能点与六动作在导入/初始化时生成 `sys_permission` 实际记录，权限码稳定为 `系统码:fNNN:动作`；不由客户端任意创建。
- 同码授权取 `ALL > DEPT > SELF`，不同动作各自保留范围；`rbac:*` 管理码由已授予清单码在服务层派生，不另直接写入目录表。

物理字段、唯一键和删除行为以[数据库设计](../database/database-design.adoc)、[数据字典](../database/data-dictionary.md)和[V001迁移](../../database/migrations/V001__create_core_tables.sql)为准。继承边、约束和审计表不属于当前模型。
