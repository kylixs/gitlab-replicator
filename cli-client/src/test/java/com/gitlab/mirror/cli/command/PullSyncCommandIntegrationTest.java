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
 * Integration tests for PullSyncCommand
 * Tests actual API calls against running server
 *
 * @author GitLab Mirror Team
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PullSyncCommandIntegrationTest {

    @Value("${cli.api.base-url:http://localhost:8080}")
    private String apiBaseUrl;

    private ApiClient apiClient;
    private PullSyncCommand command;

    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;
    private PrintStream originalOut;
    private PrintStream originalErr;

    @BeforeAll
    void setUpAll() {
        CliConfig config = new CliConfig();
        config.setApiBaseUrl(apiBaseUrl);
        config.setApiToken("test-token");
        config.setConnectTimeout(5000);
        config.setReadTimeout(30000);

        apiClient = new ApiClient(config);
        command = new PullSyncCommand(apiClient);
    }

    @BeforeEach
    void setUp() {
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        originalOut = System.out;
        originalErr = System.err;
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void testListConfigs_Default() throws Exception {
        // When
        command.execute(new String[]{"list"});

        // Then
        String output = outContent.toString();
        assertThat(output).contains("success");
        assertThat(output).contains("data");
    }

    @Test
    void testListConfigs_WithPriority() throws Exception {
        // When
        command.execute(new String[]{"list", "--priority=high"});

        // Then
        String output = outContent.toString();
        assertThat(output).contains("success");
    }

    @Test
    void testListConfigs_WithEnabled() throws Exception {
        // When
        command.execute(new String[]{"list", "--enabled"});

        // Then
        String output = outContent.toString();
        assertThat(output).contains("success");
    }

    @Test
    void testListConfigs_WithPagination() throws Exception {
        // When
        command.execute(new String[]{"list", "--page=1", "--size=10"});

        // Then
        String output = outContent.toString();
        assertThat(output).contains("success");
    }

    @Test
    void testShowConfig_NotFound() throws Exception {
        // When
        command.execute(new String[]{"show", "99999"});

        // Then - Should get error from server
        String output = outContent.toString();
        // May contain success:false or error response
        assertThat(output).isNotBlank();
    }

    @Test
    void testHelp() throws Exception {
        // When
        command.execute(new String[]{"help"});

        // Then
        String output = outContent.toString();
        assertThat(output).containsAnyOf("Usage", "list", "show", "priority", "enable", "disable");
    }

    @Test
    void testHelpFlag() throws Exception {
        // When
        command.execute(new String[]{"--help"});

        // Then
        String output = outContent.toString();
        assertThat(output).containsAnyOf("Usage", "list");
    }

    @Test
    void testUnknownSubcommand() {
        // When/Then
        Assertions.assertThrows(Exception.class, () -> {
            command.execute(new String[]{"unknown"});
        });
    }

    @Test
    void testNoArgs() throws Exception {
        // When
        command.execute(new String[]{});

        // Then
        String output = outContent.toString();
        assertThat(output).containsAnyOf("Usage", "list", "show");
    }
}
