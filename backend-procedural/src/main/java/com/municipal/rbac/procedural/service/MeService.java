package com.municipal.rbac.procedural.service;

import com.municipal.rbac.procedural.error.ApiException;
import com.municipal.rbac.procedural.mapper.DepartmentMapper;
import com.municipal.rbac.procedural.mapper.UserMapper;
import com.municipal.rbac.procedural.po.UserPO;
import com.municipal.rbac.procedural.vo.MeVO;
import com.municipal.rbac.procedural.vo.PermissionsVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.List;

/** 当前登录身份（/me）与当前有效权限查询过程。 */
@Service
public class MeService {

    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final AuthorizationService authorization;

    public MeService(UserMapper userMapper,
                     DepartmentMapper departmentMapper,
                     AuthorizationService authorization) {
        this.userMapper = userMapper;
        this.departmentMapper = departmentMapper;
        this.authorization = authorization;
    }

    public MeVO me(HttpSession session) {
        UserPO user = currentUser(session);
        String departmentName = user.departmentId() == null ? null
                : departmentMapper.findById(user.departmentId()).map(d -> d.name()).orElse(null);
        return new MeVO(user.id(), user.name(), user.employeeNo(),
                user.departmentId(), departmentName, user.accountType());
    }

    public PermissionsVO permissions(HttpSession session) {
        String userId = authorization.requireUserId(session);
        UserPO user = currentUser(session);
        EffectivePermissions perms = authorization.effectivePermissions(userId);
        List<PermissionsVO.Scope> scopes = perms.permissionCodes().stream()
                .map(code -> new PermissionsVO.Scope(code, perms.scopes().getOrDefault(code, "self")))
                .toList();
        return new PermissionsVO(userId, perms.roleIds(), perms.permissionCodes(), scopes, user.roleVersion());
    }

    private UserPO currentUser(HttpSession session) {
        String userId = authorization.requireUserId(session);
        return userMapper.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "会话对应的用户不存在。"));
    }
}
