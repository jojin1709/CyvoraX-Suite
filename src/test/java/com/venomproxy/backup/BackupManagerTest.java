package com.venomproxy.backup;

import com.venomproxy.workspace.WorkspaceInfo;
import com.venomproxy.workspace.WorkspaceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void createsAndRestoresWorkspaceBackup() throws Exception {
        WorkspaceManager workspaceManager = new WorkspaceManager(tempDir.resolve("profile"));
        WorkspaceInfo workspace = workspaceManager.createWorkspace("Client App");
        Files.writeString(workspace.getPath().resolve("notes.txt"), "real workspace data");
        BackupManager backupManager = new BackupManager(tempDir.resolve("profile"));

        BackupInfo backup = backupManager.createBackup(workspace, "Manual");
        WorkspaceInfo restored = backupManager.restoreToNewWorkspace(backup, workspaceManager, "Client App Restored");

        assertTrue(Files.exists(backup.backupPath().resolve("workspace").resolve("notes.txt")));
        assertEquals("real workspace data", Files.readString(restored.getPath().resolve("notes.txt")));
        assertTrue(backupManager.maybeRunScheduledBackup(workspace, "Daily", Instant.EPOCH).isPresent());
    }
}
