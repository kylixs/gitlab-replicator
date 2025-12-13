package com.gitlab.mirror.server.service;

import com.gitlab.mirror.server.config.properties.GitLabMirrorProperties;
import com.gitlab.mirror.server.entity.*;
import com.gitlab.mirror.server.executor.GitCommandExecutor;
import com.gitlab.mirror.server.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pull Sync Executor Service Test
 * <p>
 * MUST strictly perform unit testing for Pull sync execution
 *
 * @author GitLab Mirror Team
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PullSyncExecutorServiceTest {

    @Autowired
    private PullSyncExecutorService service;

    @MockBean
    private GitCommandExecutor gitCommandExecutor;

    @MockBean
    private TargetProjectManagementService targetProjectManagementService;

    @Autowired
    private SyncTaskMapper syncTaskMapper;

    @Autowired
    private SyncProjectMapper syncProjectMapper;

    @Autowired
    private PullSyncConfigMapper pullSyncConfigMapper;

    @Autowired
    private SourceProjectInfoMapper sourceProjectInfoMapper;

    @Autowired
    private TargetProjectInfoMapper targetProjectInfoMapper;

    @Autowired
    private SyncEventMapper syncEventMapper;

    private SyncTask task;
    private SyncProject project;
    private PullSyncConfig config;
    private SourceProjectInfo sourceInfo;
    private TargetProjectInfo targetInfo;

    @BeforeEach
    void setUp() {
        // Create project
        project = new SyncProject();
        project.setProjectKey("test-group/test-project");
        project.setSyncMethod("pull_sync");
        project.setSyncStatus("pending");
        project.setEnabled(true);
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        syncProjectMapper.insert(project);

        // Create pull sync config
        config = new PullSyncConfig();
        config.setSyncProjectId(project.getId());
        config.setPriority("normal");
        config.setEnabled(true);
        config.setLocalRepoPath(null);  // First sync
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        pullSyncConfigMapper.insert(config);

        // Create sync task
        task = new SyncTask();
        task.setSyncProjectId(project.getId());
        task.setTaskType("pull");
        task.setTaskStatus("waiting");
        task.setNextRunAt(Instant.now());
        task.setConsecutiveFailures(0);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        syncTaskMapper.insert(task);

        // Create source project info
        sourceInfo = new SourceProjectInfo();
        sourceInfo.setSyncProjectId(project.getId());
        sourceInfo.setGitlabProjectId(100L);
        sourceInfo.setPathWithNamespace("test-group/test-project");
        sourceInfo.setName("test-project");
        sourceInfo.setDefaultBranch("main");
        sourceInfo.setVisibility("private");
        sourceInfo.setArchived(false);
        sourceInfo.setEmptyRepo(false);
        sourceInfo.setSyncedAt(LocalDateTime.now());
        sourceInfo.setUpdatedAt(LocalDateTime.now());
        sourceProjectInfoMapper.insert(sourceInfo);

        // Create target project info
        targetInfo = new TargetProjectInfo();
        targetInfo.setSyncProjectId(project.getId());
        targetInfo.setGitlabProjectId(200L);
        targetInfo.setPathWithNamespace("test-group/test-project");
        targetInfo.setName("test-project");
        targetInfo.setVisibility("private");
        targetInfo.setCreatedAt(LocalDateTime.now());
        targetInfo.setUpdatedAt(LocalDateTime.now());
        targetProjectInfoMapper.insert(targetInfo);
    }

    @Test
    void testExecuteFirstSync_Success() {
        // Mock git operations
        when(gitCommandExecutor.isValidRepository(anyString())).thenReturn(false);

        GitCommandExecutor.GitResult result = new GitCommandExecutor.GitResult(
            true, "FINAL_SHA=abc123def456\n", "", 0
        );
        when(gitCommandExecutor.syncFirst(anyString(), anyString(), anyString())).thenReturn(result);

        // Execute
        service.executeSync(task);

        // Verify task updated
        SyncTask updatedTask = syncTaskMapper.selectById(task.getId());
        assertThat(updatedTask).isNotNull();
        assertThat(updatedTask.getTaskStatus()).isEqualTo("waiting");
        assertThat(updatedTask.getLastSyncStatus()).isEqualTo("success");
        assertThat(updatedTask.getSourceCommitSha()).isEqualTo("abc123def456");
        assertThat(updatedTask.getConsecutiveFailures()).isEqualTo(0);

        // Verify config updated with local repo path
        PullSyncConfig updatedConfig = pullSyncConfigMapper.selectById(config.getId());
        assertThat(updatedConfig.getLocalRepoPath()).isNotNull();
        assertThat(updatedConfig.getLocalRepoPath()).contains("test-group/test-project");

        // Verify git sync-first was called
        verify(gitCommandExecutor).syncFirst(anyString(), anyString(), anyString());
    }

    @Test
    void testExecuteSync_Disabled() {
        // Disable config
        config.setEnabled(false);
        pullSyncConfigMapper.updateById(config);

        // Execute
        service.executeSync(task);

        // Verify no git operations
        verify(gitCommandExecutor, never()).syncFirst(anyString(), anyString(), anyString());

        // Verify task still in waiting status
        SyncTask updatedTask = syncTaskMapper.selectById(task.getId());
        assertThat(updatedTask.getTaskStatus()).isEqualTo("waiting");
    }

    @Test
    void testExecuteSync_GitFailure() {
        // Mock git failure
        when(gitCommandExecutor.isValidRepository(anyString())).thenReturn(false);

        GitCommandExecutor.GitResult result = new GitCommandExecutor.GitResult(
            false, "", "Authentication failed", 128
        );
        when(gitCommandExecutor.syncFirst(anyString(), anyString(), anyString())).thenReturn(result);

        // Execute
        service.executeSync(task);

        // Verify task marked as failed
        SyncTask updatedTask = syncTaskMapper.selectById(task.getId());
        assertThat(updatedTask.getTaskStatus()).isEqualTo("waiting");
        assertThat(updatedTask.getLastSyncStatus()).isEqualTo("failed");
        assertThat(updatedTask.getConsecutiveFailures()).isEqualTo(1);
        assertThat(updatedTask.getErrorMessage()).contains("Authentication failed");
    }

    @Test
    void testExecuteSync_PriorityCalculation() {
        // Set different priorities and verify next_run_at calculation
        config.setPriority("critical");
        pullSyncConfigMapper.updateById(config);

        when(gitCommandExecutor.isValidRepository(anyString())).thenReturn(false);
        GitCommandExecutor.GitResult result = new GitCommandExecutor.GitResult(true, "FINAL_SHA=abc\n", "", 0);
        when(gitCommandExecutor.syncFirst(anyString(), anyString(), anyString())).thenReturn(result);

        Instant before = Instant.now();
        service.executeSync(task);
        Instant after = Instant.now();

        SyncTask updatedTask = syncTaskMapper.selectById(task.getId());
        // Critical priority: 10 minutes interval
        Instant expectedMin = before.plusSeconds(10 * 60 - 5);
        Instant expectedMax = after.plusSeconds(10 * 60 + 5);

        assertThat(updatedTask.getNextRunAt()).isBetween(expectedMin, expectedMax);
    }

    @Test
    void testExecuteSync_RetryWithBackoff() {
        // Simulate failure
        when(gitCommandExecutor.isValidRepository(anyString())).thenReturn(false);
        GitCommandExecutor.GitResult result = new GitCommandExecutor.GitResult(false, "", "Network error", 1);
        when(gitCommandExecutor.syncFirst(anyString(), anyString(), anyString())).thenReturn(result);

        // First failure
        Instant before1 = Instant.now();
        service.executeSync(task);

        SyncTask task1 = syncTaskMapper.selectById(task.getId());
        assertThat(task1.getConsecutiveFailures()).isEqualTo(1);
        // First retry: 5 × 2^0 = 5 minutes
        assertThat(task1.getNextRunAt()).isAfter(before1.plusSeconds(5 * 60 - 5));

        // Second failure
        task1.setTaskStatus("waiting");
        task1.setStartedAt(null);
        syncTaskMapper.updateById(task1);

        Instant before2 = Instant.now();
        service.executeSync(task1);

        SyncTask task2 = syncTaskMapper.selectById(task.getId());
        assertThat(task2.getConsecutiveFailures()).isEqualTo(2);
        // Second retry: 5 × 2^1 = 10 minutes
        assertThat(task2.getNextRunAt()).isAfter(before2.plusSeconds(10 * 60 - 5));
    }

    @Test
    void testExecuteIncrementalSync_NoChanges() {
        // Set up for incremental sync (local repo exists)
        config.setLocalRepoPath("/Users/test/.gitlab-sync/repos/test-group/test-project");
        pullSyncConfigMapper.updateById(config);

        // Set previous sync SHA
        task.setSourceCommitSha("abc123def456");
        syncTaskMapper.updateById(task);

        // Mock git operations - no changes
        when(gitCommandExecutor.isValidRepository(anyString())).thenReturn(true);

        GitCommandExecutor.GitResult lsRemoteResult = new GitCommandExecutor.GitResult(
            true, "abc123def456\n", "", 0
        );
        lsRemoteResult.parsedData.put("HEAD_SHA", "abc123def456");
        when(gitCommandExecutor.getRemoteHeadSha(anyString())).thenReturn(lsRemoteResult);

        // Execute
        service.executeSync(task);

        // Verify task updated with no changes
        SyncTask updatedTask = syncTaskMapper.selectById(task.getId());
        assertThat(updatedTask.getTaskStatus()).isEqualTo("waiting");
        assertThat(updatedTask.getLastSyncStatus()).isEqualTo("success");
        assertThat(updatedTask.getHasChanges()).isFalse();
        assertThat(updatedTask.getSourceCommitSha()).isEqualTo("abc123def456");

        // Verify no git sync was called
        verify(gitCommandExecutor, never()).syncIncremental(anyString(), anyString(), anyString());
    }

    @Test
    void testExecuteIncrementalSync_WithChanges() {
        // Set up for incremental sync
        config.setLocalRepoPath("/Users/test/.gitlab-sync/repos/test-group/test-project");
        pullSyncConfigMapper.updateById(config);

        task.setSourceCommitSha("abc123def456");
        syncTaskMapper.updateById(task);

        // Mock git operations - has changes
        when(gitCommandExecutor.isValidRepository(anyString())).thenReturn(true);

        GitCommandExecutor.GitResult lsRemoteResult = new GitCommandExecutor.GitResult(
            true, "xyz789ghi012\n", "", 0
        );
        lsRemoteResult.parsedData.put("HEAD_SHA", "xyz789ghi012");
        when(gitCommandExecutor.getRemoteHeadSha(anyString())).thenReturn(lsRemoteResult);

        GitCommandExecutor.GitResult syncResult = new GitCommandExecutor.GitResult(
            true, "FINAL_SHA=xyz789ghi012\n", "", 0
        );
        when(gitCommandExecutor.syncIncremental(anyString(), anyString(), anyString())).thenReturn(syncResult);

        // Execute
        service.executeSync(task);

        // Verify task updated with changes
        SyncTask updatedTask = syncTaskMapper.selectById(task.getId());
        assertThat(updatedTask.getTaskStatus()).isEqualTo("waiting");
        assertThat(updatedTask.getLastSyncStatus()).isEqualTo("success");
        assertThat(updatedTask.getHasChanges()).isTrue();
        assertThat(updatedTask.getSourceCommitSha()).isEqualTo("xyz789ghi012");

        // Verify git sync-incremental was called
        verify(gitCommandExecutor).syncIncremental(anyString(), anyString(), anyString());
    }

    @Test
    void testExecuteIncrementalSync_FallbackToFirstSync() {
        // Set up with missing local repo
        config.setLocalRepoPath(null);
        pullSyncConfigMapper.updateById(config);

        // Mock git operations for first sync
        when(gitCommandExecutor.isValidRepository(anyString())).thenReturn(false);

        GitCommandExecutor.GitResult result = new GitCommandExecutor.GitResult(
            true, "FINAL_SHA=abc123def456\n", "", 0
        );
        when(gitCommandExecutor.syncFirst(anyString(), anyString(), anyString())).thenReturn(result);

        // Execute
        service.executeSync(task);

        // Verify first sync was performed
        verify(gitCommandExecutor).syncFirst(anyString(), anyString(), anyString());
        verify(gitCommandExecutor, never()).syncIncremental(anyString(), anyString(), anyString());

        // Verify config updated with local repo path
        PullSyncConfig updatedConfig = pullSyncConfigMapper.selectById(config.getId());
        assertThat(updatedConfig.getLocalRepoPath()).isNotNull();
    }

    @Test
    void testExecuteIncrementalSync_LsRemoteFailure() {
        // Set up for incremental sync
        config.setLocalRepoPath("/Users/test/.gitlab-sync/repos/test-group/test-project");
        pullSyncConfigMapper.updateById(config);

        // Mock git ls-remote failure
        when(gitCommandExecutor.isValidRepository(anyString())).thenReturn(true);

        GitCommandExecutor.GitResult lsRemoteResult = new GitCommandExecutor.GitResult(
            false, "", "Network timeout", 128
        );
        when(gitCommandExecutor.getRemoteHeadSha(anyString())).thenReturn(lsRemoteResult);

        // Execute
        service.executeSync(task);

        // Verify task marked as failed
        SyncTask updatedTask = syncTaskMapper.selectById(task.getId());
        assertThat(updatedTask.getLastSyncStatus()).isEqualTo("failed");
        assertThat(updatedTask.getConsecutiveFailures()).isEqualTo(1);
        assertThat(updatedTask.getErrorMessage()).contains("Failed to get remote HEAD SHA");
    }

    @Test
    void testAutoDisable_AfterFiveFailures() {
        // Mock repeated failures
        when(gitCommandExecutor.isValidRepository(anyString())).thenReturn(false);
        GitCommandExecutor.GitResult result = new GitCommandExecutor.GitResult(false, "", "Network error", 1);
        when(gitCommandExecutor.syncFirst(anyString(), anyString(), anyString())).thenReturn(result);

        // Execute 5 times
        for (int i = 0; i < 5; i++) {
            service.executeSync(task);
            SyncTask updatedTask = syncTaskMapper.selectById(task.getId());
            updatedTask.setTaskStatus("waiting");
            updatedTask.setStartedAt(null);
            syncTaskMapper.updateById(updatedTask);
            task = updatedTask;
        }

        // Verify config auto-disabled after 5th failure
        PullSyncConfig updatedConfig = pullSyncConfigMapper.selectById(config.getId());
        assertThat(updatedConfig.getEnabled()).isFalse();

        // Verify consecutive failures count
        SyncTask finalTask = syncTaskMapper.selectById(task.getId());
        assertThat(finalTask.getConsecutiveFailures()).isEqualTo(5);
    }

    @Test
    void testAutoDisable_NonRetryableError_AuthFailed() {
        // Mock authentication failure
        when(gitCommandExecutor.isValidRepository(anyString())).thenReturn(false);
        GitCommandExecutor.GitResult result = new GitCommandExecutor.GitResult(
            false, "", "Authentication failed", 128
        );
        when(gitCommandExecutor.syncFirst(anyString(), anyString(), anyString())).thenReturn(result);

        // Execute once
        service.executeSync(task);

        // Verify config auto-disabled immediately
        PullSyncConfig updatedConfig = pullSyncConfigMapper.selectById(config.getId());
        assertThat(updatedConfig.getEnabled()).isFalse();

        // Verify error type
        SyncTask updatedTask = syncTaskMapper.selectById(task.getId());
        assertThat(updatedTask.getErrorType()).isEqualTo("auth_failed");
        assertThat(updatedTask.getConsecutiveFailures()).isEqualTo(1);
    }

    @Test
    void testAutoDisable_NonRetryableError_NotFound() {
        // Mock not found error
        when(gitCommandExecutor.isValidRepository(anyString())).thenReturn(false);
        GitCommandExecutor.GitResult result = new GitCommandExecutor.GitResult(
            false, "", "Repository not found", 128
        );
        when(gitCommandExecutor.syncFirst(anyString(), anyString(), anyString())).thenReturn(result);

        // Execute once
        service.executeSync(task);

        // Verify config auto-disabled immediately
        PullSyncConfig updatedConfig = pullSyncConfigMapper.selectById(config.getId());
        assertThat(updatedConfig.getEnabled()).isFalse();

        // Verify error type
        SyncTask updatedTask = syncTaskMapper.selectById(task.getId());
        assertThat(updatedTask.getErrorType()).isEqualTo("not_found");
    }

    @Test
    void testRetryable_NetworkError() {
        // Mock network error (retryable)
        when(gitCommandExecutor.isValidRepository(anyString())).thenReturn(false);
        GitCommandExecutor.GitResult result = new GitCommandExecutor.GitResult(
            false, "", "Network timeout", 1
        );
        when(gitCommandExecutor.syncFirst(anyString(), anyString(), anyString())).thenReturn(result);

        // Execute once
        service.executeSync(task);

        // Verify config NOT disabled (retryable error)
        PullSyncConfig updatedConfig = pullSyncConfigMapper.selectById(config.getId());
        assertThat(updatedConfig.getEnabled()).isTrue();

        // Verify error type and retry scheduled (timeout is retryable)
        SyncTask updatedTask = syncTaskMapper.selectById(task.getId());
        assertThat(updatedTask.getErrorType()).isEqualTo("timeout");
        assertThat(updatedTask.getConsecutiveFailures()).isEqualTo(1);
        assertThat(updatedTask.getNextRunAt()).isAfter(Instant.now().plusSeconds(4 * 60));
    }

    @Test
    void testSuccessResetsFailureCount() {
        // Set initial failure count
        task.setConsecutiveFailures(3);
        syncTaskMapper.updateById(task);

        // Mock successful sync
        when(gitCommandExecutor.isValidRepository(anyString())).thenReturn(false);
        GitCommandExecutor.GitResult result = new GitCommandExecutor.GitResult(
            true, "FINAL_SHA=abc123def456\n", "", 0
        );
        when(gitCommandExecutor.syncFirst(anyString(), anyString(), anyString())).thenReturn(result);

        // Execute
        service.executeSync(task);

        // Verify failure count reset to 0
        SyncTask updatedTask = syncTaskMapper.selectById(task.getId());
        assertThat(updatedTask.getConsecutiveFailures()).isEqualTo(0);
        assertThat(updatedTask.getLastSyncStatus()).isEqualTo("success");
    }
}
