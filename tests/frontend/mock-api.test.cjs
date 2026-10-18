'use strict';

// 界面原型的模拟后端按需求规格第 3 章验收标准运行。测试按顺序共享同一份内存数据。
const test = require('node:test');
const assert = require('node:assert/strict');

globalThis.Rbac0 = require('../../frontend/src/authorization/rbac0-policy.js');
const loaded = import('../../frontend/src/api/mock.js');
const PASSWORD = '123456';
let api;

test.before(async () => { ({ api } = await loaded); });
const as = username => api.login(username, PASSWORD);
const scopeOf = (me, code) => me.scopes[code] || null;

test('未登录访问受保护接口返回 401（AUTHZ-01、T-01）', async () => {
  api.logout();
  await assert.rejects(api.listUsers(), { status: 401, code: 'UNAUTHENTICATED' });
});

test('四种登录失败提示完全相同；工号和手机号都能登录同一账号（LOGIN-01）', async () => {
  const messages = [];
  for (const [account, password] of [['NOBODY', PASSWORD], ['SG000013', 'wrong'], ['DEMO000102', PASSWORD], ['', PASSWORD]]) {
    await assert.rejects(api.login(account, password), error => { messages.push(error.message); return error.status === 401; });
  }
  assert.equal(new Set(messages).size, 1);
  assert.equal(messages[0], '用户名或密码错误');
  const byPhone = await api.login('13800000013', PASSWORD);
  const byNo = await as('SG000013');
  assert.equal(byPhone.user.id, byNo.user.id);
});

test('资源目录：18 个系统、115 个模块、359 个功能点、2154 条权限（CAT-01）', async () => {
  await as('admin');
  const { stats } = await api.listCatalog();
  assert.deepEqual(stats, { systems: 18, modules: 115, functions: 359, permissions: 2154 });
});

test('普通员工菜单没有系统管理后台；考勤管理只有查看、编辑且为本人（PORTAL-01、PORTAL-02）', async () => {
  const me = await as('SG000013');
  assert.equal(me.permissions.some(code => code.startsWith('sys:') || code.startsWith('rbac:')), false);
  const attendance = ['read', 'create', 'edit', 'approve', 'export', 'delete'].map(action => scopeOf(me, `hr:f052:${action}`));
  assert.deepEqual(attendance, ['SELF', null, 'SELF', null, null, null]);
  await assert.rejects(api.listUsers(), { status: 403 });
});

test('员工台账：普通员工只看到自己；部门经理看到全部（PORTAL-04）', async () => {
  const staff = await as('SG000013');
  const own = await api.listEmployees({ pageSize: 100 });
  assert.deepEqual(own.items.map(item => item.id), [staff.user.id]);
  assert.equal(own.items[0].editable, true);
  await as('SG000011');
  const all = await api.listEmployees({ pageSize: 100 });
  const admin = await as('admin');
  const users = await api.listUsers({ pageSize: 100 });
  assert.equal(all.total, users.total);
  assert.equal(admin.user.username, 'admin');
});

test('员工台账编辑：本部门可改，范围外和非法字段被拒绝且不返回记录（PORTAL-05、T-09）', async () => {
  await as('SG000011');
  const list = await api.listEmployees({ pageSize: 100 });
  const colleague = list.items.find(item => item.departmentName === '综合办公室' && item.username !== 'SG000011');
  const outsider = list.items.find(item => !item.departmentPath.includes('综合办公室'));
  assert.equal(colleague.editable, true);
  assert.equal(outsider.editable, false);
  const saved = await api.updateEmployeeContact(colleague.id, { phone: '13800007777', email: 'office@example.com' });
  assert.equal(saved.phone, '13800007777');
  await assert.rejects(api.updateEmployeeContact(outsider.id, { phone: '13800006666' }), error => {
    assert.equal(error.status, 403);
    assert.equal(error.details, undefined);
    assert.equal(error.message.includes(outsider.name), false);
    return true;
  });
  await assert.rejects(api.updateEmployeeContact('u9999', { phone: '13800006666' }), { status: 403, code: 'OUT_OF_SCOPE' });
  await assert.rejects(api.updateEmployeeContact(colleague.id, { name: '改名' }), { status: 400, code: 'FIELD_NOT_EDITABLE' });
});

test('项目经理：员工台账查看为全部，编辑只限本项目部（需求规格 5.4）', async () => {
  await as('SG000281');
  const list = await api.listEmployees({ pageSize: 100 });
  const editable = list.items.filter(item => item.editable);
  assert.equal(list.readScope, 'ALL');
  assert.equal(list.editScope, 'DEPT');
  assert.equal(editable.length, 3);
  assert.ok(editable.every(item => item.departmentName === '道路工程分公司第一项目经理部'));
});

test('公司领导能打开用户管理，但新增、编辑、删除返回 403（CAT-03）', async () => {
  const me = await as('SG000001');
  assert.equal(scopeOf(me, 'rbac:user:read'), 'ALL');
  for (const code of ['rbac:user:create', 'rbac:user:update', 'rbac:user:delete', 'rbac:role:grant']) assert.equal(scopeOf(me, code), null);
  const list = await api.listUsers();
  assert.ok(list.total > 0);
  await assert.rejects(api.createUser({ username: 'X01', name: '演示', departmentId: 'D03' }), { status: 403 });
  await assert.rejects(api.deleteUser(list.items[1].id), { status: 403 });
});

