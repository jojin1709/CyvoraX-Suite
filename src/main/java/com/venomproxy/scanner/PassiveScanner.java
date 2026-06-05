package com.venomproxy.scanner;

import com.venomproxy.model.Finding;
import com.venomproxy.model.HttpTransaction;
import com.venomproxy.proxy.ScopeControl;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PassiveScanner {
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(api[_-]?key|secret|token|authorization|bearer)\\s*[:=]\\s*['\\\"]?([a-z0-9._\\-]{16,})"
    );

    private final ScopeControl scopeControl;

    public PassiveScanner(ScopeControl scopeControl) {
        this.scopeControl = scopeControl;
    }

    public List<Finding> scan(HttpTransaction tx) {
        List<Finding> findings = new ArrayList<>();
        if (!scopeControl.isInScope(tx.getUrl())) {
            return findings;
        }

        String response = tx.getResponseRaw() == null ? "" : tx.getResponseRaw();
        String lower = response.toLowerCase(Locale.ROOT);
        Map<String, String> headers = HeaderParser.responseHeaders(response);

        requireHeader(findings, tx, headers, "Strict-Transport-Security", "Missing HSTS header", "Low");
        requireHeader(findings, tx, headers, "Content-Security-Policy", "Missing Content-Security-Policy header", "Medium");
        requireHeader(findings, tx, headers, "X-Frame-Options", "Missing clickjacking protection header", "Low");

        for (Map.Entry<String, String> header : headers.entrySet()) {
            if (header.getKey().equalsIgnoreCase("Set-Cookie")) {
                String cookie = header.getValue().toLowerCase(Locale.ROOT);
                if (!cookie.contains("secure")) {
                    findings.add(finding("Medium", "Cookie missing Secure flag", tx, header.getValue()));
                }
                if (!cookie.contains("httponly")) {
                    findings.add(finding("Low", "Cookie missing HttpOnly flag", tx, header.getValue()));
                }
            }
        }

        Matcher secretMatcher = SECRET_PATTERN.matcher(response);
        while (secretMatcher.find()) {
            findings.add(finding("High", "Potential exposed secret or token", tx, secretMatcher.group()));
        }

        if (tx.getStatus() >= 300 && tx.getStatus() < 400) {
            String location = HeaderParser.firstHeader(response, "Location");
            if (location.startsWith("http://") || location.startsWith("https://")) {
                findings.add(finding("Medium", "Potential open redirect sink", tx, "Location: " + location));
            }
        }

        if (tx.getUrl().startsWith("https://") && (lower.contains("src=\"http://") || lower.contains("href=\"http://")
                || lower.contains("url(http://"))) {
            findings.add(finding("Low", "Mixed content reference", tx, "HTTPS response references HTTP content"));
        }

        if (lower.contains("stack trace") || lower.contains("sql syntax") || lower.contains("traceback")
                || lower.contains("nullpointerexception") || lower.contains("org.springframework")) {
            findings.add(finding("Medium", "Verbose error page information leak", tx, "Error signature in response"));
        }

        for (String parameterValue : queryValues(tx.getPath())) {
            if (parameterValue.length() > 2 && response.contains(parameterValue)) {
                findings.add(finding("Low", "Reflected parameter value", tx, parameterValue));
            }
        }

        return findings;
    }

    private void requireHeader(List<Finding> findings, HttpTransaction tx, Map<String, String> headers, String header, String issue, String severity) {
        if (tx.getStatus() >= 200 && tx.getStatus() < 400 && headers.keySet().stream().noneMatch(key -> key.equalsIgnoreCase(header))) {
            findings.add(finding(severity, issue, tx, "Header not present: " + header));
        }
    }

    private List<String> queryValues(String path) {
        List<String> values = new ArrayList<>();
        if (path == null || !path.contains("?")) {
            return values;
        }
        String query = path.substring(path.indexOf('?') + 1);
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            if (equals >= 0 && equals < pair.length() - 1) {
                values.add(URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
            }
        }
        return values;
    }

    private Finding finding(String severity, String issue, HttpTransaction tx, String evidence) {
        return new Finding(severity, issue, tx.getUrl(), "Tentative", evidence, tx.getRequestRaw(), tx.getResponseRaw(), Instant.now());
    }
}
