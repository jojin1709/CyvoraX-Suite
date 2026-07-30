package com.venomproxy.workspace;

import java.nio.file.Path;
import java.time.Instant;

public class WorkspaceInfo {
    private final String id;
    private final String name;
    private final Path path;
    private final Instant createdAt;
    private final Instant lastOpenedAt;
    private final boolean temporary;

    public WorkspaceInfo(String id, String name, Path path, Instant createdAt, Instant lastOpenedAt, boolean temporary) {
        this.id = id;
        this.name = name == null || name.isBlank() ? "Workspace" : name;
        this.path = path;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
        this.lastOpenedAt = lastOpenedAt == null ? this.createdAt : lastOpenedAt;
        this.temporary = temporary;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Path getPath() {
        return path;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastOpenedAt() {
        return lastOpenedAt;
    }

    public boolean isTemporary() {
        return temporary;
    }

    public Path databasePath() {
        return path.resolve("cyvorax-suite.db");
    }

    public Path metadataPath() {
        return path.resolve("workspace.properties");
    }
}
