package com.gitlab.mirror.server.api.controller;

import com.gitlab.mirror.server.api.dto.webhook.GitLabPushEvent;
import com.gitlab.mirror.server.api.dto.webhook.WebhookResponse;
import com.gitlab.mirror.server.config.properties.GitLabMirrorProperties;
import com.gitlab.mirror.server.service.WebhookEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WebhookController
 *
 * @author GitLab Mirror Team
 */
@ExtendWith(MockitoExtension.class)
class WebhookControllerTest {

    @Mock
    private GitLabMirrorProperties properties;

    @Mock
    private WebhookEventService webhookEventService;

    @InjectMocks
    private WebhookController webhookController;

    private GitLabPushEvent validEvent;

    @BeforeEach
    void setUp() {
        // Create valid push event
        validEvent = new GitLabPushEvent();
        GitLabPushEvent.ProjectInfo project = new GitLabPushEvent.ProjectInfo();
        project.setPathWithNamespace("test-group/test-project");
        project.setId(123L);
        project.setName("test-project");
        validEvent.setProject(project);
        validEvent.setRef("refs/heads/main");
        validEvent.setTotalCommitsCount(5);
    }

    @Test
    void testHandlePushEvent_Success() {
        // Given
        String validToken = "test-token";

        // When
        ResponseEntity<WebhookResponse> response = webhookController.handlePushEvent(validToken, validEvent);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("accepted");
        assertThat(response.getBody().getMessage()).contains("Event accepted");

        // Verify async processing was triggered
        verify(webhookEventService, times(1)).handlePushEventAsync(validEvent);
    }

    @Test
    void testHandlePushEvent_MissingToken() {
        // When
        ResponseEntity<WebhookResponse> response = webhookController.handlePushEvent(null, validEvent);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("rejected");
        assertThat(response.getBody().getMessage()).contains("Invalid or missing");

        // Verify no processing
        verify(webhookEventService, never()).handlePushEventAsync(any());
    }

    @Test
    void testHandlePushEvent_EmptyToken() {
        // When
        ResponseEntity<WebhookResponse> response = webhookController.handlePushEvent("", validEvent);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(webhookEventService, never()).handlePushEventAsync(any());
    }

    @Test
    void testHandlePushEvent_BlankToken() {
        // When
        ResponseEntity<WebhookResponse> response = webhookController.handlePushEvent("   ", validEvent);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(webhookEventService, never()).handlePushEventAsync(any());
    }

    @Test
    void testHandlePushEvent_NullEvent() {
        // Given
        String validToken = "test-token";

        // When
        ResponseEntity<WebhookResponse> response = webhookController.handlePushEvent(validToken, null);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("rejected");
        assertThat(response.getBody().getMessage()).contains("Invalid event payload");

        verify(webhookEventService, never()).handlePushEventAsync(any());
    }

    @Test
    void testHandlePushEvent_NullProject() {
        // Given
        String validToken = "test-token";
        GitLabPushEvent eventWithoutProject = new GitLabPushEvent();
        eventWithoutProject.setProject(null);

        // When
        ResponseEntity<WebhookResponse> response = webhookController.handlePushEvent(validToken, eventWithoutProject);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(webhookEventService, never()).handlePushEventAsync(any());
    }

    @Test
    void testHandlePushEvent_ServiceException() {
        // Given
        String validToken = "test-token";
        doThrow(new RuntimeException("Service error"))
                .when(webhookEventService).handlePushEventAsync(any());

        // When
        ResponseEntity<WebhookResponse> response = webhookController.handlePushEvent(validToken, validEvent);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("rejected");
        assertThat(response.getBody().getMessage()).contains("Internal server error");
    }

    @Test
    void testHandlePushEvent_ResponseTime() {
        // Given
        String validToken = "test-token";
        long startTime = System.currentTimeMillis();

        // When
        ResponseEntity<WebhookResponse> response = webhookController.handlePushEvent(validToken, validEvent);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        // Should respond quickly (< 100ms)
        assertThat(duration).isLessThan(100);
    }

    @Test
    void testHealth() {
        // When
        ResponseEntity<WebhookResponse> response = webhookController.health();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("accepted");
        assertThat(response.getBody().getMessage()).contains("healthy");
    }

    @Test
    void testHandlePushEvent_WithZeroCommits() {
        // Given
        String validToken = "test-token";
        validEvent.setTotalCommitsCount(0);

        // When
        ResponseEntity<WebhookResponse> response = webhookController.handlePushEvent(validToken, validEvent);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(webhookEventService, times(1)).handlePushEventAsync(validEvent);
    }

    @Test
    void testHandlePushEvent_WithNullCommits() {
        // Given
        String validToken = "test-token";
        validEvent.setTotalCommitsCount(null);

        // When
        ResponseEntity<WebhookResponse> response = webhookController.handlePushEvent(validToken, validEvent);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(webhookEventService, times(1)).handlePushEventAsync(validEvent);
    }
}
