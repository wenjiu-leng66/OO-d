package com.municipal.rbac.procedural.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.municipal.rbac.procedural.dto.Requests;
import com.municipal.rbac.procedural.error.ApiException;
import com.municipal.rbac.procedural.service.AuthService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

/** 控制器边界单元测试；路由、JSON、@Valid 处理仍由 MockMvc 集成用例覆盖。 */
class AuthControllerTest {
  @Test
  void successfulAuthenticationRotatesSessionIdWithoutLosingSessionIdentity() {
    var service = mock(AuthService.class);
    var controller = new AuthController(service);
    var request = new MockHttpServletRequest();
    var session = new MockHttpSession();
    request.setSession(session);
    String oldId = session.getId();
    var body = new Requests.Login("U1", "test-password");
    when(service.login(body, session)).thenAnswer(invocation -> {
      session.setAttribute("userId", "1");
      return Map.of("mustChangePassword", false);
    });
    var result = controller.login(body, session, request);
    assertNotEquals(oldId, session.getId());
    assertEquals("1", session.getAttribute("userId"));
    assertEquals(false, result.get("mustChangePassword"));
  }

  @Test
  void failedAuthenticationDoesNotRotateSessionOrReturnSuccess() {
    var service = mock(AuthService.class);
    var controller = new AuthController(service);
    var request = new MockHttpServletRequest();
    var session = new MockHttpSession();
    request.setSession(session);
    String oldId = session.getId();
    when(service.login(any(), eq(session))).thenThrow(ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid credentials"));
    assertThrows(ApiException.class, () -> controller.login(new Requests.Login("U1", "wrong"), session, request));
    assertEquals(oldId, session.getId());
  }
}
