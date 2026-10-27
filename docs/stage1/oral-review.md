# 一阶段现场检查与口头答辩准备

本文供小组自查使用。正式交付设计见[结构化设计](../design/structured-design.md)，功能检查见[41条需求覆盖](requirement-coverage.md)，执行结果见[验证记录](verification-record.md)。回答时遵循“需求编号 → 设计决策 → 实际源码 → 测试与证据 → 限制”的顺序，不用页面演示或口头承诺替代后端证据。

## 1. 两次检查分开准备

| 场合 | 已明确要求 | 到场前准备 |
| --- | --- | --- |
| 助教检查 | 在正常上课教室进行，先于老师预约；原型能打开运行，后端已部署服务器，API 放入 API 仓库并现场调用；本人后端分层单元测试现场运行，展示覆盖率报告 | 固定代码版本、可访问入口、Apifox文档及对应版本、可运行测试命令、当前报告、合成验收账号和恢复方案 |
| 老师口头检查 | 上午在211，下午地点待确认；讨论结构化设计、需求、代码、测试和API，不重复展示UI；每位成员都要发言，不发言者0分；迟到超过30分钟该次预约无效 | 组内分配主讲和补充，准备模型与代码定位，提前到场；预约时间及下午地点另行核实 |
| 课程关系 | 与 JavaEE 合并检查；非 JavaEE 同学需告知老师 | 明确本人课程情况，不代替老师决定成绩或检查口径 |

复习范围为视频1～6、实验1～3。仓库无法推断尚未提供的完整视频内容，应由各成员对照自己的课程材料补充；本页只把已知检查要求映射到真实 RBAC 实现。

## 2. 先确认什么已经有证据

