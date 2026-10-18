package com.municipal.rbac.procedural.controller;

import com.municipal.rbac.procedural.dto.LoginRequest;
import com.municipal.rbac.procedural.service.AuthService;
import com.municipal.rbac.procedural.vo.LoginVO;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 登录与退出。 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginVO login(@Valid @RequestBody LoginRequest request, HttpSession session) {
        return authService.login(request, session);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpSession session) {
        authService.logout(session);
        return Map.of("success", true);
    }
}
