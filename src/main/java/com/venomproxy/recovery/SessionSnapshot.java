package com.venomproxy.recovery;

import java.time.Instant;
import java.util.Properties;

public record SessionSnapshot(Instant timestamp, String workspaceId, String selectedModule,
                              int repeaterSelectedIndex, String searchQuery, String scannerUrl,
                              String scannerSeverity, double windowX, double windowY,
                              double windowWidth, double windowHeight, boolean maximized) {
    public Properties toProperties() {
        Properties properties = new Properties();
        properties.setProperty("timestamp", timestamp.toString());
        properties.setProperty("workspaceId", safe(workspaceId));
        properties.setProperty("selectedModule", safe(selectedModule));
        properties.setProperty("repeaterSelectedIndex", String.valueOf(repeaterSelectedIndex));
        properties.setProperty("searchQuery", safe(searchQuery));
        properties.setProperty("scannerUrl", safe(scannerUrl));
        properties.setProperty("scannerSeverity", safe(scannerSeverity));
        properties.setProperty("windowX", String.valueOf(windowX));
        properties.setProperty("windowY", String.valueOf(windowY));
        properties.setProperty("windowWidth", String.valueOf(windowWidth));
        properties.setProperty("windowHeight", String.valueOf(windowHeight));
        properties.setProperty("maximized", String.valueOf(maximized));
        return properties;
    }

    public static SessionSnapshot fromProperties(Properties properties) {
        return new SessionSnapshot(
                parseInstant(properties.getProperty("timestamp")),
                properties.getProperty("workspaceId", ""),
                properties.getProperty("selectedModule", "Dashboard"),
                parseInt(properties.getProperty("repeaterSelectedIndex"), 0),
                properties.getProperty("searchQuery", ""),
                properties.getProperty("scannerUrl", ""),
                properties.getProperty("scannerSeverity", "All Severities"),
                parseDouble(properties.getProperty("windowX"), Double.NaN),
                parseDouble(properties.getProperty("windowY"), Double.NaN),
                parseDouble(properties.getProperty("windowWidth"), 1320),
                parseDouble(properties.getProperty("windowHeight"), 860),
                Boolean.parseBoolean(properties.getProperty("maximized", "false"))
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static Instant parseInstant(String value) {
        try {
            return value == null || value.isBlank() ? Instant.now() : Instant.parse(value);
        } catch (RuntimeException ex) {
            return Instant.now();
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }
}
