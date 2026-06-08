package com.venomproxy.proxy;

import com.venomproxy.model.RequestData;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InterceptedRequestTest {
    @Test
    void editedHttpsRequestKeepsHttpsSchemeWhenForwarded() {
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        headers.put("Host", "secure.example.test");
        InterceptedRequest intercepted = new InterceptedRequest(
                new RequestData("GET", "https://secure.example.test/original", headers, new byte[0]));

        intercepted.forward("""
                GET /edited HTTP/1.1
                Host: secure.example.test

                """);

        assertEquals("https://secure.example.test/edited", intercepted.getRequestData().getUrl());
    }
}
