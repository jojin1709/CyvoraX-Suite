package com.venomproxy.analytics;

import java.util.List;
import java.util.Map;

public record DashboardMetrics(long requests, long hosts, long findings, long sessions, long plugins,
                               long requestsPerHour, Map<String, Long> findingsBySeverity,
                               List<String> recentActivity, List<String> scannerStatistics,
                               List<String> spiderStatistics) {
}
