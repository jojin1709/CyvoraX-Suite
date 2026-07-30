package com.venomproxy.proxy;

import com.venomproxy.db.Database;
import com.venomproxy.model.MatchReplaceRule;
import com.venomproxy.model.RequestData;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class MatchReplaceEngine {
    private final Database database;
    private final CopyOnWriteArrayList<MatchReplaceRule> rules = new CopyOnWriteArrayList<>();

    public MatchReplaceEngine(Database database) {
        this.database = database;
        reload();
    }

    public void reload() {
        rules.clear();
        rules.addAll(database.listMatchReplaceRules());
    }

    public List<MatchReplaceRule> rules() {
        return List.copyOf(rules);
    }

    public void save(MatchReplaceRule rule) {
        database.saveMatchReplaceRule(rule);
        reload();
    }

    public void delete(MatchReplaceRule rule) {
        if (rule != null && rule.getId() > 0) {
            database.deleteMatchReplaceRule(rule.getId());
            reload();
        }
    }

    public RequestData applyToRequest(RequestData request) {
        RequestData current = request;
        for (MatchReplaceRule rule : rules) {
            if (rule.isEnabled() && isRequestRule(rule) && matchesCondition(rule, current)) {
                current = applyRule(rule, current);
            }
        }
        return current;
    }

    private boolean isRequestRule(MatchReplaceRule rule) {
        return rule.getPhase().equalsIgnoreCase("Request");
    }

    private RequestData applyRule(MatchReplaceRule rule, RequestData request) {
        String target = rule.getTarget().toLowerCase(Locale.ROOT);
        if (target.equals("url")) {
            request.setUrl(replace(rule, request.getUrl()));
        } else if (target.equals("method")) {
            request.setMethod(replace(rule, request.getMethod()).toUpperCase(Locale.ROOT));
        } else if (target.equals("body")) {
            String body = new String(request.getBody(), StandardCharsets.UTF_8);
            request.setBody(replace(rule, body).getBytes(StandardCharsets.UTF_8));
        } else if (target.equals("cookie")) {
            replaceHeader(request.getHeaders(), "Cookie", rule);
        } else if (target.startsWith("header:")) {
            replaceHeader(request.getHeaders(), target.substring("header:".length()).trim(), rule);
        } else if (target.equals("header")) {
            replaceAllHeaders(request.getHeaders(), rule);
        }
        return request;
    }

    private void replaceHeader(LinkedHashMap<String, String> headers, String headerName, MatchReplaceRule rule) {
        String actualName = null;
        for (String name : headers.keySet()) {
            if (name.equalsIgnoreCase(headerName)) {
                actualName = name;
                break;
            }
        }
        if (actualName != null) {
            headers.put(actualName, replace(rule, headers.get(actualName)));
        }
    }

    private void replaceAllHeaders(LinkedHashMap<String, String> headers, MatchReplaceRule rule) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            entry.setValue(replace(rule, entry.getValue()));
        }
    }

    private boolean matchesCondition(MatchReplaceRule rule, RequestData request) {
        if (rule.getConditionField().isBlank() || rule.getConditionPattern().isBlank()) {
            return true;
        }
        String field = rule.getConditionField().toLowerCase(Locale.ROOT);
        String value;
        if (field.equals("url")) {
            value = request.getUrl();
        } else if (field.equals("method")) {
            value = request.getMethod();
        } else if (field.equals("body")) {
            value = new String(request.getBody(), StandardCharsets.UTF_8);
        } else if (field.startsWith("header:")) {
            value = headerValue(request.getHeaders(), field.substring("header:".length()));
        } else if (field.equals("cookie")) {
            value = headerValue(request.getHeaders(), "Cookie");
        } else {
            value = request.toRaw();
        }
        return matches(value, rule.getConditionPattern(), rule.isRegex());
    }

    private String headerValue(Map<String, String> headers, String name) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name.trim()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("");
    }

    private boolean matches(String value, String pattern, boolean regex) {
        if (value == null) {
            return false;
        }
        if (!regex) {
            return value.contains(pattern);
        }
        try {
            return Pattern.compile(pattern, Pattern.DOTALL).matcher(value).find();
        } catch (PatternSyntaxException ex) {
            return false;
        }
    }

    private String replace(MatchReplaceRule rule, String value) {
        String safe = value == null ? "" : value;
        if (rule.isRegex()) {
            try {
                return Pattern.compile(rule.getPattern(), Pattern.DOTALL)
                        .matcher(safe)
                        .replaceAll(rule.getReplacement());
            } catch (PatternSyntaxException ex) {
                return safe;
            }
        }
        return safe.replace(rule.getPattern(), rule.getReplacement());
    }
}
