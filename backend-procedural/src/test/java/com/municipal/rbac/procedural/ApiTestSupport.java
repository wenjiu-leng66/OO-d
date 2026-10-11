package com.municipal.rbac.procedural;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 集成测试基类：启动完整应用上下文（含 H2 演示数据）并提供登录助手。
 * 演示账号密码统一为 123456（E001/E002/E004/OPS001，E003 为已停用账号）。
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class ApiTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    /** 登录演示账号（如 E001），断言登录成功并返回会话。 */
    protected MockHttpSession login(String account) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"" + account + "\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
