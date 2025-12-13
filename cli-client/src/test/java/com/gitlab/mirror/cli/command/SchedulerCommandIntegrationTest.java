package com.gitlab.mirror.cli.command;

import com.gitlab.mirror.cli.client.ApiClient;
import com.gitlab.mirror.cli.config.CliConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for SchedulerCommand
 *
 * @author GitLab Mirror Team
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchedulerCommandIntegrationTest {

    @Value("${cli.api.base-url:http://localhost:8080}")
    private String apiBaseUrl;

    private ApiClient apiClient;
    private SchedulerCommand command;

    private ByteArrayOutputStream outContent;
    private PrintStream originalOut;

    @BeforeAll
    void setUpAll() {
        CliConfig config = new CliConfig();
        config.setApiBaseUrl(apiBaseUrl);
        config.setApiToken("test-token");
        config.setConnectTimeout(5000);
        config.setReadTimeout(30000);

        apiClient = new ApiClient(config);
        command = new SchedulerCommand(apiClient);
    }

    @BeforeEach
    void setUp() {
        outContent = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    void testStatus() throws Exception {
        // When
        command.execute(new String[]{"status"});

        // Then
        String output = outContent.toString();
        assertThat(output).contains("success");
        assertThat(output).containsAnyOf("enabled", "isPeakHours", "data");
    }

    @Test
    void testTrigger_Default() throws Exception {
        // When
        command.execute(new String[]{"trigger"});

        // Then
        String output = outContent.toString();
        assertThat(output).contains("success");
    }

    @Test
    void testTrigger_WithType() throws Exception {
        // When
        command.execute(new String[]{"trigger", "--type=pull"});

        // Then
        String output = outContent.toString();
        assertThat(output).contains("success");
    }

    @Test
    void testMetrics() throws Exception {
        // When
        command.execute(new String[]{"metrics"});

        // Then
        String output = outContent.toString();
        assertThat(output).contains("success");
        assertThat(output).containsAnyOf("totalScheduled", "data");
    }

    @Test
    void testHelp() throws Exception {
        // When
        command.execute(new String[]{"help"});

        // Then
        String output = outContent.toString();
        assertThat(output).containsAnyOf("Usage", "status", "trigger", "metrics");
    }

    @Test
    void testHelpFlag() throws Exception {
        // When
        command.execute(new String[]{"--help"});

        // Then
        String output = outContent.toString();
        assertThat(output).containsAnyOf("Usage", "status");
    }

    @Test
    void testNoArgs() throws Exception {
        // When
        command.execute(new String[]{});

        // Then
        String output = outContent.toString();
        assertThat(output).containsAnyOf("Usage", "status");
    }
}
