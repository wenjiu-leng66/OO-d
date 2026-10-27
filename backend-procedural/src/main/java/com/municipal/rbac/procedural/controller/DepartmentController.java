package com.municipal.rbac.procedural.controller;

import com.municipal.rbac.procedural.service.DepartmentService;
import jakarta.servlet.http.HttpSession;
import java.util.*;
import org.springframework.web.bind.annotation.*;

/**
 * 组织树及含下级成员查询入口，范围和员工粒度裁剪由 DepartmentService 负责。
 */
@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {
  private final DepartmentService departments;

  public DepartmentController(DepartmentService departments) {
    this.departments = departments;
  }

  @GetMapping
  public List<Map<String, Object>> tree(HttpSession session) {
    return departments.tree(session);
  }

  @GetMapping("/{id}/members")
  public Map<String, Object> members(
      @PathVariable String id,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      HttpSession session) {
    return departments.members(session, id, page, pageSize);
  }
}
