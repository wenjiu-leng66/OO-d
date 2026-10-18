// 合成演示数据：组织、账号、角色与授权。仓库公开，这里不出现真实姓名、电话或清单原始授权。
// 演示账号沿用需求规格 3 章验收用的账号名（admin、SG000001 等），姓名一律为“演示××”。
// 授权按规则合成，只保证需求规格验收标准涉及的格子（员工台账、考勤管理、薪酬核算等）与规格一致。
import { functions, actions, permissionCode } from './catalog.js'

export const departmentTypes = {
  HQ: '总公司', HQ_DEPT: '总部部门', BRANCH: '分公司', BRANCH_DEPT: '分公司下属部门', PROJECT: '项目经理部'
}

// [编号, 名称, 类型, 上级编号, 负责人用户编号]
const departmentRows = [
  ['D01', '总公司', 'HQ', null, 'u0002'],
  ['D02', '公司领导', 'HQ_DEPT', 'D01', 'u0002'],
  ['D03', '综合办公室', 'HQ_DEPT', 'D01', 'u0003'],
  ['D04', '人力资源部', 'HQ_DEPT', 'D01', 'u0011'],
  ['D05', '财务部', 'HQ_DEPT', 'D01', 'u0013'],
  ['D06', '工程管理部', 'HQ_DEPT', 'D01', 'u0015'],
  ['D10', '道路工程分公司', 'BRANCH', 'D01', 'u0017'],
  ['D11', '道路工程分公司-综合办公室', 'BRANCH_DEPT', 'D10', 'u0019'],
  ['D12', '道路工程分公司-财务部', 'BRANCH_DEPT', 'D10', 'u0021'],
  ['D13', '道路工程分公司-工程技术部', 'BRANCH_DEPT', 'D10', 'u0023'],
  ['D14', '道路工程分公司第一项目经理部', 'PROJECT', 'D10', 'u0005'],
  ['D15', '道路工程分公司第二项目经理部', 'PROJECT', 'D10', 'u0026'],
  ['D20', '水务运营分公司', 'BRANCH', 'D01', 'u0028'],
  ['D21', '水务运营分公司-运营管理部', 'BRANCH_DEPT', 'D20', 'u0030'],
  ['D22', '水务运营分公司-安全质量部', 'BRANCH_DEPT', 'D20', 'u0032'],
  ['D23', '水务运营分公司第一项目经理部', 'PROJECT', 'D20', 'u0034']
]
export const departments = departmentRows.map(([id, name, type, parentId, managerId]) => ({ id, name, type, parentId, managerId }))

// 角色：6 个内置角色 + 自建能力角色。kind 区分管理类与业务角色（GRANT-03）。
export const roleRows = [
  ['R01', 'ADMIN', '超级管理员', 'admin', true, '清单“系统管理员”列。系统只预置一个账号 admin'],
  ['R02', 'LEADER', '公司领导', 'business', true, '清单“公司领导”列。管理后台只读，日志可导出'],
  ['R03', 'DEPT_MANAGER', '部门经理', 'business', true, '清单“部门经理”列。查看为全部，管理类操作限本部门'],
  ['R04', 'PROJECT_MANAGER', '项目经理', 'business', true, '清单“项目经理”列。查看为全部，管理类操作限本项目部'],
  ['R05', 'STAFF', '普通员工', 'business', true, '清单“普通员工”列。人力资源系统部分功能点限本人'],
  ['R06', 'DEPT_ADMIN', '部门管理员', 'admin', true, '平台为分级管理而设：管理本部门及下级的用户，只能分配业务角色'],
  ['R07', 'LEDGER_KEEPER', '台账维护员', 'business', false, '自建能力角色（演示 GRANT-04）：员工台账查看=本人、编辑=全部'],
  ['R08', 'PAY_HANDLER', '付款经办', 'business', false, '自建能力角色，阶段三静态互斥样例（与付款复核互斥）'],
  ['R09', 'PAY_REVIEWER', '付款复核', 'business', false, '自建能力角色，阶段三静态互斥样例（与付款经办互斥）']
]

const MANAGE = ['create', 'edit', 'approve', 'delete']
const APPROVE_NAME = /审批|审核|签发|审定|评审|复核/
const CREATE_NAME = /申请|发起|预约|登记|上报|提交/

function grantsBy(rule) {
  const result = []
  for (const fn of functions) {
    for (const action of actions) {
      const scope = rule(fn, action.code)
      if (scope) result.push({ code: permissionCode(fn, action.code), scope })
    }
  }
  return result
}

const inSystems = (fn, list) => list.includes(fn.systemCode)
const business = fn => fn.systemCode !== 'sys'

// 普通员工在人力资源系统中限本人的格子（需求规格 5.3、5.4）：查看/编辑本人 与 查看本人。
const STAFF_SELF_EDIT = ['f046', 'f052', 'f053']
const STAFF_SELF_READ = ['f055', 'f056', 'f060']

