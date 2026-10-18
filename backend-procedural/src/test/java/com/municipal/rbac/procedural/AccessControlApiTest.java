package com.municipal.rbac.procedural;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 访问控制（默认拒绝）：未登录 401、无权限 403、有效权限计算。 */
class AccessControlApiTest extends ApiTestSupport {

    @Test
    void unauthenticated_request_returns_401() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void plain_employee_cannot_read_users() throws Exception {
        mockMvc.perform(get("/api/v1/users").session(login("E004")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void department_admin_can_read_users() throws Exception {
        mockMvc.perform(get("/api/v1/users").session(login("E002")))
                .andExpect(status().isOk());
    }

    @Test
    void plain_employee_cannot_read_departments() throws Exception {
        mockMvc.perform(get("/api/v1/departments").session(login("E004")))
                .andExpect(status().isForbidden());
    }

    @Test
    void plain_employee_cannot_read_roles() throws Exception {
        mockMvc.perform(get("/api/v1/roles").session(login("E004")))
                .andExpect(status().isForbidden());
    }

    @Test
    void plain_employee_can_read_own_permissions() throws Exception {
        mockMvc.perform(get("/api/v1/me/permissions").session(login("E004")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("demo-u-004"))
                .andExpect(jsonPath("$.roleIds", hasItem("demo-r-reader")))
                .andExpect(jsonPath("$.roleIds", hasItem("demo-r-hr-self")))
                .andExpect(jsonPath("$.permissionCodes", hasItem("oa:document:read")))
                .andExpect(jsonPath("$.permissionCodes", hasItem("hr:self:read")))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void unknown_user_detail_returns_404() throws Exception {
        mockMvc.perform(get("/api/v1/users/no-such-user").session(login("E001")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }
}
