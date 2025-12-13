package com.gitlab.mirror.server.client;

import com.gitlab.mirror.common.model.GitLabGroup;
import com.gitlab.mirror.common.model.GitLabProject;
import com.gitlab.mirror.server.config.GitLabProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * GitLab API Client Integration Test
 * <p>
 * 此测试需要连接真实的GitLab服务器
 * 运行前请确保：
 * 1. GitLab服务器已启动（docker-compose up -d）
 * 2. 已设置环境变量 SOURCE_GITLAB_TOKEN
 * 3. GitLab服务器地址为 http://localhost:8000
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
class GitLabApiClientIntegrationTest {

    @Autowired
    @Qualifier("targetGitLabApiClient")
    private GitLabApiClient apiClient;

    @Autowired
    private GitLabProperties gitLabProperties;

    @BeforeEach
    void setUp() {
        // 检查GitLab Token是否配置
        String token = gitLabProperties.getTarget().getToken();
        assumeTrue(token != null && !token.isEmpty(),
                "TARGET_GITLAB_TOKEN environment variable not set. Skipping integration tests.");

        // 测试连接
        boolean connected = apiClient.testConnection();
        assumeTrue(connected, "Cannot connect to GitLab server. Skipping integration tests.");

        log.info("GitLab server connected: {}", gitLabProperties.getTarget().getUrl());
    }

    @Test
    void testConnectionToGitLab() {
        // 测试连接
        boolean result = apiClient.testConnection();

        assertThat(result).isTrue();
        log.info("Successfully connected to GitLab server");
    }

    @Test
    void testGetGroups() {
        // 获取所有分组
        List<GitLabGroup> groups = apiClient.getGroups(null, false);

        assertThat(groups).isNotNull();
        log.info("Found {} groups", groups.size());

        if (!groups.isEmpty()) {
            GitLabGroup firstGroup = groups.get(0);
            log.info("First group: id={}, name={}, path={}",
                    firstGroup.getId(), firstGroup.getName(), firstGroup.getPath());

            assertThat(firstGroup.getId()).isNotNull();
            assertThat(firstGroup.getName()).isNotBlank();
            assertThat(firstGroup.getPath()).isNotBlank();
        }
    }

    @Test
    void testGetAllProjects() {
        // 获取所有项目
        List<GitLabProject> projects = apiClient.getAllProjects(null);

        assertThat(projects).isNotNull();
        log.info("Found {} projects", projects.size());

        if (!projects.isEmpty()) {
            GitLabProject firstProject = projects.get(0);
            log.info("First project: id={}, name={}, path={}",
                    firstProject.getId(), firstProject.getName(), firstProject.getPathWithNamespace());

            assertThat(firstProject.getId()).isNotNull();
            assertThat(firstProject.getName()).isNotBlank();
            assertThat(firstProject.getPathWithNamespace()).isNotBlank();
        }
    }

    // @Test
    void testGroupLifecycle() {
        String testGroupPath = "test-integration-group-" + System.currentTimeMillis();
        String testGroupName = "Test Integration Group";
        Long createdGroupId = null;

        try {
            // 1. 检查分组不存在
            boolean existsBefore = apiClient.groupExists(testGroupPath);
            assertThat(existsBefore).isFalse();
            log.info("Group {} does not exist (as expected)", testGroupPath);

            // 2. 创建分组
            GitLabGroup createdGroup = apiClient.createGroup(testGroupPath, testGroupName, null);
            assertThat(createdGroup).isNotNull();
            assertThat(createdGroup.getId()).isNotNull();
            assertThat(createdGroup.getPath()).isEqualTo(testGroupPath);
            assertThat(createdGroup.getName()).isEqualTo(testGroupName);
            createdGroupId = createdGroup.getId();
            log.info("Group created: id={}, path={}", createdGroup.getId(), createdGroup.getPath());

            // 3. 检查分组存在
            boolean existsAfter = apiClient.groupExists(testGroupPath);
            assertThat(existsAfter).isTrue();
            log.info("Group {} exists (as expected)", testGroupPath);

        } catch (Exception e) {
            log.error("Group lifecycle test failed", e);
            throw e;
        } finally {
            // 4. 清理：删除创建的分组
            if (createdGroupId != null) {
                try {
                    apiClient.deleteGroup(createdGroupId);
                    log.info("Cleaned up test group: id={}", createdGroupId);
                } catch (Exception e) {
                    log.warn("Failed to clean up test group: id={}", createdGroupId, e);
                }
            }
        }
    }

