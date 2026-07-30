package com.venomproxy.analytics;

import com.venomproxy.model.Finding;
import com.venomproxy.model.HttpTransaction;
import com.venomproxy.model.LogEntry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardAnalyticsTest {
    @Test
    void calculatesDashboardMetricsFromRealRows() {
        Instant now = Instant.now();
        List<HttpTransaction> history = List.of(
                new HttpTransaction("GET", "example.test", "/index", 200, 20, "text/html", "HTTP/1.1",
                        12, "GET http://example.test/index HTTP/1.1\r\n\r\n", "HTTP/1.1 200 OK\r\n\r\n", now, false, true),
                new HttpTransaction("GET", "example.test", "/app.js", 200, 30, "text/javascript", "HTTP/1.1",
                        15, "GET http://example.test/app.js HTTP/1.1\r\n\r\n", "HTTP/1.1 200 OK\r\n\r\n", now, false, true)
        );
        List<Finding> findings = List.of(
                new Finding("High", "Missing header", "http://example.test", "Firm", "evidence", "", "", now),
                new Finding("Low", "Cookie flag", "http://example.test", "Firm", "evidence", "", "", now)
        );
        List<LogEntry> logs = List.of(new LogEntry(now, "Proxy", "example.test", "Captured request"));

        DashboardMetrics metrics = new DashboardAnalytics().calculate(history, findings, logs, 1, 2);

        assertEquals(2, metrics.requests());
        assertEquals(1, metrics.hosts());
        assertEquals(2, metrics.findings());
        assertEquals(1, metrics.sessions());
        assertEquals(2, metrics.plugins());
        assertEquals(2, metrics.requestsPerHour());
        assertEquals(1, metrics.findingsBySeverity().get("High"));
        assertTrue(metrics.spiderStatistics().stream().anyMatch(row -> row.contains("JavaScript endpoints: 1")));
    }
}
