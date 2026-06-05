package com.venomproxy.model;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

public class RequestData {
    private String method;
    private String url;
    private final LinkedHashMap<String, String> headers;
    private byte[] body;

    public RequestData(String method, String url, Map<String, String> headers, byte[] body) {
        this.method = method;
        this.url = url;
        this.headers = new LinkedHashMap<>(headers);
        this.body = body == null ? new byte[0] : body;
    }

    public static RequestData fromRaw(String raw) {
        String normalized = raw.replace("\r\n", "\n");
        String[] parts = normalized.split("\n\n", 2);
        String head = parts.length > 0 ? parts[0] : "";
        byte[] body = parts.length > 1 ? parts[1].getBytes(StandardCharsets.UTF_8) : new byte[0];
        String[] lines = head.split("\n");
        if (lines.length == 0 || lines[0].isBlank()) {
            throw new IllegalArgumentException("Request is empty.");
        }

        String[] requestLine = lines[0].trim().split("\\s+", 3);
        if (requestLine.length < 2) {
            throw new IllegalArgumentException("Request line must contain method and URL/path.");
        }

        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) {
                headers.put(lines[i].substring(0, colon).trim(), lines[i].substring(colon + 1).trim());
            }
        }

        String url = requestLine[1];
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            String host = headers.getOrDefault("Host", "localhost");
            url = "http://" + host + url;
        }

        return new RequestData(requestLine[0], url, headers, body);
    }

    public String toRaw() {
        String pathForLine = url;
        StringJoiner joiner = new StringJoiner("\r\n");
        joiner.add(method + " " + pathForLine + " HTTP/1.1");
        headers.forEach((key, value) -> joiner.add(key + ": " + value));
        joiner.add("");
        joiner.add(new String(body, StandardCharsets.UTF_8));
        return joiner.toString();
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        if (method != null && !method.isBlank()) {
            this.method = method;
        }
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        if (url != null && !url.isBlank()) {
            this.url = url;
        }
    }

    public LinkedHashMap<String, String> getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body == null ? new byte[0] : body;
    }
}
