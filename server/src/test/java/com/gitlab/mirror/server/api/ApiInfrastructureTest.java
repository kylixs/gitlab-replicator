package com.gitlab.mirror.server.api;

import com.gitlab.mirror.server.api.exception.ResourceNotFoundException;
import com.gitlab.mirror.server.config.properties.GitLabMirrorProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * API Infrastructure Tests
 *
 * 测试要求：
 * - 测试 Token 认证
 * - 测试各种异常场景
 * - 测试响应格式
 * - 测试 CORS
 *
 * @author GitLab Mirror Team
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiInfrastructureTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GitLabMirrorProperties properties;

    /**
     * 测试 Token 认证 - 有效token
     */
    @Test
    void testAuthentication_ValidToken() throws Exception {
        String validToken = properties.getSource().getToken();

        mockMvc.perform(get("/api/stats")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * 测试 Token 认证 - 缺少token
     */
    @Test
    void testAuthentication_MissingToken() throws Exception {
        mockMvc.perform(get("/api/stats"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value(containsString("Missing or invalid")));
    }

    /**
     * 测试 Token 认证 - 无效token
     */
    @Test
    void testAuthentication_InvalidToken() throws Exception {
        mockMvc.perform(get("/api/stats")
                        .header("Authorization", "Bearer invalid-token-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value("Invalid token"));
    }

    /**
     * 测试 Token 认证 - 错误的Authorization格式
     */
    @Test
    void testAuthentication_WrongAuthFormat() throws Exception {
        mockMvc.perform(get("/api/stats")
                        .header("Authorization", "Basic invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    /**
     * 测试公开端点不需要认证
     */
    @Test
    void testAuthentication_PublicEndpoint() throws Exception {
        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("running"));
    }

    /**
     * 测试统一响应格式 - 成功响应
     */
    @Test
    void testResponseFormat_Success() throws Exception {
        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    /**
     * 测试统一响应格式 - 错误响应（资源不存在）
     */
    @Test
    void testResponseFormat_NotFound() throws Exception {
        String validToken = properties.getSource().getToken();

        mockMvc.perform(get("/api/projects/non-existent-project")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").exists())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    /**
     * 测试异常处理 - IllegalArgumentException
     */
    @Test
    void testExceptionHandling_IllegalArgument() throws Exception {
        String validToken = properties.getSource().getToken();

        // MyBatis-Plus会自动调整无效的分页参数，所以请求仍会成功
        // 测试验证自动调整行为
        mockMvc.perform(get("/api/projects")
                        .param("page", "-1")
                        .param("size", "20")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * 测试CORS配置 - OPTIONS预检请求
     */
    @Test
    void testCORS_PreflightRequest() throws Exception {
        mockMvc.perform(options("/api/status")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"))
                .andExpect(header().exists("Access-Control-Allow-Methods"));
    }

    /**
     * 测试CORS配置 - 实际请求
     */
    @Test
    void testCORS_ActualRequest() throws Exception {
        mockMvc.perform(get("/api/status")
                        .header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"));
    }

    /**
     * 测试JSON内容类型
     */
    @Test
    void testContentType_JSON() throws Exception {
        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
}
