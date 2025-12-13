package com.gitlab.mirror.cli.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CliConfig
 *
 * @author GitLab Mirror Team
 */
class CliConfigTest {

    @TempDir
    Path tempDir;

    private Map<String, String> originalEnv;
    private String originalUserDir;

    @BeforeEach
    void setUp() {
        // Save original environment
        originalEnv = new HashMap<>(System.getenv());
        originalUserDir = System.getProperty("user.dir");
    }

    @AfterEach
    void tearDown() {
        // Restore original user.dir
        System.setProperty("user.dir", originalUserDir);

        // Note: Cannot restore environment variables in Java
        // Tests should be isolated and not depend on shared state
    }

    /**
     * Test loading from properties file in user home directory
     */
    @Test
    void testLoadFromUserHomeConfig() throws IOException {
        // Given - create ~/.gitlab-mirror/config
        Path configDir = tempDir.resolve(".gitlab-mirror");
        Files.createDirectories(configDir);
        Path configFile = configDir.resolve("config");

        String configContent = """
                GITLAB_MIRROR_API_URL=http://config-test:8080
                GITLAB_MIRROR_TOKEN=config-token-123
                GITLAB_MIRROR_CONNECT_TIMEOUT=10000
                GITLAB_MIRROR_READ_TIMEOUT=60000
                """;
        Files.writeString(configFile, configContent);

        // Set user.home to temp directory
        String originalHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());

