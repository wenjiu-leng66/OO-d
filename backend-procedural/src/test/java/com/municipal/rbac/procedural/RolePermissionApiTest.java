package com.municipal.rbac.procedural;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 角色与权限目录功能：角色列表、角色权限替换（乐观锁）、权限目录、资源目录、部门树。
 * 涉及写库的用例使用 @Transactional，测试结束后自动回滚。
 */
@Transactional
class RolePermissionApiTest extends ApiTestSupport {

    @Test
    void list_roles_includes_code_and_permission_codes() throws Exception {
        mockMvc.perform(get("/api/v1/roles").session(login("E001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.items", hasSize(6)))
                .andExpect(jsonPath("$.items[0].code").isNotEmpty())
                .andExpect(jsonPath("$.items[0].permissionCodes").isArray());
    }

    @Test
    void list_roles_supports_keyword_filter() throws Exception {
        mockMvc.perform(get("/api/v1/roles").param("keyword", "document").session(login("E001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void role_permissions_returns_codes_and_version() throws Exception {
        mockMvc.perform(get("/api/v1/roles/demo-r-reader/permissions").session(login("E001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleId").value("demo-r-reader"))
                .andExpect(jsonPath("$.permissionCodes", hasItem("oa:document:read")))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void unknown_role_permissions_returns_404() throws Exception {
        mockMvc.perform(get("/api/v1/roles/no-such-role/permissions").session(login("E001")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ROLE_NOT_FOUND"));
    }

    @Test
    void replace_permissions_succeeds_and_bumps_version() throws Exception {
        mockMvc.perform(put("/api/v1/roles/demo-r-reader/permissions")
                        .session(login("E001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\":[\"oa:document:read\",\"oa:document:create\"],\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissionCodes", hasSize(2)))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void replace_permissions_conflict_on_stale_version() throws Exception {
        mockMvc.perform(put("/api/v1/roles/demo-r-reader/permissions")
                        .session(login("E001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\":[],\"version\":99}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.error.details.currentVersion").value(1));
    }

    @Test
    void replace_permissions_rejects_unknown_code() throws Exception {
        mockMvc.perform(put("/api/v1/roles/demo-r-reader/permissions")
                        .session(login("E001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\":[\"no:such:code\"],\"version\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UNKNOWN_PERMISSION"));
    }

    @Test
    void replace_permissions_requires_grant_permission() throws Exception {
        mockMvc.perform(put("/api/v1/roles/demo-r-reader/permissions")
                        .session(login("E002"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\":[],\"version\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_permissions_filters_by_system() throws Exception {
        mockMvc.perform(get("/api/v1/permissions").session(login("E001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(14));

        mockMvc.perform(get("/api/v1/permissions").param("systemId", "demo-oa").session(login("E001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.items[0].scopeOptions").isArray());
    }

    @Test
    void list_permissions_denied_for_plain_employee() throws Exception {
        mockMvc.perform(get("/api/v1/permissions").session(login("E004")))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_resources_walks_tree() throws Exception {
        mockMvc.perform(get("/api/v1/resources").session(login("E001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));

        mockMvc.perform(get("/api/v1/resources").param("parentId", "demo-oa").session(login("E001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].type").value("module"))
                .andExpect(jsonPath("$.items[0].hasChildren").value(true));
    }

    @Test
    void unknown_parent_resource_returns_404() throws Exception {
        mockMvc.perform(get("/api/v1/resources").param("parentId", "no-such-resource").session(login("E001")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void departments_tree_returns_nested_nodes() throws Exception {
        mockMvc.perform(get("/api/v1/departments").session(login("E001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("demo-dept-hq"))
                .andExpect(jsonPath("$[0].manager").value("超级管理员"))
                .andExpect(jsonPath("$[0].employeeCount").value(2))
                .andExpect(jsonPath("$[0].children", hasSize(3)));
    }
}
