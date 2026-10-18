# 第一期服务器部署与自测

部署包提供 MySQL 8.4、JDK 21 Spring Boot 后端，以及 Nginx 托管的完整 Vue 界面原型和 API 设计。只把 Nginx 的 HTTP 端口发布到服务器；数据库和后端端口留在 Compose 网络中。当前界面原型使用模拟接口，真实后端通过 Swagger 和 HTTP 脚本验收。

本目录是可执行的部署配置，**不表示已上传或已在某台服务器运行**。部署完成还需保存服务器入口、源码提交号和 HTTP 验收输出。

## Linux 服务器一键启动

前提：已有可使用的 Linux 服务器，Docker Engine、Compose 插件 2.20 或更新版本、Git，能够拉取 Maven/npm 依赖及官方容器镜像。建议至少 2 核、4 GB 内存、10 GB 可用磁盘用于构建。HTTP 默认占用 80 端口，已有站点时在 `.env` 选择空闲端口。

在服务器的项目目录执行：

```sh
cp deploy/.env.example deploy/.env
chmod 600 deploy/.env
# 用服务器上的编辑器填写下面三个不同密码，再启动。
editor deploy/.env
sh scripts/deploy-stage1.sh
```

必须填写 `MYSQL_PASSWORD`、`MYSQL_ROOT_PASSWORD`、`RBAC_INITIAL_PASSWORD`。用不同的随机字符串；建议十六进制，避免 `.env` 插值字符。后端初始密码至少 8 位。`MYSQL_USER=rbac` 是普通应用用户；MySQL root 密码不交给后端。

课程演示使用 `RBAC_DEMO_ENABLED=true`，仅在空数据库生成明确标注的合成数据。种子账号 `admin`、`SG000013`（普通员工）、`DEMO000100`（部门管理员）的密码来自 `RBAC_INITIAL_PASSWORD`；`DEMO000101` 用于首次改密检查。浏览器原型的演示密码仍按界面说明使用，它不读取服务器密码。

真实数据环境将 `RBAC_DEMO_ENABLED=false`，按数据库与导入说明准备受控种子；不把课程原始员工表放进公开仓库或镜像。MySQL 数据卷已有数据时，初始化脚本及合成种子都不会覆盖它。改变 `.env` 中初始密码也不会重置已有账号。

## 检查入口

把 `SERVER_IP` 替换为服务器实际 IP；若 `HTTP_PORT` 不是 80，URL 中加上端口。

| 路径 | 用途 |
| --- | --- |
| `http://SERVER_IP/stage1/` | 第一期统一检查入口 |
| `http://SERVER_IP/` | 完整界面原型，内存模拟数据 |
| `http://SERVER_IP/swagger-ui/index.html` | 真实后端在线 API 调试 |
| `http://SERVER_IP/api-design/openapi.yaml` | API 设计原文件 |
| `http://SERVER_IP/api/v1/health` 或 `/health` | 后端及数据库就绪检查 |

服务器防火墙和云安全组只需放行所选 HTTP 端口；SSH 保留已有管理规则。检查仅使用合成数据时可以先用文档中的 HTTP 入口。公开 HTTPS 可在已有入口代理终止 TLS，并设置 `SESSION_COOKIE_SECURE=true`；要同步配置可信转发头，再通过 HTTPS 自测。不要在 HTTP 上开启 Secure Cookie，否则浏览器不会回传登录会话。

Swagger 同域请求会使用 `JSESSIONID` Cookie。先对 `POST /api/v1/auth/login` 执行 Try it out，然后调用受保护接口，无需把密码填入其他接口。

## 检查前自动自测

以下命令在已启用合成种子的部署环境执行，需要 Python 3.11 或更新版本，不需要第三方 Python 库。脚本从本地 `.env` 读取初始密码，不在日志打印密码或 Cookie。

```sh
python3 scripts/smoke_http.py --base-url http://127.0.0.1 --env-file deploy/.env --check-web
```

