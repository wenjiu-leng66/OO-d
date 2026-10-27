# 检查前自测步骤

命令均在仓库根目录执行。先在独立的合成验收环境排练，避免重置真实员工数据。通过标准是实际结果等于本页预期，并在 [验证记录](verification-record.md) 留下版本、环境与证据；不能仅凭测试脚本存在认定通过。

先完成助教实操，再参加老师口试。助教必须现场看到后端测试运行和覆盖率报告；完整流程见 [检查入口](README.md)，异常/边界见 [检查清单](review-checklist.md)。每次执行前先核对目标环境，以下写入自测仅用于专用合成验收实例。

小组指定 API 入口为 [服务器 Swagger](http://124.70.42.35:8080/swagger-ui/index.html?url=/api-design/openapi.yaml)。公网只读检查记录见 [验证记录](verification-record.md)；现场登录及写接口由部署负责人按计划操作。

## 1. 固定检查版本

```powershell
git status --short
git rev-parse HEAD
python --version
java -version
node --version
docker compose version
```

记录源码 SHA 和未提交修改。要求 Python 3.11+、JDK 21、Maven、满足 Vite 版本要求的 Node；服务器运行使用 Docker Compose 2.20+。Docker 方案会在镜像内构建 Java/前端，本机只需 Docker、Git 和执行冒烟脚本的 Python。

Windows有多个Python时，先确认上述`python --version`满足要求；可用`py -3.12`替换下文`python`。项目要求Python3.11+，不要误用系统中其他旧Python。Linux通常使用`python3`。

## 2. 先跑自动化检查

```powershell
python scripts/check_repository.py
python -m pip install -r scripts/requirements-validation.txt
python scripts/check_openapi.py
python -m unittest discover -s tests/unit -p "test_*.py"
node --test tests/frontend/*.test.cjs
mvn -f backend-procedural/pom.xml clean verify
npm --prefix frontend ci
npm --prefix frontend run build
git diff --check
```

这些命令分别检查仓库/链接、OpenAPI、导入清洗、原型与权限策略、Java隔离单元与H2整合测试、前端生产构建。助教检查时执行完整的 `clean verify`，展示Surefire逐类结果与JaCoCo的行/分支/方法覆盖率，并说明隔离单测的mock边界。后端测试的数据库与真实服务器 MySQL 应分开记；H2 测试通过不能替代 MySQL 与持久化检查。Maven 的 Surefire 与 JaCoCo 报告位于 `backend-procedural/target/`，运行日志留在本地，不上传员工资料或凭证。

## 3. 启动服务器同款环境

Linux 服务器按 [部署说明](../../deploy/README.md) 安装 Docker，在项目根目录执行：

```bash
cp deploy/.env.example deploy/.env
# 用编辑器填写 MYSQL_PASSWORD、MYSQL_ROOT_PASSWORD、RBAC_INITIAL_PASSWORD
# 合成验收环境使用 RBAC_DEMO_ENABLED=true
sh scripts/deploy-stage1.sh
docker compose --env-file deploy/.env -f deploy/compose.yaml ps
```

Windows 本地 Docker Desktop 可先复制并编辑配置，再执行 Compose：

```powershell
Copy-Item deploy/.env.example deploy/.env
# 编辑 deploy/.env；不要把密码粘到日志或提交 Git
docker compose --env-file deploy/.env -f deploy/compose.yaml up -d --build --wait
```

浏览器访问 `http://localhost/`，应打开界面原型；`/stage1/` 是真实后端检查入口；`/api-design/openapi.yaml` 是统一接口契约；`/swagger-ui/index.html?url=/api-design/openapi.yaml` 可查看接口。`/api/v1/health` 应返回 `{"status":"UP"}`，同时代表数据库连接正常。服务器访问时将 `localhost` 换成服务器域名或 IP。

`frontend/` 的模拟演示密码仅属于界面原型；真实后端密码取 `RBAC_INITIAL_PASSWORD`。服务器检查用真实后端入口或 HTTP 脚本，不把原型右上角切换身份当作真实登录。

## 4. 真实 HTTP 自动自测

```powershell
python scripts/smoke_http.py --base-url http://127.0.0.1 --env-file deploy/.env --check-web
python scripts/check_openapi.py --base-url http://127.0.0.1
```

脚本使用真实 Cookie，会创建并清理临时合成账号。运行前确认 `RBAC_DEMO_ENABLED=true`，且该实例未装载真实课程员工数据。它从本地环境文件读取初始密码，不在命令行显示密码。检查失败应退出非零，不能把部分 PASS 当作整体通过。

预期覆盖：健康与公开入口；未登录 401；错误凭证统一 401；Cookie 登录与退出；普通员工调用管理接口 403；本人台账范围；部门管理员越范围/管理角色拒绝；首次改密拦截；角色整组替换和旧版本 409；账号/超级管理员保护；停用后旧会话 401。具体实际覆盖以脚本输出和测试源码为准，41 项覆盖表中未被自动覆盖的仍须手工演示。

在服务器执行同一命令后，换另一台电脑从外网执行一次：

```powershell
python scripts/smoke_http.py --base-url https://你的域名 --env-file deploy/.env --check-web
```

未配置域名和 HTTPS 时使用实际 `http://服务器IP`，并在部署记录注明访问方式；不要把上面的占位地址作为真实入口。服务端自测不能代替外网访问验证。

## 5. 手工排练关键验收场景

先开两个浏览器会话或使用无痕窗口，避免混用 Cookie。合成演示账号包括 `admin`、`SG000001`、`SG000011`、`SG000013`、`SG000281`、`DEMO000100`；原型与后端的数据规模和密码来源独立。

| 步骤 | 操作 | 应看到的结果 |
| --- | --- | --- |
| 1 | 不登录直接 `GET /api/v1/users`；普通员工登录后再请求 | 先 401，后 403，数据库不变 |
| 2 | 用不存在账号、错误密码、停用账号登录 | 都是 401 `INVALID_CREDENTIALS`，同一“用户名或密码错误”提示 |
| 3 | 管理员新建临时用户，再用初始密码登录 | `mustChangePassword=true`；仅本人信息、改密、退出可访问；其他接口 403 |
| 4 | 错旧密码改密，再用正确旧密码改密；退出并用新旧密码分别登录 | 错旧密码拒绝；改密后旧密码失败、新密码成功 |
| 5 | 员工 `SG000013` 查看菜单、考勤、我的权限及台账 | 无系统管理后台；考勤查看/编辑为 SELF；台账只见自己；无权按钮置灰 |
| 6 | 部门经理 `SG000011` 看台账，再修改本部门/其他部门电话或邮箱 | 查看 ALL；本部门可改，其他部门 403 且响应无目标字段 |
| 7 | 修改请求 URL 的 ID、请求体用户 ID 或提交姓名字段 | 篡改不能扩大范围；台账非电话/邮箱字段为 400 `FIELD_NOT_EDITABLE` |
| 8 | 公司领导 `SG000001` 打开用户管理并直接调用新增/删除 | 页面只读；接口仍返回 403 |
| 9 | 部门管理员 `DEMO000100` 查询本部门及下级、筛范围外部门、给范围外用户分配角色 | 只见本部门子树；范围外筛选为空；越权修改 403 |
| 10 | 部门管理员尝试分配 ADMIN/DEPT_ADMIN 或修改角色授权 | 403，原有关系不变 |
| 11 | 两个管理员先 GET 同一临时用户角色版本；第一个保存，再用旧版本保存 | 第一请求成功并 +1；第二请求 409 VERSION_CONFLICT，保存结果不被覆盖 |
| 12 | 给自建角色授权，漏 read、传未知权限、旧版本；再保存合法带范围集合 | 非法请求拒绝且原集合不变；合法请求整体保存并 +1 |
| 13 | 员工保持登录，管理员撤销其角色、停用角色、再恢复 | 下一次请求立即按新授权执行，无需重新登录 |
| 14 | 停用仍保持登录的临时用户 | 该用户下一次请求 401；重新登录统一凭证错误 |
| 15 | 尝试停用/删除自己、改自己角色；停用 ADMIN、删除内置角色、改 ADMIN 授权 | 409 PROTECTED_ACCOUNT / PROTECTED_ROLE |
| 16 | 删除仍有用户的自建角色，先不确认，再带 confirm=true | 先 409 ROLE_HAS_USERS；确认后清除关系并更新版本 |
| 17 | 删除作为部门负责人的临时用户（仅合成库） | 用户角色关系清除，部门负责人置空，无孤立记录 |

授权写入中途失败后的事务回滚应由后端集成测试验证，不能仅用原型“模拟失败”按钮作证据。完成排练后清理临时账号/角色并恢复为验收初始状态。

## 6. 数据重启后仍在

```powershell
python scripts/smoke_http.py --base-url http://127.0.0.1 --env-file deploy/.env --mode persistence-create
docker compose --env-file deploy/.env -f deploy/compose.yaml down
docker compose --env-file deploy/.env -f deploy/compose.yaml up -d --wait
python scripts/smoke_http.py --base-url http://127.0.0.1 --env-file deploy/.env --mode persistence-verify
python scripts/smoke_http.py --base-url http://127.0.0.1 --env-file deploy/.env --mode persistence-cleanup
```

不要加 `down -v`，它会删除数据库卷。重启后旧会话失效是正常现象，重新登录后临时验证数据仍存在才算持久化通过。再次初始化/导入前先按部署说明备份。

## 7. 全量课程数据和性能

三份原始 Excel 通过受控方式放入 `data/raw/`，不提交 Git。按 [数据库说明](../../database/README.md) 离线导入，并执行 `database/seeds/verify_import.sql`。导入脚本的重复执行会重建数据，应在专用空库操作；它不能当作无损增量更新。

核对 111 / 18 / 115 / 359 / 2154、10000 名员工加 admin、原清单授权 3549，平台部门管理员另外六条；抽查部门负责人、公司领导十人、副职为普通员工、部门经理台账范围、普通员工本人范围。相同输入运行两次比较指纹，密码哈希允许因随机盐不同。检查报告中的每项清洗是否能定位原表行列。

在全量 MySQL 上保存鉴权查询 `EXPLAIN ANALYZE`（目标 ≤10 ms），测用户/台账列表及授权保存响应（目标 ≤1 s），记录机器配置、数据量、SQL/参数、冷热状态和结果。没有原始 Excel 或没有执行测量时，保留“待执行”，不能用合成目录和小样例声称已达全量指标。

## 8. 检查当天

检查服务器容器均 healthy、外网入口可访问；保存源码 SHA、迁移版本、自动测试/HTTP自测/重启验证/全量导入报告。按“原型页面 → API设计 → 后端允许与拒绝 → 授权立即生效/409 → 重启持久化”的顺序演示。完成 [验证记录](verification-record.md) 的实际结果和限制，供老师对照。

## 9. 助教现场二十分钟排练顺序

| 参考顺序 | 动作 | 谁讲、用什么证据 |
| --- | --- | --- |
| 开始前 | 准备同一版本、网络、账号、终端和报告入口 | 孙逸轩统筹；每人核对本人模块 |
| 原型与API | 打开原型；Swagger真实登录并调用一条允许、一条拒绝接口 | 陈清香、杨昕桐展示原型；孙逸轩核对契约 |
| 后端部署 | 出示部署版本、容器/进程与数据库健康 | 王信凯解释实际调用链，不把localhost当服务器 |
| 现场测试 | 运行 `mvn -f backend-procedural/pom.xml clean verify` | 王信凯、胡俊朗说明单元与整合测试结构 |
| 覆盖率与数据 | 打开JaCoCo，讲未覆盖分支；展示迁移/导入核对范围 | 胡俊朗讲测试，郑凯讲数据 |
| 结束 | 保存结果、缺陷与助教确认信息，再去老师时段 | 全组按实际预约安排，预留测试耗时 |

表中是任务衔接，不替代每个人在老师口试中的发言。构建和依赖下载应提前准备，现场仍实际运行测试；不能只出示旧截图。
