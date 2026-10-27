'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');

// Node 使用 CommonJS 导出；浏览器由经典脚本提供相同的 Rbac0 对象。
// 测试的是会话到路由规则的适配，不启动 Vue Router、浏览器或真实服务；页面演示另行验收。
globalThis.Rbac0 = require('../../frontend/src/authorization/rbac0-policy.js');
const adapter = import('../../frontend/src/policy-adapter.js');
const auth = {
  isAuthenticated: true, permissionsLoaded: true,
  user: { id: 'user-002' }, permissions: ['rbac:user:read']
};
const roles = { path: '/roles', meta: { permission: 'rbac:role:read' } };

test('Vue 会话适配后允许已授权页面并拒绝角色页面', async () => {
  const { routeAccess } = await adapter;
  assert.equal(routeAccess(auth, { path: '/users', meta: { permission: 'rbac:user:read' } }).allowed, true);
  assert.equal(routeAccess(auth, roles).redirect, '/403');
  assert.equal(routeAccess({ ...auth, permissions: ['rbac:role:read'] }, roles).allowed, true);
});

test('未登录和权限未加载时不能进入受保护页面', async () => {
  const { routeAccess } = await adapter;
  assert.equal(routeAccess({ ...auth, isAuthenticated: false }, roles).redirect, '/login');
  const pending = { ...auth, permissionsLoaded: false, permissions: ['rbac:role:read'] };
  assert.equal(routeAccess(pending, roles).reason, 'permissions-not-loaded');
});

test('403 页面不会形成重定向循环，未知地址保留现有 404 页面', async () => {
  const { routeAccess } = await adapter;
  for (const path of ['/403', '/unknown']) {
    const route = { path, meta: { sessionOnly: true } };
    assert.equal(routeAccess(auth, route).allowed, true);
    assert.equal(routeAccess({ ...auth, isAuthenticated: false }, route).redirect, '/login');
  }
});

test('侧边栏与页面权限一致，权限加载前隐藏受保护入口', async () => {
  const { visibleNavigation } = await adapter;
  const items = [
    { path: '/users', permission: 'rbac:user:read' },
    { path: '/roles', permission: 'rbac:role:read' }
  ];
  assert.deepEqual(visibleNavigation(auth, items).map(item => item.path), ['/users']);
  assert.deepEqual(visibleNavigation({ ...auth, permissionsLoaded: false }, items), []);
});

test('工作台只要求登录，缺少用户查看权限也能访问，但不显示用户菜单', async () => {
  const { routeAccess, visibleNavigation, hasPermission } = await adapter;
  const dashboard = { path: '/dashboard', meta: { sessionOnly: true } };
  const noPermissions = { ...auth, permissions: [], permissionsLoaded: false };
  assert.equal(routeAccess(noPermissions, dashboard).allowed, true);
  assert.equal(routeAccess({ ...noPermissions, isAuthenticated: false }, dashboard).redirect, '/login');
  const items = [{ path: '/dashboard', sessionOnly: true }, { path: '/users', permission: 'rbac:user:read' }];
  assert.deepEqual(visibleNavigation(noPermissions, items).map(item => item.path), ['/dashboard']);
  assert.deepEqual(visibleNavigation({ ...noPermissions, isAuthenticated: false }, items), []);
  assert.equal(hasPermission(noPermissions, 'rbac:user:read'), false);
});
