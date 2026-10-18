# RBAC Permission System

“有请下一组”的 OOAD 课程设计：市政公司统一权限管理系统。

当前集成版汇总六位成员已有需求、数据库、Vue 界面原型、API 设计和面向功能 Java 后端，并补齐 RBAC0 所需接口、数据范围、首次改密、授权版本与保护规则。第一阶段按“**完整后端、完整界面原型、API 设计，上传服务器并可以运行**”检查；真实前后端联调不是本阶段的必要条件。

按用户最新要求，本轮只完善仓库并做本机验证，**不连接或操作用户服务器**。部署脚本已经准备，待用户自行部署；没有声称已经上线。实际测试、全量原始资料导入和后续部署状态见 [一期验证记录](docs/stage1/verification-record.md)。

## 先运行起来

以下服务器步骤留供用户自行执行，本轮未连接或操作服务器。

在有 Docker Compose 2.20+ 的 Linux 服务器上，进入仓库根目录：

```bash
cp deploy/.env.example deploy/.env
# 编辑 deploy/.env，设置数据库密码、后端初始密码，合成验收使用 RBAC_DEMO_ENABLED=true
sh scripts/deploy-stage1.sh
```

本地 Windows Docker Desktop 可复制并编辑同一环境文件，再执行：

```powershell
Copy-Item deploy/.env.example deploy/.env
docker compose --env-file deploy/.env -f deploy/compose.yaml up -d --build --wait
```

不要提交 `deploy/.env`。新建数据库卷自动加载正式 V001 表结构；数据库端口不发布到公网。已有数据库的迁移、原始资料导入、备份与重启步骤见 [部署说明](deploy/README.md)。

| 入口（默认本机） | 用途 |
| --- | --- |
| `http://localhost/` | 完整 Vue 界面原型，使用明确标注的模拟数据 |
| `http://localhost/stage1/` | 真实后端检查入口与自测说明 |
| `http://localhost/api-design/openapi.yaml` | 权威 OpenAPI 0.3.0 契约 |
| `http://localhost/swagger-ui/index.html?url=/api-design/openapi.yaml` | 后端接口查看/调用入口 |
| `http://localhost/api/v1/health` | 后端与数据库就绪检查 |

界面原型账号及模拟密码见 [原型说明](docs/design/stage1-ui-prototype.md)；真实后端使用环境配置 `RBAC_INITIAL_PASSWORD`。模拟身份切换不改变真实后端会话，原型刷新会重置内存数据。正式后端的数据写入 MySQL 持久卷。

本地不使用 Docker 时，可分别按 [后端说明](backend-procedural/README.md) 和 [前端说明](frontend/README.md) 构建运行。

## 检查前自测

```powershell
python scripts/check_repository.py
python -m unittest discover -s tests/unit -p "test_*.py"
node --test tests/frontend/*.test.cjs
mvn -f backend-procedural/pom.xml clean verify
npm --prefix frontend ci
npm --prefix frontend run build
git diff --check
python scripts/smoke_http.py --base-url http://127.0.0.1 --env-file deploy/.env --check-web
```

最后一条需先启动合成验收环境；它调用真实 HTTP、建立 Cookie，会创建并清理临时合成测试数据。完整步骤、关键拒绝场景、MySQL 重启持久化及服务器外网检查见 [检查前自测](docs/stage1/self-test.md)。不要把模拟 API 测试或 H2 测试通过当作云服务器、全量 MySQL 数据或性能已通过。

## 设计和交付材料

| 材料 | 入口 |
| --- | --- |
| 一期交付标准、41 条覆盖和执行记录 | [一期检查入口](docs/stage1/README.md) |
| 需求 | [需求规格](docs/requirements/req-spec.md) |
| API | [OpenAPI](api/openapi.yaml)、[接口约定与rbac映射](docs/standards/api-contract.md) |
| 数据库 | [数据字典](docs/database/data-dictionary.md)、[迁移与导入](database/README.md) |
| 界面原型 | [页面、身份和交互](docs/design/stage1-ui-prototype.md) |
| 成员成果整合 | [分支与SHA记账](docs/stage1/merge-sources.md) |
| 部署与运行 | [服务器部署说明](deploy/README.md) |
| 贡献与分工 | [GitHub手册](docs/guides/github-team-manual.md)、[贡献流程](CONTRIBUTING.md)、[责任表](docs/planning/team.md) |

## 架构与目录

`frontend/` 是 Vue 3 + Element Plus 的完整界面原型。`backend-procedural/` 是 JDK 21 + Spring Boot 3.3.5 的面向功能后端；统一 `/api/v1` JSON API、`JSESSIONID` 服务端会话，逐请求检查权限和 `ALL/DEPT/SELF` 数据范围。部署采用 Nginx、Java 后端与内部 MySQL 8.4；`api/`、`database/`、`docs/`、`scripts/` 分别存放契约、迁移导入、设计验收和检查脚本。

`backend-oo/` 留待第二阶段实现。第二阶段增加继承和两后端比较，第三阶段增加约束、激活角色、审计与函数式模块；一期页面中的后续功能明确标为静态原型。[阶段计划](docs/planning/milestones.md) 中保留课程时间表。

## 数据与公开仓库

原始 Excel、真实员工姓名/电话、密码/哈希、数据库备份、云密钥和访问令牌不得提交。三份课程资料只放被忽略的 `data/raw/`，受控离线导入；仓库样例和演示账号均为合成。

全量基准为111个组织、18个系统、115个模块、359个功能点、2154条清单权限、10000名员工加admin；原清单五角色3549条授权，平台部门管理员另六条。原始资料缺失时只能验证合成数据，不声称全量验收完成。`.gitignore` 不会清除已进入历史的资料，发现秘密泄露须先撤销或轮换凭证。
