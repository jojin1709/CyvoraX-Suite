package com.venomproxy.recovery;

import com.venomproxy.workspace.WorkspaceInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionRecoveryManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void detectsUncleanShutdownAndRestoresSnapshot() {
        WorkspaceInfo workspace = new WorkspaceInfo("default", "Default Workspace", tempDir, Instant.now(), Instant.now(), false);
        SessionRecoveryManager recovery = new SessionRecoveryManager(workspace);

        recovery.markStarted();
        recovery.saveSnapshot(new SessionSnapshot(Instant.now(), "default", "Repeater", 2,
                "token", "https://example.test", "High", 10, 20, 1200, 800, false));

        assertTrue(recovery.shouldPromptRecovery());
        SessionSnapshot loaded = recovery.loadSnapshot().orElseThrow();
        assertEquals("Repeater", loaded.selectedModule());
        assertEquals("token", loaded.searchQuery());
        assertEquals("High", loaded.scannerSeverity());

        recovery.markCleanShutdown();
        assertFalse(recovery.shouldPromptRecovery());
    }
}
