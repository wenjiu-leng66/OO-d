package com.municipal.rbac.procedural.service;

import com.municipal.rbac.procedural.error.ApiException;
import com.municipal.rbac.procedural.mapper.RoleMapper;
import com.municipal.rbac.procedural.mapper.UserMapper;
import com.municipal.rbac.procedural.po.RolePermissionPO;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 面向功能后端的集中授权过程：认证（取会话身份）→ 加载用户角色 →
 * 计算有效权限（去重并集）→ 检查权限/本人范围。默认拒绝。
 */
@Service
public class AuthorizationService {

    public static final String SESSION_USER = "userId";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    public AuthorizationService(UserMapper userMapper, RoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
    }

    /** 从会话取当前用户 ID；缺失或失效抛出 401。 */
    public String requireUserId(HttpSession session) {
        Object id = session.getAttribute(SESSION_USER);
        if (!(id instanceof String userId) || userId.isBlank()) {
            throw ApiException.unauthorized("UNAUTHENTICATED", "请先登录。");
        }
        return userId;
    }

    /** RBAC0 有效权限 = 已分配且启用角色的直接权限去重并集。 */
    public EffectivePermissions effectivePermissions(String userId) {
        List<String> roleIds = userMapper.findEnabledRoleIds(userId);
        Set<String> codes = new LinkedHashSet<>();
        Map<String, String> scopes = new HashMap<>();
        for (String roleId : roleIds) {
            for (RolePermissionPO grant : roleMapper.findGrants(roleId)) {
                codes.add(grant.permissionCode());
                scopes.merge(grant.permissionCode(), grant.scope(), AuthorizationService::mergeScope);
            }
        }
        return new EffectivePermissions(roleIds, List.copyOf(codes), scopes);
    }

    /** 要求当前用户具备精确权限码，否则 403。 */
    public void require(HttpSession session, String permissionCode) {
        String userId = requireUserId(session);
        if (!effectivePermissions(userId).permissionCodes().contains(permissionCode)) {
            throw ApiException.forbidden("没有执行该操作的权限。");
        }
    }

    /** 要求目标对象属于当前用户本人，否则 403（本人范围不可绕过）。 */
    public void requireSelf(HttpSession session, String ownerId) {
        String userId = requireUserId(session);
        if (!userId.equals(ownerId)) {
            throw ApiException.forbidden("只能访问本人数据。");
        }
    }

    private static String mergeScope(String a, String b) {
        return "all".equals(a) || "all".equals(b) ? "all" : "self";
    }
}
