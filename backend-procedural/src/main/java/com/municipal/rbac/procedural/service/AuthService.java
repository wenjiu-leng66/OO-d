package com.municipal.rbac.procedural.service;

import static com.municipal.rbac.procedural.vo.Views.map;

import com.municipal.rbac.procedural.dto.Requests;
import com.municipal.rbac.procedural.error.ApiException;
import com.municipal.rbac.procedural.mapper.UserMapper;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 真实后端的账号认证与密码生命周期。身份写入服务端 HttpSession，授权仍逐请求查库。
 * 认证错误对不存在、停用和密码不匹配账号使用同一响应，不披露账号状态。
 */
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

  /**
   * 登录前清除已有身份，避免失败登录沿用上一个账号。工号和手机号共用账号检索入口。
   * 不存在账号仍执行一次慢哈希校验，以减少明显的路径耗时差异；不声称完全消除计时侧信道。
   */
  public Map<String, Object> login(Requests.Login request, HttpSession session) {
    session.removeAttribute(AuthorizationService.SESSION_USER);
    session.removeAttribute(AuthorizationService.SESSION_CREDENTIAL);
    var user = users.findByAccount(request.account().trim()).orElse(null);
    boolean matches = false;
    try {
      matches =
          passwords.matches(request.password(), user == null ? dummyHash : user.passwordHash());
    } catch (IllegalArgumentException ignored) {
      // 不支持或损坏的历史哈希按凭据错误处理，不能绕过认证，也不向响应暴露哈希格式。
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

  /**
   * 销毁当前服务端会话；浏览器继续携带旧 JSESSIONID 也不再具有身份。
   */
  public void logout(HttpSession session) {
    authorization.current(session);
    session.invalidate();
  }

  /**
   * 改密使用 current 而非 ready，使首次改密账户能够执行本操作。
   * 校验旧密码后生成带随机盐的新摘要；只更新当前会话凭据版本，其他旧会话下一请求失效。
   */
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
