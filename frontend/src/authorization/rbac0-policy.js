/*
 * RBAC0 展示层策略：统一菜单、路由和按钮的权限判断。
 * 它不构成服务端安全边界；Java 后端仍须独立校验每次请求和数据范围。
 * 同一文件兼容浏览器经典脚本与 CommonJS，使历史原型、Vue 和 Node 测试复用规则。
 */
(function (root, createPolicy) {
  'use strict';
  var policy = createPolicy();
  // 浏览器（经典脚本或 Vite/CommonJS 打包）都必须能通过 globalThis.Rbac0 读到策略。
  root.Rbac0 = policy;
  if (typeof module === 'object' && module.exports) {
    module.exports = policy;
  }
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  function isCode(value) {
    return typeof value === 'string' && value.length > 0 && value.trim() === value;
  }

  // 必须同时完成身份认证和权限加载；加载失败/尚未加载不能视为“暂时拥有权限”。
  function readySession(session) {
    return Boolean(session && session.authenticated === true &&
      session.permissionsLoaded === true && Array.isArray(session.permissions));
  }

  function hasPermission(session, code) {
    return readySession(session) && isCode(code) && session.permissions.includes(code);
  }

  // 只接受站内绝对路径；双斜线、空白和类型错误均不能产生可访问路由。
  function validRoute(route) {
    return Boolean(route && typeof route === 'object' && !Array.isArray(route) &&
      typeof route.path === 'string' && /^\/(?!\/)\S*$/.test(route.path) &&
      (route.public === undefined || typeof route.public === 'boolean'));
  }

  function denied(redirect, reason) {
    return { allowed: false, redirect: redirect, reason: reason };
  }

  /**
   * 默认拒绝：公开页须显式声明；受保护页必须声明非空且有效的权限规则。
   * 默认要求全部权限，只有显式 match=any 才采用任一满足；无配置不等于无权限限制。
   * 返回原因供路由与测试区分未登录、权限未加载、规则错误和确实无权。
   */
  function canVisit(session, route) {
    var knownRoute = validRoute(route);
    if (knownRoute && route.public === true) {
      return { allowed: true, redirect: null, reason: 'public' };
    }
    if (!session || session.authenticated !== true) {
      return denied('/login', 'unauthenticated');
    }
    if (!knownRoute) {
      return denied('/403', 'invalid-route');
    }
    if (!readySession(session)) {
      return denied('/403', 'permissions-not-loaded');
    }
    if (!Array.isArray(route.requiredPermissions) || route.requiredPermissions.length === 0) {
      return denied('/403', 'missing-permission-rule');
    }
    if (!Array.from(route.requiredPermissions).every(isCode) ||
        (route.match !== undefined && route.match !== 'all' && route.match !== 'any')) {
      return denied('/403', 'invalid-permission-rule');
    }
    var granted = route.match === 'any'
      ? route.requiredPermissions.some(function (code) { return hasPermission(session, code); })
      : route.requiredPermissions.every(function (code) { return hasPermission(session, code); });
    return granted
      ? { allowed: true, redirect: null, reason: 'authorized' }
      : denied('/403', 'forbidden');
  }

  function visibleRoutes(session, routes) {
    if (!Array.isArray(routes)) return [];
    return routes.filter(function (route) { return canVisit(session, route).allowed; });
  }

  // 历史纯权限码原型的兼容入口；带数据范围的新原型使用 effectiveGrants。
  // 仅汇总已分配且未停用角色；排序使菜单和断言结果不受角色遍历顺序影响。
  function effectivePermissionCodes(user, roles) {
    if (!user || !Array.isArray(user.roleIds) || !Array.isArray(roles)) return [];
    var assigned = new Set(user.roleIds.filter(isCode));
    var permissions = new Set();
    roles.forEach(function (role) {
      if (!role || !isCode(role.id) || !assigned.has(role.id) ||
          (role.enabled !== undefined && role.enabled !== true) ||
          !Array.isArray(role.permissionCodes)) return;
      role.permissionCodes.filter(isCode).forEach(function (code) { permissions.add(code); });
    });
    return Array.from(permissions).sort();
  }

  // 数据范围：全部 > 本部门 > 本人（需求规格 GRANT-04、AUTHZ-03）。
  var SCOPE_RANK = { SELF: 1, DEPT: 2, ALL: 3 };

  function isScope(value) {
    return typeof value === 'string' && Object.prototype.hasOwnProperty.call(SCOPE_RANK, value);
  }

  function widerScope(a, b) {
    if (!isScope(a)) return isScope(b) ? b : null;
    if (!isScope(b)) return a;
    return SCOPE_RANK[b] > SCOPE_RANK[a] ? b : a;
  }

  // 角色授权为 grants: [{ code, scope }]；旧样例只有 permissionCodes 时视为“全部”。
  // 兼容仅限本文件的旧样例数据格式；正式导入/API 不应靠缺省值扩大数据范围。
  function roleGrantList(role) {
    if (Array.isArray(role.grants)) {
      return role.grants.filter(function (grant) {
        return grant && isCode(grant.code) && isScope(grant.scope);
      });
    }
    if (Array.isArray(role.permissionCodes)) {
      return role.permissionCodes.filter(isCode).map(function (code) { return { code: code, scope: 'ALL' }; });
    }
    return [];
  }

  // 有效权限：已分配且启用的角色授权取并集；同一权限码取最大范围，不同权限码的范围互不影响。
  /**
   * 以权限码为合并键，保留各角色原始 scope 作为来源，合并后的 scope 只影响同一码。
   * 例如 read=ALL 与 edit=SELF 同时存在时，不能把 edit 也扩大为 ALL。
   * 阶段一只计算直接分配；这里没有角色继承、SSD/DSD 或会话激活集合。
   */
  function effectiveGrants(user, roles) {
    if (!user || !Array.isArray(user.roleIds) || !Array.isArray(roles)) return [];
    var assigned = new Set(user.roleIds.filter(isCode));
    var byCode = new Map();
    roles.forEach(function (role) {
      if (!role || !isCode(role.id) || !assigned.has(role.id) ||
          (role.enabled !== undefined && role.enabled !== true)) return;
      roleGrantList(role).forEach(function (grant) {
        var entry = byCode.get(grant.code);
        if (!entry) {
          entry = { code: grant.code, scope: grant.scope, sources: [] };
          byCode.set(grant.code, entry);
        }
        entry.scope = widerScope(entry.scope, grant.scope);
        entry.sources.push({ roleId: role.id, roleName: role.name, scope: grant.scope });
      });
    });
    return Array.from(byCode.values()).sort(function (a, b) { return a.code < b.code ? -1 : a.code > b.code ? 1 : 0; });
  }

  // 会话中某权限码的数据范围；没有该权限或没有范围信息时返回 null（默认拒绝）。
  function permissionScope(session, code) {
    if (!hasPermission(session, code) || !session.scopes) return null;
    var scope = session.scopes[code];
    return isScope(scope) ? scope : null;
  }

  // 目标记录是否落在范围内。target: { ownerId, departmentId }；
  // isWithin(目标部门, 用户部门) 判断目标部门是否为用户部门本身或其下级，由调用方按部门树实现。
  // SELF 比较稳定的用户 ID，DEPT 委托部门树判断，不能按显示姓名/部门名称判断归属。
  // 非法范围、缺少对象或缺少 DEPT 判断器全部返回 false。
  function scopeCovers(scope, actor, target, isWithin) {
    if (!isScope(scope) || !actor || !target) return false;
    if (scope === 'ALL') return true;
    if (scope === 'SELF') return isCode(actor.userId) && actor.userId === target.ownerId;
    return typeof isWithin === 'function' && isCode(actor.departmentId) &&
      isCode(target.departmentId) && isWithin(target.departmentId, actor.departmentId) === true;
  }

  return Object.freeze({
    hasPermission: hasPermission,
    canVisit: canVisit,
    visibleRoutes: visibleRoutes,
    effectivePermissionCodes: effectivePermissionCodes,
    widerScope: widerScope,
    effectiveGrants: effectiveGrants,
    permissionScope: permissionScope,
    scopeCovers: scopeCovers
  });
});