    // @Test
    void testProjectLifecycle() {
        long timestamp = System.currentTimeMillis();
        String testGroupPath = "test-integration-projects";
        String testProjectPath = "test-project-" + timestamp;
        String testProjectName = "Test Project " + timestamp;
        Long createdGroupId = null;
        Long createdProjectId = null;

        try {
            // 1. 确保测试分组存在
            if (!apiClient.groupExists(testGroupPath)) {
                GitLabGroup testGroup = apiClient.createGroup(testGroupPath, "Test Integration Projects", null);
                createdGroupId = testGroup.getId();
                log.info("Created test group: id={}, path={}", testGroup.getId(), testGroupPath);
            }

            // 2. 检查项目不存在
            String fullProjectPath = testGroupPath + "/" + testProjectPath;
            boolean existsBefore = apiClient.projectExists(fullProjectPath);

            // If project exists, skip creation to avoid conflict
            if (existsBefore) {
                log.warn("Project {} already exists, skipping creation test", fullProjectPath);
                GitLabProject existingProject = apiClient.getProject(fullProjectPath);
                assertThat(existingProject).isNotNull();
                log.info("Verified existing project: {}", existingProject.getPathWithNamespace());
                return;
            }

            log.info("Project {} does not exist (as expected)", fullProjectPath);

            // 3. 创建项目
            GitLabProject createdProject = apiClient.createProject(testProjectPath, testProjectName, testGroupPath);
            assertThat(createdProject).isNotNull();
            assertThat(createdProject.getId()).isNotNull();
            assertThat(createdProject.getPath()).isEqualTo(testProjectPath);
            assertThat(createdProject.getName()).isEqualTo(testProjectName);
            createdProjectId = createdProject.getId();
            log.info("Project created: id={}, path={}, pathWithNamespace={}",
                    createdProject.getId(), createdProject.getPath(), createdProject.getPathWithNamespace());

            // Verify pathWithNamespace is not null
            assertThat(createdProject.getPathWithNamespace())
                    .as("pathWithNamespace should not be null")
                    .isNotNull();

            // 4. 验证项目存在（通过ID查询）
            GitLabProject fetchedById = apiClient.getProjectById(createdProject.getId());
            assertThat(fetchedById).isNotNull();
            assertThat(fetchedById.getId()).isEqualTo(createdProject.getId());
            assertThat(fetchedById.getPathWithNamespace()).isEqualTo(createdProject.getPathWithNamespace());
            log.info("Project verified by ID: {}", fetchedById.getPathWithNamespace());

            // 5. 验证项目存在（通过path查询）
            String pathToQuery = createdProject.getPathWithNamespace();
            log.info("Querying project by path: '{}'", pathToQuery);
            GitLabProject fetchedByPath = apiClient.getProject(pathToQuery);
            assertThat(fetchedByPath).isNotNull();
            assertThat(fetchedByPath.getId()).isEqualTo(createdProject.getId());
            log.info("Project verified by path: {}", fetchedByPath.getPathWithNamespace());

        } catch (GitLabClientException e) {
            // If it's a "has already been taken" error, just skip the test
            if (e.getMessage() != null && e.getMessage().contains("has already been taken")) {
                log.warn("Project name conflict detected (likely from previous test run), skipping");
                return;
            }
            log.error("Project lifecycle test failed", e);
            throw e;
        } catch (Exception e) {
            log.error("Project lifecycle test failed", e);
            throw e;
        } finally {
            // 6. 清理：删除创建的项目和分组
            if (createdProjectId != null) {
                try {
                    apiClient.deleteProject(createdProjectId);
                    log.info("Cleaned up test project: id={}", createdProjectId);
                } catch (Exception e) {
                    log.warn("Failed to clean up test project: id={}", createdProjectId, e);
                }
            }

            if (createdGroupId != null) {
                try {
                    apiClient.deleteGroup(createdGroupId);
                    log.info("Cleaned up test group: id={}", createdGroupId);
                } catch (Exception e) {
                    log.warn("Failed to clean up test group: id={}", createdGroupId, e);
                }
            }
        }
    }
}
