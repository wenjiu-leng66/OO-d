package com.municipal.rbac.procedural.service;

import com.municipal.rbac.procedural.dto.ReplacePermissionsRequest;
import com.municipal.rbac.procedural.error.ApiException;
import com.municipal.rbac.procedural.mapper.CatalogMapper;
import com.municipal.rbac.procedural.mapper.RoleMapper;
import com.municipal.rbac.procedural.po.RolePO;
import com.municipal.rbac.procedural.vo.PageVO;
import com.municipal.rbac.procedural.vo.RolePermissionsVO;
import com.municipal.rbac.procedural.vo.RoleVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** 角色查询与角色权限替换过程。 */
@Service
public class RoleService {

    private static final int MAX_PAGE_SIZE = 100;

    private final RoleMapper roleMapper;
    private final CatalogMapper catalogMapper;
    private final AuthorizationService authorization;

    public RoleService(RoleMapper roleMapper,
                       CatalogMapper catalogMapper,
                       AuthorizationService authorization) {
        this.roleMapper = roleMapper;
        this.catalogMapper = catalogMapper;
        this.authorization = authorization;
    }

    public PageVO<RoleVO> list(HttpSession session, String keyword, int page, int pageSize) {
        authorization.require(session, "rbac:role:read");
        int size = clamp(pageSize);
        int offset = (Math.max(page, 1) - 1) * size;
        List<RoleVO> items = roleMapper.findPage(keyword, offset, size).stream()
                .map(role -> new RoleVO(role.id(), role.code(), role.name(), role.description(),
                        roleMapper.findPermissionCodes(role.id()), role.permissionVersion()))
                .toList();
        long total = roleMapper.count(keyword);
        return new PageVO<>(items, Math.max(page, 1), size, total);
    }

    public RolePermissionsVO rolePermissions(HttpSession session, String id) {
        authorization.require(session, "rbac:role:read");
        requireRole(id);
        return new RolePermissionsVO(id, roleMapper.findPermissionCodes(id),
                roleMapper.permissionVersion(id));
    }

    @Transactional
    public RolePermissionsVO replacePermissions(HttpSession session, String id,
                                               ReplacePermissionsRequest request) {
        authorization.require(session, "rbac:role:grant");
        requireRole(id);

        if (new HashSet<>(request.permissionCodes()).size() != request.permissionCodes().size()) {
            throw ApiException.badRequest("DUPLICATE_ITEM", "permissionCodes 中存在重复元素。");
        }
        for (String code : request.permissionCodes()) {
            if (!catalogMapper.permissionExists(code)) {
                throw ApiException.badRequest("UNKNOWN_PERMISSION", "存在未知权限码。", Map.of("permissionCode", code));
            }
        }

        int current = roleMapper.permissionVersion(id);
        if (request.version() != current) {
            throw ApiException.conflict("VERSION_CONFLICT", "授权已被其他人修改，请重新读取并复核。",
                    Map.of("expectedVersion", request.version(), "currentVersion", current));
        }

        int next = current + 1;
        roleMapper.replacePermissions(id, request.permissionCodes(), next);
        return new RolePermissionsVO(id, request.permissionCodes(), next);
    }

    private RolePO requireRole(String id) {
        return roleMapper.findById(id)
                .orElseThrow(() -> ApiException.notFound("ROLE_NOT_FOUND", "角色不存在。"));
    }

    private int clamp(int pageSize) {
        if (pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
