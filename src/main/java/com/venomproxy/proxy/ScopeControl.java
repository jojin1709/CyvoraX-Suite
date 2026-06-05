package com.venomproxy.proxy;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ScopeControl {
    private final List<String> includes = new ArrayList<>();
    private final List<String> excludes = new ArrayList<>();
    private final List<String> ignores = new ArrayList<>();
    private boolean outOfScopePassthrough = true;

    public synchronized boolean isInScope(String value) {
        String host = normalizeHost(value);
        for (String exclude : excludes) {
            if (matches(exclude, host) || matches(exclude, value)) {
                return false;
            }
        }
        if (includes.isEmpty()) {
            return true;
        }
        for (String include : includes) {
            if (matches(include, host) || matches(include, value)) {
                return true;
            }
        }
        return false;
    }

    public synchronized void setIncludesFromText(String text) {
        includes.clear();
        splitRules(text).forEach(includes::add);
    }

    public synchronized void addInclude(String rule) {
        if (rule != null && !rule.isBlank() && includes.stream().noneMatch(existing -> existing.equalsIgnoreCase(rule.trim()))) {
            includes.add(rule.trim());
        }
    }

    public synchronized void setExcludesFromText(String text) {
        excludes.clear();
        splitRules(text).forEach(excludes::add);
    }

    public synchronized void setIgnoresFromText(String text) {
        ignores.clear();
        splitRules(text).forEach(ignores::add);
    }

    public synchronized String includesAsText() {
        return String.join(System.lineSeparator(), includes);
    }

    public synchronized String excludesAsText() {
        return String.join(System.lineSeparator(), excludes);
    }

    public synchronized String ignoresAsText() {
        return String.join(System.lineSeparator(), ignores);
    }

    public synchronized boolean isIgnored(String value) {
        String host = normalizeHost(value);
        for (String ignore : ignores) {
            if (matches(ignore, host) || matches(ignore, value)) {
                return true;
            }
        }
        return false;
    }

    public synchronized boolean isOutOfScopePassthrough() {
        return outOfScopePassthrough;
    }

    public synchronized void setOutOfScopePassthrough(boolean outOfScopePassthrough) {
        this.outOfScopePassthrough = outOfScopePassthrough;
    }

    private List<String> splitRules(String text) {
        List<String> rules = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return rules;
        }
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                rules.add(trimmed);
            }
        }
        return rules;
    }

    private String normalizeHost(String value) {
        try {
            if (value.startsWith("http://") || value.startsWith("https://")) {
                return URI.create(value).getHost();
            }
        } catch (RuntimeException ignored) {
        }
        return value == null ? "" : value;
    }

    private boolean matches(String rule, String value) {
        if (rule == null || value == null) {
            return false;
        }
        String normalized = value.toLowerCase();
        String candidate = rule.toLowerCase();
        if (candidate.startsWith("regex:")) {
            return Pattern.compile(rule.substring(6), Pattern.CASE_INSENSITIVE).matcher(value).find();
        }
        if (candidate.contains("*")) {
            String regex = Pattern.quote(candidate).replace("*", "\\E.*\\Q");
            return Pattern.compile("^" + regex + "$", Pattern.CASE_INSENSITIVE).matcher(normalized).find();
        }
        return normalized.contains(candidate);
    }
}
