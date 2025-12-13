package com.gitlab.mirror.server.service;

import com.gitlab.mirror.server.config.properties.GitLabMirrorProperties;
import com.gitlab.mirror.server.entity.*;
import com.gitlab.mirror.server.executor.GitCommandExecutor;
import com.gitlab.mirror.server.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
@ExtendWith(MockitoExtension.class)
class PullSyncExecutorServiceTest {

    @Mock
    private GitCommandExecutor gitCommandExecutor;

    @Mock
    private TargetProjectManagementService targetProjectManagementService;

    @Mock
    private SyncTaskMapper syncTaskMapper;

    @Mock
    private SyncProjectMapper syncProjectMapper;

    @Mock
    private PullSyncConfigMapper pullSyncConfigMapper;

    @Mock
    private SourceProjectInfoMapper sourceProjectInfoMapper;

    @Mock
    private TargetProjectInfoMapper targetProjectInfoMapper;

    @Mock
    private SyncEventMapper syncEventMapper;

    @Mock
    private GitLabMirrorProperties properties;

    @InjectMocks
    private PullSyncExecutorService service;

    private SyncTask task;
    private SyncProject project;
    private PullSyncConfig config;
    private SourceProjectInfo sourceInfo;
    private TargetProjectInfo targetInfo;

    @BeforeEach
    void setUp() {
        // Setup task
        task = new SyncTask();
        task.setId(1L);
        task.setSyncProjectId(100L);
        task.setTaskType("pull");
        task.setTaskStatus("waiting");
        task.setNextRunAt(Instant.now());
        task.setConsecutiveFailures(0);

        // Setup project
        project = new SyncProject();
        project.setId(100L);
        project.setProjectKey("group1/project1");
        project.setSyncMethod("pull_sync");

        // Setup config
        config = new PullSyncConfig();
        config.setId(1L);
        config.setSyncProjectId(100L);
        config.setPriority("normal");
        config.setEnabled(true);
        config.setLocalRepoPath(null);  // First sync

        // Setup source info
        sourceInfo = new SourceProjectInfo();
        sourceInfo.setSyncProjectId(100L);
        sourceInfo.setPathWithNamespace("group1/project1");

        // Setup target info
        targetInfo = new TargetProjectInfo();
        targetInfo.setSyncProjectId(100L);
        targetInfo.setGitlabProjectId(200L);
        targetInfo.setPathWithNamespace("group1/project1");

        // Setup properties
        GitLabMirrorProperties.GitLabInstance source = new GitLabMirrorProperties.GitLabInstance();
        source.setUrl("http://localhost:8000");
        source.setToken("source-token");

        GitLabMirrorProperties.GitLabInstance target = new GitLabMirrorProperties.GitLabInstance();
        target.setUrl("http://localhost:9000");
        target.setToken("target-token");

        when(properties.getSource()).thenReturn(source);
        when(properties.getTarget()).thenReturn(target);
    }

    @Test
    void testExecuteFirstSync_Success() {
        // Setup mocks
        when(syncProjectMapper.selectById(100L)).thenReturn(project);
        when(pullSyncConfigMapper.selectOne(any())).thenReturn(config);
        when(gitCommandExecutor.isValidRepository(anyString())).thenReturn(false);  // First sync
        when(sourceProjectInfoMapper.selectOne(any())).thenReturn(sourceInfo);
        when(targetProjectInfoMapper.selectOne(any())).thenReturn(targetInfo);

        // Setup git result
        GitCommandExecutor.GitResult result = new GitCommandExecutor.GitResult(
            true, "FINAL_SHA=abc123\n", "", 0
        );
        when(gitCommandExecutor.syncFirst(anyString(), anyString(), anyString())).thenReturn(result);

        // Execute
        service.executeSync(task);

        // Verify task status updated to running
        verify(syncTaskMapper, atLeastOnce()).updateById(argThat(t ->
            t.getTaskStatus() != null && (t.getTaskStatus().equals("running") || t.getTaskStatus().equals("waiting"))
        ));

        // Verify target project checked
        verify(targetProjectInfoMapper).selectOne(any());

        // Verify git sync-first called
        verify(gitCommandExecutor).syncFirst(anyString(), anyString(), anyString());

        // Verify config updated with local repo path
        verify(pullSyncConfigMapper).updateById(argThat(c ->
            c.getLocalRepoPath() != null && !c.getLocalRepoPath().isEmpty()
        ));

        // Verify event recorded
        verify(syncEventMapper).insert(any(SyncEvent.class));
    }

