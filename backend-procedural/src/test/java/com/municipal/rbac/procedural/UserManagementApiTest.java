package com.municipal.rbac.procedural;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户管理功能：分页查询、详情、角色替换、创建、状态变更、删除。
 * 涉及写库的用例使用 @Transactional，测试结束后自动回滚，避免相互污染。
 */
@Transactional
class UserManagementApiTest extends ApiTestSupport {

    private static final String CREATE_BODY = """
            {"name":"测试员工","employeeNo":"T9001","departmentId":"demo-dept-oa",
             "accountType":"employee","initialPassword":"abc12345",
             "phone":"13900009001","position":"测试岗","roleIds":["demo-r-reader"]}
            """;

    @Test
    void list_users_returns_seeded_page() throws Exception {
        mockMvc.perform(get("/api/v1/users").session(login("E001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.items", hasSize(5)))
                .andExpect(jsonPath("$.items[0].departmentName").isNotEmpty());
    }

    @Test
    void list_users_supports_filters() throws Exception {
        mockMvc.perform(get("/api/v1/users").param("keyword", "人事").session(login("E001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].employeeNo").value("E002"));

        mockMvc.perform(get("/api/v1/users").param("departmentId", "demo-dept-oa").session(login("E001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));

        mockMvc.perform(get("/api/v1/users").param("accountType", "temporary-ops").session(login("E001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].employeeNo").value("OPS001"));
    }

    @Test
    void detail_returns_roles_and_effective_permissions() throws Exception {
        mockMvc.perform(get("/api/v1/users/demo-u-004").session(login("E001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeNo").value("E004"))
                .andExpect(jsonPath("$.roles", hasItem("公文查看员")))
                .andExpect(jsonPath("$.roles", hasItem("本人信息查看")))
                .andExpect(jsonPath("$.permissionCodes", hasItem("hr:self:read")));
    }

    @Test
    void user_roles_endpoint_returns_assigned_roles_and_version() throws Exception {
        mockMvc.perform(get("/api/v1/users/demo-u-004/roles").session(login("E001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("demo-u-004"))
                .andExpect(jsonPath("$.roleIds", hasItem("demo-r-reader")))
                .andExpect(jsonPath("$.roleIds", hasItem("demo-r-hr-self")))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void replace_roles_bumps_version() throws Exception {
        mockMvc.perform(put("/api/v1/users/demo-u-004/roles")
                        .session(login("E001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[\"demo-r-editor\"],\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleIds", hasSize(1)))
                .andExpect(jsonPath("$.roleIds[0]").value("demo-r-editor"))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void replace_roles_conflict_on_stale_version() throws Exception {
        mockMvc.perform(put("/api/v1/users/demo-u-004/roles")
                        .session(login("E001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[],\"version\":99}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));
    }

    @Test
    void replace_roles_rejects_duplicate_and_unknown_roles() throws Exception {
        mockMvc.perform(put("/api/v1/users/demo-u-004/roles")
                        .session(login("E001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[\"demo-r-reader\",\"demo-r-reader\"],\"version\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_ITEM"));

        mockMvc.perform(put("/api/v1/users/demo-u-004/roles")
                        .session(login("E001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[\"no-such-role\"],\"version\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UNKNOWN_ROLE"));
    }

    @Test
    void replace_roles_requires_assign_permission() throws Exception {
        mockMvc.perform(put("/api/v1/users/demo-u-004/roles")
                        .session(login("E002"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[],\"version\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_user_returns_201_with_detail() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .session(login("E001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", startsWith("u-")))
                .andExpect(jsonPath("$.employeeNo").value("T9001"))
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.roles", hasItem("公文查看员")));
    }

    @Test
    void create_user_rejects_duplicate_employee_no() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .session(login("E001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY.replace("T9001", "E004").replace("13900009001", "13900009002")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_ITEM"));
    }

    @Test
    void create_user_rejects_unknown_department_and_role() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .session(login("E001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY.replace("demo-dept-oa", "no-such-dept")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UNKNOWN_DEPARTMENT"));

        mockMvc.perform(post("/api/v1/users")
                        .session(login("E001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY.replace("demo-r-reader", "no-such-role")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UNKNOWN_ROLE"));
    }

    @Test
    void create_user_rejects_super_admin_role() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .session(login("E001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY.replace("demo-r-reader", "demo-r-super")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PROTECTED_ROLE"));
    }

    @Test
    void create_user_requires_permission() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .session(login("E004"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_status_changes_status() throws Exception {
        mockMvc.perform(put("/api/v1/users/demo-u-004/status")
                        .session(login("E001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"inactive\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("demo-u-004"))
                .andExpect(jsonPath("$.status").value("inactive"));
    }

    @Test
    void update_status_rejects_invalid_value() throws Exception {
        mockMvc.perform(put("/api/v1/users/demo-u-004/status")
                        .session(login("E001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"frozen\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void update_status_protects_self_account() throws Exception {
        mockMvc.perform(put("/api/v1/users/demo-u-001/status")
                        .session(login("E001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"inactive\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PROTECTED_ACCOUNT"));
    }

    @Test
    void delete_user_removes_it_and_returns_204() throws Exception {
        String created = mockMvc.perform(post("/api/v1/users")
                        .session(login("E001"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        mockMvc.perform(delete("/api/v1/users/" + id).session(login("E001")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/" + id).session(login("E001")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    void delete_self_account_is_protected() throws Exception {
        mockMvc.perform(delete("/api/v1/users/demo-u-001").session(login("E001")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PROTECTED_ACCOUNT"));
    }

    @Test
    void delete_requires_permission() throws Exception {
        mockMvc.perform(delete("/api/v1/users/demo-u-004").session(login("E002")))
                .andExpect(status().isForbidden());
    }
}
