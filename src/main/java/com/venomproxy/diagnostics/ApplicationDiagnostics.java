package com.venomproxy.diagnostics;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.List;

public final class ApplicationDiagnostics {
    private ApplicationDiagnostics() {
    }

    public static String collect(String version, String activeWorkspace, List<String> loadedPlugins) {
        StringBuilder builder = new StringBuilder();
        builder.append("CyvoraX version: ").append(blankDefault(version, "unknown")).append('\n');
        builder.append("Timestamp: ").append(Instant.now()).append('\n');
        builder.append("OS: ").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.version")).append(" (")
                .append(System.getProperty("os.arch")).append(")\n");
        builder.append("Java: ").append(System.getProperty("java.version")).append(" (")
                .append(System.getProperty("java.vendor")).append(")\n");
        builder.append("Active workspace: ").append(blankDefault(activeWorkspace, "unknown")).append('\n');
        builder.append("Process uptime ms: ").append(ManagementFactory.getRuntimeMXBean().getUptime()).append('\n');
        builder.append("Max memory bytes: ").append(Runtime.getRuntime().maxMemory()).append('\n');
        builder.append("Free memory bytes: ").append(Runtime.getRuntime().freeMemory()).append('\n');
        builder.append("Available processors: ").append(Runtime.getRuntime().availableProcessors()).append('\n');
        builder.append("Loaded plugins:\n");
        if (loadedPlugins == null || loadedPlugins.isEmpty()) {
            builder.append("  (none)\n");
        } else {
            loadedPlugins.stream()
                    .filter(plugin -> plugin != null && !plugin.isBlank())
                    .forEach(plugin -> builder.append("  - ").append(plugin).append('\n'));
        }
        return builder.toString();
    }

    private static String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
