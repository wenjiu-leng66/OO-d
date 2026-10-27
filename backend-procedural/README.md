# 第一阶段：面向功能 / 过程后端

Java 21、Spring Boot 3.3.5、Spring JDBC、MySQL 8.4。沿用成员 4、5 的 Controller → Service → Mapper → PO 分层，在成员 1 的统一十表上完成 RBAC0。对外契约见 [OpenAPI](../api/openapi.yaml)，检查流程见 [第一阶段自测说明](../docs/stage1/self-test.md)。

## 本阶段实现

- 工号 / 手机号登录、HttpOnly `JSESSIONID` 会话、退出、首次改密、旧密码验证；账号停用或密码重置后，旧会话的下一次请求返回 401。
- 用户查询、筛选、分页、增改删、启停用、密码重置；删除负责人时清空部门负责人引用。
- 只读部门树与部门及全部下级人员查询，返回负责人、直接人数、含下级人数。
- 角色增改删与启停用、角色用户列表、带数据范围的角色授权；内置角色及唯一超级管理员保护。
- 用户角色 / 角色授权整组替换、版本 CAS、409 冲突、数据库事务回滚。业务角色撤销、角色停用、授权变更在下一次请求生效。
- 权限取并集，同码范围按 `ALL > DEPT > SELF` 合并；`DEPT` 包含全部下级部门；管理权限映射集中在 `AuthorizationService.managementCodes`。
- 按查看权限生成门户菜单；功能点返回六种操作与数据范围；员工台账按范围查询，仅允许修改电话、邮箱。角色下用户列表同样按操作者的用户查看范围裁剪。
- JSON 字段及长度校验、统一错误包络、来源检查和明确的浏览器允许来源配置。没有实现角色继承、约束、角色激活切换或日志审计，这些属于后续阶段。

## 运行合成演示

在 `backend-procedural` 目录执行，先安装 Java 21 和 Maven：

```powershell
$env:RBAC_DEMO_ENABLED = 'true'
$env:RBAC_INITIAL_PASSWORD = Read-Host '设置演示初始密码（至少 8 位）'
mvn test
mvn package -DskipTests
java -jar target/rbac-procedural-backend-0.1.0-SNAPSHOT.jar
```

`mvn test` 使用独立的内存 H2 和合成口令，不读取本地业务库；当前测试含61项隔离单元和29项整合，共90项通过；覆盖范围见下文。应用默认使用 `h2` 配置，数据保存在当前目录的 `work/rbac-local.mv.db`，重启保留数据。演示初始化仅在空库执行，重复启动不会覆盖用户或密码。修改 `RBAC_INITIAL_PASSWORD` 不会重设已有用户密码。

打开 `http://localhost:8080/swagger-ui/index.html`，登录接口为 `POST /api/v1/auth/login`。所有演示账号初始密码均为启动时注入的值：

| 账号 | 用途 |
| --- | --- |
| `admin` | 唯一超级管理员 |
| `SG000001` | 公司领导，只读管理后台 |
| `SG000011` | 部门经理，台账查看全部、编辑本部门 |
| `SG000013` | 普通员工，台账仅本人 |
| `SG000281` | 项目经理 |
| `DEMO000100` | 部门管理员，管理道路工程分公司及其下级 |
| `DEMO000101` | 首次改密演示 |
| `DEMO000102` | 停用账号，登录失败 |
| `DEMO000103` | 无角色账号，可登录，门户菜单为空 |

公开仓库的合成演示含 39 用户、16 部门、18 系统、115 模块、359 功能点、2154 权限。组织和用户只是合成样本，授权也由演示规则生成，不能代替真实的 111 部门、10000 员工及清单授权导入验收。演示数据来源为前端 fixtures，不含真实员工姓名、电话或密码哈希。

## 连接 MySQL 8.4

先在空库执行唯一权威迁移 [V001](../database/migrations/V001__create_core_tables.sql)，再运行 [离线导入脚本](../database/seeds/import_excel.py)。生产后端禁用自行建表，直接使用该迁移；`schema.sql` 仅为 H2 对应方言。可优先使用仓库的 [服务器部署配置](../deploy/README.md)。

```powershell
$env:SPRING_PROFILES_ACTIVE = 'mysql'
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://localhost:3306/rbac?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
$env:SPRING_DATASOURCE_USERNAME = 'rbac'
$env:SPRING_DATASOURCE_PASSWORD = Read-Host '数据库密码'
$env:RBAC_INITIAL_PASSWORD = Read-Host '新增用户和密码重置使用的初始密码'
$env:RBAC_DEMO_ENABLED = 'false'
java -jar target/rbac-procedural-backend-0.1.0-SNAPSHOT.jar
```

