// 菜单：门户按“查看”权限生成 系统 > 模块 > 功能点（PORTAL-01）；管理后台按 rbac:* 权限显示。
import { functions, systems, adminMapping } from './api/fixtures/catalog.js'
import { visibleNavigation } from './policy-adapter.js'

// 功能点的入口：员工台账为真实业务页；系统管理后台已实现的模块进入管理页面；其余为占位页。
export function functionPath(fn) {
  if (fn.id === 'f046') return '/ledger'
  return adminMapping[fn.id]?.page || `/portal/f/${fn.id}`
}

export function portalMenu(auth) {
  const readable = new Set(auth.permissionsLoaded ? auth.permissions : [])
  const tree = []
  for (const system of systems) {
    const items = functions.filter(fn => fn.systemCode === system.code && readable.has(`${fn.systemCode}:${fn.id}:read`))
    if (!items.length) continue
    const modules = []
    for (const fn of items) {
      let group = modules.find(item => item.name === fn.module)
      if (!group) modules.push(group = { name: fn.module, functions: [] })
      group.functions.push({ ...fn, path: functionPath(fn) })
    }
    tree.push({ ...system, modules, count: items.length })
  }
  return tree
}

export const adminItems = [
  { path: '/admin', label: '管理概览', permission: 'rbac:user:read' },
  { path: '/admin/users', label: '用户管理', permission: 'rbac:user:read' },
  { path: '/admin/departments', label: '组织架构', permission: 'rbac:department:read' },
  { path: '/admin/roles', label: '角色管理', permission: 'rbac:role:read' },
  { path: '/admin/grants', label: '角色授权', permissions: ['rbac:role:read', 'rbac:permission:read'] },
  { path: '/admin/catalog', label: '资源目录', permission: 'rbac:permission:read' }
]

export const laterStageItems = [
  { path: '/admin/hierarchy', label: '角色继承', stage: 2, permission: 'rbac:role:read' },
  { path: '/admin/constraints', label: '职责约束', stage: 3, permission: 'rbac:role:read' },
  { path: '/session/roles', label: '激活角色', stage: 3, sessionOnly: true },
  { path: '/admin/logs', label: '日志审计', stage: 3, permission: 'rbac:log:read' }
]

export const visibleAdminItems = auth => visibleNavigation(auth, adminItems)
export const visibleLaterStageItems = auth => visibleNavigation(auth, laterStageItems)
