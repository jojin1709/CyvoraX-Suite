package com.venomproxy.model;

import java.time.Instant;

public class NotificationEntry {
    private long id;
    private final Instant timestamp;
    private final String type;
    private final String title;
    private final String message;
    private boolean read;

    public NotificationEntry(Instant timestamp, String type, String title, String message, boolean read) {
        this.timestamp = timestamp == null ? Instant.now() : timestamp;
        this.type = type == null || type.isBlank() ? "System" : type;
        this.title = title == null || title.isBlank() ? "Notification" : title;
        this.message = message == null ? "" : message;
        this.read = read;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }
}