新写入口令使用 `{pbkdf2@SpringSecurity_v5_8}`，与导入器一致；旧的带前缀 BCrypt 哈希仍可验证。密码和哈希不通过业务 API 返回。`RBAC_INITIAL_PASSWORD` 未配置或不足 8 位时，创建账号及重置密码被明确拒绝，系统没有内置生产默认密码。

| 环境变量 | 含义 / 默认 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | 默认 `h2`；部署使用 `mysql` |
| `PORT` | HTTP 端口，默认 `8080` |
| `H2_URL` | 本地 H2 连接串，仅 `h2` 配置使用 |
| `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` | MySQL 连接配置，优先于配置文件中的 `DB_URL/DB_USERNAME/DB_PASSWORD` 别名 |
| `RBAC_DEMO_ENABLED` | 默认 `false`；仅显式启用才加载合成演示 |
| `RBAC_INITIAL_PASSWORD` | 必须在环境中注入；不写进仓库 |
| `RBAC_ALLOWED_ORIGINS` | 允许携带 Cookie 的开发页面来源，逗号分隔，默认本机 5173 的两个来源 |
| `SESSION_COOKIE_SECURE` | 本地 HTTP 默认 `false`；公开 HTTPS 使用 `true` |

部署支持可信反向代理传来的 Forwarded / X-Forwarded 标头。后端应放在该代理后的私有网络，外部只开放代理端口。单进程内存会话在后端重启后需要重新登录，数据库数据会保留。

## API 和自测

基础路径 `/api/v1`；业务成功响应直接返回对象 / 分页对象，错误为 `{error:{code,message,requestId,details}}`。分页字段为 `items/page/pageSize/total`，ID 用数字字符串表示，状态为 `ENABLED/DISABLED`，范围为 `ALL/DEPT/SELF`。

- 认证与身份：`/auth/login`、`/auth/logout`、`/me`、`/me/password`、`/me/permissions`。
- 门户和台账：`/portal/menu`、`/portal/functions/{functionCode}`、`/ledger/employees`、`/ledger/employees/{id}`。
- 管理：`/users`、`/users/{id}`、`/users/{id}/status`、`/users/{id}/password-reset`、`/users/{id}/roles`；角色对应 `/roles`、`/roles/{id}`、`/roles/{id}/status`、`/roles/{id}/users`、`/roles/{id}/permissions`。
- 目录和组织：`/departments`、`/departments/{id}/members`、`/catalog/functions`、`/permissions`、`/resources`。
- 健康：`GET /health` 由代理转发至 `GET /api/v1/health`；种子尚未完成时 503，完全就绪且数据库可查询时 200 `{"status":"UP"}`。

HTTP 方法和请求示例以 [OpenAPI](../api/openapi.yaml) 为准，尤其台账单条只有 `PATCH` 编辑接口，没有 `GET` 单条接口。

自动测试：

```powershell
mvn test
# 在另一个已启动后端的终端，读取之前注入的合成密码并执行 HTTP 自测：
python ../scripts/smoke_http.py --base-url http://localhost:8080
```

`Stage1ApiTest` 共 29 项集成测试，覆盖登录失败同提示、手机号登录和会话轮换、首次改密、停用 / 重置旧会话、本人 / 部门 / 下级数据范围、后台越权、用户唯一性及删除负责人清空、账号保护、部门管理员授出限制、同码最大范围、即时撤权、角色 CRUD 与停用、授权合法性、全目录数量、PBKDF2 导入口令、事务写后故障回滚，以及双线程版本 CAS 恰有一个成功、并发角色分配与未确认删除串行、128 字符及 Unicode 口令完整性。JaCoCo 报告输出 `target/site/jacoco/index.html`，不沿用旧版覆盖率数字。MySQL、原始资料导入和服务器重启的验收请按项目级自测说明继续完成。

## 分层单元测试和覆盖率

当前10类隔离测试：`BrowserSecurityConfigTest`、`AuthControllerTest`、`HealthControllerTest`、`AuthorizationServiceTest`、`AuthServiceTest`、`UserServiceTest`、`RoleServiceTest`、`DepartmentMapperTest`、`IdsTest`、`DataInitializerTest`。它们分别验证控制器协作、业务保护/范围/冲突分支、组织遍历/ID边界和合成初始化条件；mock不验证实际SQL、行锁或事务。

在仓库根目录运行 `mvn -f backend-procedural/pom.xml clean verify`，一并执行61项单元与29项整合测试。打开 `backend-procedural/target/site/jacoco/index.html` 查看实际覆盖率；2026-10-27的行90.89%、分支75.31%及未覆盖部分见[测试报告](../docs/testing/test-report-2026-10-27.md)。助教现场须重新运行，不只展示旧报告。

Compose显式传递`RBAC_ALLOWED_ORIGINS`。示例空值表示只允许同源；确需跨域时配置精确可信Origin。同源Swagger不需额外条目。独立后端未设置该变量时的开发回退来源与Compose空值应分别理解。
