package com.venomproxy.model;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestDataTest {
    @Test
    void toRawUsesPathAndQueryForAbsoluteUrls() {
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        headers.put("Host", "example.test");
        RequestData request = new RequestData("GET", "https://example.test/app?q=one", headers, new byte[0]);

        String raw = request.toRaw();

        assertTrue(raw.startsWith("GET /app?q=one HTTP/1.1"));
        assertEquals("https://example.test/app?q=one", request.getUrl());
    }

    @Test
    void fromRawUsesDefaultSchemeForRelativeRequestTargets() {
        RequestData request = RequestData.fromRaw("""
                POST /submit HTTP/1.1
                Host: secure.example.test

                body
                """, "https");

        assertEquals("https://secure.example.test/submit", request.getUrl());
        assertEquals("POST", request.getMethod());
    }
}
