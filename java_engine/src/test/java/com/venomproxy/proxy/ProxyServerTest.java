package com.venomproxy.proxy;

import okhttp3.Headers;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyServerTest {
    @Test
    void rawResponseUsesIso88591ForLosslessBinaryStorage() throws Exception {
        ProxyServer server = new ProxyServer(null, null, null, null, null, null, null);
        Method rawResponse = ProxyServer.class.getDeclaredMethod("rawResponse", Response.class, byte[].class);
        rawResponse.setAccessible(true);
        byte[] body = new byte[]{0, (byte) 0x80, (byte) 0xff};
        Response response = new Response.Builder()
                .request(new Request.Builder().url("https://example.test/asset").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .headers(Headers.of("Content-Type", "application/octet-stream"))
                .build();

        String raw = (String) rawResponse.invoke(server, response, body);
        String rawBody = raw.substring(raw.indexOf("\r\n\r\n") + 4);

        assertTrue(raw.startsWith("HTTP/1.1 200 OK"));
        assertArrayEquals(body, rawBody.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    void proxyCanRestartAfterStopShutsDownWorkerPool() {
        ProxyServer server = new ProxyServer(null, null, new ScopeControl(), null, null, null, null);

        server.start("127.0.0.1", 0);
        server.stop();
        server.start("127.0.0.1", 0);

        assertTrue(server.isRunning());
        server.stop();
        assertFalse(server.isRunning());
    }
}