test('部门管理员只看到本部门及下级，改筛选参数也看不到范围外（USER-01）', async () => {
  const me = await as('DEMO000100');
  assert.equal(scopeOf(me, 'rbac:user:read'), 'DEPT');
  const mine = await api.listUsers({ pageSize: 100 });
  assert.ok(mine.items.length > 0);
  assert.ok(mine.items.every(user => user.departmentPath.startsWith('总公司 / 道路工程分公司')));
  assert.equal((await api.listUsers({ departmentId: 'D03', pageSize: 100 })).total, 0);
  await assert.rejects(api.getUser('u0004'), { status: 403 });
  await assert.rejects(api.updateUser(mine.items.find(user => user.id !== me.user.id).id, { name: '演示', departmentId: 'D03' }), { status: 403 });
});

test('部门管理员不能分配管理类角色、不能给范围外用户分配、不能改授权（GRANT-03）', async () => {
  await as('DEMO000100');
  const target = (await api.listUsers({ departmentId: 'D13', pageSize: 100 })).items[0];
  await assert.rejects(api.assignRoles(target.id, [...target.roleIds, 'R06'], target.roleVersion), { status: 403 });
  await assert.rejects(api.assignRoles('u0004', ['R05'], 1), { status: 403 });
  await assert.rejects(api.saveRoleGrants('R05', [], 1), { status: 403 });
  const updated = await api.assignRoles(target.id, [...target.roleIds, 'R07'], target.roleVersion);
  assert.ok(updated.roleIds.includes('R07'));
  assert.equal(updated.roleVersion, target.roleVersion + 1);
});

test('分配角色整组替换并带版本号；版本不符返回 409 且不做部分写入（GRANT-01）', async () => {
  await as('admin');
  const before = await api.getUser('u0004');
  api.demo.bumpUserRoleVersion('u0004');
  await assert.rejects(api.assignRoles('u0004', ['R05', 'R07'], before.roleVersion), { status: 409, code: 'VERSION_CONFLICT' });
  const after = await api.getUser('u0004');
  assert.deepEqual(after.roleIds, before.roleIds);
  assert.equal(after.roleVersion, before.roleVersion + 1);
});

test('角色集合含重复项返回 400 且不做任何写入（API-013）', async () => {
  await as('admin');
  const before = await api.getUser('u0004');
  await assert.rejects(api.assignRoles('u0004', ['R05', 'R05'], before.roleVersion), { status: 400, code: 'VALIDATION' });
  const after = await api.getUser('u0004');
  assert.deepEqual(after.roleIds, before.roleIds);
  assert.equal(after.roleVersion, before.roleVersion);
});

test('角色编码与表结构一致，分页字段为 page / pageSize', async () => {
  await as('admin');
  const roles = await api.listRoles();
  assert.equal(roles.find(role => role.id === 'R01').code, 'ADMIN');
  const page = await api.listUsers({ page: 1, pageSize: 5 });
  assert.equal(page.pageSize, 5);
  assert.ok(page.items.length <= 5);
});

test('多角色取最大范围、授权变更立即生效（GRANT-04、GRANT-05）', async () => {
  await as('admin');
  const user = await api.getUser('u0004');
  await api.assignRoles('u0004', ['R05', 'R07'], user.roleVersion);
  let me = await as('SG000013');
  assert.equal(scopeOf(me, 'hr:f046:edit'), 'ALL');
  assert.equal(scopeOf(me, 'hr:f046:read'), 'SELF');
  await as('admin');
  await api.assignRoles('u0004', [], (await api.getUser('u0004')).roleVersion);
  me = await as('SG000013');
  assert.deepEqual(me.permissions, []);
  await assert.rejects(api.listEmployees(), { status: 403 });
  await as('admin');
  await api.assignRoles('u0004', ['R05'], (await api.getUser('u0004')).roleVersion);
});

test('账号保护：不能停用、删除自己；唯一超级管理员不能被修改角色（USER-07）', async () => {
  const admin = await as('admin');
  await assert.rejects(api.setUserStatus(admin.user.id, 'DISABLED'), { status: 409, code: 'PROTECTED_ACCOUNT' });
  await assert.rejects(api.deleteUser(admin.user.id), { status: 409 });
  const deptAdmin = await as('DEMO000100');
  await assert.rejects(api.assignRoles(deptAdmin.user.id, ['R05'], deptAdmin.user.roleVersion), { status: 409 });
  await as('admin');
  await assert.rejects(api.assignRoles('u0003', ['R03', 'R01'], 1), { status: 403 });
});

