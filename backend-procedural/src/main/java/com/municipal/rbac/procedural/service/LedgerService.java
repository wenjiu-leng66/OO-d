package com.municipal.rbac.procedural.service;

import static com.municipal.rbac.procedural.vo.Views.*;

import com.municipal.rbac.procedural.error.ApiException;
import com.municipal.rbac.procedural.mapper.*;
import com.municipal.rbac.procedural.po.UserPO;
import jakarta.servlet.http.HttpSession;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {
  private final UserMapper users;
  private final DepartmentMapper departments;
  private final AuthorizationService authorization;

  public LedgerService(
      UserMapper users, DepartmentMapper departments, AuthorizationService authorization) {
    this.users = users;
    this.departments = departments;
    this.authorization = authorization;
  }

  private Map<String, Object> view(UserPO u) {
    return map(
        "id",
        u.id(),
        "username",
        u.username(),
        "name",
        u.name(),
        "departmentId",
        u.departmentId(),
        "departmentName",
        departments.findById(u.departmentId()).map(d -> d.name()).orElse(null),
        "position",
        u.position(),
        "phone",
        u.phone(),
        "email",
        u.email());
  }

  public Map<String, Object> list(
      HttpSession session, String keyword, String departmentId, int page, int size) {
    String scope = authorization.require(session, "hr:f046:read");
    UserService.paging(page, size);
    var actor = authorization.current(session);
    var subtree = departmentId == null ? null : departments.subtree(departmentId);
    return page(
        users.findAll().stream()
            .filter(authorization.visible(actor, scope))
            .filter(u -> subtree == null || subtree.contains(u.departmentId()))
            .filter(
                u ->
                    keyword == null
                        || UserService.contains(u.username(), keyword)
                        || UserService.contains(u.name(), keyword))
            .toList(),
        page,
        size,
        this::view);
  }

  @Transactional
  public Map<String, Object> update(HttpSession session, String id, Map<String, Object> body) {
    var target = authorization.target(session, "hr:f046:edit", id);
    if (body.isEmpty()) throw ApiException.badRequest("VALIDATION", "至少提供一个可编辑字段。");
    for (String key : body.keySet())
      if (!Set.of("phone", "email").contains(key))
        throw ApiException.badRequest("FIELD_NOT_EDITABLE", "只能修改电话和邮箱。");
    for (Object value : body.values())
      if (value != null && !(value instanceof String))
        throw ApiException.badRequest("VALIDATION", "电话和邮箱必须为字符串或null。");
    String
        phone =
            body.containsKey("phone")
                ? UserService.normalize((String) body.get("phone"))
                : target.phone(),
        email =
            body.containsKey("email")
                ? UserService.normalize((String) body.get("email"))
                : target.email();
    if (phone != null && !phone.matches("[+0-9 -]{3,20}"))
      throw ApiException.badRequest("VALIDATION", "电话格式不合法。");
    if (email != null && (email.length() > 64 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")))
      throw ApiException.badRequest("VALIDATION", "邮箱格式不合法。");
    if (users.phoneExists(phone, id))
      throw ApiException.conflict("DUPLICATE_PHONE", "手机号已存在。", null);
    users.updateContact(id, phone, email);
    return view(users.findById(id).orElseThrow());
  }
}
