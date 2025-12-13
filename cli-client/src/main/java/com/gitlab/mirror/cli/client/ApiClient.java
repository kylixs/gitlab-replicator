package com.gitlab.mirror.cli.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gitlab.mirror.cli.config.CliConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * API Client for communicating with GitLab Mirror REST API
 *
 * @author GitLab Mirror Team
 */
@Slf4j
public class ApiClient {
    private final CliConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ApiClient(CliConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getConnectTimeout()))
                .build();

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Send GET request
     */
    public <T> ApiResponse<T> get(String path, TypeReference<ApiResponse<T>> responseType) throws IOException, InterruptedException {
        HttpRequest request = buildRequest(path)
                .GET()
                .build();

        return executeRequest(request, responseType);
    }

    /**
     * Send GET request with query parameters
     */
    public <T> ApiResponse<T> get(String path, Map<String, String> queryParams, TypeReference<ApiResponse<T>> responseType) throws IOException, InterruptedException {
        String fullPath = buildPathWithQuery(path, queryParams);
        HttpRequest request = buildRequest(fullPath)
                .GET()
                .build();

        return executeRequest(request, responseType);
    }

    /**
     * Send POST request
     */
    public <T> ApiResponse<T> post(String path, TypeReference<ApiResponse<T>> responseType) throws IOException, InterruptedException {
        HttpRequest request = buildRequest(path)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return executeRequest(request, responseType);
    }

    /**
     * Send POST request with body
     */
    public <T> ApiResponse<T> post(String path, Object body, TypeReference<ApiResponse<T>> responseType) throws IOException, InterruptedException {
        String jsonBody = objectMapper.writeValueAsString(body);
        HttpRequest request = buildRequest(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return executeRequest(request, responseType);
    }

    /**
     * Build HTTP request with common headers
     */
    private HttpRequest.Builder buildRequest(String path) {
        String url = config.getApiBaseUrl() + path;

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(config.getReadTimeout()))
                .header("Accept", "application/json");

        // Add authentication token if configured
        if (config.getApiToken() != null && !config.getApiToken().isEmpty()) {
            builder.header("Authorization", "Bearer " + config.getApiToken());
        }

        return builder;
    }

    /**
     * Execute HTTP request and parse response
     */
    private <T> ApiResponse<T> executeRequest(HttpRequest request, TypeReference<ApiResponse<T>> responseType) throws IOException, InterruptedException {
        log.debug("Sending request: {} {}", request.method(), request.uri());

        HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        log.debug("Received response: status={}, body length={}", httpResponse.statusCode(), httpResponse.body().length());

        if (httpResponse.statusCode() >= 400) {
            // Try to parse error response
            try {
                return objectMapper.readValue(httpResponse.body(), responseType);
            } catch (Exception e) {
                // If parsing fails, create error response
                return createErrorResponse(httpResponse.statusCode(), httpResponse.body());
            }
        }

        return objectMapper.readValue(httpResponse.body(), responseType);
    }

    /**
     * Build path with query parameters
     */
    private String buildPathWithQuery(String path, Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return path;
        }

        StringBuilder sb = new StringBuilder(path);
        sb.append('?');

        boolean first = true;
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            if (!first) {
                sb.append('&');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }

        return sb.toString();
    }

    /**
     * Simple GET request returning raw JSON string
     */
    public String get(String path) throws IOException, InterruptedException {
        HttpRequest request = buildRequest(path)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }

        return response.body();
    }

    /**
     * Simple POST request returning raw JSON string
     */
    public String post(String path, String jsonBody) throws IOException, InterruptedException {
        HttpRequest request = buildRequest(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }

        return response.body();
    }

    /**
     * Simple POST request with no body, returning raw JSON string
     * Used for POST endpoints that only accept query parameters
     */
    public String postNoBody(String path) throws IOException, InterruptedException {
        HttpRequest request = buildRequest(path)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }

        return response.body();
    }

    /**
     * Simple PUT request returning raw JSON string
     */
    public String put(String path, String jsonBody) throws IOException, InterruptedException {
        HttpRequest request = buildRequest(path)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }

        return response.body();
    }

    /**
     * Create error response when parsing fails
     */
    @SuppressWarnings("unchecked")
    private <T> ApiResponse<T> createErrorResponse(int statusCode, String body) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setError(new ApiResponse.ErrorInfo(
                "HTTP_" + statusCode,
                "HTTP request failed with status " + statusCode,
                body.length() > 200 ? body.substring(0, 200) + "..." : body
        ));
        return response;
    }

    /**
     * Generic API Response wrapper
     */
    @lombok.Data
    public static class ApiResponse<T> {
        private boolean success;
        private T data;
        private String message;
        private ErrorInfo error;

        @lombok.Data
        @lombok.NoArgsConstructor
        @lombok.AllArgsConstructor
        public static class ErrorInfo {
            private String code;
            private String message;
            private String details;
        }
    }
}
