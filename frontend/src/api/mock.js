// 模拟后端：页面统一通过这里调用，规则按需求规格第 3 章实现，正式后端必须再次校验。
// 数据全部是合成数据，只保存在本次页面运行的内存中，刷新后恢复。
import { policy } from '../policy-adapter.js'
import {
  functions, functionById, systems, modules, actions, catalogPermissions, adminMapping,
  parsePermissionCode, scopeLabel
} from './fixtures/catalog.js'
import { departments as departmentRows, departmentTypes, roleRows, roleGrants, createUsers } from './fixtures/demo-data.js'

// 演示口令只用于本原型；正式初始密码在导入时通过参数指定，不写进仓库（IMPORT-06）。
export const DEMO_PASSWORD = '123456'

export class ApiError extends Error {
  constructor(status, code, message, details) {
    super(message)
    Object.assign(this, { status, code, details })
  }
}
const fail = (status, code, message, details) => { throw new ApiError(status, code, message, details) }

// 向页面返回脱离内存库的 JSON 快照；组件不能通过修改响应对象绕过保存接口。
// 这里只处理 JSON 结构，不承诺保留 Date、Map、undefined 等非 JSON 类型。
const copy = value => JSON.parse(JSON.stringify(value))
const inBrowser = typeof window !== 'undefined'

export function demoMode() {
  return inBrowser ? new URLSearchParams(window.location.search).get('demo') || 'normal' : 'normal'
}
const wait = (value, ms = 250) => new Promise(resolve => setTimeout(() => resolve(value), inBrowser ? ms : 0))
// 列表接口支持成员 2 的 ?demo=loading|empty|error 状态模拟。
async function listResponse(value) {
  const mode = demoMode()
  if (mode === 'error') { await wait(null); fail(500, 'DEMO_ERROR', '演示请求失败') }
  if (mode === 'loading') return wait(value, 1800)
  if (mode === 'empty') return wait(Array.isArray(value) ? [] : { ...value, items: [], total: 0 })
  return wait(value)
}

// ---------- 内存数据 ----------
const db = {
  departments: copy(departmentRows),
  users: createUsers(),
  roles: roleRows.map(([id, code, name, kind, builtin, description]) => ({
    id, code, name, kind, builtin, description, enabled: true, permissionVersion: 1, grants: copy(roleGrants[id] || [])
  }))
}
const passwords = new Map(db.users.map(user => [user.id, DEMO_PASSWORD]))
let sessionUserId = null
let nextUserNo = db.users.length + 1
let nextRoleNo = db.roles.length + 1

const departmentById = id => db.departments.find(item => item.id === id)
const userById = id => db.users.find(item => item.id === id)
const roleById = id => db.roles.find(item => item.id === id)
const SUPER_ADMIN = 'R01'

// 目标部门是否为基准部门本身或其下级（DEPT 范围）。
// 从目标向父级回溯，而非只比较直接父级，因此 DEPT 包含所有层级的下级。
// 合成部门树由固定数据保证无环；正式导入必须先验证组织树合法性。
export function isWithinDepartment(targetId, baseId) {
  for (let node = departmentById(targetId); node; node = departmentById(node.parentId)) {
    if (node.id === baseId) return true
  }
  return false
}
function departmentPath(id) {
  const names = []
  for (let node = departmentById(id); node; node = departmentById(node.parentId)) names.unshift(node.name)
  return names.join(' / ')
}

// 明确挑选对外字段，口令表永不进入列表/详情响应；显示用角色列表不等于有效授权。
function publicUser(user) {
  const roles = user.roleIds.map(roleById).filter(Boolean)
  return {
    id: user.id, username: user.username, name: user.name, departmentId: user.departmentId,
    departmentName: departmentById(user.departmentId)?.name || '', departmentPath: departmentPath(user.departmentId),
    position: user.position, phone: user.phone, email: user.email, status: user.status,
    mustChangePassword: user.mustChangePassword, roleIds: [...user.roleIds], roleVersion: user.roleVersion,
    roleNames: roles.map(role => role.name)
  }
}

