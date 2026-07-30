package com.venomproxy.scanner;

import com.venomproxy.model.Finding;
import com.venomproxy.model.HttpTransaction;
import com.venomproxy.proxy.ScopeControl;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassiveScannerTest {
    private final PassiveScanner scanner = new PassiveScanner(new ScopeControl());

    @Test
    void missingBrowserSecurityHeadersAreOnlyReportedForHtmlResponses() {
        HttpTransaction json = transaction("/api", "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n{\"ok\":true}");

        List<Finding> findings = scanner.scan(json);

        assertFalse(findings.stream().anyMatch(finding -> finding.getIssue().contains("Missing")));
    }

    @Test
    void reflectedParameterDetectionIgnoresShortAndCommonValues() {
        HttpTransaction shortValue = transaction("/search?id=123", "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n123");
        HttpTransaction longValue = transaction("/search?q=unique-token",
                "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\nunique-token");

        assertFalse(scanner.scan(shortValue).stream().anyMatch(finding -> finding.getIssue().equals("Reflected parameter value")));
        assertTrue(scanner.scan(longValue).stream().anyMatch(finding -> finding.getIssue().equals("Reflected parameter value")));
    }

    private HttpTransaction transaction(String path, String responseRaw) {
        return new HttpTransaction("GET", "example.test", path, 200, responseRaw.length(), "text/html",
                "HTTP/1.1", 1, "GET " + path + " HTTP/1.1\r\nHost: example.test\r\n\r\n",
                responseRaw, Instant.now(), false, true);
    }
}
