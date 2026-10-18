package com.municipal.rbac.procedural.controller;

import com.municipal.rbac.procedural.dto.Requests;
import com.municipal.rbac.procedural.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
  private final UserService users;

  public UserController(UserService users) {
    this.users = users;
  }

  @GetMapping
  public Map<String, Object> list(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String departmentId,
      @RequestParam(required = false) String roleId,
      @RequestParam(required = false) String username,
      @RequestParam(required = false) String name,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      HttpSession session) {
    return users.list(session, keyword, departmentId, roleId, username, name, page, pageSize);
  }

  @GetMapping("/{id}")
  public Map<String, Object> detail(@PathVariable String id, HttpSession session) {
    return users.detail(session, id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(
      @Valid @RequestBody Requests.CreateUser body, HttpSession session) {
    return users.create(session, body);
  }

  @PutMapping("/{id}")
  public Map<String, Object> update(
      @PathVariable String id, @Valid @RequestBody Requests.UpdateUser body, HttpSession session) {
    return users.update(session, id, body);
  }

  @PatchMapping("/{id}/status")
  public Map<String, Object> status(
      @PathVariable String id, @Valid @RequestBody Requests.Status body, HttpSession session) {
    return users.status(session, id, body);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String id, HttpSession session) {
    users.delete(session, id);
  }

  @PostMapping("/{id}/password-reset")
  public Map<String, Object> reset(@PathVariable String id, HttpSession session) {
    users.reset(session, id);
    return Map.of("success", true);
  }

  @GetMapping("/{id}/roles")
  public Map<String, Object> roles(@PathVariable String id, HttpSession session) {
    return users.userRoles(session, id);
  }

  @PutMapping("/{id}/roles")
  public Map<String, Object> replaceRoles(
      @PathVariable String id, @Valid @RequestBody Requests.Roles body, HttpSession session) {
    return users.replaceRoles(session, id, body);
  }
}
