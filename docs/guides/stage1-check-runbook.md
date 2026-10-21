# 第一期检查操作手册与口试预回答

适用：成员 4（王信凯）及全组。检查分「助教检查 + 老师口试」两段，顺序不能反。服务器地址、演示密码和本机私密信息见 `deploy/STAGE1-TEST-NOTES.local.md`（已 Git 忽略，不提交）。

本文中的命令以 `<服务器IP>`、`<端口>`、`<演示密码>` 占位；现场替换为真实值。

## 0. 检查安排（按老师课末说明整理）

| 项目 | 安排 |
| --- | --- |
| 预约 | 当晚钉钉群接龙，周二、周三、周四；32 组，每天 11 组（上午 5、下午 6），共 33 个时段 |
| 第一段 | 助教检查，在平时上课教室，每天一位助教；必须在老师口试时段之前完成，否则预约作废 |
| 第二段 | 老师口试，另行约的教室（211，下午教室待定） |
| 迟到 | 晚几分钟到十分钟可以；超过半小时该时段作废，只能抢唯一空位 |
| 口试形式 | 无领导小组式，组内任何人可答、可补充；小组整体计分，个人发言计个人分；全程不说话个人 0 分 |
| 口试范围 | 结构化设计 + 需求；不看原型/API 界面；重点看后端设计代码、测试、API 文档与代码 |
| JavaEE | 与 OOAD 口试合并；范围是课程视频第 1—6 部分 + 实验一/二/三（部署、容器等；性能测试未涉及）；按你写的代码提问 |

## 1. 检查前 24 小时准备清单

| 检查项 | 完成标准 | 负责人 |
| --- | --- | --- |
| 服务器运行 | `db`、`backend`、`web` 三个容器 healthy | 成员 4 |
| 外网入口 | 五个入口全部 200；`/api/v1/health` 返回 `{"status":"UP"}` | 成员 4 |
| 源码版本 | 服务器与本地 SHA 一致，记录完整 SHA | 成员 4 |
| 原型 | `/` 和 `/stage1/` 能打开、能切换身份、能点主要页面 | 成员 2、3 |
| API | `api/openapi.yaml` 在仓库；现场能调通登录和受保护接口 | 成员 4、6 |
| 测试 | `mvn clean verify` 现场能跑，29 项全过 | 成员 4、5 |
| 覆盖率 | JaCoCo 报告可打开，行覆盖 88.7% | 成员 4 |
| 材料 | 需求、设计、UML、数据字典、API 契约在仓库可检索 | 全组 |
| 备份 | 手机热点、关键截图/录屏、旧版本 SHA 和回滚办法 | 全组 |

## 2. 助教检查：逐项现场操作

### 2.1 原型能打开、能真跑

打开：

- `http://<服务器IP>:<端口>/`：完整 Vue 原型
- `http://<服务器IP>:<端口>/stage1/`：一期统一检查入口

现场操作建议：

1. 登录页能打开，切换不同演示身份；
2. 门户菜单随身份变化；
3. 用户、部门、角色、资源目录页面能打开；
4. 无权限页面显示 403，按钮置灰；
5. 刷新、窄屏、导航正常。

**口径**：一期允许原型使用内存模拟数据，原型演示与真实后端分开验收。不要说“原型已经接了真实后端”；真实后端用 Swagger 和 HTTP 脚本证明。

### 2.2 后端已部署到服务器

在服务器上：

```sh
cd /srv/RBAC-Permission-System
git rev-parse HEAD
docker compose --env-file deploy/.env -f deploy/compose.yaml ps
curl -s http://127.0.0.1:<端口>/api/v1/health
```

预期：

- 输出完整 SHA；
- `db`、`backend`、`web` 三行都是 `healthy`；
- `{"status":"UP"}`。

在自己电脑上再执行一次外网检查：

```sh
curl -s http://<服务器IP>:<端口>/api/v1/health
```

预期同样是 `{"status":"UP"}`。服务器上能跑不等于外网能访问，两步都要做。

### 2.3 API 在 API 仓库并能现场调通

**证明 API 在仓库**：

```sh
git log --oneline -3 -- api/openapi.yaml
python scripts/check_openapi.py --base-url http://<服务器IP>:<端口>
```

预期：`OpenAPI valid: 25 paths, 33 operations`，且 `Live backend methods and paths exactly match the API design`。

**现场调接口（curl）**：

