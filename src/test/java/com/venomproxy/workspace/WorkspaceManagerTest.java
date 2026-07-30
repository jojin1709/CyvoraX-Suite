package com.venomproxy.workspace;

import com.venomproxy.db.Database;
import com.venomproxy.model.HttpTransaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void migratesLegacyDatabaseAndManagesWorkspaceLifecycle() throws Exception {
        Path appDir = tempDir.resolve(".cyvorax-suite");
        Files.createDirectories(appDir);
        try (Database legacy = new Database(appDir.resolve("cyvorax-suite.db"))) {
            legacy.saveTransaction(new HttpTransaction("GET", "legacy.test", "/one", 200, 10, "text/plain",
                    "HTTP/1.1", 4, "GET http://legacy.test/one HTTP/1.1\r\nHost: legacy.test\r\n\r\n",
                    "HTTP/1.1 200 OK\r\n\r\nok", Instant.now(), false, true));
        }

        WorkspaceManager manager = new WorkspaceManager(appDir);
        WorkspaceInfo migrated = manager.listWorkspaces().stream()
                .filter(workspace -> workspace.getId().equals("default"))
                .findFirst()
                .orElseThrow();
        assertTrue(Files.exists(migrated.databasePath()));
        assertTrue(Files.exists(appDir.resolve("cyvorax-suite.db")));
        try (Database migratedDb = new Database(migrated.databasePath())) {
            assertEquals(1, migratedDb.listTransactions().size());
        }

        WorkspaceInfo renamed = manager.renameWorkspace(migrated.getId(), "Client Assessment");
        assertEquals("Client Assessment", renamed.getName());

        WorkspaceInfo duplicate = manager.duplicateWorkspace(renamed.getId(), "Client Assessment Copy");
        assertTrue(Files.exists(duplicate.databasePath()));
        try (Database duplicateDb = new Database(duplicate.databasePath())) {
            assertEquals(1, duplicateDb.listTransactions().size());
        }

        Path trashed = manager.deleteWorkspace(duplicate.getId());
        assertTrue(Files.exists(trashed));
        assertFalse(manager.listWorkspaces().stream().anyMatch(workspace -> workspace.getId().equals(duplicate.getId())));
    }
}
