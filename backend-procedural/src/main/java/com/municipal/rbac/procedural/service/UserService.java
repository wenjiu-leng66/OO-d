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

/**
 * 用户管理流程：逐操作鉴权、员工/部门双重范围检查、账号保护与角色集合原子替换。
 * 事务负责保持数据库写入一致性；前端提交的角色、部门和版本都必须在服务端重新验证。
 */
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

  /**
   * 限制分页输入及单页上限，既避免无界返回，也确保后续 (page-1)*size 的 int 运算安全。
   */
  public static void paging(int page, int size) {
    if (page < 1 || page > 1000000 || size < 1 || size > 100)
      throw ApiException.badRequest("VALIDATION", "page须为1至1000000，pageSize须为1至100。");
  }

  /**
   * 先用真实权限限制可见员工，再叠加部门子树、角色和文本条件，最后计算 total 并分页。
   * 当前实现从数据库读取员工后在 Java 内过滤，正式全量性能须实测，不能把分页返回等同于数据库分页。
   */
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

  /**
   * 新建或迁移员工时，目标部门也必须存在且在该写动作的范围内。
   * 仅校验原员工范围不足以阻止把员工迁到范围外部门。
   */
  private void department(HttpSession session, String code, String id) {
    String scope = authorization.require(session, code);
    var actor = authorization.current(session);
    if (id == null
        || departments.findById(id).isEmpty()
        || !authorization.coversDepartment(actor, scope, id))
      throw AuthorizationService.outOfScope();
  }

  /**
   * 提供可读的重复工号/手机错误；并发写仍由数据库唯一约束兜底，统一异常处理再转为 409。
   */
  private void unique(String username, String phone, String except) {
    if (username != null && users.usernameExists(username))
      throw ApiException.conflict("DUPLICATE_USERNAME", "工号已存在。", null);
    if (users.phoneExists(phone, except))
      throw ApiException.conflict("DUPLICATE_PHONE", "手机号已存在。", null);
  }

  /**
   * 初始密码由部署环境注入，不使用写死密码或保存明文；缺配置时拒绝创建和重置。
   */
  private String initialHash() {
    if (initialPassword.length() < 8)
      throw ApiException.conflict(
          "INITIAL_PASSWORD_NOT_CONFIGURED", "请由部署人员设置RBAC_INITIAL_PASSWORD（至少8位）。", null);
    return passwords.encode(initialPassword);
  }

  /**
   * SELF 不能创建另一个人；DEPT/ALL 还须校验归属部门。
   * 只创建基本资料及“必须改密”账户，角色分配是另一项有独立权限和版本控制的操作。
   */
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

  /**
   * 同时检查原员工与迁入部门，且 DTO 不包含工号、密码或角色，避免基本资料接口顺便提权。
   */
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

  /**
   * 拒绝停用/删除自己或改自己角色，并保护 admin 用户名及所有持有 ADMIN 的账号。
   * 这里采用强保护，不只保护“最后一个管理员”；调用方在写入前执行。
   */
  private void protectedAccount(HttpSession session, UserPO target) {
    if (authorization.current(session).id().equals(target.id())
        || "admin".equals(target.username())
        || users.hasRoleCode(target.id(), "ADMIN"))
      throw ApiException.conflict("PROTECTED_ACCOUNT", "不能停用、删除自己或修改自己角色；超级管理员账号受到保护。", null);
  }

  /**
   * 启用与停用共用接口，但停用前必须执行账号保护。停用后 current 的实时检查使旧会话失效。
   */
  @Transactional
  public Map<String, Object> status(HttpSession session, String id, Requests.Status r) {
    var target = authorization.target(session, "rbac:user:update", id);
    if ("DISABLED".equals(r.status())) protectedAccount(session, target);
    users.status(id, "ENABLED".equals(r.status()));
    return me.userView(users.findById(id).orElseThrow());
  }

  /**
   * 在同一事务中解除负责人和用户角色关联再删除员工，避免孤立关系；保护账号不进入写入流程。
   */
  @Transactional
  public void delete(HttpSession session, String id) {
    var target = authorization.target(session, "rbac:user:delete", id);
    protectedAccount(session, target);
    users.delete(id);
  }

  /**
   * 重置为环境指定的初始密码并重新开启强制改密；非超级管理员不能重置 ADMIN/DEPT_ADMIN。
   * 密码摘要变化后被重置账号的旧会话失效，不依赖客户端主动退出。
   */
  @Transactional
  public void reset(HttpSession session, String id) {
    var target = authorization.target(session, "rbac:user:update", id);
    if (!authorization.superAdmin(authorization.current(session))
        && (users.hasRoleCode(target.id(), "ADMIN")
            || users.hasRoleCode(target.id(), "DEPT_ADMIN")))
      throw ApiException.forbidden("不能重置管理类账号密码。");
    users.password(id, initialHash(), true);
  }

  /**
   * 读取完整已分配集合（包含停用角色）与 roleVersion；保存时必须携带该版本，不能拿有效角色集合覆盖分配关系。
   */
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

  /**
   * 全集合替换用户角色：校验重复项/角色存在/管理类边界，再 CAS 增版本并重建关联。
   * READ_COMMITTED 配合统一角色行锁，使角色删除与分配互斥；任何异常都回滚集合和版本，旧版返回 409。
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Map<String, Object> replaceRoles(HttpSession session, String id, Requests.Roles r) {
    var target = authorization.target(session, "rbac:user:assign-role", id);
    protectedAccount(session, target);
    if (new HashSet<>(r.roleIds()).size() != r.roleIds().size())
      throw ApiException.badRequest("VALIDATION", "roleIds含重复元素。");
    List<String> before = users.roleIds(id, false);
    // 与角色删除共用角色行锁，按数字 ID 升序取得旧/新集合的并集，再写目标用户版本，降低交叉锁死风险。
    Set<String> lockIds = new TreeSet<>(Comparator.comparingLong(Long::parseLong));
    lockIds.addAll(before);
    lockIds.addAll(r.roleIds());
    for (String roleId : lockIds)
      roles
          .lockById(roleId)
          .orElseThrow(() -> ApiException.badRequest("VALIDATION", "Unknown role."));
    // 对称差集只包含新增或撤销的角色：部门管理员也不能借“撤销”管理角色修改别人的管理身份。
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
    // CAS 成功是替换集合的前提；失败时完全不删除旧关联，返回当前版本供调用方重新读取后再决定。
    if (!users.compareAndIncrement(id, r.version())) {
      int current = users.currentRoleVersion(id);
      throw ApiException.conflict(
          "VERSION_CONFLICT", "角色集合已改变，请刷新后重试。", Map.of("currentVersion", current));
    }
    users.replaceRoles(id, r.roleIds());
    return assignment(users.findById(id).orElseThrow());
  }

  /**
   * 可选文本的空串统一为 null；手机号唯一性因此不会把所有空手机号当成同一个值。
   */
  public static String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
