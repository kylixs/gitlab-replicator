package com.gitlab.mirror.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * GitLab Configuration Properties
 *
 * @author GitLab Mirror Team
 */
@Data
@Component
@ConfigurationProperties(prefix = "gitlab")
public class GitLabProperties {

    /**
     * Source GitLab configuration
     */
    private GitLabInstance source = new GitLabInstance();

    /**
     * Target GitLab configuration
     */
    private GitLabInstance target = new GitLabInstance();

    /**
     * API configuration
     */
    private ApiConfig api = new ApiConfig();

    @Data
    public static class GitLabInstance {
        /**
         * GitLab URL
         */
        private String url;

        /**
         * GitLab access token
         */
        private String token;
    }

    @Data
    public static class ApiConfig {
        /**
         * Request timeout in milliseconds
         */
        private int timeout = 30000;

        /**
         * Maximum retry attempts
         */
        private int maxRetries = 3;

        /**
         * Initial retry delay in milliseconds
         */
        private long retryDelay = 1000;
    }
}
