# 项目文档入口

| 类别 | 入口 |
| --- | --- |
| 一期交付与实测 | [检查入口](stage1/README.md)、[自测步骤](stage1/self-test.md)、[41条覆盖](stage1/requirement-coverage.md)、[实际验证记录](stage1/verification-record.md)、[合并来源](stage1/merge-sources.md) |
| 当前测试报告 | [2026-10-27测试与覆盖率](testing/test-report-2026-10-27.md) |
| 代码与口试 | [代码阅读路线](stage1/code-reading-guide.md)、[结构化设计](design/structured-design.md)、[边界清单](stage1/review-checklist.md)、[口试对照](stage1/oral-review.md) |
| 六人 GitHub 操作 | [使用手册](guides/github-team-manual.md) |
| 需求 | [需求规格说明书](requirements/req-spec.md)、[需求访谈记录](requirements/grill-record.md)、[范围](requirements/scope.md)、[用例模板](requirements/use-cases.md)、[待确认问题](requirements/questions.md) |
| 设计 | [结构化设计](design/structured-design.md)、[后端详细设计（AsciiDoc）](design/detailed-design.adoc) |
| UML | [模型图集](uml/README.md)、[数据库设计（AsciiDoc）](database/database-design.adoc) |
| 数据库 | [数据字典](database/data-dictionary.md)、[导入质量规则](database/import-rules.md) |
| 界面设计 | [第一阶段界面原型](design/stage1-ui-prototype.md)、[成员 2 前端约定](design/member2-frontend-contract.md)、[成员 3 RBAC0 设计](design/member3-rbac0-design.md) |
| 技术决定 | [单仓库与双后端](decisions/0001-monorepo.md) |
| 分工与排期 | [成员](planning/team.md)、[阶段](planning/milestones.md)、[首批任务](planning/first-tasks.md) |
| 协作约定 | [API](standards/api-contract.md)、[环境](standards/environment.md) |
| 会议 | [纪要模板](meetings/meeting-template.md) |
| 质量与演示 | [验收矩阵](testing/acceptance-matrix.md)、[成员 6 RBAC0 API 测试草案](testing/member6-rbac0-api-test-cases.md)、[演示准备](demo/README.md) |

历史草案保留作者贡献；当前接口以OpenAPI和接口约定为准，当前运行与验收状态以一期验证记录为准。模板和旧分支的通过记录不替代当前版本实测。

## 阅读优先级

需求以`requirements/req-spec.md`为范围依据；当前HTTP路径、字段、状态和权限以`api/openapi.yaml`及`standards/api-contract.md`为契约依据；实现以当前main代码为准，差异需要登记并修正；执行状态以对应源码版本的`stage1/verification-record.md`为准。

`guides/`、`demo/`、`api/proposals/`、成员命名文档、早期任务和截图用于内部追溯。它们不是另一份当前标准，历史通过结果也不能替代当前版本测试。当前前端是Vue内存原型，不能从早期草案中的“待后端实现”推断真实后端仍为空。

本次遗漏贡献的采纳、修订和未原样发布的内容见[成员整合记录](stage1/contribution-review-2026-10-27.md)。
