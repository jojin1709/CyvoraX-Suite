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
    private long timeMs;
    private String requestRaw;
    private String responseRaw;
    private Instant timestamp;
    private boolean websocket;
    private boolean inScope;

    public HttpTransaction(String method, String host, String path, int status, int length, String mimeType, String protocol,
                           long timeMs, String requestRaw, String responseRaw, Instant timestamp,
                           boolean websocket, boolean inScope) {
        this.method = method;
        this.host = host;
        this.path = path;
        this.status = status;
        this.length = length;
        this.mimeType = mimeType;
        this.protocol = protocol == null || protocol.isBlank() ? "HTTP/1.1" : protocol;
        this.timeMs = timeMs;
        this.requestRaw = requestRaw;
        this.responseRaw = responseRaw;
        this.timestamp = timestamp;
        this.websocket = websocket;
        this.inScope = inScope;
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
        return "http://" + host + path;
    }
}
