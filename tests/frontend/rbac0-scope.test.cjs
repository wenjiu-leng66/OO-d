'use strict';

// 数据范围合并与检查：需求规格 GRANT-04、AUTHZ-03。
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const { effectiveGrants, widerScope, permissionScope, scopeCovers } =
  require(path.resolve(__dirname, '../../frontend/src/authorization/rbac0-policy.js'));

const staff = { id: 'R05', name: '普通员工', grants: [{ code: 'hr:f046:read', scope: 'SELF' }, { code: 'hr:f046:edit', scope: 'SELF' }] };
const keeper = { id: 'R07', name: '台账维护员', grants: [{ code: 'hr:f046:read', scope: 'SELF' }, { code: 'hr:f046:edit', scope: 'ALL' }] };
const manager = { id: 'R03', name: '部门经理', grants: [{ code: 'hr:f046:read', scope: 'ALL' }, { code: 'hr:f046:edit', scope: 'DEPT' }] };

test('同一权限码取最大范围，不同权限码的范围互不影响（GRANT-04 验收样例）', () => {
  const grants = effectiveGrants({ roleIds: ['R05', 'R07'] }, [staff, keeper]);
  const scope = Object.fromEntries(grants.map(grant => [grant.code, grant.scope]));
  assert.deepEqual(scope, { 'hr:f046:edit': 'ALL', 'hr:f046:read': 'SELF' });
  const edit = grants.find(grant => grant.code === 'hr:f046:edit');
  assert.deepEqual(edit.sources.map(source => source.roleId), ['R05', 'R07']);
});

test('范围顺序为 全部 > 本部门 > 本人，未知范围不参与比较', () => {
  assert.equal(widerScope('SELF', 'DEPT'), 'DEPT');
  assert.equal(widerScope('ALL', 'DEPT'), 'ALL');
  assert.equal(widerScope(null, 'SELF'), 'SELF');
  assert.equal(widerScope('EVERYTHING', 'SELF'), 'SELF');
  assert.equal(widerScope(undefined, 'NONE'), null);
});

test('停用、未分配的角色和非法授权都不贡献权限', () => {
  const disabled = { ...manager, enabled: false };
  assert.deepEqual(effectiveGrants({ roleIds: ['R03'] }, [disabled]), []);
  assert.deepEqual(effectiveGrants({ roleIds: ['R05'] }, [manager]), []);
  const broken = { id: 'R10', name: '坏数据', grants: [{ code: 'hr:f046:read', scope: 'ANY' }, { code: '', scope: 'ALL' }] };
  assert.deepEqual(effectiveGrants({ roleIds: ['R10'] }, [broken]), []);
});

test('旧样例只有 permissionCodes 时视为“全部”范围', () => {
  const legacy = { id: 'DEMO', name: '旧样例', permissionCodes: ['oa:document:read'] };
  assert.deepEqual(effectiveGrants({ roleIds: ['DEMO'] }, [legacy]).map(grant => [grant.code, grant.scope]), [['oa:document:read', 'ALL']]);
});

test('会话中的范围：没有权限或没有范围信息时为 null（默认拒绝）', () => {
  const session = { authenticated: true, permissionsLoaded: true, permissions: ['hr:f046:read'], scopes: { 'hr:f046:read': 'DEPT' } };
  assert.equal(permissionScope(session, 'hr:f046:read'), 'DEPT');
  assert.equal(permissionScope(session, 'hr:f046:edit'), null);
  assert.equal(permissionScope({ ...session, scopes: undefined }, 'hr:f046:read'), null);
  assert.equal(permissionScope({ ...session, permissionsLoaded: false }, 'hr:f046:read'), null);
});

test('目标对象检查：本人看归属、本部门看部门树、全部不限（AUTHZ-03）', () => {
  const tree = { D10: null, D11: 'D10', D12: 'D10', D20: null };
  const isWithin = (target, base) => { for (let node = target; node; node = tree[node]) if (node === base) return true; return false; };
  const actor = { userId: 'u1', departmentId: 'D10' };
  assert.equal(scopeCovers('ALL', actor, { ownerId: 'u9', departmentId: 'D20' }, isWithin), true);
  assert.equal(scopeCovers('DEPT', actor, { ownerId: 'u9', departmentId: 'D11' }, isWithin), true);
  assert.equal(scopeCovers('DEPT', actor, { ownerId: 'u9', departmentId: 'D20' }, isWithin), false);
  assert.equal(scopeCovers('SELF', actor, { ownerId: 'u1', departmentId: 'D20' }, isWithin), true);
  assert.equal(scopeCovers('SELF', actor, { ownerId: 'u2', departmentId: 'D10' }, isWithin), false);
  assert.equal(scopeCovers('DEPT', actor, { ownerId: 'u9', departmentId: 'D11' }), false);
  assert.equal(scopeCovers('OTHER', actor, { ownerId: 'u1', departmentId: 'D10' }, isWithin), false);
});
