package com.venomproxy.util;

import com.venomproxy.model.RequestData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestCopyUtilTest {
    @Test
    void rendersCommonCopyFormats() {
        RequestData request = RequestData.fromRaw("""
                POST http://example.test/api HTTP/1.1
                Host: example.test
                Content-Type: application/json

                {"name":"CyvoraX"}
                """);

        assertTrue(RequestCopyUtil.asCurl(request).contains("curl -X 'POST'"));
        assertTrue(RequestCopyUtil.asFetch(request).contains("fetch(\"http://example.test/api\""));
        assertTrue(RequestCopyUtil.asJavaScript(request).contains("method: \"POST\""));
        assertTrue(RequestCopyUtil.asPythonRequests(request).contains("requests.post('http://example.test/api'"));
    }
}
