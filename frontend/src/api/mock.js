const users = [
  { id: 'user-001', name: '系统管理员', employeeNo: 'E001', branch: '总公司', departmentId: 'dept-01', departmentName: '办公室', position: '系统管理员', status: 'active', accountType: 'super-admin', phone: '13800000001', roles: ['超级管理员'], permissionCodes: ['rbac:user:read', 'rbac:user:create', 'rbac:role:read', 'rbac:permission:read'] },
  { id: 'user-002', name: '李文', employeeNo: 'E002', branch: '总公司', departmentId: 'dept-02', departmentName: '人事部', position: '部门管理员', status: 'active', accountType: 'department-admin', phone: '13800000002', roles: ['人事部管理员'], permissionCodes: ['rbac:user:read', 'rbac:department:read'] },
  { id: 'user-003', name: '王强', employeeNo: 'E003', branch: '总公司', departmentId: 'dept-03', departmentName: '财务部', position: '会计', status: 'inactive', accountType: 'employee', phone: '13800000003', roles: ['财务查看员'], permissionCodes: ['finance:report:read'] },
  { id: 'user-004', name: '赵敏', employeeNo: 'E004', branch: '总公司', departmentId: 'dept-02', departmentName: '人事部', position: '人事专员', status: 'active', accountType: 'employee', phone: '13800000004', roles: ['普通员工'], permissionCodes: ['hr:self:read'] },
  { id: 'user-005', name: '陈工', employeeNo: 'OPS001', branch: '总公司', departmentId: 'dept-07', departmentName: '工程技术部', position: '临时运维人员', status: 'active', accountType: 'temporary-ops', phone: '13800000005', roles: ['临时运维查看员'], permissionCodes: ['ops:system-status:read', 'ops:log:read'] }
]

const departments = [
  { id: 'dept-root', name: '总公司', type: 'company', manager: '薛峰', branch: '总公司', parentName: '', path: '总公司', employeeCount: 120, children: [
      { id: 'dept-01', name: '办公室', type: 'department', manager: '薛峰', parentName: '总公司', branch: '总公司', path: '总公司 / 办公室', employeeCount: 12, children: [] },
      { id: 'dept-02', name: '人力资源部', type: 'department', manager: '曹瑞', parentName: '总公司', branch: '总公司', path: '总公司 / 人力资源部', employeeCount: 18, children: [] },
      { id: 'dept-03', name: '财务部', type: 'department', manager: '薛福', parentName: '总公司', branch: '总公司', path: '总公司 / 财务部', employeeCount: 21, children: [] },
      { id: 'branch-road', name: '道路工程分公司', type: 'branch', manager: '曾禄', parentName: '总公司', branch: '道路工程分公司', path: '总公司 / 道路工程分公司', employeeCount: 69, children: [
          { id: 'dept-road-office', name: '综合办公室', type: 'department', manager: '卢晓超', parentName: '道路工程分公司', branch: '道路工程分公司', path: '总公司 / 道路工程分公司 / 综合办公室', employeeCount: 16, children: [] },
          { id: 'dept-road-tech', name: '工程技术部', type: 'department', manager: '苏武瑞', parentName: '道路工程分公司', branch: '道路工程分公司', path: '总公司 / 道路工程分公司 / 工程技术部', employeeCount: 53, children: [] }
        ] },
      { id: 'branch-water', name: '水务运营分公司', type: 'branch', manager: '肖志华', parentName: '总公司', branch: '水务运营分公司', path: '总公司 / 水务运营分公司', employeeCount: 44, children: [
          { id: 'dept-water-ops', name: '运营管理部', type: 'department', manager: '陆永', parentName: '水务运营分公司', branch: '水务运营分公司', path: '总公司 / 水务运营分公司 / 运营管理部', employeeCount: 44, children: [] }
        ] }
    ] }
]

function flattenDepartments(nodes, result = []) {
  nodes.forEach(node => { result.push(node); if (node.children?.length) flattenDepartments(node.children, result) })
  return result
}
const departmentOptions = flattenDepartments(departments).filter(item => item.type === 'department')

