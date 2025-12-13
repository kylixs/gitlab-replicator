package com.gitlab.mirror.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Task Executor Configuration
 * <p>
 * Configures thread pool for async task execution
 *
 * @author GitLab Mirror Team
 */
@Configuration
public class TaskExecutorConfig {

    @Bean(name = "syncTaskExecutor")
    public Executor syncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core pool size: number of threads always kept alive
        executor.setCorePoolSize(3);

        // Max pool size: maximum number of threads
        executor.setMaxPoolSize(10);

        // Queue capacity: number of tasks queued before creating new threads
        executor.setQueueCapacity(50);

        // Thread name prefix
        executor.setThreadNamePrefix("sync-exec-");

        // Rejection policy: caller runs when queue is full
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }
}
