# 一期模型与设计图

本图集在成员5（胡俊朗）提交的 [PR #22](https://github.com/wenjiu-leng66/RBAC-Permission-System/pull/22)、源码 `73cd6bea7d0b0f7bb56fd4b3319df002fc3221d2` 基础上，按当前 JDBC 后端、API 契约和独立原型边界校订。图用于解释已有实现，不代表二期面向对象后端已经完成。

| 视图 | 内容 | 对应位置 |
| --- | --- | --- |
| [业务概念与关系](domain-model.md) | 用户、组织、角色、权限和数据范围 | USER / DEPT / ROLE / CAT / GRANT |
| [模块依赖与职责](class-responsibility.md) | Controller、Service、授权、Mapper 的实际协作 | 后端函数分解及代码定位 |
| [关键序列](sequence-diagrams.md) | 登录改密、用户授角、台账、角色授权、即时撤权、删除 | LOGIN / USER / ROLE / GRANT / AUTHZ |
| [状态模型](state-diagrams.md) | 账号启停、改密标记、角色启停分别建模 | LOGIN-05 / USER-04～07 / ROLE-02～03 |
| [用例与初始角色](use-cases.md) | 用例范围、预置授权和例外 | 需求范围及允许/拒绝场景 |
| [决策活动](activity-diagrams.md) | 对象范围检查、角色删除的拒绝分支 | AUTHZ / ROLE-03 |

结构化分析的上下文、一层 DFD、功能结构及模块契约见[结构化设计](../design/structured-design.md)，数据表与约束见[数据库设计](../database/database-design.md)及[数据字典](../database/data-dictionary.md)。DFD说明数据流，模块图说明职责，序列图说明调用顺序，状态图说明合法迁移，不能互相替代。

权威关系：功能范围以[需求规格](../requirements/req-spec.md)为准，接口字段/状态以[OpenAPI](../../api/openapi.yaml)为准，物理结构以[V001](../../database/migrations/V001__create_core_tables.sql)为准，实际执行结果另见[验证记录](../stage1/verification-record.md)。图与这些内容不一致时同步修正。

统一维护 Markdown 中的 Mermaid 源码，不再保留同内容的 AsciiDoc 副本。图能否在具体托管平台显示取决于其 Mermaid 支持；源码可独立交给兼容渲染器查看。
