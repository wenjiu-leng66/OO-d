package com.municipal.rbac.procedural;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 认证功能：登录成功/失败、会话登记、当前身份与退出。 */
class AuthApiTest extends ApiTestSupport {

    @Test
    void login_with_employee_no_returns_identity_and_permissions() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"E001\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value("demo-u-001"))
                .andExpect(jsonPath("$.user.name").value("超级管理员"))
                .andExpect(jsonPath("$.user.employeeNo").value("E001"))
                .andExpect(jsonPath("$.user.departmentName").value("总公司"))
                .andExpect(jsonPath("$.permissionCodes", hasItem("rbac:user:create")))
                .andExpect(jsonPath("$.permissionCodes", hasItem("rbac:user:update")))
                .andExpect(jsonPath("$.permissionCodes", hasItem("rbac:user:delete")));
    }

    @Test
    void login_with_phone_works() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"13800000001\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value("demo-u-001"));
    }

    @Test
    void login_with_wrong_password_returns_401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"E001\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.error.requestId").isNotEmpty());
    }

    @Test
    void login_with_disabled_account_returns_403() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"E003\",\"password\":\"123456\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void login_with_blank_account_returns_400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"  \",\"password\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void me_returns_current_identity() throws Exception {
        mockMvc.perform(get("/api/v1/me").session(login("E002")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("demo-u-002"))
                .andExpect(jsonPath("$.departmentName").value("人力资源部"))
                .andExpect(jsonPath("$.accountType").value("employee"));
    }

    @Test
    void me_without_session_returns_401() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void logout_succeeds() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").session(login("E001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
