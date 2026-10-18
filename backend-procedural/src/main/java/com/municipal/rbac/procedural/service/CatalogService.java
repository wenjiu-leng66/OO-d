package com.municipal.rbac.procedural.service;

import com.municipal.rbac.procedural.error.ApiException;
import com.municipal.rbac.procedural.mapper.CatalogMapper;
import com.municipal.rbac.procedural.po.PermissionPO;
import com.municipal.rbac.procedural.po.ResourcePO;
import com.municipal.rbac.procedural.vo.PageVO;
import com.municipal.rbac.procedural.vo.PermissionItemVO;
import com.municipal.rbac.procedural.vo.ResourceItemVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/** 权限目录与资源目录查询过程。scopeOptions 字符串在此处拆分为列表组装 VO。 */
@Service
public class CatalogService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CatalogMapper catalogMapper;
    private final AuthorizationService authorization;

    public CatalogService(CatalogMapper catalogMapper, AuthorizationService authorization) {
        this.catalogMapper = catalogMapper;
        this.authorization = authorization;
    }

    public PageVO<PermissionItemVO> listPermissions(HttpSession session, String systemId, String moduleId,
                                                    String keyword, int page, int pageSize) {
        authorization.require(session, "rbac:permission:read");
        int size = clamp(pageSize);
        int offset = (Math.max(page, 1) - 1) * size;
        List<PermissionItemVO> items = catalogMapper.findPermissions(systemId, moduleId, keyword, offset, size)
                .stream().map(this::toPermissionVO).toList();
        long total = catalogMapper.countPermissions(systemId, moduleId, keyword);
        return new PageVO<>(items, Math.max(page, 1), size, total);
    }

    public PageVO<ResourceItemVO> listResources(HttpSession session, String parentId, int page, int pageSize) {
        authorization.require(session, "rbac:permission:read");
        if (parentId != null && !parentId.isBlank() && !catalogMapper.resourceExists(parentId)) {
            throw ApiException.notFound("RESOURCE_NOT_FOUND", "指定的父目录不存在。");
        }
        int size = clamp(pageSize);
        int offset = (Math.max(page, 1) - 1) * size;
        List<ResourceItemVO> items = catalogMapper.findResources(parentId, offset, size)
                .stream().map(this::toResourceVO).toList();
        long total = catalogMapper.countResources(parentId);
        return new PageVO<>(items, Math.max(page, 1), size, total);
    }

    private PermissionItemVO toPermissionVO(PermissionPO po) {
        List<String> scopes = (po.scopeOptions() == null || po.scopeOptions().isBlank())
                ? List.of()
                : Arrays.stream(po.scopeOptions().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return new PermissionItemVO(po.code(), po.name(), po.systemId(), po.systemName(),
                po.moduleId(), po.moduleName(), po.resourceCode(), po.resourceName(),
                po.action(), scopes);
    }

    private ResourceItemVO toResourceVO(ResourcePO po) {
        return new ResourceItemVO(po.id(), po.name(), po.type(), po.parentId(),
                catalogMapper.hasChildren(po.id()));
    }

    private int clamp(int pageSize) {
        if (pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
