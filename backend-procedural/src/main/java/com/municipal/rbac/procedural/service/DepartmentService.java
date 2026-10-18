package com.municipal.rbac.procedural.service;

import static com.municipal.rbac.procedural.vo.Views.*;

import com.municipal.rbac.procedural.mapper.*;
import com.municipal.rbac.procedural.po.*;
import jakarta.servlet.http.HttpSession;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class DepartmentService {
  private final DepartmentMapper departments;
  private final UserMapper users;
  private final AuthorizationService authorization;
  private final MeService me;

  public DepartmentService(
      DepartmentMapper departments,
      UserMapper users,
      AuthorizationService authorization,
      MeService me) {
    this.departments = departments;
    this.users = users;
    this.authorization = authorization;
    this.me = me;
  }

  public List<Map<String, Object>> tree(HttpSession session) {
    String scope = authorization.require(session, "rbac:department:read");
    var actor = authorization.current(session);
    Set<String> allowed =
        "DEPT".equals(scope) ? departments.subtree(actor.departmentId()) : Set.of();
    List<DepartmentPO> visible =
        departments.findAll().stream()
            .filter(
                d ->
                    "ALL".equals(scope)
                        || "DEPT".equals(scope) && allowed.contains(d.id())
                        || "SELF".equals(scope) && Objects.equals(actor.departmentId(), d.id()))
            .toList();
    Set<String> ids = new HashSet<>();
    visible.forEach(d -> ids.add(d.id()));
    List<UserPO> staff = users.findAll();
    return visible.stream()
        .filter(d -> d.parentId() == null || !ids.contains(d.parentId()))
        .map(d -> node(d, visible, staff))
        .toList();
  }

  private Map<String, Object> node(DepartmentPO d, List<DepartmentPO> visible, List<UserPO> staff) {
    var subtree = departments.subtree(d.id());
    return map(
        "id",
        d.id(),
        "name",
        d.name(),
        "shortName",
        d.shortName(),
        "type",
        d.type(),
        "parentId",
        d.parentId(),
        "parentName",
        departments.findById(d.parentId()).map(DepartmentPO::name).orElse(null),
        "managerUserId",
        d.managerId(),
        "managerName",
        d.managerId() == null ? null : users.findById(d.managerId()).map(UserPO::name).orElse(null),
        "employeeCount",
        staff.stream().filter(u -> Objects.equals(u.departmentId(), d.id())).count(),
        "totalCount",
        staff.stream().filter(u -> subtree.contains(u.departmentId())).count(),
        "children",
        visible.stream()
            .filter(c -> Objects.equals(c.parentId(), d.id()))
            .map(c -> node(c, visible, staff))
            .toList());
  }

  public Map<String, Object> members(HttpSession session, String id, int page, int size) {
    String scope = authorization.require(session, "rbac:department:read");
    if (!authorization.coversDepartment(authorization.current(session), scope, id)
        || departments.findById(id).isEmpty()) throw AuthorizationService.outOfScope();
    UserService.paging(page, size);
    var ids = departments.subtree(id);
    return page(
        users.findAll().stream()
            .filter(u -> ids.contains(u.departmentId()))
            .filter(authorization.visible(authorization.current(session), scope))
            .toList(),
        page,
        size,
        me::userView);
  }
}