```sh
BASE=http://<服务器IP>:<端口>

# 1. 健康检查
curl -s $BASE/api/v1/health

# 2. 未登录应 401
curl -s -o /dev/null -w "%{http_code}\n" $BASE/api/v1/users

# 3. 管理员登录，保存 Cookie
curl -s -c /tmp/rbac-admin.txt \
  -H "Content-Type: application/json" \
  -d '{"account":"admin","password":"<演示密码>"}' \
  $BASE/api/v1/auth/login

# 4. 当前身份与权限
curl -s -b /tmp/rbac-admin.txt $BASE/api/v1/me
curl -s -b /tmp/rbac-admin.txt $BASE/api/v1/me/permissions

# 5. 用户分页
curl -s -b /tmp/rbac-admin.txt "$BASE/api/v1/users?page=1&pageSize=5"

# 6. 普通员工调用管理接口应 403
curl -s -c /tmp/rbac-emp.txt \
  -H "Content-Type: application/json" \
  -d '{"account":"SG000013","password":"<演示密码>"}' \
  $BASE/api/v1/auth/login
curl -s -b /tmp/rbac-emp.txt -o /dev/null -w "%{http_code}\n" $BASE/api/v1/users
```

预期：健康 `UP`；未登录 `401`；登录 `200`；`me` 返回身份；用户列表返回 `{items,page,pageSize,total}`；普通员工管理接口 `403`。

也可以直接用 Swagger：

`http://<服务器IP>:<端口>/swagger-ui/index.html?url=/api-design/openapi.yaml`

先执行 `POST /api/v1/auth/login`，再调用受保护接口；同源请求会自动带 `JSESSIONID`。

### 2.4 现场跑后端测试

服务器上没有装 Maven 也可以用 Docker 跑（镜像已预拉，依赖已缓存）：

```sh
cd /srv/RBAC-Permission-System
docker run --rm \
  -v /srv/RBAC-Permission-System:/workspace \
  -v rbac-maven-repo:/root/.m2 \
  -w /workspace \
  maven:3.9.11-eclipse-temurin-21 \
  mvn -B -ntp -f backend-procedural/pom.xml clean verify
```

预期结尾：

