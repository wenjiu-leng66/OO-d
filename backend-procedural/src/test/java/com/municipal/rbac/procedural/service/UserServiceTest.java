package com.municipal.rbac.procedural.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.municipal.rbac.procedural.dto.Requests;
import com.municipal.rbac.procedural.error.ApiException;
import com.municipal.rbac.procedural.mapper.*;
import com.municipal.rbac.procedural.po.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 用户写入前置条件隔离测试；mock 的无写入断言不替代真实数据库事务/竞态测试。 */
class UserServiceTest {
  UserMapper users;
  RoleMapper roles;
  DepartmentMapper departments;
  AuthorizationService authorization;
  MeService me;
  PasswordEncoder passwords;
  UserService service;
  MockHttpSession session;
  UserPO actor;
  UserPO target;

  @BeforeEach
  void setUp() {
    users = mock(UserMapper.class);
    roles = mock(RoleMapper.class);
    departments = mock(DepartmentMapper.class);
    authorization = mock(AuthorizationService.class);
    me = mock(MeService.class);
    passwords = mock(PasswordEncoder.class);
    service = new UserService(users, roles, departments, authorization, me, passwords, "test-initial-password");
    session = new MockHttpSession();
    actor = AuthorizationServiceTest.user("1", "10", true, false, "hash");
    target = AuthorizationServiceTest.user("4", "10", true, false, "hash");
    when(authorization.current(session)).thenReturn(actor);
    when(authorization.target(eq(session), anyString(), eq("4"))).thenReturn(target);
  }

  Requests.CreateUser createRequest() {
    return new Requests.CreateUser("NEW1", "Synthetic", "10", null, null, null);
  }

  void allowCreateDepartment() {
    when(authorization.require(session, "rbac:user:create")).thenReturn("DEPT");
    when(departments.findById("10")).thenReturn(Optional.of(new DepartmentPO("10", "Synthetic", "S", "HQ_DEPT", null, null)));
    when(authorization.coversDepartment(actor, "DEPT", "10")).thenReturn(true);
  }

