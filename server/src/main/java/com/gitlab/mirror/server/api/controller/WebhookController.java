package com.gitlab.mirror.server.api.controller;

import com.gitlab.mirror.server.api.dto.webhook.GitLabPushEvent;
import com.gitlab.mirror.server.api.dto.webhook.WebhookResponse;
import com.gitlab.mirror.server.config.properties.GitLabMirrorProperties;
import com.gitlab.mirror.server.service.WebhookEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Webhook Controller
 * <p>
 * Receives GitLab Push Webhook events for real-time sync triggering
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final GitLabMirrorProperties properties;
    private final WebhookEventService webhookEventService;

    /**
     * Handle GitLab Push Event
     * <p>
     * Endpoint: POST /api/webhook/gitlab/push
     * Headers: X-Gitlab-Token (for authentication)
     * Body: GitLab Push Event JSON
     *
     * @param token GitLab Secret Token from header
     * @param event Push event payload
     * @return 202 Accepted if valid, 401 if unauthorized, 400 if invalid
     */
    @PostMapping("/gitlab/push")
    public ResponseEntity<WebhookResponse> handlePushEvent(
            @RequestHeader(value = "X-Gitlab-Token", required = false) String token,
            @RequestBody GitLabPushEvent event) {

        long startTime = System.currentTimeMillis();

        try {
            // 1. Verify Secret Token
            if (!verifyToken(token)) {
                log.warn("Webhook rejected: Invalid or missing token");
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(WebhookResponse.rejected("Invalid or missing authentication token"));
            }

            // 2. Validate event
            if (event == null || event.getProject() == null) {
                log.warn("Webhook rejected: Invalid event payload");
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(WebhookResponse.rejected("Invalid event payload"));
            }

            String projectKey = event.getProject().getPathWithNamespace();
            String ref = event.getRef();
            int commits = event.getTotalCommitsCount() != null ? event.getTotalCommitsCount() : 0;

            log.info("Webhook received: projectKey={}, ref={}, commits={}",
                    projectKey, ref, commits);

            // 3. Process async (to return quickly)
            webhookEventService.handlePushEventAsync(event);

            long duration = System.currentTimeMillis() - startTime;
            log.debug("Webhook processing time: {}ms", duration);

            // 4. Return 202 Accepted immediately
            return ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body(WebhookResponse.accepted("Event accepted for processing"));

        } catch (Exception e) {
            log.error("Failed to handle webhook event", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(WebhookResponse.rejected("Internal server error: " + e.getMessage()));
        }
    }

    /**
     * Verify Secret Token
     *
     * @param providedToken Token from request header
     * @return true if valid, false otherwise
     */
    private boolean verifyToken(String providedToken) {
        // TODO: Get secret token from configuration in T5.4
        // For now, accept any non-null token (development mode)
        if (providedToken == null || providedToken.isBlank()) {
            return false;
        }

        // In production, compare with configured secret token
        // String expectedToken = properties.getWebhook().getSecretToken();
        // return secureEquals(expectedToken, providedToken);

        // Development mode: accept any token
        log.debug("Token verification (dev mode): token present");
        return true;
    }

    /**
     * Constant-time string comparison (prevents timing attacks)
     *
     * @param expected Expected token
     * @param provided Provided token
     * @return true if equal
     */
    private boolean secureEquals(String expected, String provided) {
        if (expected == null || provided == null) {
            return false;
        }
        if (expected.length() != provided.length()) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < expected.length(); i++) {
            result |= expected.charAt(i) ^ provided.charAt(i);
        }
        return result == 0;
    }

    /**
     * Health check endpoint for webhook
     *
     * @return 200 OK
     */
    @GetMapping("/health")
    public ResponseEntity<WebhookResponse> health() {
        return ResponseEntity.ok(WebhookResponse.accepted("Webhook endpoint is healthy"));
    }
}
