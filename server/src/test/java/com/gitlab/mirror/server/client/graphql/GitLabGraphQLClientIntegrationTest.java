package com.gitlab.mirror.server.client.graphql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitlab.mirror.server.client.RetryableGitLabClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GitLabGraphQLClient 集成测试
 * <p>
 * 连接真实GitLab实例进行测试
 * 需要本地GitLab运行在 http://localhost:8000
 */
@Slf4j
@Disabled("Integration test - requires running GitLab instance")
class GitLabGraphQLClientIntegrationTest {

    private static final String GITLAB_URL = "http://localhost:8000";
    private static final String GITLAB_TOKEN = "glpat-QfaqawuLrzcPfJ3oEgiStG86MQp1OjEH.01.0w01363lq";

    // 测试项目IDs（需要在GitLab中存在）
    private static final Long PROJECT_1_ID = 1L;   // devops/gitlab-mirror
    private static final Long PROJECT_16_ID = 16L; // arch/test-spring-app1
    private static final Long PROJECT_17_ID = 17L; // ai/test-node-app2

    private GitLabGraphQLClient graphQLClient;

    @BeforeEach
    void setUp() {
        // 创建RestTemplate
        RestTemplate restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();

        // 创建RetryableGitLabClient
        RetryableGitLabClient retryableClient = new RetryableGitLabClient(
                restTemplate, GITLAB_URL, GITLAB_TOKEN, 3, 1000L);

        // 创建ObjectMapper
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // 注册Java 8时间模块

        // 创建GraphQLClient
        graphQLClient = new GitLabGraphQLClient(retryableClient, objectMapper);
    }

    @Test
    void testBatchQueryProjects_SingleProject() {
        // Given
        List<Long> projectIds = Arrays.asList(PROJECT_1_ID);

        // When
        List<GraphQLProjectInfo> results = graphQLClient.batchQueryProjects(projectIds);

        // Then
        assertNotNull(results);
        assertEquals(1, results.size());

        GraphQLProjectInfo project = results.get(0);
        assertNotNull(project);

        // 验证基础信息
        assertEquals(PROJECT_1_ID, project.getProjectId());
        assertNotNull(project.getFullPath());
        assertTrue(project.getFullPath().contains("gitlab-mirror"));

        // 验证时间字段
        assertNotNull(project.getCreatedAt());
        assertNotNull(project.getLastActivityAt());

        // 验证Repository信息
        assertNotNull(project.getRepository());
        assertNotNull(project.getRepository().getRootRef());
        assertTrue(
                project.getRepository().getRootRef().equals("main") ||
                        project.getRepository().getRootRef().equals("master")
        );

        // 验证Commit信息
        assertNotNull(project.getRepository().getTree());
        assertNotNull(project.getRepository().getTree().getLastCommit());
        String lastCommitSha = project.getLastCommitSha();
        assertNotNull(lastCommitSha);
        assertEquals(40, lastCommitSha.length(), "SHA should be 40 characters");

        OffsetDateTime lastCommitDate = project.getLastCommitDate();
        assertNotNull(lastCommitDate);

        // 验证统计信息
        assertNotNull(project.getStatistics());
        Integer commitCount = project.getCommitCount();
        assertNotNull(commitCount);
        assertTrue(commitCount > 0, "Commit count should be greater than 0");

        Long repositorySize = project.getRepositorySize();
        assertNotNull(repositorySize);
        assertTrue(repositorySize > 0, "Repository size should be greater than 0");

        // 日志输出
        log.info("Project: {}", project.getFullPath());
        log.info("  Root Branch: {}", project.getRepository().getRootRef());
        log.info("  Last Commit SHA: {}", lastCommitSha);
        log.info("  Last Commit Date: {}", lastCommitDate);
        log.info("  Commit Count: {}", commitCount);
        log.info("  Repository Size: {} bytes ({} KB)",
                repositorySize, repositorySize / 1024);
    }

    @Test
    void testBatchQueryProjects_MultipleProjects() {
        // Given
        List<Long> projectIds = Arrays.asList(PROJECT_1_ID, PROJECT_16_ID, PROJECT_17_ID);

        // When
        long startTime = System.currentTimeMillis();
        List<GraphQLProjectInfo> results = graphQLClient.batchQueryProjects(projectIds);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertNotNull(results);
        assertEquals(3, results.size());

        log.info("Batch query 3 projects took {}ms", duration);
        assertTrue(duration < 300, "Batch query should be fast (<300ms)");

        // 验证每个项目
        for (GraphQLProjectInfo project : results) {
            assertNotNull(project.getProjectId());
            assertNotNull(project.getFullPath());
            assertNotNull(project.getCreatedAt());
            assertNotNull(project.getLastActivityAt());
            assertNotNull(project.getRepository());
            assertNotNull(project.getRepository().getRootRef());

            // 验证lastCommit可能为null（空项目）或者有值
            if (project.getRepository().getTree() != null
                    && project.getRepository().getTree().getLastCommit() != null) {
                String sha = project.getLastCommitSha();
                assertNotNull(sha);
                assertEquals(40, sha.length());
            }

            // 验证统计信息
            if (project.getStatistics() != null) {
                log.info("Project {}: commits={}, size={} KB",
                        project.getFullPath(),
                        project.getCommitCount(),
                        project.getRepositorySize() != null ? project.getRepositorySize() / 1024 : 0);
            }
        }
    }

