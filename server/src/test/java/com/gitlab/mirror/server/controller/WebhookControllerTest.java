package com.gitlab.mirror.server.controller;

import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.entity.SyncTask;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.mapper.SyncTaskMapper;
import com.gitlab.mirror.server.service.ProjectInitializationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WebhookController
 *
 * @author GitLab Mirror Team
 */
@ExtendWith(MockitoExtension.class)
class WebhookControllerTest {

    @Mock
    private SyncProjectMapper syncProjectMapper;

    @Mock
    private SyncTaskMapper syncTaskMapper;

    @Mock
    private ProjectInitializationService projectInitializationService;

    @InjectMocks
    private WebhookController webhookController;

    private SyncProject mockSyncProject;
    private SyncTask mockSyncTask;

    @BeforeEach
    void setUp() {
        // Prepare mock sync project
        mockSyncProject = new SyncProject();
        mockSyncProject.setId(984L);
        mockSyncProject.setProjectKey("ai/test-rails-5");
        mockSyncProject.setSyncMethod(SyncProject.SyncMethod.PULL_SYNC);
        mockSyncProject.setSyncStatus(SyncProject.SyncStatus.ACTIVE);
        mockSyncProject.setEnabled(true);

        // Prepare mock sync task
        mockSyncTask = new SyncTask();
        mockSyncTask.setId(392L);
        mockSyncTask.setSyncProjectId(984L);
        mockSyncTask.setTaskType(SyncTask.TaskType.PULL);
        mockSyncTask.setTaskStatus(SyncTask.TaskStatus.WAITING);
        mockSyncTask.setTriggerSource(SyncTask.TriggerSource.SCHEDULED);
        mockSyncTask.setNextRunAt(Instant.now().plusSeconds(60));
    }

    /**
     * Test webhook handles Push Hook event successfully
     */
    @Test
    void testHandlePushHook_Success() {
        // Prepare webhook payload
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> project = new HashMap<>();
        project.put("path_with_namespace", "ai/test-rails-5");
        payload.put("project", project);
        payload.put("ref", "refs/heads/main");
        payload.put("before", "0000000000000000000000000000000000000000");
        payload.put("after", "abc123def456");

        // Mock sync project exists
        when(syncProjectMapper.selectByProjectKey("ai/test-rails-5"))
                .thenReturn(mockSyncProject);

        // Mock sync task exists
        when(syncTaskMapper.selectOne(any())).thenReturn(mockSyncTask);
        when(syncTaskMapper.updateById(any())).thenReturn(1);

        // Execute
        ResponseEntity<?> response = webhookController.handleGitLabWebhook("Push Hook", payload);

        // Verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("success");
        assertThat(body.get("triggered")).isEqualTo(true);
        assertThat(body.get("project")).isEqualTo("ai/test-rails-5");
        assertThat(body.get("change_type")).isEqualTo("branch_create");

        // Verify task update
        ArgumentCaptor<SyncTask> taskCaptor = ArgumentCaptor.forClass(SyncTask.class);
        verify(syncTaskMapper).updateById(taskCaptor.capture());
        SyncTask updatedTask = taskCaptor.getValue();
        assertThat(updatedTask.getTriggerSource()).isEqualTo(SyncTask.TriggerSource.WEBHOOK);
        assertThat(updatedTask.getTaskStatus()).isEqualTo(SyncTask.TaskStatus.WAITING);
        assertThat(updatedTask.getNextRunAt()).isNotNull();
    }

