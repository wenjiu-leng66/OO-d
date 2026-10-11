package com.municipal.rbac.procedural.service;

import com.municipal.rbac.procedural.dto.LoginRequest;
import com.municipal.rbac.procedural.error.ApiException;
import com.municipal.rbac.procedural.mapper.DepartmentMapper;
import com.municipal.rbac.procedural.mapper.UserMapper;
import com.municipal.rbac.procedural.po.UserPO;
import com.municipal.rbac.procedural.vo.LoginVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** 认证过程：校验账号密码与状态，登记会话并返回身份与有效权限摘要。 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthorizationService authorization;

    public AuthService(UserMapper userMapper,
                       DepartmentMapper departmentMapper,
                       PasswordEncoder passwordEncoder,
                       AuthorizationService authorization) {
        this.userMapper = userMapper;
        this.departmentMapper = departmentMapper;
        this.passwordEncoder = passwordEncoder;
        this.authorization = authorization;
    }

    public LoginVO login(LoginRequest request, HttpSession session) {
        UserPO user = userMapper.findByAccount(request.account().trim())
                .orElseThrow(() -> ApiException.unauthorized("INVALID_CREDENTIALS", "账号或密码错误。"));
        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "账号或密码错误。");
        }
        if (!"active".equals(user.status())) {
            throw ApiException.forbidden("ACCOUNT_DISABLED", "账号已停用，请联系管理员。");
        }
        session.setAttribute(AuthorizationService.SESSION_USER, user.id());
        var permissions = authorization.effectivePermissions(user.id());
        String departmentName = departmentName(user.departmentId());
        return new LoginVO(
                new LoginVO.UserSummary(user.id(), user.name(), user.employeeNo(), departmentName),
                permissions.permissionCodes());
    }

    public void logout(HttpSession session) {
        session.invalidate();
    }

    private String departmentName(String departmentId) {
        if (departmentId == null) {
            return null;
        }
        return departmentMapper.findById(departmentId).map(d -> d.name()).orElse(null);
    }
}
