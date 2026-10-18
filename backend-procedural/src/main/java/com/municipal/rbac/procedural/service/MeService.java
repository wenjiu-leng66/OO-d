package com.municipal.rbac.procedural.service;

import static com.municipal.rbac.procedural.vo.Views.map;

import com.municipal.rbac.procedural.mapper.*;
import com.municipal.rbac.procedural.po.UserPO;
import jakarta.servlet.http.HttpSession;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class MeService {
  private final UserMapper users;
  private final RoleMapper roles;
  private final DepartmentMapper departments;
  private final AuthorizationService authorization;

  public MeService(
      UserMapper users,
      RoleMapper roles,
      DepartmentMapper departments,
      AuthorizationService authorization) {
    this.users = users;
    this.roles = roles;
    this.departments = departments;
    this.authorization = authorization;
  }

  public Map<String, Object> userView(UserPO user) {
    return map(
        "id",
        user.id(),
        "username",
        user.username(),
        "name",
        user.name(),
        "departmentId",
        user.departmentId(),
        "departmentName",
        departments.findById(user.departmentId()).map(d -> d.name()).orElse(null),
        "position",
        user.position(),
        "phone",
        user.phone(),
        "email",
        user.email(),
        "status",
        user.enabled() ? "ENABLED" : "DISABLED",
        "mustChangePassword",
        user.mustChangePassword(),
        "roleVersion",
        user.roleVersion(),
        "roleIds",
        users.roleIds(user.id(), false));
  }

  public Map<String, Object> me(HttpSession session) {
    return userView(authorization.current(session));
  }

  public Map<String, Object> permissions(HttpSession session) {
    var user = authorization.ready(session);
    var p = authorization.effectivePermissions(user.id());
    Map<String, List<Map<String, Object>>> sources = new HashMap<>();
    for (String id : p.roleIds()) {
      var role = roles.findById(id).orElseThrow();
      Map<String, Object> source = map("id", id, "code", role.code(), "name", role.name());
      for (var g : roles.findGrants(id)) {
        sources.computeIfAbsent(g.permissionCode(), k -> new ArrayList<>()).add(source);
        for (String derived : AuthorizationService.managementCodes(g.permissionCode())) {
          var list = sources.computeIfAbsent(derived, k -> new ArrayList<>());
          if (!list.contains(source)) list.add(source);
        }
      }
    }
    List<Map<String, Object>> scopes =
        p.permissionCodes().stream()
            .map(
                code ->
                    map(
                        "permissionCode",
                        code,
                        "scope",
                        p.scopes().get(code),
                        "roles",
                        sources.getOrDefault(code, List.of())))
            .toList();
    return map(
        "userId",
        user.id(),
        "roleIds",
        p.roleIds(),
        "permissionCodes",
        p.permissionCodes(),
        "scopes",
        scopes,
        "version",
        user.roleVersion());
  }
}
