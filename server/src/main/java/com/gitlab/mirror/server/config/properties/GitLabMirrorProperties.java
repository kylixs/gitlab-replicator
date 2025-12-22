package com.gitlab.mirror.server.config.properties;

import com.gitlab.mirror.server.util.TimeUnitParser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * GitLab Mirror Configuration Properties
 *
 * @author GitLab Mirror Team
 */
@Data
@Validated
@ConfigurationProperties(prefix = "gitlab.mirror")
public class GitLabMirrorProperties {

    /**
     * Source GitLab Configuration
     */
    @Valid
    @NotNull(message = "Source GitLab configuration is required")
    private GitLabInstance source;

    /**
     * Target GitLab Configuration
     */
    @Valid
    @NotNull(message = "Target GitLab configuration is required")
    private GitLabInstance target;

    /**
     * Sync Configuration
     */
    @Valid
    private SyncConfig sync = new SyncConfig();

    /**
     * Performance Configuration
     */
    @Valid
    private PerformanceConfig performance = new PerformanceConfig();

    /**
     * API Configuration
     */
    @Valid
    private ApiConfig api = new ApiConfig();

    /**
     * GitLab Instance Configuration
     */
    @Data
    public static class GitLabInstance {
        @NotBlank(message = "GitLab URL is required")
        private String url;

        @NotBlank(message = "GitLab access token is required")
        private String token;

        /**
         * Connection timeout in seconds
         */
        private Integer timeout = 30;

        /**
         * GitLab Mirror URL (for push mirror, accessible from source GitLab container)
         * If not set, falls back to url
         */
        private String mirrorUrl;
    }

    /**
     * Sync Configuration
     */
    @Data
    public static class SyncConfig {
        /**
         * Enable Push Mirror
         */
        private Boolean enabled = true;

        /**
         * Exclude archived projects
         */
        private Boolean excludeArchived = true;

        /**
         * Exclude empty repositories
         */
        private Boolean excludeEmpty = true;

        /**
         * Default sync method: push_mirror or pull_sync
         */
        private String defaultSyncMethod = "push_mirror";

        /**
         * Sync method configurations by group path
         */
        private java.util.List<SyncMethodConfig> syncMethods = new java.util.ArrayList<>();

        /**
         * Peak hours (format: "9-18")
         */
        private String peakHours = "9-18";

        /**
         * Peak concurrent limit
         */
        private Integer peakConcurrent = 3;

        /**
         * Off-peak concurrent limit
         */
        private Integer offPeakConcurrent = 8;

        /**
         * Pull sync interval configuration
         */
        @Valid
        private PullSyncIntervalConfig pullInterval = new PullSyncIntervalConfig();
    }

    /**
     * Pull Sync Interval Configuration
     */
    @Data
    public static class PullSyncIntervalConfig {
        /**
         * Interval for critical priority tasks
         * Supports time units: s(seconds), m(minutes), h(hours)
         * Examples: 60s, 1m, 2h
         * Default: 1m (60 seconds)
         */
        private String critical = "1m";

        /**
         * Interval for high priority tasks
         * Supports time units: s(seconds), m(minutes), h(hours)
         * Examples: 60s, 3m, 1h
         * Default: 3m (180 seconds)
         */
        private String high = "3m";

        /**
         * Interval for normal priority tasks
         * Supports time units: s(seconds), m(minutes), h(hours)
         * Examples: 60s, 3m, 1h
         * Default: 3m (180 seconds)
         */
        private String normal = "3m";

        /**
         * Interval for low priority tasks
         * Supports time units: s(seconds), m(minutes), h(hours)
         * Examples: 600s, 10m, 1h
         * Default: 10m (600 seconds)
         */
        private String low = "10m";

        /**
         * Get critical interval in seconds
         */
        public int getCriticalSeconds() {
            return TimeUnitParser.parseToSeconds(critical, 60);
        }

        /**
         * Get high interval in seconds
         */
        public int getHighSeconds() {
            return TimeUnitParser.parseToSeconds(high, 180);
        }

        /**
         * Get normal interval in seconds
         */
        public int getNormalSeconds() {
            return TimeUnitParser.parseToSeconds(normal, 180);
        }

        /**
         * Get low interval in seconds
         */
        public int getLowSeconds() {
            return TimeUnitParser.parseToSeconds(low, 600);
        }
    }

    /**
     * Sync Method Configuration for specific group paths
     */
    @Data
    public static class SyncMethodConfig {
        /**
         * Group path pattern (supports wildcards like "critical/*")
         */
        @NotBlank(message = "Group path is required")
        private String groupPath;

        /**
         * Sync method: push_mirror or pull_sync
         */
        @NotBlank(message = "Sync method is required")
        private String method;
    }

    /**
     * Performance Configuration
     */
    @Data
    public static class PerformanceConfig {
        /**
         * Project discovery concurrency
         */
        private Integer projectDiscoveryConcurrency = 5;

        /**
         * Mirror setup concurrency
         */
        private Integer mirrorSetupConcurrency = 10;

        /**
         * Mirror polling batch size
         */
        private Integer mirrorPollingBatchSize = 50;

        /**
         * API rate limit delay in milliseconds
         */
        private Integer apiRateLimitDelay = 100;
    }

    /**
     * API Configuration
     */
    @Data
    public static class ApiConfig {
        /**
         * API authentication key
         * Independent from GitLab access tokens
         */
        @NotBlank(message = "API key is required")
        private String key;
    }
}
