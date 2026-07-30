package com.venomproxy.ui;

import com.venomproxy.model.RequestData;
import com.venomproxy.util.TextCodecs;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpInspectorPane extends TabPane {
    private static final Pattern JWT_PATTERN = Pattern.compile("eyJ[A-Za-z0-9_\\-.]+\\.[A-Za-z0-9_\\-.]+\\.?[A-Za-z0-9_\\-.]*");
    private static final Pattern FORM_PATTERN = Pattern.compile("(?is)<form\\b[^>]*>(.*?)</form>");

    private final TextArea headers = UiUtil.codeArea("Headers");
    private final TextArea cookies = UiUtil.codeArea("Cookies");
    private final TextArea parameters = UiUtil.codeArea("Parameters");
    private final TextArea json = UiUtil.codeArea("JSON");
    private final TextArea jwt = UiUtil.codeArea("JWT");
    private final TextArea forms = UiUtil.codeArea("HTML forms");
    private final TextArea metadata = UiUtil.codeArea("Metadata");
    private final TextArea notes = UiUtil.codeArea("Notes");

    public HttpInspectorPane() {
        getStyleClass().add("http-inspector");
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        setTabMinWidth(65);
        setTabMaxWidth(120);
        getTabs().addAll(
                tab("Headers", headers),
                tab("Cookies", cookies),
                tab("Params", parameters),
                tab("JSON", json),
                tab("JWT", jwt),
                tab("Forms", forms),
                tab("Meta", metadata),
                tab("Notes", notes)
        );
    }

    public void inspect(String requestRaw, String responseRaw, String noteText) {
        headers.setText(headersView(requestRaw, responseRaw));
        cookies.setText(cookieView(requestRaw, responseRaw));
        parameters.setText(parameterView(requestRaw));
        json.setText(jsonView(requestRaw, responseRaw));
        jwt.setText(jwtView(requestRaw, responseRaw));
        forms.setText(formsView(responseRaw));
        metadata.setText(metadataView(requestRaw, responseRaw));
        notes.setText(noteText == null ? "" : noteText);
    }

    private Tab tab(String title, TextArea area) {
        Tab tab = new Tab(title, area);
        tab.setClosable(false);
        return tab;
    }

    private String headersView(String requestRaw, String responseRaw) {
        return "Request Headers\n" + headerBlock(requestRaw) + "\n\nResponse Headers\n" + headerBlock(responseRaw);
    }

    private String cookieView(String requestRaw, String responseRaw) {
        StringBuilder builder = new StringBuilder();
        headersMap(requestRaw).forEach((key, value) -> {
            if (key.equalsIgnoreCase("Cookie")) {
                builder.append("Request Cookie\n");
                for (String cookie : value.split(";")) {
                    builder.append(cookie.trim()).append('\n');
                }
            }
        });
        headersMap(responseRaw).forEach((key, value) -> {
            if (key.equalsIgnoreCase("Set-Cookie")) {
                builder.append("\nSet-Cookie\n").append(value).append('\n');
            }
        });
        return builder.length() == 0 ? "No cookies detected." : builder.toString();
    }

    private String parameterView(String requestRaw) {
        StringBuilder builder = new StringBuilder();
        try {
            RequestData data = RequestData.fromRaw(requestRaw);
            URI uri = URI.create(data.getUrl());
            appendPairs("Query", uri.getRawQuery(), builder);
            String contentType = data.getHeaders().getOrDefault("Content-Type", "").toLowerCase(Locale.ROOT);
            if (contentType.contains("application/x-www-form-urlencoded")) {
                appendPairs("Body", new String(data.getBody(), StandardCharsets.UTF_8), builder);
            }
        } catch (Exception ex) {
            return "Could not parse request parameters: " + ex.getMessage();
        }
        return builder.length() == 0 ? "No parameters detected." : builder.toString();
    }

    private String jsonView(String requestRaw, String responseRaw) {
        String requestBody = bodyBlock(requestRaw).trim();
        String responseBody = bodyBlock(responseRaw).trim();
        StringBuilder builder = new StringBuilder();
        if (looksJson(requestBody)) {
            builder.append("Request JSON\n").append(prettyJson(requestBody)).append("\n\n");
        }
        if (looksJson(responseBody)) {
            builder.append("Response JSON\n").append(prettyJson(responseBody)).append('\n');
        }
        return builder.length() == 0 ? "No JSON body detected." : builder.toString();
    }

    private String jwtView(String requestRaw, String responseRaw) {
        String source = (requestRaw == null ? "" : requestRaw) + "\n" + (responseRaw == null ? "" : responseRaw);
        Matcher matcher = JWT_PATTERN.matcher(source);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            builder.append(TextCodecs.apply("JWT Decode", matcher.group())).append("\n\n");
        }
        return builder.length() == 0 ? "No JWT detected." : builder.toString();
    }

    private String formsView(String responseRaw) {
        Matcher matcher = FORM_PATTERN.matcher(bodyBlock(responseRaw));
        StringBuilder builder = new StringBuilder();
        int count = 0;
        while (matcher.find()) {
            builder.append("Form ").append(++count).append('\n')
                    .append(matcher.group().replaceAll("\\s+", " ").trim()).append("\n\n");
        }
        return builder.length() == 0 ? "No HTML forms detected." : builder.toString();
    }

    private String metadataView(String requestRaw, String responseRaw) {
        String requestBody = bodyBlock(requestRaw);
        String responseBody = bodyBlock(responseRaw);
        return "Request bytes: " + byteLength(requestRaw) + "\n"
                + "Request body bytes: " + byteLength(requestBody) + "\n"
                + "Response bytes: " + byteLength(responseRaw) + "\n"
                + "Response body bytes: " + byteLength(responseBody) + "\n"
                + "Response content type: " + headersMap(responseRaw).getOrDefault("Content-Type", "");
    }

    private void appendPairs(String title, String encoded, StringBuilder builder) {
        if (encoded == null || encoded.isBlank()) {
            return;
        }
        builder.append(title).append('\n');
        for (String pair : encoded.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            builder.append(decode(key)).append(" = ").append(decode(value)).append('\n');
        }
        builder.append('\n');
    }

    private String decode(String value) {
        return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String headerBlock(String raw) {
        String normalized = raw == null ? "" : raw.replace("\r\n", "\n");
        int separator = normalized.indexOf("\n\n");
        return separator >= 0 ? normalized.substring(0, separator) : normalized;
    }

    private String bodyBlock(String raw) {
        String normalized = raw == null ? "" : raw.replace("\r\n", "\n");
        int separator = normalized.indexOf("\n\n");
        return separator >= 0 ? normalized.substring(separator + 2) : "";
    }

    private Map<String, String> headersMap(String raw) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : headerBlock(raw).split("\\R")) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                map.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
            }
        }
        return map;
    }

    private boolean looksJson(String value) {
        return (value.startsWith("{") && value.endsWith("}")) || (value.startsWith("[") && value.endsWith("]"));
    }

    private String prettyJson(String value) {
        StringBuilder builder = new StringBuilder();
        int indent = 0;
        boolean quoted = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '"' && (i == 0 || value.charAt(i - 1) != '\\')) {
                quoted = !quoted;
            }
            if (!quoted && (ch == '{' || ch == '[')) {
                builder.append(ch).append('\n').append("  ".repeat(++indent));
            } else if (!quoted && (ch == '}' || ch == ']')) {
                builder.append('\n').append("  ".repeat(Math.max(0, --indent))).append(ch);
            } else if (!quoted && ch == ',') {
                builder.append(ch).append('\n').append("  ".repeat(indent));
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private int byteLength(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }
}
