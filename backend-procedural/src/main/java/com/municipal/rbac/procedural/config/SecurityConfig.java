package com.municipal.rbac.procedural.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.*;

/**
 * 密码编码策略与离线导入统一。新密码使用 Spring Security 5.8 参数的 PBKDF2，随机盐由编码器生成。
 */
@Configuration
public class SecurityConfig {
  /**
   * 带算法前缀保存新摘要，历史带前缀摘要通过 DelegatingPasswordEncoder 校验。
   * PBKDF2 保留长密码的全部输入，避免 bcrypt 72 字节截断影响一期长密码验收。
   * upgradeEncoding 只声明升级需求；本服务登录流程没有据此自动改写历史哈希。
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    PasswordEncoder legacy = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    PasswordEncoder current = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    return new PasswordEncoder() {
      @Override
      public String encode(CharSequence raw) {
        return "{pbkdf2@SpringSecurity_v5_8}" + current.encode(raw);
      }

      @Override
      public boolean matches(CharSequence raw, String encoded) {
        return legacy.matches(raw, encoded);
      }

      @Override
      public boolean upgradeEncoding(String encoded) {
        return !encoded.startsWith("{pbkdf2@SpringSecurity_v5_8}");
      }
    };
  }
}
