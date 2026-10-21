# 一期服务器自测说明

适用对象：成员 2、3、5、6 及组长。用于在成员 4 部署的一期合成验收环境上做页面复查、接口复查和独立验收。

服务器地址、端口和演示密码通过组内安全渠道向成员 4 获取，**不写入仓库、不贴在公开 Issue 或群消息**。本文用 `<服务器IP>` 和 `<端口>` 占位。

## 1. 环境边界

| 项目 | 说明 |
| --- | --- |
| 源码版本 | 部署时由成员 4 提供完整 SHA，测试前先与组长确认 |
| 访问方式 | HTTP，未配置域名和 HTTPS |
| 端口 | 当前为 `8080` |
| 数据 | 合成验收数据，`RBAC_DEMO_ENABLED=true` |
| 数据库 | MySQL 8.4，数据卷持久化 |
| 界面原型 | 使用内存模拟数据，不代表真实后端 |
| 真实后端 | `/api/v1/**`、Swagger、HTTP 脚本 |

不要把原型页面的模拟结果当作真实接口、数据库事务或权限校验已通过的证据。

## 2. 访问入口

| 地址 | 用途 |
| --- | --- |
| `http://<服务器IP>:<端口>/stage1/` | 一期统一检查入口 |
| `http://<服务器IP>:<端口>/` | 完整界面原型（模拟数据） |
| `http://<服务器IP>:<端口>/swagger-ui/index.html?url=/api-design/openapi.yaml` | 统一 API 文档与真实接口调试 |
| `http://<服务器IP>:<端口>/api-design/openapi.yaml` | API 设计原文件 |
| `http://<服务器IP>:<端口>/api/v1/health` | 后端与数据库就绪检查，返回 `{"status":"UP"}` |

## 3. 演示账号

| 账号 | 身份 | 用途 |
| --- | --- | --- |
| `admin` | 超级管理员 | 管理接口、授权、角色维护 |
| `SG000001` | 公司领导 | 只读管理页面、越权拒绝 |
| `SG000011` | 部门经理 | DEPT 范围、本部门与下级 |
| `SG000013` | 普通员工 | SELF 范围、无权管理 |
| `SG000281` | 普通员工 | 补充范围检查 |
| `DEMO000100` | 部门管理员 | 本部门子树、范围外拒绝 |

密码为部署时设置的 `RBAC_INITIAL_PASSWORD`，向成员 4 获取。2026-10-21 起演示环境的所有预置账号使用同一演示密码。新创建的临时账号会被要求首次改密，这是预期行为。

## 4. 五分钟快速验证

在能访问服务器的电脑上执行（PowerShell 或 Linux shell）：

```sh
curl -s http://<服务器IP>:<端口>/api/v1/health
# 预期 {"status":"UP"}

curl -s -o /dev/null -w "%{http_code}\n" http://<服务器IP>:<端口>/stage1/
# 预期 200

curl -s -o /dev/null -w "%{http_code}\n" http://<服务器IP>:<端口>/swagger-ui/index.html
# 预期 200

curl -s -o /dev/null -w "%{http_code}\n" http://<服务器IP>:<端口>/api/v1/users
# 未登录预期 401
```

浏览器打开 Swagger 后：

1. 展开 `POST /api/v1/auth/login`，点 Try it out；
2. 请求体填 `{"account":"admin","password":"<演示密码>"}`；
3. 执行成功后会保存 `JSESSIONID` Cookie；
4. 再调用 `GET /api/v1/me`、`GET /api/v1/me/permissions`、`GET /api/v1/users`。

## 5. 自动 HTTP 自测

### 5.1 从另一台电脑（推荐成员 6 使用）

先克隆仓库并切到与服务器一致的 SHA：

```sh
git clone https://github.com/wenjiu-leng66/RBAC-Permission-System.git
cd RBAC-Permission-System
git checkout <部署SHA>
```

执行冒烟脚本（脚本会提示输入演示密码，不会回显）：

```sh
python3 scripts/smoke_http.py --base-url http://<服务器IP>:<端口> --check-web
```

通过标准：

