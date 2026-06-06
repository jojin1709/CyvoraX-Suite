package com.venomproxy.db;

import com.venomproxy.model.Finding;
import com.venomproxy.model.HttpTransaction;
import com.venomproxy.model.SearchResult;
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
            tx.setColorLabel("Purple");
            tx.setFavorite(true);
            database.updateTransactionAnnotations(tx);

            HttpTransaction loaded = database.listTransactions().get(0);
            assertEquals("credential flow", loaded.getNotes());
            assertEquals("needs retest", loaded.getComments());
            assertEquals("auth,login", loaded.getTags());
            assertEquals("Purple", loaded.getColorLabel());
            assertTrue(loaded.isFavorite());
            assertEquals("Note", loaded.getNoteIndicator());

            long recordingId = database.createSessionRecording("login replay");
            database.saveSessionEntry(recordingId, loaded, 1);
            database.saveSessionEntryRaw(recordingId, 0, 2,
                    "GET http://example.test/imported HTTP/1.1\r\nHost: example.test\r\n\r\n",
                    "HTTP/1.1 204 No Content\r\n\r\n",
                    Instant.now());
            database.stopSessionRecording(recordingId);
            List<SessionEntry> entries = database.listSessionEntries(recordingId);

            assertEquals(1, database.listSessionRecordings().size());
            assertEquals(2, entries.size());
            assertEquals("GET http://example.test/login HTTP/1.1\r\nHost: example.test\r\n\r\n", entries.get(0).getRequestRaw());
            assertEquals(0, entries.get(1).getTransactionId());

            database.saveFinding(new Finding("High", "Reflected token", "http://example.test/login", "Firm",
                    "token appears in response", loaded.getRequestRaw(), loaded.getResponseRaw(), Instant.now()));
            List<SearchResult> noteResults = database.search("credential", 20);
            List<SearchResult> findingResults = database.search("reflected token", 20);
            List<SearchResult> sessionResults = database.search("imported", 20);
            assertTrue(noteResults.stream().anyMatch(result -> result.getType().equals("History") && result.getRecordId() == loaded.getId()));
            assertTrue(findingResults.stream().anyMatch(result -> result.getType().equals("Finding")));
            assertTrue(sessionResults.stream().anyMatch(result -> result.getType().equals("Session")));
        }
    }
}
