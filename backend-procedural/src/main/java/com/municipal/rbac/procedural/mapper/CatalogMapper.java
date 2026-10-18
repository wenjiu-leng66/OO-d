package com.municipal.rbac.procedural.mapper;

import com.municipal.rbac.procedural.po.PermissionPO;
import com.municipal.rbac.procedural.po.ResourcePO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/** 权限目录表（permissions）与资源目录表（resources）的数据访问。 */
@Repository
public class CatalogMapper {

    private static final RowMapper<PermissionPO> PERMISSION_MAPPER = (rs, i) -> new PermissionPO(
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("system_id"),
            rs.getString("system_name"),
            rs.getString("module_id"),
            rs.getString("module_name"),
            rs.getString("resource_code"),
            rs.getString("resource_name"),
            rs.getString("action"),
            rs.getString("scope_options"));

    private static final RowMapper<ResourcePO> RESOURCE_MAPPER = (rs, i) -> new ResourcePO(
            rs.getString("id"),
            rs.getString("name"),
            rs.getString("type"),
            rs.getString("parent_id"));

    private final JdbcTemplate jdbc;

    public CatalogMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean permissionExists(String code) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM permissions WHERE code = ?", Integer.class, code);
        return n != null && n > 0;
    }

    public List<PermissionPO> findPermissions(String systemId, String moduleId, String keyword,
                                              int offset, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT code, name, system_id, system_name, module_id, module_name, "
                        + "resource_code, resource_name, action, scope_options FROM permissions WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (systemId != null && !systemId.isBlank()) {
            sql.append("AND system_id = ? ");
            args.add(systemId.trim());
        }
        if (moduleId != null && !moduleId.isBlank()) {
            sql.append("AND module_id = ? ");
            args.add(moduleId.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append("AND (name LIKE ? OR resource_name LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        sql.append("ORDER BY code LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), PERMISSION_MAPPER, args.toArray());
    }

    public long countPermissions(String systemId, String moduleId, String keyword) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM permissions WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (systemId != null && !systemId.isBlank()) {
            sql.append("AND system_id = ? ");
            args.add(systemId.trim());
        }
        if (moduleId != null && !moduleId.isBlank()) {
            sql.append("AND module_id = ? ");
            args.add(moduleId.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append("AND (name LIKE ? OR resource_name LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        Long count = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    public boolean resourceExists(String id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM resources WHERE id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    /** 查询指定父节点下的资源（parentId 为空表示根节点）。 */
    public List<ResourcePO> findResources(String parentId, int offset, int limit) {
        String base = "SELECT id, name, type, parent_id FROM resources ";
        if (parentId == null || parentId.isBlank()) {
            return jdbc.query(base + "WHERE parent_id IS NULL ORDER BY id LIMIT ? OFFSET ?",
                    RESOURCE_MAPPER, limit, offset);
        }
        return jdbc.query(base + "WHERE parent_id = ? ORDER BY id LIMIT ? OFFSET ?",
                RESOURCE_MAPPER, parentId, limit, offset);
    }

    public long countResources(String parentId) {
        if (parentId == null || parentId.isBlank()) {
            Long n = jdbc.queryForObject("SELECT COUNT(*) FROM resources WHERE parent_id IS NULL", Long.class);
            return n == null ? 0L : n;
        }
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM resources WHERE parent_id = ?", Long.class, parentId);
        return n == null ? 0L : n;
    }

    /** 资源是否存在子节点（用于组装 hasChildren 展示字段）。 */
    public boolean hasChildren(String resourceId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM resources WHERE parent_id = ?",
                Integer.class, resourceId);
        return n != null && n > 0;
    }
}
