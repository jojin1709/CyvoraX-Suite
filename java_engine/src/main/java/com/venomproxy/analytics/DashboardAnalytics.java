package com.venomproxy.analytics;

import com.venomproxy.model.Finding;
import com.venomproxy.model.HttpTransaction;
import com.venomproxy.model.LogEntry;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class DashboardAnalytics {
    public DashboardMetrics calculate(List<HttpTransaction> history, List<Finding> findings, List<LogEntry> logs,
                                      long sessionCount, long pluginCount) {
        List<HttpTransaction> safeHistory = history == null ? List.of() : history;
        List<Finding> safeFindings = findings == null ? List.of() : findings;
        List<LogEntry> safeLogs = logs == null ? List.of() : logs;
        Instant oneHourAgo = Instant.now().minus(Duration.ofHours(1));
        long hosts = safeHistory.stream()
                .map(HttpTransaction::getHost)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .count();
        long requestsPerHour = safeHistory.stream()
                .filter(tx -> tx.getTimestamp() != null && !tx.getTimestamp().isBefore(oneHourAgo))
                .count();
        Map<String, Long> bySeverity = safeFindings.stream()
                .collect(Collectors.groupingBy(finding -> normalizeSeverity(finding.getSeverity()),
                        LinkedHashMap::new, Collectors.counting()));
        return new DashboardMetrics(
                safeHistory.size(),
                hosts,
                safeFindings.size(),
                sessionCount,
                pluginCount,
                requestsPerHour,
                bySeverity,
                recentActivity(safeHistory, safeLogs),
                scannerStatistics(safeFindings),
                spiderStatistics(safeHistory)
        );
    }

    private List<String> recentActivity(List<HttpTransaction> history, List<LogEntry> logs) {
        List<String> logRows = logs.stream()
                .sorted(Comparator.comparing(LogEntry::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(5)
                .map(log -> safe(log.getDirection()) + " | " + safe(log.getHost()) + " | " + safe(log.getMessage()))
                .toList();
        if (!logRows.isEmpty()) {
            return logRows;
        }
        return history.stream()
                .limit(5)
                .map(tx -> safe(tx.getMethod()) + " | " + safe(tx.getHost()) + " | " + safe(tx.getPath()))
                .toList();
    }

    private List<String> scannerStatistics(List<Finding> findings) {
        if (findings.isEmpty()) {
            return List.of("No scanner findings recorded");
        }
        Map<String, Long> grouped = findings.stream()
                .collect(Collectors.groupingBy(finding -> normalizeSeverity(finding.getSeverity()),
                        LinkedHashMap::new, Collectors.counting()));
        return grouped.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .toList();
    }

    private List<String> spiderStatistics(List<HttpTransaction> history) {
        long urls = history.stream().map(HttpTransaction::getUrl).filter(value -> !value.isBlank()).distinct().count();
        long hosts = history.stream().map(HttpTransaction::getHost).filter(value -> value != null && !value.isBlank()).distinct().count();
        long javascript = history.stream()
                .map(HttpTransaction::getPath)
                .filter(path -> path != null && path.toLowerCase(Locale.ROOT).contains(".js"))
                .distinct()
                .count();
        return List.of(
                "Discovered URLs: " + urls,
                "Hosts: " + hosts,
                "JavaScript endpoints: " + javascript
        );
    }

    private String normalizeSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            return "Info";
        }
        String lower = severity.toLowerCase(Locale.ROOT);
        if (lower.contains("critical")) {
            return "Critical";
        }
        if (lower.contains("high")) {
            return "High";
        }
        if (lower.contains("medium")) {
            return "Medium";
        }
        if (lower.contains("low")) {
            return "Low";
        }
        return "Info";
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
