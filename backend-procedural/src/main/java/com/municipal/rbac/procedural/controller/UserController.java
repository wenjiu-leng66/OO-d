package com.municipal.rbac.procedural.controller;

import com.municipal.rbac.procedural.dto.CreateUserRequest;
import com.municipal.rbac.procedural.dto.ReplaceRolesRequest;
import com.municipal.rbac.procedural.dto.UpdateUserStatusRequest;
import com.municipal.rbac.procedural.service.UserService;
import com.municipal.rbac.procedural.vo.PageVO;
import com.municipal.rbac.procedural.vo.UserDetailVO;
import com.municipal.rbac.procedural.vo.UserRolesVO;
import com.municipal.rbac.procedural.vo.UserVO;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 用户管理：查询、创建、状态变更、删除与用户角色替换。 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public PageVO<UserVO> list(@RequestParam(required = false) String keyword,
                               @RequestParam(required = false) String departmentId,
                               @RequestParam(required = false) String accountType,
                               @RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "20") int pageSize,
                               HttpSession session) {
        return userService.list(session, keyword, departmentId, accountType, page, pageSize);
    }

    @GetMapping("/{id}")
    public UserDetailVO detail(@PathVariable String id, HttpSession session) {
        return userService.detail(session, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDetailVO create(@Valid @RequestBody CreateUserRequest request, HttpSession session) {
        return userService.create(session, request);
    }

    @PutMapping("/{id}/status")
    public UserVO updateStatus(@PathVariable String id,
                               @Valid @RequestBody UpdateUserStatusRequest request,
                               HttpSession session) {
        return userService.updateStatus(session, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id, HttpSession session) {
        userService.delete(session, id);
    }

    @GetMapping("/{id}/roles")
    public UserRolesVO roles(@PathVariable String id, HttpSession session) {
        return userService.userRoles(session, id);
    }

    @PutMapping("/{id}/roles")
    public UserRolesVO replaceRoles(@PathVariable String id,
                                    @Valid @RequestBody ReplaceRolesRequest request,
                                    HttpSession session) {
        return userService.replaceRoles(session, id, request);
    }
}
