package com.gitlab.mirror.server.config.properties;

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
}
