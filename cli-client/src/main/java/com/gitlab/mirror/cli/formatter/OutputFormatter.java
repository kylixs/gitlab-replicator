package com.gitlab.mirror.cli.formatter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Output Formatter for CLI
 * Provides formatted output with ANSI colors and table layouts
 *
 * @author GitLab Mirror Team
 */
public class OutputFormatter {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ANSI Color Codes
    public static final String RESET = "\u001B[0m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String CYAN = "\u001B[36m";
    public static final String BOLD = "\u001B[1m";

    /**
     * Print success message
     */
    public static void printSuccess(String message) {
        System.out.println(GREEN + "✓ " + message + RESET);
    }

    /**
     * Print error message
     */
    public static void printError(String message) {
        System.err.println(RED + "✗ " + message + RESET);
    }

    /**
     * Print warning message
     */
    public static void printWarning(String message) {
        System.out.println(YELLOW + "⚠ " + message + RESET);
    }

    /**
     * Print info message
     */
    public static void printInfo(String message) {
        System.out.println(CYAN + "ℹ " + message + RESET);
    }

    /**
     * Print section header
     */
    public static void printHeader(String title) {
        System.out.println();
        System.out.println(BOLD + CYAN + title + RESET);
        System.out.println(CYAN + "━".repeat(Math.min(title.length(), 80)) + RESET);
    }

    /**
     * Print table
     */
    public static void printTable(String[] headers, List<String[]> rows) {
        if (rows == null || rows.isEmpty()) {
            printWarning("No data to display");
            return;
        }

        // Calculate column widths (excluding ANSI color codes)
        int[] widths = new int[headers.length];
        for (int i = 0; i < headers.length; i++) {
            widths[i] = getVisibleLength(headers[i]);
        }

        for (String[] row : rows) {
            for (int i = 0; i < Math.min(row.length, widths.length); i++) {
                if (row[i] != null) {
                    widths[i] = Math.max(widths[i], getVisibleLength(row[i]));
                }
            }
        }

        // Print top border
        printTableBorder(widths, "┌", "┬", "┐");

        // Print headers
        printTableRow(headers, widths, true);

        // Print header separator
        printTableBorder(widths, "├", "┼", "┤");

        // Print rows
        for (String[] row : rows) {
            printTableRow(row, widths, false);
        }

        // Print bottom border
        printTableBorder(widths, "└", "┴", "┘");
    }

    /**
     * Print table border
     */
    private static void printTableBorder(int[] widths, String left, String middle, String right) {
        System.out.print(left);
        for (int i = 0; i < widths.length; i++) {
            System.out.print("─".repeat(widths[i] + 2));
            if (i < widths.length - 1) {
                System.out.print(middle);
            }
        }
        System.out.println(right);
    }

    /**
     * Print table row
     */
    private static void printTableRow(String[] row, int[] widths, boolean isHeader) {
        System.out.print("│");
        for (int i = 0; i < widths.length; i++) {
            String cell = i < row.length && row[i] != null ? row[i] : "";
            // Calculate padding needed (accounting for ANSI codes)
            int visibleLength = getVisibleLength(cell);
            int paddingNeeded = widths[i] - visibleLength;
            String formatted = " " + cell + " ".repeat(paddingNeeded + 1);

            if (isHeader) {
                System.out.print(BOLD + formatted + RESET);
            } else {
                System.out.print(formatted);
            }
            System.out.print("│");
        }
        System.out.println();
    }

    /**
     * Get visible length of string (excluding ANSI color codes)
     */
    private static int getVisibleLength(String str) {
        if (str == null) {
            return 0;
        }
        // Remove ANSI escape codes to get actual visible length
        String withoutAnsi = str.replaceAll("\u001B\\[[0-9;]*[mGKH]", "");
        return withoutAnsi.length();
    }

    /**
     * Print key-value pairs
     */
    public static void printKeyValue(String key, String value) {
        System.out.printf("%-25s %s%n", key + ":", value);
    }

    /**
     * Print key-value with color
     */
    public static void printKeyValue(String key, String value, String color) {
        System.out.printf("%-25s %s%s%s%n", key + ":", color, value, RESET);
    }

    /**
     * Format LocalDateTime
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "-";
        }
        return dateTime.format(DATE_FORMATTER);
    }

    /**
     * Format ISO 8601 datetime string to local timezone
     * Handles both UTC (with Z suffix) and non-timezone formats
     *
     * @param isoDateTimeStr ISO 8601 datetime string (e.g., "2025-12-15T06:06:40.563027Z" or "2025-12-14T05:02:26")
     * @return Formatted datetime in local timezone (e.g., "2025-12-15 14:06:40")
     */
    public static String formatDateTime(String isoDateTimeStr) {
        if (isoDateTimeStr == null || isoDateTimeStr.isEmpty() || isoDateTimeStr.equals("-")) {
            return "-";
        }

        try {
            // Try parsing as ZonedDateTime (handles UTC with Z suffix)
            if (isoDateTimeStr.contains("Z") || isoDateTimeStr.contains("+") ||
                (isoDateTimeStr.contains("-") && isoDateTimeStr.lastIndexOf("-") > 10)) {
                ZonedDateTime utcDateTime = ZonedDateTime.parse(isoDateTimeStr);
                // Convert to system default timezone
                ZonedDateTime localZonedDateTime = utcDateTime.withZoneSameInstant(ZoneId.systemDefault());
                return localZonedDateTime.format(DATE_FORMATTER);
            } else {
                // Parse as LocalDateTime (no timezone info, assume already local)
                LocalDateTime localDateTime = LocalDateTime.parse(isoDateTimeStr);
                return localDateTime.format(DATE_FORMATTER);
            }
        } catch (DateTimeParseException e) {
            // If parsing fails, return original string
            return isoDateTimeStr;
        }
    }

    /**
     * Format status with color
     */
    public static String formatStatus(String status) {
        if (status == null) {
            return "-";
        }

        switch (status.toLowerCase()) {
            case "active":
            case "synced":
            case "success":
            case "finished":
                return GREEN + "✓ " + status + RESET;
            case "failed":
            case "error":
                return RED + "✗ " + status + RESET;
            case "syncing":
            case "pending":
            case "running":
                return YELLOW + "⟳ " + status + RESET;
            default:
                return status;
        }
    }

    /**
     * Truncate long strings
     */
    public static String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }

