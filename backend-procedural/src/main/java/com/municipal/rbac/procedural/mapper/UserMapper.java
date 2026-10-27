package com.municipal.rbac.procedural.mapper;

import com.municipal.rbac.procedural.po.UserPO;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.*;
import org.springframework.stereotype.Repository;

/**
 * 用户表及用户角色关联的 JDBC 访问层。业务权限由 Service 校验，此层仅执行参数绑定和数据操作。
 * 带 FOR UPDATE 或集合替换的方法依赖调用方事务，不能脱离 Service 事务单独实现原子性。
 */
@Repository
public class UserMapper {
  private final JdbcTemplate jdbc;

  public UserMapper(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final RowMapper<UserPO> ROW =
      (r, i) ->
          new UserPO(
              r.getString("id"),
              r.getString("username"),
              r.getString("real_name"),
              r.getString("dept_id"),
              r.getString("position"),
              r.getString("phone"),
              r.getString("email"),
              r.getInt("status") == 1,
              r.getString("password_hash"),
              r.getInt("must_change_password") == 1,
              r.getInt("role_version"));

  public Optional<UserPO> findById(String id) {
    if (!Ids.valid(id)) return Optional.empty();
    return jdbc.query("SELECT * FROM sys_user WHERE id=?", ROW, id).stream().findFirst();
  }

  /**
   * 工号或手机号登录共用参数化查询；唯一约束及导入规则负责保证账号标识的确定性。
   */
  public Optional<UserPO> findByAccount(String account) {
    return jdbc
        .query("SELECT * FROM sys_user WHERE username=? OR phone=?", ROW, account, account)
        .stream()
        .findFirst();
  }

  public List<UserPO> findAll() {
    return jdbc.query("SELECT * FROM sys_user ORDER BY id", ROW);
  }

  /**
   * 鉴权传 true，仅纳入启用角色；编辑分配集合传 false，保留已分配但停用的角色。
   */
  public List<String> roleIds(String id, boolean enabledOnly) {
    return jdbc.queryForList(
        "SELECT r.id FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=?"
            + (enabledOnly ? " AND r.status=1" : "")
            + " ORDER BY r.id",
        String.class,
        id);
  }

  /**
   * 保护规则需要识别完整分配关系，因此这里不按角色启停过滤。
   */
  public boolean hasRoleCode(String id, String code) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE"
                + " ur.user_id=? AND r.code=?",
            Integer.class,
            id,
            code)
        > 0;
  }

  /**
   * 新账户必须改密，初始密码已由 Service 编码；插入后读取数据库生成 ID，ID 对外统一为十进制字符串。
   */
  public String insert(
      String username,
      String name,
      String dept,
      String position,
      String phone,
      String email,
      String hash) {
    KeyHolder key = new GeneratedKeyHolder();
    jdbc.update(
        c -> {
          var ps =
              c.prepareStatement(
                  "INSERT INTO"
                      + " sys_user(username,real_name,dept_id,position,phone,email,password_hash,must_change_password)"
                      + " VALUES(?,?,?,?,?,?,?,1)",
                  new String[] {"id"});
          Object[] a = {username, name, dept, position, phone, email, hash};
          for (int i = 0; i < a.length; i++) ps.setObject(i + 1, a[i]);
          return ps;
        },
        key);
    return key.getKey().toString();
  }

  public boolean usernameExists(String value) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys_user WHERE username=?", Integer.class, value)
        > 0;
  }

  /**
   * null 手机号不参与重复检查；编辑本人资料时排除当前 ID，并发冲突仍由数据库唯一约束处理。
   */
  public boolean phoneExists(String value, String except) {
    return value != null
        && jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE phone=? AND id<>?",
                Integer.class,
                value,
                except == null ? "0" : except)
            > 0;
  }

  public void update(
      String id, String name, String dept, String position, String phone, String email) {
    jdbc.update(
        "UPDATE sys_user SET"
            + " real_name=?,dept_id=?,position=?,phone=?,email=?,updated_at=CURRENT_TIMESTAMP WHERE"
            + " id=?",
        name,
        dept,
        position,
        phone,
        email,
        id);
  }

  public void updateContact(String id, String phone, String email) {
    jdbc.update(
        "UPDATE sys_user SET phone=?,email=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
        phone,
        email,
        id);
  }

  public void status(String id, boolean enabled) {
    jdbc.update(
        "UPDATE sys_user SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
        enabled ? 1 : 0,
        id);
  }

  public void password(String id, String hash, boolean mustChange) {
    jdbc.update(
        "UPDATE sys_user SET password_hash=?,must_change_password=?,updated_at=CURRENT_TIMESTAMP"
            + " WHERE id=?",
        hash,
        mustChange ? 1 : 0,
        id);
  }

  public void loginTime(String id) {
    jdbc.update("UPDATE sys_user SET last_login_at=CURRENT_TIMESTAMP WHERE id=?", id);
  }

  /**
   * 先清空以该员工为负责人的引用，再清理用户角色关联，最后删除员工；三个动作由上层事务包裹。
   */
  public void delete(String id) {
    jdbc.update("UPDATE sys_dept SET manager_user_id=NULL WHERE manager_user_id=?", id);
    jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", id);
    jdbc.update("DELETE FROM sys_user WHERE id=?", id);
  }

  /**
   * 以“ID + 旧版本”做比较并递增（CAS），这是角色集合事务的首个写操作。
   * 并发事务等待后使用旧版本更新将影响零行，Service 据此返回 409，避免静默覆盖。
   */
  public boolean compareAndIncrement(String id, int version) {
    return jdbc.update(
            "UPDATE sys_user SET role_version=role_version+1 WHERE id=? AND role_version=?",
            id,
            version)
        == 1;
  }

  /**
   * 冲突后用锁定读取得当前提交版本，避免 REPEATABLE_READ 快照返回旧值；调用方已处于事务中。
   */
  public int currentRoleVersion(String id) {
    return jdbc.queryForObject(
        "SELECT role_version FROM sys_user WHERE id=? FOR UPDATE", Integer.class, id);
  }

  /**
   * 整组替换而非增量追加。调用方须先锁定相关角色、校验版本并持有事务，失败才能连同版本一起回滚。
   */
  public void replaceRoles(String id, List<String> roles) {
    jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", id);
    for (String role : roles)
      jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", id, role);
  }
}
