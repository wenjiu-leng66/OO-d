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
| 数据来源 | 合成测试/性能数据；课程三份原始 Excel 的全量导入与重复导入已在本机独立核对库完成（见下方“全量导入核对证据”） |

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
| 全量 Excel 导入与重复导入 T-12 | 通过 | 2026-10-18 成员1在本机MySQL8.4.8独立库`rbac_verify_full`用三份原始Excel导入两轮：111/18/115/359/2154、用户10001、授权3549+平台6=3555，`verify_import.sql`空检查全空；两轮第9条指纹与逐表SHA-256强指纹完全相同（仅密码哈希与时间列不同）。详见下方证据 |
| 万条合成数据本机HTTP查询性能 | 通过 | 临时增加10000合成用户，总10040（含1个持久化标记）；暖机1次+测量5次，users page20中位64.29ms/最大70.91ms、ledger中位47.25ms/最大51.26ms、me/permissions中位31.09ms/最大41.27ms。本机端到端HTTP测量，仅证明这三项合成场景在1秒内 |
| 全量数据上的保存及DB性能 | 待执行 | 全量库已建好（`rbac_verify_full`）；保存授权≤1s、数据库纯鉴权EXPLAIN ANALYZE≤10ms仍须在全量库补测；上行HTTP数据不能替代 |
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

本轮按用户最新要求完成仓库整合和本机验证，未连接或操作其服务器。已准备服务器部署脚本与自测步骤，留待用户自行执行。后端29项、前端50项、导入41项、真MySQL HTTP22检查点、SELF管理范围及本机重启持久化均通过，静态契约与运行接口33项完全覆盖，原型和静态API设计已在本地浏览器查看。原始Excel全量导入与重复导入已于2026-10-18在本机独立库核对通过（3555条授权）；全量库上的数据库纯鉴权与保存性能、服务器外网验收仍需后续实测，不能由合成测试替代。

## CI 容器验收证据

[MySQL Docker HTTP acceptance](https://github.com/wenjiu-leng66/RBAC-Permission-System/actions/runs/39111641339/job/117628866178) 在源码 0fe6747a020044e85d921cdb47a88e00b570de8e 上成功。该轮仓库检查只因历史 Markdown 空白失败，已在 fc0ec1e 修正；最终整轮状态以 PR #18 最新检查为准。

## 全量导入核对证据

T-12 / IMPORT-05，执行人成员1，2026-10-18 17:10–17:25。导入脚本与迁移取自 `29543856a4d429ebf3379a44e22d7b7695af6771`（`database/` 与 `aaf64ec` 相同）。环境 Windows 11、MySQL 8.4.8、Python 3.13.16（脚本只用标准库）。独立全量核对库 `rbac_verify_full`，不是演示库或集成库；原表、生成 SQL 和含个人信息的报告只存放在本机受控目录，未进入仓库。

操作：同一份三表输入用 `import_excel.py --dept --perm --emp` 生成两次 SQL（各约2分钟）；空库执行 V001 → 导入第1轮（2.36秒）→ `verify_import.sql` 与强指纹 → 在同一库再导入第2轮（2.58秒，走清空重建路径）→ 再次核对。

| 核对项 | 基准 | 实际 |
| --- | --- | --- |
| 组织 / 负责人关联 | 111 | 111，111/111，待核对0 |
| 系统 / 模块 / 功能点 / 权限 | 18 / 115 / 359 / 2154 | 相同，新登记编号0 |
| 用户 | 10000 + admin | 10001；员工表10000行成功、0失败 |
| 原五角色授权 | 3549 | ADMIN 2154、LEADER 466、DEPT_MANAGER 582（DEPT 259）、PROJECT_MANAGER 253（DEPT 92）、STAFF 94（SELF 9） |
| 平台部门管理员授权 | 6 | `sys:f339` read/create/edit/delete=DEPT、`sys:f340:read`=ALL、`sys:f342:read`=DEPT |
| 角色权限关系合计 | 3555 | 3555 |
| 有效权限视图行数 | 987,222 | 987,222 |
| `verify_import.sql` 第3–7条 | 空 | 两轮均空；无密码哈希为空的用户 |
| 清洗警告 | 3条 | H117、G310 的 view，I340 空白，与基准相同 |

重复导入：`verify_import.sql` 第9条两轮均为 dept 3410408844、user 1115645591、user_role 3789538624、perm 2058455335、grant 2830430600。另按主键排序逐表计算 SHA-256（排除 `password_hash` 与时间列），10张表两轮完全相同，例如 `sys_role_permission` 3555行为 `66990cec467532ea8bf284b2f39630246adedb030cd095f675118396375da837`、`sys_user` 10001行为 `b8c44330edbcf7eb82513e3ae125dbbf8d5739b0cf09bf24bc33ae4e36f2dccf`。两次生成的 SQL 屏蔽10001个密码哈希后逐字节相同；`app_function` 库内哈希与从源 SQL 反算的一致，确认中文无转码损失。
