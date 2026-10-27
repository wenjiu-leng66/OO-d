package com.municipal.rbac.procedural.data;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 启动种子安全前置条件；完整种子数据和重启持久化由集成/部署用例验证。 */
class DataInitializerTest {
  @Test
  void disabledDemoNeverTouchesDatabaseOrPasswordEncoder() throws Exception {
    var jdbc = mock(JdbcTemplate.class);
    var passwords = mock(PasswordEncoder.class);
    new DataInitializer(jdbc, passwords, new ObjectMapper(), false, "").run(mock(ApplicationArguments.class));
    verifyNoInteractions(jdbc, passwords);
  }

  @Test
  void enabledDemoRequiresInjectedPasswordBeforeAnyDatabaseWrite() {
    var jdbc = mock(JdbcTemplate.class);
    var passwords = mock(PasswordEncoder.class);
    var initializer = new DataInitializer(jdbc, passwords, new ObjectMapper(), true, "short");
    assertThrows(IllegalStateException.class, () -> initializer.run(mock(ApplicationArguments.class)));
    verifyNoInteractions(jdbc, passwords);
  }

  @Test
  void existingUserDataSkipsSeedAndDoesNotResetPasswords() throws Exception {
    var jdbc = mock(JdbcTemplate.class);
    var passwords = mock(PasswordEncoder.class);
    when(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class)).thenReturn(1);
    new DataInitializer(jdbc, passwords, new ObjectMapper(), true, "test-password").run(mock(ApplicationArguments.class));
    verify(jdbc).queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class);
    verifyNoMoreInteractions(jdbc);
    verifyNoInteractions(passwords);
  }

  @Test
  void existingCatalogAlsoSkipsSeedEvenWhenUsersAreEmpty() throws Exception {
    var jdbc = mock(JdbcTemplate.class);
    var passwords = mock(PasswordEncoder.class);
    when(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class)).thenReturn(0);
    when(jdbc.queryForObject("SELECT COUNT(*) FROM app_system", Integer.class)).thenReturn(18);
    new DataInitializer(jdbc, passwords, new ObjectMapper(), true, "test-password").run(mock(ApplicationArguments.class));
    verify(jdbc).queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class);
    verify(jdbc).queryForObject("SELECT COUNT(*) FROM app_system", Integer.class);
    verifyNoMoreInteractions(jdbc);
    verifyNoInteractions(passwords);
  }
}