test('新增用户：工号唯一、手机号非空时唯一，首次登录须改密码（USER-02、LOGIN-05）', async () => {
  await as('admin');
  await assert.rejects(api.createUser({ username: 'SG000013', name: '演示', departmentId: 'D03' }), { status: 409, code: 'DUPLICATE_USERNAME' });
  await assert.rejects(api.createUser({ username: 'DEMO009001', name: '演示', departmentId: 'D03', phone: '13800000011' }), { status: 409, code: 'DUPLICATE_PHONE' });
  const created = await api.createUser({ username: 'DEMO009001', name: '演示新增', departmentId: 'D03', phone: '' });
  assert.equal(created.mustChangePassword, true);
  await api.createUser({ username: 'DEMO009002', name: '演示新增二', departmentId: 'D03', phone: '' });
});

test('首次登录只能修改密码；改后旧密码失效（LOGIN-04、LOGIN-05）', async () => {
  const me = await as('DEMO000101');
  assert.equal(me.user.mustChangePassword, true);
  assert.deepEqual(me.permissions, []);
  await assert.rejects(api.listEmployees(), { status: 403, code: 'PASSWORD_CHANGE_REQUIRED' });
  await assert.rejects(api.changePassword('wrong', 'NewPass#2026'), { status: 400 });
  const changed = await api.changePassword(PASSWORD, 'NewPass#2026');
  assert.equal(changed.user.mustChangePassword, false);
  await assert.rejects(api.login('DEMO000101', PASSWORD), { status: 401 });
  assert.equal((await api.login('DEMO000101', 'NewPass#2026')).user.username, 'DEMO000101');
});

test('停用角色后其权限对所有人不生效，启用后恢复（ROLE-02）', async () => {
  await as('admin');
  await api.setRoleEnabled('R05', false);
  assert.deepEqual((await as('SG000013')).permissions, []);
  await as('admin');
  await api.setRoleEnabled('R05', true);
  assert.ok((await as('SG000013')).permissions.includes('hr:f046:read'));
});

test('内置角色不能删除，超级管理员角色不能停用或改授权，编码唯一（ROLE-01、ROLE-03）', async () => {
  await as('admin');
  await assert.rejects(api.deleteRole('R05'), { status: 409, code: 'PROTECTED_ROLE' });
  await assert.rejects(api.setRoleEnabled('R01', false), { status: 409 });
  await assert.rejects(api.saveRoleGrants('R01', [], 1), { status: 409 });
  await assert.rejects(api.createRole({ code: 'STAFF', name: '重复' }), { status: 409, code: 'DUPLICATE_ROLE_CODE' });
  const role = await api.createRole({ code: 'TEMP_ROLE', name: '临时演示角色' });
  await api.assignRoles('u0004', ['R05', role.id], (await api.getUser('u0004')).roleVersion);
  await assert.rejects(api.deleteRole(role.id), { status: 409, code: 'ROLE_HAS_USERS' });
  await api.deleteRole(role.id, { confirmed: true });
  assert.deepEqual((await api.getUser('u0004')).roleIds, ['R05']);
});

test('角色授权：缺少“查看”被拒绝；中途失败全部不变；版本冲突 409（GRANT-02）', async () => {
  await as('admin');
  const before = await api.getRoleGrants('R07');
  const withEdit = [...before.grants, { code: 'oa:f010:edit', scope: 'DEPT' }];
  await assert.rejects(api.saveRoleGrants('R07', withEdit, before.version), { status: 400, code: 'READ_REQUIRED' });
  const complete = [...withEdit, { code: 'oa:f010:read', scope: 'ALL' }];
  await assert.rejects(api.saveRoleGrants('R07', complete, before.version, { simulateFailure: true }), { status: 500 });
  assert.deepEqual(await api.getRoleGrants('R07'), before);
  const saved = await api.saveRoleGrants('R07', complete, before.version);
  assert.equal(saved.version, before.version + 1);
  await assert.rejects(api.saveRoleGrants('R07', before.grants, before.version), { status: 409, code: 'VERSION_CONFLICT' });
  await api.saveRoleGrants('R07', before.grants, saved.version);
});

test('组织架构：部门管理员只看到本部门子树，可查看含下级的人员（DEPT-01、DEPT-02）', async () => {
  await as('admin');
  const [root] = await api.listDepartments();
  const count = nodes => nodes.reduce((sum, node) => sum + 1 + count(node.children), 0);
  assert.equal(count([root]), 16);
  await as('DEMO000100');
  const tree = await api.listDepartments();
  assert.deepEqual(tree.map(node => node.name), ['道路工程分公司']);
  const members = await api.listDepartmentMembers('D10');
  assert.ok(members.some(member => member.departmentName === '道路工程分公司第一项目经理部'));
  await assert.rejects(api.listDepartmentMembers('D03'), { status: 403 });
});

test('停用账号后，已登录的会话下一次请求返回 401（USER-04）', async () => {
  const staff = await as('SG000013');
  await as('admin');
  await api.setUserStatus(staff.user.id, 'DISABLED');
  await assert.rejects(api.resumeDemoSession(staff.user.id), { status: 401 });
  await assert.rejects(as('SG000013'), { status: 401, message: '用户名或密码错误' });
  await as('admin');
  await api.setUserStatus(staff.user.id, 'ENABLED');
});
