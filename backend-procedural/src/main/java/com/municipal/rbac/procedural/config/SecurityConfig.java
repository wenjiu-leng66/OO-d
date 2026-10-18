package com.municipal.rbac.procedural.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.*;

@Configuration
public class SecurityConfig {
  /** New hashes match the importer's PBKDF2 algorithm; legacy prefixed hashes remain readable. */
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
