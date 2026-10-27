package com.municipal.rbac.procedural.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.municipal.rbac.procedural.dto.Requests;
import com.municipal.rbac.procedural.error.ApiException;
import com.municipal.rbac.procedural.mapper.UserMapper;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 认证流程隔离测试；密码算法正确性另由真实编码器集成用例验证。 */
class AuthServiceTest {
  UserMapper users;
  PasswordEncoder passwords;
  AuthorizationService authorization;
  MeService me;
  AuthService auth;
  MockHttpSession session;

  @BeforeEach
  void setUp() {
    users = mock(UserMapper.class);
    passwords = mock(PasswordEncoder.class);
    authorization = mock(AuthorizationService.class);
    me = mock(MeService.class);
    when(passwords.encode(any())).thenReturn("dummy-hash");
    auth = new AuthService(users, passwords, authorization, me);
    session = new MockHttpSession();
  }

  @Test
  void successfulLoginTrimsAccountStoresCredentialVersionAndReturnsSafeView() {
    var user = AuthorizationServiceTest.user("4", "10", true, false, "stored-hash");
    when(users.findByAccount("U4")).thenReturn(Optional.of(user));
    when(passwords.matches("provided", "stored-hash")).thenReturn(true);
    when(me.me(session)).thenReturn(Map.of("id", "4"));
    when(authorization.effectivePermissions("4")).thenReturn(new EffectivePermissions(List.of(), List.of(), Map.of()));
    var result = auth.login(new Requests.Login(" U4 ", "provided"), session);
    assertEquals("4", session.getAttribute(AuthorizationService.SESSION_USER));
    assertEquals("stored-hash", session.getAttribute(AuthorizationService.SESSION_CREDENTIAL));
    assertEquals(Map.of("id", "4"), result.get("user"));
    assertFalse(result.containsKey("passwordHash"));
    verify(users).loginTime("4");
  }

  @Test
  void missingAccountStillRunsSlowHashAndClearsPreviousIdentity() {
    session.setAttribute(AuthorizationService.SESSION_USER, "old-user");
    session.setAttribute(AuthorizationService.SESSION_CREDENTIAL, "old-hash");
    when(users.findByAccount("missing")).thenReturn(Optional.empty());
    var error = assertThrows(ApiException.class, () -> auth.login(new Requests.Login("missing", "provided"), session));
    assertEquals("INVALID_CREDENTIALS", error.code());
    assertNull(session.getAttribute(AuthorizationService.SESSION_USER));
    assertNull(session.getAttribute(AuthorizationService.SESSION_CREDENTIAL));
    verify(passwords).matches("provided", "dummy-hash");
    verify(users, never()).loginTime(anyString());
  }

  @Test
  void disabledAndWrongPasswordAndMalformedHashShareCredentialFailure() {
    when(users.findByAccount("U4")).thenReturn(Optional.of(AuthorizationServiceTest.user("4", "10", false, false, "stored-hash")));
    when(passwords.matches("provided", "stored-hash")).thenReturn(true);
    var disabled = assertThrows(ApiException.class, () -> auth.login(new Requests.Login("U4", "provided"), session));
    when(users.findByAccount("U4")).thenReturn(Optional.of(AuthorizationServiceTest.user("4", "10", true, false, "stored-hash")));
    when(passwords.matches("provided", "stored-hash")).thenReturn(false);
    var wrong = assertThrows(ApiException.class, () -> auth.login(new Requests.Login("U4", "provided"), session));
    when(passwords.matches("provided", "stored-hash")).thenThrow(new IllegalArgumentException("unsupported hash"));
    var malformed = assertThrows(ApiException.class, () -> auth.login(new Requests.Login("U4", "provided"), session));
    assertEquals(disabled.code(), wrong.code());
    assertEquals(wrong.code(), malformed.code());
    assertEquals(disabled.getMessage(), malformed.getMessage());
    verify(users, never()).loginTime(anyString());
  }

  @Test
  void wrongOldPasswordDoesNotUpdateDatabaseOrCredentialMarker() {
    when(authorization.current(session)).thenReturn(AuthorizationServiceTest.user("4", "10", true, true, "stored-hash"));
    var error = assertThrows(ApiException.class, () -> auth.password(session, new Requests.Password("wrong", "new-password")));
    assertEquals("BAD_OLD_PASSWORD", error.code());
    verify(users, never()).password(anyString(), anyString(), anyBoolean());
  }

  @Test
  void unchangedPasswordIsRejectedBeforeWriting() {
    when(authorization.current(session)).thenReturn(AuthorizationServiceTest.user("4", "10", true, true, "stored-hash"));
    when(passwords.matches("same-password", "stored-hash")).thenReturn(true);
    assertEquals("VALIDATION", assertThrows(ApiException.class, () -> auth.password(session, new Requests.Password("same-password", "same-password"))).code());
    verify(users, never()).password(anyString(), anyString(), anyBoolean());
  }

  @Test
  void forcedPasswordChangeUpdatesOnlyCurrentSessionMarkerAndClearsMustChangeFlag() {
    when(authorization.current(session)).thenReturn(AuthorizationServiceTest.user("4", "10", true, true, "stored-hash"));
    when(passwords.matches("old-password", "stored-hash")).thenReturn(true);
    when(passwords.encode("new-password")).thenReturn("new-salted-hash");
    auth.password(session, new Requests.Password("old-password", "new-password"));
    verify(users).password("4", "new-salted-hash", false);
    assertEquals("new-salted-hash", session.getAttribute(AuthorizationService.SESSION_CREDENTIAL));
    verify(authorization, never()).ready(any());
  }

  @Test
  void logoutInvalidatesAuthenticatedSession() {
    auth.logout(session);
    verify(authorization).current(session);
    assertTrue(session.isInvalid());
  }
}
