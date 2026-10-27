# 市政公司统一权限管理系统 · RBAC Permission System

“有请下一组”六人小组的 OOAD 项目。系统统一管理员工、组织、角色、资源目录和授权，并用员工台账验证权限的数据范围。

本仓库用于组内开发、代码阅读和检查排练；[Gitee 交付仓库](https://gitee.com/wenjiuleng66/rbac-permission-system)面向正式检查，收录交付所需材料。两边按用途独立维护，不能将 GitHub 的内部资料直接镜像覆盖到 Gitee。

## 先弄清楚当前实现

| 部分 | 当前实现 | 核查方式 |
| --- | --- | --- |
| 面向功能后端 | JDK 21、Spring Boot、Spring JDBC；真实会话、数据库和 RBAC0 鉴权 | Java 单元/整合测试、Swagger 和真实 HTTP |
| 完整界面原型 | Vue 3 + Element Plus；统一使用浏览器内存 mock | 页面演示、前端测试、生产构建 |
| API 设计 | `api/openapi.yaml`，版本 0.3.0，25 路径 / 33 操作 | 规范校验、运行路径比对、逐接口现场调用 |
| 数据库 | MySQL 8.4 的 V001 十表、回滚、离线 Excel 导入与核对 SQL | 专用库迁移、导入报告、约束与持久化检查 |
| 面向对象后端 | `backend-oo/` 只有目录规划，尚不能运行 | RBAC1 阶段实现 |
| 继承、约束、激活角色、审计 | 后续阶段设计；页面为静态示例 | 不计作一期后端能力 |

**原型与真实后端是两条独立链路。** 原型中的登录、保存和切换身份不会写入 MySQL，也不证明服务端权限、事务或旧会话失效。当前一期允许分别展示，但现场必须实际调用已部署后端。

## 第一次读仓库的顺序

1. 读本页，了解目录、启动和当前边界。
2. 读 [一期检查入口](docs/stage1/README.md)，分清助教实操和老师口试。
3. 读 [需求规格](docs/requirements/req-spec.md)及 [41 条需求追溯](docs/stage1/requirement-coverage.md)，明确“要做什么”。
4. 读 [结构化设计](docs/design/structured-design.md)及 [后端详细设计](docs/design/detailed-design.md)，明确“怎样分解、数据怎样流动”。
5. 按 [代码阅读路线](docs/stage1/code-reading-guide.md)追一次登录、一次范围查询和一次授权保存。
6. 按 [自测步骤](docs/stage1/self-test.md)运行，再用 [边界检查清单](docs/stage1/review-checklist.md)补查；实际结果记在 [验证记录](docs/stage1/verification-record.md)。
7. 全组按 [口试对照与分工排练](docs/stage1/oral-review.md)讲解自己的代码和测试。

## 现场入口

| 入口 | 地址 |
| --- | --- |
| 界面原型 | [服务器首页](http://124.70.42.35:8080/) |
| API 展示及真实调用 | [Swagger UI](http://124.70.42.35:8080/swagger-ui/index.html?url=/api-design/openapi.yaml) |
| 权威 API 文件 | [OpenAPI YAML](http://124.70.42.35:8080/api-design/openapi.yaml) |
| 健康检查 | [后端与数据库状态](http://124.70.42.35:8080/api/v1/health) |

2026-10-27 已以只读 GET 核验公开首页、API 文件和运行文档可访问，健康检查为 `UP`。服务器源码 SHA、身份调用、业务写入和重启持久化仍需现场确认；只读入口成功不等于全部验收通过。该 Swagger 地址是小组提供的 API 检查入口，是否满足课程“API 仓库”的归档要求由助教确认。

## 在本地运行

工具：JDK 21、Maven 3.9、Python 3.11+、Node.js 22.12+（已用 Node 24 验证）、npm。所有文件使用 UTF-8。完整配置见 [环境约定](docs/standards/environment.md)。

### 只看界面原型

在仓库根目录执行：

```powershell
npm --prefix frontend ci
npm --prefix frontend run dev
```

打开终端显示的地址（默认 `http://localhost:5173`）。原型合成账号可在登录页选择，原型演示口令为 `123456`；身份表和状态说明见 [前端 README](frontend/README.md)。刷新页面会恢复内存业务数据。

### 独立运行真实后端

新开终端，仍在仓库根目录执行：

```powershell
$env:RBAC_DEMO_ENABLED = 'true'
$env:RBAC_INITIAL_PASSWORD = Read-Host '设置本地合成环境初始密码（至少8位）'
mvn -f backend-procedural/pom.xml clean verify
java -jar backend-procedural/target/rbac-procedural-backend-0.1.0-SNAPSHOT.jar
```

默认端口 `8080`，默认 H2 文件位于启动目录下的 `work/`；测试使用独立内存 H2。Swagger 本地入口为 `http://localhost:8080/swagger-ui/index.html`，真实后端口令来自环境变量，不使用原型口令。合成初始化仅在空库且显式启用时执行；修改初始口令配置不会重置已有账号。MySQL 启动和全部环境变量见 [后端 README](backend-procedural/README.md)。

### 部署同款环境

```powershell
Copy-Item deploy/.env.example deploy/.env
# 在本机编辑 deploy/.env，分别配置数据库、root和后端初始密码。
docker compose --env-file deploy/.env -f deploy/compose.yaml up -d --build --wait
```

需要 Docker Compose 2.20+。Linux 可使用 `sh scripts/deploy-stage1.sh`。只有 Web 端口映射到宿主机，数据库和 Java 无主机端口映射；MySQL 使用命名卷。完整部署、备份与重启步骤见 [部署说明](deploy/README.md)。原型默认 `/`，真实后端调试 `/swagger-ui/index.html`，统一检查导航 `/stage1/`。

## 自查命令与报告

以下命令均在仓库根目录执行；Windows 确认 `python --version` 为 3.11+，Linux 可用 `python3`。

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

| 证据 | 位置 / 用途 |
| --- | --- |
| 后端逐测试结果 | `backend-procedural/target/surefire-reports/`；区分隔离单元测试和 `Stage1ApiTest` 整合测试 |
| 后端覆盖率 | `backend-procedural/target/site/jacoco/index.html`、`jacoco.xml`；现场展示实际行/分支/方法覆盖率 |
| 前端测试 | `tests/frontend/*.test.cjs`；覆盖原型策略、mock 与路由，不能代替服务端测试 |
| 导入测试 | `tests/unit/test_import_excel.py`；合成工作簿，不等于正式三份 Excel 全量导入 |
| 本次测试报告 | [90项后端测试与JaCoCo](docs/testing/test-report-2026-10-27.md) |
| 本次及历史结果 | [验证记录](docs/stage1/verification-record.md)；记录版本、命令、环境和范围 |

启动专用合成环境后，再执行 `python scripts/smoke_http.py --base-url http://127.0.0.1 --env-file deploy/.env --check-web`。脚本会创建及清理临时合成数据，先确认目标；现场已部署业务库不得未经安排执行写入自测。`check_openapi.py --base-url ...` 只比较方法与路径集合，请求/响应字段和授权语义另行验证。

## 目录和主责

| 目录 | 内容 | 主责 |
| --- | --- | --- |
| `frontend/` | 一套 Vue 原型；`prototypes/` 为早期独立原型 | 陈清香、杨昕桐 |
| `backend-procedural/` | Controller → Service → Mapper → 数据库，RBAC0 真后端 | 王信凯，胡俊朗协作 |
| `backend-oo/` | 后续面向对象与函数式模块规划 | 胡俊朗 |
| `api/` | OpenAPI 及历史接口提案 | 孙逸轩统筹，各模块作者维护 |
| `database/`、`data/` | 迁移、导入、稳定编码、合成样例 | 郑凯 |
| `deploy/`、`scripts/`、`tests/` | 部署、检查脚本、跨模块测试 | 各模块作者，孙逸轩集成 |
| `docs/` | 需求、设计、分工、追溯、验收和内部解释 | 全组 |

详细责任见 [六人分工](docs/planning/team.md)。功能实现按 [阶段计划](docs/planning/milestones.md)分期；助教检查和老师口试是一次课程检查的两个环节，不能与 RBAC0/RBAC1/最终阶段混为一谈。

## 成员贡献整合

[本次整合决定](docs/stage1/contribution-review-2026-10-27.md)记录代码修复、数据/部署历史证据和模型图的采纳及修订。[UML图集](docs/uml/README.md)与[数据库设计](docs/database/database-design.md)和实际代码对照维护。历史记录不等于当前版本重新部署或重跑验收。

## 数据、限制和协作

正式基准：111 个组织、18 个系统、115 个模块、359 个功能点、2154 条清单权限、10000 名员工加 `admin`；原清单五角色 3549 条授权，平台部门管理员另外 6 条。公开仓库仅含合成数据和稳定目录编码，正式原表通过受控方式离线提供。

导入器只生成 SQL；SQL 通过TRUNCATE清空十表数据后重新导入，不重建表结构，也不是增量更新。必须在专用库执行，检查失败行、未解析映射、数量和脱敏报告。当前部分后端列表先全表读入内存再裁剪/分页，全量数据下的性能需要测量；现有 Origin 检查也不等于完整 CSRF 令牌方案。所有实际限制和改进项见 [边界清单](docs/stage1/review-checklist.md)。

开发按 [贡献流程](CONTRIBUTING.md)从短期分支开始，变更需求、接口、迁移时同步代码和测试。历史讨论、成员解释和截图保留用于追溯；发生冲突时按 [文档索引](docs/README.md)中的权威顺序核对，不能把旧草案当当前实现。
