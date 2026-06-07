package com.venomproxy.diagnostics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

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
}
