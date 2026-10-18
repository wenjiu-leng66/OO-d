package com.municipal.rbac.procedural.controller;

import com.municipal.rbac.procedural.service.CatalogService;
import com.municipal.rbac.procedural.vo.PageVO;
import com.municipal.rbac.procedural.vo.PermissionItemVO;
import com.municipal.rbac.procedural.vo.ResourceItemVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 权限目录与资源目录查询。 */
@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/permissions")
    public PageVO<PermissionItemVO> permissions(@RequestParam(required = false) String systemId,
                                                @RequestParam(required = false) String moduleId,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int pageSize,
                                                HttpSession session) {
        return catalogService.listPermissions(session, systemId, moduleId, keyword, page, pageSize);
    }

    @GetMapping("/resources")
    public PageVO<ResourceItemVO> resources(@RequestParam(required = false) String parentId,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int pageSize,
                                            HttpSession session) {
        return catalogService.listResources(session, parentId, page, pageSize);
    }
}
