package com.gitlab.mirror.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.Executor;

/**
 * Async Configuration
 * <p>
 * Configure thread pool for async audit logging
 *
 * @author GitLab Mirror Team
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Audit log executor
     */
    @Bean(name = "auditExecutor")
    public Executor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("audit-");
        executor.initialize();
        return executor;
    }
}