    /**
     * Test webhook handles commit push (normal push)
     */
    @Test
    void testHandlePushHook_CommitPush() {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> project = new HashMap<>();
        project.put("path_with_namespace", "ai/test-rails-5");
        payload.put("project", project);
        payload.put("ref", "refs/heads/main");
        payload.put("before", "abc123def456");
        payload.put("after", "def456abc123");

        when(syncProjectMapper.selectByProjectKey("ai/test-rails-5")).thenReturn(mockSyncProject);
        when(syncTaskMapper.selectOne(any())).thenReturn(mockSyncTask);
        when(syncTaskMapper.updateById(any())).thenReturn(1);

        ResponseEntity<?> response = webhookController.handleGitLabWebhook("Push Hook", payload);

        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("change_type")).isEqualTo("commit_push");
    }

    /**
     * Test webhook handles branch delete
     */
    @Test
    void testHandlePushHook_BranchDelete() {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> project = new HashMap<>();
        project.put("path_with_namespace", "ai/test-rails-5");
        payload.put("project", project);
        payload.put("ref", "refs/heads/feature");
        payload.put("before", "abc123def456");
        payload.put("after", "0000000000000000000000000000000000000000");

        when(syncProjectMapper.selectByProjectKey("ai/test-rails-5")).thenReturn(mockSyncProject);
        when(syncTaskMapper.selectOne(any())).thenReturn(mockSyncTask);
        when(syncTaskMapper.updateById(any())).thenReturn(1);

        ResponseEntity<?> response = webhookController.handleGitLabWebhook("Push Hook", payload);

        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("change_type")).isEqualTo("branch_delete");
    }

    /**
     * Test webhook handles Tag Push Hook
     */
    @Test
    void testHandleTagPushHook_Success() {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> project = new HashMap<>();
        project.put("path_with_namespace", "ai/test-rails-5");
        payload.put("project", project);
        payload.put("ref", "refs/tags/v1.0.0");
        payload.put("before", "0000000000000000000000000000000000000000");
        payload.put("after", "abc123def456");

        when(syncProjectMapper.selectByProjectKey("ai/test-rails-5")).thenReturn(mockSyncProject);
        when(syncTaskMapper.selectOne(any())).thenReturn(mockSyncTask);
        when(syncTaskMapper.updateById(any())).thenReturn(1);

        ResponseEntity<?> response = webhookController.handleGitLabWebhook("Tag Push Hook", payload);

        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("change_type")).isEqualTo("tag_push");
    }

    /**
     * Test webhook handles tag delete
     */
    @Test
    void testHandleTagPushHook_TagDelete() {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> project = new HashMap<>();
        project.put("path_with_namespace", "ai/test-rails-5");
        payload.put("project", project);
        payload.put("ref", "refs/tags/v1.0.0");
        payload.put("before", "abc123def456");
        payload.put("after", "0000000000000000000000000000000000000000");

        when(syncProjectMapper.selectByProjectKey("ai/test-rails-5")).thenReturn(mockSyncProject);
        when(syncTaskMapper.selectOne(any())).thenReturn(mockSyncTask);
        when(syncTaskMapper.updateById(any())).thenReturn(1);

        ResponseEntity<?> response = webhookController.handleGitLabWebhook("Tag Push Hook", payload);

        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("change_type")).isEqualTo("tag_delete");
    }

    /**
     * Test webhook ignores project not in sync
     * Now it tries to initialize the project, so we expect initialization to be called
     */
    @Test
    void testHandleWebhook_ProjectNotSynced() {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> project = new HashMap<>();
        project.put("path_with_namespace", "unknown/project");
        payload.put("project", project);

        when(syncProjectMapper.selectByProjectKey("unknown/project")).thenReturn(null);
        when(projectInitializationService.initializeProjectByPath("unknown/project"))
                .thenThrow(new IllegalArgumentException("Project not found"));

        ResponseEntity<?> response = webhookController.handleGitLabWebhook("Push Hook", payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("status")).isEqualTo("error");
        assertThat(body.get("reason")).isEqualTo("initialization_failed");

        verify(syncTaskMapper, never()).updateById(any());
    }

    /**
     * Test webhook skips if task is already running
     */
    @Test
    void testHandleWebhook_TaskAlreadyRunning() {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> project = new HashMap<>();
        project.put("path_with_namespace", "ai/test-rails-5");
        payload.put("project", project);

        mockSyncTask.setTaskStatus(SyncTask.TaskStatus.RUNNING);

        when(syncProjectMapper.selectByProjectKey("ai/test-rails-5")).thenReturn(mockSyncProject);
        when(syncTaskMapper.selectOne(any())).thenReturn(mockSyncTask);

        ResponseEntity<?> response = webhookController.handleGitLabWebhook("Push Hook", payload);

        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("triggered")).isEqualTo(false);

        verify(syncTaskMapper, never()).updateById(any());
    }

    /**
     * Test webhook skips if task is pending
     */
    @Test
    void testHandleWebhook_TaskPending() {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> project = new HashMap<>();
        project.put("path_with_namespace", "ai/test-rails-5");
        payload.put("project", project);

        mockSyncTask.setTaskStatus(SyncTask.TaskStatus.PENDING);

        when(syncProjectMapper.selectByProjectKey("ai/test-rails-5")).thenReturn(mockSyncProject);
        when(syncTaskMapper.selectOne(any())).thenReturn(mockSyncTask);

        ResponseEntity<?> response = webhookController.handleGitLabWebhook("Push Hook", payload);

        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("triggered")).isEqualTo(false);

        verify(syncTaskMapper, never()).updateById(any());
    }

    /**
     * Test webhook handles missing project field gracefully
     */
    @Test
    void testHandleWebhook_MissingProjectField() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("ref", "refs/heads/main");

        ResponseEntity<?> response = webhookController.handleGitLabWebhook("Push Hook", payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("status")).isEqualTo("ignored");
        assertThat(body.get("reason")).isEqualTo("no_project");
    }

    /**
     * Test webhook handles task not found gracefully
     */
    @Test
    void testHandleWebhook_TaskNotFound() {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> project = new HashMap<>();
        project.put("path_with_namespace", "ai/test-rails-5");
        payload.put("project", project);

        when(syncProjectMapper.selectByProjectKey("ai/test-rails-5")).thenReturn(mockSyncProject);
        when(syncTaskMapper.selectOne(any())).thenReturn(null);

        ResponseEntity<?> response = webhookController.handleGitLabWebhook("Push Hook", payload);

        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("triggered")).isEqualTo(false);
    }

    /**
     * Test webhook handles exceptions gracefully
     */
    @Test
    void testHandleWebhook_Exception() {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> project = new HashMap<>();
        project.put("path_with_namespace", "ai/test-rails-5");
        payload.put("project", project);

        when(syncProjectMapper.selectByProjectKey("ai/test-rails-5"))
                .thenThrow(new RuntimeException("Database error"));

        ResponseEntity<?> response = webhookController.handleGitLabWebhook("Push Hook", payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("status")).isEqualTo("error");
        assertThat(body.get("message")).isEqualTo("Database error");
    }

    /**
     * Test health check endpoint
     */
    @Test
    void testHealthCheck() {
        ResponseEntity<?> response = webhookController.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("status")).isEqualTo("ok");
        assertThat(body.get("service")).isEqualTo("webhook");
    }

    /**
     * Test webhook discovers and initializes new project
     */
    @Test
    void testHandleWebhook_NewProjectDiscovery() {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> project = new HashMap<>();
        project.put("path_with_namespace", "new/project");
        payload.put("project", project);
        payload.put("ref", "refs/heads/main");
        payload.put("before", "0000000000000000000000000000000000000000");
        payload.put("after", "abc123def456");

        // Mock project doesn't exist
        when(syncProjectMapper.selectByProjectKey("new/project")).thenReturn(null);

        // Mock project initialization
        SyncProject newProject = new SyncProject();
        newProject.setId(999L);
        newProject.setProjectKey("new/project");
        when(projectInitializationService.initializeProjectByPath("new/project")).thenReturn(999L);
        when(syncProjectMapper.selectById(999L)).thenReturn(newProject);

        // Mock task creation
        SyncTask newTask = new SyncTask();
        newTask.setId(500L);
        newTask.setSyncProjectId(999L);
        newTask.setTaskStatus(SyncTask.TaskStatus.WAITING);
        when(syncTaskMapper.selectOne(any())).thenReturn(newTask);
        when(syncTaskMapper.updateById(any())).thenReturn(1);

        ResponseEntity<?> response = webhookController.handleGitLabWebhook("Push Hook", payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("status")).isEqualTo("success");
        assertThat(body.get("triggered")).isEqualTo(true);
        assertThat(body.get("project")).isEqualTo("new/project");

        // Verify initialization was called
        verify(projectInitializationService).initializeProjectByPath("new/project");
        verify(syncTaskMapper).updateById(any());
    }

    /**
     * Test webhook handles new project initialization failure
     */
    @Test
    void testHandleWebhook_NewProjectInitializationFailed() {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> project = new HashMap<>();
        project.put("path_with_namespace", "new/project");
        payload.put("project", project);

        when(syncProjectMapper.selectByProjectKey("new/project")).thenReturn(null);
        when(projectInitializationService.initializeProjectByPath("new/project"))
                .thenThrow(new IllegalArgumentException("Project not found in source GitLab"));

        ResponseEntity<?> response = webhookController.handleGitLabWebhook("Push Hook", payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("status")).isEqualTo("error");
        assertThat(body.get("reason")).isEqualTo("initialization_failed");
        assertThat(body.get("message")).isEqualTo("Project not found in source GitLab");

        verify(syncTaskMapper, never()).updateById(any());
    }
}
