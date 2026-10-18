package com.municipal.rbac.procedural.controller;

import com.municipal.rbac.procedural.dto.Requests;
import com.municipal.rbac.procedural.service.AuthService;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

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
    request.changeSessionId();
    return result;
  }

  @PostMapping("/logout")
  public Map<String, Object> logout(HttpSession session) {
    auth.logout(session);
    return Map.of("success", true);
  }
}
