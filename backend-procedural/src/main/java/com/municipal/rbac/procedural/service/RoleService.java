package com.municipal.rbac.procedural.service;

import static com.municipal.rbac.procedural.vo.Views.*;

import com.municipal.rbac.procedural.dto.Requests;
import com.municipal.rbac.procedural.error.ApiException;
import com.municipal.rbac.procedural.mapper.*;
import com.municipal.rbac.procedural.po.*;
import jakarta.servlet.http.HttpSession;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {
  private final RoleMapper roles;
  private final UserMapper users;
  private final CatalogMapper catalog;
  private final AuthorizationService authorization;
  private final MeService me;

  public RoleService(
      RoleMapper roles,
      UserMapper users,
      CatalogMapper catalog,
      AuthorizationService authorization,
      MeService me) {
    this.roles = roles;
    this.users = users;
    this.catalog = catalog;
    this.authorization = authorization;
    this.me = me;
  }

  public RolePO requireRole(String id) {
    return roles.findById(id).orElseThrow(() -> ApiException.notFound("NOT_FOUND", "角色不存在。"));
  }

  private Map<String, Object> view(RolePO role) {
    return map(
        "id",
        role.id(),
        "code",
        role.code(),
        "name",
        role.name(),
        "description",
        role.description(),
        "enabled",
        role.enabled(),
        "builtin",
        role.builtin(),
        "adminRole",
        authorization.adminRole(role),
        "permissionVersion",
        role.permissionVersion(),
        "userCount",
        roles.userIds(role.id()).size(),
        "grantCount",
        roles.findGrants(role.id()).size());
  }

  public Map<String, Object> list(HttpSession session, String keyword, int page, int size) {
    authorization.require(session, "rbac:role:read");
    UserService.paging(page, size);
    return page(
        roles.findAll().stream()
            .filter(
                r ->
                    keyword == null
                        || UserService.contains(r.name(), keyword)
                        || UserService.contains(r.code(), keyword))
            .map(this::view)
            .toList(),
        page,
        size);
  }

  public Map<String, Object> detail(HttpSession session, String id) {
    authorization.require(session, "rbac:role:read");
    return view(requireRole(id));
  }

  @Transactional
  public Map<String, Object> create(HttpSession session, Requests.CreateRole r) {
    authorization.require(session, "rbac:role:create");
    if (roles.codeExists(r.code()))
      throw ApiException.conflict("DUPLICATE_ROLE_CODE", "角色编码已存在。", null);
    return view(
        requireRole(roles.insert(r.code(), r.name(), UserService.normalize(r.description()))));
  }

  @Transactional
  public Map<String, Object> update(HttpSession session, String id, Requests.UpdateRole r) {
    authorization.require(session, "rbac:role:update");
    requireRole(id);
    roles.update(id, r.name(), UserService.normalize(r.description()));
    return view(requireRole(id));
  }

  @Transactional
  public Map<String, Object> status(HttpSession session, String id, Requests.Enabled r) {
    authorization.require(session, "rbac:role:update");
    var role = requireRole(id);
    if ("ADMIN".equals(role.code()) && !r.enabled()) throw protectedRole();
    roles.status(id, r.enabled());
    return view(requireRole(id));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void delete(HttpSession session, String id, boolean confirm) {
    authorization.require(session, "rbac:role:delete");
    var role =
        roles.lockById(id).orElseThrow(() -> ApiException.notFound("NOT_FOUND", "Role not found."));
    if (role.builtin()) throw protectedRole();
    List<String> assignedUsers = roles.lockedUserIds(id);
    int count = assignedUsers.size();
    if (count > 0 && !confirm)
      throw ApiException.conflict("ROLE_HAS_USERS", "角色仍有用户，确认后才能删除。", Map.of("userCount", count));
    roles.delete(id, assignedUsers);
  }

  public Map<String, Object> roleUsers(HttpSession session, String id, int page, int size) {
    authorization.require(session, "rbac:role:read");
    requireRole(id);
    UserService.paging(page, size);
    var actor = authorization.current(session);
    String scope = authorization.effectivePermissions(actor.id()).scopes().get("rbac:user:read");
    return page(
        roles.userIds(id).stream()
            .map(users::findById)
            .flatMap(Optional::stream)
            .filter(authorization.visible(actor, scope))
            .toList(),
        page,
        size,
        me::userView);
  }

  private Map<String, Object> assignment(RolePO role) {
    return map(
        "roleId",
        role.id(),
        "grants",
        roles.findGrants(role.id()).stream()
            .map(g -> map("code", g.permissionCode(), "scope", g.scope()))
            .toList(),
        "version",
        role.permissionVersion(),
        "readOnly",
        "ADMIN".equals(role.code()));
  }

  public Map<String, Object> rolePermissions(HttpSession session, String id) {
    authorization.require(session, "rbac:role:read");
    authorization.require(session, "rbac:permission:read");
    return assignment(requireRole(id));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Map<String, Object> replacePermissions(HttpSession session, String id, Requests.Grants r) {
    authorization.require(session, "rbac:role:grant");
    var role =
        roles.lockById(id).orElseThrow(() -> ApiException.notFound("NOT_FOUND", "Role not found."));
    if ("ADMIN".equals(role.code())) throw protectedRole();
    Set<String> known = new HashSet<>(catalog.permissionCodes()), codes = new HashSet<>();
    for (var g : r.grants()) {
      if (!known.contains(g.code()))
        throw ApiException.badRequest("UNKNOWN_PERMISSION", "未知权限码，管理映射码不能直接授予。");
      if (!codes.add(g.code())) throw ApiException.badRequest("VALIDATION", "grants含重复权限码。");
    }
    for (String code : codes) {
      String read = code.substring(0, code.lastIndexOf(':')) + ":read";
      if (!code.endsWith(":read") && !codes.contains(read))
        throw ApiException.badRequest("READ_REQUIRED", "非查看操作必须同时授予该功能点的查看权限。");
    }
    if (!roles.compareAndIncrement(id, r.version()))
      throw ApiException.conflict(
          "VERSION_CONFLICT",
          "角色授权已改变，请刷新后重试。",
          Map.of("currentVersion", roles.currentVersion(id)));
    roles.replaceGrants(id, r.grants());
    return assignment(requireRole(id));
  }

  private ApiException protectedRole() {
    return ApiException.conflict("PROTECTED_ROLE", "内置角色不能删除，超级管理员角色不能停用或修改授权。", null);
  }
}
