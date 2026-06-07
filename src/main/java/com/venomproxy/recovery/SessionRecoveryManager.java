package com.venomproxy.recovery;

import com.venomproxy.workspace.WorkspaceInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;

public class SessionRecoveryManager {
    private static final String CLEAN_SHUTDOWN = "cleanShutdown";
    private static final String LAST_STARTED = "lastStarted";

    private final WorkspaceInfo workspace;
    private final Path recoveryDirectory;
    private final Path snapshotPath;

    public SessionRecoveryManager(WorkspaceInfo workspace) {
        this.workspace = workspace;
        this.recoveryDirectory = workspace.getPath().resolve("recovery");
        this.snapshotPath = recoveryDirectory.resolve("session-snapshot.properties");
    }

    public synchronized void markStarted() {
        Properties properties = loadProperties();
        properties.setProperty(CLEAN_SHUTDOWN, "false");
        properties.setProperty(LAST_STARTED, Instant.now().toString());
        storeProperties(properties);
    }

    public synchronized void markCleanShutdown() {
        Properties properties = loadProperties();
        properties.setProperty(CLEAN_SHUTDOWN, "true");
        properties.setProperty("lastCleanShutdown", Instant.now().toString());
        storeProperties(properties);
    }

    public synchronized void saveSnapshot(SessionSnapshot snapshot) {
        Properties properties = snapshot.toProperties();
        Properties existing = loadProperties();
        properties.setProperty(CLEAN_SHUTDOWN, existing.getProperty(CLEAN_SHUTDOWN, "false"));
        properties.setProperty(LAST_STARTED, existing.getProperty(LAST_STARTED, Instant.now().toString()));
        storeProperties(properties);
    }

    public synchronized Optional<SessionSnapshot> loadSnapshot() {
        if (!Files.exists(snapshotPath)) {
            return Optional.empty();
        }
        Properties properties = loadProperties();
        if (!workspace.getId().equals(properties.getProperty("workspaceId", workspace.getId()))) {
            return Optional.empty();
        }
        return Optional.of(SessionSnapshot.fromProperties(properties));
    }

    public synchronized boolean shouldPromptRecovery() {
        Properties properties = loadProperties();
        return Files.exists(snapshotPath)
                && !Boolean.parseBoolean(properties.getProperty(CLEAN_SHUTDOWN, "true"))
                && workspace.getId().equals(properties.getProperty("workspaceId", workspace.getId()));
    }

    public Path snapshotPath() {
        return snapshotPath;
    }

    private Properties loadProperties() {
        Properties properties = new Properties();
        if (!Files.exists(snapshotPath)) {
            return properties;
        }
        try (InputStream input = Files.newInputStream(snapshotPath)) {
            properties.load(input);
            return properties;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read recovery snapshot", ex);
        }
    }

    private void storeProperties(Properties properties) {
        try {
            Files.createDirectories(recoveryDirectory);
            try (OutputStream output = Files.newOutputStream(snapshotPath)) {
                properties.store(output, "CyvoraX session recovery snapshot");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not write recovery snapshot", ex);
        }
    }
}
