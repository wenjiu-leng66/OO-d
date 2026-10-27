// 菜单：门户按“查看”权限生成 系统 > 模块 > 功能点（PORTAL-01）；管理后台按 rbac:* 权限显示。
import { functions, systems, adminMapping } from './api/fixtures/catalog.js'
import { visibleNavigation } from './policy-adapter.js'

// 功能点入口：台账有完整原型交互（数据仍来自 mock）；管理功能进入对应页面，其余仅展示操作占位。
export function functionPath(fn) {
  if (fn.id === 'f046') return '/ledger'
  return adminMapping[fn.id]?.page || `/portal/f/${fn.id}`
}

// 先按 read 权限筛选叶子，再生成非空系统/模块，避免菜单泄露无权访问的空分组。
// 导航可见不代替路由守卫和 API 校验；只有 create/edit 而没有 read 时也不产生入口。
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
