# 成员 2：Vue 路由接入成员 3 权限策略

## 接入范围

`frontend/src/policy-adapter.js` 加载成员 3 原有经典脚本，将 Vue 的
`isAuthenticated` 映射为策略要求的 `authenticated`，同时传入
`permissionsLoaded`、用户 ID 和有效权限。路由守卫调用 `canVisit`，
侧边栏调用 `visibleRoutes`。未登录跳转登录页并保留目标地址；已登录
但缺少权限或权限未加载时跳转 `/403`。

`/403` 和未知地址的 404 页面只要求登录，以免错误页面产生重定向循环。
第一阶段界面原型已补齐角色管理、角色授权等页面，并在每次切换页面时重新拉取权限，见 [第一阶段界面原型](../design/stage1-ui-prototype.md)。
前端判断不能替代后端授权。

## 验收方法

在 `frontend` 下运行 `npm run dev`，使用终端显示的地址。

1. 未登录直接访问 `/admin/roles`，应进入 `/login?redirect=/admin/roles`。
2. 使用 admin / 123456 登录，应回到角色管理；用户、组织架构页面可正常访问。
3. 退出后直接访问 `/admin/roles`，使用 SG000013 / 123456 登录，应进入 `/403`；侧边栏没有管理后台入口。
4. SG000013 点击“返回门户首页”，应正常进入门户首页。
5. 权限未加载时不得放行，相关行为由下面的接入测试覆盖。

刷新页面会恢复初始数据，但会话按 `sessionStorage` 中记住的账号恢复。

在仓库根目录运行：

```sh
node --test tests/frontend/rbac0-policy.test.cjs tests/frontend/policy-router.test.cjs
```

在 `frontend` 下运行 `npm run build`，确认生产构建能加载策略。
