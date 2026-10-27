package com.municipal.rbac.procedural.config;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 直接执行真实 CORS/Origin 过滤器验证精确来源边界，不启动服务器、不模拟业务登录。
 * 过滤器放行仅表示进入后续认证/授权链，不表示请求已获业务权限；无 Origin 仍沿用既有非浏览器边界。
 */
class BrowserSecurityConfigTest {
  static final String API_ORIGIN = "http://api.example.test:8080";
  static final String TRUSTED_ORIGIN = "https://ui.example.test";
  final BrowserSecurityConfig configuration = new BrowserSecurityConfig();
  final ObjectMapper json = new ObjectMapper();

  MockHttpServletRequest request(String method, String origin) {
    var request = new MockHttpServletRequest(method, "/api/v1/users");
    request.setScheme("http");
    request.setServerName("api.example.test");
    request.setServerPort(8080);
    request.addHeader("Host", "api.example.test:8080");
    if (origin != null) request.addHeader("Origin", origin);
    return request;
  }

  MockHttpServletResponse filter(
      String allowedOrigins, MockHttpServletRequest request, AtomicBoolean reachedBusiness)
      throws Exception {
    var response = new MockHttpServletResponse();
    var originGuard = configuration.originGuard(allowedOrigins, json);
    FilterChain business = (req, res) -> reachedBusiness.set(true);
    configuration
        .corsFilter(allowedOrigins)
        .doFilter(request, response, (req, res) -> originGuard.doFilter(req, res, business));
    return response;
  }

  @Test
  void emptyAllowlistStillAcceptsSameOriginWrite() throws Exception {
    var reached = new AtomicBoolean();
    var response = filter("", request("POST", API_ORIGIN), reached);
    assertTrue(reached.get());
    assertEquals(200, response.getStatus());
    assertNull(response.getHeader("Access-Control-Allow-Origin"));
  }

  @Test
  void emptyAllowlistRejectsCrossOriginAndDoesNotInheritDevelopmentOrigins() throws Exception {
    for (String origin : new String[] {TRUSTED_ORIGIN, "http://localhost:5173", "http://127.0.0.1:5173"}) {
      var reached = new AtomicBoolean();
      var response = filter("", request("POST", origin), reached);
      assertFalse(reached.get(), origin);
      assertEquals(403, response.getStatus());
      assertNull(response.getHeader("Access-Control-Allow-Origin"));
    }
  }

  @Test
  void configuredExactOriginReceivesCredentialedCorsHeaders() throws Exception {
    var reached = new AtomicBoolean();
    var response = filter(TRUSTED_ORIGIN, request("POST", TRUSTED_ORIGIN), reached);
    assertTrue(reached.get());
    assertEquals(TRUSTED_ORIGIN, response.getHeader("Access-Control-Allow-Origin"));
    assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"));
  }

  @Test
  void configuredOriginDoesNotAllowOtherSchemesPortsSubdomainsOrNullOrigin() throws Exception {
    for (String origin : new String[] {"http://ui.example.test", "https://ui.example.test:8443", "https://other.ui.example.test", "https://untrusted.example.test", "null"}) {
      var reached = new AtomicBoolean();
      var response = filter(TRUSTED_ORIGIN, request("POST", origin), reached);
      assertFalse(reached.get(), origin);
      assertEquals(403, response.getStatus());
      assertNull(response.getHeader("Access-Control-Allow-Credentials"));
    }
  }

  @Test
  void trustedPreflightAllowsContentTypeButDoesNotInvokeBusinessEndpoint() throws Exception {
    var request = request("OPTIONS", TRUSTED_ORIGIN);
    request.addHeader("Access-Control-Request-Method", "POST");
    request.addHeader("Access-Control-Request-Headers", "Content-Type");
    var reached = new AtomicBoolean();
    var response = filter(TRUSTED_ORIGIN, request, reached);
    assertFalse(reached.get());
    assertEquals(200, response.getStatus());
    assertEquals(TRUSTED_ORIGIN, response.getHeader("Access-Control-Allow-Origin"));
    assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"));
    assertTrue(response.getHeader("Access-Control-Allow-Methods").contains("POST"));
    assertEquals("Content-Type", response.getHeader("Access-Control-Allow-Headers"));
  }

  @Test
  void untrustedPreflightIsRejectedWithoutCredentialHeaders() throws Exception {
    var request = request("OPTIONS", "https://untrusted.example.test");
    request.addHeader("Access-Control-Request-Method", "PATCH");
    var reached = new AtomicBoolean();
    var response = filter(TRUSTED_ORIGIN, request, reached);
    assertFalse(reached.get());
    assertEquals(403, response.getStatus());
    assertNull(response.getHeader("Access-Control-Allow-Origin"));
  }

  @Test
  void originGuardAloneRejectsUntrustedWriteWithApiErrorEnvelope() throws Exception {
    var reached = new AtomicBoolean();
    var response = new MockHttpServletResponse();
    configuration.originGuard("", json).doFilter(
        request("PATCH", "https://untrusted.example.test"), response,
        (req, res) -> reached.set(true));
    assertFalse(reached.get());
    assertEquals(403, response.getStatus());
    assertEquals("FORBIDDEN", json.readTree(response.getContentAsString()).path("error").path("code").asText());
  }

  @Test
  void absentOriginRetainsNonBrowserBehaviorWithoutClaimingAuthentication() throws Exception {
    var reached = new AtomicBoolean();
    var response = filter("", request("POST", null), reached);
    assertTrue(reached.get());
    assertEquals(200, response.getStatus());
    assertNull(response.getHeader("Access-Control-Allow-Origin"));
  }
}
