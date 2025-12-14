package com.gitlab.mirror.server.client;

import com.gitlab.mirror.server.config.GitLabProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

/**
 * Retryable GitLab Client with Rate Limit Handling
 *
 * @author GitLab Mirror Team
 */
@Slf4j
public class RetryableGitLabClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String token;
    private final int maxRetries;
    private final long initialRetryDelay;

    public RetryableGitLabClient(RestTemplate restTemplate, String baseUrl, String token,
                                  int maxRetries, long initialRetryDelay) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.token = token;
        this.maxRetries = maxRetries;
        this.initialRetryDelay = initialRetryDelay;
    }

    /**
     * Execute GET request with retry
     */
    public <T> T get(String path, Class<T> responseType) {
        return executeWithRetry(() -> {
            long startTime = System.currentTimeMillis();
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            String urlString = baseUrl + path;

            log.debug("GET {}", sanitizeUrl(urlString));
            // Use URI instead of String to avoid double encoding
            java.net.URI uri = java.net.URI.create(urlString);
            ResponseEntity<T> response = restTemplate.exchange(uri, HttpMethod.GET, entity, responseType);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[API-PERF] GET {} - {}ms", sanitizeUrl(path), duration);

            return response.getBody();
        });
    }

    /**
     * Execute POST request with retry
     */
    public <T, R> R post(String path, T body, Class<R> responseType) {
        return executeWithRetry(() -> {
            HttpHeaders headers = createHeaders();
            HttpEntity<T> entity = new HttpEntity<>(body, headers);
            String urlString = baseUrl + path;

            log.debug("POST {}", sanitizeUrl(urlString));
            java.net.URI uri = java.net.URI.create(urlString);
            ResponseEntity<R> response = restTemplate.exchange(uri, HttpMethod.POST, entity, responseType);
            return response.getBody();
        });
    }

    /**
     * Execute PUT request with retry
     */
    public <T, R> R put(String path, T body, Class<R> responseType) {
        return executeWithRetry(() -> {
            HttpHeaders headers = createHeaders();
            HttpEntity<T> entity = new HttpEntity<>(body, headers);
            String urlString = baseUrl + path;

            log.debug("PUT {}", sanitizeUrl(urlString));
            java.net.URI uri = java.net.URI.create(urlString);
            ResponseEntity<R> response = restTemplate.exchange(uri, HttpMethod.PUT, entity, responseType);
            return response.getBody();
        });
    }

    /**
     * Execute DELETE request with retry
     */
    public void delete(String path) {
        executeWithRetry(() -> {
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            String urlString = baseUrl + path;

            log.debug("DELETE {}", sanitizeUrl(urlString));
            java.net.URI uri = java.net.URI.create(urlString);
            restTemplate.exchange(uri, HttpMethod.DELETE, entity, Void.class);
            return null;
        });
    }

    /**
     * Test connection
     */
    public boolean testConnection() {
        try {
            get("/api/v4/version", Object.class);
            return true;
        } catch (Exception e) {
            log.error("Connection test failed", e);
            return false;
        }
    }

    /**
     * Execute with retry and rate limit handling
     */
    private <T> T executeWithRetry(RetryableOperation<T> operation) {
        int attempt = 0;
        long delay = initialRetryDelay;

        while (true) {
            try {
                return operation.execute();
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429) {
                    // Rate limit exceeded
                    if (attempt >= maxRetries) {
                        log.error("Max retries exceeded for rate limit");
                        throw new GitLabClientException("Rate limit exceeded after " + maxRetries + " retries", e);
                    }

                    long waitTime = getRetryAfter(e.getResponseHeaders());
                    if (waitTime == 0) {
                        waitTime = delay;
                        delay *= 2; // Exponential backoff
                    }

                    log.warn("Rate limit hit (429), waiting {} ms before retry {}/{}",
                            waitTime, attempt + 1, maxRetries);
                    sleep(waitTime);
                    attempt++;
                } else {
                    // Other 4xx errors are not retryable
                    throw new GitLabClientException(
                            "GitLab API error: " + e.getStatusCode() + " - " + e.getMessage(),
                            e.getStatusCode().value(),
                            e.getResponseBodyAsString()
                    );
                }
            } catch (HttpServerErrorException e) {
                // 5xx errors are retryable
                if (attempt >= maxRetries) {
                    log.error("Max retries exceeded for server error");
                    throw new GitLabClientException(
                            "Server error after " + maxRetries + " retries: " + e.getMessage(),
                            e.getStatusCode().value(),
                            e.getResponseBodyAsString()
                    );
                }

                log.warn("Server error ({}), retrying {}/{} after {} ms",
                        e.getStatusCode(), attempt + 1, maxRetries, delay);
                sleep(delay);
                delay *= 2; // Exponential backoff
                attempt++;
            } catch (Exception e) {
                // Network errors are retryable
                if (attempt >= maxRetries) {
                    log.error("Max retries exceeded for network error");
                    throw new GitLabClientException("Network error after " + maxRetries + " retries", e);
                }

                log.warn("Network error, retrying {}/{} after {} ms: {}",
                        attempt + 1, maxRetries, delay, e.getMessage());
                sleep(delay);
                delay *= 2; // Exponential backoff
                attempt++;
            }
        }
    }

    /**
     * Get Retry-After header value in milliseconds
     */
    private long getRetryAfter(HttpHeaders headers) {
        String retryAfter = headers.getFirst("Retry-After");
        if (retryAfter != null) {
            try {
                return Long.parseLong(retryAfter) * 1000; // Convert seconds to milliseconds
            } catch (NumberFormatException e) {
                log.warn("Invalid Retry-After header: {}", retryAfter);
            }
        }
        return 0;
    }

    /**
     * Sleep for specified milliseconds
     */
    private void sleep(long milliseconds) {
        try {
            TimeUnit.MILLISECONDS.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitLabClientException("Interrupted during retry", e);
        }
    }

    /**
     * Create HTTP headers with authentication
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("PRIVATE-TOKEN", token);
        return headers;
    }

    /**
     * Sanitize URL for logging (remove sensitive info)
     */
    private String sanitizeUrl(String url) {
        // Don't log token in URL
        return url;
    }

    @FunctionalInterface
    private interface RetryableOperation<T> {
        T execute();
    }
}
