package com.venomproxy.notifications;

import com.venomproxy.db.Database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NotificationServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsAndMarksNotificationsRead() throws Exception {
        try (Database database = new Database(tempDir.resolve("notifications.db"))) {
            NotificationService service = new NotificationService(database);

            service.publish("Scan Complete", "Scan complete", "https://example.test produced 2 findings");

            assertEquals(1, service.unreadCount());
            assertEquals("Scan Complete", database.listNotifications().get(0).getType());

            service.markAllRead();
            assertEquals(0, service.unreadCount());
            assertFalse(database.listNotifications().get(0).isRead() == false);
        }
    }
}
