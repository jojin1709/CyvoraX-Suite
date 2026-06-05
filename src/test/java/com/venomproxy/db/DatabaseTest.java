package com.venomproxy.db;

import com.venomproxy.model.HttpTransaction;
import com.venomproxy.model.SessionEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsSettingsAndTransactionAnnotations() throws Exception {
        try (Database database = new Database(tempDir.resolve("test.db"))) {
            database.setSetting("theme", "Light");
            assertEquals("Light", database.getSetting("theme", "Dark"));

            HttpTransaction tx = new HttpTransaction(
                    "GET",
                    "example.test",
                    "/login",
                    200,
                    123,
                    "text/html",
                    "HTTP/1.1",
                    42,
                    "GET http://example.test/login HTTP/1.1\r\nHost: example.test\r\n\r\n",
                    "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\nok",
                    Instant.now(),
                    false,
                    true
            );
            database.saveTransaction(tx);
            tx.setNotes("credential flow");
            tx.setComments("needs retest");
            tx.setTags("auth,login");
            tx.setColorLabel("Yellow");
            tx.setFavorite(true);
            database.updateTransactionAnnotations(tx);

            HttpTransaction loaded = database.listTransactions().get(0);
            assertEquals("credential flow", loaded.getNotes());
            assertEquals("needs retest", loaded.getComments());
            assertEquals("auth,login", loaded.getTags());
            assertEquals("Yellow", loaded.getColorLabel());
            assertTrue(loaded.isFavorite());

            long recordingId = database.createSessionRecording("login replay");
            database.saveSessionEntry(recordingId, loaded, 1);
            database.stopSessionRecording(recordingId);
            List<SessionEntry> entries = database.listSessionEntries(recordingId);

            assertEquals(1, database.listSessionRecordings().size());
            assertEquals(1, entries.size());
            assertEquals("GET http://example.test/login HTTP/1.1\r\nHost: example.test\r\n\r\n", entries.get(0).getRequestRaw());
        }
    }
}