    /**
     * Print progress bar
     */
    public static void printProgress(int current, int total, String message) {
        int barLength = 40;
        int progress = (int) ((double) current / total * barLength);

        System.out.print("\r" + message + " [");
        for (int i = 0; i < barLength; i++) {
            if (i < progress) {
                System.out.print("=");
            } else if (i == progress) {
                System.out.print(">");
            } else {
                System.out.print(" ");
            }
        }
        System.out.print("] " + current + "/" + total);

        if (current == total) {
            System.out.println();
        }
    }

    /**
     * Print JSON response (pretty printed)
     */
    public static void printJson(String json) {
        if (json == null || json.isEmpty()) {
            printWarning("No data");
            return;
        }
        // Simple pretty print by adding indentation
        System.out.println(prettyPrintJson(json));
    }

    /**
     * Simple JSON pretty printer
     */
    private static String prettyPrintJson(String json) {
        StringBuilder result = new StringBuilder();
        int indent = 0;
        boolean inString = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inString = !inString;
            }

            if (!inString) {
                if (c == '{' || c == '[') {
                    result.append(c).append('\n');
                    indent++;
                    result.append("  ".repeat(indent));
                } else if (c == '}' || c == ']') {
                    result.append('\n');
                    indent--;
                    result.append("  ".repeat(indent));
                    result.append(c);
                } else if (c == ',') {
                    result.append(c).append('\n');
                    result.append("  ".repeat(indent));
                } else if (c == ':') {
                    result.append(c).append(' ');
                } else if (!Character.isWhitespace(c)) {
                    result.append(c);
                }
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }
}
