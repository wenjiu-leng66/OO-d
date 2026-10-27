# 历史记录：2026-10-18集成验证

本页仅保留原有执行记录，不是2026-10-27重跑结果。最新状态见[当前验证记录](verification-record.md)。

# 第一阶段验证记录

本表区分已有执行证据与待执行验收。状态只允许“通过 / 失败 / 待执行 / 不在一期”。新增结果必须记录命令、版本、环境与实际现象；写有测试用例不等于执行通过。

## 版本与环境

| 项目 | 记录 |
| --- | --- |
| 日期 | 2026-10-18（集成检查；时间使用 Asia/Shanghai） |
| 集成分支 | `integration/stage1` |
| 被测功能源码 SHA | `aaf64ec953bd7aed9c773487c66cb32be20e3db9`；后续若只补文档，功能测试版本保持此记录 |
| 基础来源 | 见 [合并记账](merge-sources.md) |
| 本地环境 | Windows；JBR21.0.10、Maven3.9.11、Node24.18.0、MySQL8.4.8、Bundled Python3.12.14；独立后端18080、MySQL33067、库rbac_stage1，本地浏览器入口5174 |
| 服务器 / 外网 URL | 按用户最新要求，本轮不连接或操作服务器；脚本已准备，待用户自行部署 |
| 服务器源码 SHA / 迁移版本 | 待用户自行部署时填写 |
| 数据来源 | 合成测试/性能数据；课程三份原始 Excel 的本次全量验证待执行 |

## 本次执行状态

| 检查 | 状态 | 实际结果与证据 |
| --- | --- | --- |
| 前端权限策略与模拟 API 测试 | 通过 | `node --test tests/frontend/*.test.cjs`，50项通过 |
| Python 导入单元测试 | 通过 | `python -m unittest discover -s tests/unit -p "test_*.py"`，新增部门管理员授权测试后41项通过；实际使用Bundled Python3.12.14 |
| MySQL 数据库迁移/合成导入 | 通过 | MySQL 8.4.8一次性库：V001→合成Excel生成SQL；重复生成导入快照相同（排除哈希/时间）；工号唯一/FK/scope CHECK拒绝非法写；U001回滚后重建再导入快照相同。数据为6组织/3用户/2系统/2模块/3功能/18权限/34清单授权，非原Excel全量。集成执行者使用本地verify_mysql_import.py，最终日志待归档 |
| 后端 Java 完整测试和打包 | 通过 | `mvn -f backend-procedural/pom.xml clean verify`，最终29项通过（0失败/错误），12.12秒，打包成功；含PBKDF2长密码、角色/删除并发及SELF管理范围回归 |
| OpenAPI 语法/引用及操作覆盖 | 通过 | Python3.12.14 + openapi-spec-validator验证OpenAPI3.0.3，25路径/33操作；静态契约33操作与运行`/v3/api-docs`的33操作集合完全相同，missing/undocumented均空；每个操作声明所需权限 |
| 前端生产构建 | 通过 | `npm --prefix frontend ci`、`npm --prefix frontend run build`成功 |
| 仓库、链接与空白检查 | 通过 | Bundled Python运行`python scripts/check_repository.py`，本地链接/UTF-8/合成关联通过；所辖README/API/docs的`git diff --check`通过，最终提交前再检查全树 |
| 真 HTTP + Cookie 冒烟 | 通过 | `python scripts/smoke_http.py --base-url http://127.0.0.1:18080`（初始密码由运行环境提供），最终29项测试对应版本在MySQL8.4.8真实Java后端22个输出检查点通过；GitHub临时Linux容器环境的 `--check-web` 也通过，证据见下方CI记录 |
| 真MySQL SELF管理范围 | 通过 | 自建SELF管理角色实测部门成员仅本人、SELF不能新建他人账号，两项均通过；与29项中的对应H2回归分开记录 |
| 本地浏览器页面 | 通过 | 本地5174原型页面实际登录成功；Swagger实际加载静态契约，显示“第一阶段统一API”、版本0.3.0；不是云服务器或Docker运行证明 |
| MySQL 服务重启后持久化 | 通过 | `smoke_http.py --mode persistence-create/verify/cleanup`：本机Java和MySQL两个进程停止重启后验证账号仍在，种子跳过已有数据，验证后已清理标记；GitHub容器环境保留命名卷重建后也通过 |
| 全量 Excel 导入与重复导入 T-12 | 待执行 | 需要三份原始 Excel；合成单元测试不替代本项 |
| 万条合成数据本机HTTP查询性能 | 通过 | 临时增加10000合成用户，总10040（含1个持久化标记）；暖机1次+测量5次，users page20中位64.29ms/最大70.91ms、ledger中位47.25ms/最大51.26ms、me/permissions中位31.09ms/最大41.27ms。本机端到端HTTP测量，仅证明这三项合成场景在1秒内 |
| 正式全量数据/保存及DB性能 | 待执行 | 正式Excel全量、保存授权≤1s、数据库纯鉴权EXPLAIN ANALYZE≤10ms仍须补测；上行HTTP数据不能替代 |
| Linux Docker Compose 构建运行 | 通过 | GitHub临时runner构建MySQL/Java/Nginx三服务，真实HTTP与静态入口、运行API契约一致性、仅重建backend后的代理访问、保留卷重建和清理标记全部成功；没有连接用户服务器 |
| 云服务器上传、外网访问 | 待执行 | 用户明确要求本轮不连接或操作服务器；部署脚本已准备，待用户自行部署，不作为本轮代码工作阻塞 |