  @Test
  void selfScopeCannotCreateAnotherAccountEvenInsideOwnDepartment() {
    when(authorization.require(session, "rbac:user:create")).thenReturn("SELF");
    assertEquals("OUT_OF_SCOPE", assertThrows(ApiException.class, () -> service.create(session, createRequest())).code());
    verifyNoInteractions(departments, passwords);
    verify(users, never()).insert(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void updateMustCheckDestinationDepartmentAsWellAsOriginalEmployee() {
    when(authorization.require(session, "rbac:user:update")).thenReturn("DEPT");
    when(departments.findById("20")).thenReturn(Optional.of(new DepartmentPO("20", "Other", "O", "HQ_DEPT", null, null)));
    when(authorization.coversDepartment(actor, "DEPT", "20")).thenReturn(false);
    var request = new Requests.UpdateUser("Synthetic", "20", null, null, null);
    assertEquals("OUT_OF_SCOPE", assertThrows(ApiException.class, () -> service.update(session, "4", request)).code());
    verify(users, never()).update(any(), any(), any(), any(), any(), any());
  }

  @Test
  void duplicateUsernameIsReportedBeforeInsert() {
    allowCreateDepartment();
    when(users.usernameExists("NEW1")).thenReturn(true);
    assertEquals("DUPLICATE_USERNAME", assertThrows(ApiException.class, () -> service.create(session, createRequest())).code());
    verifyNoInteractions(passwords);
    verify(users, never()).insert(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void missingInitialPasswordRejectsCreationWithoutDatabaseWrite() {
    allowCreateDepartment();
    var unconfigured = new UserService(users, roles, departments, authorization, me, passwords, "");
    assertEquals("INITIAL_PASSWORD_NOT_CONFIGURED", assertThrows(ApiException.class, () -> unconfigured.create(session, createRequest())).code());
    verify(users, never()).insert(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void selfDisableIsProtectedBeforeStatusMutation() {
    when(authorization.target(session, "rbac:user:update", "1")).thenReturn(actor);
    assertEquals("PROTECTED_ACCOUNT", assertThrows(ApiException.class, () -> service.status(session, "1", new Requests.Status("DISABLED"))).code());
    verify(users, never()).status(anyString(), anyBoolean());
  }

  @Test
  void departmentAdminCannotResetManagementAccountPassword() {
    when(users.hasRoleCode("4", "DEPT_ADMIN")).thenReturn(true);
    assertEquals(403, assertThrows(ApiException.class, () -> service.reset(session, "4")).status().value());
    verify(users, never()).password(anyString(), anyString(), anyBoolean());
  }

  @Test
  void duplicateRoleIdsNeverEnterLockOrReplaceFlow() {
    assertEquals("VALIDATION", assertThrows(ApiException.class, () -> service.replaceRoles(session, "4", new Requests.Roles(List.of("5", "5"), 1))).code());
    verifyNoInteractions(roles);
    verify(users, never()).compareAndIncrement(anyString(), anyInt());
  }

  @Test
  void adminRoleCannotBeAssignedToAnotherAccountEvenBySuperAdmin() {
    var adminRole = AuthorizationServiceTest.role("1", "ADMIN");
    when(users.roleIds("4", false)).thenReturn(List.of());
    when(roles.lockById("1")).thenReturn(Optional.of(adminRole));
    when(roles.findById("1")).thenReturn(Optional.of(adminRole));
    when(authorization.superAdmin(actor)).thenReturn(true);
    assertEquals(403, assertThrows(ApiException.class, () -> service.replaceRoles(session, "4", new Requests.Roles(List.of("1"), 1))).status().value());
    verify(users, never()).compareAndIncrement(anyString(), anyInt());
  }

  @Test
  void revokingManagementRoleAlsoRequiresSuperAdmin() {
    var management = AuthorizationServiceTest.role("6", "CUSTOM_MANAGER");
    when(users.roleIds("4", false)).thenReturn(List.of("6"));
    when(roles.lockById("6")).thenReturn(Optional.of(management));
    when(roles.findById("6")).thenReturn(Optional.of(management));
    when(authorization.adminRole(management)).thenReturn(true);
    assertEquals(403, assertThrows(ApiException.class, () -> service.replaceRoles(session, "4", new Requests.Roles(List.of(), 1))).status().value());
    verify(users, never()).compareAndIncrement(anyString(), anyInt());
  }

  @Test
  void staleRoleVersionReturnsCurrentVersionWithoutDeletingExistingAssignments() {
    when(users.roleIds("4", false)).thenReturn(List.of());
    when(users.compareAndIncrement("4", 1)).thenReturn(false);
    when(users.currentRoleVersion("4")).thenReturn(2);
    var error = assertThrows(ApiException.class, () -> service.replaceRoles(session, "4", new Requests.Roles(List.of(), 1)));
    assertEquals("VERSION_CONFLICT", error.code());
    assertEquals(2, error.details().get("currentVersion"));
    verify(users, never()).replaceRoles(anyString(), anyList());
  }

  @Test
  void replacementLocksNumericRoleOrderBeforeCasAndWritesOnlyAfterWinning() {
    var r2 = AuthorizationServiceTest.role("2", "BUSINESS2");
    var r10 = AuthorizationServiceTest.role("10", "BUSINESS10");
    when(users.roleIds("4", false)).thenReturn(List.of("10"), List.of("2", "10"));
    when(roles.lockById("2")).thenReturn(Optional.of(r2));
    when(roles.lockById("10")).thenReturn(Optional.of(r10));
    when(roles.findById("2")).thenReturn(Optional.of(r2));
    when(roles.findById("10")).thenReturn(Optional.of(r10));
    when(users.compareAndIncrement("4", 1)).thenReturn(true);
    when(users.findById("4")).thenReturn(Optional.of(target));
    service.replaceRoles(session, "4", new Requests.Roles(List.of("2", "10"), 1));
    var ordered = inOrder(roles, users);
    ordered.verify(roles).lockById("2");
    ordered.verify(roles).lockById("10");
    ordered.verify(users).compareAndIncrement("4", 1);
    ordered.verify(users).replaceRoles("4", List.of("2", "10"));
  }
}
