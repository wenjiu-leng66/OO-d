package com.municipal.rbac.procedural.data;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.sql.Types;
import java.util.List;

/**
 * 启动时写入合成演示数据（DEMO，密码均为 123456）。
 * 仅当库为空时执行，便于本地演示 RBAC0 的登录、授权与角色分配。
 * 正式环境的数据由成员 1 的迁移与导入流程提供，不使用本类。
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final List<String> ALL_PERMISSIONS = List.of(
            "rbac:role:read", "rbac:role:grant", "rbac:permission:read",
            "rbac:user:read", "rbac:user:assign-role",
            "rbac:user:create", "rbac:user:update", "rbac:user:delete",
            "rbac:department:read",
            "oa:document:read", "oa:document:create", "oa:document:edit", "oa:document:approve",
            "hr:self:read");

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        seedDepartments();
        seedUsers();
        seedRoles();
        seedPermissions();
        seedResources();
        seedAssignments();
    }

    private void seedDepartments() {
        insertDepartment("demo-dept-hq", "总公司", "总公司", null, "demo-u-001");
        insertDepartment("demo-dept-hr", "人力资源部", "总公司", "demo-dept-hq", "demo-u-002");
        insertDepartment("demo-dept-oa", "办公室", "总公司", "demo-dept-hq", null);
        insertDepartment("demo-dept-branch", "分公司", "分公司", "demo-dept-hq", null);
        insertDepartment("demo-dept-branch-eng", "工程技术部", "分公司", "demo-dept-branch", null);
    }

    private void insertDepartment(String id, String name, String branch, String parentId, String managerId) {
        jdbc.update("INSERT INTO departments (id, name, branch, parent_id, manager_id) VALUES (?,?,?,?,?)",
                new Object[]{id, name, branch, parentId, managerId},
                new int[]{Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR});
    }

    private void seedUsers() {
        String hash = passwordEncoder.encode("123456");
        insertUser("demo-u-001", "超级管理员", "E001", "13800000001", "demo-dept-hq", "系统管理员", "employee", "active", hash);
        insertUser("demo-u-002", "部门管理员", "E002", "13800000002", "demo-dept-hr", "人事专员", "employee", "active", hash);
        insertUser("demo-u-003", "已停用账号", "E003", "13800000003", "demo-dept-oa", "会计", "employee", "inactive", hash);
        insertUser("demo-u-004", "普通员工", "E004", "13800000004", "demo-dept-oa", "文员", "employee", "active", hash);
        insertUser("demo-u-ops-001", "临时运维", "OPS001", "13800000005", "demo-dept-hq", "运维", "temporary-ops", "active", hash);
    }

    private void insertUser(String id, String name, String employeeNo, String phone, String departmentId,
                            String position, String accountType, String status, String passwordHash) {
        jdbc.update("INSERT INTO users (id, name, employee_no, phone, department_id, position, account_type, "
                        + "status, password_hash, role_version) VALUES (?,?,?,?,?,?,?,?,?,1)",
                id, name, employeeNo, phone, departmentId, position, accountType, status, passwordHash);
    }

    private void seedRoles() {
        insertRole("demo-r-super", "super-admin", "超级管理员", "系统预置唯一超级管理员");
        insertRole("demo-r-dept-admin", "dept-admin", "部门管理员", "管理部门内账号");
        insertRole("demo-r-reader", "document-reader", "公文查看员", "查看公文");
        insertRole("demo-r-editor", "document-editor", "公文编辑员", "查看/新建/编辑公文");
        insertRole("demo-r-approver", "document-approver", "公文审批员", "查看/审批公文");
        insertRole("demo-r-hr-self", "hr-self", "本人信息查看", "查看本人HR信息");
    }

    private void insertRole(String id, String code, String name, String description) {
        jdbc.update("INSERT INTO roles (id, code, name, description, enabled, permission_version) VALUES (?,?,?,?,?,1)",
                new Object[]{id, code, name, description, true},
                new int[]{Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.BOOLEAN});
    }

    private void seedPermissions() {
        insertPermission("rbac:role:read", "查看角色与授权情况", null, null, null, null, null, null, null, "all");
        insertPermission("rbac:role:grant", "修改角色权限", null, null, null, null, null, null, null, "all");
        insertPermission("rbac:permission:read", "查看资源权限目录", null, null, null, null, null, null, null, "all");
        insertPermission("rbac:user:read", "查看授权目标用户", null, null, null, null, null, null, null, "all");
        insertPermission("rbac:user:assign-role", "替换用户角色集合", null, null, null, null, null, null, null, "all");
        insertPermission("rbac:user:create", "创建用户", null, null, null, null, null, null, null, "all");
        insertPermission("rbac:user:update", "修改用户状态", null, null, null, null, null, null, null, "all");
        insertPermission("rbac:user:delete", "删除用户", null, null, null, null, null, null, null, "all");
        insertPermission("rbac:department:read", "查看部门架构", null, null, null, null, null, null, null, "all");
        insertPermission("oa:document:read", "查看公文", "demo-oa", "OA办公系统", "demo-document", "公文管理",
                "oa:document", "公文", "read", "all,self");
        insertPermission("oa:document:create", "创建公文", "demo-oa", "OA办公系统", "demo-document", "公文管理",
                "oa:document", "公文", "create", "all");
        insertPermission("oa:document:edit", "编辑公文", "demo-oa", "OA办公系统", "demo-document", "公文管理",
                "oa:document", "公文", "edit", "all");
        insertPermission("oa:document:approve", "审批公文", "demo-oa", "OA办公系统", "demo-document", "公文管理",
                "oa:document", "公文", "approve", "all");
        insertPermission("hr:self:read", "查看本人HR信息", "demo-hr", "HR人力资源", "demo-personnel", "人员管理",
                "hr:self", "本人信息", "read", "self");
    }

    private void insertPermission(String code, String name, String systemId, String systemName, String moduleId,
                                  String moduleName, String resourceCode, String resourceName, String action,
                                  String scopeOptions) {
        jdbc.update("INSERT INTO permissions (code, name, system_id, system_name, module_id, module_name, "
                        + "resource_code, resource_name, action, scope_options) VALUES (?,?,?,?,?,?,?,?,?,?)",
                new Object[]{code, name, systemId, systemName, moduleId, moduleName, resourceCode, resourceName, action, scopeOptions},
                new int[]{Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                        Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR});
    }

    private void seedResources() {
        insertResource("demo-oa", "OA办公系统", "system", null);
        insertResource("demo-hr", "HR人力资源", "system", null);
        insertResource("demo-document", "公文管理", "module", "demo-oa");
        insertResource("demo-document-func", "公文", "function", "demo-document");
        insertResource("demo-personnel", "人员管理", "module", "demo-hr");
        insertResource("demo-self", "本人信息", "function", "demo-personnel");
    }

    private void insertResource(String id, String name, String type, String parentId) {
        jdbc.update("INSERT INTO resources (id, name, type, parent_id) VALUES (?,?,?,?)",
                new Object[]{id, name, type, parentId},
                new int[]{Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR});
    }

    private void seedAssignments() {
        for (String code : ALL_PERMISSIONS) {
            grant("demo-r-super", code, "all");
        }
        grant("demo-r-dept-admin", "rbac:department:read", "all");
        grant("demo-r-dept-admin", "rbac:user:read", "all");
        grant("demo-r-dept-admin", "oa:document:read", "all");
        grant("demo-r-reader", "oa:document:read", "all");
        grant("demo-r-editor", "oa:document:read", "all");
        grant("demo-r-editor", "oa:document:create", "all");
        grant("demo-r-editor", "oa:document:edit", "all");
        grant("demo-r-approver", "oa:document:read", "all");
        grant("demo-r-approver", "oa:document:approve", "all");
        grant("demo-r-hr-self", "hr:self:read", "self");

        assignRole("demo-u-001", "demo-r-super");
        assignRole("demo-u-002", "demo-r-dept-admin");
        assignRole("demo-u-004", "demo-r-reader");
        assignRole("demo-u-004", "demo-r-hr-self");
    }

    private void grant(String roleId, String permissionCode, String scope) {
        jdbc.update("INSERT INTO role_permissions (role_id, permission_code, scope) VALUES (?,?,?)",
                roleId, permissionCode, scope);
    }

    private void assignRole(String userId, String roleId) {
        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?,?)", userId, roleId);
    }
}