export const roleGrants = {
  R01: grantsBy(() => 'ALL'),
  R02: grantsBy((fn, action) => {
    if (fn.systemCode === 'sys') return action === 'read' || (action === 'export' && ['f348', 'f349'].includes(fn.id)) ? 'ALL' : null
    if (action === 'read' || action === 'export') return 'ALL'
    return action === 'approve' && APPROVE_NAME.test(fn.name) ? 'ALL' : null
  }),
  R03: grantsBy((fn, action) => {
    if (!business(fn) || !inSystems(fn, ['oa', 'im', 'kb', 'pub', 'arch', 'hr', 'fin', 'cost', 'contract', 'veh', 'safety'])) return null
    if (action === 'read' || action === 'export') return 'ALL'
    if (inSystems(fn, ['oa', 'hr', 'veh'])) return 'DEPT'
    return action === 'approve' && APPROVE_NAME.test(fn.name) ? 'DEPT' : null
  }),
  R04: grantsBy((fn, action) => {
    const projectSystems = ['pmis', 'bid', 'mat', 'insp', 'video', 'gis', 'safety', 'contract', 'cost']
    const hrItems = ['f046', 'f052', 'f053']
    if (!business(fn)) return null
    if (inSystems(fn, [...projectSystems, 'oa', 'im', 'kb']) || hrItems.includes(fn.id)) {
      if (action === 'read' || action === 'export') return 'ALL'
      if (MANAGE.includes(action) && (inSystems(fn, ['pmis', 'mat', 'insp', 'video', 'safety']) || (fn.id === 'f046' && action === 'edit'))) return 'DEPT'
    }
    return null
  }),
  R05: grantsBy((fn, action) => {
    if (STAFF_SELF_EDIT.includes(fn.id)) return ['read', 'edit'].includes(action) ? 'SELF' : null
    if (STAFF_SELF_READ.includes(fn.id)) return action === 'read' ? 'SELF' : null
    if (!inSystems(fn, ['oa', 'im', 'kb', 'pub'])) return null
    if (action === 'read') return 'ALL'
    return action === 'create' && CREATE_NAME.test(fn.name) ? 'ALL' : null
  }),
  // PR #13 中部门管理员内置角色没有授权；原型按“管理本部门及下级用户”提议以下授权，待接口设计确认。
  R06: [
    ...['read', 'create', 'edit', 'delete'].map(action => ({ code: `sys:f339:${action}`, scope: 'DEPT' })),
    { code: 'sys:f340:read', scope: 'ALL' },
    { code: 'sys:f342:read', scope: 'DEPT' }
  ],
  R07: [{ code: 'hr:f046:read', scope: 'SELF' }, { code: 'hr:f046:edit', scope: 'ALL' }],
  R08: [{ code: 'fin:f072:read', scope: 'ALL' }, { code: 'fin:f072:create', scope: 'ALL' }],
  R09: [{ code: 'fin:f072:read', scope: 'ALL' }, { code: 'fin:f072:approve', scope: 'ALL' }]
}

// [编号, 账号, 姓名, 部门, 岗位, 角色, 额外字段]
const fixedUsers = [
  ['u0001', 'admin', '系统管理员', 'D01', '系统管理员', ['R01'], { phone: '' }],
  ['u0002', 'SG000001', '演示董事长', 'D02', '董事长', ['R02']],
  ['u0003', 'SG000011', '演示主任', 'D03', '综合办公室主任', ['R03']],
  ['u0004', 'SG000013', '演示干事', 'D03', '综合办公室干事', ['R05']],
  ['u0005', 'SG000281', '演示项目经理', 'D14', '项目经理', ['R04']],
  ['u0006', 'DEMO000100', '演示部门管理员', 'D10', '人事专员', ['R05', 'R06']],
  ['u0007', 'DEMO000101', '演示新员工', 'D13', '技术员', ['R05'], { mustChangePassword: true }],
  ['u0008', 'DEMO000102', '演示停用账号', 'D12', '会计', ['R05'], { status: 'DISABLED' }],
  ['u0009', 'DEMO000103', '演示无角色', 'D21', '运营专员', []]
]

// 其余合成员工：[部门, 岗位, 角色]。
const fillerUsers = [
  ['D03', '文秘', ['R05']], ['D04', '人力资源部部长', ['R03']], ['D04', '人事专员', ['R05']],
  ['D05', '财务部部长', ['R03']], ['D05', '会计', ['R05']], ['D06', '工程管理部部长', ['R03']],
  ['D06', '工程师', ['R05']], ['D10', '分公司经理', ['R03']], ['D10', '分公司副经理', ['R05']],
  ['D11', '主任', ['R03']], ['D11', '干事', ['R05']], ['D12', '部长', ['R03']],
  ['D12', '出纳', ['R05']], ['D13', '部长', ['R03']], ['D13', '工程师', ['R05']],
  ['D14', '技术员', ['R05']], ['D15', '项目经理', ['R04']], ['D15', '项目副经理', ['R05']],
  ['D20', '分公司经理', ['R03']], ['D20', '经理助理', ['R05']], ['D21', '部长', ['R03']],
  ['D21', '运营专员', ['R05']], ['D22', '部长', ['R03']], ['D22', '安全员', ['R05']],
  ['D23', '项目经理', ['R04']], ['D23', '施工员', ['R05']], ['D14', '施工员', ['R05']],
  ['D02', '副总经理', ['R02']], ['D03', '副主任', ['R05']], ['D13', '技术员', ['R05']]
]

export function createUsers() {
  const rows = fixedUsers.map(([id, username, name, departmentId, position, roleIds, extra = {}]) => ({
    id, username, name, departmentId, position, roleIds,
    phone: username === 'admin' ? '' : '1380000' + username.slice(-4), ...extra
  }))
  fillerUsers.forEach(([departmentId, position, roleIds], index) => {
    const no = String(index + 1).padStart(2, '0')
    rows.push({ id: 'u' + String(index + 10).padStart(4, '0'), username: 'DEMO0000' + no, name: '演示员工' + no, departmentId, position, roleIds, phone: '139000000' + no })
  })
  return rows.map(user => ({
    status: 'ENABLED', mustChangePassword: false, email: '', roleVersion: 1, ...user,
    email: user.email || (user.id.endsWith('2') ? `${user.username.toLowerCase()}@example.com` : '')
  }))
}
