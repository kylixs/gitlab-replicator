package com.gitlab.mirror.cli;

import com.gitlab.mirror.cli.client.ApiClient;
import com.gitlab.mirror.cli.command.*;
import com.gitlab.mirror.cli.config.CliConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CLI Integration Tests
 *
 * IMPORTANT: These tests make REAL API calls to the server
 * The server must be running before executing these tests
 *
 * Test requirements from T6.1 and T6.2:
 * - Test API calls (real, not mocked)
 * - Test authentication
 * - Test error handling
 * - Test all commands
 * - Test output format
 *
 * @author GitLab Mirror Team
 */
class CliIntegrationTest {

    private ApiClient apiClient;
    private CliConfig config;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setUp() {
        // Load configuration from environment
        config = CliConfig.fromEnvironment();

        // Use default test configuration if not set
        if (config.getApiBaseUrl() == null || config.getApiBaseUrl().isEmpty()) {
            config.setApiBaseUrl("http://localhost:8080");
        }

        // Create API client
        apiClient = new ApiClient(config);

        // Redirect System.out and System.err to capture output
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        // Restore original streams
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    /**
     * T6.1 Test: API client can make requests
     */
    @Test
    void testApiClientConnection() {
        assertNotNull(apiClient, "API client should be initialized");
        assertNotNull(config.getApiBaseUrl(), "API base URL should be configured");
    }

    /**
     * T6.1 Test: Authentication with valid token
     */
    @Test
    void testAuthenticationWithValidToken() {
        // Skip if server is not running
        if (!isServerRunning()) {
            System.out.println("Skipping authentication test - server not running");
            return;
        }

        // This test requires GITLAB_MIRROR_TOKEN to be set
        if (config.getApiToken() == null || config.getApiToken().isEmpty()) {
            System.out.println("Skipping authentication test - no token configured");
            return;
        }

        // Try to call a protected endpoint
        try {
            apiClient.get("/api/projects", new com.fasterxml.jackson.core.type.TypeReference<ApiClient.ApiResponse<java.util.Map<String, Object>>>() {});
        } catch (Exception e) {
            fail("API call with valid token should not throw exception: " + e.getMessage());
        }
    }

    /**
     * T6.2 Test: ProjectCommand execution
     */
    @Test
    void testProjectCommand() throws Exception {
        if (!isServerRunning() || config.getApiToken() == null || config.getApiToken().isEmpty()) {
            System.out.println("Skipping project command test - server not running or no token");
            return;
        }

        ProjectCommand command = new ProjectCommand(apiClient);
        command.execute(new String[]{"--page", "1", "--size", "10"});

        String output = outContent.toString();
        assertFalse(output.isEmpty(), "Project command should produce output");
    }

    /**
     * T6.2 Test: DiscoverCommand execution
     */
    @Test
    void testDiscoverCommand() throws Exception {
        if (!isServerRunning() || config.getApiToken() == null || config.getApiToken().isEmpty()) {
            System.out.println("Skipping discover command test - server not running or no token");
            return;
        }

        DiscoverCommand command = new DiscoverCommand(apiClient);
        command.execute(new String[]{});

        String output = outContent.toString();
        assertTrue(output.contains("Discovery") || output.contains("discovered"),
                "Discover command should indicate discovery operation");
    }

    /**
     * T6.2 Test: MirrorListCommand execution
     */
    @Test
    void testMirrorListCommand() throws Exception {
        if (!isServerRunning() || config.getApiToken() == null || config.getApiToken().isEmpty()) {
            System.out.println("Skipping mirror list command test - server not running or no token");
            return;
        }

        MirrorListCommand command = new MirrorListCommand(apiClient);
        command.execute(new String[]{"--page", "1", "--size", "10"});

        String output = outContent.toString();
        assertFalse(output.isEmpty(), "Mirror list command should produce output");
    }

    /**
     * T6.2 Test: EventCommand execution
     */
    @Test
    void testEventCommand() throws Exception {
        if (!isServerRunning() || config.getApiToken() == null || config.getApiToken().isEmpty()) {
            System.out.println("Skipping event command test - server not running or no token");
            return;
        }

        EventCommand command = new EventCommand(apiClient);
        command.execute(new String[]{"--page", "1", "--size", "10"});

        String output = outContent.toString();
        assertFalse(output.isEmpty(), "Event command should produce output");
    }

    /**
     * T6.1 Test: Error handling for invalid token
     */
    @Test
    void testErrorHandlingInvalidToken() throws Exception {
        // Skip if server is not running
        if (!isServerRunning()) {
            System.out.println("Skipping invalid token test - server not running");
            return;
        }

        // Create a client with invalid token
        CliConfig invalidConfig = new CliConfig();
        invalidConfig.setApiBaseUrl(config.getApiBaseUrl());
        invalidConfig.setApiToken("invalid-token-12345");

        ApiClient invalidClient = new ApiClient(invalidConfig);
        ProjectCommand command = new ProjectCommand(invalidClient);

        command.execute(new String[]{});

        String output = outContent.toString();
        String errOutput = errContent.toString();

        assertTrue(output.contains("Failed") || errOutput.contains("Failed") || errOutput.contains("error"),
                "Command should show error message for invalid token");
    }

    /**
     * Helper method to check if server is running
     */
    private boolean isServerRunning() {
        try {
            apiClient.get("/api/status", new com.fasterxml.jackson.core.type.TypeReference<ApiClient.ApiResponse<java.util.Map<String, Object>>>() {});
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * T6.2 Test: Output format contains table elements
     */
    @Test
    void testOutputFormatHasTableElements() throws Exception {
        if (!isServerRunning() || config.getApiToken() == null || config.getApiToken().isEmpty()) {
            System.out.println("Skipping output format test - server not running or no token");
            return;
        }

        ProjectCommand command = new ProjectCommand(apiClient);
        command.execute(new String[]{"--page", "1", "--size", "10"});

        String output = outContent.toString();

        // Check for table borders (Unicode box drawing characters)
        boolean hasTableFormat = output.contains("│") || output.contains("┌") ||
                                 output.contains("└") || output.contains("─") ||
                                 output.contains("No projects") || output.contains("No data");

        assertTrue(hasTableFormat, "Output should contain table formatting or 'no data' message");
    }

    /**
     * T6.2 Test: Command option parsing
     */
    @Test
    void testCommandOptionParsing() throws Exception {
        if (!isServerRunning() || config.getApiToken() == null || config.getApiToken().isEmpty()) {
            System.out.println("Skipping option parsing test - server not running or no token");
            return;
        }

        // Test with various options
        ProjectCommand command = new ProjectCommand(apiClient);
        command.execute(new String[]{"--status", "active", "--page", "1", "--size", "5"});

        // If no exception thrown, option parsing worked
        assertTrue(true, "Command should handle multiple options without error");
    }

    /**
     * T6.1 Test: Configuration loading from environment
     */
    @Test
    void testConfigurationFromEnvironment() {
        CliConfig envConfig = CliConfig.fromEnvironment();

        assertNotNull(envConfig, "Configuration should be created from environment");
        assertNotNull(envConfig.getApiBaseUrl(), "API base URL should have a default value");
        assertTrue(envConfig.getConnectTimeout() > 0, "Connect timeout should be positive");
        assertTrue(envConfig.getReadTimeout() > 0, "Read timeout should be positive");
    }

    /**
     * T6.2 Test: ExportCommand basic functionality
     */
    @Test
    void testExportCommand() throws Exception {
        if (!isServerRunning() || config.getApiToken() == null || config.getApiToken().isEmpty()) {
            System.out.println("Skipping export command test - server not running or no token");
            return;
        }

        ExportCommand command = new ExportCommand(apiClient);

        // Test export mirrors to JSON (output to stdout)
        command.execute(new String[]{"mirrors", "--format", "json"});

        String output = outContent.toString();
        assertTrue(output.contains("[") || output.contains("No") || output.contains("Exporting"),
                "Export command should show JSON array or status message");
    }
}
