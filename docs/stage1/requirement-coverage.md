# 一期需求覆盖与验收追溯

来源：[需求规格附录 A](../requirements/req-spec.md) 的 41 条功能需求。以下是实现入口及应执行的检查，不是 41 条“已通过”声明。实际状态、版本与证据统一填入 [验证记录](verification-record.md)。所有接口省略 `/api/v1`；路径以当前 [OpenAPI](../../api/openapi.yaml) 为准。

## 41 条功能需求

| 需求 | 集成实现入口 | 必须检查的结果 |
| --- | --- | --- |
| LOGIN-01 | `POST /auth/login`、`AuthService` | 工号/手机号登录同人；不存在/密码错误/停用/锁定的提示统一；自动锁定属于三期 |
| LOGIN-02 | `AuthService`、`AuthorizationService` | 全部已分配且启用的角色生效；无角色可登录但无权限 |
| LOGIN-03 | `POST /auth/logout` | 旧 Cookie 下一次请求 401 |
| LOGIN-04 | `PATCH /me/password` | 错旧密码拒绝；成功后旧密码失效、新密码可用 |
| LOGIN-05 | `mustChangePassword`、`AuthorizationService.ready` | 首次/重置后只能本人信息、改密、退出，其他 403 |
| PORTAL-01 | `GET /portal/menu`、原型 `/portal` | 只含有 read 权限的系统/模块/功能点；无权详情直接请求被拒绝 |
| PORTAL-02 | `GET /portal/functions/{functionCode}`、原型功能页 | 六动作与范围；无权按钮置灰；普通员工考勤 read/edit=SELF |
| PORTAL-03 | `GET /me/permissions` | 码、范围、来源角色与后端实际鉴权一致 |
| PORTAL-04 | `GET /ledger/employees` | 真实后端按查看范围分页；普通员工仅本人；部门经理查看 ALL |
| PORTAL-05 | `PATCH /ledger/employees/{id}` | 只改电话/邮箱；SELF/DEPT/ALL与篡改ID拒绝；不泄露范围外对象 |
| PORTAL-06 | 原型员工台账页、功能动作元数据 | 新增/删除/审批/导出按权限展示且点击说明占位；不伪装业务保存 |
| USER-01 | `GET /users`、`UserService` | 服务端范围、部门含下级、工号/姓名/角色筛选、分页及统计一致 |
| USER-02 | `POST /users` | 工号唯一；手机号可空、非空唯一；初始密码来自配置，不能顺便授角色 |
| USER-03 | `PUT /users/{id}` | 仅基本信息可改；目标和迁入部门均在管理范围 |
| USER-04 | `PATCH /users/{id}/status` | 停用后不能登录；既有会话下一请求 401 |
| USER-05 | `POST /users/{id}/password-reset` | 恢复配置初始密码、mustChangePassword=true；旧会话失效 |
| USER-06 | `DELETE /users/{id}`、V001外键 | 清理用户角色关系，负责人外键置空，无孤立数据 |
| USER-07 | `UserService`账号保护 | 自己/唯一admin的停用、删除、角色变更规则；拒绝409、数据不变 |
| DEPT-01 | `GET /departments`、迁移和离线导入 | 五类型、父子、负责人ID、直接员工数/含下级数；正式全量111个 |
| DEPT-02 | `GET /departments/{id}/members` | 包含全部下级；范围外或不存在统一拒绝 |
| ROLE-01 | `GET/POST /roles`、`PATCH /roles/{id}` | 唯一编码、名称/说明修改；编码不可随显示名变动 |
| ROLE-02 | `PATCH /roles/{id}/status` | 停用后下一请求即不贡献权限；恢复后有效 |
| ROLE-03 | `DELETE /roles/{id}`、角色保护 | 六内置角色不可删；ADMIN不可停用/改授权；自建角色带用户需确认后级联清理 |
| ROLE-04 | `GET /roles/{id}/users` | 全部可见角色成员与关系匹配；部门管理员不泄露范围外人员 |
| CAT-01 | `GET /catalog/functions`、`/permissions`、`/resources` | 只读、关键词/系统/模块筛选与分页；18/115/359/2154 |
| CAT-02 | 固定系统/功能编号CSV、`sys_permission` | `系统码:fNNN:动作`不随名称或行顺序变化 |
| CAT-03 | `AuthorizationService.managementCodes`、[映射表](../standards/api-contract.md) | 清单系统18授权派生rbac；公司领导可读但写接口403 |
| GRANT-01 | `GET/PUT /users/{id}/roles` | 全集合、version原子替换；两管理员旧版409，无部分写入 |
| GRANT-02 | `GET/PUT /roles/{id}/permissions`、事务 | grants带scope；缺read/未知码/重复码拒绝；旧版409；中途DB失败全部回滚 |
| GRANT-03 | `UserService.replaceRoles`、管理类角色识别 | 部门管理员仅范围内业务角色；不能分配/撤销管理角色或改角色授权 |
| GRANT-04 | `AuthorizationService`有效权限合并 | 同码最大ALL>DEPT>SELF；不同码互不放大 |
| GRANT-05 | 逐请求读取账号、角色、授权 | 撤角色/改授权/停角色无需重新登录，下一请求生效 |
| AUTHZ-01 | 各Controller/Service、`AuthorizationService` | 未登录401、无权403；认证→权限→范围→目标，写请求也检查 |
| AUTHZ-02 | 默认拒绝、`UNKNOWN_PERMISSION` | 未授权/未知码/未加载权限不可放行；不能直接授rbac派生码 |
| AUTHZ-03 | `target`、`visible`、`covers`、`coversDepartment` | SELF为本人；部门成员SELF仅本人、SELF不能新建他人；DEPT含全部下级；修改URL/请求体不能越权 |
| IMPORT-01 | `database/seeds/import_excel.py` | 正式部门表111个组织及负责人ID匹配，不按姓名合并同名人 |
| IMPORT-02 | 导入脚本、功能固定编号、核对SQL | 18/115/359/2154；原清单五角色3549条可逐行追溯；平台另6条 |
| IMPORT-03 | 导入脚本范围规则 | 经理台账read=ALL/edit=DEPT；普通员工薪酬read=SELF |
| IMPORT-04 | 员工表导入与初始角色规则 | 正式10000员工+admin；公司领导10人、副职普通员工、负责人角色正确 |
| IMPORT-05 | 重复导入与核对指纹 | 同输入两次业务快照一致（排除随机盐哈希/时间）；清洗可定位源表 |
| IMPORT-06 | 交互密码/环境、gitignore与仓库检查 | 初始密码参数/交互传入；原Excel、真实姓名电话、密码哈希不提交 |

