package com.venomproxy.scanner;

import java.util.LinkedHashMap;
import java.util.Map;

final class HeaderParser {
    private HeaderParser() {
    }

    static Map<String, String> responseHeaders(String raw) {
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        if (raw == null) {
            return headers;
        }
        String[] lines = raw.replace("\r\n", "\n").split("\n");
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                break;
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
            }
        }
        return headers;
    }

    static String firstHeader(String raw, String name) {
        for (Map.Entry<String, String> entry : responseHeaders(raw).entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return "";
    }
}
