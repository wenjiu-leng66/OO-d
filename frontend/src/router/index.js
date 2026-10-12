import { createRouter, createWebHistory } from 'vue-router'
import { auth } from '../stores/auth'
import { routeAccess } from '../policy-adapter'
import LoginView from '../views/LoginView.vue'
import DashboardView from '../views/DashboardView.vue'
import UsersView from '../views/UsersView.vue'
import DepartmentsView from '../views/DepartmentsView.vue'
import NotFoundView from '../views/NotFoundView.vue'
import ForbiddenView from '../views/ForbiddenView.vue'
import RolesView from '../views/RolesView.vue'

const routes = [
  { path: '/login', component: LoginView, meta: { public: true } },
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', component: DashboardView, meta: { title: '管理首页', sessionOnly: true } },
  { path: '/users', component: UsersView, meta: { title: '用户管理', permission: 'rbac:user:read' } },
  { path: '/departments', component: DepartmentsView, meta: { title: '部门架构', permission: 'rbac:department:read' } },
  { path: '/403', component: ForbiddenView, meta: { title: '无权访问', sessionOnly: true } },
  { path: '/roles', component: RolesView, meta: { title: '角色权限', permission: 'rbac:role:read' } },
  // 成员3的角色、权限、继承和约束页面从这里扩展，不重复实现。
  { path: '/:pathMatch(.*)*', component: NotFoundView, meta: { sessionOnly: true } }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  if (to.meta.public) return auth.isAuthenticated ? '/dashboard' : true
  const access = routeAccess(auth, to)
  if (!access.allowed) return access.redirect === '/login'
    ? { path: '/login', query: { redirect: to.fullPath } }
    : access.redirect
  return true
})

export default router
