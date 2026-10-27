# 成员贡献评审与整合记录 · 2026-10-27

本次在GitHub自查版`af1aa788`和Gitee正式版`b51d2e65`基础上审查遗漏贡献，按当前实现修订后同步。原分支及作者历史保留，正式文档只维护一套权威字段和规则。

| 来源 | 决定 | 处理与理由 |
| --- | --- | --- |
| `cadbcd6`，策略全局导出 | 采纳 | CommonJS/Vite路径原本仅写module.exports，实际适配器读globalThis可能失败；改为两种导出同时成立，新增真实适配器导入回归 |
| `a71ef2e`，部署来源配置 | 修订后采纳 | Compose透传已有允许来源；示例默认空、同源可用，跨域仅配置精确可信来源，避免把某个部署地址固化为默认 |
| PR#19，成员1全量导入 | 采纳历史证据 | 保留日期、被测版本及3555=3549+6口径；未重取原表/复算，不用旧29项测试覆盖当前报告 |
| PR#21，SQL性能 | 修订后采纳 | 保留SQL测量数据和工具；删除把ROLLBACK等同COMMIT、SQL时间等同HTTP保存时间的结论；工具安全约束及测量范围明确化 |
| PR#22，成员5设计图 | 修订后采纳 | 修正真实接口、Service调用层次、独立账号状态/改密标记、初始角色权限和范围；维护Markdown模型图集 |
| PR#22，10份AsciiDoc副本 | 不作为第二套文档发布 | 多份是Markdown重复或旧快照，长篇另含不存在的客户端/凭据版本/审计承诺；准确的数据库设计内容提炼进规范Markdown |
| `f989fc3`，成员5后端复核 | 按历史记录吸收 | GitHub保留日期和原版本，修正额外授角前提；当前测试报告为现行证据，Gitee保留正式汇总 |
| 成员4部署/自测分支（含junmoxiao517两提交） | 提炼后采纳 | 服务器自测为有来源的历史证据；不复制口令及重复566行旧教程，操作与口试要点并入当前入口 |

## 主要纠正

- 设计图使用实际HTTP方法、权限码和Controller→Service→Authorization/Mapper调用；台账响应不虚构canEdit字段。
- ENABLED/DISABLED与mustChangePassword是独立状态维度；首次改密不是第三种互斥账号状态。
- 公司领导的只读管理权、部门管理员的角色读取、经理台账read=ALL/edit=DEPT按实际种子与服务规则描述。
- MySQL语句计时、SQL写入回放、Java服务/HTTP响应、真实COMMIT、正式压测分别留证；不互相替代。
- 旧部署/全量记录归入历史证据，当前版本的现场签收、部署SHA和新覆盖率另记。

## 文档入口

新设计见[模型图集](../uml/README.md)、[结构化设计](../design/structured-design.md)、[数据库设计](../database/database-design.md)。历史数据与部署证据见[全量与SQL测量](../testing/full-data-verification-2026-10-18.md)、[服务器记录](../testing/server-verification-2026-10-21.md)。当前实际测试和未完成项见[验证记录](verification-record.md)。

GitHub保留内部讲解与追溯，Gitee仅同步修订后的必要模型、正式记录、工具及相同运行代码。此次同步不表示已更新服务器上的应用。
