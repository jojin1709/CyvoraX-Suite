package com.venomproxy.util;

import com.venomproxy.model.Finding;
import com.venomproxy.model.HttpTransaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportTemplateTest {
    @TempDir
    Path tempDir;

    @Test
    void exportsTemplatesUsingCollectedData() throws Exception {
        Finding finding = new Finding("High", "Reflected token", "https://example.test/login", "Firm",
                "token appeared in response", "GET /login HTTP/1.1\r\n\r\n", "HTTP/1.1 200 OK\r\n\r\ntoken", Instant.now());
        HttpTransaction tx = new HttpTransaction("GET", "example.test", "/login", 200, 128, "text/html",
                "HTTP/1.1", 22, "GET https://example.test/login HTTP/1.1\r\n\r\n",
                "HTTP/1.1 200 OK\r\n\r\nok", Instant.now(), false, true);
        Path output = tempDir.resolve("bug-bounty.html");

        ReportExporter.templateHtml(ReportTemplate.BUG_BOUNTY, List.of(finding), List.of(tx), output);
        String report = Files.readString(output);

        assertTrue(report.contains("Bug Bounty Report"));
        assertTrue(report.contains("Reflected token"));
        assertTrue(report.contains("example.test"));
    }
}
