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

@Configuration
public class BrowserSecurityConfig {
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
