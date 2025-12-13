package com.gitlab.mirror.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * GitLab Mirror Service Application
 *
 * @author GitLab Mirror Team
 */
@SpringBootApplication
@EnableRetry
@EnableScheduling
public class GitLabMirrorApplication {

    public static void main(String[] args) {
        SpringApplication.run(GitLabMirrorApplication.class, args);
    }
}
