package com.municipal.rbac.procedural.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.municipal.rbac.procedural.error.ApiException;
import com.municipal.rbac.procedural.mapper.*;
import com.municipal.rbac.procedural.po.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

/** 隔离测试 RBAC 决策规则，不启动 Spring/数据库；DAO 用例由集成测试验证。 */
class AuthorizationServiceTest {
  UserMapper users;
  RoleMapper roles;
  DepartmentMapper departments;
  AuthorizationService authorization;
  MockHttpSession session;

  static UserPO user(String id, String department, boolean enabled, boolean mustChange, String hash) {
    return new UserPO(id, "U" + id, "Synthetic", department, null, null, null, enabled, hash, mustChange, 1);
  }

  static RolePO role(String id, String code) {
    return new RolePO(id, code, code, null, true, false, 1);
  }

  @BeforeEach
  void setUp() {
    users = mock(UserMapper.class);
    roles = mock(RoleMapper.class);
    departments = mock(DepartmentMapper.class);
    authorization = new AuthorizationService(users, roles, departments);
    session = new MockHttpSession();
    session.setAttribute(AuthorizationService.SESSION_USER, "4");
    session.setAttribute(AuthorizationService.SESSION_CREDENTIAL, "hash");
    when(users.findById("4")).thenReturn(Optional.of(user("4", "10", true, false, "hash")));
  }

  @Test
  void liveUserIsAcceptedWithoutSerializingCredential() {
    assertEquals("4", authorization.current(session).id());
    assertFalse(session.isInvalid());
    verify(users).findById("4");
  }

  @Test
  void deletedDisabledOrPasswordChangedIdentityInvalidatesOldSession() {
    for (UserPO current : Arrays.asList(null, user("4", "10", false, false, "hash"), user("4", "10", true, false, "new-hash"))) {
      var oldSession = new MockHttpSession();
      oldSession.setAttribute(AuthorizationService.SESSION_USER, "4");
      oldSession.setAttribute(AuthorizationService.SESSION_CREDENTIAL, "hash");
      when(users.findById("4")).thenReturn(Optional.ofNullable(current));
      ApiException error = assertThrows(ApiException.class, () -> authorization.current(oldSession));
      assertEquals("UNAUTHENTICATED", error.code());
      assertEquals(401, error.status().value());
      assertTrue(oldSession.isInvalid());
    }
  }

  @Test
  void firstLoginMayReadIdentityButCannotEnterBusinessFlow() {
    when(users.findById("4")).thenReturn(Optional.of(user("4", "10", true, true, "hash")));
    assertTrue(authorization.current(session).mustChangePassword());
    assertEquals("PASSWORD_CHANGE_REQUIRED", assertThrows(ApiException.class, () -> authorization.ready(session)).code());
    verifyNoInteractions(roles);
  }

  @Test
  void sameCodeUsesWidestScopeWhileDifferentActionsRemainIndependent() {
    when(users.roleIds("4", true)).thenReturn(List.of("5", "7"));
    when(roles.findGrants("5")).thenReturn(List.of(new RolePermissionPO("5", "hr:f046:read", "SELF"), new RolePermissionPO("5", "hr:f046:edit", "ALL")));
    when(roles.findGrants("7")).thenReturn(List.of(new RolePermissionPO("7", "hr:f046:read", "DEPT")));
    var permissions = authorization.effectivePermissions("4");
    assertEquals("DEPT", permissions.scopes().get("hr:f046:read"));
    assertEquals("ALL", permissions.scopes().get("hr:f046:edit"));
    assertEquals(2, permissions.permissionCodes().size());
    verify(users).roleIds("4", true);
  }

  @Test
  void derivedManagementActionsKeepOriginalScope() {
    when(users.roleIds("4", true)).thenReturn(List.of("6"));
    when(roles.findGrants("6")).thenReturn(List.of(new RolePermissionPO("6", "sys:f339:edit", "DEPT")));
    var scopes = authorization.effectivePermissions("4").scopes();
    assertEquals("DEPT", scopes.get("rbac:user:update"));
    assertEquals("DEPT", scopes.get("rbac:user:assign-role"));
    assertFalse(scopes.containsKey("rbac:user:delete"));
  }

