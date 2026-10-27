package com.municipal.rbac.procedural.service;

import static com.municipal.rbac.procedural.vo.Views.*;

import com.municipal.rbac.procedural.mapper.*;
import com.municipal.rbac.procedural.po.*;
import jakarta.servlet.http.HttpSession;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * 组织树和组织成员查询。一阶段组织结构由数据库/离线导入维护，此服务不开放任意改树。
 */
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

  /**
   * 先裁剪可见部门，再从其可见根节点构树。DEPT 以本人部门及下级为边界，
   * 即使上级节点不可见，也不能因此把整个可见子树丢失。
   */
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

  /**
   * employeeCount 为直属员工数，totalCount 含全部下级；children 只从已裁剪的可见节点中组装。
   * 这里假定离线导入已保证父子关系无环；负责人名称按负责人 ID 查询，不通过同名员工推断。
   */
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

  /**
   * 部门节点合法且在范围内仍不足以返回所有成员；还须逐员工执行 visible。
   * 这一步确保 SELF 只能返回本人，而不会把同部门同事一并暴露。
   */
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
