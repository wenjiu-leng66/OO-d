# 一期测试报告 · 2026-10-27

## 本轮范围和版本

基于GitHub `af1aa788`，整合遗漏成员贡献并修订设计与证据口径。本轮实际运行修复为策略同时写入CommonJS和全局导出，以及Compose允许来源配置透传；API、业务权限规则和数据库表结构未改变。

- 运行环境：Windows、JBR21.0.10、Maven、Node24.18.0、Python3.12.14。
- 后端命令：`mvn -f backend-procedural/pom.xml clean verify`，2026-10-27 19:41（Asia/Shanghai）完成，BUILD SUCCESS。
- 后端测试数据库：独立内存H2；本轮没有操作检查服务器、原始Excel或真实业务数据库。
- 被测后端源码/测试/脚本/POM指纹：`ad5b76f70a344301ca309b67a004fa759f1b143acc3e26b2d129fe9f8c2504ea`。相对路径排序，内容LF归一化，逐文件SHA256后再SHA256；不计`.gitkeep`和生成目录。

## 后端结果

**90项通过，0失败、0错误、0跳过**：61项隔离单元测试和29项MockMvc/H2整合测试。此次增加8项Origin/CORS边界测试，原有单元和整合覆盖继续执行。

| 测试类 | 层次 | 项数 | 失败/错误 |
| --- | --- | --- | --- |
| BrowserSecurityConfigTest | 隔离单元 | 8 | 0 |
| AuthControllerTest | 隔离单元 | 2 | 0 |
| HealthControllerTest | 隔离单元 | 3 | 0 |
| DataInitializerTest | 隔离单元 | 4 | 0 |
| DepartmentMapperTest | 隔离单元 | 3 | 0 |
| IdsTest | 隔离单元 | 2 | 0 |
| AuthorizationServiceTest | 隔离单元 | 12 | 0 |
| AuthServiceTest | 隔离单元 | 7 | 0 |
| RoleServiceTest | 隔离单元 | 9 | 0 |
| UserServiceTest | 隔离单元 | 11 | 0 |
| Stage1ApiTest | MockMvc / H2 整合 | 29 | 0 |

来源相关测试覆盖同源允许、空白名单拒绝跨域、精确可信来源携带凭据、协议/端口/子域/null来源拒绝、预检及错误包络。无Origin的既有行为保留；来源过滤不是身份认证或完整CSRF令牌方案。

隔离测试验证单层规则及协作；MockMvc执行真实MVC/JDBC/H2但不启动公网服务器。事务、并发和数据库方言还需区分H2、MySQL及实际部署，不能由mock单独证明。

## JaCoCo覆盖率

来源为上述完整clean verify生成的`backend-procedural/target/site/jacoco/jacoco.xml`，未排除低覆盖类。

| 指标 | 已覆盖 | 未覆盖 | 总计 | 覆盖率 |
| --- | --- | --- | --- | --- |
| 行 | 998 | 100 | 1098 | 90.89% |
| 分支 | 360 | 118 | 478 | 75.31% |
| 方法 | 245 | 29 | 274 | 89.42% |
| 类 | 49 | 0 | 49 | 100.00% |
| 指令 | 6293 | 774 | 7067 | 89.05% |
| 复杂度 | 407 | 114 | 521 | 78.12% |

| 包 | 行覆盖率 | 分支覆盖率 |
| --- | --- | --- |
| procedural | 33.33% | — |
| config | 97.14% | 78.57% |
| controller | 92.86% | 100.00% |
| data | 100.00% | 94.23% |
| dto | 100.00% | — |
| error | 58.70% | 25.00% |
| mapper | 99.41% | 92.86% |
| po | 100.00% | — |
| service | 88.29% | 69.39% |
| vo | 100.00% | 100.00% |

整体行90.89%、分支75.31%，仍有服务层及异常处理分支未覆盖。类100%仅代表每个类有代码执行，不代表每条路径或所有部署风险通过。完整HTML在`backend-procedural/target/site/jacoco/index.html`，逐用例结果在`target/surefire-reports/`。

## 原型与其他检查

| 检查 | 结果 | 证据边界 |
| --- | --- | --- |
| 前端测试 | 60/60通过 | 新增CJS+global一致、实际ESM适配器导入回归，不再由测试手动注入全局掩盖缺陷 |
| 生产构建 | 通过 | Vite构建/API资源复制；仍有主JS包超过500kB提示 |
| 生产原型浏览器 | 本机4178成功打开登录、管理员登录、门户及角色授权页，错误级控制台记录为空 | 是本机生产构建和内存mock，不是检查服务器或真实API登录证明 |
| 导入及SQL测量工具 | 结果见当前验证记录 | 合成输入与假客户端验证，不操作真实数据库或原始Excel |
| 设计图 | 22个权威Mermaid图块通过10.9.8语法解析 | 另已按源码人工复核；托管平台实际渲染支持不由语法解析保证 |
| API | 契约未修改，25路径/33操作 | 运行实例检查与现场字段/权限验证仍分开 |

## 历史证据及剩余验收

成员1的原表全量/重复导入、SQL测量，成员4的部署/重启，成员5的旧版本后端复核已按来源归档。它们不是本轮重跑结果；SQL语句耗时不等于HTTP或真实COMMIT耗时。

现场仍需核对新版本部署SHA/镜像、登录与越权场景，并执行相应MySQL、原始数据和端到端性能复验。详细采纳决定见[整合记录](../stage1/contribution-review-2026-10-27.md)，当前状态见[验证记录](../stage1/verification-record.md)。
