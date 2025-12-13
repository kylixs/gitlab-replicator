package com.gitlab.mirror.server.config;

import com.gitlab.mirror.server.config.properties.GitLabMirrorProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Configuration Test
 *
 * @author GitLab Mirror Team
 */
@SpringBootTest
@ActiveProfiles("test")
class ConfigurationTest {

    @Autowired(required = false)
    private GitLabMirrorProperties properties;

    @Autowired(required = false)
    private DataSource dataSource;

    @Test
    void testConfigurationPropertiesLoaded() {
        // Properties may not be loaded in test profile due to missing required fields
        // This test validates the configuration structure
        assertThat(properties).isNotNull();
    }

    @Test
    void testDataSourceConnection() throws Exception {
        // Verify database connection can be established
        assertThat(dataSource).isNotNull();
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection).isNotNull();
            assertThat(connection.isValid(5)).isTrue();
        }
    }
}
