package com.gitlab.mirror.cli.formatter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Simple JSON parser for CLI output formatting
 *
 * @author GitLab Mirror Team
 */
public class JsonParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parse JSON string to JsonNode
     */
    public static JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Get string value from JSON path
     */
    public static String getString(JsonNode node, String... path) {
        JsonNode current = node;
        for (String key : path) {
            if (current == null || current.isNull()) {
                return "-";
            }
            current = current.get(key);
        }
        return current != null && !current.isNull() ? current.asText() : "-";
    }

    /**
     * Get int value from JSON path
     */
    public static int getInt(JsonNode node, String... path) {
        JsonNode current = node;
        for (String key : path) {
            if (current == null || current.isNull()) {
                return 0;
            }
            current = current.get(key);
        }
        return current != null && !current.isNull() ? current.asInt() : 0;
    }

    /**
     * Get long value from JSON path
     */
    public static long getLong(JsonNode node, String... path) {
        JsonNode current = node;
        for (String key : path) {
            if (current == null || current.isNull()) {
                return 0L;
            }
            current = current.get(key);
        }
        return current != null && !current.isNull() ? current.asLong() : 0L;
    }

    /**
     * Get double value from JSON path
     */
    public static double getDouble(JsonNode node, String... path) {
        JsonNode current = node;
        for (String key : path) {
            if (current == null || current.isNull()) {
                return 0.0;
            }
            current = current.get(key);
        }
        return current != null && !current.isNull() ? current.asDouble() : 0.0;
    }

    /**
     * Get boolean value from JSON path
     */
    public static boolean getBoolean(JsonNode node, String... path) {
        JsonNode current = node;
        for (String key : path) {
            if (current == null || current.isNull()) {
                return false;
            }
            current = current.get(key);
        }
        return current != null && !current.isNull() && current.asBoolean();
    }

    /**
     * Get array from JSON path
     */
    public static List<JsonNode> getArray(JsonNode node, String... path) {
        JsonNode current = node;
        for (String key : path) {
            if (current == null || current.isNull()) {
                return new ArrayList<>();
            }
            current = current.get(key);
        }

        List<JsonNode> result = new ArrayList<>();
        if (current != null && current.isArray()) {
            Iterator<JsonNode> elements = current.elements();
            while (elements.hasNext()) {
                result.add(elements.next());
            }
        }
        return result;
    }

    /**
     * Check if node exists and is not null
     */
    public static boolean has(JsonNode node, String key) {
        return node != null && node.has(key) && !node.get(key).isNull();
    }

    /**
     * Format number with thousands separator
     */
    public static String formatNumber(long number) {
        return String.format("%,d", number);
    }

    /**
     * Format priority with color
     */
    public static String formatPriority(String priority) {
        if (priority == null) {
            return "-";
        }
        switch (priority.toLowerCase()) {
            case "critical":
                return OutputFormatter.RED + "CRITICAL" + OutputFormatter.RESET;
            case "high":
                return OutputFormatter.YELLOW + "HIGH" + OutputFormatter.RESET;
            case "normal":
                return "NORMAL";
            case "low":
                return OutputFormatter.CYAN + "LOW" + OutputFormatter.RESET;
            default:
                return priority;
        }
    }

    /**
     * Format enabled status
     */
    public static String formatEnabled(boolean enabled) {
        return enabled
                ? OutputFormatter.GREEN + "Enabled" + OutputFormatter.RESET
                : OutputFormatter.RED + "Disabled" + OutputFormatter.RESET;
    }
}
