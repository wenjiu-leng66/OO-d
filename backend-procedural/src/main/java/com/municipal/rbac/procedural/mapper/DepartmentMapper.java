package com.municipal.rbac.procedural.mapper;

import com.municipal.rbac.procedural.po.DepartmentPO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 部门表（departments）的数据访问。 */
@Repository
public class DepartmentMapper {

    private static final RowMapper<DepartmentPO> MAPPER = (rs, i) -> new DepartmentPO(
            rs.getString("id"),
            rs.getString("name"),
            rs.getString("branch"),
            rs.getString("parent_id"),
            rs.getString("manager_id"));

    private final JdbcTemplate jdbc;

    public DepartmentMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DepartmentPO> findAll() {
        return jdbc.query("SELECT id, name, branch, parent_id, manager_id FROM departments ORDER BY id", MAPPER);
    }

    public Optional<DepartmentPO> findById(String id) {
        return jdbc.query("SELECT id, name, branch, parent_id, manager_id FROM departments WHERE id = ?",
                        MAPPER, id)
                .stream().findFirst();
    }

    public long countEmployees(String departmentId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE department_id = ?", Long.class, departmentId);
        return n == null ? 0L : n;
    }
}