- 最后一行输出 `HTTP acceptance checks passed.`
- 退出码为 0

脚本会创建并清理临时合成账号，只允许在合成验收环境运行。

### 5.2 校验 OpenAPI

```sh
python3 -m venv .venv
. .venv/bin/activate            # Windows: .venv\Scripts\activate
pip install -r scripts/requirements-validation.txt
python scripts/check_openapi.py --base-url http://<服务器IP>:<端口>
```

预期：`OpenAPI valid`，且 `Live backend methods and paths exactly match the API design`。

### 5.3 在服务器上执行

只有能登录服务器的人使用，`deploy/.env` 提供密码：

```sh
cd /srv/RBAC-Permission-System
python3 scripts/smoke_http.py --base-url http://127.0.0.1:<端口> --env-file deploy/.env --check-web
```

## 6. 手工验收清单

先用无痕窗口和不同身份各开一个会话，避免 Cookie 混用。至少完成下表：

| 编号 | 操作 | 预期 |
| --- | --- | --- |
| 1 | 未登录调用 `GET /api/v1/users` | 401 |
| 2 | 用错误密码、不存在账号、停用账号登录 | 统一 401 `INVALID_CREDENTIALS` |
| 3 | 普通员工调用管理接口 | 403 |
| 4 | 普通员工查看台账、我的权限 | 只见本人数据，SELF 范围 |
| 5 | 部门经理查看本部门及下级 | 可见本部门子树 |
| 6 | 修改 URL 中的用户 ID 访问其他部门 | 403，响应不含目标数据 |
| 7 | 新建临时账号并首次登录 | 要求改密，未改密前其他接口 403 |
| 8 | 错旧密码改密，再用正确旧密码改密 | 错旧密码拒绝；改密后旧密码失效 |
| 9 | 两个管理员先后用同一版本保存角色 | 第一次成功，第二次 409 `VERSION_CONFLICT` |
| 10 | 管理员撤销员工角色后员工立即刷新 | 权限立即变化，无需重新登录 |
| 11 | 停用仍保持登录的临时用户 | 下一次请求 401 |
| 12 | 停用/删除自己、停用 ADMIN、改 ADMIN 授权 | 409 `PROTECTED_ACCOUNT` / `PROTECTED_ROLE` |
| 13 | 角色授权提交未知权限码或重复元素 | 400，原授权不变 |
| 14 | 退出后重新访问受保护接口 | 401 |

授权写入中途失败的回滚应以后端集成测试为准，不能用原型里的“模拟失败”按钮作为证据。

## 7. 服务器运维命令（仅部署负责人使用）

```sh
cd /srv/RBAC-Permission-System
docker compose --env-file deploy/.env -f deploy/compose.yaml ps
docker compose --env-file deploy/.env -f deploy/compose.yaml logs --tail=100 backend
docker compose --env-file deploy/.env -f deploy/compose.yaml restart backend
docker compose --env-file deploy/.env -f deploy/compose.yaml stop
```

**禁止执行** `docker compose down -v` 或 `down --volumes`，会删除 MySQL 数据卷。

## 8. 注意事项

- 不要把 `.env`、密码、Cookie、数据库备份提交到 Git 或发到公开群。
- 不要在装载真实员工数据的库上运行冒烟脚本。
- 合成数据与原始课程 Excel 数据分开核对；全量导入由成员 1 负责。
- 重启后旧会话失效是正常现象，重新登录即可。
- 页面原型是模拟数据；真实后端结论以 Swagger、HTTP 脚本和响应状态为准。

## 9. 记录格式

每个验收人按下面格式提交，只附脱敏证据：

```text
姓名 / 完成时间：
被测 SHA：
环境与数据：服务器地址、合成数据、浏览器或 HTTP 脚本
操作与命令：
预期：
实际：
结论：通过 / 失败 / 待执行
证据：脱敏日志、截图、PR/Issue 链接
剩余事项：负责人与时间
```

服务器部署与自测的成员 4 记录见 [成员4一期服务器部署与自测记录](../testing/member4-stage1-deployment.md)。
