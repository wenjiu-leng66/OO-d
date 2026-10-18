package com.municipal.rbac.procedural.service;

import static com.municipal.rbac.procedural.vo.Views.*;

import com.municipal.rbac.procedural.dto.Requests;
import com.municipal.rbac.procedural.error.ApiException;
import com.municipal.rbac.procedural.mapper.*;
import com.municipal.rbac.procedural.po.*;
import jakarta.servlet.http.HttpSession;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
  private final UserMapper users;
  private final RoleMapper roles;
  private final DepartmentMapper departments;
  private final AuthorizationService authorization;
  private final MeService me;
  private final PasswordEncoder passwords;
  private final String initialPassword;

  public UserService(
      UserMapper users,
      RoleMapper roles,
      DepartmentMapper departments,
      AuthorizationService authorization,
      MeService me,
      PasswordEncoder passwords,
      @Value("${rbac.initial-password:}") String initialPassword) {
    this.users = users;
    this.roles = roles;
    this.departments = departments;
    this.authorization = authorization;
    this.me = me;
    this.passwords = passwords;
    this.initialPassword = initialPassword;
  }

  public static void paging(int page, int size) {
    if (page < 1 || page > 1000000 || size < 1 || size > 100)
      throw ApiException.badRequest("VALIDATION", "page须为1至1000000，pageSize须为1至100。");
  }

  public Map<String, Object> list(
      HttpSession session,
      String keyword,
      String departmentId,
      String roleId,
      String username,
      String name,
      int page,
      int size) {
    String scope = authorization.require(session, "rbac:user:read");
    var actor = authorization.current(session);
    paging(page, size);
    var subtree = departmentId == null ? null : departments.subtree(departmentId);
    Set<String> roleUsers = roleId == null ? null : new HashSet<>(roles.userIds(roleId));
    List<UserPO> result =
        users.findAll().stream()
            .filter(authorization.visible(actor, scope))
            .filter(u -> subtree == null || subtree.contains(u.departmentId()))
            .filter(u -> roleUsers == null || roleUsers.contains(u.id()))
            .filter(u -> contains(u.username(), username) && contains(u.name(), name))
            .filter(
                u ->
                    keyword == null
                        || contains(u.username(), keyword)
                        || contains(u.name(), keyword)
                        || contains(u.position(), keyword))
            .toList();
    return page(result, page, size, me::userView);
  }

  public static boolean contains(String text, String term) {
    return term == null
        || term.isBlank()
        || text != null && text.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT));
  }

  public Map<String, Object> detail(HttpSession session, String id) {
    return me.userView(authorization.target(session, "rbac:user:read", id));
  }

  private void department(HttpSession session, String code, String id) {
    String scope = authorization.require(session, code);
    var actor = authorization.current(session);
    if (id == null
        || departments.findById(id).isEmpty()
        || !authorization.coversDepartment(actor, scope, id))
      throw AuthorizationService.outOfScope();
  }

  private void unique(String username, String phone, String except) {
    if (username != null && users.usernameExists(username))
      throw ApiException.conflict("DUPLICATE_USERNAME", "工号已存在。", null);
    if (users.phoneExists(phone, except))
      throw ApiException.conflict("DUPLICATE_PHONE", "手机号已存在。", null);
  }

  private String initialHash() {
    if (initialPassword.length() < 8)
      throw ApiException.conflict(
          "INITIAL_PASSWORD_NOT_CONFIGURED", "请由部署人员设置RBAC_INITIAL_PASSWORD（至少8位）。", null);
    return passwords.encode(initialPassword);
  }

  @Transactional
  public Map<String, Object> create(HttpSession session, Requests.CreateUser r) {
    if ("SELF".equals(authorization.require(session, "rbac:user:create")))
      throw AuthorizationService.outOfScope();
    department(session, "rbac:user:create", r.departmentId());
    String phone = normalize(r.phone());
    unique(r.username(), phone, null);
    String id =
        users.insert(
            r.username(),
            r.name(),
            r.departmentId(),
            normalize(r.position()),
            phone,
            normalize(r.email()),
            initialHash());
    return me.userView(users.findById(id).orElseThrow());
  }

  @Transactional
  public Map<String, Object> update(HttpSession session, String id, Requests.UpdateUser r) {
    authorization.target(session, "rbac:user:update", id);
    department(session, "rbac:user:update", r.departmentId());
    unique(null, normalize(r.phone()), id);
    users.update(
        id,
        r.name(),
        r.departmentId(),
        normalize(r.position()),
        normalize(r.phone()),
        normalize(r.email()));
    return me.userView(users.findById(id).orElseThrow());
  }

  private void protectedAccount(HttpSession session, UserPO target) {
    if (authorization.current(session).id().equals(target.id())
        || "admin".equals(target.username())
        || users.hasRoleCode(target.id(), "ADMIN"))
      throw ApiException.conflict("PROTECTED_ACCOUNT", "不能停用、删除自己或修改自己角色；超级管理员账号受到保护。", null);
  }

  @Transactional
  public Map<String, Object> status(HttpSession session, String id, Requests.Status r) {
    var target = authorization.target(session, "rbac:user:update", id);
    if ("DISABLED".equals(r.status())) protectedAccount(session, target);
    users.status(id, "ENABLED".equals(r.status()));
    return me.userView(users.findById(id).orElseThrow());
  }

  @Transactional
  public void delete(HttpSession session, String id) {
    var target = authorization.target(session, "rbac:user:delete", id);
    protectedAccount(session, target);
    users.delete(id);
  }

  @Transactional
  public void reset(HttpSession session, String id) {
    var target = authorization.target(session, "rbac:user:update", id);
    if (!authorization.superAdmin(authorization.current(session))
        && (users.hasRoleCode(target.id(), "ADMIN")
            || users.hasRoleCode(target.id(), "DEPT_ADMIN")))
      throw ApiException.forbidden("不能重置管理类账号密码。");
    users.password(id, initialHash(), true);
  }

  public Map<String, Object> userRoles(HttpSession session, String id) {
    authorization.target(session, "rbac:user:read", id);
    var user = users.findById(id).orElseThrow();
    return assignment(user);
  }

  private Map<String, Object> assignment(UserPO user) {
    return map(
        "userId",
        user.id(),
        "roleIds",
        users.roleIds(user.id(), false),
        "version",
        user.roleVersion());
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Map<String, Object> replaceRoles(HttpSession session, String id, Requests.Roles r) {
    var target = authorization.target(session, "rbac:user:assign-role", id);
    protectedAccount(session, target);
    if (new HashSet<>(r.roleIds()).size() != r.roleIds().size())
      throw ApiException.badRequest("VALIDATION", "roleIds含重复元素。");
    List<String> before = users.roleIds(id, false);
    // The common lock order is role IDs ascending, followed by the target user CAS.
    Set<String> lockIds = new TreeSet<>(Comparator.comparingLong(Long::parseLong));
    lockIds.addAll(before);
    lockIds.addAll(r.roleIds());
    for (String roleId : lockIds)
      roles
          .lockById(roleId)
          .orElseThrow(() -> ApiException.badRequest("VALIDATION", "Unknown role."));
    Set<String> changed = new HashSet<>(before);
    for (String roleId : r.roleIds()) if (!changed.add(roleId)) changed.remove(roleId);
    boolean superAdmin = authorization.superAdmin(authorization.current(session));
    for (String roleId : r.roleIds()) {
      var role =
          roles.findById(roleId).orElseThrow(() -> ApiException.badRequest("VALIDATION", "未知角色。"));
      if ("ADMIN".equals(role.code())) throw ApiException.forbidden("不能将超级管理员角色授予其他账号。");
    }
    if (!superAdmin)
      for (String roleId : changed) {
        var role =
            roles
                .findById(roleId)
                .orElseThrow(() -> ApiException.badRequest("VALIDATION", "未知角色。"));
        if (authorization.adminRole(role)) throw ApiException.forbidden("只能分配或撤销业务角色。");
      }
    if (!users.compareAndIncrement(id, r.version())) {
      int current = users.currentRoleVersion(id);
      throw ApiException.conflict(
          "VERSION_CONFLICT", "角色集合已改变，请刷新后重试。", Map.of("currentVersion", current));
    }
    users.replaceRoles(id, r.roleIds());
    return assignment(users.findById(id).orElseThrow());
  }

  public static String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
