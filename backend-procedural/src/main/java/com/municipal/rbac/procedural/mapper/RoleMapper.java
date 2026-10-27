package com.municipal.rbac.procedural.mapper;

import com.municipal.rbac.procedural.po.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.*;
import org.springframework.stereotype.Repository;

/**
 * 角色及授权关联的 JDBC 访问层；行锁、乐观版本与关联集合由 Service 在同一事务中协调。
 */
@Repository
public class RoleMapper {
  private final JdbcTemplate jdbc;

  public RoleMapper(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final RowMapper<RolePO> ROW =
      (r, i) ->
          new RolePO(
              r.getString("id"),
              r.getString("code"),
              r.getString("name"),
              r.getString("description"),
              r.getInt("status") == 1,
              r.getInt("builtin") == 1,
              r.getInt("permission_version"));

  public Optional<RolePO> findById(String id) {
    if (!Ids.valid(id)) return Optional.empty();
    return jdbc.query("SELECT * FROM sys_role WHERE id=?", ROW, id).stream().findFirst();
  }

  /**
   * 角色分配、授权修改和删除共用这一行锁；跨角色操作须按数字 ID 升序取锁。
   */
  public Optional<RolePO> lockById(String id) {
    if (!Ids.valid(id)) return Optional.empty();
    return jdbc.query("SELECT * FROM sys_role WHERE id=? FOR UPDATE", ROW, id).stream().findFirst();
  }

  /**
   * 锁定读返回冲突时的当前 permissionVersion，用于 409 响应而不写入任何授权。
   */
  public int currentVersion(String id) {
    return jdbc.queryForObject(
        "SELECT permission_version FROM sys_role WHERE id=? FOR UPDATE", Integer.class, id);
  }

  /**
   * 持有角色行锁后锁定其关联用户集合，保证“仍有关联用户”检查与删除使用同一稳定集合。
   */
  public List<String> lockedUserIds(String id) {
    return jdbc.queryForList(
        "SELECT user_id FROM sys_user_role WHERE role_id=? ORDER BY user_id FOR UPDATE",
        String.class,
        id);
  }

  public List<RolePO> findAll() {
    return jdbc.query("SELECT * FROM sys_role ORDER BY sort_no,id", ROW);
  }

  /**
   * 查询角色的原始清单授权及逐码 scope；派生 rbac:* 码只在 AuthorizationService 中产生。
   */
  public List<RolePermissionPO> findGrants(String id) {
    return jdbc.query(
        "SELECT rp.role_id,p.code,rp.data_scope FROM sys_role_permission rp JOIN sys_permission p"
            + " ON p.id=rp.permission_id WHERE rp.role_id=? ORDER BY p.id",
        (r, i) -> new RolePermissionPO(r.getString(1), r.getString(2), r.getString(3)),
        id);
  }

  public List<String> userIds(String id) {
    if (!Ids.valid(id)) return List.of();
    return jdbc.queryForList(
        "SELECT user_id FROM sys_user_role WHERE role_id=? ORDER BY user_id", String.class, id);
  }

  public boolean codeExists(String code) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM sys_role WHERE code=?", Integer.class, code)
        > 0;
  }

  public String insert(String code, String name, String description) {
    KeyHolder key = new GeneratedKeyHolder();
    jdbc.update(
        c -> {
          var p =
              c.prepareStatement(
                  "INSERT INTO sys_role(code,name,description) VALUES(?,?,?)", new String[] {"id"});
          p.setString(1, code);
          p.setString(2, name);
          p.setString(3, description);
          return p;
        },
        key);
    return key.getKey().toString();
  }

  public void update(String id, String name, String description) {
    jdbc.update("UPDATE sys_role SET name=?,description=? WHERE id=?", name, description, id);
  }

  public void status(String id, boolean enabled) {
    jdbc.update("UPDATE sys_role SET status=? WHERE id=?", enabled ? 1 : 0, id);
  }

  /**
   * 调用方持有与分配流程共用的角色行锁。先递增受影响用户的角色版本，
   * 再删角色授权、用户关联和角色本体；同一事务失败时全部回滚。
   */
  public void delete(String id, List<String> userIds) {
    for (String userId : userIds)
      jdbc.update("UPDATE sys_user SET role_version=role_version+1 WHERE id=?", userId);
    jdbc.update("DELETE FROM sys_role_permission WHERE role_id=?", id);
    jdbc.update("DELETE FROM sys_user_role WHERE role_id=?", id);
    jdbc.update("DELETE FROM sys_role WHERE id=?", id);
  }

  /**
   * 只允许期望版本匹配的事务增加授权版本；返回 false 表示客户端草稿过期，不能继续替换 grants。
   */
  public boolean compareAndIncrement(String id, int version) {
    return jdbc.update(
            "UPDATE sys_role SET permission_version=permission_version+1 WHERE id=? AND"
                + " permission_version=?",
            id,
            version)
        == 1;
  }

  /**
   * 在已有事务中先清空再按权限码查 ID 插入；已知码及 read 依赖必须由 Service 先验证。
   * 不能把该方法单独当作“自动事务”，否则中途失败会留下部分授权。
   */
  public void replaceGrants(
      String id, List<com.municipal.rbac.procedural.dto.Requests.Grant> grants) {
    jdbc.update("DELETE FROM sys_role_permission WHERE role_id=?", id);
    for (var g : grants)
      jdbc.update(
          "INSERT INTO sys_role_permission(role_id,permission_id,data_scope) SELECT ?,id,? FROM"
              + " sys_permission WHERE code=?",
          id,
          g.scope(),
          g.code());
  }
}
