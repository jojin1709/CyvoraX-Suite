package com.venomproxy.notifications;

import com.venomproxy.db.Database;
import com.venomproxy.model.NotificationEntry;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.Instant;
import java.util.List;

public class NotificationService {
    private final Database database;
    private final ObservableList<NotificationEntry> notifications;

    public NotificationService(Database database) {
        this.database = database;
        this.notifications = FXCollections.observableArrayList(database.listNotifications());
    }

    public ObservableList<NotificationEntry> notifications() {
        return notifications;
    }

    public NotificationEntry publish(String type, String title, String message) {
        NotificationEntry entry = new NotificationEntry(Instant.now(), type, title, message, false);
        database.saveNotification(entry);
        notifications.add(0, entry);
        return entry;
    }

    public void markRead(NotificationEntry entry) {
        if (entry == null || entry.isRead()) {
            return;
        }
        entry.setRead(true);
        database.markNotificationRead(entry.getId());
        int index = notifications.indexOf(entry);
        if (index >= 0) {
            notifications.set(index, entry);
        }
    }

    public void markAllRead() {
        database.markAllNotificationsRead();
        notifications.forEach(entry -> entry.setRead(true));
        notifications.setAll(List.copyOf(notifications));
    }

    public long unreadCount() {
        return notifications.stream().filter(entry -> !entry.isRead()).count();
    }

    public void refresh() {
        notifications.setAll(database.listNotifications());
    }
}
