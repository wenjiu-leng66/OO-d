package com.municipal.rbac.procedural.controller;

import com.municipal.rbac.procedural.dto.Requests;
import com.municipal.rbac.procedural.service.RoleService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {
  private final RoleService roles;

  public RoleController(RoleService roles) {
    this.roles = roles;
  }

  @GetMapping
  public Map<String, Object> list(
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      HttpSession session) {
    return roles.list(session, keyword, page, pageSize);
  }

  @GetMapping("/{id}")
  public Map<String, Object> detail(@PathVariable String id, HttpSession session) {
    return roles.detail(session, id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(
      @Valid @RequestBody Requests.CreateRole body, HttpSession session) {
    return roles.create(session, body);
  }

  @PatchMapping("/{id}")
  public Map<String, Object> update(
      @PathVariable String id, @Valid @RequestBody Requests.UpdateRole body, HttpSession session) {
    return roles.update(session, id, body);
  }

  @PatchMapping("/{id}/status")
  public Map<String, Object> status(
      @PathVariable String id, @Valid @RequestBody Requests.Enabled body, HttpSession session) {
    return roles.status(session, id, body);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @PathVariable String id,
      @RequestParam(defaultValue = "false") boolean confirm,
      HttpSession session) {
    roles.delete(session, id, confirm);
  }

  @GetMapping("/{id}/users")
  public Map<String, Object> users(
      @PathVariable String id,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      HttpSession session) {
    return roles.roleUsers(session, id, page, pageSize);
  }

  @GetMapping("/{id}/permissions")
  public Map<String, Object> permissions(@PathVariable String id, HttpSession session) {
    return roles.rolePermissions(session, id);
  }

  @PutMapping("/{id}/permissions")
  public Map<String, Object> replace(
      @PathVariable String id, @Valid @RequestBody Requests.Grants body, HttpSession session) {
    return roles.replacePermissions(session, id, body);
  }
}
