package com.municipal.rbac.procedural.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.municipal.rbac.procedural.dto.Requests;
import com.municipal.rbac.procedural.error.ApiException;
import com.municipal.rbac.procedural.mapper.*;
import com.municipal.rbac.procedural.po.RolePO;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

/** 角色保护和整组授权验证；事务原子性、数据库锁等待由 Stage1ApiTest 验证。 */
class RoleServiceTest {
  RoleMapper roles;
  UserMapper users;
  CatalogMapper catalog;
  AuthorizationService authorization;
  RoleService service;
  MockHttpSession session;
  RolePO role;

  @BeforeEach
  void setUp() {
    roles = mock(RoleMapper.class);
    users = mock(UserMapper.class);
    catalog = mock(CatalogMapper.class);
    authorization = mock(AuthorizationService.class);
    service = new RoleService(roles, users, catalog, authorization, mock(MeService.class));
    session = new MockHttpSession();
    role = AuthorizationServiceTest.role("7", "BUSINESS");
    when(roles.lockById("7")).thenReturn(Optional.of(role));
    when(roles.findById("7")).thenReturn(Optional.of(role));
    when(catalog.permissionCodes()).thenReturn(List.of("hr:f046:read", "hr:f046:edit"));
  }

  @Test
  void builtinRoleCannotBeDeletedEvenWithConfirmation() {
    when(roles.lockById("7")).thenReturn(Optional.of(new RolePO("7", "STAFF", "Staff", null, true, true, 1)));
    assertEquals("PROTECTED_ROLE", assertThrows(ApiException.class, () -> service.delete(session, "7", true)).code());
    verify(roles, never()).delete(anyString(), anyList());
  }

  @Test
  void roleWithUsersRequiresConfirmationAfterLockingAndReportsCount() {
    when(roles.lockedUserIds("7")).thenReturn(List.of("4", "5"));
    var error = assertThrows(ApiException.class, () -> service.delete(session, "7", false));
    assertEquals("ROLE_HAS_USERS", error.code());
    assertEquals(2, error.details().get("userCount"));
    verify(roles, never()).delete(anyString(), anyList());
    var ordered = inOrder(roles);
    ordered.verify(roles).lockById("7");
    ordered.verify(roles).lockedUserIds("7");
  }

  @Test
  void confirmedDeletionUsesLockedAssignmentSet() {
    when(roles.lockedUserIds("7")).thenReturn(List.of("4", "5"));
    service.delete(session, "7", true);
    verify(roles).delete("7", List.of("4", "5"));
  }

  @Test
  void adminCannotBeDisabledOrHaveItsGrantsReplaced() {
    var admin = AuthorizationServiceTest.role("7", "ADMIN");
    when(roles.findById("7")).thenReturn(Optional.of(admin));
    when(roles.lockById("7")).thenReturn(Optional.of(admin));
    assertEquals("PROTECTED_ROLE", assertThrows(ApiException.class, () -> service.status(session, "7", new Requests.Enabled(false))).code());
    assertEquals("PROTECTED_ROLE", assertThrows(ApiException.class, () -> service.replacePermissions(session, "7", new Requests.Grants(List.of(), 1))).code());
    verify(roles, never()).status(anyString(), anyBoolean());
    verify(roles, never()).compareAndIncrement(anyString(), anyInt());
  }

  @Test
  void derivedManagementPermissionCannotBeDirectlyGranted() {
    var request = new Requests.Grants(List.of(new Requests.Grant("rbac:user:delete", "ALL")), 1);
    assertEquals("UNKNOWN_PERMISSION", assertThrows(ApiException.class, () -> service.replacePermissions(session, "7", request)).code());
    verify(roles, never()).compareAndIncrement(anyString(), anyInt());
  }

  @Test
  void duplicatePermissionCodesAreRejectedBeforeVersionWrite() {
    var request = new Requests.Grants(List.of(new Requests.Grant("hr:f046:read", "SELF"), new Requests.Grant("hr:f046:read", "ALL")), 1);
    assertEquals("VALIDATION", assertThrows(ApiException.class, () -> service.replacePermissions(session, "7", request)).code());
    verify(roles, never()).compareAndIncrement(anyString(), anyInt());
  }

  @Test
  void writeActionWithoutSameFunctionReadIsRejected() {
    var request = new Requests.Grants(List.of(new Requests.Grant("hr:f046:edit", "ALL")), 1);
    assertEquals("READ_REQUIRED", assertThrows(ApiException.class, () -> service.replacePermissions(session, "7", request)).code());
    verify(roles, never()).replaceGrants(anyString(), anyList());
  }

  @Test
  void staleGrantVersionDoesNotReplaceStoredCollection() {
    when(roles.compareAndIncrement("7", 1)).thenReturn(false);
    when(roles.currentVersion("7")).thenReturn(3);
    var request = new Requests.Grants(List.of(new Requests.Grant("hr:f046:read", "SELF")), 1);
    var error = assertThrows(ApiException.class, () -> service.replacePermissions(session, "7", request));
    assertEquals("VERSION_CONFLICT", error.code());
    assertEquals(3, error.details().get("currentVersion"));
    verify(roles, never()).replaceGrants(anyString(), anyList());
  }

  @Test
  void validGrantSetKeepsDifferentReadAndEditScopesAndChecksVersionFirst() {
    var grants = List.of(new Requests.Grant("hr:f046:read", "SELF"), new Requests.Grant("hr:f046:edit", "ALL"));
    when(roles.compareAndIncrement("7", 1)).thenReturn(true);
    service.replacePermissions(session, "7", new Requests.Grants(grants, 1));
    var ordered = inOrder(roles);
    ordered.verify(roles).lockById("7");
    ordered.verify(roles).compareAndIncrement("7", 1);
    ordered.verify(roles).replaceGrants("7", grants);
  }
}
