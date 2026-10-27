'use strict';

// 纯策略单元测试：精确权限码、默认拒绝和 RBAC0 角色并集；不涉及数据库或后端认证。
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { spawnSync } = require('node:child_process');
const { pathToFileURL } = require('node:url');
const policyPath = path.resolve(__dirname, '../../frontend/src/authorization/rbac0-policy.js');
const { hasPermission, canVisit, visibleRoutes, effectivePermissionCodes } = require(policyPath);

const session = {
  authenticated: true,
  permissionsLoaded: true,
  userId: 'user-demo',
  permissions: ['rbac:role:read', 'oa:document:read', 'hr:self:read']
};
const roleRoute = { path: '/roles', requiredPermissions: ['rbac:role:read'] };

test('browser classic script exposes the same usable API without CommonJS', () => {
  // 用独立 VM 模拟没有 require/module 的浏览器，验证经典脚本与 Node 的共用导出协议。
  const browser = vm.createContext({});
  vm.runInContext(fs.readFileSync(policyPath, 'utf8'), browser);
  assert.deepEqual(Object.keys(browser.Rbac0).sort(), [
    'canVisit', 'effectiveGrants', 'effectivePermissionCodes', 'hasPermission',
    'permissionScope', 'scopeCovers', 'visibleRoutes', 'widerScope'
  ]);
  assert.equal(browser.Rbac0.hasPermission(session, 'rbac:role:read'), true);
});

test('CommonJS export also publishes the identical global API used by the Vite adapter', () => {
  // 模拟打包器提供 module 的执行环境；旧版只写 module.exports，导致适配层读不到全局策略。
  const bundled = vm.createContext({ module: { exports: {} } });
  vm.runInContext(fs.readFileSync(policyPath, 'utf8'), bundled);
  assert.equal(bundled.Rbac0, bundled.module.exports);
  assert.equal(typeof bundled.Rbac0?.canVisit, 'function');
  assert.equal(bundled.Rbac0.hasPermission(session, 'rbac:role:read'), true);
  assert.equal(Object.isFrozen(bundled.Rbac0), true);
});

test('fresh ESM adapter import works without manually injecting globalThis.Rbac0', () => {
  // 独立进程避免其他测试手动赋值/模块缓存掩盖缺陷；直接加载实际适配层并执行一次授权判断。
  const adapterUrl = pathToFileURL(path.resolve(__dirname, '../../frontend/src/policy-adapter.js')).href;
  const script = `
    import { routeAccess } from ${JSON.stringify(adapterUrl)};
    const result = routeAccess(
      { isAuthenticated: true, permissionsLoaded: true, user: { id: 'synthetic' }, permissions: ['rbac:user:read'] },
      { path: '/users', meta: { permission: 'rbac:user:read' } }
    );
    if (!globalThis.Rbac0 || !result.allowed) throw new Error('Policy export regression');
  `;
  const result = spawnSync(process.execPath, ['--input-type=module', '--eval', script], { encoding: 'utf8', timeout: 10000 });
  assert.equal(result.error, undefined);
  assert.equal(result.status, 0, result.stderr);
});

test('permissions require both authentication and a completed permission load', () => {
  assert.equal(hasPermission(null, 'rbac:role:read'), false);
  assert.equal(hasPermission({ ...session, authenticated: false }, 'rbac:role:read'), false);
  assert.equal(hasPermission({ ...session, permissionsLoaded: false }, 'rbac:role:read'), false);
  assert.equal(hasPermission({ ...session, permissionsLoaded: 'true' }, 'rbac:role:read'), false);
  assert.equal(hasPermission({ ...session, permissions: null }, 'rbac:role:read'), false);
  assert.equal(hasPermission(session, 'rbac:role:read'), true);
});

test('permission matching is exact and never expands a wildcard or job title', () => {
  assert.equal(hasPermission(session, 'RBAC:ROLE:READ'), false);
  assert.equal(hasPermission(session, ' rbac:role:read'), false);
  assert.equal(hasPermission(session, 'rbac:role:grant'), false);
  assert.equal(hasPermission({ ...session, permissions: ['rbac:*', '公司领导'] }, 'rbac:role:grant'), false);
});

test('anonymous visitors are redirected to login even with cached permissions', () => {
  assert.deepEqual(canVisit({ ...session, authenticated: false }, roleRoute), {
    allowed: false, redirect: '/login', reason: 'unauthenticated'
  });
});

test('authenticated visitors cannot visit a protected route before permissions load', () => {
  assert.deepEqual(canVisit({ ...session, permissionsLoaded: false }, roleRoute), {
    allowed: false, redirect: '/403', reason: 'permissions-not-loaded'
  });
});

test('explicit public routes remain reachable without a session', () => {
  assert.deepEqual(canVisit(null, { path: '/login', public: true }), {
    allowed: true, redirect: null, reason: 'public'
  });
  assert.equal(canVisit(null, { path: '/login', public: 'true' }).allowed, false);
  assert.equal(canVisit(session, { public: true }).allowed, false);
});

test('unknown and malformed routes fail closed', () => {
  for (const route of [undefined, null, {}, [], { path: 'roles' }, { path: '//external.test' }, { path: '/ role' }]) {
    assert.deepEqual(canVisit(session, route), {
      allowed: false, redirect: '/403', reason: 'invalid-route'
    });
  }
});