预期最后输出 `HTTP acceptance checks passed.`，进程退出码为 0。检查覆盖：真实 HTTP 登录及 Cookie、未登录 401、无权限 403、SELF 与 DEPT（含子部门）范围、直接按 ID 越权、唯一管理员保护、新账号首次改密、用户角色和角色授权版本冲突 409、无效授权回滚、停用后旧会话失效、退出，以及完整原型/API设计/Swagger 入口。脚本只新增和删除它创建的合成临时账号、角色，不修改已有员工或业务角色。

数据库持久化要另外实测，按顺序执行：

```sh
python3 scripts/smoke_http.py --base-url http://127.0.0.1 --env-file deploy/.env --mode persistence-create
docker compose --env-file deploy/.env -f deploy/compose.yaml down
docker compose --env-file deploy/.env -f deploy/compose.yaml up -d --wait --wait-timeout 300
python3 scripts/smoke_http.py --base-url http://127.0.0.1 --env-file deploy/.env --mode persistence-verify
python3 scripts/smoke_http.py --base-url http://127.0.0.1 --env-file deploy/.env --mode persistence-cleanup
```

`down` 保留命名数据卷；**不要加 `--volumes` 或 `-v`**。标记文件默认写到被 Git 忽略的 `data/raw/stage1-persistence.json`，只包含合成账号 ID 和工号。验收成功后用 `persistence-cleanup` 清除标记。重复执行前先处理已有标记，避免遗漏临时账号。

## 常用运维命令

```sh
docker compose --env-file deploy/.env -f deploy/compose.yaml ps
docker compose --env-file deploy/.env -f deploy/compose.yaml logs --tail=100 backend
docker compose --env-file deploy/.env -f deploy/compose.yaml logs --tail=100 web
docker compose --env-file deploy/.env -f deploy/compose.yaml restart backend
docker compose --env-file deploy/.env -f deploy/compose.yaml stop
# 更新代码后重建；继续沿用 MySQL 数据卷。
sh scripts/deploy-stage1.sh
```

Nginx 通过 Docker DNS 每 5 秒重新解析后端服务名。仅重建后端后，可执行 `python3 scripts/smoke_http.py --base-url http://127.0.0.1 --env-file deploy/.env --wait-ready 30 --check-web`，验证后端地址更新后同源入口仍可用。

首次空数据卷启动时，MySQL 执行权威迁移 `database/migrations/V001__create_core_tables.sql`。后端的 `mysql` profile 关闭自动重建表。后续数据库结构变更须新增版本迁移并评审，不能靠删除数据卷升级。

备份存到仓库外的受控目录，例如 `/srv/rbac-backups`。以下命令用应用数据库账号导出，不在命令行直接写密码：

```sh
set -o pipefail
mkdir -p /srv/rbac-backups
chmod 700 /srv/rbac-backups
docker compose --env-file deploy/.env -f deploy/compose.yaml exec -T db sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump -u"$MYSQL_USER" --single-transaction --no-tablespaces --set-gtid-purged=OFF "$MYSQL_DATABASE"' | gzip > /srv/rbac-backups/rbac-before-check.sql.gz
chmod 600 /srv/rbac-backups/rbac-before-check.sql.gz
gzip -t /srv/rbac-backups/rbac-before-check.sql.gz
```

恢复应先在独立的空测试数据库验证备份，再在受控维护窗口恢复目标库。不得把含员工信息和密码哈希的备份上传公开 GitHub。验收记录至少保存提交号、访问入口、Compose 容器状态、自测输出、持久化验证结果和备份时间。

## CI 范围和本机限制

GitHub Actions 分别执行仓库检查、Python 导入测试、全部前端测试及构建、JDK 21 后端测试；另在 GitHub 的 Linux runner 上构建 Compose 栈，执行 MySQL 真实 HTTP 和重建后持久化检查。CI 的临时数据卷可在该任务结束后删除，服务器数据卷不得照搬删除命令。

当前 Windows 工作机没有 Docker，不能以本机源码或配置检查代替容器运行结果。最终以服务器及 CI 实际执行成功记录为准；失败时保留日志定位，不把“配置已写好”写成“部署已完成”。
