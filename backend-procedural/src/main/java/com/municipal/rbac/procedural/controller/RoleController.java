package com.municipal.rbac.procedural.controller;

import com.municipal.rbac.procedural.dto.ReplacePermissionsRequest;
import com.municipal.rbac.procedural.service.RoleService;
import com.municipal.rbac.procedural.vo.PageVO;
import com.municipal.rbac.procedural.vo.RolePermissionsVO;
import com.municipal.rbac.procedural.vo.RoleVO;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 角色查询与角色权限替换。 */
@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public PageVO<RoleVO> list(@RequestParam(required = false) String keyword,
                               @RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "20") int pageSize,
                               HttpSession session) {
        return roleService.list(session, keyword, page, pageSize);
    }

    @GetMapping("/{id}/permissions")
    public RolePermissionsVO permissions(@PathVariable String id, HttpSession session) {
        return roleService.rolePermissions(session, id);
    }

    @PutMapping("/{id}/permissions")
    public RolePermissionsVO replacePermissions(@PathVariable String id,
                                                @Valid @RequestBody ReplacePermissionsRequest request,
                                                HttpSession session) {
        return roleService.replacePermissions(session, id, request);
    }
}
