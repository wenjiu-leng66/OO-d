package com.municipal.rbac.procedural.controller;

import java.util.Map;
import org.springframework.boot.availability.*;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

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
    jdbc.queryForObject("SELECT 1", Integer.class);
    return ResponseEntity.ok(Map.of("status", "UP"));
  }
}
