package com.municipal.rbac.procedural.service;

import static com.municipal.rbac.procedural.vo.Views.map;

import com.municipal.rbac.procedural.dto.Requests;
import com.municipal.rbac.procedural.error.ApiException;
import com.municipal.rbac.procedural.mapper.UserMapper;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
  private final UserMapper users;
  private final PasswordEncoder passwords;
  private final AuthorizationService authorization;
  private final MeService me;
  private final String dummyHash;

  public AuthService(
      UserMapper users,
      PasswordEncoder passwords,
      AuthorizationService authorization,
      MeService me) {
    this.users = users;
    this.passwords = passwords;
    this.authorization = authorization;
    this.me = me;
    this.dummyHash = passwords.encode("unused-constant-time-comparison");
  }

  public Map<String, Object> login(Requests.Login request, HttpSession session) {
    session.removeAttribute(AuthorizationService.SESSION_USER);
    session.removeAttribute(AuthorizationService.SESSION_CREDENTIAL);
    var user = users.findByAccount(request.account().trim()).orElse(null);
    boolean matches = false;
    try {
      matches =
          passwords.matches(request.password(), user == null ? dummyHash : user.passwordHash());
    } catch (IllegalArgumentException ignored) {
    }
    if (user == null || !user.enabled() || !matches)
      throw ApiException.unauthorized("INVALID_CREDENTIALS", "用户名或密码错误");
    session.setAttribute(AuthorizationService.SESSION_USER, user.id());
    session.setAttribute(AuthorizationService.SESSION_CREDENTIAL, user.passwordHash());
    users.loginTime(user.id());
    return map(
        "user",
        me.me(session),
        "permissionCodes",
        authorization.effectivePermissions(user.id()).permissionCodes(),
        "mustChangePassword",
        user.mustChangePassword());
  }

  public void logout(HttpSession session) {
    authorization.current(session);
    session.invalidate();
  }

  public void password(HttpSession session, Requests.Password request) {
    var user = authorization.current(session);
    if (!passwords.matches(request.oldPassword(), user.passwordHash()))
      throw ApiException.badRequest("BAD_OLD_PASSWORD", "旧密码错误。");
    if (request.oldPassword().equals(request.newPassword()))
      throw ApiException.badRequest("VALIDATION", "新密码不能与旧密码相同。");
    String hash = passwords.encode(request.newPassword());
    users.password(user.id(), hash, false);
    session.setAttribute(AuthorizationService.SESSION_CREDENTIAL, hash);
  }
}
