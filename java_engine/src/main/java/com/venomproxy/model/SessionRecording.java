package com.venomproxy.model;

import java.time.Instant;

public class SessionRecording {
    private long id;
    private final String name;
    private final Instant startedAt;
    private final Instant stoppedAt;

    public SessionRecording(String name, Instant startedAt, Instant stoppedAt) {
        this.name = name == null || name.isBlank() ? "Session " + startedAt : name;
        this.startedAt = startedAt;
        this.stoppedAt = stoppedAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getStoppedAt() {
        return stoppedAt;
    }

    public String getStatus() {
        return stoppedAt == null ? "Recording" : "Saved";
    }
}
