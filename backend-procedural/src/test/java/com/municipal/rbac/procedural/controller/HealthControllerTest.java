package com.municipal.rbac.procedural.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

/** 就绪状态只验证决策；真实数据库可用性由部署 HTTP 冒烟检查。 */
class HealthControllerTest {
  @Test
  void startingApplicationReturns503BeforeQueryingDatabase() {
    var jdbc = mock(JdbcTemplate.class);
    var availability = mock(ApplicationAvailability.class);
    when(availability.getReadinessState()).thenReturn(ReadinessState.REFUSING_TRAFFIC);
    var result = new HealthController(jdbc, availability).health();
    assertEquals(503, result.getStatusCode().value());
    assertEquals("STARTING", result.getBody().get("status"));
    verifyNoInteractions(jdbc);
  }

  @Test
  void readyApplicationMustProbeDatabaseBeforeReturningUp() {
    var jdbc = mock(JdbcTemplate.class);
    var availability = mock(ApplicationAvailability.class);
    when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
    when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
    var result = new HealthController(jdbc, availability).health();
    assertEquals(200, result.getStatusCode().value());
    assertEquals("UP", result.getBody().get("status"));
    verify(jdbc).queryForObject("SELECT 1", Integer.class);
  }

  @Test
  void databaseFailureIsNotReportedAsHealthy() {
    var jdbc = mock(JdbcTemplate.class);
    var availability = mock(ApplicationAvailability.class);
    when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
    when(jdbc.queryForObject("SELECT 1", Integer.class)).thenThrow(new DataAccessResourceFailureException("unavailable"));
    assertThrows(DataAccessResourceFailureException.class, () -> new HealthController(jdbc, availability).health());
  }
}
