package com.venomproxy.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SecretMasker {
    private static final String MASK = "************";
    private static final Pattern GITHUB_TOKEN = Pattern.compile(
            "(github_pat_[A-Za-z0-9_]{12,}|gh[pousr]_[A-Za-z0-9_]{8,})"
    );
    private static final Pattern AI_PROVIDER_TOKEN = Pattern.compile(
            "(gsk_[A-Za-z0-9_-]{12,}|csk-[A-Za-z0-9_-]{12,}|sk-or-v1-[A-Za-z0-9_-]{12,})"
    );
    private static final Pattern AUTHORIZATION_BEARER = Pattern.compile(
            "(?i)(Authorization\\s*[:=]\\s*Bearer\\s+)([^\\s\\r\\n]+)"
    );
    private static final Pattern GITHUB_TOKEN_PROPERTY = Pattern.compile(
            "(?i)(github\\.token\\s*=\\s*)([^\\s\\r\\n]+)"
    );
    private static final Pattern AI_KEY_PROPERTY = Pattern.compile(
            "(?i)((?:GROQ|CEREBRAS|MISTRAL|OPENROUTER)_API_KEY\\s*=\\s*)([^\\s\\r\\n]+)"
    );

    private SecretMasker() {
    }

    public static String maskSecrets(String value) {
        if (value == null || value.isBlank()) {
            return value == null ? "" : value;
        }
        String masked = maskPattern(value, AUTHORIZATION_BEARER, true);
        masked = maskPattern(masked, GITHUB_TOKEN_PROPERTY, true);
        masked = maskPattern(masked, AI_KEY_PROPERTY, true);
        Matcher matcher = GITHUB_TOKEN.matcher(masked);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(maskToken(matcher.group(1))));
        }
        matcher.appendTail(buffer);
        String githubMasked = buffer.toString();
        matcher = AI_PROVIDER_TOKEN.matcher(githubMasked);
        buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(maskToken(matcher.group(1))));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    public static String maskToken(String token) {
        String clean = token == null ? "" : token.trim();
        if (clean.isBlank()) {
            return "";
        }
        int prefixEnd = clean.indexOf('_');
        if (clean.startsWith("github_pat_")) {
            return "github_pat_" + MASK;
        }
        if (clean.startsWith("sk-or-v1-")) {
            return "sk-or-v1-" + MASK;
        }
        if (prefixEnd > 0 && prefixEnd < clean.length() - 1) {
            return clean.substring(0, prefixEnd + 1) + MASK;
        }
        int dashEnd = clean.indexOf('-');
        if (dashEnd > 0 && dashEnd < clean.length() - 1) {
            return clean.substring(0, dashEnd + 1) + MASK;
        }
        return MASK;
    }

    private static String maskPattern(String value, Pattern pattern, boolean preservePrefix) {
        Matcher matcher = pattern.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String replacement = preservePrefix ? matcher.group(1) + MASK : MASK;
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
