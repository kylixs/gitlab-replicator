package com.gitlab.mirror.server.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Retryable GitLab Client Test
 *
 * @author GitLab Mirror Team
 */
class RetryableGitLabClientTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static final String TOKEN = "test-token";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY = 100; // Use short delay for tests

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private RetryableGitLabClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        client = new RetryableGitLabClient(restTemplate, BASE_URL, TOKEN, MAX_RETRIES, RETRY_DELAY);
    }

    @Test
    void testGetSuccess() {
        // Setup
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("version", "15.0.0");

        mockServer.expect(requestTo(BASE_URL + "/api/v4/version"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("PRIVATE-TOKEN", TOKEN))
                .andRespond(withSuccess("{\"version\":\"15.0.0\"}", MediaType.APPLICATION_JSON));

        // Execute
        Object response = client.get("/api/v4/version", Object.class);

        // Verify
        assertThat(response).isNotNull();
        mockServer.verify();
    }

    @Test
    void testPostSuccess() {
        // Setup
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("name", "test-project");

        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("id", 123);

        mockServer.expect(requestTo(BASE_URL + "/api/v4/projects"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("PRIVATE-TOKEN", TOKEN))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{\"id\":123}", MediaType.APPLICATION_JSON));

        // Execute
        Object response = client.post("/api/v4/projects", requestBody, Object.class);

        // Verify
        assertThat(response).isNotNull();
        mockServer.verify();
    }

    @Test
    void testRateLimitRetry() {
        // Setup - First request returns 429, second succeeds
        mockServer.expect(requestTo(BASE_URL + "/api/v4/version"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header("Retry-After", "1")); // 1 second

        mockServer.expect(requestTo(BASE_URL + "/api/v4/version"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"version\":\"15.0.0\"}", MediaType.APPLICATION_JSON));

        // Execute
        Object response = client.get("/api/v4/version", Object.class);

        // Verify
        assertThat(response).isNotNull();
        mockServer.verify();
    }

    @Test
    void testRateLimitMaxRetriesExceeded() {
        // Setup - All requests return 429
        for (int i = 0; i <= MAX_RETRIES; i++) {
            mockServer.expect(requestTo(BASE_URL + "/api/v4/version"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        }

        // Execute & Verify
        assertThatThrownBy(() -> client.get("/api/v4/version", Object.class))
                .isInstanceOf(GitLabClientException.class)
                .hasMessageContaining("Rate limit exceeded");

        mockServer.verify();
    }

    @Test
    void testServerErrorRetry() {
        // Setup - First request returns 500, second succeeds
        mockServer.expect(requestTo(BASE_URL + "/api/v4/version"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        mockServer.expect(requestTo(BASE_URL + "/api/v4/version"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"version\":\"15.0.0\"}", MediaType.APPLICATION_JSON));

        // Execute
        Object response = client.get("/api/v4/version", Object.class);

        // Verify
        assertThat(response).isNotNull();
        mockServer.verify();
    }

    @Test
    void testServerErrorMaxRetriesExceeded() {
        // Setup - All requests return 500
        for (int i = 0; i <= MAX_RETRIES; i++) {
            mockServer.expect(requestTo(BASE_URL + "/api/v4/version"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withServerError());
        }

        // Execute & Verify
        assertThatThrownBy(() -> client.get("/api/v4/version", Object.class))
                .isInstanceOf(GitLabClientException.class)
                .hasMessageContaining("Server error after");

        mockServer.verify();
    }

    @Test
    void testClientErrorNotRetried() {
        // Setup - 404 error should not be retried
        mockServer.expect(requestTo(BASE_URL + "/api/v4/projects/999"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .body("{\"message\":\"404 Project Not Found\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        // Execute & Verify
        assertThatThrownBy(() -> client.get("/api/v4/projects/999", Object.class))
                .isInstanceOf(GitLabClientException.class)
                .hasMessageContaining("404");

        mockServer.verify();
    }

    @Test
    void testConnectionTest() {
        // Setup
        mockServer.expect(requestTo(BASE_URL + "/api/v4/version"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"version\":\"15.0.0\"}", MediaType.APPLICATION_JSON));

        // Execute
        boolean result = client.testConnection();

        // Verify
        assertThat(result).isTrue();
        mockServer.verify();
    }

    @Test
    void testConnectionTestFailure() {
        // Setup
        mockServer.expect(requestTo(BASE_URL + "/api/v4/version"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        mockServer.expect(requestTo(BASE_URL + "/api/v4/version"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        mockServer.expect(requestTo(BASE_URL + "/api/v4/version"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        mockServer.expect(requestTo(BASE_URL + "/api/v4/version"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        // Execute
        boolean result = client.testConnection();

        // Verify
        assertThat(result).isFalse();
        mockServer.verify();
    }

    @Test
    void testDeleteSuccess() {
        // Setup
        mockServer.expect(requestTo(BASE_URL + "/api/v4/projects/123"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("PRIVATE-TOKEN", TOKEN))
                .andRespond(withSuccess());

        // Execute
        client.delete("/api/v4/projects/123");

        // Verify
        mockServer.verify();
    }

    @Test
    void testPutSuccess() {
        // Setup
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("name", "updated-project");

        mockServer.expect(requestTo(BASE_URL + "/api/v4/projects/123"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("PRIVATE-TOKEN", TOKEN))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{\"id\":123,\"name\":\"updated-project\"}", MediaType.APPLICATION_JSON));

        // Execute
        Object response = client.put("/api/v4/projects/123", requestBody, Object.class);

        // Verify
        assertThat(response).isNotNull();
        mockServer.verify();
    }
}