```text
Tests run: 29, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

测试类：[backend-procedural/src/test/java/com/municipal/rbac/procedural/Stage1ApiTest.java](../../backend-procedural/src/test/java/com/municipal/rbac/procedural/Stage1ApiTest.java)。本轮实测 29 项、0 失败、0 错误，耗时约 27 秒（依赖已缓存时）。

如果助教要求看测试报告：

- Surefire：`backend-procedural/target/surefire-reports/`
- JaCoCo：`backend-procedural/target/site/jacoco/index.html`

### 2.5 覆盖率

打开 `backend-procedural/target/site/jacoco/index.html`，本轮实测：

| 指标 | 覆盖 |
| --- | --- |
| 指令 INSTRUCTION | 87.1%（6156/7067） |
| 分支 BRANCH | 70.3%（336/478） |
| 行 LINE | 88.7%（974/1098） |
| 复杂度 COMPLEXITY | 74.3%（387/521） |
| 方法 METHOD | 89.1%（244/274） |
| 类 CLASS | 100%（49/49） |

把报告复制到自己的电脑查看：

```sh
scp -r root@<服务器IP>:/srv/RBAC-Permission-System/backend-procedural/target/site/jacoco ./jacoco-report
```

然后打开 `jacoco-report/index.html`。

**口径**：覆盖率是证据之一，不是唯一目标。未覆盖的分支主要是防御性错误路径和配置分支；关键路径（登录、401/403、范围、整组替换、409、回滚、并发）都有对应用例。

### 2.6 现场证据清单

| 证据 | 位置/方式 |
| --- | --- |
| 源码 SHA | `git rev-parse HEAD` |
| 三容器 healthy | `docker compose ... ps` 截图 |
| 健康检查 | `/api/v1/health` 截图 |
| API 契约 | `api/openapi.yaml` + `check_openapi.py` 输出 |
| 现场接口调用 | curl 输出或 Swagger 截图 |
| 测试结果 | `Tests run: 29` + `BUILD SUCCESS` 截图 |
| 覆盖率 | JaCoCo 首页截图 |
| 原型 | `/` 与 `/stage1/` 页面截图 |

不要记录密码、Cookie、真实员工信息。

## 3. 老师口试：3 分钟陈述模板

> 我们做的是市政集团 RBAC 权限系统。第一阶段目标是 RBAC0：用户通过角色获得权限，权限取并集、默认拒绝，数据范围分全部、本部门、本人。我们交付了完整原型、MySQL 表结构与迁移、面向功能后端和统一的 OpenAPI 契约。
>
> 后端按“认证 → 加载角色 → 计算有效权限 → 检查范围 → 执行事务”的过程组织。身份只从服务端会话取，每次请求重新读数据库，不信任前端自报的用户、角色或权限。有效权限只来自已分配且启用的角色；同一权限码多角色取最大范围，没有明确授予就拒绝。
>
> 数据库用 10 张主表和 1 个视图，外键、唯一键、CHECK 保证数据合法。用户角色和角色授权都采用“完整集合 + 版本号”的整组替换，版本不一致返回 409，事务内要么全部成功要么全部回滚；并发分配和删除角色用行锁与 READ_COMMITTED 处理。
>
> 测试有 29 个 Spring Boot 架构级用例，覆盖登录、会话失效、SELF/DEPT 范围、越权拒绝、409、回滚、并发 CAS、密码兼容；行覆盖 88.7%，类覆盖 100%。部署用 Docker Compose 跑 MySQL、Java 后端和 Nginx，只对外发布一个 HTTP 端口，数据库和后端不暴露公网。

## 4. 预回答 Q&A

回答原则：先给结论，再指代码或文件；不会就明说“这块是后续阶段/待执行”，不要编。

### 4.1 需求与范围

**Q1：这个系统解决什么问题？**
统一管理集团多套系统的用户、角色和权限，避免每个系统各自维护账号；支持数据范围（全部/本部门/本人），保证部门管理员只能管自己人。

**Q2：第一阶段做到哪一步？**
RBAC0：用户—角色—权限、并集、默认拒绝、三种数据范围；不含角色继承（RBAC1）、互斥/基数约束和会话激活角色（RBAC2）。

**Q3：为什么第一阶段不做继承和约束？**
课程按 RBAC0→RBAC1→RBAC2 分阶段验收；继承闭包和约束检查会改变授权算法，必须在 RBAC0 稳定后单独设计和测试。

**Q4：参与者有哪些？**
超级管理员、部门管理员、公司领导、部门经理/项目经理、普通员工。部门管理员不是清单里的角色，是平台为分级管理增加的。

**Q5：核心用例有哪些？**
登录、查询用户、分配/撤销角色、配置角色权限、执行受保护操作（员工台账编辑）；继承、约束、激活角色属于后续阶段。

**Q6：数据规模多大？**
111 个组织、18 个系统、115 个模块、359 个功能点、2154 条权限、10000 名员工加 admin、3555 条角色授权（3549 条清单 + 6 条部门管理员）。

**Q7：合成数据和原始数据是什么关系？**
仓库只提交合成样例；原始 Excel 放本地 `data/raw/`，由成员 1 离线导入。合成数据证明规则和接口，不等于全量导入已验收。

**Q8：数据范围怎么定义？**
`ALL` 所有目标；`DEPT` 本人部门及全部下级；`SELF` 目标记录属于本人。多角色同一权限码取 `ALL > DEPT > SELF` 的最大范围。

**Q9：为什么查看范围不能放大编辑范围？**
不同权限码分别计算。查看是 `ALL` 不代表编辑也是 `ALL`；台账编辑只按编辑码的范围检查。

**Q10：需求文档在哪里？**
[docs/requirements/req-spec.md](../requirements/req-spec.md)、[API 约定](../standards/api-contract.md)、[数据字典](../database/data-dictionary.md)。

### 4.2 结构与设计

**Q11：为什么叫“面向功能/过程”后端？**
按一次请求的过程组织：认证→加载角色→计算有效权限→检查范围→执行事务，而不是按 User/Role 等领域对象组织。核心授权集中为可复用步骤。

**Q12：分层结构是什么？**
Controller 接收 HTTP、校验 DTO；AuthorizationService 统一认证/授权/范围；功能 Service 编排业务；Mapper 只写参数化 SQL；Views 显式构建不含密码的响应。

**Q13：为什么授权集中在一个 Service？**
避免权限判断散落在各 Controller；新增接口只调用 `require/target/visible`，鉴权口径一致，也方便测试。

**Q14：`AuthorizationService` 的关键方法？**
`current` 校验会话和凭证版本；`effectivePermissions` 计算并集；`require` 检查权限码；`target` 检查目标对象和范围；`covers/visible` 判断 DEPT/SELF；`widerScope` 合并范围。

**Q15：DEPT 子树怎么算？**
`DepartmentMapper.subtree` 一次读出全部部门，在 Java 里从当前部门迭代扩展子部门。部门只有 111 个，内存计算简单、无数据库方言依赖；表上有 `parent_id` 索引，后续数据量变大可换递归 CTE。

**Q16：`rbac:*` 权限从哪来？**
由清单里的系统管理后台权限派生：`sys:f339:read`→`rbac:user:read`，`sys:f339:edit`→`rbac:user:update` 和 `rbac:user:assign-role` 等。派生码不能直接写入角色授权，映射表是契约的一部分。

**Q17：为什么用 `JdbcTemplate` 而不是 JPA/MyBatis？**
SQL 可控、参数绑定明确、没有隐式查询；这个项目的核心是授权和并发，手写 SQL 更容易解释和优化。MyBatis 也可以，属于技术选型。

**Q18：DTO、PO、VO 怎么区分？**
PO 对应数据库行；DTO 是请求体并带校验注解；VO/Views 是响应形状，显式排除 `password_hash` 等敏感字段。

**Q19：错误怎么统一？**
`GlobalExceptionHandler` 把业务异常、参数校验、JSON 解析错误统一成 `{error:{code,message,requestId,details}}`，状态码按 400/401/403/404/409 区分。

**Q20：401、403、404 怎么区分？**
未登录/会话失效 401；已登录但无权限或目标不在范围 403 `OUT_OF_SCOPE`；范围内的目标不存在 404。范围外统一 403 且不返回目标字段，避免枚举。

**Q21：前后端契约在哪？**
权威是 [api/openapi.yaml](../../api/openapi.yaml)（25 路径、33 操作）；[docs/standards/api-contract.md](../standards/api-contract.md) 说明字段、范围和错误码；代码、契约、测试三方一致。

**Q22：为什么原型没有接真实后端？**
一期明确允许原型用内存模拟数据，原型演示和后台验收分开；真实后端通过 Swagger、curl 和 HTTP 自测脚本验证。接真实后端是后续集成工作。

**Q23：两套后端怎么分工？**
`backend-procedural/` 按过程组织，`backend-oo/` 按领域对象组织；外部契约和数据库一致，核心授权各自实现，不共享权限计算。第一期先交付面向功能后端。

**Q24：结构化设计体现在哪里？**
分层、模块职责、过程流水线、ER/数据字典、OpenAPI、关键序列和状态；文档在 [detailed-design.md](../design/detailed-design.md)、[UML](../uml/README.md)、[数据字典](../database/data-dictionary.md)。

**Q25：如果要加一个新功能，要改哪里？**
资源目录增加功能点和权限码（数据，不改表）；后端加 Controller/Service/Mapper；授权复用 `AuthorizationService`；前端加页面和路由；API 契约同步。权限码不随显示名称变化。

### 4.3 RBAC0 授权

**Q26：有效权限怎么算？**
只取已分配且启用的角色，遍历角色的直接权限，按权限码去重并合并范围；没有继承、没有显式拒绝。

**Q27：默认拒绝怎么体现？**
`require` 从 `scopes` 取不到权限码就抛 403；前端传来的 `permissionCodes`、`roleIds`、`userId` 一律不参与鉴权。

**Q28：会话里存了什么？**
用户 ID 和登录时的密码哈希（凭证版本）。每次请求重新查用户：用户不存在、停用、密码哈希变化都会让旧会话 401。

**Q29：权限变更什么时候生效？**
下一次请求立即生效，因为每次请求都重新读数据库计算；不需要重新登录。撤销角色、停用角色、改授权都是如此。

**Q30：SELF 怎么防止改 URL 越权？**
`target` 先按权限码取范围，再比对目标用户 ID；SELF 只允许本人。台账查询先按范围裁剪再筛选/分页，改请求体里的 ID 也不能扩大范围。

**Q31：DEPT 怎么防止查别的部门？**
`covers` 判断目标部门是否在当前部门的 `subtree` 内；列表用 `visible` 先裁剪；改筛选参数也看不到范围外数据。

**Q32：部门管理员能分配哪些角色？**
只能给范围内用户增删业务角色；ADMIN、DEPT_ADMIN 以及带管理码的自建角色都不能动；修改角色授权也拒绝。

**Q33：超级管理员有什么特殊保护？**
只预置一个 `admin`；不能停用、删除或改其角色；ADMIN 角色不能停用、删除或修改授权；ADMIN 也不能授给其他账号。

**Q34：不能停用/删除自己？**
对，防止把自己锁死；`protectedAccount` 检查当前用户和目标用户，违反返回 409 `PROTECTED_ACCOUNT`。

**Q35：多角色权限重复怎么办？**
权限码去重；同一码的范围取最大。撤销一个角色后，其他角色仍提供的权限保留。

**Q36：停用的角色算不算？**
不算。`roleIds(userId, true)` 只取启用角色；停用后该角色的权限和成员关系都不参与鉴权。

**Q37：为什么授权要“整组替换”？**
幂等、原子、易比较版本；`[A,B]→[B,C]` 一次表达撤销 A、保留 B、分配 C，不会出现“加了但没删”的半状态。

**Q38：权限码为什么用 `系统:资源:动作`？**
稳定、可读、与显示名称解耦；清单行顺序变化不影响已有授权。管理后台用 `rbac:*` 派生码，避免把界面名称写进授权数据。

**Q39：非 read 操作为什么自动要求 read？**
没有查看权限却能有编辑/审批权限会导致越权；后端用 `READ_REQUIRED` 兜底，前端勾选时自动补 read。

**Q40：前端隐藏按钮算安全吗？**
不算。前端只做展示；后端每个受保护接口都重新鉴权，直接调接口同样会被拒绝。

### 4.4 数据库、事务与并发

**Q41：数据库有几张表？**
10 张主表：`sys_dept`、`sys_user`、`sys_role`、`app_system`、`app_module`、`app_function`、`sys_action`、`sys_permission`、`sys_user_role`、`sys_role_permission`；另有 `v_user_permission` 视图。

**Q42：完整性怎么保证？**
外键（部门父子、用户部门、角色权限、用户角色）、唯一键（工号、手机号、角色编码）、CHECK（状态、范围、部门类型、性别、内置标记）。

**Q43：为什么用版本号？**
用户角色集合用 `sys_user.role_version`，角色授权用 `sys_role.permission_version`；GET 取版本，PUT 比版本，成功后 +1。旧版本返回 409，不会覆盖并发成功结果。

**Q44：版本比较和更新是原子的吗？**
是。事务内先做权限/范围/合法性校验，再用 `compareAndIncrement` 带版本条件更新；失败返回 409，整个集合不变。

**Q45：为什么还要 `FOR UPDATE`？**
版本号解决同一集合的并发写；`FOR UPDATE` 解决结构性冲突，例如“删除角色”和“给用户分配该角色”同时发生。按角色 ID 升序加锁，避免死锁。

**Q46：为什么用 READ_COMMITTED？**
角色删除/授权替换需要读到最新已提交关系，避免可重复读快照把并发结果看旧；版本冲突的 `currentVersion` 也用当前读获取。

**Q47：替换失败会部分写入吗？**
不会。`@Transactional` 包住替换；测试用 `@SpyBean` 在写完后抛异常，验证版本和授权都回滚（`failedReplacementRollsBackGrantsAndVersion`）。

**Q48：删除角色时还有用户怎么办？**
返回 409 `ROLE_HAS_USERS`，需要 `confirm=true`；确认后清理关联并更新受影响用户的版本。内置角色不可删除。

**Q49：用户删除后部门负责人怎么办？**
外键 `ON DELETE SET NULL` 把 `manager_user_id` 置空；用户角色关系 `ON DELETE CASCADE` 清理。

**Q50：10,000 用户查询会不会慢？**
列表服务端分页，先按范围裁剪再筛选/计数；`username`、`dept_id`、关联表都有索引。合成 10,000 数据实测列表中位约 64 ms、台账约 47 ms、本人权限约 31 ms；正式全量性能由成员 1 补测。

### 4.5 API 与安全

**Q51：API 前缀和格式？**
`/api/v1`，JSON；分页从 1 开始，默认 20、最大 100；用户角色和角色授权用 PUT 传完整集合 + version。

**Q52：错误包络是什么？**
`{"error":{"code","message","requestId","details"}}`；业务码稳定，前端按码处理，不靠 message 文本。

**Q53：登录失败为什么统一提示？**
用户不存在、密码错误、账号停用都返回 401 `INVALID_CREDENTIALS`，防止探测工号是否存在；停用账号也不额外暴露原因。

**Q54：密码怎么存？**
PBKDF2（`{pbkdf2@SpringSecurity_v5_8}` 前缀）加盐慢哈希；兼容导入的 `{bcrypt}` 前缀旧哈希；响应不返回密码字段；创建/重置接口不接受前端指定初始密码。

**Q55：为什么不用 BCrypt 写新密码？**
PBKDF2 处理完整 8–128 字符，避免 BCrypt 72 字节截断；旧 BCrypt 哈希仍可校验。

**Q56：SQL 注入怎么防？**
JdbcTemplate 全部参数绑定；用户输入不拼 SQL；分页参数有上下界；数字 ID 先校验，避免超大/畸形值变成 SQL 错误。

**Q57：CSRF 怎么考虑？**
Cookie 会话 + `SameSite=Lax` + 同源 Nginx；当前没有单独 CSRF token。生产环境应加 CSRF token 或改用 Token 认证；这是已知限制。

**Q58：XSS 怎么考虑？**
API 返回 JSON，前端按文本渲染；不返回可直接执行的 HTML；输入有长度/格式校验。前端框架默认转义。

**Q59：为什么只发布一个 HTTP 端口？**
Nginx 同源提供原型、API 文档和 `/api` 代理；MySQL 和 Java 端口只在 Compose 内部网络，不暴露公网，减少攻击面。

**Q60：会话 Cookie 怎么设置？**
`JSESSIONID`，HttpOnly、SameSite=Lax；HTTPS 时 `Secure`；登录成功轮换会话 ID，降低会话固定风险。

### 4.6 测试与覆盖率

**Q61：测试是什么类型？**
架构级集成测试：`@SpringBootTest` + MockMvc + H2，覆盖 Controller→Authorization→Service→Mapper→DB 的完整链路；不是只测单个类。

**Q62：为什么用集成测试而不是纯单元测试？**
核心风险在跨层：会话、权限并集、范围、事务、并发、版本冲突。MockMvc 隔离 HTTP，H2 隔离数据库，既能复现真实请求，又不依赖外部服务。

**Q63：29 个用例覆盖什么？**
匿名 401、登录失败不可区分、会话轮换/退出/停用失效、首次改密、SELF/DEPT 范围、跨部门越权、用户增删改、账号保护、管理角色限制、权限并集与立即撤销、409、回滚、并发 CAS、并发删除、PBKDF2/长密码/Unicode、分页与脏数据。

**Q64：怎么模拟失败和并发？**
`@SpyBean RoleMapper` 在写完抛异常验证回滚；`ExecutorService + CountDownLatch` 让两个请求同时提交，断言只一个 200、另一个 409；并发删除用锁门闩控制时序。

**Q65：覆盖率为多少？**
行 88.7%、指令 87.1%、分支 70.3%、类 100%；报告在 `target/site/jacoco/index.html`，本轮 `Tests run: 29, Failures: 0, Errors: 0`。

**Q66：H2 测试能代替 MySQL 吗？**
不能。H2 只用于快速隔离测试；真实 MySQL 还通过迁移、导入脚本、真实 HTTP 冒烟和重启持久化验证，两类证据分开记录。

**Q67：CI 跑什么？**
GitHub Actions 四个任务：仓库检查、后端测试、前端测试与构建、MySQL Docker 真实 HTTP 验收。

**Q68：还有哪些没测？**
原始 Excel 全量导入、正式数据量下的数据库性能、两套后端等价性、HTTPS/CSRF、浏览器全景兼容；这些在验证记录里保持“待执行”。

### 4.7 部署与 JavaEE

**Q69：怎么部署的？**
Docker Compose 三个服务：MySQL 8.4、JDK 21 Spring Boot 后端、Nginx；后端镜像多阶段构建（Maven 构建 → JRE 运行）；只发布 Nginx 端口。

**Q70：容器、镜像、卷的区别？**
镜像是只读模板；容器是镜像的运行实例；命名卷保存 MySQL 数据，`down` 不删卷，`down -v` 才删。

**Q71：健康检查怎么做？**
MySQL 用 `mysqladmin ping` 或 `SELECT 1`；后端用 Java 健康检查类请求 `/api/v1/health`；Nginx 用 `wget /nginx-health`；Compose 的 `depends_on: condition: service_healthy` 控制启动顺序。

**Q72：敏感配置怎么传？**
`deploy/.env` 通过 Compose 环境变量注入；文件 600、不进 Git；`.env.example` 只有占位符；数据库和后端端口不发布。

**Q73：重启后数据还在吗？**
在。MySQL 数据在命名卷；重启验证脚本先创建临时账号，`down` 后 `up`，再确认账号仍存在并清理。会话在内存里，重启后需要重新登录。

**Q74：Nginx 做什么？**
提供 Vue 静态文件、`/stage1/`、`/api-design/`，把 `/api/v1` 和 Swagger 代理到后端；同源避免跨域 Cookie 问题；Docker DNS 每 5 秒重新解析后端地址。

**Q75：Spring Boot 自动配置是什么？**
通过 starter 和条件注解自动装配 Web、JDBC、JSON、校验等 Bean；我们只用需要的 starter，密码编码器自己定义。

**Q76：IoC/DI 怎么用？**
Service/Mapper/Controller 由 Spring 容器管理，构造器注入依赖；测试可以用 `@SpyBean` 替换真实 Bean 的部分方法。

**Q77：Spring MVC 关键注解？**
`@RestController`、`@RequestMapping`、`@GetMapping/@PostMapping/@PutMapping/@PatchMapping/@DeleteMapping`、`@RequestBody`、`@PathVariable`、`@RequestParam`、`@Valid`。

**Q78：参数校验怎么做？**
DTO 上用 `@NotBlank/@NotNull` 等，Controller 加 `@Valid`；失败由 `MethodArgumentNotValidException` 统一转 400。

**Q79：全局异常处理怎么做？**
`@RestControllerAdvice` + `@ExceptionHandler`；业务异常 `ApiException` 带状态码和错误码，未知异常记日志并返回 500，不泄露堆栈。

**Q80：事务注解怎么工作？**
`@Transactional` 由 Spring AOP 代理开启/提交/回滚；默认运行时异常回滚；需要新事务或挂起用 propagation；读已提交用 `isolation=READ_COMMITTED`。

**Q81：Maven 生命周期？**
`clean` 清 target；`compile` 编译；`test` 跑 Surefire；`package` 打包；`verify` 在 package 后跑集成检查。JaCoCo 在 test 阶段注入探针并生成报告。

**Q82：Profile 怎么切换？**
`application.yml` + `application-h2.yml` / `application-mysql.yml`；`SPRING_PROFILES_ACTIVE=mysql` 用于服务器，测试用 `h2`；环境变量覆盖数据源。

**Q83：为什么用 Docker 多阶段构建？**
构建阶段用 Maven+JDK 下载依赖、编译、打包；运行阶段只保留 JRE 和 jar，镜像更小、攻击面更小。

**Q84：前后端怎么同源？**
Nginx 同时提供静态页面和 `/api` 反向代理；浏览器只访问一个源，Cookie 自动携带，不需要 CORS。

**Q85：如果服务器挂了怎么办？**
先看 `docker compose ps` 和 `logs backend/db`；常见是数据库未 healthy、端口占用、环境变量错误；重启 `docker compose up -d`，保留数据卷。检查当天准备截图/录屏和手机热点兜底。

**Q86：数据库为什么用 8.4？**
LTS、支持 CHECK 约束和递归 CTE、与迁移脚本兼容；部署用官方镜像，字符集 utf8mb4。

**Q87：为什么密码哈希不提交仓库？**
公开仓库规则：真实员工信息、密码、哈希、原始 Excel、备份都不提交；仓库只有合成数据和占位配置。

### 4.8 刁钻问题与边界

**Q88：你说默认拒绝，怎么证明？**
测试 `allProtectedFamiliesRejectAnonymous`、`departmentAdminCannotGrantOrRemoveManagementRoles`、`selfAndUniqueAdminProtection`；代码上 `require` 取不到权限码直接 403，没有 fallback allow。

**Q89：前端把 `permissionCodes` 改成全部权限会怎样？**
后端不读请求里的权限字段，只从会话用户 ID 重新查数据库；改了也没用，受保护接口仍按真实授权判断。

**Q90：两个管理员同时改同一用户角色会怎样？**
都带 version=1，只有一个 `compareAndIncrement` 成功变成 2；另一个 409，数据是第一个成功的结果。测试 `simultaneousCasHasExactlyOneWinner` 断言 `[200,409]`。

**Q91：删除角色和分配角色同时发生会怎样？**
删除先 `FOR UPDATE` 锁角色并读成员；分配按角色 ID 升序锁。二者串行化，后执行的一方看到最新关系，未确认删除返回 409，不会留下“角色已删但用户还引用”的状态。

**Q92：范围外和不存在的目标为什么都返回 403？**
防止通过 404/403 差异枚举目标是否存在；统一 `OUT_OF_SCOPE`，且不返回目标字段。

**Q93：为什么不用数据库触发器做全部约束？**
RBAC0 阶段用服务层事务和版本控制保证一致性；触发器会把业务规则分散到数据库，难测试、难迁移。第二阶段约束可在数据库层加兜底（需求里已预留）。

**Q94：如果老师问“这个功能是谁做的”？**
按分工回答：成员 1 数据库/导入，成员 2 公共页面，成员 3 角色与授权页面，成员 4 面向功能后端与部署，成员 5 面向对象后端与后续函数式模块，成员 6 API 与独立验收。不要抢别人的工作，也不要贬低队友。

**Q95：如果被问到没做的功能？**
直接说“这部分属于第二阶段/第三阶段，当前只保留了扩展位置”，并说明当前实现边界；不要假装已经实现。

**Q96：如果代码里有一段你讲不清？**
承认“这段是队友负责/我需要打开代码确认”，然后现场打开对应文件读关键行；老师更看重你是否理解设计，而不是背代码。

## 5. 不会答时的话术与分工

| 场景 | 话术 |
| --- | --- |
| 不是自己负责 | “这块主要是 X 负责的，请 X 补充；我从接口/授权角度补一点……” |
| 后续阶段 | “这是 RBAC1/2 的内容，第一阶段只保留了扩展位置，当前边界是……” |
| 不确定数字 | “我需要打开验证记录确认，当前记录是……”不要编数字 |
| 被追问实现细节 | 打开对应文件，按“入口→授权→服务→SQL”顺序读 3–5 行 |
| 完全不会 | “这个点我目前答不上来，我记下来回去补；我能说明的是相关设计是……” |

发言分工建议（口试是小组计分，个人也要发言）：

- 成员 4：后端过程、授权、事务/并发、部署、测试；
- 成员 5：面向对象后端、后续函数式模块、双后端对比；
- 成员 1：数据库、迁移、导入、数据规模；
- 成员 2、3：原型、页面、前后端契约；
- 成员 6：API 契约、独立验收、测试证据。

## 6. 检查当天时间线与应急预案

| 时间 | 动作 |
| --- | --- |
| 提前 60 分钟 | 到教室，连网，打开服务器五个入口，跑一遍 health |
| 提前 30 分钟 | 跑一遍 `mvn clean verify`，确认 29 项通过；打开 JaCoCo |
| 助教检查 | 按 2.1—2.5 顺序演示，边操作边说出证据位置 |
| 前往口试教室 | 预留至少 20 分钟；迟到不超过 30 分钟 |
| 口试 | 先 3 分钟陈述，再按分工回答；每人至少发言一次 |

应急：

- 服务器不可用：先 `docker compose ps`/`logs`，重启 backend/db；同时展示截图和录屏，说明真实环境已通过。
- 外网打不开：检查安全组端口；用手机热点换网络再试。
- 测试失败：保留失败输出，先看是否是环境/端口/数据库问题；不要删日志、不要改测试掩盖。
- 覆盖率报告缺失：重新跑 `clean verify`，报告在 `target/site/jacoco/index.html`。
- 投影/网络故障：用本地保存的截图、录屏和 API 输出继续讲。

## 7. 证据记录模板

```text
姓名 / 时间：
被测 SHA：
服务器 / 端口：
环境与数据：Ubuntu 22.04、MySQL 8.4、合成数据
助教检查：
- 原型：/ 与 /stage1/ 均 200
- 后端：db/backend/web healthy；health = {"status":"UP"}
- API：OpenAPI 25 路径 33 操作；登录/me/users 调通；未登录 401；员工 403
- 测试：Tests run: 29, Failures: 0, Errors: 0；BUILD SUCCESS
- 覆盖率：行 88.7%、指令 87.1%、分支 70.3%、类 100%
口试：
- 小组陈述与问答记录：
剩余事项：
```

相关文档：[部署与自测记录](../testing/member4-stage1-deployment.md)、[服务器自测说明](stage1-server-self-test.md)、[验证记录](../stage1/verification-record.md)、[详细设计](../design/detailed-design.md)、[需求规格](../requirements/req-spec.md)。