  @Test
  void noEnabledRolesMeansNoPermissionAndDefaultDeny() {
    when(users.roleIds("4", true)).thenReturn(List.of());
    assertTrue(authorization.effectivePermissions("4").permissionCodes().isEmpty());
    assertEquals(403, assertThrows(ApiException.class, () -> authorization.require(session, "hr:f046:edit")).status().value());
    verifyNoInteractions(roles);
  }

  @Test
  void repeatedRequestsReReadGrantsInsteadOfKeepingRevokedRightsInSession() {
    when(users.roleIds("4", true)).thenReturn(List.of("5"), List.of());
    when(roles.findGrants("5")).thenReturn(List.of(new RolePermissionPO("5", "hr:f046:read", "SELF")));
    assertEquals("SELF", authorization.require(session, "hr:f046:read"));
    assertThrows(ApiException.class, () -> authorization.require(session, "hr:f046:read"));
    verify(users, times(2)).roleIds("4", true);
  }

  @Test
  void missingAndOutOfScopeTargetsHaveSamePublicError() {
    when(users.roleIds("4", true)).thenReturn(List.of("5"));
    when(roles.findGrants("5")).thenReturn(List.of(new RolePermissionPO("5", "hr:f046:edit", "SELF")));
    when(users.findById("5")).thenReturn(Optional.of(user("5", "10", true, false, "hash")));
    when(users.findById("999")).thenReturn(Optional.empty());
    var outside = assertThrows(ApiException.class, () -> authorization.target(session, "hr:f046:edit", "5"));
    var missing = assertThrows(ApiException.class, () -> authorization.target(session, "hr:f046:edit", "999"));
    assertEquals("OUT_OF_SCOPE", outside.code());
    assertEquals(outside.code(), missing.code());
    assertEquals(outside.getMessage(), missing.getMessage());
  }

  @Test
  void employeeScopeRejectsPeersUnknownAndNullScopes() {
    var actor = user("4", "10", true, false, "hash");
    var peer = user("5", "10", true, false, "hash");
    var child = user("6", "11", true, false, "hash");
    var outside = user("7", "20", true, false, "hash");
    when(departments.subtree("10")).thenReturn(Set.of("10", "11"));
    assertTrue(authorization.visible(actor, "SELF").test(actor));
    assertFalse(authorization.visible(actor, "SELF").test(peer));
    assertTrue(authorization.visible(actor, "DEPT").test(child));
    assertFalse(authorization.visible(actor, "DEPT").test(outside));
    assertTrue(authorization.visible(actor, "ALL").test(outside));
    assertFalse(authorization.visible(actor, "UNKNOWN").test(actor));
    assertFalse(authorization.visible(actor, null).test(actor));
  }

  @Test
  void ownDepartmentNodeDoesNotGiveSelfScopeAccessToCoworker() {
    var actor = user("4", "10", true, false, "hash");
    assertTrue(authorization.coversDepartment(actor, "SELF", "10"));
    assertFalse(authorization.covers(actor, "SELF", user("5", "10", true, false, "hash")));
    assertFalse(authorization.covers(actor, "UNKNOWN", actor));
  }

  @Test
  void customRoleWithManagementGrantIsStillAManagementRole() {
    var custom = role("9", "BUSINESS_NAMED_ROLE");
    when(roles.findGrants("9")).thenReturn(List.of(new RolePermissionPO("9", "sys:f339:read", "SELF")));
    assertTrue(authorization.adminRole(custom));
    when(roles.findGrants("9")).thenReturn(List.of(new RolePermissionPO("9", "hr:f046:read", "ALL")));
    assertFalse(authorization.adminRole(custom));
  }

  @Test
  void superAdminComesFromEnabledRoleLookup() {
    var actor = user("4", "10", true, false, "hash");
    when(users.roleIds("4", true)).thenReturn(List.of("1"));
    when(roles.findById("1")).thenReturn(Optional.of(role("1", "ADMIN")));
    assertTrue(authorization.superAdmin(actor));
    when(users.roleIds("4", true)).thenReturn(List.of());
    assertFalse(authorization.superAdmin(actor));
  }
}
