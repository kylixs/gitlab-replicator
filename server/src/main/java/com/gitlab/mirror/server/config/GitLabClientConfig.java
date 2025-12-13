package com.gitlab.mirror.server.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.gitlab.mirror.server.client.GitLabApiClient;
import com.gitlab.mirror.server.client.RetryableGitLabClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * GitLab Client Configuration
 *
 * @author GitLab Mirror Team
 */
@Configuration
public class GitLabClientConfig {

    private final GitLabProperties gitLabProperties;

    public GitLabClientConfig(GitLabProperties gitLabProperties) {
        this.gitLabProperties = gitLabProperties;
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // Configure ObjectMapper to handle GitLab's datetime format
        ObjectMapper objectMapper = new ObjectMapper();
        JavaTimeModule javaTimeModule = new JavaTimeModule();

        objectMapper.registerModule(javaTimeModule);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);

        return builder
                .setConnectTimeout(Duration.ofMillis(gitLabProperties.getApi().getTimeout()))
                .setReadTimeout(Duration.ofMillis(gitLabProperties.getApi().getTimeout()))
                .additionalMessageConverters(converter)
                .build();
    }

    @Bean("sourceGitLabClient")
    public RetryableGitLabClient sourceGitLabClient(RestTemplate restTemplate) {
        return new RetryableGitLabClient(
                restTemplate,
                gitLabProperties.getSource().getUrl(),
                gitLabProperties.getSource().getToken(),
                gitLabProperties.getApi().getMaxRetries(),
                gitLabProperties.getApi().getRetryDelay()
        );
    }

    @Bean("targetGitLabClient")
    public RetryableGitLabClient targetGitLabClient(RestTemplate restTemplate) {
        return new RetryableGitLabClient(
                restTemplate,
                gitLabProperties.getTarget().getUrl(),
                gitLabProperties.getTarget().getToken(),
                gitLabProperties.getApi().getMaxRetries(),
                gitLabProperties.getApi().getRetryDelay()
        );
    }

    @Bean("sourceGitLabApiClient")
    public GitLabApiClient sourceGitLabApiClient(@Qualifier("sourceGitLabClient") RetryableGitLabClient client) {
        return new GitLabApiClient(client);
    }

    @Bean("targetGitLabApiClient")
    public GitLabApiClient targetGitLabApiClient(@Qualifier("targetGitLabClient") RetryableGitLabClient client) {
        return new GitLabApiClient(client);
    }
}
