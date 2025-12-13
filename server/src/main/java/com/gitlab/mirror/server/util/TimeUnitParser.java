package com.gitlab.mirror.server.util;

/**
 * Time Unit Parser Utility
 * <p>
 * Parses time interval strings with units (s, m, h) to seconds
 *
 * @author GitLab Mirror Team
 */
public class TimeUnitParser {

    /**
     * Parse interval string to seconds
     * <p>
     * Supported units:
     * - s: seconds
     * - m: minutes
     * - h: hours
     * <p>
     * Examples:
     * - "60s" -> 60
     * - "3m" -> 180
     * - "1h" -> 3600
     * - "60" -> 60 (default to seconds if no unit specified)
     *
     * @param interval Interval string (e.g., "60s", "3m", "1h")
     * @return Seconds
     * @throws IllegalArgumentException if interval format is invalid
     */
    public static int parseToSeconds(String interval) {
        if (interval == null || interval.isEmpty()) {
            throw new IllegalArgumentException("Interval cannot be null or empty");
        }

        interval = interval.trim().toLowerCase();

        // Extract number and unit
        String numberPart = interval.replaceAll("[^0-9]", "");
        String unitPart = interval.replaceAll("[0-9]", "").trim();

        if (numberPart.isEmpty()) {
            throw new IllegalArgumentException("Invalid interval format: " + interval);
        }

        int value = Integer.parseInt(numberPart);

        // Convert to seconds based on unit
        return switch (unitPart) {
            case "s", "" -> value;  // seconds (default if no unit)
            case "m" -> value * 60;  // minutes
            case "h" -> value * 3600;  // hours
            default -> throw new IllegalArgumentException(
                "Invalid time unit: " + unitPart + ". Supported units: s, m, h");
        };
    }

    /**
     * Parse interval string to seconds with a default value
     *
     * @param interval     Interval string
     * @param defaultValue Default value to return if parsing fails
     * @return Seconds
     */
    public static int parseToSeconds(String interval, int defaultValue) {
        try {
            return parseToSeconds(interval);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
