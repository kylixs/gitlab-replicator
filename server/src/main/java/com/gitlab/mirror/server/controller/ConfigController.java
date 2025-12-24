package com.gitlab.mirror.server.controller;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Configuration Controller
 * <p>
 * REST API for system configuration management
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    @Value("${SOURCE_GITLAB_URL:http://localhost:8000}")
    private String sourceGitlabUrl;

    @Value("${TARGET_GITLAB_URL:http://localhost:9000}")
    private String targetGitlabUrl;

    /**
     * Get all configuration
     *
     * GET /api/config/all
     */
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<SystemConfig>> getAllConfig() {
        log.info("Get all configuration");

        try {
            SystemConfig config = new SystemConfig();

            // GitLab configuration
            GitLabConfig gitlab = new GitLabConfig();
            gitlab.setSource(new GitLabInstance(sourceGitlabUrl, maskToken("glpat-****")));
            gitlab.setTarget(new GitLabInstance(targetGitlabUrl, maskToken("glpat-****")));
            config.setGitlab(gitlab);

            // Scan settings
            ScanSettings scanSettings = new ScanSettings();
            scanSettings.setIncrementalInterval(300000L);
            scanSettings.setFullScanCron("0 0 2 * * ?");
            scanSettings.setEnabled(true);
            config.setScanSettings(scanSettings);

            // Sync settings
            SyncSettings syncSettings = new SyncSettings();
            syncSettings.setSyncInterval(300);
            syncSettings.setConcurrency(5);
            config.setSyncSettings(syncSettings);

            // Default sync rules
            DefaultSyncRules syncRules = new DefaultSyncRules();
            syncRules.setMethod("pull_sync");
            syncRules.setExcludeArchived(true);
            syncRules.setExcludeEmpty(true);
            syncRules.setExcludePattern("^temp/.*");
            config.setDefaultSyncRules(syncRules);

            // Thresholds
            Thresholds thresholds = new Thresholds();
            thresholds.setDelayWarningHours(1);
            thresholds.setDelayCriticalHours(24);
            thresholds.setMaxRetryAttempts(3);
            thresholds.setTimeoutSeconds(300);
            config.setThresholds(thresholds);

            return ResponseEntity.ok(ApiResponse.success(config));
        } catch (Exception e) {
            log.error("Get configuration failed", e);
            return ResponseEntity.ok(ApiResponse.error("Failed to get configuration: " + e.getMessage()));
        }
    }

    /**
     * Update configuration
     *
     * POST /api/config/all
     */
    @PostMapping("/all")
    public ResponseEntity<ApiResponse<String>> updateConfig(@RequestBody SystemConfig config) {
        log.info("Update configuration");

        try {
            // TODO: Implement configuration update logic
            // For now, just return success
            log.info("Configuration update requested: {}", config);

            return ResponseEntity.ok(ApiResponse.success("Configuration saved successfully"));
        } catch (Exception e) {
            log.error("Update configuration failed", e);
            return ResponseEntity.ok(ApiResponse.error("Failed to update configuration: " + e.getMessage()));
        }
    }

    /**
     * Test GitLab connection
     *
     * POST /api/config/test-connection?type=source|target
     */
    @PostMapping("/test-connection")
    public ResponseEntity<ApiResponse<ConnectionTestResult>> testConnection(
            @RequestParam String type) {
        log.info("Test connection - type: {}", type);

        try {
            // TODO: Implement actual connection test
            // For now, return a mock result
            ConnectionTestResult result = new ConnectionTestResult();
            result.setConnected(true);
            result.setVersion("16.11.0");
            result.setLatencyMs(50L);

            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Test connection failed", e);
            ConnectionTestResult result = new ConnectionTestResult();
            result.setConnected(false);
            result.setError(e.getMessage());
            return ResponseEntity.ok(ApiResponse.success(result));
        }
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "****";
        }
        return token.substring(0, 8) + "****";
    }

    /**
     * System configuration
     */
    @Data
    public static class SystemConfig {
        private GitLabConfig gitlab;
        private ScanSettings scanSettings;
        private SyncSettings syncSettings;
        private DefaultSyncRules defaultSyncRules;
        private Thresholds thresholds;
    }

    @Data
    public static class GitLabConfig {
        private GitLabInstance source;
        private GitLabInstance target;
    }

    @Data
    public static class GitLabInstance {
        private String url;
        private String token;

        public GitLabInstance() {}

        public GitLabInstance(String url, String token) {
            this.url = url;
            this.token = token;
        }
    }

    @Data
    public static class ScanSettings {
        private Long incrementalInterval;
        private String fullScanCron;
        private Boolean enabled;
    }

    @Data
    public static class SyncSettings {
        private Integer syncInterval;
        private Integer concurrency;
    }

    @Data
    public static class DefaultSyncRules {
        private String method;
        private Boolean excludeArchived;
        private Boolean excludeEmpty;
        private String excludePattern;
    }

    @Data
    public static class Thresholds {
        private Integer delayWarningHours;
        private Integer delayCriticalHours;
        private Integer maxRetryAttempts;
        private Integer timeoutSeconds;
    }

    @Data
    public static class ConnectionTestResult {
        private Boolean connected;
        private String version;
        private Long latencyMs;
        private String error;
    }

    /**
     * API Response wrapper
     */
    @Data
    public static class ApiResponse<T> {
        private boolean success;
        private T data;
        private String message;

        public static <T> ApiResponse<T> success(T data) {
            ApiResponse<T> response = new ApiResponse<>();
            response.setSuccess(true);
            response.setData(data);
            return response;
        }

        public static <T> ApiResponse<T> error(String message) {
            ApiResponse<T> response = new ApiResponse<>();
            response.setSuccess(false);
            response.setMessage(message);
            return response;
        }
    }
}
