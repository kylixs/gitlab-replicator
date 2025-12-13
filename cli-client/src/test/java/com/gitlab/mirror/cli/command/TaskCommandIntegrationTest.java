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
 * Integration tests for TaskCommand
 *
 * @author GitLab Mirror Team
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaskCommandIntegrationTest {

    @Value("${cli.api.base-url:http://localhost:8080}")
    private String apiBaseUrl;

    private ApiClient apiClient;
    private TaskCommand command;

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
        command = new TaskCommand(apiClient);
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
    void testListTasks_Default() throws Exception {
        // When
        command.execute(new String[]{"list"});

        // Then
        String output = outContent.toString();
        assertThat(output).contains("success");
        assertThat(output).contains("data");
    }

    @Test
    void testListTasks_WithTypeFilter() throws Exception {
        // When
        command.execute(new String[]{"list", "--type=pull"});

        // Then
        String output = outContent.toString();
        assertThat(output).contains("success");
    }

    @Test
    void testListTasks_WithStatusFilter() throws Exception {
        // When
        command.execute(new String[]{"list", "--status=waiting"});

        // Then
        String output = outContent.toString();
        assertThat(output).contains("success");
    }

    @Test
    void testListTasks_WithPriorityFilter() throws Exception {
        // When
        command.execute(new String[]{"list", "--priority=high"});

        // Then
        String output = outContent.toString();
        assertThat(output).contains("success");
    }

    @Test
    void testListTasks_WithEnabledFilter() throws Exception {
        // When
        command.execute(new String[]{"list", "--enabled"});

        // Then
        String output = outContent.toString();
        assertThat(output).contains("success");
    }

    @Test
    void testListTasks_WithPagination() throws Exception {
        // When
        command.execute(new String[]{"list", "--page=1", "--size=10"});

        // Then
        String output = outContent.toString();
        assertThat(output).contains("success");
    }

    @Test
    void testStats() throws Exception {
        // When
        command.execute(new String[]{"stats"});

        // Then
        String output = outContent.toString();
        assertThat(output).contains("success");
        assertThat(output).containsAnyOf("totalTasks", "data");
    }

    @Test
    void testHelp() throws Exception {
        // When
        command.execute(new String[]{"help"});

        // Then
        String output = outContent.toString();
        assertThat(output).containsAnyOf("Usage", "list", "show", "stats");
    }

    @Test
    void testNoArgs() throws Exception {
        // When
        command.execute(new String[]{});

        // Then
        String output = outContent.toString();
        assertThat(output).containsAnyOf("Usage", "list");
    }
}