## 手工验收逐项记录模板

复制以下一行填写每项结果，需求编号对应 [覆盖表](requirement-coverage.md)。不要记录密码、Cookie、哈希、真实员工姓名/电话。

| 用例 / 需求 | 版本、环境、数据 | 命令或操作 | 预期 | 实际 | 状态 | 脱敏证据 / 缺陷 |
| --- | --- | --- | --- | --- | --- | --- |
| 例：T-09 / PORTAL-05 | SHA、MySQL版本、合成库 | 员工A PATCH员工B电话 | 403 OUT_OF_SCOPE，不含B字段，DB不变 | 待执行 | 待执行 | 日志路径/截图 |

## 服务器交付记录模板

| 项目 | 实际值 |
| --- | --- |
| 部署人、日期时间、服务器系统 | 待执行 |
| 域名/IP、访问协议、开放端口 | 待执行 |
| 源码 SHA / 镜像标签或摘要 | 待执行 |
| 迁移版本 / 初始数据类型及数量 | 待执行 |
| 环境文件路径（只记位置，不记值） | `deploy/.env` |
| 备份位置 / 恢复演练 | 待执行 |
| `compose ps` 与健康检查 | 待执行 |
| 外网 HTTP 自测 / 重启验证结果 | 待执行 |
| 检查前需要处理的缺陷 | 待执行后填写 |

## 完成结论

本轮按用户最新要求完成仓库整合和本机验证，未连接或操作其服务器。已准备服务器部署脚本与自测步骤，留待用户自行执行。后端29项、前端50项、导入41项、真MySQL HTTP22检查点、SELF管理范围及本机重启持久化均通过，静态契约与运行接口33项完全覆盖，原型和静态API设计已在本地浏览器查看；原始Excel全量、数据库纯鉴权性能和服务器外网验收仍需后续实测，不能由合成测试替代。

## CI 容器验收证据

[MySQL Docker HTTP acceptance](https://github.com/wenjiu-leng66/RBAC-Permission-System/actions/runs/39111641339/job/117628866178) 在源码 0fe6747a020044e85d921cdb47a88e00b570de8e 上成功。该轮仓库检查只因历史 Markdown 空白失败，已在 fc0ec1e 修正；最终整轮状态以 PR #18 最新检查为准。
