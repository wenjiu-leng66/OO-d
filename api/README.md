# 第一阶段 API 契约

[openapi.yaml](openapi.yaml) 是统一接口文件，版本 `0.3.0`，覆盖认证/首次改密、当前权限、门户、员工台账、用户、部门、角色、目录、两种授权整组替换，以及健康检查。面向功能后端位于 `backend-procedural/`；两后端共用契约是第二阶段目标。

通用约定、`rbac:*` 映射、错误码、Cookie、`ALL/DEPT/SELF`、首次改密限制和版本含义见 [接口约定](../docs/standards/api-contract.md)。登录请求为 `{account,password}`，`account`接受工号或手机号；用户资料中的工号字段为`username`，不存在账号类别。角色授权使用 `grants:[{code,scope}]`，不能直接授予派生 `rbac:*`。

部署后可打开 `/api-design/openapi.yaml` 下载权威文件；`/swagger-ui/index.html?url=/api-design/openapi.yaml` 与 `/v3/api-docs` 便于查看实际控制器入口，自动生成文档不能替代此处的字段、权限和错误规则。需导入工具调用时使用同源 Cookie 会话。

本版吸收成员 6 草案、成员 1 需求/差异评审与成员 4、5 后端实现；[合并来源](../docs/stage1/merge-sources.md) 保留版本。实际契约校验及 HTTP 执行结果见 [验证记录](../docs/stage1/verification-record.md)，不以“设计完成”推断“已测试通过”。

修改接口时同步契约、实现、客户端与验收用例；历史建议保留在 `proposals/`，不另立一份相互矛盾的标准。
