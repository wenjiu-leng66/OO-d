// 保留成员 3 的经典脚本；浏览器加载后通过 Rbac0 使用同一份策略。
import './authorization/rbac0-policy.js'

const policy = globalThis.Rbac0
if (!policy) throw new Error('RBAC0 权限策略未加载')

export function policySession(auth) {
  return {
    authenticated: auth.isAuthenticated,
    permissionsLoaded: auth.permissionsLoaded,
    userId: auth.user?.id,
    permissions: auth.permissions
  }
}

export function routeAccess(auth, to) {
  const session = policySession(auth)
  // 工作台和错误页面仅要求登录，错误页避免重复跳转到自身。
  if (to.meta.sessionOnly) {
    return session.authenticated
      ? { allowed: true, redirect: null, reason: 'session-only' }
      : { allowed: false, redirect: '/login', reason: 'unauthenticated' }
  }
  return policy.canVisit(session, {
    path: to.path,
    public: to.meta.public,
    requiredPermissions: to.meta.permission ? [to.meta.permission] : undefined
  })
}

export function visibleNavigation(auth, items) {
  return items.filter(item => routeAccess(auth, { path: item.path, meta: item }).allowed)
}

export function hasPermission(auth, code) {
  return policy.hasPermission(policySession(auth), code)
}
