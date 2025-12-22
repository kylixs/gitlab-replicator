package com.gitlab.mirror.server.service.monitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitlab.mirror.server.client.RetryableGitLabClient;
import com.gitlab.mirror.server.client.graphql.GitLabGraphQLClient;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.service.monitor.model.ScanResult;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import org.mockito.Mockito;

/**
 * UnifiedProjectMonitor 集成测试
 * <p>
 * 连接真实GitLab实例进行完整扫描流程测试，验证GraphQL优化效果
 * 需要本地GitLab运行在 http://localhost:8000
 */
@Slf4j
@Disabled("Integration test - requires running GitLab instance and database")
class UnifiedProjectMonitorIntegrationTest {

    private static final String SOURCE_GITLAB_URL = "http://localhost:8000";
    private static final String SOURCE_GITLAB_TOKEN = "glpat-QfaqawuLrzcPfJ3oEgiStG86MQp1OjEH.01.0w01363lq";
    private static final String TARGET_GITLAB_URL = "http://localhost:9000";
    private static final String TARGET_GITLAB_TOKEN = "glpat-b2nrFAAy9q2SozZr3Dm0N286MQp1OjEH.01.0w0t2khzm";

    private UnifiedProjectMonitor unifiedProjectMonitor;
    private BatchQueryExecutor batchQueryExecutor;

    @BeforeEach
    void setUp() {
        // Create RestTemplate
        RestTemplate restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();

        // Create RetryableGitLabClients
        RetryableGitLabClient sourceClient = new RetryableGitLabClient(
                restTemplate, SOURCE_GITLAB_URL, SOURCE_GITLAB_TOKEN, 3, 1000L);
        RetryableGitLabClient targetClient = new RetryableGitLabClient(
                restTemplate, TARGET_GITLAB_URL, TARGET_GITLAB_TOKEN, 3, 1000L);

        // Create ObjectMapper
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        // Create GraphQL client
        GitLabGraphQLClient graphQLClient = new GitLabGraphQLClient(objectMapper);

        // Create BatchQueryExecutor
        batchQueryExecutor = new BatchQueryExecutor(sourceClient, targetClient, graphQLClient);

        // Mock other dependencies for integration test
        // In real scenario, these would be autowired from Spring context
        UpdateProjectDataService updateProjectDataService = mock(UpdateProjectDataService.class);
        DiffCalculator diffCalculator = mock(DiffCalculator.class);
        LocalCacheManager cacheManager = mock(LocalCacheManager.class);
        SyncProjectMapper syncProjectMapper = mock(SyncProjectMapper.class);
        MetricsExporter metricsExporter = mock(MetricsExporter.class);
        com.gitlab.mirror.server.service.ProjectDiscoveryService projectDiscoveryService = mock(com.gitlab.mirror.server.service.ProjectDiscoveryService.class);
        com.gitlab.mirror.server.mapper.SourceProjectInfoMapper sourceProjectInfoMapper = mock(com.gitlab.mirror.server.mapper.SourceProjectInfoMapper.class);
        com.gitlab.mirror.server.mapper.TargetProjectInfoMapper targetProjectInfoMapper = mock(com.gitlab.mirror.server.mapper.TargetProjectInfoMapper.class);
        com.gitlab.mirror.server.service.PullSyncConfigService pullSyncConfigService = mock(com.gitlab.mirror.server.service.PullSyncConfigService.class);
        com.gitlab.mirror.server.service.SyncTaskService syncTaskService = mock(com.gitlab.mirror.server.service.SyncTaskService.class);
        com.gitlab.mirror.server.client.GitLabApiClient sourceGitLabApiClient = mock(com.gitlab.mirror.server.client.GitLabApiClient.class);
        com.gitlab.mirror.server.client.GitLabApiClient targetGitLabApiClient = mock(com.gitlab.mirror.server.client.GitLabApiClient.class);
        com.gitlab.mirror.server.service.BranchSnapshotService branchSnapshotService = mock(com.gitlab.mirror.server.service.BranchSnapshotService.class);

        // Configure mock behaviors
        UpdateProjectDataService.UpdateResult mockResult = new UpdateProjectDataService.UpdateResult();
        mockResult.setSuccessCount(0);
        when(updateProjectDataService.updateSourceProjectsFromGraphQL(anyList(), any(), anyBoolean())).thenReturn(mockResult);
        when(updateProjectDataService.updateTargetProjects(anyList(), any())).thenReturn(mockResult);
        when(diffCalculator.calculateDiffBatch(anyList())).thenReturn(java.util.List.of());
        when(syncProjectMapper.selectList(any())).thenReturn(java.util.List.of());
        when(sourceGitLabApiClient.getAllBranches(any())).thenReturn(java.util.List.of());
        when(targetGitLabApiClient.getAllBranches(any())).thenReturn(java.util.List.of());

        // Create UnifiedProjectMonitor
        unifiedProjectMonitor = new UnifiedProjectMonitor(
                batchQueryExecutor,
                updateProjectDataService,
                diffCalculator,
                cacheManager,
                syncProjectMapper,
                metricsExporter,
                projectDiscoveryService,
                sourceProjectInfoMapper,
                targetProjectInfoMapper,
                pullSyncConfigService,
                syncTaskService,
                sourceGitLabApiClient,
                targetGitLabApiClient,
                branchSnapshotService
        );
    }

