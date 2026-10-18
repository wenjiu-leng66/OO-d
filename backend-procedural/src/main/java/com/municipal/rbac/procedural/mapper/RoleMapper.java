package com.municipal.rbac.procedural.mapper;

import com.municipal.rbac.procedural.po.RolePO;
import com.municipal.rbac.procedural.po.RolePermissionPO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 角色表（roles）与角色权限关联表（role_permissions）的数据访问。 */
@Repository
public class RoleMapper {

    private static final RowMapper<RolePO> MAPPER = (rs, i) -> new RolePO(
            rs.getString("id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getBoolean("enabled"),
            rs.getInt("permission_version"));

    private static final RowMapper<RolePermissionPO> GRANT_MAPPER = (rs, i) -> new RolePermissionPO(
            rs.getString("role_id"),
            rs.getString("permission_code"),
            rs.getString("scope"));

    private final JdbcTemplate jdbc;

    public RoleMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<RolePO> findById(String id) {
        return jdbc.query("SELECT id, code, name, description, enabled, permission_version "
                        + "FROM roles WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    public boolean exists(String id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM roles WHERE id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    public List<RolePO> findPage(String keyword, int offset, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, code, name, description, enabled, permission_version FROM roles WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append("AND (name LIKE ? OR code LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        sql.append("ORDER BY id LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    public long count(String keyword) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM roles WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append("AND (name LIKE ? OR code LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        Long count = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    public List<String> findPermissionCodes(String roleId) {
        return jdbc.queryForList(
                "SELECT permission_code FROM role_permissions WHERE role_id = ? ORDER BY permission_code",
                String.class, roleId);
    }

    /** 角色直接授权的权限集合（含范围）。 */
    public List<RolePermissionPO> findGrants(String roleId) {
        return jdbc.query(
                "SELECT role_id, permission_code, scope FROM role_permissions WHERE role_id = ? "
                        + "ORDER BY permission_code",
                GRANT_MAPPER, roleId);
    }

    public int permissionVersion(String roleId) {
        Integer v = jdbc.queryForObject("SELECT permission_version FROM roles WHERE id = ?", Integer.class, roleId);
        return v == null ? 0 : v;
    }

    /** 原子替换角色直接权限集合并递增版本（调用方需在事务中）。 */
    public void replacePermissions(String roleId, List<String> permissionCodes, int newVersion) {
        jdbc.update("DELETE FROM role_permissions WHERE role_id = ?", roleId);
        for (String code : permissionCodes) {
            jdbc.update("INSERT INTO role_permissions (role_id, permission_code, scope) VALUES (?, ?, ?)",
                    roleId, code, "all");
        }
        jdbc.update("UPDATE roles SET permission_version = ? WHERE id = ?", newVersion, roleId);
    }
}
