package com.gitlab.mirror.server.config;

import com.gitlab.mirror.server.config.properties.GitLabMirrorProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration Properties Enabler
 *
 * @author GitLab Mirror Team
 */
@Configuration
@EnableConfigurationProperties({
    GitLabMirrorProperties.class
})
public class PropertiesConfig {
}
