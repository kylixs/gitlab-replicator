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
}
