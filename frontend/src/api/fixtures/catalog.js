// 资源目录：系统 > 模块 > 功能点 > 操作。目录只读，由导入维护（CAT-01）。
import { systems, functionRows } from './catalog-data.js'

export { systems }

// 6 种操作，顺序即界面按钮顺序（CAT-02）。
export const actions = [
  { code: 'read', label: '查看' },
  { code: 'create', label: '新增' },
  { code: 'edit', label: '编辑' },
  { code: 'approve', label: '审批' },
  { code: 'export', label: '导出' },
  { code: 'delete', label: '删除' }
]
export const actionLabel = code => actions.find(item => item.code === code)?.label || code

export const scopeOptions = [
  { value: 'ALL', label: '全部' },
  { value: 'DEPT', label: '本部门' },
  { value: 'SELF', label: '本人' }
]
export const scopeLabel = value => scopeOptions.find(item => item.value === value)?.label || '—'

const systemByCode = new Map(systems.map(item => [item.code, item]))
export const functions = functionRows.map(([id, systemCode, module, name]) => ({
  id, systemCode, systemName: systemByCode.get(systemCode).name, module, name,
  description: `${systemByCode.get(systemCode).name} / ${module} / ${name}`
}))
export const functionById = new Map(functions.map(item => [item.id, item]))
export const modules = [...new Map(functions.map(item => [`${item.systemCode}/${item.module}`, { systemCode: item.systemCode, name: item.module }])).values()]

export const permissionCode = (fn, action) => `${fn.systemCode}:${fn.id}:${action}`
export const catalogPermissions = functions.flatMap(fn => actions.map(action => ({
  code: permissionCode(fn, action.code), functionId: fn.id, action: action.code,
  systemCode: fn.systemCode, systemName: fn.systemName, module: fn.module, name: fn.name
})))

export function parsePermissionCode(code) {
  const [systemCode, resource, action] = String(code).split(':')
  return { systemCode, resource, action }
}

// 系统管理后台中已实现的模块映射为 rbac:* 权限（CAT-03）。映射表是原型提案，接口设计阶段写入契约。
export const adminMapping = {
  f339: { page: '/admin/users', actions: { read: ['rbac:user:read'], create: ['rbac:user:create'], edit: ['rbac:user:update', 'rbac:user:assign-role'], delete: ['rbac:user:delete'] } },
  f340: { page: '/admin/roles', actions: { read: ['rbac:role:read'], create: ['rbac:role:create'], edit: ['rbac:role:update'], delete: ['rbac:role:delete'] } },
  f341: { page: '/admin/grants', actions: { read: ['rbac:permission:read'], edit: ['rbac:role:grant'] } },
  f342: { page: '/admin/departments', actions: { read: ['rbac:department:read'] } },
  f348: { page: '/admin/logs', stage: 3, actions: { read: ['rbac:log:read'], export: ['rbac:log:export'] } },
  f349: { page: '/admin/logs', stage: 3, actions: { read: ['rbac:log:read'], export: ['rbac:log:export'] } }
}

// 管理后台权限码的中文说明，用于“我的权限”和映射表。
export const adminPermissions = [
  ['rbac:user:read', '用户管理', '查看用户'],
  ['rbac:user:create', '用户管理', '新增用户'],
  ['rbac:user:update', '用户管理', '编辑、启停用、重置密码'],
  ['rbac:user:assign-role', '用户管理', '分配角色'],
  ['rbac:user:delete', '用户管理', '删除用户'],
  ['rbac:role:read', '角色管理', '查看角色'],
  ['rbac:role:create', '角色管理', '新增角色'],
  ['rbac:role:update', '角色管理', '编辑、启停用角色'],
  ['rbac:role:delete', '角色管理', '删除自建角色'],
  ['rbac:permission:read', '权限配置', '查看资源目录与角色授权'],
  ['rbac:role:grant', '权限配置', '修改角色授权'],
  ['rbac:department:read', '组织架构管理', '查看组织架构'],
  ['rbac:log:read', '日志审计', '查看日志（阶段三）'],
  ['rbac:log:export', '日志审计', '导出日志（阶段三）']
].map(([code, module, name]) => ({ code, systemCode: 'rbac', systemName: '管理后台', module, name }))

export function describePermission(code) {
  const admin = adminPermissions.find(item => item.code === code)
  if (admin) return { ...admin, action: code.split(':')[2], functionId: null }
  const { systemCode, resource, action } = parsePermissionCode(code)
  const fn = functionById.get(resource)
  if (!fn || fn.systemCode !== systemCode) return null
  return { code, systemCode, systemName: fn.systemName, module: fn.module, name: fn.name, functionId: fn.id, action }
}

export const knownPermission = code => Boolean(describePermission(code)) && (code.startsWith('rbac:') || actions.some(item => item.code === parsePermissionCode(code).action))
