package com.venomproxy.model;

import java.time.Instant;

public class Finding {
    private long id;
    private String severity;
    private String issue;
    private String url;
    private String confidence;
    private String evidence;
    private String requestRaw;
    private String responseRaw;
    private Instant timestamp;

    public Finding(String severity, String issue, String url, String confidence, String evidence,
                   String requestRaw, String responseRaw, Instant timestamp) {
        this.severity = severity;
        this.issue = issue;
        this.url = url;
        this.confidence = confidence;
        this.evidence = evidence;
        this.requestRaw = requestRaw;
        this.responseRaw = responseRaw;
        this.timestamp = timestamp;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getSeverity() {
        return severity;
    }

    public String getIssue() {
        return issue;
    }

    public String getUrl() {
        return url;
    }

    public String getConfidence() {
        return confidence;
    }

    public String getEvidence() {
        return evidence;
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