// 有效权限 = 启用角色授权的并集，同码取最大范围；系统管理后台功能点再映射为 rbac:*（CAT-03）。
// 映射只允许从目录中已有 sys:* 授权派生，不能因为有某个管理角色名称就放开所有 rbac:*。
// via 记录派生链，sources 记录角色与原范围，便于“我的权限”追溯实际来源。
export function computeGrants(user) {
  const base = policy.effectiveGrants(user, db.roles)
  const derived = new Map()
  for (const grant of base) {
    const { systemCode, resource, action } = parsePermissionCode(grant.code)
    const mapped = systemCode === 'sys' ? adminMapping[resource]?.actions[action] : null
    for (const code of mapped || []) {
      const entry = derived.get(code) || { code, scope: null, sources: [], via: [] }
      entry.scope = policy.widerScope(entry.scope, grant.scope)
      for (const source of grant.sources) {
        if (!entry.sources.some(item => item.roleId === source.roleId && item.scope === source.scope)) entry.sources.push(source)
      }
      if (!entry.via.includes(grant.code)) entry.via.push(grant.code)
      derived.set(code, entry)
    }
  }
  return [...base, ...[...derived.values()].sort((a, b) => a.code.localeCompare(b.code))]
}

function scopesOf(grants) {
  return Object.fromEntries(grants.map(grant => [grant.code, grant.scope]))
}

// ---------- 认证与鉴权：认证 → 权限码 → 数据范围 → 目标对象（AUTHZ-01） ----------
// 每个接口调用读取当前账号状态；停用/删除身份即失效。
// 首次改密仅对本人信息和改密显式放行，不能因为成功登录就进入业务。
function sessionUser({ allowPasswordChange = false } = {}) {
  const user = userById(sessionUserId)
  if (!user || user.status !== 'ENABLED') {
    sessionUserId = null
    fail(401, 'UNAUTHENTICATED', '登录已失效，请重新登录')
  }
  if (user.mustChangePassword && !allowPasswordChange) fail(403, 'PASSWORD_CHANGE_REQUIRED', '请先修改初始密码')
  return user
}

// 写入延迟只模拟网络；返回后再检查权限与版本，比较和提交之间不能再次让出执行权。
async function waitForWrite(ms = 250) {
  const actor = sessionUser()
  await wait(null, ms)
  if (sessionUserId !== actor.id) fail(401, 'UNAUTHENTICATED', '演示身份已改变，本次保存已取消')
}

// 每次重新计算启用角色的当前授权，不缓存上次登录的结果（GRANT-05）。
function authorize(code) {
  const user = sessionUser()
  const grant = computeGrants(user).find(item => item.code === code)
  if (!grant) fail(403, 'FORBIDDEN', '没有执行此操作的权限')
  return { user, scope: grant.scope }
}

function covers(scope, actor, target) {
  return policy.scopeCovers(scope,
    { userId: actor.id, departmentId: actor.departmentId },
    { ownerId: target.id, departmentId: target.departmentId },
    isWithinDepartment)
}

// 部门本身没有个人 ownerId，因此 SELF 不得用于新建他人账号或跨部门迁移。
function departmentAllowed(scope, actor, departmentId) {
  return covers(scope, actor, { id: null, departmentId })
}

// rows 必须已经完成范围和业务筛选，再分页并统计 total，防止通过数量泄露范围外人员。
function paginate(rows, page = 1, pageSize = 10) {
  const safeSize = Math.min(Math.max(Number(pageSize) || 10, 1), 100)
  const safePage = Math.max(Number(page) || 1, 1)
  return { items: rows.slice((safePage - 1) * safeSize, safePage * safeSize), total: rows.length, page: safePage, pageSize: safeSize }
}

const PHONE = /^1\d{10}$/
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
function checkContact({ phone, email }, userId) {
  if (phone !== undefined && phone !== '') {
    if (!PHONE.test(phone)) fail(400, 'VALIDATION', '手机号须为 11 位数字')
    if (db.users.some(item => item.phone === phone && item.id !== userId)) fail(409, 'DUPLICATE_PHONE', '手机号已被其他账号使用')
  }
  if (email !== undefined && email !== '' && !EMAIL.test(email)) fail(400, 'VALIDATION', '邮箱格式不正确')
}

function checkRoleInfo(name, description) {
  if (!String(name || '').trim()) fail(400, 'VALIDATION', '请输入角色名称')
  if (String(name).trim().length > 32) fail(400, 'VALIDATION', '角色名称不能超过 32 位')
  if (String(description).length > 255) fail(400, 'VALIDATION', '角色说明不能超过 255 位')
}

