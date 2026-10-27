package com.municipal.rbac.procedural.config;

import static com.municipal.rbac.procedural.vo.Views.map;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.web.cors.*;
import org.springframework.web.filter.*;

/**
 * 浏览器来源边界。CORS 白名单仅放行已配置源携带 Cookie；写请求另检查显式 Origin。
 * 缺少 Origin 的非浏览器请求仍可进入业务鉴权，本过滤器不声称替代完整的 CSRF 令牌机制。
 */
@Configuration
public class BrowserSecurityConfig {
  /**
   * 携带会话 Cookie 时不能用任意源通配符；允许来源由部署配置提供，开发地址不应直接当作生产白名单。
   */
  @Bean
  public CorsFilter corsFilter(@Value("${rbac.allowed-origins:}") String configured) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(
        Arrays.stream(configured.split(",")).filter(s -> !s.isBlank()).toList());
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Content-Type", "Accept"));
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return new CorsFilter(source);
  }

  /**
   * 仅对 /api/ 的写方法检查来源；拒绝响应在 Filter 内直接构造，保持与业务错误相同的 JSON 包络。
   * 同源比较使用当前请求 scheme/Host，部署反向代理须正确传递并约束可信转发头。
   */
  @Bean
  public OncePerRequestFilter originGuard(
      @Value("${rbac.allowed-origins:}") String configured, ObjectMapper json) {
    Set<String> allowed = new HashSet<>(Arrays.asList(configured.split(",")));
    return new OncePerRequestFilter() {
      @Override
      protected void doFilterInternal(
          HttpServletRequest request, HttpServletResponse response, FilterChain chain)
          throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        String own = request.getScheme() + "://" + request.getHeader("Host");
        if (request.getRequestURI().startsWith("/api/")
            && Set.of("POST", "PUT", "PATCH", "DELETE").contains(request.getMethod())
            && origin != null
            && !origin.equals(own)
            && !allowed.contains(origin)) {
          response.setStatus(403);
          response.setContentType("application/json");
          json.writeValue(
              response.getOutputStream(),
              map(
                  "error",
                  map(
                      "code",
                      "FORBIDDEN",
                      "message",
                      "Origin is not allowed.",
                      "requestId",
                      UUID.randomUUID().toString(),
                      "details",
                      null)));
          return;
        }
        chain.doFilter(request, response);
      }
    };
  }
}
