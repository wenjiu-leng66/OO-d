package com.municipal.rbac.procedural.controller;

import java.util.Map;
import org.springframework.boot.availability.*;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * 就绪检查同时验证应用接受流量状态及数据库连接；不返回凭据或内部业务数据。
 */
@RestController
public class HealthController {
  private final JdbcTemplate jdbc;
  private final ApplicationAvailability availability;

  public HealthController(JdbcTemplate jdbc, ApplicationAvailability availability) {
    this.jdbc = jdbc;
    this.availability = availability;
  }

  @GetMapping("/api/v1/health")
  public ResponseEntity<Map<String, Object>> health() {
    if (availability.getReadinessState() != ReadinessState.ACCEPTING_TRAFFIC)
      return ResponseEntity.status(503).body(Map.of("status", "STARTING"));
    // 数据库不可达会进入统一 500 错误处理；健康探测因此不会把仅有 HTTP 进程存活误当作就绪。
    jdbc.queryForObject("SELECT 1", Integer.class);
    return ResponseEntity.ok(Map.of("status", "UP"));
  }
}
