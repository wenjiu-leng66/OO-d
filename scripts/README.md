# 仓库检查

在根目录执行 `python scripts/check_repository.py`。脚本仅用 Python 标准库及 Git。

覆盖结构所需文件、UTF-8、本地 Markdown 文件链接、残留冲突标记、明确不应提交的文件和合成样例关联。未检查远程链接可访问性、Markdown 章节锚点、所有秘密形式或应用业务行为。

检查通过不取代人工检查暂存差异，不能证明不存在所有敏感信息。

一期新增：

- `sh scripts/deploy-stage1.sh`：读取服务器 `deploy/.env`，构建并等待 MySQL、后端、Nginx 三个服务就绪。
- `python scripts/smoke_http.py --base-url http://127.0.0.1 --env-file deploy/.env --check-web`：通过真实 HTTP Cookie 验证授权、数据范围、改密、版本冲突、旧会话及页面入口，进程退出码非 0 表示失败。
- `smoke_http.py --mode persistence-create|persistence-verify|persistence-cleanup`：创建合成账号标记，配合重建容器验证 MySQL 命名卷持久化。

- `pwsh -File scripts/measure_full_db_perf.ps1 -LoginPath rbac-verify`：在本机 `rbac_verify_full` 中测量只读 SQL/CLI 成本；不执行授权写入、不测 COMMIT 或 HTTP，不输出达标判决。

## 只读数据库测量

要求 PowerShell 7+、MySQL 8.4 CLI（PATH 中的 `mysql` 或 `-MySql` 指定路径），支持 Windows/Linux。先由环境负责人配置只读账号及本机 `mysql_config_editor` login-path；脚本不创建凭据，也不要求在命令行传密码。仅允许本机地址及固定核对库，不连接公开服务器。

使用 `--no-defaults` 排除普通配置，再读取指定 login-path；每批查询置于只读事务，具有查询与子进程期限。结果只保存计数、代理ID、计时和计划；查询资料在内存中丢弃。`-OutFile`应为不存在的 `.json` 路径，默认生成临时文件；不会覆盖旧证据。参数见 `Get-Help ./scripts/measure_full_db_perf.ps1 -Detailed`。

```powershell
pwsh -NoProfile -File tests/scripts/measure_full_db_perf.test.ps1
pwsh -File scripts/measure_full_db_perf.ps1 -LoginPath rbac-verify -Runs 5 -OutFile data/raw/read-perf.json
```

第一条只使用假子进程，不连接数据库；第二条须在受控数据副本和只读账号准备好后执行。新版工具移除了旧版回滚式授权写入；[2026-10-18 历史性能记录](../docs/testing/full-data-verification-2026-10-18.md)不代表新版已经实测。

HTTP参数见 `python scripts/smoke_http.py --help`；部署顺序见[部署说明](../deploy/README.md)。`check_openapi.py`需要`requirements-validation.txt`中依赖；其余Python脚本使用标准库。
