package com.venomproxy.diagnostics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrashReporterTest {
    @TempDir
    Path tempDir;

    @Test
    void writesDiagnosticCrashReport() {
        CrashReporter reporter = new CrashReporter(tempDir.resolve("crash-reports"), "1.2.0-test",
                () -> "Default Workspace", () -> List.of("ExamplePlugin [Loaded]"));

        Path report = reporter.record(new IllegalStateException("boom"), "Unit test crash");
        String content = reporter.listReports().get(0).content();

        assertTrue(report.getFileName().toString().endsWith(".log"));
        assertTrue(content.contains("CyvoraX version: 1.2.0-test"));
        assertTrue(content.contains("Active workspace: Default Workspace"));
        assertTrue(content.contains("ExamplePlugin [Loaded]"));
        assertTrue(content.contains("java.lang.IllegalStateException: boom"));
    }

    @Test
    void masksTokensInCrashReportsAndDiagnostics() {
        String token = "gh" + "p_crashToken1234567890";
        CrashReporter reporter = new CrashReporter(tempDir.resolve("crash-reports"), "1.2.0-test",
                () -> "Default Workspace",
                () -> List.of("Plugin configured with " + token),
                () -> "Updater diagnostics\nAuthentication: github.token=" + token);

        Path report = reporter.record(new IllegalStateException("boom " + token), "Unit test " + token);
        String content = reporter.listReports().get(0).content();

        assertFalse(content.contains(token));
        assertFalse(report.getFileName().toString().contains(token));
        assertTrue(content.contains("ghp_************"));
        assertTrue(content.contains("github.token=************"));
    }
}
