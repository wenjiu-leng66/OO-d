package com.municipal.rbac.procedural.controller;

import com.municipal.rbac.procedural.service.CatalogService;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/**
 * 只读权限目录 HTTP 适配层；分页与目录查看权在 CatalogService 统一检查。
 */
@RestController
@RequestMapping("/api/v1")
public class CatalogController {
  private final CatalogService catalog;

  public CatalogController(CatalogService catalog) {
    this.catalog = catalog;
  }

  @GetMapping("/catalog/functions")
  public Map<String, Object> functions(
      @RequestParam(required = false) String systemCode,
      @RequestParam(required = false) String moduleId,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      HttpSession session) {
    return catalog.functions(session, systemCode, moduleId, keyword, page, pageSize);
  }

  @GetMapping("/permissions")
  public Map<String, Object> permissions(
      @RequestParam(required = false) String systemCode,
      @RequestParam(required = false) String moduleId,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      HttpSession session) {
    return catalog.permissions(session, systemCode, moduleId, keyword, page, pageSize);
  }

  @GetMapping("/resources")
  public Map<String, Object> resources(
      @RequestParam(required = false) String parentId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      HttpSession session) {
    return catalog.resources(session, parentId, page, pageSize);
  }
}
