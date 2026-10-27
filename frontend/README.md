# 前端界面原型

Vue 3、Element Plus、Vue Router 和 Vite。成员 2 陈清香负责公共结构、登录、用户与部门，成员 3 杨昕桐负责角色、授权、资源目录及门户权限交互。

## 运行和构建

在仓库根目录执行：

```powershell
npm --prefix frontend ci
npm --prefix frontend run dev
node --test tests/frontend/*.test.cjs
npm --prefix frontend run build
```

开发地址以终端为准，默认 `http://localhost:5173`。构建输出 `frontend/dist/`；构建脚本同时复制 OpenAPI 和 `/stage1/` 检查入口。Vite 的代理配置不改变当前页面统一调用 `src/api/mock.js` 的事实。

## 数据和身份边界

当前是一套可交互的完整原型，**尚未接入真实 HTTP 后端**。所有用户、角色、权限与保存结果均在内存中；刷新后重建，`sessionStorage` 仅保存用于恢复演示身份的用户 ID。演示身份切换免密码仅用于原型，不能移植为真实认证方案。

原型口令为 `123456`，与真实后端的 `RBAC_INITIAL_PASSWORD` 分别管理：

| 身份 | 合成账号 | 关注点 |
| --- | --- | --- |
| 超级管理员 | `admin` | 管理界面和账号保护 |
| 公司领导 | `SG000001` | 可读管理页面，写操作受限 |
| 部门经理 | `SG000011` | 台账 read=ALL，edit=DEPT |
| 项目经理 | `SG000281` | 项目部数据范围 |
| 普通员工 | `SG000013` | 本人台账与权限菜单 |
| 部门管理员 | `DEMO000100` | 本部门及下级用户、业务角色分配 |
| 首次登录 | `DEMO000101` | 强制改密导航 |
| 停用账号 | `DEMO000102` | 统一失败提示 |
| 无角色账号 | `DEMO000103` | 可登录、空菜单、默认拒绝 |

页面和路由总表见 [界面设计](../docs/design/stage1-ui-prototype.md)。角色继承、职责约束、角色激活和审计页面为后续阶段静态示例，不代表已实现。

## 代码怎么读

| 顺序 | 文件 | 需要理解的规则 |
| --- | --- | --- |
| 1 | `src/authorization/rbac0-policy.js` | 授权取并集、同码最大范围、未授予拒绝；不处理一期以外的继承/约束 |
| 2 | `src/policy-adapter.js` | 数据形状与路由规则适配；管理码派生在`src/api/mock.js`的`computeGrants` |
| 3 | `src/stores/auth.js`、`src/router/index.js` | 原型身份恢复、权限刷新、首次改密优先、直接 URL 访问 |
| 4 | `src/api/mock.js` | 内存 CRUD、版本冲突、范围模拟；边界与真后端不同 |
| 5 | `src/views/RoleGrantsView.vue` | 全量授权草稿、read 前置、旧响应抑制、冲突后刷新 |
| 6 | `src/views/UsersView.vue`、`LedgerView.vue` | 筛选分页、用户授权和仅电话/邮箱编辑 |
| 7 | `src/App.vue`、`src/navigation.js` | 门户/管理区、导航与演示身份切换 |

复杂逻辑的注释解释约束和失败路径；完整调用链见 [代码阅读路线](../docs/stage1/code-reading-guide.md)。

## 状态与测试范围

列表地址可加 `?demo=loading`、`?demo=empty`、`?demo=error` 演示加载、空态和失败。角色授权与用户分配角色可模拟另一管理员保存导致的 409，角色授权可模拟中途失败；这些都是交互演示，数据库事务必须由 Java/真实 HTTP 验证。

mock 对角色成员范围、重复授权码等细节与后端存在差异。Users/Ledger/Departments/Dashboard 的快速筛选或切换目标还需手工检查旧请求回写；注释记录了当前行为，未把它包装成已修复问题。完整自查见 [边界清单](../docs/stage1/review-checklist.md)。

历史独立原型在 `prototypes/`，成员记录和截图保留在 `docs/testing/`、`docs/demo/`。它们用于追溯，当前 Vue 原型和实际运行结果以本页及 [一期验证记录](../docs/stage1/verification-record.md)为准。
