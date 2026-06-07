package com.venomproxy.diagnostics;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class CrashReporter {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneOffset.UTC);

    private final Path reportsDirectory;
    private final String version;
    private final Supplier<String> workspaceSupplier;
    private final Supplier<List<String>> pluginSupplier;

    public CrashReporter(Path reportsDirectory, String version, Supplier<String> workspaceSupplier,
                         Supplier<List<String>> pluginSupplier) {
        this.reportsDirectory = reportsDirectory;
        this.version = version;
        this.workspaceSupplier = workspaceSupplier;
        this.pluginSupplier = pluginSupplier;
    }

    public void installGlobalHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                record(throwable, "Uncaught exception on thread " + thread.getName()));
    }

    public Path record(Throwable throwable, String source) {
        return recordStandalone(reportsDirectory, version, source, throwable, safeWorkspace(), safePlugins());
    }

    public List<CrashReport> listReports() {
        try {
            Files.createDirectories(reportsDirectory);
            try (Stream<Path> paths = Files.list(reportsDirectory)) {
                return paths.filter(path -> path.getFileName().toString().endsWith(".log"))
                        .sorted(Comparator.comparing(this::modifiedTime).reversed())
                        .map(this::readReport)
                        .toList();
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not list crash reports", ex);
        }
    }

    public Optional<CrashReport> latestReport() {
        return listReports().stream().findFirst();
    }

    public Path getReportsDirectory() {
        return reportsDirectory;
    }

    public static Path recordStandalone(Path reportsDirectory, String version, String source, Throwable throwable,
                                        String activeWorkspace, List<String> loadedPlugins) {
        try {
            Files.createDirectories(reportsDirectory);
            Instant timestamp = Instant.now();
            String safeSource = sanitize(source == null || source.isBlank() ? "crash" : source);
            Path report = reportsDirectory.resolve("crash-" + FILE_TIME.format(timestamp) + "-" + safeSource + ".log");
            String content = buildContent(version, source, throwable, activeWorkspace, loadedPlugins);
            Files.writeString(report, content, StandardCharsets.UTF_8);
            return report;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not write crash report", ex);
        }
    }

    private static String buildContent(String version, String source, Throwable throwable, String activeWorkspace,
                                       List<String> loadedPlugins) {
        StringBuilder builder = new StringBuilder();
        builder.append("CyvoraX Crash Report\n");
        builder.append("====================\n\n");
        builder.append("Source: ").append(source == null || source.isBlank() ? "unknown" : source).append("\n\n");
        builder.append(ApplicationDiagnostics.collect(version, activeWorkspace, loadedPlugins)).append('\n');
        builder.append("Stack trace:\n");
        builder.append(stackTrace(throwable));
        return builder.toString();
    }

    private static String stackTrace(Throwable throwable) {
        if (throwable == null) {
            return "No throwable supplied.\n";
        }
        StringWriter stringWriter = new StringWriter();
        try (PrintWriter writer = new PrintWriter(stringWriter)) {
            throwable.printStackTrace(writer);
        }
        return stringWriter.toString();
    }

    private CrashReport readReport(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return new CrashReport(path, modifiedTime(path), summary(content), content);
        } catch (IOException ex) {
            return new CrashReport(path, modifiedTime(path), "Could not read report: " + ex.getMessage(), "");
        }
    }

    private Instant modifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException ex) {
            return Instant.EPOCH;
        }
    }

    private String summary(String content) {
        if (content == null || content.isBlank()) {
            return "Empty crash report";
        }
        return content.lines()
                .filter(line -> line.startsWith("Source:") || line.contains("Exception") || line.contains("Error"))
                .findFirst()
                .orElse("Crash report");
    }

    private String safeWorkspace() {
        try {
            return workspaceSupplier == null ? "unknown" : workspaceSupplier.get();
        } catch (RuntimeException ex) {
            return "unknown";
        }
    }

    private List<String> safePlugins() {
        try {
            List<String> plugins = pluginSupplier == null ? List.of() : pluginSupplier.get();
            return plugins == null ? List.of() : plugins;
        } catch (RuntimeException ex) {
            return List.of("Plugin diagnostics unavailable: " + ex.getMessage());
        }
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9_.-]+", "-").replaceAll("^-+|-+$", "");
    }
}
