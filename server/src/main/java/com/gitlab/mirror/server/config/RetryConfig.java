package com.gitlab.mirror.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

/**
 * Retry Configuration
 * <p>
 * Configures retry mechanism with exponential backoff for API calls
 *
 * @author GitLab Mirror Team
 */
@Configuration
public class RetryConfig {

    /**
     * Create retry template with exponential backoff
     * <p>
     * - Max attempts: 3
     * - Initial interval: 1000ms (1s)
     * - Multiplier: 2.0 (1s -> 2s -> 4s)
     * - Max interval: 10000ms (10s)
     */
    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // Retry policy: max 3 attempts
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        retryTemplate.setRetryPolicy(retryPolicy);

        // Backoff policy: exponential backoff
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000L);      // 1 second
        backOffPolicy.setMultiplier(2.0);             // Double each retry
        backOffPolicy.setMaxInterval(10000L);         // Max 10 seconds
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }
}