    @Test
    void testExecuteSync_Disabled() {
        // Setup disabled config
        config.setEnabled(false);

        when(syncProjectMapper.selectById(100L)).thenReturn(project);
        when(pullSyncConfigMapper.selectOne(any())).thenReturn(config);

        // Execute
        service.executeSync(task);

        // Verify no git operations
        verify(gitCommandExecutor, never()).syncFirst(anyString(), anyString(), anyString());

        // Verify task updated to waiting
        verify(syncTaskMapper, atLeastOnce()).updateById(any());
    }

    @Test
    void testExecuteSync_Failure() {
        // Setup mocks
        when(syncProjectMapper.selectById(100L)).thenReturn(project);
        when(pullSyncConfigMapper.selectOne(any())).thenReturn(config);
        when(gitCommandExecutor.isValidRepository(anyString())).thenReturn(false);
        when(sourceProjectInfoMapper.selectOne(any())).thenReturn(sourceInfo);
        when(targetProjectInfoMapper.selectOne(any())).thenReturn(targetInfo);

        // Setup git failure
        GitCommandExecutor.GitResult result = new GitCommandExecutor.GitResult(
            false, "", "Authentication failed", 1
        );
        when(gitCommandExecutor.syncFirst(anyString(), anyString(), anyString())).thenReturn(result);

        // Execute (should handle exception)
        service.executeSync(task);

        // Verify failure handling
        verify(syncTaskMapper, atLeastOnce()).updateById(argThat(t ->
            t.getLastSyncStatus() != null && t.getLastSyncStatus().equals("failed")
        ));

        // Verify consecutive failures incremented
        verify(syncTaskMapper, atLeastOnce()).updateById(argThat(t ->
            t.getConsecutiveFailures() != null && t.getConsecutiveFailures() > 0
        ));

        // Verify failure event recorded
        verify(syncEventMapper).insert(argThat((SyncEvent event) ->
            event.getStatus().equals("failed")
        ));
    }

    @Test
    void testBuildGitUrl() throws Exception {
        // Use reflection to test private method indirectly through executeSync
        // This is verified through the integration test

        when(syncProjectMapper.selectById(100L)).thenReturn(project);
        when(pullSyncConfigMapper.selectOne(any())).thenReturn(config);
        when(gitCommandExecutor.isValidRepository(anyString())).thenReturn(false);
        when(sourceProjectInfoMapper.selectOne(any())).thenReturn(sourceInfo);
        when(targetProjectInfoMapper.selectOne(any())).thenReturn(targetInfo);

        GitCommandExecutor.GitResult result = new GitCommandExecutor.GitResult(true, "FINAL_SHA=abc\n", "", 0);
        when(gitCommandExecutor.syncFirst(anyString(), anyString(), anyString())).thenReturn(result);

        service.executeSync(task);

        // Verify git URLs contain tokens
        verify(gitCommandExecutor).syncFirst(
            argThat(url -> url.contains("source-token")),
            argThat(url -> url.contains("target-token")),
            anyString()
        );
    }

    @Test
    void testExecuteSync_WithPriority() {
        // Test that different priorities work
        config.setPriority("critical");

        when(syncProjectMapper.selectById(100L)).thenReturn(project);
        when(pullSyncConfigMapper.selectOne(any())).thenReturn(config);
        when(gitCommandExecutor.isValidRepository(anyString())).thenReturn(false);
        when(sourceProjectInfoMapper.selectOne(any())).thenReturn(sourceInfo);
        when(targetProjectInfoMapper.selectOne(any())).thenReturn(targetInfo);

        GitCommandExecutor.GitResult result = new GitCommandExecutor.GitResult(true, "FINAL_SHA=xyz\n", "", 0);
        when(gitCommandExecutor.syncFirst(anyString(), anyString(), anyString())).thenReturn(result);

        // Execute
        service.executeSync(task);

        // Verify sync executed
        verify(gitCommandExecutor).syncFirst(anyString(), anyString(), anyString());
    }
}
