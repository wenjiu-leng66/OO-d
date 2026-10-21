# 成员4一期服务器部署与自测记录

日期：2026-10-21。责任范围：王信凯（成员4）牵头的一期服务器部署与后端启动。本次由成员4本人在华为云 Flexus 应用服务器 L 实例上执行，记录只包含合成验收数据，不含真实员工信息、密码或 Cookie。

## 版本与环境

| 项目 | 实际值 |
| --- | --- |
| 被测源码 SHA | `e19b12767ebdd2e6a60aae37e9527299c5490fe5`（main） |
| 服务器 | 华为云 Flexus 应用服务器 L 实例，华北-北京四 |
| 操作系统 | Ubuntu 22.04.5 LTS |
| 规格 | 2 vCPU / 4 GiB / 50 GiB 系统盘；4 GiB swap |
| 公网入口 | `http://<服务器公网IP>:8080`（HTTP，未配置域名和 HTTPS） |
| 容器运行时 | Docker 29.9.2；Docker Compose v5.6.1 |
| 数据模式 | 合成验收数据，`RBAC_DEMO_ENABLED=true` |
| 数据库迁移 | `V001__create_core_tables.sql` |
| 配置位置 | `/srv/RBAC-Permission-System/deploy/.env`，权限 600，未被 Git 跟踪 |

## 部署步骤

在服务器仓库根目录执行：

```sh
cp deploy/.env.example deploy/.env
chmod 600 deploy/.env
# 填写密码（首次部署为三个不同的随机密码；2026-10-21 起演示环境统一为同一密码）；HTTP_PORT=8080；RBAC_DEMO_ENABLED=true
sh scripts/deploy-stage1.sh
```

首次部署时为降低 3 Mbps 带宽下的并发下载压力，先顺序预拉了 `mysql:8.4`、`maven:3.9.11-eclipse-temurin-21`、`eclipse-temurin:21-jre-jammy`、`node:22-alpine`、`nginx:stable-alpine` 五个镜像。Docker Hub 直连不可达，使用可达的公共镜像源完成拉取；该设置只影响服务器拉取方式，不改变应用代码。

2026-10-21 按演示便利要求，将 SSH、MySQL 应用账号/root 与 `RBAC_INITIAL_PASSWORD` 统一为同一演示密码，并重置全部预置账号的密码哈希；调整后重新执行冒烟测试，27 项通过、0 失败，三个容器保持 healthy。该统一密码只用于课程合成演示，接入真实数据前必须更换。

## 自动检查实际结果

| 检查 | 命令 | 实际结果 |
| --- | --- | --- |
| 容器状态 | `docker compose --env-file deploy/.env -f deploy/compose.yaml ps` | `db`、`backend`、`web` 均为 healthy；`web` 发布 `0.0.0.0:8080->80/tcp` |
| 健康检查 | `curl -s http://127.0.0.1:8080/api/v1/health` | `{"status":"UP"}` |
| 外网健康检查 | 成员4本机访问 `http://<服务器公网IP>:8080/api/v1/health` | HTTP 200，`{"status":"UP"}` |
| 统一入口 | `/stage1/`、`/`、`/api-design/openapi.yaml`、`/swagger-ui/index.html`、`/api/v1/health` | 均为 HTTP 200 |
| HTTP 验收 | `python3 scripts/smoke_http.py --base-url http://127.0.0.1:8080 --env-file deploy/.env --check-web` | 29 项 PASS，最后输出 `HTTP acceptance checks passed.`，退出码 0 |
| OpenAPI | `python scripts/check_openapi.py --base-url http://127.0.0.1:8080` | `OpenAPI valid: 25 paths, 33 operations`；`Live backend methods and paths exactly match the API design`；退出码 0 |
| 重启持久化 | `persistence-create` → `compose down` → `compose up -d --wait` → `persistence-verify` → `persistence-cleanup` | 重启后临时合成账号仍在；标记已清理；两段检查均 `HTTP acceptance checks passed.`，退出码 0 |

HTTP 验收覆盖：健康与公开入口、未登录 401、错误密码 401、管理员登录 Cookie、普通员工管理接口 403、SELF 与 DEPT 范围、跨部门 ID 访问 403、唯一管理员保护、首次改密、角色分配版本递增、旧版本 409、非法授权回滚、停用后旧会话失效、临时账号与角色清理、退出失效、五个 Web/API 入口。

## 访问入口

| 地址 | 用途 |
| --- | --- |
| `http://<服务器公网IP>:8080/stage1/` | 一期统一检查入口 |
| `http://<服务器公网IP>:8080/` | 完整界面原型（内存模拟数据） |
| `http://<服务器公网IP>:8080/swagger-ui/index.html?url=/api-design/openapi.yaml` | 统一 API 文档与真实接口调试 |
| `http://<服务器公网IP>:8080/api-design/openapi.yaml` | API 设计原文件 |
| `http://<服务器公网IP>:8080/api/v1/health` | 后端与数据库就绪检查 |

服务器安全组仅放行 TCP 22 与 TCP 8080，未开放 MySQL 3306 和后端内部 8080 之外的端口。

## 启动、停止与重启

```sh
docker compose --env-file deploy/.env -f deploy/compose.yaml ps
docker compose --env-file deploy/.env -f deploy/compose.yaml logs --tail=100 backend
docker compose --env-file deploy/.env -f deploy/compose.yaml restart backend
docker compose --env-file deploy/.env -f deploy/compose.yaml stop
```

重启验证使用 `down` 后再 `up`，保留命名数据卷；**未执行** `down -v` 或 `down --volumes`。

## 交接给成员6

- 源码 SHA：`e19b12767ebdd2e6a60aae37e9527299c5490fe5`
- 服务器：华为云 Flexus L，Ubuntu 22.04.5，2C4G
- 地址与端口：`http://<服务器公网IP>:8080`
- 容器状态：`db`、`backend`、`web` 均 healthy
- 健康检查：`{"status":"UP"}`
- HTTP 验收：29 项 PASS，退出码 0
- 持久化：重启后账号保留，已验证并清理
- 配置位置：`/srv/RBAC-Permission-System/deploy/.env`（不提供内容）
- 数据模式：合成验收；未导入原始课程 Excel

## 待执行与已知限制

- 成员6从另一台电脑通过公网地址独立复验，尚未执行。
- 原始课程 Excel 全量导入与核对（111 组织 / 10000 员工 / 2154 权限等）由成员1负责，尚未执行。
- 全量数据下的鉴权与列表性能测量尚未执行。
- 未配置域名与 HTTPS；当前为 HTTP 演示，`SESSION_COOKIE_SECURE=false`。
- 本记录只证明上述服务器自测结果，不替代成员6的独立验收。
