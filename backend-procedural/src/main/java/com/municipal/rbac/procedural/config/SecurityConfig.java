package com.municipal.rbac.procedural.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 仅提供 BCrypt 密码编码器；认证与授权过程由 AuthService / AuthorizationService
 * 显式实现，不启用 Spring Security 的自动配置链。
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
