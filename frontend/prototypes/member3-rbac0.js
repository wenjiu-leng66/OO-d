/* RBAC0 交互原型：仅使用合成数据与浏览器内存，不代表服务端授权。 */
(function () {
  'use strict';
  var policy = window.Rbac0;
  var main = document.getElementById('main');
  if (!policy) {
    main.innerHTML = '<section class="state-page"><h1>授权逻辑未加载</h1><p>请保留完整目录结构，并检查 ../src/authorization/rbac0-policy.js 文件是否存在。</p></section>';
    return;
  }

  var permissions = [
    { code: 'rbac:role:read', system: '权限管理系统', module: '角色维护', name: '查看角色', detail: '查看角色列表及已保存授权' },
    { code: 'rbac:role:grant', system: '权限管理系统', module: '角色维护', name: '修改角色授权', detail: '保存或撤销角色拥有的功能权限' },
    { code: 'rbac:permission:read', system: '权限管理系统', module: '资源目录', name: '查看资源目录', detail: '查询系统、模块与功能权限' },
    { code: 'rbac:user:read', system: '权限管理系统', module: '用户角色', name: '查看用户', detail: '查看用户信息与角色分配' },
    { code: 'rbac:user:assign-role', system: '权限管理系统', module: '用户角色', name: '分配用户角色', detail: '完整替换用户的角色列表' },
    { code: 'oa:document:read', system: '办公协同系统', module: '文档管理', name: '查看文档', detail: '访问文档列表' },
    { code: 'oa:document:create', system: '办公协同系统', module: '文档管理', name: '新建文档', detail: '创建一份演示文档' },
    { code: 'oa:document:edit', system: '办公协同系统', module: '文档管理', name: '编辑文档', detail: '将文档调整为修改草稿' },
    { code: 'oa:document:approve', system: '办公协同系统', module: '文档审批', name: '审批文档', detail: '将演示文档标记为已审批' },
    { code: 'hr:self:read', system: '人事信息系统', module: '个人信息', name: '查看个人信息', detail: '访问当前登录用户的基本信息' }
  ];
  var routes = [
    { path: '/roles', title: '角色授权', icon: '◈', group: 'management', requiredPermissions: ['rbac:role:read', 'rbac:permission:read'], match: 'all' },
    { path: '/permissions', title: '资源目录', icon: '▤', group: 'management', requiredPermissions: ['rbac:permission:read'] },
    { path: '/assignments', title: '用户角色分配', icon: '⇄', group: 'management', requiredPermissions: ['rbac:user:read', 'rbac:role:read'], match: 'all' },
    { path: '/documents', title: '文档管理', icon: '▣', group: 'business', requiredPermissions: ['oa:document:read'] },
    { path: '/self', title: '个人信息', icon: '○', group: 'business', requiredPermissions: ['hr:self:read'] }
  ];
  var baseRoles = [
    { id: 'DEMO-role-admin', name: '演示管理员', description: '维护授权与用户角色，具有全部样例权限。', permissionCodes: permissions.map(function (p) { return p.code; }), version: 1 },
    { id: 'DEMO-role-clerk', name: '演示文档编辑员', description: '可查看、新建、编辑文档，不具有审批权限。', permissionCodes: ['oa:document:read', 'oa:document:create', 'oa:document:edit', 'hr:self:read'], version: 1 },
    { id: 'DEMO-role-approver', name: '演示文档审批员', description: '可查看和审批文档，不具有编辑权限。', permissionCodes: ['oa:document:read', 'oa:document:approve', 'hr:self:read'], version: 1 },
    { id: 'DEMO-role-employee', name: '演示普通员工', description: '可查看文档与本人基本信息。', permissionCodes: ['oa:document:read', 'hr:self:read'], version: 1 }
  ];
  var baseUsers = [
    { id: 'DEMO-user-admin', name: '演示管理员', department: '演示信息中心', roleIds: ['DEMO-role-admin'], version: 1 },
    { id: 'DEMO-user-employee', name: '演示普通员工', department: '演示工程一部', roleIds: ['DEMO-role-employee'], version: 1 },
    { id: 'DEMO-user-clerk', name: '演示文档编辑员', department: '演示办公室', roleIds: ['DEMO-role-clerk'], version: 1 },
    { id: 'DEMO-user-approver', name: '演示文档审批员', department: '演示工程二部', roleIds: ['DEMO-role-approver'], version: 1 }
  ];
  var baseDocuments = [
    { id: 'DEMO-doc-001', title: '道路养护周报（演示）', department: '演示工程一部', status: '待审批' },
    { id: 'DEMO-doc-002', title: '设施巡检记录（演示）', department: '演示工程二部', status: '草稿' },
    { id: 'DEMO-doc-003', title: '施工会议纪要（演示）', department: '演示办公室', status: '已审批' }
  ];
  function clone(value) { return JSON.parse(JSON.stringify(value)); }
  var state = {
    roles: clone(baseRoles), users: clone(baseUsers), documents: clone(baseDocuments),
    identity: 'DEMO-user-admin', scenario: 'normal', selectedRoleId: 'DEMO-role-clerk', selectedUserId: 'DEMO-user-employee',
    roleDraft: [], roleVersion: 1, userDraft: [], userVersion: 1,
    roleSearch: '', catalogSearch: '', message: '', saving: false, generation: 0
  };
  var toastTimer;
  // 搜索词等可变文本进入 innerHTML 前转义；不把用户输入当作 HTML 拼接。
  function escape(value) { return String(value).replace(/[&<>"']/g, function (char) { return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]; }); }
  function findRole(id) { return state.roles.find(function (role) { return role.id === id; }); }
  function findUser(id) { return state.users.find(function (user) { return user.id === id; }); }
  function session() {
    var user = findUser(state.identity);
    return { authenticated: !!user, permissionsLoaded: state.scenario !== 'loading', userId: user ? user.id : null, permissions: user ? policy.effectivePermissionCodes(user, state.roles) : [] };
  }
  function allowed(code) { return policy.hasPermission(session(), code); }
  function sameList(a, b) { return a.slice().sort().join('|') === b.slice().sort().join('|'); }
  function loadRoleDraft() { var role = findRole(state.selectedRoleId); state.roleDraft = role ? role.permissionCodes.slice() : []; state.roleVersion = role ? role.version : 1; state.message = ''; }
  function loadUserDraft() { var user = findUser(state.selectedUserId); state.userDraft = user ? user.roleIds.slice() : []; state.userVersion = user ? user.version : 1; state.message = ''; }
  loadRoleDraft(); loadUserDraft();
  function path() { return location.hash.slice(1) || '/roles'; }
  function toast(message, error) {
    var element = document.getElementById('toast');
    element.textContent = message; element.classList.toggle('error', !!error); element.hidden = false;
    clearTimeout(toastTimer); toastTimer = setTimeout(function () { element.hidden = true; }, 4500);
  }
  function pageHeader(title, text, badge) {
    return '<div class="page-header"><div><p class="page-eyebrow">市政应用 / 权限工作台</p><h1>' + title + '</h1><p class="page-subtitle">' + text + '</p></div><span class="stage-badge">' + (badge || '基础角色模型 · RBAC0') + '</span></div>';
  }
  function stateMessage() {
    return state.message ? '<div class="notice error" role="alert">' + escape(state.message) + ' <button class="button secondary small" data-action="reload">重新载入</button></div>' : '';
  }
  function empty(title, text) { return '<div class="empty-state"><div class="empty-symbol">∅</div><h2>' + title + '</h2><p>' + text + '</p><button class="button secondary" data-action="normal-state">恢复正常数据</button></div>'; }
  function renderNav(current) {
    var visible = policy.visibleRoutes(session(), routes);
    ['management', 'business'].forEach(function (group) {
      var list = visible.filter(function (route) { return route.group === group; });
      document.getElementById(group + '-nav').innerHTML = list.length ? list.map(function (route) {
        return '<a class="nav-link' + (route.path === current ? ' active' : '') + '" href="#' + route.path + '"' + (route.path === current ? ' aria-current="page"' : '') + '><span class="nav-icon" aria-hidden="true">' + route.icon + '</span>' + route.title + '</a>';
      }).join('') : '<span class="nav-empty">暂无可访问页面</span>';
    });
    document.getElementById('session-label').textContent = session().authenticated ? '已登录 · ' + session().permissions.length + ' 项权限' : '未登录';
  }
  function renderRoles() {
    var header = pageHeader('角色授权', '先选择角色，再按功能授予权限。修改后保存，所有拥有该角色的用户会获得新的权限。');
    if (state.scenario === 'empty') return header + '<section class="panel">' + empty('暂无角色', '这是空列表状态演示。恢复正常数据后可以选择角色授权。') + '</section>';
    var role = findRole(state.selectedRoleId);
    var dirty = !sameList(state.roleDraft, role.permissionCodes);
    var editable = allowed('rbac:role:grant') && !state.saving;
    var roleList = state.roles.map(function (item) {
      return '<button class="role-item' + (item.id === role.id ? ' selected' : '') + '" data-role-id="' + item.id + '"' + (state.saving ? ' disabled' : '') + '><span class="role-number">' + item.permissionCodes.length + ' 项</span><strong>' + item.name + '</strong><small>' + item.id + '</small></button>';
    }).join('');
    var query = state.roleSearch.trim().toLowerCase();
    var matches = permissions.filter(function (item) { return (item.name + item.module + item.system + item.code).toLowerCase().includes(query); });
    var systems = Array.from(new Set(matches.map(function (item) { return item.system; })));
    var rows = systems.map(function (system) {
      var subset = matches.filter(function (item) { return item.system === system; });
      return '<section class="permission-group"><div class="group-heading"><h3>' + system + '</h3><span>' + subset.length + ' 项功能</span></div>' + subset.map(function (item) {
        return '<label class="permission-row" for="perm-' + item.code + '"><input id="perm-' + item.code + '" type="checkbox" data-permission="' + item.code + '"' + (state.roleDraft.includes(item.code) ? ' checked' : '') + (editable ? '' : ' disabled') + '><span class="permission-label"><span>' + item.name + '</span><small>' + item.module + ' / ' + item.detail + '</small></span><code class="permission-code">' + item.code + '</code></label>';
      }).join('') + '</section>';
    }).join('');
    return header + '<div class="work-grid"><section class="panel"><div class="panel-header compact"><h2>角色清单</h2><span class="count">' + state.roles.length + ' 个</span></div><div class="role-list">' + roleList + '</div><p class="list-note">这里的角色均为演示样例，未导入员工表的真实职位映射。</p></section><section class="panel"><div class="sheet-header"><div class="sheet-title"><div><h2>' + role.name + ' · 授权作业单</h2><p class="sheet-description">' + role.description + '</p></div><span class="tag ' + (dirty ? 'dirty' : 'saved') + '">' + (dirty ? '有未保存修改' : '已保存') + '</span></div><div class="sheet-meta"><span><span class="meta-label">已保存</span> ' + role.permissionCodes.length + ' 项</span><span><span class="meta-label">当前勾选</span> ' + state.roleDraft.length + ' 项</span><span><span class="meta-label">版本</span> ' + role.version + '</span></div></div><div class="searchbar"><input id="role-search" type="search" placeholder="搜索功能名称、模块或权限码" aria-label="搜索角色功能权限" value="' + escape(state.roleSearch) + '"><span class="search-hint">勾选授予，取消勾选撤销</span></div>' + (rows || '<div class="empty-state"><h2>没有匹配的功能</h2><p>试试“文档”或“read”，已勾选的权限会保留。</p></div>') + (!allowed('rbac:role:grant') ? '<p class="hint">当前身份仅可查看授权，没有修改授权的权限。</p>' : '') + stateMessage() + '<div class="sheet-actions"><button class="button" data-action="save-role"' + (!editable || !dirty ? ' disabled' : '') + '>' + (state.saving ? '正在保存…' : '保存授权') + '</button><button class="button secondary" data-action="cancel-role"' + (!dirty || state.saving ? ' disabled' : '') + '>取消修改</button><button class="button secondary" data-action="clear-role"' + (!editable || !state.roleDraft.length ? ' disabled' : '') + '>取消全部勾选</button><span class="action-note">保存会替换该角色的全部功能权限。</span></div></section></div>';
  }
  function renderCatalog() {
    var header = pageHeader('资源目录', '按“系统 → 模块 → 功能”查看权限资源。每个功能拥有独立权限码，可用于页面访问与操作按钮判断。');
    if (state.scenario === 'empty') return header + '<section class="panel">' + empty('暂无资源', '这是空资源状态演示。真实资源将在后续数据清洗与接口接入后加载。') + '</section>';
    var query = state.catalogSearch.trim().toLowerCase();
    var matches = permissions.filter(function (item) { return (item.system + item.module + item.name + item.code).toLowerCase().includes(query); });
    var systems = Array.from(new Set(matches.map(function (item) { return item.system; })));
    var sections = systems.map(function (system) {
      var systemItems = matches.filter(function (item) { return item.system === system; });
      var modules = Array.from(new Set(systemItems.map(function (item) { return item.module; })));
      return '<section class="catalog-system"><div class="catalog-system-title"><span class="system-line" aria-hidden="true"></span><h2>' + system + '</h2><span class="count">' + systemItems.length + ' 项功能</span></div>' + modules.map(function (module) {
        return '<div class="catalog-module"><h3 class="module-title">' + module + '</h3><div class="catalog-functions">' + systemItems.filter(function (item) { return item.module === module; }).map(function (item) { return '<div class="catalog-function"><span>' + item.name + '</span><code>' + item.code + '</code></div>'; }).join('') + '</div></div>';
      }).join('') + '</section>';
    }).join('');
    return header + '<section class="panel"><div class="catalog-toolbar"><input id="catalog-search" type="search" aria-label="搜索资源目录" placeholder="搜索系统、模块、功能或权限码" value="' + escape(state.catalogSearch) + '"><span class="count">样例：3 个系统 / 6 个模块 / 10 项功能 · 当前匹配 ' + matches.length + ' 项</span></div>' + (sections || '<div class="empty-state"><h2>没有匹配的资源</h2><p>换一个关键词，或清空搜索框查看全部资源。</p></div>') + '</section>';
  }
  function renderAssignments() {
    var header = pageHeader('用户角色分配', '一个用户可以拥有多个角色，功能权限取这些角色的并集。取消角色并保存后，该角色独有的权限立即失效。');
    if (state.scenario === 'empty') return header + '<section class="panel">' + empty('暂无用户', '这是空用户列表状态演示。恢复正常数据后可以分配角色。') + '</section>';
    var user = findUser(state.selectedUserId);
    var dirty = !sameList(state.userDraft, user.roleIds);
    var editable = allowed('rbac:user:assign-role') && !state.saving;
    var preview = policy.effectivePermissionCodes({ id: user.id, roleIds: state.userDraft }, state.roles);
    var userList = state.users.map(function (item) { return '<button class="role-item' + (item.id === user.id ? ' selected' : '') + '" data-user-id="' + item.id + '"' + (state.saving ? ' disabled' : '') + '><strong>' + item.name + '</strong><small>' + item.department + '</small><small>' + item.id + '</small></button>'; }).join('');
    var options = state.roles.map(function (role) { return '<label class="role-option" for="assign-' + role.id + '"><input id="assign-' + role.id + '" type="checkbox" data-assigned-role="' + role.id + '"' + (state.userDraft.includes(role.id) ? ' checked' : '') + (editable ? '' : ' disabled') + '><span><strong>' + role.name + '</strong><small>' + role.description + '</small><small>' + role.id + ' · ' + role.permissionCodes.length + ' 项权限</small></span></label>'; }).join('');
    return header + '<div class="work-grid"><section class="panel"><div class="panel-header compact"><h2>用户清单</h2><span class="count">' + state.users.length + ' 位</span></div><div class="role-list">' + userList + '</div></section><section class="panel"><div class="sheet-header"><div class="sheet-title"><div><h2>' + user.name + '</h2><p class="sheet-description">' + user.department + ' · ' + user.id + '</p></div><span class="tag ' + (dirty ? 'dirty' : 'saved') + '">' + (dirty ? '有未保存修改' : '已保存') + '</span></div></div><div class="assignment-summary">勾选 <strong>' + state.userDraft.length + '</strong> 个角色，保存后将拥有 <strong>' + preview.length + '</strong> 项去重权限。</div><div class="role-options">' + options + '</div><div class="effective-permissions">待保存的权限预览<div>' + (preview.length ? preview.map(function (code) { return '<code>' + code + '</code>'; }).join('') : '无角色或角色未授予权限，该用户不能访问受保护页面。') + '</div></div>' + (!allowed('rbac:user:assign-role') ? '<p class="hint">当前身份仅可查看用户角色，没有分配角色的权限。</p>' : '') + stateMessage() + '<div class="sheet-actions"><button class="button" data-action="save-user"' + (!editable || !dirty ? ' disabled' : '') + '>' + (state.saving ? '正在保存…' : '保存角色分配') + '</button><button class="button secondary" data-action="cancel-user"' + (!dirty || state.saving ? ' disabled' : '') + '>取消修改</button><span class="action-note">保存会替换该用户的全部角色。</span></div></section></div>';
  }
  function renderDocuments() {
    var header = pageHeader('文档管理', '切换当前身份，观察菜单与操作按钮如何随权限变化。这里的文档都是演示数据。', '业务权限演示');
    var items = state.scenario === 'empty' ? [] : state.documents;
    var createButton = allowed('oa:document:create') ? '<button class="button" data-action="create-document">新建文档</button>' : '<span class="count">当前身份没有新建权限</span>';
    var table = items.length ? '<div class="table-scroll"><table class="documents-table"><thead><tr><th>文档名称</th><th>所属部门</th><th>状态</th><th>可用操作</th></tr></thead><tbody>' + items.map(function (doc) {
      var actions = (allowed('oa:document:edit') ? '<button class="button secondary small" data-action="edit-document" data-document="' + doc.id + '">编辑</button>' : '') + (allowed('oa:document:approve') ? '<button class="button green small" data-action="approve-document" data-document="' + doc.id + '">审批</button>' : '');
      return '<tr><td><strong>' + doc.title + '</strong><br><code>' + doc.id + '</code></td><td>' + doc.department + '</td><td><span class="tag' + (doc.status === '已审批' ? ' saved' : '') + '">' + doc.status + '</span></td><td><div class="document-actions">' + (actions || '<span class="count">仅查看</span>') + '</div></td></tr>';
    }).join('') + '</tbody></table></div>' : empty('暂无文档', '这是空文档列表状态演示。恢复正常数据后继续验证操作权限。');
    return header + '<section class="panel"><div class="panel-header"><h2>文档清单</h2>' + createButton + '</div>' + table + '<p class="table-note">隐藏按钮用于改善界面体验。真实应用中的每次读取、编辑和审批，仍须通过后端授权检查。</p></section>';
  }
  function renderSelf() {
    var user = findUser(state.identity);
    return pageHeader('个人信息', '查看当前演示用户的基本信息与已保存授权。', '本人信息') + '<section class="panel"><div class="panel-header"><h2>' + user.name + '</h2><span class="tag saved">已登录</span></div><div class="self-content"><dl class="info-grid"><div><dt>用户标识</dt><dd><code>' + user.id + '</code></dd></div><div><dt>部门</dt><dd>' + user.department + '</dd></div><div><dt>拥有的角色</dt><dd>' + (user.roleIds.map(function (id) { var role = findRole(id); return role ? role.name : id; }).join('、') || '无') + '</dd></div><div><dt>有效功能权限</dt><dd>' + session().permissions.length + ' 项</dd></div></dl><p class="hint">个人信息仅为合成样例。没有使用真实员工姓名或联系方式。</p></div></section>';
  }
  function deniedView(current, unauthenticated) {
    var available = policy.visibleRoutes(session(), routes);
    return '<section class="state-page"><div class="state-code">' + (unauthenticated ? '登录' : '403') + '</div><h1>' + (unauthenticated ? '请先登录' : '当前身份无法访问此页面') + '</h1><p>' + (unauthenticated ? '这是登录占位页。使用顶部“当前身份”选择一个演示用户，再访问所需页面。' : '请求路径：' + escape(current) + '。此页面需要相应的功能权限；直接输入地址也会检查。') + '</p>' + (available.length ? '<a class="button" href="#' + available[0].path + '">前往可访问页面</a>' : '<button class="button secondary" data-action="admin-identity">切换演示管理员</button>') + '</section>';
  }
  // 整块重绘会替换输入框，保存焦点与光标后再恢复，避免搜索每输入一字就失焦。
  // 未登录、权限加载中、未知路由和无权限均在选择具体页面前处理。
  function render() {
    var focus = document.activeElement;
    var focusId = focus ? focus.id : '';
    var selection = focus && typeof focus.selectionStart === 'number' ? focus.selectionStart : null;
    var current = path();
    renderNav(current);
    var route = routes.find(function (item) { return item.path === current; });
    var currentSession = session();
    if (!currentSession.authenticated) main.innerHTML = deniedView(current, true);
    else if (state.scenario === 'loading') main.innerHTML = '<section class="state-page"><div class="spinner" aria-hidden="true"></div><h1>正在读取权限与页面数据</h1><p>权限加载完成前，受保护页面与操作不会展示。</p><button class="button secondary" data-action="normal-state">完成加载</button></section>';
    else if (!route || !policy.canVisit(currentSession, route).allowed) main.innerHTML = deniedView(current, false);
    else main.innerHTML = ({ '/roles': renderRoles, '/permissions': renderCatalog, '/assignments': renderAssignments, '/documents': renderDocuments, '/self': renderSelf })[current]();
    if (focusId) {
      var replacement = document.getElementById(focusId);
      if (replacement && !replacement.disabled) { replacement.focus({ preventScroll: true }); if (selection !== null && typeof replacement.setSelectionRange === 'function') replacement.setSelectionRange(selection, selection); }
    }
  }
  // 冻结目标、草稿、版本及 generation；延时结束仍重新查权限与版本后才能写入。
  // 这是历史纯浏览器示例，不是事务或真实服务端并发测试。
  function save(kind) {
    var code = kind === 'role' ? 'rbac:role:grant' : 'rbac:user:assign-role';
    if (!allowed(code) || state.saving) { toast('当前身份不能执行此操作。', true); return; }
    var id = kind === 'role' ? state.selectedRoleId : state.selectedUserId;
    var draft = (kind === 'role' ? state.roleDraft : state.userDraft).slice();
    var version = kind === 'role' ? state.roleVersion : state.userVersion;
    var generation = state.generation;
    state.saving = true; state.message = ''; render();
    setTimeout(function () {
      if (generation !== state.generation) return;
      var item = kind === 'role' ? findRole(id) : findUser(id);
      state.saving = false;
      if (!allowed(code)) state.message = '身份权限已变化，本次保存未执行。';
      else if (state.scenario === 'save-error') state.message = '模拟保存失败：已保存数据保持原样。恢复“正常数据”后可重试，或取消修改。';
      else if (state.scenario === 'conflict' || item.version !== version) state.message = '模拟版本冲突：本次修改未保存。请重新载入已保存数据，再检查修改。';
      else {
        if (kind === 'role') item.permissionCodes = draft; else item.roleIds = draft;
        item.version += 1;
        if (kind === 'role') loadRoleDraft(); else loadUserDraft();
        toast(kind === 'role' ? '角色授权已保存，相关用户权限已更新。' : '用户角色已保存，撤销与新增的角色立即生效。');
      }
      if (state.message) toast(state.message, true);
      render();
    }, 500);
  }
  function setIdentity(id) {
    // 切换身份时取消旧身份发起的模拟保存，避免延迟回调污染新会话。
    state.generation += 1;
    state.saving = false;
    state.identity = id; state.message = '';
    document.getElementById('identity').value = id;
    loadRoleDraft(); loadUserDraft(); render();
  }
  function setScenario(value) { state.scenario = value; state.message = ''; document.getElementById('scenario').value = value; render(); }
  function reset() {
    state.generation += 1; state.saving = false;
    state.roles = clone(baseRoles); state.users = clone(baseUsers); state.documents = clone(baseDocuments);
    state.selectedRoleId = 'DEMO-role-clerk'; state.selectedUserId = 'DEMO-user-employee'; state.roleSearch = ''; state.catalogSearch = '';
    state.scenario = 'normal'; document.getElementById('scenario').value = 'normal';
    setIdentity('DEMO-user-admin'); loadRoleDraft(); loadUserDraft();
    if (path() !== '/roles') location.hash = '/roles'; else render();
    toast('演示数据已重置。');
  }
  function handleDocument(action, id) {
    var required = { 'create-document': 'oa:document:create', 'edit-document': 'oa:document:edit', 'approve-document': 'oa:document:approve' }[action];
    if (!allowed(required)) { toast('当前身份没有这项操作权限。', true); render(); return; }
    if (state.scenario === 'save-error' || state.scenario === 'conflict') { toast('模拟操作失败，文档保持原样。恢复正常数据后重试。', true); return; }
    if (state.scenario === 'empty') { toast('请先恢复正常数据，再执行文档操作。'); return; }
    if (action === 'create-document') {
      var number = state.documents.length + 1;
      state.documents.unshift({ id: 'DEMO-doc-' + String(number).padStart(3, '0'), title: '新增工作记录（演示）', department: findUser(state.identity).department, status: '草稿' });
      toast('已新建一份演示文档。');
    } else {
      var doc = state.documents.find(function (item) { return item.id === id; });
      if (!doc) return;
      doc.status = action === 'edit-document' ? '修改草稿' : '已审批';
      toast(action === 'edit-document' ? '演示文档已调整为修改草稿。' : '演示文档已审批。');
    }
    render();
  }

  // 容器事件委托在 innerHTML 重绘后仍有效；所有写动作仍再次判断权限，不依赖控件禁用。
  main.addEventListener('click', function (event) {
    var roleButton = event.target.closest('[data-role-id]');
    if (roleButton && !state.saving) { state.selectedRoleId = roleButton.dataset.roleId; loadRoleDraft(); render(); return; }
    var userButton = event.target.closest('[data-user-id]');
    if (userButton && !state.saving) { state.selectedUserId = userButton.dataset.userId; loadUserDraft(); render(); return; }
    var target = event.target.closest('[data-action]');
    if (!target || target.disabled) return;
    var action = target.dataset.action;
    if (action === 'save-role') save('role');
    else if (action === 'save-user') save('user');
    else if (action === 'cancel-role') { loadRoleDraft(); render(); toast('未保存的授权修改已取消。'); }
    else if (action === 'cancel-user') { loadUserDraft(); render(); toast('未保存的角色分配已取消。'); }
    else if (action === 'clear-role' && allowed('rbac:role:grant') && !state.saving) { state.roleDraft = []; state.message = ''; render(); }
    else if (action === 'reload') { if (path() === '/roles') loadRoleDraft(); else loadUserDraft(); render(); }
    else if (action === 'normal-state') setScenario('normal');
    else if (action === 'admin-identity') setIdentity('DEMO-user-admin');
    else if (action.endsWith('-document')) handleDocument(action, target.dataset.document);
  });
  main.addEventListener('change', function (event) {
    if (event.target.dataset.permission && allowed('rbac:role:grant') && !state.saving) {
      var code = event.target.dataset.permission;
      state.roleDraft = state.roleDraft.filter(function (item) { return item !== code; });
      if (event.target.checked) state.roleDraft.push(code);
      state.message = ''; render();
    }
    if (event.target.dataset.assignedRole && allowed('rbac:user:assign-role') && !state.saving) {
      var id = event.target.dataset.assignedRole;
      state.userDraft = state.userDraft.filter(function (item) { return item !== id; });
      if (event.target.checked) state.userDraft.push(id);
      state.message = ''; render();
    }
  });
  main.addEventListener('input', function (event) {
    if (event.target.id === 'role-search') { state.roleSearch = event.target.value; render(); }
    if (event.target.id === 'catalog-search') { state.catalogSearch = event.target.value; render(); }
  });
  var identity = document.getElementById('identity');
  identity.innerHTML = baseUsers.map(function (user) { return '<option value="' + user.id + '">' + user.name + '</option>'; }).join('') + '<option value="anonymous">未登录访客</option>';
  identity.value = state.identity;
  identity.addEventListener('change', function () { setIdentity(identity.value); });
  document.getElementById('scenario').addEventListener('change', function (event) { setScenario(event.target.value); });
  document.getElementById('reset-demo').addEventListener('click', reset);
  window.addEventListener('hashchange', function () { state.message = ''; render(); });
  if (!location.hash) location.hash = '/roles';
  render();

  // 只读快照便于课堂验收与自动化检查，不暴露修改已保存授权的入口。
  window.Rbac0Demo = Object.freeze({ snapshot: function () { return clone({ roles: state.roles, users: state.users, session: session(), path: path(), scenario: state.scenario }); } });
}());
