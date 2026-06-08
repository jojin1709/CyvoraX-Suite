package com.venomproxy.scanner;

import com.venomproxy.model.Finding;
import com.venomproxy.proxy.ScopeControl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ActiveScanner {
    private final ScopeControl scopeControl;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .followRedirects(false)
            .connectTimeout(Duration.ofSeconds(15))
            .readTimeout(Duration.ofSeconds(30))
            .build();

    public ActiveScanner(ScopeControl scopeControl) {
        this.scopeControl = scopeControl;
    }

    public List<Finding> scanUrl(String url) {
        if (!scopeControl.isInScope(url)) {
            return List.of(new Finding("Info", "Active scan skipped: target is out of scope", url, "Firm",
                    "Add the target to scope before active scanning.", "", "", Instant.now()));
        }

        List<Finding> findings = new ArrayList<>();
        findings.addAll(testReflectedXss(url));
        findings.addAll(testSqlError(url));
        findings.addAll(testPathTraversal(url));
        findings.addAll(testOpenRedirect(url));
        findings.addAll(testSsrf(url));
        findings.addAll(testCommandInjectionMarker(url));
        return findings;
    }

    private List<Finding> testReflectedXss(String url) {
        String payload = "\"><script>alert('CYVORAX')</script>";
        ScanResponse response = request(replaceFirstParameter(url, payload));
        if (response.body().contains(payload)) {
            return List.of(finding("High", "Reflected XSS indicator", url, payload, response));
        }
        return List.of();
    }

    private List<Finding> testSqlError(String url) {
        String payload = "'";
        ScanResponse response = request(replaceFirstParameter(url, payload));
        String body = response.body().toLowerCase(Locale.ROOT);
        if (body.contains("sql syntax") || body.contains("mysql") || body.contains("postgresql") || body.contains("ora-")) {
            return List.of(finding("High", "SQL injection error indicator", url, payload, response));
        }
        return List.of();
    }

    private List<Finding> testPathTraversal(String url) {
        String payload = "../../../../../../etc/passwd";
        ScanResponse response = request(replaceFirstParameter(url, payload));
        if (response.body().contains("root:x:0:0")) {
            return List.of(finding("High", "Path traversal indicator", url, payload, response));
        }
        return List.of();
    }

    private List<Finding> testOpenRedirect(String url) {
        String payload = "https://example.com/cyvorax-suite-redirect-test";
        ScanResponse response = request(replaceFirstParameter(url, payload));
        if (response.status() >= 300 && response.status() < 400 && response.headers().toLowerCase(Locale.ROOT).contains(payload)) {
            return List.of(finding("Medium", "Open redirect indicator", url, payload, response));
        }
        return List.of();
    }

    private List<Finding> testSsrf(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (!(lower.contains("url=") || lower.contains("uri=") || lower.contains("path=")
                || lower.contains("next=") || lower.contains("redirect=") || lower.contains("callback="))) {
            return List.of();
        }
        String payload = "http://127.0.0.1:1/cyvorax-suite-ssrf-test";
        ScanResponse response = request(replaceFirstParameter(url, payload));
        String body = response.body().toLowerCase(Locale.ROOT);
        if (body.contains("127.0.0.1") || body.contains("localhost") || body.contains("connection refused")
                || body.contains("failed to connect")) {
            return List.of(finding("High", "SSRF indicator", url, payload, response));
        }
        return List.of(new Finding("Info", "SSRF sink candidate", url, "Tentative",
                "Parameter name suggests URL fetching. Manual validation recommended.",
                "GET " + url + " HTTP/1.1", "", Instant.now()));
    }

    private List<Finding> testCommandInjectionMarker(String url) {
        String marker = "CYVORAX_TEST";
        String payload = ";echo " + marker;
        ScanResponse response = request(replaceFirstParameter(url, payload));
        if (response.body().contains(marker)) {
            return List.of(finding("Critical", "Command injection indicator", url, payload, response));
        }
        return List.of();
    }

    private ScanResponse request(String url) {
        try (Response response = client.newCall(new Request.Builder().url(url).build()).execute()) {
            ResponseBody body = response.body();
            String text = body == null ? "" : body.string();
            StringBuilder headers = new StringBuilder();
            response.headers().forEach(pair -> headers.append(pair.getFirst()).append(": ").append(pair.getSecond()).append('\n'));
            return new ScanResponse(response.code(), headers.toString(), text);
        } catch (Exception ex) {
            return new ScanResponse(0, "", "Scan request failed: " + ex.getMessage());
        }
    }

    private String replaceFirstParameter(String url, String payload) {
        try {
            URI uri = URI.create(url);
            String encoded = URLEncoder.encode(payload, StandardCharsets.UTF_8);
            String query = uri.getRawQuery();
            if (query == null || query.isBlank()) {
                String separator = url.contains("?") ? "&" : "?";
                return url + separator + "cyvorax=" + encoded;
            }
            String[] pairs = query.split("&", 2);
            String first = pairs[0];
            int equals = first.indexOf('=');
            String name = equals > 0 ? first.substring(0, equals) : first;
            String replacement = name + "=" + encoded;
            String newQuery = pairs.length == 1 ? replacement : replacement + "&" + pairs[1];
            return uri.resolve(uri.getRawPath() + "?" + newQuery).toString();
        } catch (IllegalArgumentException ex) {
            String encoded = URLEncoder.encode(payload, StandardCharsets.UTF_8);
            String separator = url.contains("?") ? "&" : "?";
            return url + separator + "cyvorax=" + encoded;
        }
    }

    private Finding finding(String severity, String issue, String url, String evidence, ScanResponse response) {
        String rawResponse = "HTTP " + response.status() + "\n" + response.headers() + "\n" + response.body();
        return new Finding(severity, issue, url, "Tentative", evidence, "GET " + url + " HTTP/1.1", rawResponse, Instant.now());
    }

    private record ScanResponse(int status, String headers, String body) {
    }
}