用户提供的 [Apifox API 文档](https://7u8ub108r6.apifox.cn/)作为文档主入口；[Swagger UI](http://124.70.42.35:8080/swagger-ui/index.html?url=/api-design/openapi.yaml)用于运行实例的在线调试。2026-10-27 的只读核对收到首页、健康端点、静态 OpenAPI 和运行时 API 文档的 200 响应，健康体为 `UP`。这只证明当时入口可达且就绪查询成功，不证明某个登录、写操作、权限拒绝、重启持久化或完整部署流程通过。

| 材料/现象 | 能证明 | 还不能证明 |
| --- | --- | --- |
| Vue 原型正常打开、合成账号可交互 | 原型页面和内存交互可用 | 已连接 Java、使用真实 Cookie、数据持久化 |
| Apifox与静态OpenAPI可访问 | 接口文档和仓库契约均可查阅 | 运行接口全部符合设计；现场仍需核对版本与实际调用 |
| `/api/v1/health` 返回 UP | 应用接受流量且一次 `SELECT 1` 成功 | 所有业务正确、事务和权限隔离、CPU/内存/吞吐达标 |
| MockMvc + H2 测试通过 | 该版本在测试上下文中的跨层行为 | 真实网络、Nginx、MySQL差异和云端持久化 |
| 隔离层测试通过 | 选定边界和替身条件下的模块逻辑 | 未覆盖路径、实际依赖配置、跨层集成 |
| JaCoCo 报告生成 | 实际执行路径的覆盖统计 | 需求覆盖完整、断言有效，或存在某个未规定的合格百分比 |

仓库内的 `api/openapi.yaml` 是版本化契约，Apifox 是公开文档入口，Swagger 用于实际接口调试。提交前应核对三处的版本、路径和请求定义一致；不一致时先记录并修正差异。

## 3. 助教现场执行顺序

1. 展示本地和服务器版本信息，说明它们是否一致。记录提交 SHA、分支、Java/数据库版本和数据来源；代码刚更新而服务器未更新时，应明确说明。
2. 打开原型，完成登录、首次改密、用户/组织、角色授权、台账等约定交互。说明此原型使用 mock，刷新后内存重建；只展示必要路径。
3. 打开静态 OpenAPI 和 Apifox与仓库契约对应版本，选一个查询、一个受控变更和一个拒绝场景，解释请求字段、Cookie、预期状态码及响应。
4. 在授权的合成验收环境现场调用真实后端。先健康，再登录与 Cookie，再改密或业务查询，再验证无权/越界；写入使用可清理合成对象。未获得操作服务器授权时只准备命令和数据，不执行写入、压测或重启。
5. 各后端负责人现场运行本人层次的隔离测试，说明被测对象、替身、输入、断言和失败路径。随后说明跨层 `Stage1ApiTest` 与独立单元测试的不同作用。
6. 打开本轮 JaCoCo HTML 报告，进入本人负责类查看行/分支和未覆盖位置；说明哪些通过证据来自单测、集成或真HTTP，不报未测百分比。

通用本地命令在仓库根目录执行：

```bash
git rev-parse HEAD
git status --short
mvn -f backend-procedural/pom.xml clean verify
```

报告路径为 `backend-procedural/target/surefire-reports/` 和 `backend-procedural/target/site/jacoco/index.html`。`clean` 清理旧报告后重新构建，避免现场打开上个版本报告。失败时先保留失败信息和版本，不删除失败用例制造“全绿”。覆盖率没有在项目中设置统一合格阈值，应按检查要求解释实际数据。

分层隔离测试已有以下真实类，可从仓库根目录整体运行：

```bash
mvn -f backend-procedural/pom.xml "-Dtest=*ServiceTest,*ControllerTest,*MapperTest,IdsTest,DataInitializerTest" test
```

| 层次 | 实际测试类 | 可展示断言与边界 |
| --- | --- | --- |
| Service | [AuthorizationServiceTest](../../backend-procedural/src/test/java/com/municipal/rbac/procedural/service/AuthorizationServiceTest.java)、[AuthServiceTest](../../backend-procedural/src/test/java/com/municipal/rbac/procedural/service/AuthServiceTest.java) | 会话/凭据失效、范围与映射、登录/改密规则；Mapper/编码器用替身隔离 |
| Service | [UserServiceTest](../../backend-procedural/src/test/java/com/municipal/rbac/procedural/service/UserServiceTest.java)、[RoleServiceTest](../../backend-procedural/src/test/java/com/municipal/rbac/procedural/service/RoleServiceTest.java) | 保护/越界、重复集合、CAS失败不执行关系替换、read依赖等；不等于真实数据库回滚证明 |
| Controller | [AuthControllerTest](../../backend-procedural/src/test/java/com/municipal/rbac/procedural/controller/AuthControllerTest.java)、[HealthControllerTest](../../backend-procedural/src/test/java/com/municipal/rbac/procedural/controller/HealthControllerTest.java) | 成功登录轮换会话、认证失败不轮换、就绪/数据库探测分支；直接调用Java方法，不覆盖MVC路由和JSON绑定 |
| Mapper及ID | [DepartmentMapperTest](../../backend-procedural/src/test/java/com/municipal/rbac/procedural/mapper/DepartmentMapperTest.java)、[IdsTest](../../backend-procedural/src/test/java/com/municipal/rbac/procedural/mapper/IdsTest.java) | 部门子树算法、ID数值边界；`findAll`被替换，不验证SQL/FK/真实数据库 |
| 初始化 | [DataInitializerTest](../../backend-procedural/src/test/java/com/municipal/rbac/procedural/data/DataInitializerTest.java) | 合成数据必须显式启用、目标库状态和密码前置条件 |

`mvn -f backend-procedural/pom.xml -Dtest=Stage1ApiTest test` 另行运行 SpringBootTest/MockMvc/H2 集成测试，包含真实JDBC写路径、回滚和并发断言，不能改称“Controller隔离单元测试”。单层替身与跨层实际依赖各有作用，现场应解释自己正在运行哪一类，执行状态以最新[验证记录](verification-record.md)为准。

## 4. 老师口头检查的讲述路线

建议先用[上下文/一层DFD与功能结构图](../design/structured-design.md)说明系统做什么、输入输出如何流动，再选授权保存或台账越界展开源码和测试。不要把 DFD、调用顺序图、ER图混为一张图：DFD强调数据从哪里来、经过何种变换、落在哪里；结构图强调职责分解；时序图强调一次请求的调用与返回；ER图强调数据关系和约束。

| 成员 | 主要讲述及代码/资料入口 | 必须能回答的追问 |
| --- | --- | --- |
| 1 郑凯 | [数据字典](../database/data-dictionary.md)、[迁移](../../database/migrations/V001__create_core_tables.sql)、[导入脚本](../../database/seeds/import_excel.py)、[导入测试](../../tests/unit/test_import_excel.py) | 同名人员/部门如何定位；固定编号为何不能重排；重复导入如何核对；3549与3555的区别；未完成全量验证在哪里记录 |
| 2 陈清香 | [路由](../../frontend/src/router/index.js)、[会话](../../frontend/src/stores/auth.js)、[用户页](../../frontend/src/views/UsersView.vue)、[组织页](../../frontend/src/views/DepartmentsView.vue) | 首次改密如何阻断；按钮禁用为何不是安全边界；过滤为何回第一页；mock与真后端如何区分 |
| 3 杨昕桐 | [共享策略](../../frontend/src/authorization/rbac0-policy.js)、[授权页](../../frontend/src/views/RoleGrantsView.vue)、[目录映射](../../frontend/src/api/fixtures/catalog.js) | 同码范围合并、read依赖、全量草稿/分页、409与500、过期响应；继承/约束为何仍为后续静态页 |
| 4 王信凯 | [AuthService](../../backend-procedural/src/main/java/com/municipal/rbac/procedural/service/AuthService.java)、[AuthorizationService](../../backend-procedural/src/main/java/com/municipal/rbac/procedural/service/AuthorizationService.java)、[UserService](../../backend-procedural/src/main/java/com/municipal/rbac/procedural/service/UserService.java) | Cookie与凭据版本；SELF/DEPT/ALL；源目标和迁入部门检查；自保护；用户角色版本及并发边界 |
| 5 胡俊朗 | [RoleService](../../backend-procedural/src/main/java/com/municipal/rbac/procedural/service/RoleService.java)、[Mapper目录](../../backend-procedural/src/main/java/com/municipal/rbac/procedural/mapper)、[详细设计](../design/detailed-design.adoc) | 行锁/CAS/事务各解决什么；失败回滚；管理类角色识别；为什么JDBC不等于JPA；二期OO设计尚未实现什么 |
| 6 孙逸轩 | [OpenAPI](../../api/openapi.yaml)、[覆盖表](requirement-coverage.md)、[后端集成测试](../../backend-procedural/src/test/java/com/municipal/rbac/procedural/Stage1ApiTest.java)、[HTTP检查](../../scripts/smoke_http.py) | 41条需求如何追溯；契约与运行API如何比较；单测/集成/现场调用证据不同；覆盖率与性能口径 |

上述安排是按既有责任表做的准备路线，不替代实际贡献。每人应能打开并解释自己的代码和测试，再由相关成员补充跨模块问题；不能让一个人替所有人讲完。

## 5. 需求与实现：高频问题逐项准备

### 5.1 为什么一期不实现所有清单业务？

本系统核心是统一权限，目录中的大部分功能用权限按钮和范围占位表示；员工台账提供可验证的电话/邮箱业务示例。不要把占位按钮点击提示说成审批/导出等业务已实现。一期是 RBAC0，后续继承、SSD/DSD、角色激活和审计不能混入本期通过项。

### 5.2 什么叫默认拒绝？

未登录返回401；已登录但无动作权限返回403；没有角色不会变管理员；前端权限未加载不显示受保护入口。真实后端从数据库读取角色/授权，不接受客户端自报 `role=admin`。前端路由守卫保护交互，Java服务保护真实数据。定位 `AuthorizationService.require/target` 和匿名接口测试 `allProtectedFamiliesRejectAnonymous`。

### 5.3 同一个人有多个角色，范围如何合并？

以完整权限码为键，同码取 `ALL > DEPT > SELF`，不同码不放大。例如 read=ALL、edit=DEPT 仍只能改本部门及下级。`AuthorizationService.effectivePermissions/widerScope` 对应 `businessRoleUnionAndImmediateRevocation`、`managerReadAllEditDepartmentAndChildren`；前端共享策略也有同类测试，但不能代替后端。

### 5.4 DEPT 和 SELF 的小边界有哪些？

DEPT是部门本身加全部下级，不只直接子节点；SELF以稳定用户ID比对，不按姓名。部门节点可见不代表同事均可见；SELF成员接口还须逐员工过滤，SELF不能新建他人账号。列表先过滤范围再计数/分页，修改人员还要检查迁入部门。定位 `AuthorizationService.visible/covers/coversDepartment`、`DepartmentService.members` 和 `selfManagementScopesNeverExposeDepartmentPeersOrCreateOtherAccounts`。

### 5.5 为什么404和403不能随意互换？

用户/台账目标不存在与不在范围内统一 `403 OUT_OF_SCOPE`，避免通过编号枚举记录是否存在；一般角色资源不存在采用404。应按具体契约解释，不能说整个项目所有不存在资源都返回同一个状态。

### 5.6 登录后为什么还要查数据库？

登录只建立身份。每次请求核对账号启用状态、凭据标记以及当前启用角色授权，撤权或停用在下一请求生效，不要求所有人重新登录。代价是更多查询，目前没有声称实现跨实例分布式授权缓存；若引入缓存，须设计版本/失效机制并新增测试。

### 5.7 为什么要轮换JSESSIONID，改密后旧会话怎么失效？

`AuthController` 成功登录后 `changeSessionId()`；`AuthorizationService.current` 比较会话中凭据标记和数据库摘要。密码变更后其他会话旧标记不匹配而失效，当前改密会话更新标记。摘要只存服务端，不返回JSON。Cookie属性见应用配置；当前HTTP入口不能被描述为已配置HTTPS或Secure Cookie。

### 5.8 初始密码和真实员工资料在哪里？

初始密码来自受控环境参数，后端慢哈希保存。原型 `123456` 只是合成演示口令。原始Excel、真实姓名/电话、哈希和环境文件不提交；公开种子只用于合成或非人员目录。新建/重置后必须改密，拒绝通过新建接口顺便指定管理员角色。

### 5.9 为什么使用PBKDF2，是否已经自动升级旧密码？

[密码编码器](../../backend-procedural/src/main/java/com/municipal/rbac/procedural/config/SecurityConfig.java)对新密码使用带算法前缀的PBKDF2，兼容编码器验证历史带前缀摘要；有测试区分超过旧BCrypt字节限制的不同长尾。`upgradeEncoding` 声明升级需求，但当前登录流程未据此自动重写历史摘要，不能声称已完成自动迁移。

### 5.10 角色授权为什么要整组替换？

读取一个完整授权集合和版本，再提交目标集合，能明确新增/撤销的最终结果。前端 `draft Map` 跨页保存完整选择，只提交可见页会误删其他页。后端先检查合法权限、重复码、read依赖及保护规则，再CAS递增版本并替换关系。版本旧返回409，刷新复核后再决定，不能静默覆盖。

### 5.11 行锁、乐观版本、事务有什么不同？

行锁协调相关并发变更；旧版本检测保护“管理员读完后又有人修改”的业务意图；事务确保版本与关联集合原子提交。用户授角和角色删除按共同规则锁定角色，避免删除确认与新分配穿透。不能只说“加了事务所以没有并发问题”。定位 `UserService.replaceRoles`、`RoleService.replacePermissions/delete`、`simultaneousCasHasExactlyOneWinner`、`concurrentAssignmentMakesUnconfirmedRoleDeleteRefuse`。

### 5.12 如何证明写到一半失败没有留下半套授权？

`failedReplacementRollsBackGrantsAndVersion` 对真实服务/JDBC写路径注入失败，断言授权集合及版本均不变。原型的模拟失败在赋值前抛错，只能证明前端处理路径，不能证明数据库事务回滚。生产API没有暴露任意失败注入开关。

### 5.13 管理员保护只判断角色名字可以吗？

不可以。内置ADMIN/DEPT_ADMIN以及含管理清单授权的自建角色都属于管理类，防止复制管理权限换个名字再由部门管理员分配。保护本人和唯一admin；内置角色不可删除，ADMIN不可停用或改授权。定位 `adminRole/superAdmin`、`managementRoleClassificationPreventsCustomPrivilegeEscalation` 与 `selfAndUniqueAdminProtection`。

### 5.14 为什么目录码与rbac码要分开？

目录是权威可授权白名单，稳定 `系统码:fNNN:动作`；管理码由固定清单码派生，保持scope并可追溯来源。不能把派生 `rbac:*` 直接写入原始权限表，也不能按显示名称/数组下标重编号。代码定位 `AuthorizationService.managementCodes`、固定CSV和API约定。

### 5.15 输入有校验为什么还要数据库约束？

DTO负责格式，Service负责权限、存在性和业务关系，数据库唯一/FK/CHECK负责持久化兜底；预检查不能阻止两个并发请求同时通过。Mapper使用参数绑定，响应通过显式字段避免泄露摘要。测试还要覆盖未知字段、非法ID、重复集合以及唯一性冲突后的数据不变。

### 5.16 所有列表是不是数据库分页？

不是。部分Mapper先取集合，Service做范围/业务过滤后在内存分页，当前保证语义正确不等于性能最优。大规模应按真实查询、执行计划、数据量和耗时定位瓶颈；下推SQL时必须保持范围、总数、排序和分页一致性，不能为了快先分页再过滤。

### 5.17 为什么要同时准备单元、集成、HTTP和持久化检查？

隔离单测快速定位单层规则；跨层集成验证框架、DTO、事务和H2协作；HTTP验证运行进程、Cookie、代理和部署配置；MySQL/重启验证实际数据库语义和卷。H2不证明MySQL锁行为，MockMvc不经过公网代理，覆盖率高不代表权限拒绝断言完整。

### 5.18 代码注释为什么写“不能证明”？

原型、H2、小样例、目录合成和全量原始数据是不同证据。应能指出当前实现在哪里、用什么测试过、还有哪些环境没测。清晰说明边界比把所有状态都写成“完成”更能让检查者复现。

## 6. 课程实验三与本项目逐项对照

用户提供的实验三是独立产品示例，涉及 JPA/AOP、四机Swarm、JMeter/MySQL/应用及Manager/Prometheus，并涉及 actuator、micrometer、node_exporter。现有RBAC不能靠改文档就变成该示例的实现。

| 实验内容 | RBAC当前事实 | 现场可以怎样解释 |
| --- | --- | --- |
| JPA实体和Repository | 使用JdbcTemplate/Mapper、参数化SQL和显式表迁移，没有JPA实体映射 | 解释都在持久化层解决什么问题，再打开本项目实际SQL；不把Mapper称为JpaRepository |
| AOP示例 | 没有实验要求的自定义业务切面 | `@Transactional`由Spring事务代理管理；这不代表已经完成实验中的自定义切面、切点和通知示例 |
| 四机Swarm与角色分配 | 仓库提供单套Compose的web/backend/db服务，没有Swarm stack或多机部署脚本 | 说明实际Compose配置及未来迁移需要的服务发现、会话、卷、密钥和调度设计；不声称已有四机集群 |
| actuator/micrometer | 无对应指标采集配置与依赖；有自定义 `/api/v1/health` | 区分业务就绪检查与指标时间序列；不能说health返回UP就具备完整监控 |
| Prometheus/node_exporter | 当前部署文件未定义对应服务或抓取任务 | 说明将来需应用指标、宿主机指标、抓取目标和标签等配套，现阶段属于设计建议 |
| JMeter压力测试 | 无本轮服务器压测执行证据 | 解释压测设计与指标含义，不能把旧本机合成查询耗时外推为服务器QPS |

如课程要求必须现场演示实验三自身的四机产品，应准备该独立实验的真实代码、拓扑和报告，明确它与RBAC是两套交付，不能互相冒充。

## 7. 压测口径：参数、并发和吞吐分清楚

| 名称 | 是什么 | 不能直接推导什么 |
| --- | --- | --- |
| Threads | 配置的虚拟用户线程数量 | 不等于全程实际活跃并发，也不等于每秒请求数 |
| Ramp-up | 线程启动分布时间 | `线程数/启动秒数`只是计划平均启动速率，不是吞吐/QPS |
| 活跃线程 | 随时间运行的虚拟用户 | 线程可能等待响应、定时器或思考时间，不全在同时发HTTP |
| 吞吐 | 指定统计窗口实际完成的请求/样本数除以时间 | 不能用线程数直接代替；须注明样本定义、事务控制器与错误是否计入 |
| 响应时间 | 一次请求从发起到完成的观测时间 | 端到端耗时不等于数据库纯鉴权耗时 |
| 平均值/P95/P99 | 分布的不同统计量 | 平均快不代表尾延迟或错误率达标 |

若未来压测，应先确定允许环境和合成数据、读写比例、登录会话保持、慢哈希成本、升压/稳态/降压时段及停止条件；同时记录真实活跃线程曲线、完成请求率、错误类型、P95/P99、CPU/内存/GC、数据库连接/慢SQL和网络。JMeter本身可能成为瓶颈，需区分客户端能力与被测服务能力。

项目目标中的“鉴权≤10ms”应在明确SQL/数据量/缓存冷热及执行计划条件下测量；“列表/保存≤1s”应定义接口、负载、分位数和成功判定。没有本轮测量和报告时保留待执行，不新增QPS、实际并发、覆盖率或集群配置的达标声明。

## 8. 检查前最后核对

- 每人能在1～2分钟内说清本人模块的输入、输出、依赖、失败场景和至少一个真实断言；再准备深入追问，而不是背函数名。
- 逐项核对[41条覆盖表](requirement-coverage.md)，优先练未登录、无角色、首次改密、跨范围、旧版本409、回滚、停用/重置旧会话失效这些拒绝路径。
- 本地与服务器版本、Apifox与仓库契约版本、报告版本有记录；若不同，当场说明差异。
- 无法现场运行的测试或尚未执行的全量导入/性能/持久化保留实际状态；不要把历史截图搬到新版本下当作本次结果。
- 助教完成原型和运行检查后，老师环节转向结构化模型、需求到代码/测试的追溯，每人发言；上午211、下午地点、预约时间和本人JavaEE情况提前确认。
