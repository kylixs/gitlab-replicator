package com.gitlab.mirror.server.api;

import com.gitlab.mirror.server.config.properties.GitLabMirrorProperties;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * API Endpoints Tests
 *
 * 测试要求：
 * - 测试所有端点
 * - 测试过滤条件
 * - 测试分页
 * - 测试认证保护
 * - 测试错误响应
 *
 * @author GitLab Mirror Team
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GitLabMirrorProperties properties;

    @Autowired
    private SyncProjectMapper syncProjectMapper;

    private String validToken;

    @BeforeEach
    void setUp() {
        validToken = properties.getSource().getToken();

        // 清理测试数据
        syncProjectMapper.delete(null);
    }

    // ==================== Status API Tests ====================

    /**
     * 测试获取服务状态
     */
    @Test
    void testGetStatus() throws Exception {
        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("running"))
                .andExpect(jsonPath("$.data.version").exists())
                .andExpect(jsonPath("$.data.timestamp").exists());
    }

    /**
     * 测试获取整体统计
     */
    @Test
    void testGetStats() throws Exception {
        mockMvc.perform(get("/api/stats")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.events").exists())
                .andExpect(jsonPath("$.data.events_by_type").exists())
                .andExpect(jsonPath("$.data.events_by_status").exists())
                .andExpect(jsonPath("$.data.avg_sync_delay_seconds").exists());
    }

    /**
     * 测试重新加载配置
     */
    @Test
    void testReloadConfiguration() throws Exception {
        mockMvc.perform(post("/api/reload")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(containsString("reloaded")));
    }

    // ==================== Project API Tests ====================

    /**
     * 测试获取项目列表
     */
    @Test
    void testGetProjects() throws Exception {
        // 创建测试项目
        createTestProject("test/project1", "active");
        createTestProject("test/project2", "pending");

        mockMvc.perform(get("/api/projects")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.total").exists())
                .andExpect(jsonPath("$.data.page").exists())
                .andExpect(jsonPath("$.data.pageSize").exists())
                .andExpect(jsonPath("$.data.totalPages").exists());
    }

    /**
     * 测试项目列表分页
     */
    @Test
    void testGetProjects_Pagination() throws Exception {
        // 创建多个测试项目
        for (int i = 1; i <= 25; i++) {
            createTestProject("test/project" + i, "active");
        }

        // 测试第一页
        mockMvc.perform(get("/api/projects")
                        .param("page", "1")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(10))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(25)));

        // 测试第二页
        mockMvc.perform(get("/api/projects")
                        .param("page", "2")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2));
    }

    /**
     * 测试项目列表过滤 - 按状态
     */
    @Test
    void testGetProjects_FilterByStatus() throws Exception {
        createTestProject("test/project1", "active");
        createTestProject("test/project2", "pending");
        createTestProject("test/project3", "active");

        mockMvc.perform(get("/api/projects")
                        .param("status", "active")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    /**
     * 测试获取项目详情
     */
    @Test
    void testGetProject() throws Exception {
        String projectKey = "test-project1";  // 使用不含斜杠的项目key
        createTestProject(projectKey, "active");

        mockMvc.perform(get("/api/projects/" + projectKey)
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.project").exists())
                .andExpect(jsonPath("$.data.project.projectKey").value(projectKey));
    }

    /**
     * 测试获取不存在的项目
     */
    @Test
    void testGetProject_NotFound() throws Exception {
        mockMvc.perform(get("/api/projects/non-existent-project")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    /**
     * 测试手动触发项目发现（需要认证）
     */
    @Test
    void testDiscoverProjects_RequiresAuth() throws Exception {
        // 无token应该失败
        mockMvc.perform(post("/api/projects/discover"))
                .andExpect(status().isUnauthorized());

        // 有效token应该成功
        mockMvc.perform(post("/api/projects/discover")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ==================== Mirror API Tests ====================

    /**
     * 测试获取Mirror列表
     */
    @Test
    void testGetMirrors() throws Exception {
        mockMvc.perform(get("/api/mirrors")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.total").exists());
    }

    /**
     * 测试Mirror列表分页
     */
    @Test
    void testGetMirrors_Pagination() throws Exception {
        mockMvc.perform(get("/api/mirrors")
                        .param("page", "1")
                        .param("size", "20")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20));
    }

    /**
     * 测试Mirror列表过滤 - 按状态
     */
    @Test
    void testGetMirrors_FilterByStatus() throws Exception {
        mockMvc.perform(get("/api/mirrors")
                        .param("status", "finished")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    /**
     * 测试手动触发Mirror轮询
     */
    @Test
    void testPollMirrors() throws Exception {
        mockMvc.perform(post("/api/mirrors/poll")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total_checked").exists())
                .andExpect(jsonPath("$.data.changed_count").exists());
    }

    // ==================== Event API Tests ====================

    /**
     * 测试获取事件列表
     */
    @Test
    void testGetEvents() throws Exception {
        mockMvc.perform(get("/api/events")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.total").exists());
    }

    /**
     * 测试事件列表分页
     */
    @Test
    void testGetEvents_Pagination() throws Exception {
        mockMvc.perform(get("/api/events")
                        .param("page", "1")
                        .param("size", "50")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(50));
    }

    /**
     * 测试事件列表多维度过滤
     */
    @Test
    void testGetEvents_MultiDimensionalFilter() throws Exception {
        mockMvc.perform(get("/api/events")
                        .param("eventType", "sync_finished")
                        .param("status", "success")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    // ==================== Authentication Tests ====================

    /**
     * 测试所有受保护端点都需要认证
     */
    @Test
    void testAllProtectedEndpoints_RequireAuth() throws Exception {
        // 所有非/status的端点都应该需要认证

        mockMvc.perform(get("/api/stats"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/reload"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/mirrors"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== Error Response Tests ====================

    /**
     * 测试无效的分页参数
     */
    @Test
    void testInvalidPaginationParameters() throws Exception {
        // 页码为0或负数时，MyBatis-Plus会自动调整，所以请求仍会成功
        mockMvc.perform(get("/api/projects")
                        .param("page", "0")
                        .param("size", "20")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk());
    }

    // ==================== Helper Methods ====================

    private void createTestProject(String projectKey, String status) {
        SyncProject project = new SyncProject();
        project.setProjectKey(projectKey);
        project.setSyncMethod(SyncProject.SyncMethod.PUSH_MIRROR);
        project.setSyncStatus(status);
        project.setEnabled(true);
        syncProjectMapper.insert(project);
    }
}
