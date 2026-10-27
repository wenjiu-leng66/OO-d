# 一期代码阅读路线

从可观察的请求出发，沿着“入口 → 规则 → 数据 → 返回 → 测试”读代码。需求见 [41 条追溯](requirement-coverage.md)，结构图和模块契约见 [结构化设计](../design/structured-design.md)。以下路径均从仓库根目录算起。

## 1. 认证、会话与首次改密

1. `backend-procedural/.../controller/AuthController.java`：接收登录/退出，处理服务端会话与登录后的会话 ID 轮换。
2. `service/AuthService.java`：工号或手机号查找、统一失败提示、密码哈希校验；改密验证旧口令、更新凭证并处理其他旧会话。
3. `config/SecurityConfig.java`：新口令 PBKDF2 编码及带前缀历史哈希兼容。
4. `service/AuthorizationService.java` 的 `current`、`ready`：每次重读账号及凭证，停用/重置后的旧会话返回 401；首次改密只允许规定的少量入口。
5. `dto/Requests.java` 与 `error/GlobalExceptionHandler.java`：输入校验、错误包络及不向响应暴露内部异常。

需要能回答：为什么退出不是只清前端变量？为什么 Cookie 里不存角色？为什么登录成功后还要逐请求查账号？为什么首次改密必须后端也拦？哪些测试隔离了 Mapper，哪些使用 H2、真实会话？

## 2. 有效权限与数据范围

`AuthorizationService` 从账号已分配且启用的角色得到清单授权；同码取 `ALL > DEPT > SELF`，不同码互不放大。`managementCodes` 将系统管理清单码派生为 `rbac:*`，保留来源范围；请求体不能直接给自己授派生码。

`visible`、`covers`、`target` 处理可见人员和具体目标；部门子树用于 DEPT，SELF 只按本人 ID。`coversDepartment` 是部门归属辅助规则，不能替代“能否创建他人”的业务判断，`UserService.create` 另拒绝 SELF 创建。

沿 `UserController → UserService → UserMapper` 或 `PortalController → LedgerService → UserMapper` 检查：先取得操作权限和范围，再裁剪可见人员，然后筛选、统计和分页。范围外与不存在目标统一拒绝，避免用 ID 探测越权对象。查看 ALL 不意味着编辑 ALL。

当前部分列表调用 `findAll` 后在 Java 中过滤。这符合当前代码的裁剪顺序，但不能据此推断大规模性能；评估应记录全量数据下端到端耗时、SQL 与内存占用，再考虑 SQL 端过滤分页。

## 3. 整组授权、版本与并发

阅读 `UserService.replaceRoles`、`RoleService.replacePermissions`，再看 Mapper 的锁定与 compare-and-increment SQL。

```text
读取原集合和version → 客户端编辑完整草稿
→ 服务端认证/范围/保护/合法性校验
→ 事务内按固定顺序锁定相关角色、重新核对角色分类
→ 比较并递增version → 替换关联集合 → 提交
```

旧版本返回 409 `VERSION_CONFLICT`，不能静默最后写入覆盖。用户角色替换在锁前读取旧分配集合，锁后重新检查角色分类，用户version的CAS负责拒绝旧请求；不是锁后重新读取用户分配集合。角色授权要求每个非 read 动作同时包含该功能 read，拒绝未知/重复码和非法范围；中间异常需整个集合及版本回滚。用户角色分配与角色删除共享相关行锁，防止删除确认和并发授角穿透。

需要区分三层证据：隔离单元测试验证“不应继续调用 Mapper”的业务分支；`Stage1ApiTest` 验证 H2 下回滚及并发结果；真实 MySQL 仍需方言/隔离级别的整合验收。前端“模拟冲突”只证明交互。

## 4. 用户/角色保护与响应

`UserService` 保护自己和唯一 admin，部门管理员只能在范围内变更业务角色，不能把 ADMIN 授给他人。`RoleService` 保护内置角色，有成员的自建角色删除需要确认并更新受影响用户版本。外键负责关联清理和负责人置空，但“是否允许这次删除”仍由业务校验决定。

`vo/Views.java` 显式组织安全字段；PO 是数据库形状，不能直接当 API 响应返回。分页 `total` 必须对应裁剪后的集合。`Ids.java` 约束字符串 ID 到 SQL 标识的转换，Mapper 使用参数绑定，不把用户输入拼入 SQL。

## 5. 原型和真后端的同与不同

前端从 `src/authorization/rbac0-policy.js → policy-adapter.js → stores/auth.js → router/index.js → views/` 阅读。注释重点说明默认拒绝、权限并集、路由直接访问、首次改密和授权草稿。

`src/api/mock.js` 是内存模型，不发真实 HTTP。演示身份恢复、成员范围、重复授权码和部分异步写入与后端不完全等价。原型只保存当前页面数据；真实后端保存 MySQL。不要把原型 `userId` 恢复机制描述成真实会话认证。

## 6. 数据导入、部署和测试

| 入口 | 必须读懂的内容 |
| --- | --- |
| `database/seeds/import_excel.py` | 工作簿解析、组织映射、稳定编码、负责人消歧、角色/范围生成、坏行与报告、随机盐哈希 |
| `database/migrations/V001__create_core_tables.sql` | 唯一键、外键、CHECK、角色版本及核对视图；应用不直接依赖视图做完整鉴权 |
| `database/rollback/U001__create_core_tables.sql` | 依赖顺序与数据损失边界 |
| `backend-procedural/scripts/generate-h2-schema.py` | MySQL 到 H2 的测试方言转换；修改 V001 后需重新生成并 `--check` |
| `scripts/check_openapi.py` | 规范校验和方法/路径集合比较；不是完整接口行为测试 |
| `scripts/smoke_http.py` | 独立 Cookie 客户端、合成临时数据清理、真实允许/拒绝与持久化标记 |
| `deploy/compose.yaml`、`nginx.conf` | Web 主机端口、服务名解析、数据库命名卷、健康依赖、反向代理 |
| `src/test/`、`tests/` | 单元、整合、原型、导入四类证据的边界 |

导入生成的 SQL 包含 TRUNCATE，MySQL DDL 不受普通事务完整回滚保护；重复执行是重建，不是无损升级。员工坏行可能被跳过但整体生成仍成功，必须看报告里的 failed/unresolved/warnings。`--update-codes` 可能先写编号表，后续失败也要检查差异。

## 按任务定位

| 想查的问题 | 首选入口 |
| --- | --- |
| 为什么返回401/403/409 | Auth/Authorization/User/Role Service、错误处理、OpenAPI |
| 为什么菜单有/没有功能 | PortalService、清单权限、管理码映射、前端适配器 |
| 为什么只能看到部分人 | AuthorizationService、User/Ledger/Department Service |
| 为什么授权保存会丢失/冲突 | version、事务、Role/User Mapper、草稿刷新逻辑 |
| 为什么部署后改配置不生效 | 已有数据库卷、空库初始化条件、容器环境和实际镜像版本 |
| 为什么覆盖率高仍有问题 | 分支断言、未测数据库方言、现场环境、原型和后端差异 |

上述 `...` 对应 `src/main/java/com/municipal/rbac/procedural/`。在 [后端目录](../../backend-procedural/src/main/java/com/municipal/rbac/procedural/)按包查看，测试在 [测试目录](../../backend-procedural/src/test/java/com/municipal/rbac/procedural/)。
