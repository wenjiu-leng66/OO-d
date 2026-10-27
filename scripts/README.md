# 仓库检查

在根目录执行 `python scripts/check_repository.py`。脚本仅用 Python 标准库及 Git。

覆盖结构所需文件、UTF-8、本地 Markdown 文件链接、残留冲突标记、明确不应提交的文件和合成样例关联。未检查远程链接可访问性、Markdown 章节锚点、所有秘密形式或应用业务行为。

检查通过不取代人工检查暂存差异，不能证明不存在所有敏感信息。

一期新增：

- `sh scripts/deploy-stage1.sh`：读取服务器 `deploy/.env`，构建并等待 MySQL、后端、Nginx 三个服务就绪。
- `python scripts/smoke_http.py --base-url http://127.0.0.1 --env-file deploy/.env --check-web`：通过真实 HTTP Cookie 验证授权、数据范围、改密、版本冲突、旧会话及页面入口，进程退出码非 0 表示失败。
- `smoke_http.py --mode persistence-create|persistence-verify|persistence-cleanup`：创建合成账号标记，配合重建容器验证 MySQL 命名卷持久化。

- `pwsh -File scripts/measure_full_db_perf.ps1`：在本机全量核对库 `rbac_verify_full` 按后端实际 SQL 测鉴权、列表、台账和授权保存（事务内执行后回滚）的数据库端耗时；终端里输入 MySQL 密码，结果 JSON 只含 id、计数和耗时。

完整参数见 `python scripts/smoke_http.py --help`；执行顺序与预期结果见 [部署与自测](../deploy/README.md)。Python 脚本仅使用标准库，不需安装 requirements。
