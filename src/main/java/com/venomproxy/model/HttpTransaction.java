package com.venomproxy.model;

import java.time.Instant;

public class HttpTransaction {
    private long id;
    private String method;
    private String host;
    private String path;
    private int status;
    private int length;
    private String mimeType;
    private String protocol;
    private String scheme;
    private long timeMs;
    private String requestRaw;
    private String responseRaw;
    private Instant timestamp;
    private boolean websocket;
    private boolean inScope;
    private String notes;
    private String comments;
    private String tags;
    private String colorLabel;
    private boolean favorite;

    public HttpTransaction(String method, String host, String path, int status, int length, String mimeType, String protocol,
                           long timeMs, String requestRaw, String responseRaw, Instant timestamp,
                           boolean websocket, boolean inScope) {
        this(method, host, path, status, length, mimeType, protocol, timeMs, requestRaw, responseRaw,
                timestamp, websocket, inScope, "", "", "", "", false);
    }

    public HttpTransaction(String method, String host, String path, int status, int length, String mimeType, String protocol,
                           long timeMs, String requestRaw, String responseRaw, Instant timestamp,
                           boolean websocket, boolean inScope, String notes, String comments, String tags,
                           String colorLabel, boolean favorite) {
        this.method = method;
        this.host = host;
        this.path = path;
        this.status = status;
        this.length = length;
        this.mimeType = mimeType;
        this.protocol = protocol == null || protocol.isBlank() ? "HTTP/1.1" : protocol;
        this.scheme = inferScheme(requestRaw, path);
        this.timeMs = timeMs;
        this.requestRaw = requestRaw;
        this.responseRaw = responseRaw;
        this.timestamp = timestamp;
        this.websocket = websocket;
        this.inScope = inScope;
        this.notes = notes == null ? "" : notes;
        this.comments = comments == null ? "" : comments;
        this.tags = tags == null ? "" : tags;
        this.colorLabel = colorLabel == null ? "" : colorLabel;
        this.favorite = favorite;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getMethod() {
        return method;
    }

    public String getHost() {
        return host;
    }

    public String getPath() {
        return path;
    }

    public int getStatus() {
        return status;
    }

    public int getLength() {
        return length;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getProtocol() {
        if (websocket) {
            return "WS";
        }
        return protocol;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = "https".equalsIgnoreCase(scheme) ? "https" : "http";
    }

    public long getTimeMs() {
        return timeMs;
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

    public boolean isWebsocket() {
        return websocket;
    }

    public boolean isInScope() {
        return inScope;
    }

    public String getNotes() {
        return notes;
    }

    public String getNoteIndicator() {
        return notes == null || notes.isBlank() ? "" : "Note";
    }

    public void setNotes(String notes) {
        this.notes = notes == null ? "" : notes;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments == null ? "" : comments;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags == null ? "" : tags;
    }

    public String getColorLabel() {
        return colorLabel;
    }

    public void setColorLabel(String colorLabel) {
        this.colorLabel = colorLabel == null ? "" : colorLabel;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public String getUrl() {
        if (requestRaw != null) {
            String firstLine = requestRaw.lines().findFirst().orElse("");
            String[] parts = firstLine.split("\\s+");
            if (parts.length >= 2 && (parts[1].startsWith("http://") || parts[1].startsWith("https://"))) {
                return parts[1];
            }
        }
        if (path == null || path.startsWith("http://") || path.startsWith("https://")) {
            return path == null ? "" : path;
        }
        return (scheme == null || scheme.isBlank() ? "http" : scheme) + "://" + host + path;
    }

    private String inferScheme(String requestRaw, String path) {
        if (requestRaw != null) {
            String firstLine = requestRaw.lines().findFirst().orElse("");
            String[] parts = firstLine.split("\\s+");
            if (parts.length >= 2 && parts[1].startsWith("https://")) {
                return "https";
            }
        }
        if (path != null && path.startsWith("https://")) {
            return "https";
        }
        return "http";
    }
}
