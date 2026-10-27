package com.municipal.rbac.procedural.mapper;

import com.municipal.rbac.procedural.po.DepartmentPO;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 组织数据只读访问及下级集合展开；所有范围判定都以稳定部门 ID 而不是名称为依据。
 */
@Repository
public class DepartmentMapper {
  private final JdbcTemplate jdbc;

  public DepartmentMapper(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<DepartmentPO> findAll() {
    return jdbc.query(
        "SELECT * FROM sys_dept ORDER BY sort_no,id",
        (r, i) ->
            new DepartmentPO(
                r.getString("id"),
                r.getString("name"),
                r.getString("short_name"),
                r.getString("dept_type"),
                r.getString("parent_id"),
                r.getString("manager_user_id")));
  }

  public Optional<DepartmentPO> findById(String id) {
    if (!Ids.valid(id)) return Optional.empty();
    return jdbc
        .query(
            "SELECT * FROM sys_dept WHERE id=?",
            (r, i) ->
                new DepartmentPO(
                    r.getString("id"),
                    r.getString("name"),
                    r.getString("short_name"),
                    r.getString("dept_type"),
                    r.getString("parent_id"),
                    r.getString("manager_user_id")),
            id)
        .stream()
        .findFirst();
  }

  /**
   * 以给定节点为起点反复扩展子节点，结果包含自身；无效/不存在节点返回空集合。
   * Set 使每个节点最多加入一次，避免此展开过程在坏环上无限迭代；不负责证明组织树本身无环。
   */
  public Set<String> subtree(String id) {
    Set<String> found = new LinkedHashSet<>();
    if (id == null) return found;
    List<DepartmentPO> ds = findAll();
    if (ds.stream().noneMatch(d -> d.id().equals(id))) return found;
    found.add(id);
    boolean changed;
    do {
      changed = false;
      for (var d : ds)
        if (d.parentId() != null && found.contains(d.parentId()) && found.add(d.id()))
          changed = true;
    } while (changed);
    return found;
  }
}
