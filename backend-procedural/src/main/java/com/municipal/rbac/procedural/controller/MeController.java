package com.municipal.rbac.procedural.controller;

import com.municipal.rbac.procedural.service.MeService;
import com.municipal.rbac.procedural.vo.MeVO;
import com.municipal.rbac.procedural.vo.PermissionsVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 当前身份与当前有效权限。 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final MeService meService;

    public MeController(MeService meService) {
        this.meService = meService;
    }

    @GetMapping
    public MeVO me(HttpSession session) {
        return meService.me(session);
    }

    @GetMapping("/permissions")
    public PermissionsVO permissions(HttpSession session) {
        return meService.permissions(session);
    }
}
