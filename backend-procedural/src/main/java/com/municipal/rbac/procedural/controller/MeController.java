package com.municipal.rbac.procedural.controller;

import com.municipal.rbac.procedural.dto.Requests;
import com.municipal.rbac.procedural.service.*;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/**
 * 本人信息、有效权限和改密入口；强制改密时只允许本人信息及密码操作，不放行业务接口。
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {
  private final MeService me;
  private final AuthService auth;

  public MeController(MeService me, AuthService auth) {
    this.me = me;
    this.auth = auth;
  }

  @GetMapping
  public Map<String, Object> me(HttpSession session) {
    return me.me(session);
  }

  @GetMapping("/permissions")
  public Map<String, Object> permissions(HttpSession session) {
    return me.permissions(session);
  }

  @PatchMapping("/password")
  public Map<String, Object> password(
      @Valid @RequestBody Requests.Password body, HttpSession session) {
    auth.password(session, body);
    return Map.of("success", true);
  }
}