// 原型中 R01 对应唯一超级管理员；自操作和管理员保护均由接口再次检查，按钮禁用只是提示。
function protectAccount(actor, target, action) {
  if (target.id === actor.id) fail(409, 'PROTECTED_ACCOUNT', `不能${action}自己的账号`)
  if (target.roleIds.includes(SUPER_ADMIN)) fail(409, 'PROTECTED_ACCOUNT', `唯一的超级管理员账号不能被${action}`)
}

function userInScope(code, id) {
  const { user: actor, scope } = authorize(code)
  const target = userById(id)
  // 范围外与不存在返回同样的结果，不泄露目标是否存在（AUTHZ-03）。
  if (!target || !covers(scope, actor, target)) fail(403, 'OUT_OF_SCOPE', '目标不在你的管理范围内')
  return { actor, target, scope }
}

// ---------- 对外接口 ----------
export const api = {
  // 登录与会话（LOGIN-01 ~ 05）
  async login(account, password) {
    await wait(null)
    const key = String(account || '').trim()
    const user = key && db.users.find(item => item.username === key || (item.phone && item.phone === key))
    if (!user || passwords.get(user.id) !== password || user.status !== 'ENABLED') {
      fail(401, 'BAD_CREDENTIALS', '用户名或密码错误')
    }
    sessionUserId = user.id
    return this.me()
  },
  // 仅原型：刷新页面后按会话中记住的账号恢复，或切换演示身份（无需密码）。正式系统由后端会话完成。
  async resumeDemoSession(userId) {
    const user = userById(userId)
    if (!user || user.status !== 'ENABLED') fail(401, 'UNAUTHENTICATED', '登录已失效，请重新登录')
    sessionUserId = user.id
    return this.me()
  },
  async switchDemoIdentity(username) {
    const user = db.users.find(item => item.username === username)
    if (!user || user.status !== 'ENABLED') fail(401, 'BAD_CREDENTIALS', '用户名或密码错误')
    sessionUserId = user.id
    return this.me()
  },
  logout() { sessionUserId = null },
  async me() {
    const user = sessionUser({ allowPasswordChange: true })
    const grants = user.mustChangePassword ? [] : computeGrants(user)
    return copy({
      user: publicUser(user),
      roles: user.roleIds.map(roleById).filter(Boolean).map(role => ({ id: role.id, name: role.name, enabled: role.enabled })),
      grants, permissions: grants.map(grant => grant.code), scopes: scopesOf(grants)
    })
  },
  async changePassword(oldPassword, newPassword) {
    const user = sessionUser({ allowPasswordChange: true })
    await wait(null)
    if (passwords.get(user.id) !== oldPassword) fail(400, 'BAD_OLD_PASSWORD', '原密码不正确')
    if (typeof newPassword !== 'string' || newPassword.length < 8 || newPassword.length > 64) fail(400, 'VALIDATION', '新密码须为 8–64 位')
    if (newPassword === oldPassword) fail(400, 'VALIDATION', '新密码不能与原密码相同')
    passwords.set(user.id, newPassword)
    user.mustChangePassword = false
    return this.me()
  },

  // 员工台账原型交互（PORTAL-04 ~ 06、UC-08）；此处仍为内存数据，真后端另有独立实现。
  // 查看范围与编辑范围分别计算：能看到的行不一定能修改。
  async listEmployees({ keyword = '', page = 1, pageSize = 10 } = {}) {
    const { user: actor, scope } = authorize('hr:f046:read')
    const editGrant = computeGrants(actor).find(item => item.code === 'hr:f046:edit')
    const text = keyword.trim()
    const rows = db.users
      .filter(user => covers(scope, actor, user))
      .filter(user => !text || `${user.username}${user.name}${user.position}`.includes(text))
      .map(user => ({
        id: user.id, username: user.username, name: user.name, departmentName: departmentById(user.departmentId)?.name,
        departmentPath: departmentPath(user.departmentId), position: user.position, phone: user.phone, email: user.email,
        editable: Boolean(editGrant && covers(editGrant.scope, actor, user))
      }))
    return listResponse({ ...paginate(rows, page, pageSize), readScope: scope, editScope: editGrant?.scope || null })
  },
  // 字段白名单阻止借联系方式接口修改姓名/角色；缺失字段保留，空串允许清空联系方式。
  // 本原型的 await 仅模拟延时，不等同于真后端事务/并发隔离；安全验收须直接调用 Java API。
  async updateEmployeeContact(id, payload) {
    const allowed = ['phone', 'email']
    const extra = Object.keys(payload || {}).filter(key => !allowed.includes(key))
    const { user: actor, scope } = authorize('hr:f046:edit')
    if (extra.length) fail(400, 'FIELD_NOT_EDITABLE', `只能修改电话和邮箱，不能修改：${extra.join('、')}`)
    const target = userById(id)
    if (!target || !covers(scope, actor, target)) fail(403, 'OUT_OF_SCOPE', `目标不在编辑范围（${scopeLabel(scope)}）内，已拒绝`)
    checkContact(payload, target.id)
    await wait(null)
    Object.assign(target, { phone: payload.phone ?? target.phone, email: payload.email ?? target.email })
    return { id: target.id, phone: target.phone, email: target.email }
  },

  // 管理概览（成员 2 的工作台）
  // 人员统计与列表使用同一可见范围，避免只隐藏列表却暴露公司级人数。
  async getOverview() {
    const { user: actor, scope } = authorize('rbac:user:read')
    const visible = db.users.filter(user => covers(scope, actor, user))
    return listResponse({
      scope, totalUsers: visible.length,
      disabledUsers: visible.filter(user => user.status !== 'ENABLED').length,
      pendingPassword: visible.filter(user => user.mustChangePassword).length,
      totalRoles: db.roles.length
    })
  },

  // 用户管理（USER-01 ~ 07、GRANT-01、GRANT-03）
  async listUsers({ departmentId = '', keyword = '', roleId = '', status = '', page = 1, pageSize = 10 } = {}) {
    const { user: actor, scope } = authorize('rbac:user:read')
    const text = keyword.trim()
    const rows = db.users
      .filter(user => covers(scope, actor, user))
      .filter(user => !departmentId || isWithinDepartment(user.departmentId, departmentId))
      .filter(user => !text || user.username.includes(text) || user.name.includes(text))
      .filter(user => !roleId || user.roleIds.includes(roleId))
      .filter(user => !status || user.status === status)
      .map(publicUser)
    return listResponse({ ...paginate(rows, page, pageSize), scope })
  },
  async getUser(id) {
    const { target } = userInScope('rbac:user:read', id)
    return wait({ ...publicUser(target), permissionCount: computeGrants(target).length })
  },
  // 新增只建账号且强制首次改密，初始角色为空；授角色必须走独立授权接口。
  async createUser(payload) {
    const { user: actor, scope } = authorize('rbac:user:create')
    const username = String(payload.username || '').trim()
    if (!/^[A-Za-z0-9_]{2,32}$/.test(username)) fail(400, 'VALIDATION', '工号须为 2–32 位字母、数字或下划线')
    if (!String(payload.name || '').trim()) fail(400, 'VALIDATION', '请输入姓名')
    if (!departmentById(payload.departmentId)) fail(400, 'VALIDATION', '请选择部门')
    if (!departmentAllowed(scope, actor, payload.departmentId)) fail(403, 'OUT_OF_SCOPE', '只能在管理范围内的部门新增用户')
    if (db.users.some(user => user.username === username)) fail(409, 'DUPLICATE_USERNAME', '工号已存在')
    checkContact(payload)
    await wait(null)
    const user = {
      id: 'u' + String(nextUserNo++).padStart(4, '0'), username, name: payload.name.trim(), departmentId: payload.departmentId,
      position: payload.position || '', phone: payload.phone || '', email: payload.email || '',
      status: 'ENABLED', mustChangePassword: true, roleIds: [], roleVersion: 1
    }
    db.users.push(user)
    passwords.set(user.id, DEMO_PASSWORD)
    return publicUser(user)
  },
  // 既检查原目标，也检查迁入部门；只校验旧部门会允许把人员移出管理边界。
  async updateUser(id, payload) {
    const { actor, target, scope } = userInScope('rbac:user:update', id)
    if (!String(payload.name || '').trim()) fail(400, 'VALIDATION', '请输入姓名')
    if (!departmentById(payload.departmentId)) fail(400, 'VALIDATION', '请选择部门')
    if (!departmentAllowed(scope, actor, payload.departmentId)) fail(403, 'OUT_OF_SCOPE', '不能把用户调到管理范围之外的部门')
    checkContact(payload, target.id)
    await wait(null)
    Object.assign(target, { name: payload.name.trim(), departmentId: payload.departmentId, position: payload.position || '', phone: payload.phone || '', email: payload.email || '' })
    return publicUser(target)
  },
  async setUserStatus(id, status) {
    const { actor, target } = userInScope('rbac:user:update', id)
    if (!['ENABLED', 'DISABLED'].includes(status)) fail(400, 'VALIDATION', '状态无效')
    protectAccount(actor, target, status === 'DISABLED' ? '停用' : '启用')
    await wait(null)
    target.status = status
    return publicUser(target)
  },
  async resetPassword(id) {
    const { actor, target } = userInScope('rbac:user:update', id)
    protectAccount(actor, target, '重置密码')
    await wait(null)
    passwords.set(target.id, DEMO_PASSWORD)
    target.mustChangePassword = true
    return publicUser(target)
  },
  async deleteUser(id) {
    const { actor, target } = userInScope('rbac:user:delete', id)
    protectAccount(actor, target, '删除')
    await wait(null)
    db.users.splice(db.users.indexOf(target), 1)
    passwords.delete(target.id)
    db.departments.forEach(dept => { if (dept.managerId === target.id) dept.managerId = null })
    return { id }
  },
  // 整组替换并带版本号；版本不符返回 409，不做部分写入（GRANT-01）。
  async assignRoles(id, roleIds, version) {
    await waitForWrite()
    const { actor, target } = userInScope('rbac:user:assign-role', id)
    protectAccount(actor, target, '修改角色')
    if (!Array.isArray(roleIds) || roleIds.some(roleId => !roleById(roleId))) fail(400, 'VALIDATION', '请选择有效角色')
    // 重复元素不静默去重，按 API 草案返回 400（测试用例 API-013）。
    if (new Set(roleIds).size !== roleIds.length) fail(400, 'VALIDATION', '角色集合中有重复项')
    const next = [...roleIds]
    if (next.includes(SUPER_ADMIN)) fail(403, 'FORBIDDEN', '超级管理员角色不能授给其他账号')
    // 按差集判断管理角色是否改变；已有管理角色保持不变不构成本次越权新增/撤销。
    const changed = [...next.filter(roleId => !target.roleIds.includes(roleId)), ...target.roleIds.filter(roleId => !next.includes(roleId))]
    const actorIsSuper = actor.roleIds.includes(SUPER_ADMIN) && roleById(SUPER_ADMIN).enabled
    const adminChange = changed.map(roleById).find(role => role.kind === 'admin')
    if (!actorIsSuper && adminChange) fail(403, 'FORBIDDEN', `部门管理员只能分配或撤销业务角色，不能变更“${adminChange.name}”`)
    // 校验完成后同步比较版本并整组替换，中间没有 await，旧版本不能覆盖另一管理员的新结果。
    if (version !== target.roleVersion) fail(409, 'VERSION_CONFLICT', '该用户的角色已被其他管理员修改，请刷新后重试', { currentVersion: target.roleVersion })
    target.roleIds = next
    target.roleVersion += 1
    return publicUser(target)
  },

  // 组织架构（DEPT-01 ~ 02，只读）
  // 从可见根构建子树，直接人数与含下级人数分开统计。
  // 当前 mock 的 SELF 返回空树；SELF 部门成员等精细语义以真实后端验收为准。
  async listDepartments() {
    const { user: actor, scope } = authorize('rbac:department:read')
    const count = id => db.users.filter(user => user.departmentId === id).length
    const build = node => {
      const children = db.departments.filter(item => item.parentId === node.id).map(build)
      return {
        id: node.id, name: node.name, type: node.type, typeLabel: departmentTypes[node.type],
        parentName: departmentById(node.parentId)?.name || '', path: departmentPath(node.id),
        managerName: userById(node.managerId)?.name || '', managerUsername: userById(node.managerId)?.username || '',
        employeeCount: count(node.id), totalCount: children.reduce((sum, child) => sum + child.totalCount, count(node.id)), children
      }
    }
    const roots = scope === 'ALL'
      ? db.departments.filter(item => !item.parentId)
      : scope === 'DEPT' ? [departmentById(actor.departmentId)] : []
    return listResponse(roots.map(build))
  },
  async listDepartmentMembers(departmentId) {
    const { user: actor, scope } = authorize('rbac:department:read')
    if (!departmentById(departmentId) || !departmentAllowed(scope, actor, departmentId)) fail(403, 'OUT_OF_SCOPE', '该部门不在你的查看范围内')
    return wait(db.users.filter(user => isWithinDepartment(user.departmentId, departmentId)).map(user => {
      const { id, username, name, departmentName, position, status } = publicUser(user)
      return { id, username, name, departmentName, position, status }
    }))
  },
  // 下拉选项：部门列表（按指定权限码的范围过滤），供用户表单使用。
  async departmentOptions(code = 'rbac:user:read') {
    const { user: actor, scope } = authorize(code)
    return db.departments.filter(dept => departmentAllowed(scope, actor, dept.id))
      .map(dept => ({ id: dept.id, name: dept.name, path: departmentPath(dept.id), type: dept.type }))
  },
  // 角色管理（ROLE-01 ~ 04）
  async listRoles() {
    authorize('rbac:role:read')
    return listResponse(db.roles.map(role => ({
      id: role.id, code: role.code, name: role.name, kind: role.kind, builtin: role.builtin, description: role.description,
      enabled: role.enabled, permissionVersion: role.permissionVersion, grantCount: role.grants.length,
      userCount: db.users.filter(user => user.roleIds.includes(role.id)).length
    })))
  },
  async createRole({ code, name, description = '' }) {
    await waitForWrite()
    authorize('rbac:role:create')
    const roleCode = String(code || '').trim().toUpperCase()
    if (!/^[A-Z][A-Z0-9_]{1,31}$/.test(roleCode)) fail(400, 'VALIDATION', '角色编码须为大写字母开头的 2–32 位字母、数字或下划线')
    checkRoleInfo(name, description)
    if (db.roles.some(role => role.code === roleCode)) fail(409, 'DUPLICATE_ROLE_CODE', '角色编码已存在')
    const role = { id: 'R' + String(nextRoleNo++).padStart(2, '0'), code: roleCode, name: name.trim(), kind: 'business', builtin: false, description, enabled: true, permissionVersion: 1, grants: [] }
    db.roles.push(role)
    return copy(role)
  },
  async updateRole(id, { name, description = '' }) {
    await waitForWrite()
    authorize('rbac:role:update')
    const role = roleById(id) || fail(404, 'NOT_FOUND', '角色不存在')
    checkRoleInfo(name, description)
    Object.assign(role, { name: name.trim(), description })
    return copy(role)
  },
  async setRoleEnabled(id, enabled) {
    await waitForWrite()
    authorize('rbac:role:update')
    const role = roleById(id) || fail(404, 'NOT_FOUND', '角色不存在')
    if (role.id === SUPER_ADMIN && !enabled) fail(409, 'PROTECTED_ROLE', '超级管理员角色不能停用')
    role.enabled = Boolean(enabled)
    return copy(role)
  },
  // 内置角色拒绝删除；自建角色有持有者时先返回 ROLE_HAS_USERS，二次确认后才清关系。
  // 同步递增受影响用户的 roleVersion，使已打开的旧角色分配草稿在保存时冲突。
  async deleteRole(id, { confirmed = false } = {}) {
    await waitForWrite()
    authorize('rbac:role:delete')
    const role = roleById(id) || fail(404, 'NOT_FOUND', '角色不存在')
    if (role.builtin) fail(409, 'PROTECTED_ROLE', '内置角色不能删除')
    const holders = db.users.filter(user => user.roleIds.includes(id))
    if (holders.length && !confirmed) fail(409, 'ROLE_HAS_USERS', `该角色下还有 ${holders.length} 个用户`, { userCount: holders.length })
    holders.forEach(user => { user.roleIds = user.roleIds.filter(roleId => roleId !== id); user.roleVersion += 1 })
    db.roles.splice(db.roles.indexOf(role), 1)
    return { id }
  },
  // 原型只展示角色关系；这里未做部门范围裁剪，不能用于证明真后端的成员数据隔离。
  async listRoleUsers(id) {
    authorize('rbac:role:read')
    if (!roleById(id)) fail(404, 'NOT_FOUND', '角色不存在')
    return wait(db.users.filter(user => user.roleIds.includes(id)).map(user => {
      const { username, name, departmentName, position, status } = publicUser(user)
      return { id: user.id, username, name, departmentName, position, status }
    }))
  },

  // 角色授权（GRANT-02）：整组替换并带版本号，要么全部成功，要么全部不变。
  async getRoleGrants(id) {
    authorize('rbac:role:read'); authorize('rbac:permission:read')
    const role = roleById(id) || fail(404, 'NOT_FOUND', '角色不存在')
    return wait({ roleId: role.id, version: role.permissionVersion, grants: copy(role.grants), readOnly: role.id === SUPER_ADMIN })
  },
  async saveRoleGrants(id, grants, version, { simulateFailure = false } = {}) {
    await waitForWrite(400)
    authorize('rbac:role:grant')
    const role = roleById(id) || fail(404, 'NOT_FOUND', '角色不存在')
    if (role.id === SUPER_ADMIN) fail(409, 'PROTECTED_ROLE', '超级管理员角色的授权不能修改')
    const known = new Set(catalogPermissions.map(item => item.code))
    // 在临时 Map 中验证，全部通过才替换 role.grants。该 mock 对重复码采用后值覆盖，
    // 不代表真 API 接受重复授权；检查重复码/数据库回滚仍须执行后端测试。
    const byCode = new Map()
    for (const grant of grants || []) {
      if (!known.has(grant.code)) fail(400, 'UNKNOWN_PERMISSION', `未知的权限码：${grant.code}`)
      if (!['ALL', 'DEPT', 'SELF'].includes(grant.scope)) fail(400, 'VALIDATION', `数据范围无效：${grant.code}`)
      byCode.set(grant.code, grant.scope)
    }
    // read 是同一功能点的操作依赖；不是“拥有任意 read 即可”，也不把范围自动扩大。
    for (const code of byCode.keys()) {
      const { systemCode, resource } = parsePermissionCode(code)
      if (!byCode.has(`${systemCode}:${resource}:read`)) fail(400, 'READ_REQUIRED', `${code} 缺少同一功能点的“查看”权限`)
    }
    if (version !== role.permissionVersion) fail(409, 'VERSION_CONFLICT', '该角色的授权已被其他管理员修改，请刷新后重试', { currentVersion: role.permissionVersion })
    // 失败注入放在正式赋值前，仅证明原型失败时内存不变；真实事务回滚由 Java/MySQL 检查证明。
    if (simulateFailure) fail(500, 'SAVE_FAILED', '模拟保存中途失败：事务已回滚，授权与保存前完全相同')
    role.grants = [...byCode].map(([code, scope]) => ({ code, scope })).sort((a, b) => a.code.localeCompare(b.code))
    role.permissionVersion += 1
    return { roleId: role.id, version: role.permissionVersion, grants: copy(role.grants) }
  },

  // 资源目录（CAT-01，只读）
  async listCatalog({ systemCode = '', module = '', keyword = '', page = 1, pageSize = 15 } = {}) {
    authorize('rbac:permission:read')
    const text = keyword.trim().toLowerCase()
    const rows = functions
      .filter(fn => !systemCode || fn.systemCode === systemCode)
      .filter(fn => !module || fn.module === module)
      .filter(fn => !text || `${fn.id} ${fn.name} ${fn.module} ${actions.map(action => `${fn.systemCode}:${fn.id}:${action.code}`).join(' ')}`.toLowerCase().includes(text))
      .map(fn => ({ ...fn, codes: actions.map(action => `${fn.systemCode}:${fn.id}:${action.code}`), admin: adminMapping[fn.id] || null }))
    return listResponse({ ...paginate(rows, page, pageSize), stats: { systems: systems.length, modules: modules.length, functions: functions.length, permissions: catalogPermissions.length } })
  },

  // 仅原型：模拟“另一位管理员已先保存”，用来演示 409 冲突。
  demo: {
    bumpUserRoleVersion(id) { const user = userById(id); if (user) user.roleVersion += 1 },
    bumpRoleGrantVersion(id) { const role = roleById(id); if (role) role.permissionVersion += 1 },
    identities: [
      { username: 'admin', label: '超级管理员', note: '全部功能，管理全部组织' },
      { username: 'SG000001', label: '公司领导', note: '门户按清单使用；管理后台只读' },
      { username: 'SG000011', label: '部门经理', note: '综合办公室；查看全部，管理限本部门' },
      { username: 'SG000281', label: '项目经理', note: '道路工程分公司第一项目经理部' },
      { username: 'SG000013', label: '普通员工', note: '综合办公室；人力资源部分功能点限本人' },
      { username: 'DEMO000100', label: '部门管理员', note: '道路工程分公司及下级部门的用户管理' },
      { username: 'DEMO000101', label: '首次登录', note: '登录后必须先修改密码' },
      { username: 'DEMO000103', label: '无角色账号', note: '可以登录，菜单为空' }
    ]
  }
}

export { functionById, departmentTypes }
