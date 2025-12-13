package com.gitlab.mirror.cli.command;

import com.gitlab.mirror.cli.client.ApiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PullSyncCommand
 *
 * @author GitLab Mirror Team
 */
@ExtendWith(MockitoExtension.class)
class PullSyncCommandTest {

    @Mock
    private ApiClient apiClient;

    private PullSyncCommand command;

    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;
    private PrintStream originalOut;
    private PrintStream originalErr;

    @BeforeEach
    void setUp() {
        command = new PullSyncCommand(apiClient);

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
        // Given
        when(apiClient.get(anyString()))
                .thenReturn("{\"success\":true,\"data\":{\"items\":[]}}");

        // When
        command.execute(new String[]{"list"});

        // Then
        verify(apiClient).get(contains("/api/pull-sync/config"));
        assertThat(outContent.toString()).contains("success");
    }

    @Test
    void testListConfigs_WithPriority() throws Exception {
        // Given
        when(apiClient.get(anyString()))
                .thenReturn("{\"success\":true,\"data\":{\"items\":[]}}");

        // When
        command.execute(new String[]{"list", "--priority=high"});

        // Then
        verify(apiClient).get(contains("priority=high"));
    }

    @Test
    void testListConfigs_WithEnabled() throws Exception {
        // Given
        when(apiClient.get(anyString()))
                .thenReturn("{\"success\":true,\"data\":{\"items\":[]}}");

        // When
        command.execute(new String[]{"list", "--enabled"});

        // Then
        verify(apiClient).get(contains("enabled=true"));
    }

    @Test
    void testListConfigs_WithDisabled() throws Exception {
        // Given
        when(apiClient.get(anyString()))
                .thenReturn("{\"success\":true,\"data\":{\"items\":[]}}");

        // When
        command.execute(new String[]{"list", "--disabled"});

        // Then
        verify(apiClient).get(contains("enabled=false"));
    }

    @Test
    void testListConfigs_WithPagination() throws Exception {
        // Given
        when(apiClient.get(anyString()))
                .thenReturn("{\"success\":true,\"data\":{\"items\":[]}}");

        // When
        command.execute(new String[]{"list", "--page=2", "--size=50"});

        // Then
        verify(apiClient).get(contains("page=2"));
        verify(apiClient).get(contains("size=50"));
    }

    @Test
    void testShowConfig_Success() throws Exception {
        // Given
        when(apiClient.get(anyString()))
                .thenReturn("{\"success\":true,\"data\":{\"id\":123}}");

        // When
        command.execute(new String[]{"show", "123"});

        // Then
        verify(apiClient).get("/api/pull-sync/config/123");
        assertThat(outContent.toString()).contains("success");
    }

    @Test
    void testShowConfig_MissingProjectId() throws Exception {
        // When
        command.execute(new String[]{"show"});

        // Then
        String errOutput = errContent.toString();
        assertThat(errOutput).containsAnyOf("Missing", "Usage", "required");
    }

    @Test
    void testUpdatePriority_Success() throws Exception {
        // Given
        when(apiClient.put(anyString(), anyString()))
                .thenReturn("{\"success\":true,\"message\":\"Priority updated\"}");

        // When
        command.execute(new String[]{"priority", "123", "critical"});

        // Then
        verify(apiClient).put(eq("/api/pull-sync/config/123/priority"), contains("critical"));
        String output = outContent.toString();
        assertThat(output).containsAnyOf("Priority updated", "success");
    }

    @Test
    void testUpdatePriority_MissingArgs() throws Exception {
        // When
        command.execute(new String[]{"priority", "123"});

        // Then
        String errOutput = errContent.toString();
        assertThat(errOutput).containsAnyOf("Missing", "Usage", "required");
    }

    @Test
    void testEnableConfig_Success() throws Exception {
        // Given
        when(apiClient.put(anyString(), anyString()))
                .thenReturn("{\"success\":true,\"message\":\"Enabled\"}");

        // When
        command.execute(new String[]{"enable", "123"});

        // Then
        verify(apiClient).put(eq("/api/pull-sync/config/123/enabled"), contains("true"));
        String output = outContent.toString();
        assertThat(output).containsAnyOf("Enabled", "success");
    }

    @Test
    void testDisableConfig_Success() throws Exception {
        // Given
        when(apiClient.put(anyString(), anyString()))
                .thenReturn("{\"success\":true,\"message\":\"Disabled\"}");

        // When
        command.execute(new String[]{"disable", "123"});

        // Then
        verify(apiClient).put(eq("/api/pull-sync/config/123/enabled"), contains("false"));
        String output = outContent.toString();
        assertThat(output).containsAnyOf("Disabled", "success");
    }

    @Test
    void testHelp() throws Exception {
        // When
        command.execute(new String[]{"help"});

        // Then
        String output = outContent.toString();
        assertThat(output).containsAnyOf("Usage", "list", "show", "priority");
        verify(apiClient, never()).get(anyString());
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
    void testUnknownSubcommand() throws Exception {
        // When
        command.execute(new String[]{"unknown"});

        // Then
        String errOutput = errContent.toString();
        assertThat(errOutput).contains("Unknown subcommand");
    }

    @Test
    void testNoArgs() throws Exception {
        // When
        command.execute(new String[]{});

        // Then
        String output = outContent.toString();
        assertThat(output).containsAnyOf("Usage", "list", "show");
    }

    @Test
    void testApiError_IOException() throws Exception {
        // Given
        when(apiClient.get(anyString()))
                .thenThrow(new java.io.IOException("Connection refused"));

        // When/Then
        try {
            command.execute(new String[]{"list"});
        } catch (Exception e) {
            assertThat(e).isInstanceOf(java.io.IOException.class);
        }
    }
}
