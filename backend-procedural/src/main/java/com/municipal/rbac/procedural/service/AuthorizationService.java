package com.municipal.rbac.procedural.service;

import com.municipal.rbac.procedural.error.ApiException;
import com.municipal.rbac.procedural.mapper.*;
import com.municipal.rbac.procedural.po.*;
import jakarta.servlet.http.HttpSession;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * 一阶段 RBAC0 的统一判定入口：实时身份 → 启用的直接角色 → 同码权限并集 → 数据范围 → 目标对象。
 * 会话不缓存授权结果，因此撤角色、停用角色和修改授权会在下一次请求重新计算。
 * 此处不实现二期继承、三期激活角色或 SSD/DSD 约束；无匹配权限或未知范围均默认拒绝。
 */
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

  /**
   * 重新查库确认账号仍存在、启用且密码摘要与登录时一致。
   * 密码变更或管理员重置会使旧会话在下一次请求失效；摘要只作服务端凭据版本标记，不返回客户端。
   */
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

  /**
   * 业务接口的登录前置条件。首次或重置密码后的会话仍可访问本人信息、改密和退出，
   * 但调用此方法的权限查询及其他业务入口必须先完成改密。
   */
  public UserPO ready(HttpSession session) {
    UserPO u = current(session);
    if (u.mustChangePassword())
      throw ApiException.forbidden("PASSWORD_CHANGE_REQUIRED", "请先修改初始密码。");
    return u;
  }

  /**
   * 仅合并启用的直接角色。同一权限码取 ALL > DEPT > SELF，互不相同的权限码不互相放大范围。
   * 原始清单码与派生管理码保留相同 scope；没有角色时返回空权限集合，而不是默认管理员。
   */
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

  /**
   * 校验认证和首次改密状态，再要求指定动作的权限，返回该动作自己的数据范围。
   * 拥有 read 不代表拥有 edit；服务层不能只凭前端按钮状态放行写请求。
   */
  public String require(HttpSession session, String code) {
    UserPO u = ready(session);
    String scope = effectivePermissions(u.id()).scopes().get(code);
    if (scope == null) throw ApiException.forbidden("没有执行该操作的权限。");
    return scope;
  }

  /**
   * 对单个员工对象执行“权限 → 查找目标 → 范围”的检查。
   * 不存在和范围外使用同一拒绝响应，避免通过替换 URL 中的 ID 探测他人是否存在。
   */
  public UserPO target(HttpSession session, String code, String id) {
    String scope = require(session, code);
    UserPO actor = current(session);
    UserPO target = users.findById(id).orElse(null);
    if (target == null || !covers(actor, scope, target)) throw outOfScope();
    return target;
  }

  /**
   * 列表过滤器必须在业务筛选、统计和分页之前执行，防止 total 或页边界泄露范围外记录。
   * DEPT 的子树一次取出供整批过滤使用；SELF 始终指员工本人，不能解释为同部门所有人。
   */
  public java.util.function.Predicate<UserPO> visible(UserPO actor, String scope) {
    Set<String> ids = "DEPT".equals(scope) ? departments.subtree(actor.departmentId()) : Set.of();
    return u ->
        "ALL".equals(scope)
            || "SELF".equals(scope) && actor.id().equals(u.id())
            || "DEPT".equals(scope) && ids.contains(u.departmentId());
  }

  /**
   * 员工对象的范围检查。未知 scope 显式返回 false，不能把异常输入当作 ALL。
   */
  public boolean covers(UserPO actor, String scope, UserPO target) {
    return switch (scope) {
      case "ALL" -> true;
      case "SELF" -> actor.id().equals(target.id());
      case "DEPT" -> departments.subtree(actor.departmentId()).contains(target.departmentId());
      default -> false;
    };
  }

  /**
   * 判断“部门节点”是否在范围内。SELF 允许定位自己的部门节点，
   * 但不代表可看该部门的全部员工；成员查询仍需 visible，新增他人账号另由 UserService 拒绝 SELF。
   */
  public boolean coversDepartment(UserPO actor, String scope, String department) {
    return "ALL".equals(scope)
        || ("DEPT".equals(scope) && departments.subtree(actor.departmentId()).contains(department))
        || ("SELF".equals(scope) && Objects.equals(actor.departmentId(), department));
  }

  /**
   * 超级管理员身份只来自当前启用的 ADMIN 角色；不能仅看用户名或前端身份标签。
   */
  public boolean superAdmin(UserPO user) {
    return users.roleIds(user.id(), true).stream()
        .map(roles::findById)
        .flatMap(Optional::stream)
        .anyMatch(r -> "ADMIN".equals(r.code()));
  }

  /**
   * 管理类角色既包括内置 ADMIN/DEPT_ADMIN，也包括含管理清单权限的自建角色。
   * 避免部门管理员通过复制管理权限到另一个角色编码，再将其分配给员工而越权。
   */
  public boolean adminRole(RolePO role) {
    return Set.of("ADMIN", "DEPT_ADMIN").contains(role.code())
        || roles.findGrants(role.id()).stream()
            .anyMatch(g -> !managementCodes(g.permissionCode()).isEmpty());
  }

  public static ApiException outOfScope() {
    return ApiException.forbidden("OUT_OF_SCOPE", "目标不在允许的数据范围内或不存在。");
  }

  /**
   * 范围是同一权限码上的最大值合并，不是角色继承；例如 read=SELF 与 edit=ALL 仍分别保留。
   */
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

  /**
   * 把清单系统的固定功能码映射为后端管理动作；功能编号必须与导入 CSV/OpenAPI 保持一致。
   * 派生 rbac:* 码不写入 sys_permission，也不接受直接授予；无映射时返回空集合。
   */
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