test('protected routes require at least one explicit permission', () => {
  for (const route of [{ path: '/roles' }, { path: '/roles', requiredPermissions: [] }]) {
    assert.deepEqual(canVisit(session, route), {
      allowed: false, redirect: '/403', reason: 'missing-permission-rule'
    });
  }
});

test('invalid permission rules cannot accidentally grant a route', () => {
  for (const route of [
    { path: '/roles', requiredPermissions: ['rbac:role:read', ''] },
    { path: '/roles', requiredPermissions: ['rbac:role:read', null], match: 'any' },
    { path: '/roles', requiredPermissions: new Array(1) },
    { path: '/roles', requiredPermissions: ['rbac:role:read'], match: 'ANY' }
  ]) {
    assert.deepEqual(canVisit(session, route), {
      allowed: false, redirect: '/403', reason: 'invalid-permission-rule'
    });
  }
});

test('an authorized route is allowed and a different operation is forbidden', () => {
  assert.deepEqual(canVisit(session, roleRoute), {
    allowed: true, redirect: null, reason: 'authorized'
  });
  assert.deepEqual(canVisit(session, { path: '/role-grants', requiredPermissions: ['rbac:role:grant'] }), {
    allowed: false, redirect: '/403', reason: 'forbidden'
  });
});

test('all is the default and requires every listed permission', () => {
  const route = { path: '/combined', requiredPermissions: ['rbac:role:read', 'rbac:role:grant'] };
  assert.equal(canVisit(session, route).allowed, false);
  assert.equal(canVisit({ ...session, permissions: [...session.permissions, 'rbac:role:grant'] }, route).allowed, true);
  assert.equal(canVisit(session, { ...route, match: 'all' }).allowed, false);
});

test('any requires one listed permission, and still denies when none are held', () => {
  const route = { path: '/combined', requiredPermissions: ['rbac:role:read', 'rbac:role:grant'], match: 'any' };
  assert.equal(canVisit(session, route).allowed, true);
  assert.equal(canVisit({ ...session, permissions: [] }, route).allowed, false);
});

test('visible navigation contains only public or authorized routes', () => {
  const publicRoute = { path: '/login', public: true };
  const forbiddenRoute = { path: '/grant', requiredPermissions: ['rbac:role:grant'] };
  const routes = [publicRoute, roleRoute, forbiddenRoute, undefined];
  assert.deepEqual(visibleRoutes(session, routes), [publicRoute, roleRoute]);
  assert.deepEqual(visibleRoutes(null, routes), [publicRoute]);
  assert.deepEqual(visibleRoutes(session, null), []);
});

test('RBAC0 combines assigned enabled roles and removes duplicate permissions', () => {
  const user = { roleIds: ['reader', 'creator', 'reader', 'unknown'] };
  const roles = [
    { id: 'reader', enabled: true, permissionCodes: ['oa:document:read', 'hr:self:read'] },
    { id: 'creator', permissionCodes: ['oa:document:create', 'oa:document:read'] },
    { id: 'unassigned-admin', permissionCodes: ['rbac:role:grant'] }
  ];
  const before = JSON.stringify({ user, roles });
  assert.deepEqual(effectivePermissionCodes(user, roles), [
    'hr:self:read', 'oa:document:create', 'oa:document:read'
  ]);
  assert.equal(JSON.stringify({ user, roles }), before);
});

test('disabled, malformed and unknown roles never add permissions', () => {
  const user = { roleIds: ['disabled', 'bad-status', 'broken', 'unknown'] };
  const roles = [
    { id: 'disabled', enabled: false, permissionCodes: ['rbac:role:grant'] },
    { id: 'bad-status', enabled: 'true', permissionCodes: ['rbac:role:grant'] },
    { id: 'broken', enabled: true, permissionCodes: 'rbac:role:grant' }
  ];
  assert.deepEqual(effectivePermissionCodes(user, roles), []);
  assert.deepEqual(effectivePermissionCodes(null, roles), []);
  assert.deepEqual(effectivePermissionCodes(user, null), []);
});

test('revoking a role or disabling it removes its exclusive permissions on recalculation', () => {
  const roles = [
    { id: 'reader', enabled: true, permissionCodes: ['oa:document:read'] },
    { id: 'creator', enabled: true, permissionCodes: ['oa:document:create', 'oa:document:read'] }
  ];
  assert.deepEqual(effectivePermissionCodes({ roleIds: ['reader', 'creator'] }, roles), [
    'oa:document:create', 'oa:document:read'
  ]);
  assert.deepEqual(effectivePermissionCodes({ roleIds: ['reader'] }, roles), ['oa:document:read']);
  assert.deepEqual(effectivePermissionCodes({ roleIds: ['reader', 'creator'] }, [
    roles[0], { ...roles[1], enabled: false }
  ]), ['oa:document:read']);
});

test('role identities stay exact and RBAC1 hierarchy fields do not grant inherited permissions', () => {
  const roles = [
    { id: 'leader', permissionCodes: ['oa:document:approve'], parentRoleIds: ['admin'] },
    { id: 'admin', permissionCodes: ['rbac:role:grant'] },
    { id: '42', permissionCodes: ['oa:document:edit'] }
  ];
  assert.deepEqual(effectivePermissionCodes({ roleIds: ['leader', 42] }, roles), ['oa:document:approve']);
});