## 数据规模的证据边界

完整目录可由已固定的 CSV 合成并验证为 18/115/359/2154；这证明目录结构，不证明真实授权与员工全量导入正确。后端合成演示账号用于现场拒绝/允许检查，不代表原始 Excel 的 10000 员工。正式验收须在受控环境执行原始资料导入并保存脱敏核对结果。

`IMPORT-02` 的 3549 是原清单五角色的角色权限关系；平台部门管理员六条不混入该统计，完整清单包含对应功能点时数据库总数为 3555。合成小样例只有存在的功能点才产生平台补充，不能强行套用3555。

## 一期非功能及交付检查

| 要求 | 实现/检查入口 | 证据 |
| --- | --- | --- |
| 密码慢哈希与导入兼容 | 新密码及导入统一带前缀PBKDF2；兼容既有带前缀哈希校验 | 后端登录/改密与长密码尾缀区分测试、导入账号实测 |
| 参数化SQL、外键/CHECK/唯一键 | JDBC绑定参数、正式V001迁移 | 单元/集成及真MySQL约束拒绝记录 |
| 事务和乐观锁 | 用户角色/角色授权整组替换、compare-and-increment | 409并发、失败后集合和版本均不变 |
| 有效权限集中计算 | `AuthorizationService` | 多角色范围合并与即时生效测试 |
| 数据迁移与可恢复 | V001、U001、MySQL持久卷 | 迁移/回滚/重建、重启后数据留存 |
| 性能 | 正式全量MySQL | 鉴权≤10ms、常用列表/保存≤1s：数据库端已在全量核对库实测通过（[证据](verification-record.md#全量数据库性能证据)）；全量HTTP端到端待执行 |
| 公开仓库安全 | gitignore、仓库检查、差异审查 | 不含原Excel/真实员工/哈希/密钥 |
| 服务器可以运行 | `deploy/compose.yaml`、[服务器步骤](../../deploy/README.md) | 上传版本、compose健康、外网HTTP、重启持久化，缺一不可 |
| 完整界面原型 | [页面清单](../design/stage1-ui-prototype.md) | 按不同身份演示，不以模拟数据代替后端证据 |
| API设计与实现一致 | [OpenAPI](../../api/openapi.yaml)、[接口约定](../standards/api-contract.md) | 语法/引用校验及真实请求字段/状态一致 |

后续阶段的继承、SSD/DSD/基数、主动选择激活角色、审计日志、两后端等价与函数式模块在本次覆盖表中标为范围外，不计作一期缺陷。
