package com.venomproxy.model;

import java.time.Instant;

public class LogEntry {
    private long id;
    private Instant timestamp;
    private String direction;
    private String host;
    private String message;

    public LogEntry(Instant timestamp, String direction, String host, String message) {
        this.timestamp = timestamp;
        this.direction = direction;
        this.host = host;
        this.message = message;
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

    public String getDirection() {
        return direction;
    }

    public String getHost() {
        return host;
    }

    public String getMessage() {
        return message;
    }
}
