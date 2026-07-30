package com.venomproxy.util;

import com.venomproxy.model.RequestData;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

public final class RequestCopyUtil {
    private RequestCopyUtil() {
    }

    public static String asCurl(RequestData request) {
        StringBuilder builder = new StringBuilder("curl");
        builder.append(" -X ").append(shell(request.getMethod()));
        for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
            builder.append(" -H ").append(shell(header.getKey() + ": " + header.getValue()));
        }
        if (request.getBody().length > 0) {
            builder.append(" --data-binary ").append(shell(new String(request.getBody(), StandardCharsets.UTF_8)));
        }
        builder.append(' ').append(shell(request.getUrl()));
        return builder.toString();
    }

    public static String asFetch(RequestData request) {
        StringBuilder builder = new StringBuilder();
        builder.append("fetch(").append(js(request.getUrl())).append(", {\n");
        builder.append("  method: ").append(js(request.getMethod())).append(",\n");
        builder.append("  headers: {\n");
        for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
            builder.append("    ").append(js(header.getKey())).append(": ").append(js(header.getValue())).append(",\n");
        }
        builder.append("  }");
        if (request.getBody().length > 0) {
            builder.append(",\n  body: ").append(js(new String(request.getBody(), StandardCharsets.UTF_8)));
        }
        builder.append("\n});");
        return builder.toString();
    }

    public static String asJavaScript(RequestData request) {
        return asFetch(request);
    }

    public static String asPythonRequests(RequestData request) {
        StringBuilder builder = new StringBuilder();
        builder.append("import requests\n\n");
        builder.append("headers = {\n");
        for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
            builder.append("    ").append(py(header.getKey())).append(": ").append(py(header.getValue())).append(",\n");
        }
        builder.append("}\n\n");
        builder.append("response = requests.")
                .append(request.getMethod().toLowerCase(Locale.ROOT))
                .append("(")
                .append(py(request.getUrl()))
                .append(", headers=headers");
        if (request.getBody().length > 0) {
            builder.append(", data=").append(py(new String(request.getBody(), StandardCharsets.UTF_8)));
        }
        builder.append(")\nprint(response.status_code)\nprint(response.text)");
        return builder.toString();
    }

    private static String shell(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static String js(String value) {
        return "\"" + escape(value).replace("'", "\\'") + "\"";
    }

    private static String py(String value) {
        return "'" + escape(value).replace("'", "\\'") + "'";
    }

    private static String escape(String value) {
        return (value == null ? "" : value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
