package com.gitlab.mirror.cli.config;

import lombok.Data;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * CLI Configuration
 *
 * @author GitLab Mirror Team
 */
@Data
public class CliConfig {
    private String apiBaseUrl = "http://localhost:8080";
    private String apiToken;
    private int connectTimeout = 5000;
    private int readTimeout = 30000;

    /**
     * Load configuration from .env file or properties file
     * Priority: ~/.gitlab-mirror/config > .env file > environment variables
     */
    public static CliConfig fromEnvironment() {
        CliConfig config = new CliConfig();

        // Try to load from user's home directory config file
        Path userConfigPath = Paths.get(System.getProperty("user.home"), ".gitlab-mirror", "config");
        if (Files.exists(userConfigPath)) {
            loadFromPropertiesFile(config, userConfigPath);
            return config;
        }

        // Try to load from .env file in current directory or project root
        Path envPath = findEnvFile();
        if (envPath != null) {
            loadFromEnvFile(config, envPath);
            return config;
        }

        // Fallback to environment variables
        loadFromEnvironmentVariables(config);

        return config;
    }

    /**
     * Find .env file in current directory or parent directories
     */
    private static Path findEnvFile() {
        Path current = Paths.get(System.getProperty("user.dir"));

        // Check current directory and up to 3 parent directories
        for (int i = 0; i < 4; i++) {
            Path envPath = current.resolve(".env");
            if (Files.exists(envPath)) {
                return envPath;
            }

            Path parent = current.getParent();
            if (parent == null) {
                break;
            }
            current = parent;
        }

        return null;
    }

    /**
     * Load configuration from properties file
     */
    private static void loadFromPropertiesFile(CliConfig config, Path path) {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            props.load(fis);

            String apiUrl = props.getProperty("GITLAB_MIRROR_API_URL");
            if (apiUrl != null && !apiUrl.isEmpty()) {
                config.setApiBaseUrl(apiUrl);
            }

            String token = props.getProperty("GITLAB_MIRROR_TOKEN");
            if (token != null && !token.isEmpty()) {
                config.setApiToken(token);
            }

            String connectTimeout = props.getProperty("GITLAB_MIRROR_CONNECT_TIMEOUT");
            if (connectTimeout != null && !connectTimeout.isEmpty()) {
                config.setConnectTimeout(Integer.parseInt(connectTimeout));
            }

            String readTimeout = props.getProperty("GITLAB_MIRROR_READ_TIMEOUT");
            if (readTimeout != null && !readTimeout.isEmpty()) {
                config.setReadTimeout(Integer.parseInt(readTimeout));
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("Warning: Failed to load config from " + path + ": " + e.getMessage());
        }
    }

    /**
     * Load configuration from .env file
     */
    private static void loadFromEnvFile(CliConfig config, Path path) {
        try {
            Properties props = new Properties();

            // Read .env file line by line
            Files.lines(path).forEach(line -> {
                line = line.trim();
                // Skip comments and empty lines
                if (line.isEmpty() || line.startsWith("#")) {
                    return;
                }

                // Parse KEY=VALUE format
                int equalIndex = line.indexOf('=');
                if (equalIndex > 0) {
                    String key = line.substring(0, equalIndex).trim();
                    String value = line.substring(equalIndex + 1).trim();
                    props.setProperty(key, value);
                }
            });

            // Map SOURCE_GITLAB_TOKEN to GITLAB_MIRROR_TOKEN if not set
            if (props.getProperty("SOURCE_GITLAB_TOKEN") != null &&
                props.getProperty("GITLAB_MIRROR_TOKEN") == null) {
                props.setProperty("GITLAB_MIRROR_TOKEN", props.getProperty("SOURCE_GITLAB_TOKEN"));
            }

            String apiUrl = props.getProperty("GITLAB_MIRROR_API_URL");
            if (apiUrl != null && !apiUrl.isEmpty()) {
                config.setApiBaseUrl(apiUrl);
            }

            String token = props.getProperty("GITLAB_MIRROR_TOKEN");
            if (token != null && !token.isEmpty()) {
                config.setApiToken(token);
            }

            String connectTimeout = props.getProperty("GITLAB_MIRROR_CONNECT_TIMEOUT");
            if (connectTimeout != null && !connectTimeout.isEmpty()) {
                config.setConnectTimeout(Integer.parseInt(connectTimeout));
            }

            String readTimeout = props.getProperty("GITLAB_MIRROR_READ_TIMEOUT");
            if (readTimeout != null && !readTimeout.isEmpty()) {
                config.setReadTimeout(Integer.parseInt(readTimeout));
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("Warning: Failed to load config from " + path + ": " + e.getMessage());
        }
    }

    /**
     * Load configuration from environment variables (fallback)
     */
    private static void loadFromEnvironmentVariables(CliConfig config) {
        String apiUrl = System.getenv("GITLAB_MIRROR_API_URL");
        if (apiUrl != null && !apiUrl.isEmpty()) {
            config.setApiBaseUrl(apiUrl);
        }

        String token = System.getenv("GITLAB_MIRROR_TOKEN");
        if (token != null && !token.isEmpty()) {
            config.setApiToken(token);
        }

        String connectTimeout = System.getenv("GITLAB_MIRROR_CONNECT_TIMEOUT");
        if (connectTimeout != null && !connectTimeout.isEmpty()) {
            config.setConnectTimeout(Integer.parseInt(connectTimeout));
        }

        String readTimeout = System.getenv("GITLAB_MIRROR_READ_TIMEOUT");
        if (readTimeout != null && !readTimeout.isEmpty()) {
            config.setReadTimeout(Integer.parseInt(readTimeout));
        }
    }
}
