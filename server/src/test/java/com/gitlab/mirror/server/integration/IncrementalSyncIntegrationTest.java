package com.gitlab.mirror.server.integration;

import com.gitlab.mirror.server.entity.*;
import com.gitlab.mirror.server.executor.GitCommandExecutor;
import com.gitlab.mirror.server.mapper.*;
import com.gitlab.mirror.server.service.PullSyncExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Incremental Sync Integration Test (T8.2)
 * <p>
 * Tests incremental sync functionality and change detection mechanism.
 * <p>
 * Test Scenarios:
 * 1. Complete First Sync - Establish baseline
 * 2. No Changes Scenario - Verify quick skip via git ls-remote (<1s)
 * 3. With Changes Scenario - Verify incremental sync execution
 * 4. Event Recording - Verify events for both scenarios
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IncrementalSyncIntegrationTest {

    @Autowired
    private PullSyncExecutorService pullSyncExecutorService;

    @MockBean
    private GitCommandExecutor gitCommandExecutor;

    @Autowired
    private SyncProjectMapper syncProjectMapper;

    @Autowired
    private PullSyncConfigMapper pullSyncConfigMapper;

    @Autowired
    private SyncTaskMapper syncTaskMapper;

    @Autowired
    private SourceProjectInfoMapper sourceProjectInfoMapper;

    @Autowired
    private TargetProjectInfoMapper targetProjectInfoMapper;

    @Autowired
    private SyncEventMapper syncEventMapper;

    @BeforeEach
    void setUp() {
        // Clean up test data (handled by @Transactional rollback)
        // Reset mocks
        reset(gitCommandExecutor);
    }

    /**
     * Test incremental sync flow starting from first sync
     * <p>
     * Complete flow:
     * 1. First sync - establish baseline with commit SHA
     * 2. Verify first sync completed successfully
     * 3. Verify local repo path is set
     */
    @Test
    void testIncrementalSync_AfterFirstSync() {
        log.info("========== Test: First Sync Baseline ==========");

        // ========== Step 1: Create Project Configuration ==========
        log.info("Step 1: Creating project configuration...");

        String projectKey = "incremental-test/baseline-project";
        SyncProject project = createSyncProject(projectKey, "pull_sync");
        PullSyncConfig config = createPullSyncConfig(project.getId(), "normal");
        SyncTask task = createSyncTask(project.getId(), "pull");
        createSourceProjectInfo(project.getId(), projectKey);
        createTargetProjectInfo(project.getId(), projectKey);

        log.info("Created project: id={}, key={}", project.getId(), projectKey);

        // ========== Step 2: Mock First Sync ==========
        log.info("Step 2: Executing first sync...");

        String firstSyncSha = "abc123def456789";

        when(gitCommandExecutor.isValidRepository(anyString())).thenReturn(false);
        GitCommandExecutor.GitResult syncResult = new GitCommandExecutor.GitResult(
            true, "FINAL_SHA=" + firstSyncSha + "\n", "", 0
        );
        when(gitCommandExecutor.syncFirst(anyString(), anyString(), anyString())).thenReturn(syncResult);

        // Execute first sync
        pullSyncExecutorService.executeSync(task);

        // ========== Step 3: Verify First Sync Completed ==========
        log.info("Step 3: Verifying first sync completion...");

        SyncTask updatedTask = syncTaskMapper.selectById(task.getId());
        assertThat(updatedTask.getLastSyncStatus())
            .as("First sync should succeed")
            .isEqualTo("success");

        assertThat(updatedTask.getSourceCommitSha())
            .as("Source commit SHA should be saved")
            .isEqualTo(firstSyncSha);

        PullSyncConfig updatedConfig = pullSyncConfigMapper.selectById(config.getId());
        assertThat(updatedConfig.getLocalRepoPath())
            .as("Local repo path should be set after first sync")
            .isNotNull();

        log.info("First sync completed: SHA={}, localRepoPath={}",
            updatedTask.getSourceCommitSha(), updatedConfig.getLocalRepoPath());

        log.info("========== Test Passed: First Sync Baseline ==========");
    }

    /**
     * Test incremental sync when no changes detected
     * <p>
     * Scenario:
     * 1. Complete first sync
     * 2. Run incremental sync with same commit SHA (no changes)
     * 3. Verify quick skip via git ls-remote
     * 4. Verify execution time < 1 second
     * 5. Verify no git sync operations performed
     */
    @Test
    void testIncrementalSync_NoChanges_QuickSkip() {
        log.info("========== Test: Incremental Sync - No Changes (Quick Skip) ==========");

        // ========== Step 1: Complete First Sync ==========
        log.info("Step 1: Completing first sync...");

        String projectKey = "incremental-test/no-changes-project";
        String commitSha = "xyz789abc123def";

        SyncProject project = createSyncProject(projectKey, "pull_sync");
        PullSyncConfig config = createPullSyncConfig(project.getId(), "high");
        SyncTask task = createSyncTask(project.getId(), "pull");
        createSourceProjectInfo(project.getId(), projectKey);
        createTargetProjectInfo(project.getId(), projectKey);

        // Simulate first sync already completed
        config.setLocalRepoPath("/Users/test/.gitlab-sync/repos/" + projectKey);
        pullSyncConfigMapper.updateById(config);

        task.setSourceCommitSha(commitSha);
        task.setLastSyncStatus("success");
        syncTaskMapper.updateById(task);

        log.info("First sync state established: SHA={}", commitSha);

        // ========== Step 2: Mock No Changes Scenario ==========
        log.info("Step 2: Setting up no changes scenario...");

        // Repository exists (first sync completed)
        when(gitCommandExecutor.isValidRepository(anyString())).thenReturn(true);

        // git ls-remote returns same SHA (no changes)
        GitCommandExecutor.GitResult lsRemoteResult = new GitCommandExecutor.GitResult(
            true, commitSha + "\n", "", 0
        );
        lsRemoteResult.parsedData.put("HEAD_SHA", commitSha);
        when(gitCommandExecutor.getRemoteHeadSha(anyString())).thenReturn(lsRemoteResult);

        log.info("Git mocks configured: ls-remote will return same SHA (no changes)");

        // ========== Step 3: Execute Incremental Sync ==========
        log.info("Step 3: Executing incremental sync...");

        long startTime = System.currentTimeMillis();
        pullSyncExecutorService.executeSync(task);
        long duration = System.currentTimeMillis() - startTime;

        log.info("Incremental sync completed in {} ms", duration);

        // ========== Step 4: Verify Quick Skip ==========
        log.info("Step 4: Verifying quick skip behavior...");

        // Verify execution time < 1 second (actually should be much faster, like <100ms)
        assertThat(duration)
            .as("No changes scenario should complete very quickly (<1000ms)")
            .isLessThan(1000);

        // Verify task status
        SyncTask updatedTask = syncTaskMapper.selectById(task.getId());
        assertThat(updatedTask.getLastSyncStatus())
            .as("Sync should still be successful (no changes)")
            .isEqualTo("success");

        assertThat(updatedTask.getHasChanges())
            .as("has_changes should be false")
            .isFalse();

        assertThat(updatedTask.getSourceCommitSha())
            .as("Commit SHA should remain unchanged")
            .isEqualTo(commitSha);

        // Verify no git sync operations were performed
        verify(gitCommandExecutor, times(1)).isValidRepository(anyString());
        verify(gitCommandExecutor, times(1)).getRemoteHeadSha(anyString());
        verify(gitCommandExecutor, never()).syncFirst(anyString(), anyString(), anyString());
        verify(gitCommandExecutor, never()).syncIncremental(anyString(), anyString(), anyString());

        log.info("Quick skip verified: duration={}ms, hasChanges=false, no sync operations",
            duration);

        // ========== Step 5: Verify Event Recording ==========
        log.info("Step 5: Verifying event recording for no changes...");

        List<SyncEvent> events = syncEventMapper.selectList(null);
        List<SyncEvent> projectEvents = events.stream()
            .filter(e -> e.getSyncProjectId().equals(project.getId()))
            .toList();

        // Should have incremental_sync_skipped event for no changes
        boolean hasSyncSkipped = projectEvents.stream()
            .anyMatch(e -> "incremental_sync_skipped".equals(e.getEventType()));

        assertThat(hasSyncSkipped)
            .as("incremental_sync_skipped event should be recorded for no changes")
            .isTrue();

        log.info("Events verified: {} events recorded for no changes scenario", projectEvents.size());

        log.info("========== Test Passed: Incremental Sync - No Changes ==========");
    }

    /**
     * Test incremental sync when changes detected
     * <p>
     * Scenario:
     * 1. Complete first sync
     * 2. Run incremental sync with different commit SHA (changes detected)
     * 3. Verify git ls-remote detects changes
     * 4. Verify git sync-incremental is executed
     * 5. Verify commit SHA is updated
     */
    @Test
    void testIncrementalSync_WithChanges_ExecutesSync() {
        log.info("========== Test: Incremental Sync - With Changes ==========");

        // ========== Step 1: Complete First Sync ==========
        log.info("Step 1: Completing first sync...");

        String projectKey = "incremental-test/with-changes-project";
        String oldCommitSha = "old123abc456def";
        String newCommitSha = "new789xyz012ghi";

        SyncProject project = createSyncProject(projectKey, "pull_sync");
        PullSyncConfig config = createPullSyncConfig(project.getId(), "normal");
        SyncTask task = createSyncTask(project.getId(), "pull");
        createSourceProjectInfo(project.getId(), projectKey);
        createTargetProjectInfo(project.getId(), projectKey);

        // Simulate first sync already completed
        config.setLocalRepoPath("/Users/test/.gitlab-sync/repos/" + projectKey);
        pullSyncConfigMapper.updateById(config);

        task.setSourceCommitSha(oldCommitSha);
        task.setLastSyncStatus("success");
        syncTaskMapper.updateById(task);

        log.info("First sync state established: oldSHA={}", oldCommitSha);

        // ========== Step 2: Mock Changes Detected Scenario ==========
        log.info("Step 2: Setting up changes detected scenario...");

        // Repository exists
        when(gitCommandExecutor.isValidRepository(anyString())).thenReturn(true);

        // git ls-remote returns different SHA (changes detected)
        GitCommandExecutor.GitResult lsRemoteResult = new GitCommandExecutor.GitResult(
            true, newCommitSha + "\n", "", 0
        );
        lsRemoteResult.parsedData.put("HEAD_SHA", newCommitSha);
        when(gitCommandExecutor.getRemoteHeadSha(anyString())).thenReturn(lsRemoteResult);

        // git sync-incremental succeeds
        GitCommandExecutor.GitResult syncResult = new GitCommandExecutor.GitResult(
            true, "FINAL_SHA=" + newCommitSha + "\n", "", 0
        );
        when(gitCommandExecutor.syncIncremental(anyString(), anyString(), anyString())).thenReturn(syncResult);

        log.info("Git mocks configured: ls-remote returns newSHA={}", newCommitSha);

        // ========== Step 3: Execute Incremental Sync ==========
        log.info("Step 3: Executing incremental sync...");

        long startTime = System.currentTimeMillis();
        pullSyncExecutorService.executeSync(task);
        long duration = System.currentTimeMillis() - startTime;

        log.info("Incremental sync completed in {} ms", duration);

        // ========== Step 4: Verify Sync Execution ==========
        log.info("Step 4: Verifying sync execution...");

        SyncTask updatedTask = syncTaskMapper.selectById(task.getId());

        assertThat(updatedTask.getLastSyncStatus())
            .as("Sync should be successful")
            .isEqualTo("success");

        assertThat(updatedTask.getHasChanges())
            .as("has_changes should be true")
            .isTrue();

        assertThat(updatedTask.getSourceCommitSha())
            .as("Commit SHA should be updated to new SHA")
            .isEqualTo(newCommitSha);

        // Verify git sync-incremental was called
        verify(gitCommandExecutor, times(1)).isValidRepository(anyString());
        verify(gitCommandExecutor, times(1)).getRemoteHeadSha(anyString());
        verify(gitCommandExecutor, times(1)).syncIncremental(anyString(), anyString(), anyString());
        verify(gitCommandExecutor, never()).syncFirst(anyString(), anyString(), anyString());

        log.info("Sync execution verified: newSHA={}, hasChanges=true, syncIncremental called",
            updatedTask.getSourceCommitSha());

        // ========== Step 5: Verify Event Recording ==========
        log.info("Step 5: Verifying event recording for changes...");

        List<SyncEvent> events = syncEventMapper.selectList(null);
        List<SyncEvent> projectEvents = events.stream()
            .filter(e -> e.getSyncProjectId().equals(project.getId()))
            .toList();

        boolean hasSyncCompleted = projectEvents.stream()
            .anyMatch(e -> "incremental_sync_completed".equals(e.getEventType()));

        assertThat(hasSyncCompleted)
            .as("incremental_sync_completed event should be recorded")
            .isTrue();

        log.info("Events verified: {} events recorded for changes scenario", projectEvents.size());

        log.info("========== Test Passed: Incremental Sync - With Changes ==========");
    }

    /**
     * Test multiple incremental syncs - no changes then changes
     * <p>
     * Scenario:
     * 1. First sync
     * 2. Incremental sync #1 - no changes (quick skip)
     * 3. Incremental sync #2 - with changes (full sync)
     * 4. Verify both scenarios work correctly in sequence
     */
    @Test
    void testIncrementalSync_MultipleRuns_MixedScenarios() {
        log.info("========== Test: Multiple Incremental Syncs (Mixed Scenarios) ==========");

        // ========== Step 1: Complete First Sync ==========
        log.info("Step 1: Completing first sync...");

        String projectKey = "incremental-test/mixed-scenarios";
        String sha1 = "sha1_first_sync";
        String sha2 = "sha2_after_changes";

        SyncProject project = createSyncProject(projectKey, "pull_sync");
        PullSyncConfig config = createPullSyncConfig(project.getId(), "high");
        SyncTask task = createSyncTask(project.getId(), "pull");
        createSourceProjectInfo(project.getId(), projectKey);
        createTargetProjectInfo(project.getId(), projectKey);

        // Simulate first sync completed
        config.setLocalRepoPath("/Users/test/.gitlab-sync/repos/" + projectKey);
        pullSyncConfigMapper.updateById(config);

        task.setSourceCommitSha(sha1);
        task.setLastSyncStatus("success");
        syncTaskMapper.updateById(task);

        log.info("First sync completed: SHA={}", sha1);

        // ========== Step 2: Incremental Sync #1 - No Changes ==========
        log.info("Step 2: Running incremental sync #1 (no changes)...");

        when(gitCommandExecutor.isValidRepository(anyString())).thenReturn(true);

        GitCommandExecutor.GitResult noChangeResult = new GitCommandExecutor.GitResult(
            true, sha1 + "\n", "", 0
        );
        noChangeResult.parsedData.put("HEAD_SHA", sha1);
        when(gitCommandExecutor.getRemoteHeadSha(anyString())).thenReturn(noChangeResult);

        long startNoChange = System.currentTimeMillis();
        pullSyncExecutorService.executeSync(task);
        long durationNoChange = System.currentTimeMillis() - startNoChange;

        SyncTask afterNoChange = syncTaskMapper.selectById(task.getId());
        assertThat(afterNoChange.getHasChanges()).isFalse();
        assertThat(afterNoChange.getSourceCommitSha()).isEqualTo(sha1);
        assertThat(durationNoChange).isLessThan(1000);

        log.info("Incremental sync #1 completed: hasChanges=false, duration={}ms", durationNoChange);

        // ========== Step 3: Incremental Sync #2 - With Changes ==========
        log.info("Step 3: Running incremental sync #2 (with changes)...");

        // Reset task for next run
        task = syncTaskMapper.selectById(task.getId());
        task.setTaskStatus("waiting");
        task.setNextRunAt(Instant.now());
        syncTaskMapper.updateById(task);

        // Mock changes detected
        GitCommandExecutor.GitResult changeResult = new GitCommandExecutor.GitResult(
            true, sha2 + "\n", "", 0
        );
        changeResult.parsedData.put("HEAD_SHA", sha2);
        when(gitCommandExecutor.getRemoteHeadSha(anyString())).thenReturn(changeResult);

        GitCommandExecutor.GitResult syncResult = new GitCommandExecutor.GitResult(
            true, "FINAL_SHA=" + sha2 + "\n", "", 0
        );
        when(gitCommandExecutor.syncIncremental(anyString(), anyString(), anyString())).thenReturn(syncResult);

        pullSyncExecutorService.executeSync(task);

        SyncTask afterChange = syncTaskMapper.selectById(task.getId());
        assertThat(afterChange.getHasChanges()).isTrue();
        assertThat(afterChange.getSourceCommitSha()).isEqualTo(sha2);

        log.info("Incremental sync #2 completed: hasChanges=true, newSHA={}", sha2);

        // ========== Step 4: Verify Command Sequence ==========
        log.info("Step 4: Verifying command sequence...");

        // Should have called ls-remote twice, sync-incremental once
        verify(gitCommandExecutor, atLeast(2)).getRemoteHeadSha(anyString());
        verify(gitCommandExecutor, times(1)).syncIncremental(anyString(), anyString(), anyString());
        verify(gitCommandExecutor, never()).syncFirst(anyString(), anyString(), anyString());

        log.info("Command sequence verified: ls-remote called twice, syncIncremental once");

        log.info("========== Test Passed: Multiple Incremental Syncs ==========");
    }

    /**
     * Test change detection failure handling
     * <p>
     * Scenario:
     * 1. git ls-remote fails (network error)
     * 2. Verify error is recorded
     * 3. Verify retry is scheduled
     */
    @Test
    void testIncrementalSync_ChangeDetectionFailure() {
        log.info("========== Test: Change Detection Failure ==========");

        // ========== Step 1: Setup Project ==========
        String projectKey = "incremental-test/detection-failure";

        SyncProject project = createSyncProject(projectKey, "pull_sync");
        PullSyncConfig config = createPullSyncConfig(project.getId(), "normal");
        SyncTask task = createSyncTask(project.getId(), "pull");
        createSourceProjectInfo(project.getId(), projectKey);
        createTargetProjectInfo(project.getId(), projectKey);

        config.setLocalRepoPath("/Users/test/.gitlab-sync/repos/" + projectKey);
        pullSyncConfigMapper.updateById(config);

        task.setSourceCommitSha("old_sha");
        syncTaskMapper.updateById(task);

        log.info("Project setup: SHA=old_sha");

        // ========== Step 2: Mock ls-remote Failure ==========
        log.info("Step 2: Mocking ls-remote failure...");

        when(gitCommandExecutor.isValidRepository(anyString())).thenReturn(true);

        GitCommandExecutor.GitResult lsRemoteError = new GitCommandExecutor.GitResult(
            false, "", "Network timeout", 128
        );
        when(gitCommandExecutor.getRemoteHeadSha(anyString())).thenReturn(lsRemoteError);

        // ========== Step 3: Execute Sync ==========
        log.info("Step 3: Executing sync with ls-remote failure...");

        pullSyncExecutorService.executeSync(task);

        // ========== Step 4: Verify Failure Handling ==========
        log.info("Step 4: Verifying failure handling...");

        SyncTask updatedTask = syncTaskMapper.selectById(task.getId());

        assertThat(updatedTask.getLastSyncStatus())
            .as("Last sync status should be failed")
            .isEqualTo("failed");

        assertThat(updatedTask.getErrorMessage())
            .as("Error message should be recorded")
            .contains("Failed to get remote HEAD SHA");

        assertThat(updatedTask.getConsecutiveFailures())
            .as("Consecutive failures should be incremented")
            .isEqualTo(1);

        // Verify retry is scheduled (with backoff)
        assertThat(updatedTask.getNextRunAt())
            .as("Next run should be scheduled in the future (retry with backoff)")
            .isAfter(Instant.now().plus(4, ChronoUnit.MINUTES));

        log.info("Failure handling verified: status=failed, consecutiveFailures=1, nextRunAt={}",
            updatedTask.getNextRunAt());

        log.info("========== Test Passed: Change Detection Failure ==========");
    }

    // ========== Helper Methods ==========

    private SyncProject createSyncProject(String projectKey, String syncMethod) {
        SyncProject project = new SyncProject();
        project.setProjectKey(projectKey);
        project.setSyncMethod(syncMethod);
        project.setSyncStatus("active");
        project.setEnabled(true);
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        syncProjectMapper.insert(project);
        return project;
    }

    private PullSyncConfig createPullSyncConfig(Long syncProjectId, String priority) {
        PullSyncConfig config = new PullSyncConfig();
        config.setSyncProjectId(syncProjectId);
        config.setPriority(priority);
        config.setEnabled(true);
        config.setLocalRepoPath(null);
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        pullSyncConfigMapper.insert(config);
        return config;
    }

    private SyncTask createSyncTask(Long syncProjectId, String taskType) {
        SyncTask task = new SyncTask();
        task.setSyncProjectId(syncProjectId);
        task.setTaskType(taskType);
        task.setTaskStatus("waiting");
        task.setNextRunAt(Instant.now());
        task.setConsecutiveFailures(0);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        syncTaskMapper.insert(task);
        return task;
    }

    private SourceProjectInfo createSourceProjectInfo(Long syncProjectId, String projectKey) {
        SourceProjectInfo info = new SourceProjectInfo();
        info.setSyncProjectId(syncProjectId);
        info.setGitlabProjectId(1000L + syncProjectId);
        info.setPathWithNamespace(projectKey);
        info.setName(projectKey.substring(projectKey.lastIndexOf('/') + 1));
        info.setDefaultBranch("main");
        info.setVisibility("private");
        info.setArchived(false);
        info.setEmptyRepo(false);
        info.setSyncedAt(LocalDateTime.now());
        info.setUpdatedAt(LocalDateTime.now());
        sourceProjectInfoMapper.insert(info);
        return info;
    }

    private TargetProjectInfo createTargetProjectInfo(Long syncProjectId, String projectKey) {
        TargetProjectInfo info = new TargetProjectInfo();
        info.setSyncProjectId(syncProjectId);
        info.setGitlabProjectId(2000L + syncProjectId);
        info.setPathWithNamespace(projectKey);
        info.setName(projectKey.substring(projectKey.lastIndexOf('/') + 1));
        info.setVisibility("private");
        info.setCreatedAt(LocalDateTime.now());
        info.setUpdatedAt(LocalDateTime.now());
        targetProjectInfoMapper.insert(info);
        return info;
    }
}