        try {
            // When
            CliConfig config = CliConfig.fromEnvironment();

            // Then
            assertThat(config.getApiBaseUrl()).isEqualTo("http://config-test:8080");
            assertThat(config.getApiToken()).isEqualTo("config-token-123");
            assertThat(config.getConnectTimeout()).isEqualTo(10000);
            assertThat(config.getReadTimeout()).isEqualTo(60000);
        } finally {
            System.setProperty("user.home", originalHome);
        }
    }

    /**
     * Test loading from .env file in current directory
     */
    @Test
    void testLoadFromEnvFile() throws IOException {
        // Given - create .env file
        Path envFile = tempDir.resolve(".env");
        String envContent = """
                GITLAB_MIRROR_API_URL=http://env-test:8080
                GITLAB_MIRROR_TOKEN=env-token-456
                GITLAB_MIRROR_CONNECT_TIMEOUT=8000
                GITLAB_MIRROR_READ_TIMEOUT=40000
                """;
        Files.writeString(envFile, envContent);

        // Set user.dir to temp directory
        System.setProperty("user.dir", tempDir.toString());

        // When
        CliConfig config = CliConfig.fromEnvironment();

        // Then
        assertThat(config.getApiBaseUrl()).isEqualTo("http://env-test:8080");
        assertThat(config.getApiToken()).isEqualTo("env-token-456");
        assertThat(config.getConnectTimeout()).isEqualTo(8000);
        assertThat(config.getReadTimeout()).isEqualTo(40000);
    }

    /**
     * Test mapping SOURCE_GITLAB_TOKEN to GITLAB_MIRROR_TOKEN
     */
    @Test
    void testSourceGitlabTokenMapping() throws IOException {
        // Given - create .env file with SOURCE_GITLAB_TOKEN
        Path envFile = tempDir.resolve(".env");
        String envContent = """
                GITLAB_MIRROR_API_URL=http://localhost:8000
                SOURCE_GITLAB_TOKEN=source-token-789
                """;
        Files.writeString(envFile, envContent);

        // Set user.dir to temp directory
        System.setProperty("user.dir", tempDir.toString());

        // When
        CliConfig config = CliConfig.fromEnvironment();

        // Then - SOURCE_GITLAB_TOKEN should be mapped to GITLAB_MIRROR_TOKEN
        assertThat(config.getApiToken()).isEqualTo("source-token-789");
    }

    /**
     * Test GITLAB_MIRROR_TOKEN takes precedence over SOURCE_GITLAB_TOKEN
     */
    @Test
    void testGitlabMirrorTokenPrecedence() throws IOException {
        // Given - create .env file with both tokens
        Path envFile = tempDir.resolve(".env");
        String envContent = """
                SOURCE_GITLAB_TOKEN=source-token-789
                GITLAB_MIRROR_TOKEN=mirror-token-abc
                """;
        Files.writeString(envFile, envContent);

        // Set user.dir to temp directory
        System.setProperty("user.dir", tempDir.toString());

        // When
        CliConfig config = CliConfig.fromEnvironment();

        // Then - GITLAB_MIRROR_TOKEN should take precedence
        assertThat(config.getApiToken()).isEqualTo("mirror-token-abc");
    }

    /**
     * Test .env file with comments and empty lines
     */
    @Test
    void testEnvFileWithCommentsAndEmptyLines() throws IOException {
        // Given - create .env file with comments
        Path envFile = tempDir.resolve(".env");
        String envContent = """
                # Configuration for GitLab Mirror
                GITLAB_MIRROR_API_URL=http://localhost:8080

                # Token configuration
                GITLAB_MIRROR_TOKEN=test-token

                # Timeout settings
                GITLAB_MIRROR_CONNECT_TIMEOUT=5000
                """;
        Files.writeString(envFile, envContent);

        // Set user.dir to temp directory
        System.setProperty("user.dir", tempDir.toString());

        // When
        CliConfig config = CliConfig.fromEnvironment();

        // Then
        assertThat(config.getApiBaseUrl()).isEqualTo("http://localhost:8080");
        assertThat(config.getApiToken()).isEqualTo("test-token");
        assertThat(config.getConnectTimeout()).isEqualTo(5000);
    }

    /**
     * Test .env file in parent directory
     */
    @Test
    void testEnvFileInParentDirectory() throws IOException {
        // Given - create .env in parent and subdirectory structure
        Path envFile = tempDir.resolve(".env");
        String envContent = """
                GITLAB_MIRROR_API_URL=http://parent:8080
                GITLAB_MIRROR_TOKEN=parent-token
                """;
        Files.writeString(envFile, envContent);

        // Create subdirectory
        Path subDir = tempDir.resolve("sub1").resolve("sub2");
        Files.createDirectories(subDir);

        // Set user.dir to subdirectory
        System.setProperty("user.dir", subDir.toString());

        // When
        CliConfig config = CliConfig.fromEnvironment();

        // Then - should find .env in parent directory
        assertThat(config.getApiBaseUrl()).isEqualTo("http://parent:8080");
        assertThat(config.getApiToken()).isEqualTo("parent-token");
    }

    /**
     * Test default values when no configuration source available
     */
    @Test
    void testDefaultValues() {
        // Given - set user.dir to empty temp directory
        System.setProperty("user.dir", tempDir.toString());

        // Ensure no user config exists
        String originalHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.resolve("nonexistent").toString());

        try {
            // When
            CliConfig config = CliConfig.fromEnvironment();

            // Then - should use default values
            assertThat(config.getApiBaseUrl()).isEqualTo("http://localhost:8080");
            assertThat(config.getApiToken()).isNull();
            assertThat(config.getConnectTimeout()).isEqualTo(5000);
            assertThat(config.getReadTimeout()).isEqualTo(30000);
        } finally {
            System.setProperty("user.home", originalHome);
        }
    }

    /**
     * Test configuration priority: user config > .env > environment
     */
    @Test
    void testConfigurationPriority() throws IOException {
        // Given - create both user config and .env file
        Path configDir = tempDir.resolve(".gitlab-mirror");
        Files.createDirectories(configDir);
        Path configFile = configDir.resolve("config");

        String configContent = """
                GITLAB_MIRROR_API_URL=http://user-config:8080
                GITLAB_MIRROR_TOKEN=user-config-token
                """;
        Files.writeString(configFile, configContent);

        Path envFile = tempDir.resolve(".env");
        String envContent = """
                GITLAB_MIRROR_API_URL=http://env-file:8080
                GITLAB_MIRROR_TOKEN=env-file-token
                """;
        Files.writeString(envFile, envContent);

        // Set user.home and user.dir to temp directory
        String originalHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        System.setProperty("user.dir", tempDir.toString());

        try {
            // When
            CliConfig config = CliConfig.fromEnvironment();

            // Then - user config should take precedence
            assertThat(config.getApiBaseUrl()).isEqualTo("http://user-config:8080");
            assertThat(config.getApiToken()).isEqualTo("user-config-token");
        } finally {
            System.setProperty("user.home", originalHome);
        }
    }

    /**
     * Test partial configuration - only some values set
     */
    @Test
    void testPartialConfiguration() throws IOException {
        // Given - create .env file with only some values
        Path envFile = tempDir.resolve(".env");
        String envContent = """
                GITLAB_MIRROR_TOKEN=partial-token
                """;
        Files.writeString(envFile, envContent);

        // Set user.dir to temp directory
        System.setProperty("user.dir", tempDir.toString());

        // When
        CliConfig config = CliConfig.fromEnvironment();

        // Then - should have token but use default URL and timeouts
        assertThat(config.getApiToken()).isEqualTo("partial-token");
        assertThat(config.getApiBaseUrl()).isEqualTo("http://localhost:8080");
        assertThat(config.getConnectTimeout()).isEqualTo(5000);
        assertThat(config.getReadTimeout()).isEqualTo(30000);
    }

    /**
     * Test invalid timeout values - should keep defaults
     */
    @Test
    void testInvalidTimeoutValues() throws IOException {
        // Given - create .env file with invalid timeout
        Path envFile = tempDir.resolve(".env");
        String envContent = """
                GITLAB_MIRROR_CONNECT_TIMEOUT=not-a-number
                GITLAB_MIRROR_READ_TIMEOUT=invalid
                """;
        Files.writeString(envFile, envContent);

        // Set user.dir to temp directory
        System.setProperty("user.dir", tempDir.toString());

        // When
        CliConfig config = CliConfig.fromEnvironment();

        // Then - should keep default timeout values due to parse error
        assertThat(config.getConnectTimeout()).isEqualTo(5000);
        assertThat(config.getReadTimeout()).isEqualTo(30000);
    }

    /**
     * Test empty values in .env file
     */
    @Test
    void testEmptyValuesInEnvFile() throws IOException {
        // Given - create .env file with empty values
        Path envFile = tempDir.resolve(".env");
        String envContent = """
                GITLAB_MIRROR_API_URL=
                GITLAB_MIRROR_TOKEN=
                """;
        Files.writeString(envFile, envContent);

        // Set user.dir to temp directory
        System.setProperty("user.dir", tempDir.toString());

        // When
        CliConfig config = CliConfig.fromEnvironment();

        // Then - should use default values for empty strings
        assertThat(config.getApiBaseUrl()).isEqualTo("http://localhost:8080");
        assertThat(config.getApiToken()).isNull();
    }

    /**
     * Test .env file with spaces around values
     */
    @Test
    void testEnvFileWithSpaces() throws IOException {
        // Given - create .env file with spaces
        Path envFile = tempDir.resolve(".env");
        String envContent = """
                GITLAB_MIRROR_API_URL = http://spaced:8080
                GITLAB_MIRROR_TOKEN = spaced-token
                """;
        Files.writeString(envFile, envContent);

        // Set user.dir to temp directory
        System.setProperty("user.dir", tempDir.toString());

        // When
        CliConfig config = CliConfig.fromEnvironment();

        // Then - should trim spaces
        assertThat(config.getApiBaseUrl()).isEqualTo("http://spaced:8080");
        assertThat(config.getApiToken()).isEqualTo("spaced-token");
    }
}
