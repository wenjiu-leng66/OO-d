# 测试与覆盖率

项目已有后端隔离单元测试、Spring Boot/H2 整合测试、前端策略与 mock 测试、合成 Excel 导入测试，以及独立的真实 HTTP 验收脚本。不同层次的证据分别记录。

| 层次 | 位置 / 命令（仓库根目录） | 能证明的内容 |
| --- | --- | --- |
| Java 隔离单元测试 | `backend-procedural/src/test/`；`mvn -f backend-procedural/pom.xml test` | Service/Controller 的分支、边界与协作契约；使用 mock，不连接真实数据库 |
| Java 整合测试 | 同目录 `Stage1ApiTest`；上述 Maven 命令一并执行 | MockMvc执行实际MVC/Controller、H2、会话、鉴权、事务回滚和并发场景；不启动网络服务器 |
| 导入单元测试 | `python -m unittest discover -s tests/unit -p "test_*.py"` | 合成输入解析、稳定编号、清洗、角色与范围、拒绝/失败规则 |
| 前端单元测试 | `node --test tests/frontend/*.test.cjs` | 纯策略、mock、范围和路由；不证明真实后端安全性 |
| 契约静态检查 | `python scripts/check_openapi.py` | OpenAPI 语法、引用、操作声明 |
| 运行接口集合 | `python scripts/check_openapi.py --base-url http://127.0.0.1` | 方法与路径集合一致；不验证全部字段/状态/语义 |
| 真实 HTTP | `python scripts/smoke_http.py --base-url http://127.0.0.1 --env-file deploy/.env --check-web` | 运行实例 Cookie、授权、范围、冲突、静态入口；需要专用合成环境 |
| MySQL / 重启 / 全量 | [一期自测](../docs/stage1/self-test.md) | 正式方言、持久化、三份原始表及真实规模 |

## 现场运行后端测试

```powershell
mvn -f backend-procedural/pom.xml clean verify
```

该命令重新编译并执行全部后端测试，避免展示陈旧的 `target/` 报告。Surefire XML/TXT 在 `backend-procedural/target/surefire-reports/`；JaCoCo HTML 在 `backend-procedural/target/site/jacoco/index.html`，机器可读数据为同目录 `jacoco.xml`。JUnit 单测与整合测试都由 Surefire 执行，不把 `mvn test` 这个命令名等同于“全部都是隔离单元测试”。

报告至少说明行、分支、方法和类覆盖率及未覆盖模块。覆盖率反映代码被执行的程度；边界断言、数据库方言、事务隔离、真实部署和课程要求需要另外评估。未设置全局“达到某百分比就算验收通过”的门槛。

隔离测试不证明真实 SQL 锁、事务回滚或并发原子性；H2 整合测试不证明 MySQL 的全部行为；mock 前端通过不证明服务端通过。对应需求、测试分工及当前实测值见 [需求追溯](../docs/stage1/requirement-coverage.md)、[结构化设计](../docs/design/structured-design.md)和 [验证记录](../docs/stage1/verification-record.md)。

`contract/`、`e2e/` 等空目录是后续预留，不代表其中已有测试。测试和报告只使用合成数据，生成日志及覆盖率文件不提交为源码；正式检查报告记录版本、执行时间、环境、命令和结果摘要。

## 只读SQL测量工具测试

`pwsh -NoProfile -File tests/scripts/measure_full_db_perf.test.ps1`执行8组隔离测试，使用假客户端子进程检查只读约束、数值/样本边界、解析、去敏和超时；不连接MySQL。GitHub自动检查已加入该命令，Gitee可按同一命令复现。