const roleOptions = [
  { id: 'role-employee', name: '普通员工', permissionCodes: ['hr:self:read'] },
  { id: 'role-department-admin', name: '部门管理员', permissionCodes: ['rbac:user:read', 'rbac:user:create', 'rbac:user:update', 'rbac:user:assign-role', 'rbac:department:read'] },
  { id: 'role-ops-reader', name: '临时运维查看员', permissionCodes: ['ops:system-status:read', 'ops:log:read'] },
  { id: 'role-finance-reader', name: '财务查看员', permissionCodes: ['finance:report:read'] },
  { id: 'role-document-reader', name: '公文查看员', permissionCodes: ['oa:document:read'] },
  { id: 'role-document-editor', name: '公文编辑员', permissionCodes: ['oa:document:read', 'oa:document:edit'] },
  { id: 'role-admin', name: '超级管理员', permissionCodes: ['rbac:user:read', 'rbac:user:create', 'rbac:user:update', 'rbac:user:assign-role', 'rbac:department:read', 'rbac:role:read', 'rbac:permission:read'] }
]


// 成员 3 原始合成样例；演示管理员在共享应用中映射到唯一超级管理员 E001。
export const member3Fixtures = {
  "permissions": [
    {
      "code": "rbac:role:read",
      "system": "权限管理系统",
      "module": "角色维护",
      "name": "查看角色",
      "detail": "查看角色列表及已保存授权"
    },
    {
      "code": "rbac:role:grant",
      "system": "权限管理系统",
      "module": "角色维护",
      "name": "修改角色授权",
      "detail": "保存或撤销角色拥有的功能权限"
    },
    {
      "code": "rbac:permission:read",
      "system": "权限管理系统",
      "module": "资源目录",
      "name": "查看资源目录",
      "detail": "查询系统、模块与功能权限"
    },
    {
      "code": "rbac:user:read",
      "system": "权限管理系统",
      "module": "用户角色",
      "name": "查看用户",
      "detail": "查看用户信息与角色分配"
    },
    {
      "code": "rbac:user:assign-role",
      "system": "权限管理系统",
      "module": "用户角色",
      "name": "分配用户角色",
      "detail": "完整替换用户的角色列表"
    },
    {
      "code": "oa:document:read",
      "system": "办公协同系统",
      "module": "文档管理",
      "name": "查看文档",
      "detail": "访问文档列表"
    },
    {
      "code": "oa:document:create",
      "system": "办公协同系统",
      "module": "文档管理",
      "name": "新建文档",
      "detail": "创建一份演示文档"
    },
    {
      "code": "oa:document:edit",
      "system": "办公协同系统",
      "module": "文档管理",
      "name": "编辑文档",
      "detail": "将文档调整为修改草稿"
    },
    {
      "code": "oa:document:approve",
      "system": "办公协同系统",
      "module": "文档审批",
      "name": "审批文档",
      "detail": "将演示文档标记为已审批"
    },
    {
      "code": "hr:self:read",
      "system": "人事信息系统",
      "module": "个人信息",
      "name": "查看个人信息",
      "detail": "访问当前登录用户的基本信息"
    }
  ],
  "roles": [
    {
      "id": "DEMO-role-admin",
      "name": "演示管理员",
      "description": "维护授权与用户角色，具有全部样例权限。",
      "permissionCodes": [
        "rbac:role:read",
        "rbac:role:grant",
        "rbac:permission:read",
        "rbac:user:read",
        "rbac:user:assign-role",
        "oa:document:read",
        "oa:document:create",
        "oa:document:edit",
        "oa:document:approve",
        "hr:self:read"
      ],
      "version": 1
    },
    {
      "id": "DEMO-role-clerk",
      "name": "演示文档编辑员",
      "description": "可查看、新建、编辑文档，不具有审批权限。",
      "permissionCodes": [
        "oa:document:read",
        "oa:document:create",
        "oa:document:edit",
        "hr:self:read"
      ],
      "version": 1
    },
    {
      "id": "DEMO-role-approver",
      "name": "演示文档审批员",
      "description": "可查看和审批文档，不具有编辑权限。",
      "permissionCodes": [
        "oa:document:read",
        "oa:document:approve",
        "hr:self:read"
      ],
      "version": 1
    },
    {
      "id": "DEMO-role-employee",
      "name": "演示普通员工",
      "description": "可查看文档与本人基本信息。",
      "permissionCodes": [
        "oa:document:read",
        "hr:self:read"
      ],
      "version": 1
    }
  ],
  "users": [
    {
      "id": "DEMO-user-admin",
      "name": "演示管理员",
      "department": "演示信息中心",
      "roleIds": [
        "DEMO-role-admin"
      ],
      "version": 1
    },
    {
      "id": "DEMO-user-employee",
      "name": "演示普通员工",
      "department": "演示工程一部",
      "roleIds": [
        "DEMO-role-employee"
      ],
      "version": 1
    },
    {
      "id": "DEMO-user-clerk",
      "name": "演示文档编辑员",
      "department": "演示办公室",
      "roleIds": [
        "DEMO-role-clerk"
      ],
      "version": 1
    },
    {
      "id": "DEMO-user-approver",
      "name": "演示文档审批员",
      "department": "演示工程二部",
      "roleIds": [
        "DEMO-role-approver"
      ],
      "version": 1
    }
  ],
  "documents": [
    {
      "id": "DEMO-doc-001",
      "title": "道路养护周报（演示）",
      "department": "演示工程一部",
      "status": "待审批"
    },
    {
      "id": "DEMO-doc-002",
      "title": "设施巡检记录（演示）",
      "department": "演示工程二部",
      "status": "草稿"
    },
    {
      "id": "DEMO-doc-003",
      "title": "施工会议纪要（演示）",
      "department": "演示办公室",
      "status": "已审批"
    }
  ],
  "routes": [
    {
      "path": "/roles",
      "title": "角色授权",
      "icon": "◈",
      "group": "management",
      "requiredPermissions": [
        "rbac:role:read",
        "rbac:permission:read"
      ],
      "match": "all"
    },
    {
      "path": "/permissions",
      "title": "资源目录",
      "icon": "▤",
      "group": "management",
      "requiredPermissions": [
        "rbac:permission:read"
      ]
    },
    {
      "path": "/assignments",
      "title": "用户角色分配",
      "icon": "⇄",
      "group": "management",
      "requiredPermissions": [
        "rbac:user:read",
        "rbac:role:read"
      ],
      "match": "all"
    },
    {
      "path": "/documents",
      "title": "文档管理",
      "icon": "▣",
      "group": "business",
      "requiredPermissions": [
        "oa:document:read"
      ]
    },
    {
      "path": "/self",
      "title": "个人信息",
      "icon": "○",
      "group": "business",
      "requiredPermissions": [
        "hr:self:read"
      ]
    }
  ]
}
const copy = value => JSON.parse(JSON.stringify(value))
const permissionCatalog = [...member3Fixtures.permissions]
for (const role of roleOptions) {
  for (const code of role.permissionCodes) {
    if (!permissionCatalog.some(item => item.code === code)) permissionCatalog.push({ code, system: '权限管理系统', module: '其他功能', name: code, detail: '现有原型权限，名称待团队确认' })
  }
  role.description ||= role.name
  role.version = 1
}
roleOptions.push(...copy(member3Fixtures.roles.filter(role => role.id !== 'DEMO-role-admin')))
const adminRole = roleOptions.find(role => role.id === 'role-admin')
adminRole.permissionCodes = [...new Set([...adminRole.permissionCodes, ...member3Fixtures.permissions.map(item => item.code)])]
const sampleDepartments = ['dept-road-tech', 'dept-01', 'dept-water-ops']
member3Fixtures.users.filter(user => user.id !== 'DEMO-user-admin').forEach((sample, index) => {
  const department = departmentOptions.find(item => item.id === sampleDepartments[index])
  users.push({ ...copy(sample), employeeNo: 'DEMO00' + (index + 1), phone: '1390000000' + (index + 1), branch: department.branch, departmentId: department.id, departmentName: department.name, position: '演示岗位', status: 'active', accountType: 'employee', roles: [], permissionCodes: [] })
})
const documents = copy(member3Fixtures.documents)
export const member3UserIdMap = { 'DEMO-user-admin': 'user-001', 'DEMO-user-employee': 'DEMO-user-employee', 'DEMO-user-clerk': 'DEMO-user-clerk', 'DEMO-user-approver': 'DEMO-user-approver' }
function refreshUserPermissions(user) {
  const assigned = roleOptions.filter(role => user.roleIds.includes(role.id))
  user.roles = assigned.map(role => role.name)
  user.permissionCodes = [...new Set(assigned.flatMap(role => role.permissionCodes))].sort()
}

