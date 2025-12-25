package com.gitlab.mirror.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Authentication Configuration Properties
 *
 * @author GitLab Mirror Team
 */
@Data
@Component
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    /**
     * Brute-force protection configuration
     */
    private BruteForce bruteForce = new BruteForce();

    @Data
    public static class BruteForce {
        /**
         * Time window for failure counting (minutes)
         */
        private int windowMinutes = 10;

        /**
         * Maximum failures per IP within window
         */
        private int maxIpFailures = 20;

        /**
         * Maximum failures per account within window
         */
        private int maxAccountFailures = 10;

        /**
         * Maximum lockout duration (seconds)
         */
        private int maxLockoutSeconds = 300;
    }
}
