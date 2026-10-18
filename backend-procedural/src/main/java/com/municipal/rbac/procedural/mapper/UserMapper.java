package com.municipal.rbac.procedural.mapper;

import com.municipal.rbac.procedural.po.UserPO;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.*;
import org.springframework.stereotype.Repository;

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

  public Optional<UserPO> findByAccount(String account) {
    return jdbc
        .query("SELECT * FROM sys_user WHERE username=? OR phone=?", ROW, account, account)
        .stream()
        .findFirst();
  }

  public List<UserPO> findAll() {
    return jdbc.query("SELECT * FROM sys_user ORDER BY id", ROW);
  }

  public List<String> roleIds(String id, boolean enabledOnly) {
    return jdbc.queryForList(
        "SELECT r.id FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=?"
            + (enabledOnly ? " AND r.status=1" : "")
            + " ORDER BY r.id",
        String.class,
        id);
  }

  public boolean hasRoleCode(String id, String code) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE"
                + " ur.user_id=? AND r.code=?",
            Integer.class,
            id,
            code)
        > 0;
  }

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

  public void delete(String id) {
    jdbc.update("UPDATE sys_dept SET manager_user_id=NULL WHERE manager_user_id=?", id);
    jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", id);
    jdbc.update("DELETE FROM sys_user WHERE id=?", id);
  }

  /**
   * CAS is the first write in the transaction; a competing writer waits, then sees zero updated
   * rows.
   */
  public boolean compareAndIncrement(String id, int version) {
    return jdbc.update(
            "UPDATE sys_user SET role_version=role_version+1 WHERE id=? AND role_version=?",
            id,
            version)
        == 1;
  }

  public int currentRoleVersion(String id) {
    return jdbc.queryForObject(
        "SELECT role_version FROM sys_user WHERE id=? FOR UPDATE", Integer.class, id);
  }

  public void replaceRoles(String id, List<String> roles) {
    jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", id);
    for (String role : roles)
      jdbc.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", id, role);
  }
}
