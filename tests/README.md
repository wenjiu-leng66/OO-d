# 测试目录

当前没有业务测试。预留 unit、contract、integration、e2e、fixtures 目录。

- unit：核心授权与纯计算等单元测试。成员 1 的 Excel 导入脚本测试：`python tests/unit/test_import_excel.py`，只需 Python 3 标准库和合成数据，不需要原始 Excel 或数据库。
- contract：同一外部契约对两种后端的检查。
- integration：数据库、事务、真实身份与 API 链路。
- e2e：浏览器中的完整用户操作。
- fixtures：合成测试数据与初始化说明。

后端语言内的单元测试也可放各自 `src/test`；此处保留跨实现与跨组件资料。新增测试时写清运行方式和对环境的依赖。

验证目标见 [验收矩阵](../docs/testing/acceptance-matrix.md)。
