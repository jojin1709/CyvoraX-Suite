package com.venomproxy.model;

import java.time.Instant;

public class SessionEntry {
    private long id;
    private final long recordingId;
    private final long transactionId;
    private final int sequence;
    private final String requestRaw;
    private final String responseRaw;
    private final Instant timestamp;

    public SessionEntry(long recordingId, long transactionId, int sequence, String requestRaw, String responseRaw, Instant timestamp) {
        this.recordingId = recordingId;
        this.transactionId = transactionId;
        this.sequence = sequence;
        this.requestRaw = requestRaw == null ? "" : requestRaw;
        this.responseRaw = responseRaw == null ? "" : responseRaw;
        this.timestamp = timestamp;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getRecordingId() {
        return recordingId;
    }

    public long getTransactionId() {
        return transactionId;
    }

    public int getSequence() {
        return sequence;
    }

    public String getRequestRaw() {
        return requestRaw;
    }

    public String getResponseRaw() {
        return responseRaw;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
