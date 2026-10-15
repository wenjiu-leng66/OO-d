import { createRouter, createWebHistory } from 'vue-router'
import { auth, restoreSession, refreshSession } from '../stores/auth'
import { routeAccess } from '../policy-adapter'
import { functionById } from '../api/fixtures/catalog'
import LoginView from '../views/LoginView.vue'
import ChangePasswordView from '../views/ChangePasswordView.vue'
import PortalHomeView from '../views/PortalHomeView.vue'
import FunctionView from '../views/FunctionView.vue'
import LedgerView from '../views/LedgerView.vue'
import MyPermissionsView from '../views/MyPermissionsView.vue'
import DashboardView from '../views/DashboardView.vue'
import UsersView from '../views/UsersView.vue'
import DepartmentsView from '../views/DepartmentsView.vue'
import RolesView from '../views/RolesView.vue'
import RoleGrantsView from '../views/RoleGrantsView.vue'
import CatalogView from '../views/CatalogView.vue'
import HierarchyView from '../views/HierarchyView.vue'
import ConstraintsView from '../views/ConstraintsView.vue'
import ActivateRolesView from '../views/ActivateRolesView.vue'
import AuditLogsView from '../views/AuditLogsView.vue'
import NotFoundView from '../views/NotFoundView.vue'
import ForbiddenView from '../views/ForbiddenView.vue'

// 功能点占位页需要该功能点的“查看”权限；编号不存在时由页面显示 404。
const functionReadCode = to => {
  const fn = functionById.get(String(to.params.fid))
  return fn ? `${fn.systemCode}:${fn.id}:read` : null
}

const routes = [
  { path: '/login', component: LoginView, meta: { public: true } },
  { path: '/', redirect: '/portal' },
  { path: '/password', component: ChangePasswordView, meta: { title: '修改密码', sessionOnly: true, area: 'portal' } },
  { path: '/portal', component: PortalHomeView, meta: { title: '门户首页', sessionOnly: true, area: 'portal' } },
  { path: '/portal/f/:fid', component: FunctionView, meta: { title: '功能点', permissionOf: functionReadCode, area: 'portal' } },
  { path: '/ledger', component: LedgerView, meta: { title: '员工台账管理', permission: 'hr:f046:read', area: 'portal' } },
  { path: '/me/permissions', component: MyPermissionsView, meta: { title: '我的权限', sessionOnly: true, area: 'portal' } },
  { path: '/session/roles', component: ActivateRolesView, meta: { title: '激活角色', sessionOnly: true, area: 'portal', stage: 3 } },
  { path: '/admin', component: DashboardView, meta: { title: '管理概览', permission: 'rbac:user:read', area: 'admin' } },
  { path: '/admin/users', component: UsersView, meta: { title: '用户管理', permission: 'rbac:user:read', area: 'admin' } },
  { path: '/admin/departments', component: DepartmentsView, meta: { title: '组织架构', permission: 'rbac:department:read', area: 'admin' } },
  { path: '/admin/roles', component: RolesView, meta: { title: '角色管理', permission: 'rbac:role:read', area: 'admin' } },
  { path: '/admin/grants', component: RoleGrantsView, meta: { title: '角色授权', permissions: ['rbac:role:read', 'rbac:permission:read'], area: 'admin' } },
  { path: '/admin/catalog', component: CatalogView, meta: { title: '资源目录', permission: 'rbac:permission:read', area: 'admin' } },
  { path: '/admin/hierarchy', component: HierarchyView, meta: { title: '角色继承', permission: 'rbac:role:read', area: 'admin', stage: 2 } },
  { path: '/admin/constraints', component: ConstraintsView, meta: { title: '职责约束', permission: 'rbac:role:read', area: 'admin', stage: 3 } },
  { path: '/admin/logs', component: AuditLogsView, meta: { title: '日志审计', permission: 'rbac:log:read', area: 'admin', stage: 3 } },
  // 成员 2 早期路由，保留为跳转，避免旧链接失效。
  { path: '/dashboard', redirect: '/portal' },
  { path: '/users', redirect: '/admin/users' },
  { path: '/departments', redirect: '/admin/departments' },
  { path: '/roles', redirect: '/admin/roles' },
  { path: '/403', component: ForbiddenView, meta: { title: '无权访问', sessionOnly: true } },
  { path: '/:pathMatch(.*)*', component: NotFoundView, meta: { title: '页面不存在', sessionOnly: true } }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach(async (to) => {
  await restoreSession()
  await refreshSession()
  if (to.meta.public) return auth.isAuthenticated ? '/portal' : true
  // 首次登录或被重置密码后，只能进入修改密码页（LOGIN-05）。
  if (auth.isAuthenticated && auth.mustChangePassword && to.path !== '/password') return '/password'
  const access = routeAccess(auth, to)
  if (!access.allowed) return access.redirect === '/login'
    ? { path: '/login', query: { redirect: to.fullPath } }
    : { path: access.redirect, query: { from: to.fullPath } }
  return true
})

export default router
