// 保留成员 3 的经典脚本；浏览器加载后通过 Rbac0 使用同一份策略。
import './authorization/rbac0-policy.js'

export const policy = globalThis.Rbac0
if (!policy) throw new Error('RBAC0 权限策略未加载')

export function policySession(auth) {
  return {
    authenticated: auth.isAuthenticated,
    permissionsLoaded: auth.permissionsLoaded,
    userId: auth.user?.id,
    permissions: auth.permissions,
    scopes: auth.scopes
  }
}

// 路由需要的权限码：meta.permission（单个）、meta.permissions（全部满足）或 meta.permissionOf(route)（随参数变化）。
function requiredPermissions(to) {
  if (typeof to.meta.permissionOf === 'function') {
    const code = to.meta.permissionOf(to)
    return code ? [code] : null
  }
  if (Array.isArray(to.meta.permissions)) return to.meta.permissions
  return to.meta.permission ? [to.meta.permission] : undefined
}

export function routeAccess(auth, to) {
  const session = policySession(auth)
  const required = requiredPermissions(to)
  // 工作台、错误页和参数无效的功能点页仅要求登录，错误页避免重复跳转到自身。
  if (to.meta.sessionOnly || required === null) {
    return session.authenticated
      ? { allowed: true, redirect: null, reason: 'session-only' }
      : { allowed: false, redirect: '/login', reason: 'unauthenticated' }
  }
  return policy.canVisit(session, {
    path: to.path,
    public: to.meta.public,
    requiredPermissions: required
  })
}

export function visibleNavigation(auth, items) {
  return items.filter(item => routeAccess(auth, { path: item.path, meta: item }).allowed)
}

export function hasPermission(auth, code) {
  return policy.hasPermission(policySession(auth), code)
}

// 当前会话中某权限码的数据范围：ALL / DEPT / SELF；没有权限时为 null。
export function permissionScope(auth, code) {
  return policy.permissionScope(policySession(auth), code)
}
