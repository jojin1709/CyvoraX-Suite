package com.venomproxy.workspace;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

public class WorkspaceManager {
    private static final String DATABASE_FILE = "cyvorax-suite.db";
    private static final String METADATA_FILE = "workspace.properties";

    private final Path appDirectory;
    private final Path workspacesDirectory;
    private final Path trashDirectory;
    private final Path temporaryDirectory;

    public WorkspaceManager(Path appDirectory) throws IOException {
        this.appDirectory = appDirectory;
        this.workspacesDirectory = appDirectory.resolve("workspaces");
        this.trashDirectory = appDirectory.resolve("workspace-trash");
        this.temporaryDirectory = appDirectory.resolve("temporary-workspaces");
        Files.createDirectories(this.workspacesDirectory);
        Files.createDirectories(this.trashDirectory);
        Files.createDirectories(this.temporaryDirectory);
        migrateLegacyProfile();
        ensureDefaultWorkspace();
    }

    public Path appDirectory() {
        return appDirectory;
    }

    public Path workspacesDirectory() {
        return workspacesDirectory;
    }

    public synchronized List<WorkspaceInfo> listWorkspaces() {
        List<WorkspaceInfo> workspaces = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(workspacesDirectory)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    readWorkspace(entry).ifPresent(workspaces::add);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not list workspaces", ex);
        }
        workspaces.sort(Comparator.comparing(WorkspaceInfo::getLastOpenedAt).reversed()
                .thenComparing(WorkspaceInfo::getName, String.CASE_INSENSITIVE_ORDER));
        return workspaces;
    }

    public synchronized WorkspaceInfo createWorkspace(String name) {
        String id = uniqueId(name);
        Path workspacePath = workspacesDirectory.resolve(id);
        try {
            Files.createDirectories(workspacePath);
            WorkspaceInfo workspace = new WorkspaceInfo(id, cleanName(name), workspacePath, Instant.now(), Instant.now(), false);
            writeWorkspace(workspace);
            return workspace;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not create workspace", ex);
        }
    }

    public synchronized WorkspaceInfo temporaryWorkspace() {
        String id = "temporary-" + Instant.now().toEpochMilli();
        Path workspacePath = temporaryDirectory.resolve(id);
        try {
            Files.createDirectories(workspacePath);
            WorkspaceInfo workspace = new WorkspaceInfo(id, "Temporary Workspace", workspacePath, Instant.now(), Instant.now(), true);
            writeWorkspace(workspace);
            return workspace;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not create temporary workspace", ex);
        }
    }

    public synchronized WorkspaceInfo openWorkspace(Path path) {
        Path workspacePath = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(workspacePath)) {
            throw new IllegalArgumentException("Workspace folder does not exist: " + workspacePath);
        }
        Optional<WorkspaceInfo> existing = readWorkspace(workspacePath);
        WorkspaceInfo workspace = existing.orElseGet(() -> adoptWorkspace(workspacePath));
        return markOpened(workspace);
    }

    public synchronized WorkspaceInfo openWorkspace(String id) {
        WorkspaceInfo workspace = findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + id));
        return markOpened(workspace);
    }

    public synchronized WorkspaceInfo renameWorkspace(String id, String newName) {
        WorkspaceInfo workspace = findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + id));
        WorkspaceInfo renamed = new WorkspaceInfo(workspace.getId(), cleanName(newName), workspace.getPath(),
                workspace.getCreatedAt(), workspace.getLastOpenedAt(), workspace.isTemporary());
        try {
            writeWorkspace(renamed);
            return renamed;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not rename workspace", ex);
        }
    }

    public synchronized WorkspaceInfo duplicateWorkspace(String id, String newName) {
        WorkspaceInfo source = findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + id));
        WorkspaceInfo duplicate = createWorkspace(newName);
        try {
            copyDirectory(source.getPath(), duplicate.getPath());
            WorkspaceInfo metadata = new WorkspaceInfo(duplicate.getId(), cleanName(newName), duplicate.getPath(),
                    Instant.now(), Instant.now(), false);
            writeWorkspace(metadata);
            return metadata;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not duplicate workspace", ex);
        }
    }

    public synchronized Path deleteWorkspace(String id) {
        WorkspaceInfo workspace = findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + id));
        Path target = trashDirectory.resolve(workspace.getId() + "-" + Instant.now().toEpochMilli());
        try {
            Files.move(workspace.getPath(), target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not delete workspace", ex);
        }
    }

    private Optional<WorkspaceInfo> findById(String id) {
        return listWorkspaces().stream()
                .filter(workspace -> workspace.getId().equals(id))
                .findFirst();
    }

    private WorkspaceInfo markOpened(WorkspaceInfo workspace) {
        WorkspaceInfo opened = new WorkspaceInfo(workspace.getId(), workspace.getName(), workspace.getPath(),
                workspace.getCreatedAt(), Instant.now(), workspace.isTemporary());
        try {
            writeWorkspace(opened);
            return opened;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not update workspace recency", ex);
        }
    }

    private WorkspaceInfo adoptWorkspace(Path path) {
        String id = path.getFileName() == null ? uniqueId("workspace") : sanitize(path.getFileName().toString());
        WorkspaceInfo workspace = new WorkspaceInfo(id, path.getFileName() == null ? "Workspace" : path.getFileName().toString(),
                path, Instant.now(), Instant.now(), false);
        try {
            writeWorkspace(workspace);
            return workspace;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not adopt workspace", ex);
        }
    }

    private void migrateLegacyProfile() throws IOException {
        Path legacyDb = appDirectory.resolve(DATABASE_FILE);
        Path migratedPath = workspacesDirectory.resolve("default");
        Path migratedDb = migratedPath.resolve(DATABASE_FILE);
        if (!Files.exists(legacyDb) || Files.exists(migratedDb)) {
            return;
        }
        Files.createDirectories(migratedPath);
        copyIfPresent(legacyDb, migratedDb);
        copyIfPresent(appDirectory.resolve(DATABASE_FILE + "-wal"), migratedPath.resolve(DATABASE_FILE + "-wal"));
        copyIfPresent(appDirectory.resolve(DATABASE_FILE + "-shm"), migratedPath.resolve(DATABASE_FILE + "-shm"));
        WorkspaceInfo migrated = new WorkspaceInfo("default", "Default Workspace", migratedPath, Instant.now(), Instant.now(), false);
        writeWorkspace(migrated);
    }

    private void ensureDefaultWorkspace() throws IOException {
        if (!listWorkspaces().isEmpty()) {
            return;
        }
        Path defaultPath = workspacesDirectory.resolve("default");
        Files.createDirectories(defaultPath);
        writeWorkspace(new WorkspaceInfo("default", "Default Workspace", defaultPath, Instant.now(), Instant.now(), false));
    }

    private void copyIfPresent(Path source, Path target) throws IOException {
        if (Files.exists(source)) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private Optional<WorkspaceInfo> readWorkspace(Path path) {
        Path metadata = path.resolve(METADATA_FILE);
        if (!Files.exists(metadata)) {
            if (Files.exists(path.resolve(DATABASE_FILE))) {
                return Optional.of(adoptWorkspace(path));
            }
            return Optional.empty();
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(metadata)) {
            properties.load(input);
            String id = properties.getProperty("id", sanitize(path.getFileName().toString()));
            String name = properties.getProperty("name", path.getFileName().toString());
            Instant createdAt = parseInstant(properties.getProperty("createdAt"));
            Instant lastOpenedAt = parseInstant(properties.getProperty("lastOpenedAt"));
            boolean temporary = Boolean.parseBoolean(properties.getProperty("temporary", "false"));
            return Optional.of(new WorkspaceInfo(id, name, path, createdAt, lastOpenedAt, temporary));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read workspace metadata: " + metadata, ex);
        }
    }

    private void writeWorkspace(WorkspaceInfo workspace) throws IOException {
        Files.createDirectories(workspace.getPath());
        Properties properties = new Properties();
        properties.setProperty("id", workspace.getId());
        properties.setProperty("name", workspace.getName());
        properties.setProperty("createdAt", workspace.getCreatedAt().toString());
        properties.setProperty("lastOpenedAt", workspace.getLastOpenedAt().toString());
        properties.setProperty("temporary", String.valueOf(workspace.isTemporary()));
        try (OutputStream output = Files.newOutputStream(workspace.metadataPath())) {
            properties.store(output, "CyvoraX workspace metadata");
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private String uniqueId(String name) {
        String base = sanitize(name);
        String candidate = base;
        int suffix = 2;
        while (Files.exists(workspacesDirectory.resolve(candidate))) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String sanitize(String value) {
        String source = value == null || value.isBlank() ? "workspace" : value.trim().toLowerCase(Locale.ROOT);
        String cleaned = source.replaceAll("[^a-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        return cleaned.isBlank() ? "workspace" : cleaned;
    }

    private String cleanName(String name) {
        return name == null || name.isBlank() ? "Workspace" : name.trim();
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ex) {
            return Instant.now();
        }
    }
}
