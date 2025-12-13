package com.gitlab.mirror.cli.config;

import lombok.Data;

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

    public static CliConfig fromEnvironment() {
        CliConfig config = new CliConfig();

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

        return config;
    }
}