    @Test
    void testScan_withGraphQLOptimization() {
        log.info("=".repeat(80));
        log.info("Testing UnifiedProjectMonitor with GraphQL optimization");
        log.info("=".repeat(80));

        // When - execute full scan
        long startTime = System.currentTimeMillis();
        ScanResult result = unifiedProjectMonitor.scan("full");
        long duration = System.currentTimeMillis() - startTime;

        // Then - verify result
        assertNotNull(result);
        log.info("\n" + "=".repeat(80));
        log.info("SCAN RESULT");
        log.info("=".repeat(80));
        log.info("Status: {}", result.getStatus());
        log.info("Projects Scanned: {}", result.getProjectsScanned());
        log.info("Projects Updated: {}", result.getProjectsUpdated());
        log.info("Changes Detected: {}", result.getChangesDetected());
        log.info("Duration: {}ms", result.getDurationMs());
        log.info("Real Duration: {}ms", duration);
        log.info("=".repeat(80));

        // Verify success
        assertEquals("success", result.getStatus());

        // If projects were found, verify GraphQL optimization was used
        if (result.getProjectsScanned() > 0) {
            log.info("\n✅ GraphQL optimization test completed successfully");
            log.info("   - Scanned {} projects", result.getProjectsScanned());
            log.info("   - Total duration: {}ms", result.getDurationMs());

            // Performance assertion: should be much faster than REST API
            // For 10-20 projects, should complete in under 5 seconds
            assertTrue(result.getDurationMs() < 5000,
                    "Scan should complete in under 5 seconds with GraphQL optimization");
        } else {
            log.warn("⚠️  No projects found in source GitLab");
            log.warn("   Please ensure test projects exist in GitLab");
        }
    }

    @Test
    void testBatchQueryExecutor_graphqlBatchQuery() {
        log.info("=".repeat(80));
        log.info("Testing BatchQueryExecutor GraphQL batch query");
        log.info("=".repeat(80));

        // When - query source projects
        long startTime = System.currentTimeMillis();
        var sourceProjects = batchQueryExecutor.querySourceProjects(null, 100);
        long queryDuration = System.currentTimeMillis() - startTime;

        log.info("Queried {} projects in {}ms", sourceProjects.size(), queryDuration);

        if (!sourceProjects.isEmpty()) {
            // Test GraphQL batch query
            startTime = System.currentTimeMillis();
            var graphQLInfos = batchQueryExecutor.getProjectDetailsBatchGraphQL(sourceProjects);
            long graphqlDuration = System.currentTimeMillis() - startTime;

            log.info("\n" + "=".repeat(80));
            log.info("GRAPHQL BATCH QUERY RESULT");
            log.info("=".repeat(80));
            log.info("Projects queried: {}", sourceProjects.size());
            log.info("GraphQL results: {}", graphQLInfos.size());
            log.info("Duration: {}ms", graphqlDuration);
            log.info("Avg per project: {:.1f}ms", graphqlDuration / (double) sourceProjects.size());
            log.info("=".repeat(80));

            // Verify results
            assertNotNull(graphQLInfos);
            assertTrue(graphQLInfos.size() <= sourceProjects.size());

            // Performance assertion: GraphQL should be fast
            // For 10-20 projects, should complete in under 1 second
            assertTrue(graphqlDuration < 1000,
                    "GraphQL batch query should complete in under 1 second");

            log.info("\n✅ GraphQL batch query test passed");
        } else {
            log.warn("⚠️  No projects found for GraphQL testing");
        }
    }

}
