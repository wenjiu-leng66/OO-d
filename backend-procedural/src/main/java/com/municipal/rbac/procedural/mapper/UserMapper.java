package com.municipal.rbac.procedural.mapper;

import com.municipal.rbac.procedural.po.UserPO;
import com.municipal.rbac.procedural.po.UserRolePO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 用户表（users）与用户角色关联表（user_roles）的数据访问。只返回 PO，不组装展示字段。 */
@Repository
public class UserMapper {

    private static final RowMapper<UserPO> MAPPER = (rs, i) -> new UserPO(
            rs.getString("id"),
            rs.getString("name"),
            rs.getString("employee_no"),
            rs.getString("phone"),
            rs.getString("department_id"),
            rs.getString("position"),
            rs.getString("account_type"),
            rs.getString("status"),
            rs.getString("password_hash"),
            rs.getInt("role_version"));

    private static final String SELECT_USER =
            "SELECT id, name, employee_no, phone, department_id, position, account_type, status, "
                    + "password_hash, role_version FROM users ";

    private final JdbcTemplate jdbc;

    public UserMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserPO> findByAccount(String account) {
        return jdbc.query(SELECT_USER + "WHERE employee_no = ? OR phone = ?", MAPPER, account, account)
                .stream().findFirst();
    }

    public Optional<UserPO> findById(String id) {
        return jdbc.query(SELECT_USER + "WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    public Optional<UserPO> findByEmployeeNo(String employeeNo) {
        return jdbc.query(SELECT_USER + "WHERE employee_no = ?", MAPPER, employeeNo)
                .stream().findFirst();
    }

    public Optional<UserPO> findByPhone(String phone) {
        return jdbc.query(SELECT_USER + "WHERE phone = ?", MAPPER, phone)
                .stream().findFirst();
    }

    public List<UserPO> findPage(String keyword, String departmentId, String accountType, int offset, int limit) {
        StringBuilder sql = new StringBuilder(SELECT_USER).append("WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append("AND (name LIKE ? OR employee_no LIKE ? OR position LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (departmentId != null && !departmentId.isBlank()) {
            sql.append("AND department_id = ? ");
            args.add(departmentId.trim());
        }
        if (accountType != null && !accountType.isBlank()) {
            sql.append("AND account_type = ? ");
            args.add(accountType.trim());
        }
        sql.append("ORDER BY id LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    public long count(String keyword, String departmentId, String accountType) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM users WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append("AND (name LIKE ? OR employee_no LIKE ? OR position LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (departmentId != null && !departmentId.isBlank()) {
            sql.append("AND department_id = ? ");
            args.add(departmentId.trim());
        }
        if (accountType != null && !accountType.isBlank()) {
            sql.append("AND account_type = ? ");
            args.add(accountType.trim());
        }
        Long count = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    public void insert(UserPO user) {
        jdbc.update("INSERT INTO users (id, name, employee_no, phone, department_id, position, account_type, "
                        + "status, password_hash, role_version) VALUES (?,?,?,?,?,?,?,?,?,?)",
                user.id(), user.name(), user.employeeNo(), user.phone(), user.departmentId(),
                user.position(), user.accountType(), user.status(), user.passwordHash(), user.roleVersion());
    }

    public int updateStatus(String id, String status) {
        return jdbc.update("UPDATE users SET status = ? WHERE id = ?", status, id);
    }

    public int deleteById(String id) {
        return jdbc.update("DELETE FROM users WHERE id = ?", id);
    }

    public void deleteUserRoles(String userId) {
        jdbc.update("DELETE FROM user_roles WHERE user_id = ?", userId);
    }

    public void insertUserRole(String userId, String roleId) {
        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);
    }

    /** 用户已分配的全部角色行（不展开角色信息、不过滤启用状态）。 */
    public List<UserRolePO> findUserRoleRows(String userId) {
        return jdbc.query("SELECT user_id, role_id FROM user_roles WHERE user_id = ? ORDER BY role_id",
                (rs, i) -> new UserRolePO(rs.getString("user_id"), rs.getString("role_id")), userId);
    }

    /** 已分配且启用的角色 ID（用于有效权限计算）。 */
    public List<String> findEnabledRoleIds(String userId) {
        return jdbc.queryForList(
                "SELECT r.id FROM user_roles ur JOIN roles r ON ur.role_id = r.id "
                        + "WHERE ur.user_id = ? AND r.enabled = TRUE ORDER BY r.id",
                String.class, userId);
    }

    public int roleVersion(String userId) {
        Integer v = jdbc.queryForObject("SELECT role_version FROM users WHERE id = ?", Integer.class, userId);
        return v == null ? 0 : v;
    }

    /** 原子替换用户角色集合并递增版本（调用方需在事务中）。 */
    public void replaceRoles(String userId, List<String> roleIds, int newVersion) {
        deleteUserRoles(userId);
        for (String roleId : roleIds) {
            insertUserRole(userId, roleId);
        }
        jdbc.update("UPDATE users SET role_version = ? WHERE id = ?", newVersion, userId);
    }

    /** 判断用户是否拥有指定角色码（用于保护超级管理员账号）。 */
    public boolean hasRoleCode(String userId, String roleCode) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_roles ur JOIN roles r ON ur.role_id = r.id "
                        + "WHERE ur.user_id = ? AND r.code = ?",
                Integer.class, userId, roleCode);
        return n != null && n > 0;
    }
}