    @Test
    void testBatchQueryProjectsInChunks() {
        // Given - 创建一个较大的项目ID列表
        List<Long> projectIds = Arrays.asList(
                PROJECT_1_ID, PROJECT_16_ID, PROJECT_17_ID,
                PROJECT_1_ID, PROJECT_16_ID  // 重复ID用于测试
        );
        int batchSize = 2; // 小批次用于测试分批逻辑

        // When
        long startTime = System.currentTimeMillis();
        List<GraphQLProjectInfo> results = graphQLClient.batchQueryProjectsInChunks(
                projectIds, batchSize);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertNotNull(results);
        assertEquals(5, results.size()); // 应该返回所有查询的项目（包括重复）

        log.info("Chunked query {} projects (batch size={}) took {}ms",
                projectIds.size(), batchSize, duration);

        // 验证批次数 = ceil(5 / 2) = 3 batches
        int expectedBatches = (int) Math.ceil((double) projectIds.size() / batchSize);
        log.info("Expected {} batches", expectedBatches);
    }

    @Test
    void testProjectIdExtraction() {
        // Given
        List<Long> projectIds = Arrays.asList(PROJECT_1_ID);
        List<GraphQLProjectInfo> results = graphQLClient.batchQueryProjects(projectIds);

        // When
        GraphQLProjectInfo project = results.get(0);
        Long extractedId = project.getProjectId();

        // Then
        assertEquals(PROJECT_1_ID, extractedId);
        assertTrue(project.getId().contains("gid://gitlab/Project/"));
        assertTrue(project.getId().endsWith("/" + PROJECT_1_ID));
    }

    @Test
    void testActivityTimeComparison() {
        // Given
        List<Long> projectIds = Arrays.asList(PROJECT_1_ID);
        List<GraphQLProjectInfo> results = graphQLClient.batchQueryProjects(projectIds);
        GraphQLProjectInfo project = results.get(0);

        // When
        OffsetDateTime lastActivityAt = project.getLastActivityAt();
        OffsetDateTime lastCommitDate = project.getLastCommitDate();

        // Then
        assertNotNull(lastActivityAt);
        assertNotNull(lastCommitDate);

        // 最后活动时间应该 >= 最后commit时间（或者非常接近）
        Duration diff = Duration.between(lastCommitDate, lastActivityAt);
        log.info("Activity vs Commit time diff: {} seconds", diff.getSeconds());

        assertTrue(diff.getSeconds() >= -60,
                "Last activity should be after or close to last commit");
    }

    @Test
    void testEmptyProjectList() {
        // Given
        List<Long> projectIds = Arrays.asList();

        // When
        List<GraphQLProjectInfo> results = graphQLClient.batchQueryProjects(projectIds);

        // Then
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testNullProjectList() {
        // Given
        List<Long> projectIds = null;

        // When
        List<GraphQLProjectInfo> results = graphQLClient.batchQueryProjects(projectIds);

        // Then
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testNonExistentProject() {
        // Given - 使用一个不存在的项目ID
        List<Long> projectIds = Arrays.asList(99999L);

        // When
        List<GraphQLProjectInfo> results = graphQLClient.batchQueryProjects(projectIds);

        // Then
        assertNotNull(results);
        // GraphQL对于不存在的项目会返回空节点，不会报错
        assertTrue(results.isEmpty() || results.size() < projectIds.size());
    }

    @Test
    void testPerformanceBenchmark() {
        // Given
        List<Long> projectIds = Arrays.asList(PROJECT_1_ID, PROJECT_16_ID, PROJECT_17_ID);
        int iterations = 5;

        // When
        long totalDuration = 0;
        for (int i = 0; i < iterations; i++) {
            long startTime = System.currentTimeMillis();
            List<GraphQLProjectInfo> results = graphQLClient.batchQueryProjects(projectIds);
            long duration = System.currentTimeMillis() - startTime;

            totalDuration += duration;
            assertNotNull(results);
            assertEquals(3, results.size());

            log.info("Iteration {}: {}ms", i + 1, duration);
        }

        // Then
        long avgDuration = totalDuration / iterations;
        log.info("Average duration for 3 projects: {}ms", avgDuration);

        // 性能断言：3个项目平均应该 < 200ms
        assertTrue(avgDuration < 200,
                "Average query time should be less than 200ms, but was " + avgDuration + "ms");
    }
}
