package com.municipal.rbac.procedural.data;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Opt-in SYNTHETIC demo fixture. Never reads or embeds the private course employee workbook. */
@Component
public class DataInitializer implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
  private final JdbcTemplate jdbc;
  private final PasswordEncoder passwords;
  private final ObjectMapper json;
  private final boolean enabled;
  private final String initialPassword;

  public DataInitializer(
      JdbcTemplate jdbc,
      PasswordEncoder passwords,
      ObjectMapper json,
      @Value("${rbac.demo-enabled:false}") boolean enabled,
      @Value("${rbac.initial-password:}") String initialPassword) {
    this.jdbc = jdbc;
    this.passwords = passwords;
    this.json = json;
    this.enabled = enabled;
    this.initialPassword = initialPassword;
  }

  private static int number(String value) {
    return Integer.parseInt(value.replaceAll("[^0-9]", ""));
  }

  private static Object nullable(JsonNode row, String key) {
    return row.hasNonNull(key) && !row.get(key).asText().isBlank() ? row.get(key).asText() : null;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) throws Exception {
    if (!enabled) return;
    if (initialPassword.length() < 8)
      throw new IllegalStateException(
          "RBAC_DEMO_ENABLED requires an injected RBAC_INITIAL_PASSWORD of at least 8 characters.");
    if (jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class) > 0
        || jdbc.queryForObject("SELECT COUNT(*) FROM app_system", Integer.class) > 0) {
      log.info("Demo initialization skipped: database already contains data.");
      return;
    }
    JsonNode data;
    try (var stream = new ClassPathResource("demo-data.json").getInputStream()) {
      data = json.readTree(stream);
    }
    for (var d : data.get("departments")) {
      String parent = d.path("parentId").asText(null);
      String type = d.get("type").asText();
      jdbc.update(
          "INSERT INTO sys_dept(id,name,short_name,parent_id,dept_type,level,sort_no)"
              + " VALUES(?,?,?,?,?,?,?)",
          number(d.get("id").asText()),
          d.get("name").asText(),
          d.get("name").asText().substring(Math.max(0, d.get("name").asText().length() - 32)),
          parent == null ? null : number(parent),
          type,
          "HQ".equals(type) ? 1 : List.of("HQ_DEPT", "BRANCH").contains(type) ? 2 : 3,
          number(d.get("id").asText()));
    }
    Map<String, Integer> systems = new LinkedHashMap<>(),
        modules = new LinkedHashMap<>(),
        permissionIds = new HashMap<>();
    int sid = 0, mid = 0, pid = 0, aid = 0;
    for (var s : data.get("systems")) {
      systems.put(s.get("code").asText(), ++sid);
      jdbc.update(
          "INSERT INTO app_system(id,code,name,sort_no) VALUES(?,?,?,?)",
          sid,
          s.get("code").asText(),
          s.get("name").asText(),
          sid);
    }
    for (var a : data.get("actions"))
      jdbc.update(
          "INSERT INTO sys_action(id,code,name) VALUES(?,?,?)",
          ++aid,
          a.get("code").asText(),
          a.get("label").asText());
    for (var f : data.get("functions")) {
      String sys = f.get("systemCode").asText(),
          module = f.get("module").asText(),
          key = sys + "/" + module,
          code = f.get("id").asText();
      if (!modules.containsKey(key)) {
        modules.put(key, ++mid);
        jdbc.update(
            "INSERT INTO app_module(id,system_id,name,sort_no) VALUES(?,?,?,?)",
            mid,
            systems.get(sys),
            module,
            mid);
      }
      int fid = number(code);
      jdbc.update(
          "INSERT INTO app_function(id,module_id,code,name,description,sort_no)"
              + " VALUES(?,?,?,?,?,?)",
          fid,
          modules.get(key),
          code,
          f.get("name").asText(),
          f.get("description").asText(),
          fid);
      int action = 0;
      for (var a : data.get("actions")) {
        String permission = sys + ":" + code + ":" + a.get("code").asText();
        permissionIds.put(permission, ++pid);
        jdbc.update(
            "INSERT INTO sys_permission(id,function_id,action_id,code) VALUES(?,?,?,?)",
            pid,
            fid,
            ++action,
            permission);
      }
    }
    for (var r : data.get("roleRows"))
      jdbc.update(
          "INSERT INTO sys_role(id,code,name,description,builtin,sort_no) VALUES(?,?,?,?,?,?)",
          number(r.get(0).asText()),
          r.get(1).asText(),
          r.get(2).asText(),
          r.get(5).asText(),
          r.get(4).asBoolean() ? 1 : 0,
          number(r.get(0).asText()));
    var grants = data.get("roleGrants").fields();
    while (grants.hasNext()) {
      var entry = grants.next();
      for (var g : entry.getValue())
        jdbc.update(
            "INSERT INTO sys_role_permission(role_id,permission_id,data_scope) VALUES(?,?,?)",
            number(entry.getKey()),
            permissionIds.get(g.get("code").asText()),
            g.get("scope").asText());
    }
    for (var u : data.get("users")) {
      int uid = number(u.get("id").asText());
      jdbc.update(
          "INSERT INTO"
              + " sys_user(id,username,password_hash,must_change_password,real_name,dept_id,position,phone,email,status)"
              + " VALUES(?,?,?,?,?,?,?,?,?,?)",
          uid,
          u.get("username").asText(),
          passwords.encode(initialPassword),
          u.path("mustChangePassword").asBoolean() ? 1 : 0,
          u.get("name").asText(),
          number(u.get("departmentId").asText()),
          nullable(u, "position"),
          nullable(u, "phone"),
          nullable(u, "email"),
          "DISABLED".equals(u.path("status").asText()) ? 0 : 1);
      for (var r : u.get("roleIds"))
        jdbc.update(
            "INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", uid, number(r.asText()));
    }
    for (var d : data.get("departments"))
      if (d.hasNonNull("managerId"))
        jdbc.update(
            "UPDATE sys_dept SET manager_user_id=? WHERE id=?",
            number(d.get("managerId").asText()),
            number(d.get("id").asText()));
    String product;
    try (var connection = jdbc.getDataSource().getConnection()) {
      product = connection.getMetaData().getDatabaseProductName();
    }
    if (product.equals("H2")) {
      jdbc.execute(
          "ALTER TABLE sys_user ALTER COLUMN id RESTART WITH " + (data.get("users").size() + 1));
      jdbc.execute(
          "ALTER TABLE sys_role ALTER COLUMN id RESTART WITH " + (data.get("roleRows").size() + 1));
    }
    log.info(
        "Synthetic demo initialized: {} users, {} departments, 18 systems, 115 modules, 359"
            + " functions, 2154 permissions. These are not private course data.",
        data.get("users").size(),
        data.get("departments").size());
  }
}