// 演示密码仅保存在本次页面运行内存中，不存入用户列表或浏览器存储。
const passwords = new Map(users.map(user => [user.id, '123456']))
let currentUser = null
const publicUser = user => ({ ...user, roleIds: [...user.roleIds], roles: [...user.roles], permissionCodes: [...user.permissionCodes] })
function inScope(user) { return currentUser?.roleIds.includes('role-admin') || user.departmentId === currentUser?.departmentId }
function requirePermission(code) {
  if (!currentUser || currentUser.status !== 'active') throw new Error('请重新登录')
  if (!currentUser.permissionCodes.includes(code)) throw new Error('没有执行此操作的权限')
}
users.forEach(user => {
  user.roleIds = user.roleIds || (user.accountType === 'super-admin' ? ['role-admin'] : user.accountType === 'department-admin' ? ['role-department-admin'] : user.accountType === 'temporary-ops' ? ['role-ops-reader'] : user.id === 'user-003' ? ['role-finance-reader'] : ['role-employee'])
  if (user.id === 'user-002') user.roleIds.push('role-employee')
  user.accountType = user.accountType === 'temporary-ops' ? 'temporary-ops' : 'employee'
  if (user.id === 'user-001') user.position = '信息技术专员'
  if (user.id === 'user-002') user.position = '人事专员'
  if (user.id === 'user-005') user.position = '运维工程师'
  const roles = roleOptions.filter(role => user.roleIds.includes(role.id))
  user.roles = roles.map(role => role.name)
  user.permissionCodes = [...new Set(roles.flatMap(role => role.permissionCodes))]
  if (user.departmentId === 'dept-02') user.departmentName = '人力资源部'
  if (user.id === 'user-005') { user.departmentId = 'dept-road-tech'; user.branch = '道路工程分公司' }
})

