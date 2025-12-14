package com.gitlab.mirror.cli.command;

import com.fasterxml.jackson.core.type.TypeReference;
import com.gitlab.mirror.cli.client.ApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Diff Command Test
 *
 * @author GitLab Mirror Team
 */
@ExtendWith(MockitoExtension.class)
class DiffCommandTest {

    @Mock
    private ApiClient apiClient;

    private DiffCommand diffCommand;
    private ByteArrayOutputStream outputStream;
    private PrintStream originalOut;

    @BeforeEach
    void setUp() {
        diffCommand = new DiffCommand(apiClient);
        outputStream = new ByteArrayOutputStream();
        originalOut = System.out;
        System.setOut(new PrintStream(outputStream));
    }

    @Test
    void testExecute_ListMode() throws Exception {
        // Given
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("page", 1);
        responseData.put("total", 2L);

        Map<String, Object> diff1 = new HashMap<>();
        diff1.put("projectKey", "test/project1");
        diff1.put("status", "SYNCED");

        Map<String, Object> diff2 = new HashMap<>();
        diff2.put("projectKey", "test/project2");
        diff2.put("status", "OUTDATED");

        responseData.put("items", Arrays.asList(diff1, diff2));

        ApiClient.ApiResponse<Map<String, Object>> response = new ApiClient.ApiResponse<>();
        response.setSuccess(true);
        response.setData(responseData);

        when(apiClient.get(eq("/api/sync/diffs"), anyMap(), any(TypeReference.class)))
                .thenReturn(response);

        // When
        diffCommand.execute(new String[]{});

        // Then
        verify(apiClient, times(1)).get(eq("/api/sync/diffs"), anyMap(), any(TypeReference.class));
        String output = outputStream.toString();
        assertTrue(output.contains("test/project1"));
        assertTrue(output.contains("test/project2"));
    }

    @Test
    void testExecute_SingleProjectMode() throws Exception {
        // Given
        Map<String, Object> diffData = new HashMap<>();
        diffData.put("projectKey", "test/project");
        diffData.put("status", "SYNCED");
        diffData.put("checkedAt", "2025-12-15T00:00:00");

        Map<String, Object> source = new HashMap<>();
        source.put("commitSha", "abc123");
        source.put("commitCount", 100);
        source.put("branchCount", 5);
        source.put("sizeBytes", 1024000L);
        source.put("lastActivityAt", "2025-12-15T00:00:00");
        source.put("defaultBranch", "main");
        diffData.put("source", source);

        Map<String, Object> target = new HashMap<>();
        target.put("commitSha", "abc123");
        target.put("commitCount", 100);
        target.put("branchCount", 5);
        target.put("sizeBytes", 1024000L);
        target.put("lastActivityAt", "2025-12-15T00:00:00");
        target.put("defaultBranch", "main");
        diffData.put("target", target);

        Map<String, Object> diffDetails = new HashMap<>();
        diffDetails.put("shaMatches", true);
        diffDetails.put("commitDelta", 0);
        diffDetails.put("branchDelta", 0);
        diffDetails.put("sizeDeltaBytes", 0L);
        diffDetails.put("delayMinutes", 0L);
        diffData.put("diff", diffDetails);

        ApiClient.ApiResponse<Map<String, Object>> response = new ApiClient.ApiResponse<>();
        response.setSuccess(true);
        response.setData(diffData);

        when(apiClient.get(eq("/api/sync/diff"), anyMap(), any(TypeReference.class)))
                .thenReturn(response);

        // When
        diffCommand.execute(new String[]{"test/project"});

        // Then
        verify(apiClient, times(1)).get(eq("/api/sync/diff"), anyMap(), any(TypeReference.class));
        String output = outputStream.toString();
        assertTrue(output.contains("test/project"));
        assertTrue(output.contains("SYNCED"));
        assertTrue(output.contains("abc123"));
    }

    @Test
    void testExecute_WithStatusFilter() throws Exception {
        // Given
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("page", 1);
        responseData.put("total", 1L);
        responseData.put("items", new ArrayList<>());

        ApiClient.ApiResponse<Map<String, Object>> response = new ApiClient.ApiResponse<>();
        response.setSuccess(true);
        response.setData(responseData);

        when(apiClient.get(eq("/api/sync/diffs"), anyMap(), any(TypeReference.class)))
                .thenReturn(response);

        // When
        diffCommand.execute(new String[]{"--status=OUTDATED"});

        // Then
        verify(apiClient, times(1)).get(eq("/api/sync/diffs"), argThat(params ->
                params.containsKey("status") && params.get("status").equals("OUTDATED")
        ), any(TypeReference.class));
    }

    @Test
    void testExecute_ApiError() throws Exception {
        // Given
        ApiClient.ApiResponse<Map<String, Object>> response = new ApiClient.ApiResponse<>();
        response.setSuccess(false);
        ApiClient.ErrorInfo error = new ApiClient.ErrorInfo();
        error.setMessage("API Error");
        response.setError(error);

        when(apiClient.get(anyString(), anyMap(), any(TypeReference.class)))
                .thenReturn(response);

        // When
        diffCommand.execute(new String[]{"test/project"});

        // Then
        String output = outputStream.toString();
        assertTrue(output.contains("Failed to fetch diff"));
        assertTrue(output.contains("API Error"));
    }

    @Test
    void testExecute_NullData() throws Exception {
        // Given
        ApiClient.ApiResponse<Map<String, Object>> response = new ApiClient.ApiResponse<>();
        response.setSuccess(true);
        response.setData(null);

        when(apiClient.get(anyString(), anyMap(), any(TypeReference.class)))
                .thenReturn(response);

        // When
        diffCommand.execute(new String[]{"test/project"});

        // Then
        String output = outputStream.toString();
        assertTrue(output.contains("No diff data available"));
    }

    @Test
    void testExecute_WithPagination() throws Exception {
        // Given
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("page", 2);
        responseData.put("total", 50L);
        responseData.put("items", new ArrayList<>());

        ApiClient.ApiResponse<Map<String, Object>> response = new ApiClient.ApiResponse<>();
        response.setSuccess(true);
        response.setData(responseData);

        when(apiClient.get(eq("/api/sync/diffs"), anyMap(), any(TypeReference.class)))
                .thenReturn(response);

        // When
        diffCommand.execute(new String[]{"--page=2", "--size=10"});

        // Then
        verify(apiClient, times(1)).get(eq("/api/sync/diffs"), argThat(params ->
                params.get("page").equals("2") && params.get("size").equals("10")
        ), any(TypeReference.class));
    }

    void tearDown() {
        System.setOut(originalOut);
    }
}
