# 环境与工具约定

第一阶段集成运行方式以 [部署说明](../../deploy/README.md)、[后端说明](../../backend-procedural/README.md) 和 [前端说明](../../frontend/README.md) 为准。下面是当前代码约定；实际被测版本另外记录于 [验证记录](../stage1/verification-record.md)。

| 组件 | 约定 |
| --- | --- |
| 后端 | JDK 21、Spring Boot 3.3.5、Spring JDBC、Maven |
| 密码 | Spring Security PBKDF2 v5_8，写带前缀`{pbkdf2@SpringSecurity_v5_8}`；兼容旧带前缀哈希校验 |
| 数据库 | MySQL 8.4；正式V001结构；H2仅用于独立开发/自动测试，不替代MySQL验收 |
| 前端 | Vue 3、JavaScript、Element Plus、Vite；npm，提交锁文件 |
| Node.js | 20.19+或22.12+；Docker构建版本以web.Dockerfile为准 |
| 部署 | Linux、Docker Engine、Compose 2.20+、Nginx |
| 文档/导入/HTTP脚本 | Python 3.11+，仅标准库脚本无需额外pip依赖 |
| 浏览器 | 桌面版Chrome/Edge |

前端开发端口5173、后端开发端口8080；Docker同源入口默认80，仅发布Web端口，MySQL和Java留在内部网络。生产HTTPS按部署说明配置，Cookie启用Secure。

使用UTF-8；环境文件、真实输入资料、数据库数据和报告与源码分开。无需为一期引入Redis或Kubernetes。第二阶段面向对象后端使用独立等价数据库，不与面向功能后端随机混用会话。
