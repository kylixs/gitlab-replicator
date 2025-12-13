package com.gitlab.mirror.cli.command;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gitlab.mirror.cli.client.ApiClient;
import com.gitlab.mirror.cli.formatter.OutputFormatter;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

/**
 * Data export command
 *
 * @author GitLab Mirror Team
 */
public class ExportCommand {
    private final ApiClient apiClient;
    private final ObjectMapper objectMapper;

    public ExportCommand(ApiClient apiClient) {
        this.apiClient = apiClient;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public void execute(String[] args) throws Exception {
        if (args.length == 0) {
            OutputFormatter.printError("Export type is required");
            OutputFormatter.printInfo("Usage: gitlab-mirror export <mirrors|events> [--format json|csv] [--output file]");
            return;
        }

        String exportType = args[0];
        String format = "json";
        String outputFile = null;

        // Parse options
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--format":
                    if (i + 1 < args.length) {
                        format = args[++i];
                    }
                    break;
                case "--output":
                    if (i + 1 < args.length) {
                        outputFile = args[++i];
                    }
                    break;
            }
        }

        switch (exportType) {
            case "mirrors":
                exportMirrors(format, outputFile);
                break;
            case "events":
                exportEvents(format, outputFile);
                break;
            default:
                OutputFormatter.printError("Unknown export type: " + exportType);
                OutputFormatter.printInfo("Supported types: mirrors, events");
        }
    }

    private void exportMirrors(String format, String outputFile) throws Exception {
        OutputFormatter.printInfo("Exporting mirrors...");

        // Fetch all mirrors (pagination)
        Map<String, String> params = new HashMap<>();
        params.put("page", "1");
        params.put("size", "1000");

        ApiClient.ApiResponse<Map<String, Object>> response = apiClient.get(
                "/api/mirrors",
                params,
                new TypeReference<ApiClient.ApiResponse<Map<String, Object>>>() {}
        );

        if (!response.isSuccess()) {
            OutputFormatter.printError("Failed to fetch mirrors: " + response.getError().getMessage());
            return;
        }

        Map<String, Object> data = response.getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");

        if (items == null || items.isEmpty()) {
            OutputFormatter.printWarning("No mirrors to export");
            return;
        }

        exportData(items, format, outputFile);
        OutputFormatter.printSuccess("Exported " + items.size() + " mirrors");
    }

    private void exportEvents(String format, String outputFile) throws Exception {
        OutputFormatter.printInfo("Exporting events...");

        // Fetch all events (pagination)
        Map<String, String> params = new HashMap<>();
        params.put("page", "1");
        params.put("size", "1000");

        ApiClient.ApiResponse<Map<String, Object>> response = apiClient.get(
                "/api/events",
                params,
                new TypeReference<ApiClient.ApiResponse<Map<String, Object>>>() {}
        );

        if (!response.isSuccess()) {
            OutputFormatter.printError("Failed to fetch events: " + response.getError().getMessage());
            return;
        }

        Map<String, Object> data = response.getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");

        if (items == null || items.isEmpty()) {
            OutputFormatter.printWarning("No events to export");
            return;
        }

        exportData(items, format, outputFile);
        OutputFormatter.printSuccess("Exported " + items.size() + " events");
    }

    private void exportData(List<Map<String, Object>> items, String format, String outputFile) throws Exception {
        if ("json".equalsIgnoreCase(format)) {
            exportAsJson(items, outputFile);
        } else if ("csv".equalsIgnoreCase(format)) {
            exportAsCsv(items, outputFile);
        } else {
            OutputFormatter.printError("Unsupported format: " + format);
            OutputFormatter.printInfo("Supported formats: json, csv");
        }
    }

    private void exportAsJson(List<Map<String, Object>> items, String outputFile) throws Exception {
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(items);

        if (outputFile != null) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
                writer.write(json);
            }
            OutputFormatter.printInfo("Output written to: " + outputFile);
        } else {
            System.out.println(json);
        }
    }

    private void exportAsCsv(List<Map<String, Object>> items, String outputFile) throws Exception {
        if (items.isEmpty()) {
            return;
        }

        // Get all unique keys
        Set<String> allKeys = new LinkedHashSet<>();
        for (Map<String, Object> item : items) {
            allKeys.addAll(item.keySet());
        }

        StringBuilder csv = new StringBuilder();

        // Write header
        csv.append(String.join(",", allKeys)).append("\n");

        // Write rows
        for (Map<String, Object> item : items) {
            List<String> values = new ArrayList<>();
            for (String key : allKeys) {
                Object value = item.get(key);
                String strValue = value != null ? String.valueOf(value) : "";
                // Escape commas and quotes
                strValue = strValue.replace("\"", "\"\"");
                if (strValue.contains(",") || strValue.contains("\"") || strValue.contains("\n")) {
                    strValue = "\"" + strValue + "\"";
                }
                values.add(strValue);
            }
            csv.append(String.join(",", values)).append("\n");
        }

        if (outputFile != null) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
                writer.write(csv.toString());
            }
            OutputFormatter.printInfo("Output written to: " + outputFile);
        } else {
            System.out.print(csv);
        }
    }
}
