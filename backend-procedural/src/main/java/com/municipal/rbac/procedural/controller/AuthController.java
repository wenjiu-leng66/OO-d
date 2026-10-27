package com.municipal.rbac.procedural.controller;

import com.municipal.rbac.procedural.dto.Requests;
import com.municipal.rbac.procedural.service.AuthService;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/**
 * 会话认证入口；成功登录后轮换 JSESSIONID，降低固定会话标识被复用的风险。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping("/login")
  public Map<String, Object> login(
      @Valid @RequestBody Requests.Login body, HttpSession session, HttpServletRequest request) {
    var result = auth.login(body, session);
    // 只在认证成功后轮换标识；业务身份保留在同一服务端会话中，不向响应写入密码或摘要。
    request.changeSessionId();
    return result;
  }

  @PostMapping("/logout")
  public Map<String, Object> logout(HttpSession session) {
    auth.logout(session);
    return Map.of("success", true);
  }
}
