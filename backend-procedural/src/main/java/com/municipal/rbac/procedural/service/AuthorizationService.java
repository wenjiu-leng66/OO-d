package com.municipal.rbac.procedural.service;

import com.municipal.rbac.procedural.error.ApiException;
import com.municipal.rbac.procedural.mapper.*;
import com.municipal.rbac.procedural.po.*;
import jakarta.servlet.http.HttpSession;
import java.util.*;
import org.springframework.stereotype.Service;

/** One RBAC0 decision path: live identity -> enabled roles -> union -> scope -> target. */
@Service
public class AuthorizationService {
  public static final String SESSION_USER = "userId", SESSION_CREDENTIAL = "credential";
  private final UserMapper users;
  private final RoleMapper roles;
  private final DepartmentMapper departments;

  public AuthorizationService(UserMapper users, RoleMapper roles, DepartmentMapper departments) {
    this.users = users;
    this.roles = roles;
    this.departments = departments;
  }

  public UserPO current(HttpSession session) {
    Object id = session.getAttribute(SESSION_USER);
    UserPO user = id instanceof String s ? users.findById(s).orElse(null) : null;
    if (user == null
        || !user.enabled()
        || !Objects.equals(session.getAttribute(SESSION_CREDENTIAL), user.passwordHash())) {
      session.invalidate();
      throw ApiException.unauthorized("UNAUTHENTICATED", "请先登录。");
    }
    return user;
  }

  public String requireUserId(HttpSession session) {
    return current(session).id();
  }

  public UserPO ready(HttpSession session) {
    UserPO u = current(session);
    if (u.mustChangePassword())
      throw ApiException.forbidden("PASSWORD_CHANGE_REQUIRED", "请先修改初始密码。");
    return u;
  }

  public EffectivePermissions effectivePermissions(String userId) {
    List<String> ids = users.roleIds(userId, true);
    Map<String, String> scopes = new LinkedHashMap<>();
    for (String id : ids)
      for (var g : roles.findGrants(id)) {
        scopes.merge(g.permissionCode(), g.scope(), AuthorizationService::widerScope);
        for (String mapped : managementCodes(g.permissionCode()))
          scopes.merge(mapped, g.scope(), AuthorizationService::widerScope);
      }
    return new EffectivePermissions(ids, List.copyOf(scopes.keySet()), Map.copyOf(scopes));
  }

  public String require(HttpSession session, String code) {
    UserPO u = ready(session);
    String scope = effectivePermissions(u.id()).scopes().get(code);
    if (scope == null) throw ApiException.forbidden("没有执行该操作的权限。");
    return scope;
  }

  public UserPO target(HttpSession session, String code, String id) {
    String scope = require(session, code);
    UserPO actor = current(session);
    UserPO target = users.findById(id).orElse(null);
    if (target == null || !covers(actor, scope, target)) throw outOfScope();
    return target;
  }

  public java.util.function.Predicate<UserPO> visible(UserPO actor, String scope) {
    Set<String> ids = "DEPT".equals(scope) ? departments.subtree(actor.departmentId()) : Set.of();
    return u ->
        "ALL".equals(scope)
            || "SELF".equals(scope) && actor.id().equals(u.id())
            || "DEPT".equals(scope) && ids.contains(u.departmentId());
  }

  public boolean covers(UserPO actor, String scope, UserPO target) {
    return switch (scope) {
      case "ALL" -> true;
      case "SELF" -> actor.id().equals(target.id());
      case "DEPT" -> departments.subtree(actor.departmentId()).contains(target.departmentId());
      default -> false;
    };
  }

  public boolean coversDepartment(UserPO actor, String scope, String department) {
    return "ALL".equals(scope)
        || ("DEPT".equals(scope) && departments.subtree(actor.departmentId()).contains(department))
        || ("SELF".equals(scope) && Objects.equals(actor.departmentId(), department));
  }

  public boolean superAdmin(UserPO user) {
    return users.roleIds(user.id(), true).stream()
        .map(roles::findById)
        .flatMap(Optional::stream)
        .anyMatch(r -> "ADMIN".equals(r.code()));
  }

  public boolean adminRole(RolePO role) {
    return Set.of("ADMIN", "DEPT_ADMIN").contains(role.code())
        || roles.findGrants(role.id()).stream()
            .anyMatch(g -> !managementCodes(g.permissionCode()).isEmpty());
  }

  public static ApiException outOfScope() {
    return ApiException.forbidden("OUT_OF_SCOPE", "目标不在允许的数据范围内或不存在。");
  }

  public static String widerScope(String a, String b) {
    return rank(a) >= rank(b) ? a : b;
  }

  private static int rank(String scope) {
    return switch (scope) {
      case "ALL" -> 3;
      case "DEPT" -> 2;
      case "SELF" -> 1;
      default -> 0;
    };
  }

  public static List<String> managementCodes(String code) {
    return switch (code) {
      case "sys:f339:read" -> List.of("rbac:user:read");
      case "sys:f339:create" -> List.of("rbac:user:create");
      case "sys:f339:edit" -> List.of("rbac:user:update", "rbac:user:assign-role");
      case "sys:f339:delete" -> List.of("rbac:user:delete");
      case "sys:f340:read" -> List.of("rbac:role:read");
      case "sys:f340:create" -> List.of("rbac:role:create");
      case "sys:f340:edit" -> List.of("rbac:role:update");
      case "sys:f340:delete" -> List.of("rbac:role:delete");
      case "sys:f341:read" -> List.of("rbac:permission:read");
      case "sys:f341:edit" -> List.of("rbac:role:grant");
      case "sys:f342:read" -> List.of("rbac:department:read");
      case "sys:f348:read", "sys:f349:read" -> List.of("rbac:log:read");
      case "sys:f348:export", "sys:f349:export" -> List.of("rbac:log:export");
      default -> List.of();
    };
  }
}
