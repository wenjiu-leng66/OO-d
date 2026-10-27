package com.municipal.rbac.procedural;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.municipal.rbac.procedural.mapper.RoleMapper;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.*;

/**
 * 一期真实 Service/JDBC 的 MockMvc + H2 集成回归，使用合成数据和测试专用密码。
 * 覆盖允许/拒绝、范围、版本冲突及回滚；不替代真实 Cookie 网络、MySQL 持久化或全量数据性能验收。
 * 普通用例用外层事务回滚，跨线程/独立事务用例显式关闭外层事务并在 finally 清理。
 */
@SpringBootTest(
    properties = {
      "spring.profiles.active=h2",
      "spring.datasource.url=jdbc:h2:mem:stage1-tests;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "rbac.demo-enabled=true",
      "rbac.initial-password=Stage1-Test-2026!"
    })
@AutoConfigureMockMvc
@Transactional
class Stage1ApiTest {
  static final String P = "Stage1-Test-2026!", API = "/api/v1";
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder passwords;
  @SpyBean RoleMapper roles;

  MockHttpSession login(String account) throws Exception {
    return login(account, P);
  }

  MockHttpSession login(String account, String password) throws Exception {
    var r =
        mvc.perform(
                post(API + "/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(Map.of("account", account, "password", password))))
            .andExpect(status().isOk())
            .andReturn();
    return (MockHttpSession) r.getRequest().getSession(false);
  }

  ResultActions send(String method, String path, MockHttpSession session, Object body)
      throws Exception {
    var req =
        switch (method) {
          case "POST" -> post(API + path);
          case "PUT" -> put(API + path);
          case "PATCH" -> patch(API + path);
          case "DELETE" -> delete(API + path);
          default -> get(API + path);
        };
    if (session != null) req.session(session);
    if (body != null)
      req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    return mvc.perform(req);
  }

  JsonNode node(ResultActions action) throws Exception {
    return json.readTree(action.andReturn().getResponse().getContentAsString());
  }

  Map<String, Object> grants(int version, String scope) {
    return Map.of(
        "version",
        version,
        "grants",
        List.of(
            Map.of("code", "hr:f046:read", "scope", "SELF"),
            Map.of("code", "hr:f046:edit", "scope", scope)));
  }

  @Test
  void allProtectedFamiliesRejectAnonymous() throws Exception {
    for (String path :
        List.of(
            "/me",
            "/me/permissions",
            "/portal/menu",
            "/portal/functions/f046",
            "/ledger/employees",
            "/users",
            "/roles",
            "/departments",
            "/catalog/functions",
            "/permissions",
            "/resources"))
      send("GET", path, null, null)
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
  }

  @Test
  void credentialFailuresAreIndistinguishable() throws Exception {
    String expected = null;
    for (var pair :
        List.of(
            new String[] {"missing", P},
            new String[] {"SG000013", "wrong"},
            new String[] {"DEMO000102", P})) {
      var r =
          send("POST", "/auth/login", null, Map.of("account", pair[0], "password", pair[1]))
              .andExpect(status().isUnauthorized())
              .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
      String msg = node(r).path("error").path("message").asText();
      if (expected == null) expected = msg;
      else assertEquals(expected, msg);
    }
  }

  @Test
  void phoneLoginSessionRotationAndNoSecretFields() throws Exception {
    String phone = jdbc.queryForObject("SELECT phone FROM sys_user WHERE id=4", String.class);
    var before = new MockHttpSession();
    String id = before.getId();
    send("POST", "/auth/login", before, Map.of("account", phone, "password", P))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.username").value("SG000013"))
        .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    assertNotEquals(id, before.getId());
  }

  @Test
  void logoutInvalidatesCookieSession() throws Exception {
    var s = login("SG000013");
    send("POST", "/auth/logout", s, null).andExpect(status().isOk());
    send("GET", "/me", null, null).andExpect(status().isUnauthorized());
    assertTrue(s.isInvalid());
  }

  @Test
  void firstLoginMustChangeAndChecksOldPassword() throws Exception {
    var s = login("DEMO000101");
    send("GET", "/me", s, null).andExpect(jsonPath("$.mustChangePassword").value(true));
    send("GET", "/portal/menu", s, null)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_REQUIRED"));
    send(
            "PATCH",
            "/me/password",
            s,
            Map.of("oldPassword", "wrong", "newPassword", "Changed-2026!A"))
        .andExpect(status().isBadRequest());
    send("PATCH", "/me/password", s, Map.of("oldPassword", P, "newPassword", "Changed-2026!A"))
        .andExpect(status().isOk());
    send("GET", "/portal/menu", s, null).andExpect(status().isOk());
    send("POST", "/auth/login", null, Map.of("account", "DEMO000101", "password", P))
        .andExpect(status().isUnauthorized());
    login("DEMO000101", "Changed-2026!A");
  }

  @Test
  void disableInvalidatesExistingSessionOnNextRequest() throws Exception {
    var s = login("SG000013");
    send("PATCH", "/users/4/status", login("admin"), Map.of("status", "DISABLED"))
        .andExpect(status().isOk());
    send("GET", "/me", s, null).andExpect(status().isUnauthorized());
    assertTrue(s.isInvalid());
  }

  @Test
  void resetPasswordInvalidatesExistingSessionAndRequiresChange() throws Exception {
    var s = login("SG000013");
    send("POST", "/users/4/password-reset", login("admin"), null).andExpect(status().isOk());
    send("GET", "/me", s, null).andExpect(status().isUnauthorized());
    send("GET", "/portal/menu", login("SG000013"), null)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_REQUIRED"));
  }

  @Test
  void ledgerSelfScopeAndUnknownFieldProtection() throws Exception {
    var s = login("SG000013");
    send("GET", "/ledger/employees", s, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].id").value("4"));
    send("PATCH", "/ledger/employees/4", s, Map.of("email", "self@example.com"))
        .andExpect(status().isOk());
    send("PATCH", "/ledger/employees/3", s, Map.of("email", "bad@example.com"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("OUT_OF_SCOPE"))
        .andExpect(jsonPath("$.error.details").isEmpty());
    send("PATCH", "/ledger/employees/999999", s, Map.of("email", "bad@example.com"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("OUT_OF_SCOPE"));
    send("PATCH", "/ledger/employees/4", s, Map.of("name", "intrusion"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("FIELD_NOT_EDITABLE"));
  }

  @Test
  void managerReadAllEditDepartmentAndChildren() throws Exception {
    var manager = login("SG000011");
    send("GET", "/ledger/employees", manager, null).andExpect(jsonPath("$.total").value(39));
    send("PATCH", "/ledger/employees/4", manager, Map.of("email", "office@example.com"))
        .andExpect(status().isOk());
    send("PATCH", "/ledger/employees/5", manager, Map.of("email", "bad@example.com"))
        .andExpect(status().isForbidden());
    send(
            "PUT",
            "/users/6/roles",
            login("admin"),
            Map.of("roleIds", List.of("3", "5", "6"), "version", 1))
        .andExpect(status().isOk());
    var branch = login("DEMO000100");
    send("PATCH", "/ledger/employees/7", branch, Map.of("email", "child@example.com"))
        .andExpect(status().isOk());
    send("PATCH", "/ledger/employees/4", branch, Map.of("email", "bad@example.com"))
        .andExpect(status().isForbidden());
  }

  @Test
  void departmentUserFiltersCannotEscapeScope() throws Exception {
    var s = login("DEMO000100");
    var result = node(send("GET", "/users?pageSize=100", s, null).andExpect(status().isOk()));
    Set<String> allowed = Set.of("10", "11", "12", "13", "14", "15");
    for (var u : result.path("items"))
      assertTrue(allowed.contains(u.path("departmentId").asText()));
    send("GET", "/users?departmentId=3", s, null).andExpect(jsonPath("$.total").value(0));
    send("GET", "/users/4", s, null).andExpect(status().isForbidden());
    send(
            "PUT",
            "/users/7",
            s,
            Map.of("name", "Synthetic", "departmentId", "3", "phone", "", "email", ""))
        .andExpect(status().isForbidden());
  }

  @Test
  void createUniqueUserEditResetAndDeleteManagerReference() throws Exception {
    var admin = login("admin");
    var r =
        node(
            send(
                    "POST",
                    "/users",
                    admin,
                    Map.of(
                        "username",
                        "TEST_NEW",
                        "name",
                        "Synthetic",
                        "departmentId",
                        "13",
                        "phone",
                        "15500000001"))
                .andExpect(status().isCreated()));
    String id = r.path("id").asText();
    assertTrue(r.path("mustChangePassword").asBoolean());
    send(
            "POST",
            "/users",
            admin,
            Map.of("username", "TEST_NEW", "name", "Synthetic", "departmentId", "13"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("DUPLICATE_USERNAME"));
    send(
            "POST",
            "/users",
            admin,
            Map.of(
                "username",
                "OTHER",
                "name",
                "Synthetic",
                "departmentId",
                "13",
                "phone",
                "15500000001"))
        .andExpect(status().isConflict());
    send(
            "PUT",
            "/users/" + id,
            admin,
            Map.of("name", "Edited", "departmentId", "14", "email", "edited@example.com"))
        .andExpect(status().isOk());
    jdbc.update("UPDATE sys_dept SET manager_user_id=? WHERE id=13", id);
    send("PUT", "/users/" + id + "/roles", admin, Map.of("roleIds", List.of("5"), "version", 1))
        .andExpect(status().isOk());
    send("DELETE", "/users/" + id, admin, null).andExpect(status().isNoContent());
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys_user_role WHERE user_id=?", Integer.class, id));
    assertNull(
        jdbc.queryForObject("SELECT manager_user_id FROM sys_dept WHERE id=13", Integer.class));
  }

  @Test
  void selfAndUniqueAdminProtection() throws Exception {
    var admin = login("admin");
    send("PATCH", "/users/1/status", admin, Map.of("status", "DISABLED"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("PROTECTED_ACCOUNT"));
    send("DELETE", "/users/1", admin, null).andExpect(status().isConflict());
    send("PUT", "/users/1/roles", admin, Map.of("roleIds", List.of("5"), "version", 1))
        .andExpect(status().isConflict());
    send(
            "PUT",
            "/users/6/roles",
            login("DEMO000100"),
            Map.of("roleIds", List.of("5"), "version", 1))
        .andExpect(status().isConflict());
    send("PUT", "/users/4/roles", admin, Map.of("roleIds", List.of("1"), "version", 1))
        .andExpect(status().isForbidden());
  }

  @Test
  void departmentAdminCannotGrantOrRemoveManagementRoles() throws Exception {
    var dept = login("DEMO000100");
    send("PUT", "/users/7/roles", dept, Map.of("roleIds", List.of("5", "6"), "version", 1))
        .andExpect(status().isForbidden());
    send("PUT", "/users/4/roles", dept, Map.of("roleIds", List.of("5"), "version", 1))
        .andExpect(status().isForbidden());
    send("PUT", "/roles/5/permissions", dept, grants(1, "SELF")).andExpect(status().isForbidden());
    var admin = login("admin");
    send("PUT", "/users/7/roles", admin, Map.of("roleIds", List.of("5", "6"), "version", 1))
        .andExpect(status().isOk());
    send("PUT", "/users/7/roles", dept, Map.of("roleIds", List.of("5"), "version", 2))
        .andExpect(status().isForbidden());
  }

  @Test
  void businessRoleUnionAndImmediateRevocation() throws Exception {
    MockHttpSession staff = login("SG000013"), admin = login("admin");
    send("PUT", "/users/4/roles", admin, Map.of("roleIds", List.of("5", "7"), "version", 1))
        .andExpect(status().isOk());
    var p = node(send("GET", "/me/permissions", staff, null));
    Map<String, String> scopes = new HashMap<>();
    for (var x : p.path("scopes"))
      scopes.put(x.path("permissionCode").asText(), x.path("scope").asText());
    assertEquals("SELF", scopes.get("hr:f046:read"));
    assertEquals("ALL", scopes.get("hr:f046:edit"));
    send("PATCH", "/ledger/employees/5", staff, Map.of("email", "union@example.com"))
        .andExpect(status().isOk());
    send("PUT", "/users/4/roles", admin, Map.of("roleIds", List.of(), "version", 2))
        .andExpect(status().isOk());
    send("GET", "/ledger/employees", staff, null).andExpect(status().isForbidden());
  }

  @Test
  void userRoleVersionConflictAndDuplicateValidation() throws Exception {
    var admin = login("admin");
    send("PUT", "/users/4/roles", admin, Map.of("roleIds", List.of("5", "7"), "version", 1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2));
    send("PUT", "/users/4/roles", admin, Map.of("roleIds", List.of("5"), "version", 1))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.details.currentVersion").value(2));
    send("GET", "/users/4/roles", admin, null).andExpect(jsonPath("$.roleIds.length()").value(2));
    send("PUT", "/users/4/roles", admin, Map.of("roleIds", List.of("5", "5"), "version", 2))
        .andExpect(status().isBadRequest());
  }

  @Test
  void roleCrudStatusDeleteAndBuiltinGuards() throws Exception {
    var admin = login("admin");
    var role =
        node(
            send("POST", "/roles", admin, Map.of("code", "TEST_ROLE", "name", "Synthetic"))
                .andExpect(status().isCreated()));
    String id = role.path("id").asText();
    send("POST", "/roles", admin, Map.of("code", "TEST_ROLE", "name", "Duplicate"))
        .andExpect(status().isConflict());
    send("PATCH", "/roles/" + id, admin, Map.of("name", "Renamed", "description", "Synthetic"))
        .andExpect(status().isOk());
    send("PUT", "/roles/" + id + "/permissions", admin, grants(1, "ALL"))
        .andExpect(status().isOk());
    send("PUT", "/users/4/roles", admin, Map.of("roleIds", List.of(id), "version", 1))
        .andExpect(status().isOk());
    var staff = login("SG000013");
    send("GET", "/ledger/employees", staff, null).andExpect(status().isOk());
    send("PATCH", "/roles/" + id + "/status", admin, Map.of("enabled", false))
        .andExpect(status().isOk());
    send("GET", "/ledger/employees", staff, null).andExpect(status().isForbidden());
    send("DELETE", "/roles/" + id, admin, null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("ROLE_HAS_USERS"));
    send("DELETE", "/roles/" + id + "?confirm=true", admin, null).andExpect(status().isNoContent());
    send("GET", "/users/4/roles", admin, null).andExpect(jsonPath("$.version").value(3));
    send("DELETE", "/roles/5?confirm=true", admin, null).andExpect(status().isConflict());
    send("PATCH", "/roles/1/status", admin, Map.of("enabled", false))
        .andExpect(status().isConflict());
    send("PUT", "/roles/1/permissions", admin, Map.of("grants", List.of(), "version", 1))
        .andExpect(status().isConflict());
  }

  @Test
  void grantValidationVersionAndImmediateChange() throws Exception {
    MockHttpSession admin = login("admin"), staff = login("SG000013");
    send(
            "PUT",
            "/roles/5/permissions",
            admin,
            Map.of(
                "version",
                1,
                "grants",
                List.of(Map.of("code", "rbac:user:delete", "scope", "ALL"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("UNKNOWN_PERMISSION"));
    send(
            "PUT",
            "/roles/5/permissions",
            admin,
            Map.of("version", 1, "grants", List.of(Map.of("code", "hr:f046:edit", "scope", "ALL"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("READ_REQUIRED"));
    send("PUT", "/roles/5/permissions", admin, grants(1, "ALL"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2));
    send("PATCH", "/ledger/employees/5", staff, Map.of("email", "live@example.com"))
        .andExpect(status().isOk());
    send("PUT", "/roles/5/permissions", admin, grants(1, "SELF")).andExpect(status().isConflict());
  }

  @Test
  void managementRoleClassificationPreventsCustomPrivilegeEscalation() throws Exception {
    var admin = login("admin");
    String id =
        node(send("POST", "/roles", admin, Map.of("code", "MANAGER_COPY", "name", "Synthetic"))
                .andExpect(status().isCreated()))
            .path("id")
            .asText();
    send(
            "PUT",
            "/roles/" + id + "/permissions",
            admin,
            Map.of(
                "version", 1, "grants", List.of(Map.of("code", "sys:f339:read", "scope", "ALL"))))
        .andExpect(status().isOk());
    send("GET", "/roles/" + id, admin, null).andExpect(jsonPath("$.adminRole").value(true));
    send("PUT", "/users/7/roles", login("DEMO000100"), Map.of("roleIds", List.of(id), "version", 1))
        .andExpect(status().isForbidden());
  }

  @Test
  void portalCatalogAndRoleSourcesAreComplete() throws Exception {
    MockHttpSession admin = login("admin"), staff = login("SG000013");
    send("GET", "/catalog/functions", admin, null)
        .andExpect(jsonPath("$.stats.systems").value(18))
        .andExpect(jsonPath("$.stats.modules").value(115))
        .andExpect(jsonPath("$.stats.functions").value(359))
        .andExpect(jsonPath("$.stats.permissions").value(2154));
    send("GET", "/portal/functions/f052", staff, null)
        .andExpect(jsonPath("$.actions.length()").value(6))
        .andExpect(jsonPath("$.placeholder").value(true));
    send("GET", "/portal/functions/f339", staff, null).andExpect(status().isForbidden());
    var menu = node(send("GET", "/portal/menu", staff, null));
    for (var sys : menu) assertNotEquals("sys", sys.path("systemCode").asText());
    var p = node(send("GET", "/me/permissions", staff, null));
    assertEquals(p.path("permissionCodes").size(), p.path("scopes").size());
    for (var entry : p.path("scopes")) assertTrue(entry.path("roles").size() > 0);
  }

  @Test
  void departmentTreeMembersAndRoleUsersAreScoped() throws Exception {
    var dept = login("DEMO000100");
    send("GET", "/departments", dept, null)
        .andExpect(jsonPath("$[0].id").value("10"))
        .andExpect(jsonPath("$[0].children.length()").value(5));
    send("GET", "/departments/10/members", dept, null).andExpect(status().isOk());
    send("GET", "/departments/3/members", dept, null).andExpect(status().isForbidden());
    var r = node(send("GET", "/roles/5/users?pageSize=100", dept, null));
    for (var u : r.path("items"))
      assertTrue(
          Set.of("10", "11", "12", "13", "14", "15").contains(u.path("departmentId").asText()));
  }

  @Test
  void importedPbkdf2HashesAreAccepted() throws Exception {
    String hash =
        org.springframework.security.crypto.password.Pbkdf2PasswordEncoder
            .defaultsForSpringSecurity_v5_8()
            .encode(P);
    jdbc.update(
        "UPDATE sys_user SET password_hash=? WHERE id=4", "{pbkdf2@SpringSecurity_v5_8}" + hash);
    login("SG000013");
  }

  @Test
  void invalidPaginationAndContactDataReturn400() throws Exception {
    MockHttpSession admin = login("admin"), staff = login("SG000013");
    send("GET", "/users?page=0", admin, null).andExpect(status().isBadRequest());
    send("GET", "/catalog/functions?pageSize=101", admin, null).andExpect(status().isBadRequest());
    send("PATCH", "/ledger/employees/4", staff, Map.of("email", "bad"))
        .andExpect(status().isBadRequest());
    send("PATCH", "/ledger/employees/4", staff, Map.of("phone", 123))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  // 在真实替换完成后注入异常，独立于测试外层事务验证版本与授权均被服务事务回滚。
  void failedReplacementRollsBackGrantsAndVersion() throws Exception {
    var admin = login("admin");
    String id =
        node(send("POST", "/roles", admin, Map.of("code", "ROLLBACK_CASE", "name", "Synthetic"))
                .andExpect(status().isCreated()))
            .path("id")
            .asText();
    try {
      doAnswer(
              invocation -> {
                invocation.callRealMethod();
                throw new IllegalStateException("simulated storage failure after writes");
              })
          .when(roles)
          .replaceGrants(eq(id), anyList());
      send("PUT", "/roles/" + id + "/permissions", admin, grants(1, "ALL"))
          .andExpect(status().isInternalServerError());
      assertEquals(
          1,
          jdbc.queryForObject(
              "SELECT permission_version FROM sys_role WHERE id=?", Integer.class, id));
      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM sys_role_permission WHERE role_id=?", Integer.class, id));
    } finally {
      reset(roles);
      jdbc.update("DELETE FROM sys_role WHERE id=?", id);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  // 两个会话同时提交同一旧版本；必须恰好一方成功、一方 409，最终集合不能拼接为两方并集。
  void simultaneousCasHasExactlyOneWinner() throws Exception {
    MockHttpSession admin1 = login("admin"), admin2 = login("admin");
    String id =
        node(send(
                    "POST",
                    "/users",
                    admin1,
                    Map.of("username", "CAS_CASE", "name", "Synthetic", "departmentId", "13"))
                .andExpect(status().isCreated()))
            .path("id")
            .asText();
    var pool = Executors.newFixedThreadPool(2);
    try {
      var gate = new CountDownLatch(1);
      List<Future<Integer>> futures = new ArrayList<>();
      for (var entry : List.of(Map.entry(admin1, "5"), Map.entry(admin2, "7")))
        futures.add(
            pool.submit(
                () -> {
                  gate.await();
                  return send(
                          "PUT",
                          "/users/" + id + "/roles",
                          entry.getKey(),
                          Map.of("roleIds", List.of(entry.getValue()), "version", 1))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      gate.countDown();
      List<Integer> statuses = new ArrayList<>();
      for (var f : futures) statuses.add(f.get(15, TimeUnit.SECONDS));
      Collections.sort(statuses);
      assertEquals(List.of(200, 409), statuses);
      assertEquals(
          2,
          jdbc.queryForObject("SELECT role_version FROM sys_user WHERE id=?", Integer.class, id));
      assertEquals(
          1,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM sys_user_role WHERE user_id=?", Integer.class, id));
    } finally {
      pool.shutdownNow();
      jdbc.update("DELETE FROM sys_user WHERE id=?", id);
    }
  }

  @Test
  // 末尾字符差异位于 bcrypt 截断边界之外，用于证明新 PBKDF2 路径确实校验全部长密码。
  void longPasswordsDoNotAcceptDifferentSuffixBeyondBcryptLimit() throws Exception {
    String a = "A".repeat(127) + "B", b = "A".repeat(127) + "C";
    var session = login("SG000013");
    send("PATCH", "/me/password", session, Map.of("oldPassword", P, "newPassword", a))
        .andExpect(status().isOk());
    assertTrue(
        jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE id=4", String.class)
            .startsWith("{pbkdf2@SpringSecurity_v5_8}"));
    send("POST", "/auth/login", null, Map.of("account", "SG000013", "password", b))
        .andExpect(status().isUnauthorized());
    login("SG000013", a);
  }

  @Test
  void unicodePasswordsPreserveAllUtf8BytesAndLegacyBcryptStillMatches() throws Exception {
    String a = "\u5bc6".repeat(40) + "X", b = "\u5bc6".repeat(40) + "Y";
    var session = login("SG000013");
    send("PATCH", "/me/password", session, Map.of("oldPassword", P, "newPassword", a))
        .andExpect(status().isOk());
    send("POST", "/auth/login", null, Map.of("account", "SG000013", "password", b))
        .andExpect(status().isUnauthorized());
    login("SG000013", a);
    String legacy =
        "{bcrypt}"
            + new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(P);
    jdbc.update("UPDATE sys_user SET password_hash=? WHERE id=4", legacy);
    login("SG000013");
  }

  @Test
  void oversizedAndMalformedNumericIdsDoNotBecomeSqlErrors() throws Exception {
    var admin = login("admin");
    send("GET", "/users/999999999999999999999999999999", admin, null)
        .andExpect(status().isForbidden());
    send("GET", "/roles/999999999999999999999999999999", admin, null)
        .andExpect(status().isNotFound());
    send("GET", "/users?roleId=not-an-id", admin, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0));
    send("PUT", "/users/4/roles", admin, Map.of("roleIds", List.of("99999999999"), "version", 1))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  // 用门闩固定“分配持角色锁 → 删除等待”时序，避免只靠睡眠构造不稳定竞态。
  void concurrentAssignmentMakesUnconfirmedRoleDeleteRefuse() throws Exception {
    var admin = login("admin");
    String roleId =
        node(send("POST", "/roles", admin, Map.of("code", "DELETE_LOCK_CASE", "name", "Synthetic"))
                .andExpect(status().isCreated()))
            .path("id")
            .asText();
    String userId =
        node(send(
                    "POST",
                    "/users",
                    admin,
                    Map.of(
                        "username", "DELETE_LOCK_USER", "name", "Synthetic", "departmentId", "13"))
                .andExpect(status().isCreated()))
            .path("id")
            .asText();
    var pool = Executors.newFixedThreadPool(2);
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    try {
      doAnswer(
              inv -> {
                Object value = inv.callRealMethod();
                if (calls.incrementAndGet() == 1) {
                  locked.countDown();
                  if (!release.await(10, TimeUnit.SECONDS))
                    throw new IllegalStateException("lock test timeout");
                }
                return value;
              })
          .when(roles)
          .lockById(eq(roleId));
      var assigning =
          pool.submit(
              () ->
                  send(
                          "PUT",
                          "/users/" + userId + "/roles",
                          admin,
                          Map.of("roleIds", List.of(roleId), "version", 1))
                      .andReturn()
                      .getResponse()
                      .getStatus());
      assertTrue(locked.await(10, TimeUnit.SECONDS));
      var deleting =
          pool.submit(
              () ->
                  send("DELETE", "/roles/" + roleId, admin, null)
                      .andReturn()
                      .getResponse()
                      .getStatus());
      release.countDown();
      assertEquals(200, assigning.get(15, TimeUnit.SECONDS));
      assertEquals(409, deleting.get(15, TimeUnit.SECONDS));
      assertEquals(
          1,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM sys_user_role WHERE user_id=? AND role_id=?",
              Integer.class,
              userId,
              roleId));
    } finally {
      release.countDown();
      pool.shutdownNow();
      reset(roles);
      jdbc.update("DELETE FROM sys_user WHERE id=?", userId);
      jdbc.update("DELETE FROM sys_role WHERE id=?", roleId);
    }
  }

  @Test
  // SELF 可定位本部门节点，但成员仍只能有本人，且不能借同部门范围创建另一个账号。
  void selfManagementScopesNeverExposeDepartmentPeersOrCreateOtherAccounts() throws Exception {
    var admin = login("admin");
    String roleId =
        node(send("POST", "/roles", admin, Map.of("code", "SELF_SCOPE_TEST", "name", "Synthetic"))
                .andExpect(status().isCreated()))
            .path("id")
            .asText();
    send(
            "PUT",
            "/roles/" + roleId + "/permissions",
            admin,
            Map.of(
                "version",
                1,
                "grants",
                List.of(
                    Map.of("code", "sys:f342:read", "scope", "SELF"),
                    Map.of("code", "sys:f339:read", "scope", "SELF"),
                    Map.of("code", "sys:f339:create", "scope", "SELF"))))
        .andExpect(status().isOk());
    send("PUT", "/users/4/roles", admin, Map.of("roleIds", List.of("5", roleId), "version", 1))
        .andExpect(status().isOk());
    var staff = login("SG000013");
    send("GET", "/departments/3/members", staff, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].id").value("4"));
    send("GET", "/departments/4/members", staff, null).andExpect(status().isForbidden());
    send("GET", "/users", staff, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1));
    send(
            "POST",
            "/users",
            staff,
            Map.of("username", "SELF_CANNOT_CREATE", "name", "Synthetic", "departmentId", "3"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("OUT_OF_SCOPE"));
  }
}
