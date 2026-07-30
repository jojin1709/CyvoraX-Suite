package com.venomproxy.model;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Pattern;

public class AuthAccount {
    private long id;
    private String name;
    private String hostPattern;
    private String bearerToken;
    private String cookieJar;
    private String expiresAt;
    private boolean active;

    public AuthAccount(String name, String hostPattern, String bearerToken, String cookieJar, String expiresAt, boolean active) {
        this.name = value(name);
        this.hostPattern = value(hostPattern);
        this.bearerToken = value(bearerToken);
        this.cookieJar = value(cookieJar);
        this.expiresAt = value(expiresAt);
        this.active = active;
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

    public void setName(String name) {
        this.name = value(name);
    }

    public String getHostPattern() {
        return hostPattern;
    }

    public void setHostPattern(String hostPattern) {
        this.hostPattern = value(hostPattern);
    }

    public String getBearerToken() {
        return bearerToken;
    }

    public void setBearerToken(String bearerToken) {
        this.bearerToken = value(bearerToken);
    }

    public String getCookieJar() {
        return cookieJar;
    }

    public void setCookieJar(String cookieJar) {
        this.cookieJar = value(cookieJar);
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = value(expiresAt);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isExpired() {
        if (expiresAt.isBlank()) {
            return false;
        }
        try {
            return Instant.parse(expiresAt).isBefore(Instant.now());
        } catch (DateTimeParseException ex) {
            return true;
        }
    }

    public boolean matches(String url) {
        if (!active || isExpired() || hostPattern.isBlank()) {
            return false;
        }
        String host = "";
        try {
            host = URI.create(url).getHost();
        } catch (RuntimeException ignored) {
        }
        String normalizedHost = host == null ? "" : host.toLowerCase(Locale.ROOT);
        String normalizedUrl = url.toLowerCase(Locale.ROOT);
        for (String rawPattern : hostPattern.split("[,\\r\\n]+")) {
            String pattern = rawPattern.trim().toLowerCase(Locale.ROOT);
            if (pattern.isBlank()) {
                continue;
            }
            if (pattern.startsWith("/") && pattern.endsWith("/") && pattern.length() > 2) {
                if (Pattern.compile(pattern.substring(1, pattern.length() - 1), Pattern.CASE_INSENSITIVE).matcher(url).find()) {
                    return true;
                }
            } else if (pattern.contains("*")) {
                if (Pattern.compile("^" + wildcardToRegex(pattern) + "$", Pattern.CASE_INSENSITIVE).matcher(normalizedHost).find()) {
                    return true;
                }
            } else if (normalizedHost.equals(pattern) || normalizedHost.endsWith("." + pattern) || normalizedUrl.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private static String wildcardToRegex(String pattern) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);
            if (ch == '*') {
                regex.append(".*");
            } else {
                regex.append(Pattern.quote(String.valueOf(ch)));
            }
        }
        return regex.toString();
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
