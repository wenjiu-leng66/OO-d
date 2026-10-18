package com.municipal.rbac.procedural.mapper;

import static com.municipal.rbac.procedural.vo.Views.map;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogMapper {
  private final JdbcTemplate jdbc;

  public CatalogMapper(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> functions() {
    return jdbc.query(
        "SELECT f.id,f.code,f.name,f.description,m.id mid,m.name mname,s.code scode,s.name sname"
            + " FROM app_function f JOIN app_module m ON m.id=f.module_id JOIN app_system s ON"
            + " s.id=m.system_id ORDER BY s.sort_no,s.id,m.sort_no,m.id,f.sort_no,f.id",
        (r, i) ->
            map(
                "id",
                r.getString("id"),
                "functionCode",
                r.getString("code"),
                "name",
                r.getString("name"),
                "description",
                r.getString("description"),
                "moduleId",
                r.getString("mid"),
                "moduleName",
                r.getString("mname"),
                "systemCode",
                r.getString("scode"),
                "systemName",
                r.getString("sname")));
  }

  public Optional<Map<String, Object>> function(String code) {
    return functions().stream().filter(f -> f.get("functionCode").equals(code)).findFirst();
  }

  public List<String> permissionCodes() {
    return jdbc.queryForList("SELECT code FROM sys_permission ORDER BY id", String.class);
  }

  public List<Map<String, Object>> actions() {
    return jdbc.query(
        "SELECT code,name FROM sys_action ORDER BY id",
        (r, i) -> map("code", r.getString(1), "label", r.getString(2)));
  }

  public Map<String, Object> stats() {
    return map(
        "systems",
        count("app_system"),
        "modules",
        count("app_module"),
        "functions",
        count("app_function"),
        "permissions",
        count("sys_permission"));
  }

  private long count(String table) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
  }
}