export function demoMode() {
  return new URLSearchParams(window.location.search).get('demo') || 'normal'
}

const wait = (value, ms = 350) => new Promise(resolve => setTimeout(() => resolve(value), ms))
const demoResponse = async (value) => {
  const mode = demoMode()
  if (mode === 'error') { await wait(null, 350); throw new Error('演示请求失败') }
  if (mode === 'loading') return wait(value, 1800)
  if (mode === 'empty') return wait(Array.isArray(value) ? [] : { items: [], total: 0 })
  return wait(value)
}
export const api = {
  async login(account, password) {
    await wait(null)
    const user = users.find(item => item.employeeNo === account.trim() || item.phone === account.trim())
    if (!user || passwords.get(user.id) !== password) throw new Error('员工号或手机号不存在，或密码错误')
    if (user.status !== 'active') throw new Error('账号已停用，请联系管理员')
    if (!user.roleIds.some(id => ['role-admin', 'role-department-admin'].includes(id))) throw new Error('当前账号无权限管理系统登录资格，请使用对应业务系统')
    currentUser = user
    return publicUser(user)
  },
  logout() { currentUser = null },
  async getOverview() { requirePermission('rbac:user:read'); const visible = users.filter(inScope); return demoResponse({ totalUsers: visible.length, temporaryUsers: visible.filter(user => user.accountType === 'temporary-ops').length, totalRoles: currentUser.roleIds.includes('role-admin') ? roleOptions.length : new Set(visible.flatMap(user => user.roleIds)).size }) },
  async listUsers() { requirePermission('rbac:user:read'); const visible = users.filter(inScope); return demoResponse({ items: visible.map(publicUser), total: visible.length }) },
  async createUser(payload) {
    requirePermission('rbac:user:create')
    if (!inScope(payload)) throw new Error('只能创建本部门账号')
    if (typeof payload.initialPassword !== 'string' || payload.initialPassword.length < 6 || payload.initialPassword.length > 64) throw new Error('初始密码须为 6–64 位')
    if (payload.roleIds?.includes('role-admin')) throw new Error('系统只保留一个超级管理员账号，不能新增或授予此角色')
    if (!['employee', 'temporary-ops'].includes(payload.accountType)) throw new Error('请选择员工账号或临时运维账号')
    if (users.some(item => item.employeeNo === payload.employeeNo)) throw new Error('员工号已存在，请使用唯一员工号')
    if (users.some(item => item.phone === payload.phone)) throw new Error('手机号已存在，请使用唯一手机号')
    if (!Array.isArray(payload.roleIds) || payload.roleIds.some(id => !roleOptions.some(role => role.id === id))) throw new Error('请选择有效角色')
    const roleIds = [...new Set(payload.roleIds)]
    const assignedRoles = roleOptions.filter(role => roleIds.includes(role.id))
    const { initialPassword, ...fields } = payload
    const created = { ...fields, status: fields.status || 'active', roleIds, id: `user-${String(users.length + 1).padStart(3, '0')}`, roles: assignedRoles.map(role => role.name), permissionCodes: [...new Set(assignedRoles.flatMap(role => role.permissionCodes))].sort() }
    const response = await demoResponse({ ...created })
    users.push(created)
    passwords.set(created.id, initialPassword)
    return response
  },
  async updateUserStatus(id, status) {
    requirePermission('rbac:user:update')
    const user = users.find(item => item.id === id)
    if (!user) throw new Error('用户不存在')
    if (user.roleIds.includes('role-admin')) throw new Error('唯一超级管理员账号不能停用')
    if (!inScope(user)) throw new Error('不能修改其他部门账号')
    if (user.id === currentUser.id) throw new Error('不能停用当前登录账号')
    const response = await demoResponse({ ...user, status })
    user.status = status
    return response
  },
  async updateUserRoles(id, roleIds) {
    requirePermission('rbac:user:assign-role')
    const user = users.find(item => item.id === id)
    if (!user) throw new Error('用户不存在')
    if (user.roleIds.includes('role-admin')) throw new Error('超级管理员角色不可修改')
    if (user.id === currentUser.id) throw new Error('不能修改当前登录账号的角色')
    if (!inScope(user)) throw new Error('不能修改其他部门账号')
    if (!Array.isArray(roleIds) || roleIds.some(roleId => !roleOptions.some(role => role.id === roleId) || roleId === 'role-admin')) throw new Error('请选择有效角色')
    const nextRoleIds = [...new Set(roleIds)]
    const assignedRoles = roleOptions.filter(role => nextRoleIds.includes(role.id))
    const updated = { roleIds: nextRoleIds, roles: assignedRoles.map(role => role.name), permissionCodes: [...new Set(assignedRoles.flatMap(role => role.permissionCodes))].sort() }
    const response = await demoResponse({ ...publicUser(user), ...updated })
    Object.assign(user, updated)
    return response
  },
  async getCurrentUser() {
    if (!currentUser || currentUser.status !== 'active') throw new Error('请重新登录')
    refreshUserPermissions(currentUser)
    return demoResponse(publicUser(currentUser))
  },
  async listRoles() { requirePermission('rbac:role:read'); return demoResponse(copy(roleOptions)) },
  async listPermissions() { requirePermission('rbac:permission:read'); return demoResponse(copy(permissionCatalog)) },
  async listDocuments() { requirePermission('oa:document:read'); return demoResponse(copy(documents)) },
  async updateRolePermissions(id, permissionCodes, version) {
    requirePermission('rbac:role:grant')
    const role = roleOptions.find(item => item.id === id)
    if (!role) throw new Error('角色不存在')
    if (id === 'role-admin') throw new Error('内置超级管理员角色不可修改')
    if (!Array.isArray(permissionCodes) || permissionCodes.some(code => !permissionCatalog.some(item => item.code === code))) throw new Error('请选择有效权限')
    if (demoMode() === 'conflict' || (version !== undefined && role.version !== version)) throw new Error('版本冲突，请重新加载角色')
    const updated = { ...role, permissionCodes: [...new Set(permissionCodes)].sort(), version: role.version + 1 }
    const response = await demoResponse(copy(updated))
    if (role.version !== updated.version - 1) throw new Error('版本冲突，请重新加载角色')
    Object.assign(role, updated)
    users.forEach(refreshUserPermissions)
    return response
  },
  async listDepartments() { requirePermission('rbac:department:read'); return demoResponse(currentUser.roleIds.includes('role-admin') ? departments : departmentOptions.filter(item => item.id === currentUser.departmentId)) },
  roleOptions: roleOptions.filter(role => role.id !== 'role-admin'),
  departmentOptions
}
