package com.venomproxy.backup;

import com.venomproxy.workspace.WorkspaceInfo;
import com.venomproxy.workspace.WorkspaceManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

public class BackupManager {
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);

    private final Path backupsDirectory;

    public BackupManager(Path appDirectory) {
        this.backupsDirectory = appDirectory.resolve("backups");
    }

    public BackupInfo createBackup(WorkspaceInfo workspace, String reason) {
        try {
            Files.createDirectories(backupsDirectory);
            String id = sanitize(workspace.getId()) + "-" + BACKUP_TIME.format(Instant.now());
            Path backupPath = uniquePath(backupsDirectory.resolve(id));
            Path dataPath = backupPath.resolve("workspace");
            copyDirectory(workspace.getPath(), dataPath);
            writeMetadata(backupPath, workspace, reason);
            return readBackup(backupPath).orElseThrow(() -> new IllegalStateException("Backup metadata was not written"));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not create workspace backup", ex);
        }
    }

    public Optional<BackupInfo> maybeRunScheduledBackup(WorkspaceInfo workspace, String schedule, Instant lastBackup) {
        String normalized = schedule == null ? "Off" : schedule;
        if ("Daily".equalsIgnoreCase(normalized) && due(lastBackup, Duration.ofDays(1))) {
            return Optional.of(createBackup(workspace, "Daily"));
        }
        if ("Weekly".equalsIgnoreCase(normalized) && due(lastBackup, Duration.ofDays(7))) {
            return Optional.of(createBackup(workspace, "Weekly"));
        }
        return Optional.empty();
    }

    public WorkspaceInfo restoreToNewWorkspace(BackupInfo backup, WorkspaceManager workspaceManager, String workspaceName) {
        WorkspaceInfo restored = workspaceManager.createWorkspace(workspaceName == null || workspaceName.isBlank()
                ? backup.workspaceName() + " Restored" : workspaceName);
        try {
            copyDirectory(backup.backupPath().resolve("workspace"), restored.getPath(), path ->
                    !"workspace.properties".equals(path.getFileName().toString()));
            return workspaceManager.openWorkspace(restored.getId());
        } catch (IOException ex) {
            throw new IllegalStateException("Could not restore backup", ex);
        }
    }

    public List<BackupInfo> listBackups() {
        try {
            Files.createDirectories(backupsDirectory);
            try (Stream<Path> stream = Files.list(backupsDirectory)) {
                return stream.filter(Files::isDirectory)
                        .map(this::readBackup)
                        .flatMap(Optional::stream)
                        .sorted(Comparator.comparing(BackupInfo::createdAt).reversed())
                        .toList();
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not list backups", ex);
        }
    }

    public void deleteBackup(BackupInfo backup) {
        try {
            deleteDirectory(backup.backupPath());
        } catch (IOException ex) {
            throw new IllegalStateException("Could not delete backup", ex);
        }
    }

    public Path backupsDirectory() {
        return backupsDirectory;
    }

    private boolean due(Instant lastBackup, Duration interval) {
        return lastBackup == null || lastBackup.plus(interval).isBefore(Instant.now());
    }

    private Optional<BackupInfo> readBackup(Path backupPath) {
        Path metadata = backupPath.resolve("backup.properties");
        if (!Files.exists(metadata)) {
            return Optional.empty();
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(metadata)) {
            properties.load(input);
            return Optional.of(new BackupInfo(
                    properties.getProperty("id", backupPath.getFileName().toString()),
                    properties.getProperty("workspaceId", ""),
                    properties.getProperty("workspaceName", "Workspace"),
                    parseInstant(properties.getProperty("createdAt")),
                    backupPath,
                    directorySize(backupPath)
            ));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read backup metadata", ex);
        }
    }

    private void writeMetadata(Path backupPath, WorkspaceInfo workspace, String reason) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("id", backupPath.getFileName().toString());
        properties.setProperty("workspaceId", workspace.getId());
        properties.setProperty("workspaceName", workspace.getName());
        properties.setProperty("workspacePath", workspace.getPath().toString());
        properties.setProperty("createdAt", Instant.now().toString());
        properties.setProperty("reason", reason == null || reason.isBlank() ? "Manual" : reason);
        try (OutputStream output = Files.newOutputStream(backupPath.resolve("backup.properties"))) {
            properties.store(output, "CyvoraX workspace backup");
        }
    }

    private Path uniquePath(Path candidate) throws IOException {
        Path current = candidate;
        int suffix = 2;
        while (Files.exists(current)) {
            current = candidate.resolveSibling(candidate.getFileName() + "-" + suffix++);
        }
        Files.createDirectories(current);
        return current;
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        copyDirectory(source, target, path -> true);
    }

    private void copyDirectory(Path source, Path target, java.util.function.Predicate<Path> includeFile) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path relative = source.relativize(path);
                if (relative.toString().isBlank()) {
                    Files.createDirectories(target);
                    continue;
                }
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else if (includeFile.test(path)) {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path item : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(item);
            }
        }
    }

    private long directorySize(Path path) {
        try (Stream<Path> paths = Files.walk(path)) {
            return paths.filter(Files::isRegularFile)
                    .mapToLong(item -> {
                        try {
                            return Files.size(item);
                        } catch (IOException ex) {
                            return 0;
                        }
                    })
                    .sum();
        } catch (IOException ex) {
            return 0;
        }
    }

    private Instant parseInstant(String value) {
        try {
            return value == null || value.isBlank() ? Instant.EPOCH : Instant.parse(value);
        } catch (RuntimeException ex) {
            return Instant.EPOCH;
        }
    }

    private String sanitize(String value) {
        return (value == null || value.isBlank() ? "workspace" : value)
                .replaceAll("[^A-Za-z0-9_.-]+", "-")
                .replaceAll("^-+|-+$", "");
    }
}
