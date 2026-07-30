package com.venomproxy.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiProviderClientTest {
    @Test
    void authenticatedProviderTestSendsBearerToken() throws Exception {
        String token = "g" + "sk_providerClient1234567890";
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/models", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"data\":[{\"id\":\"model-a\"},{\"id\":\"model-b\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            AiProviderClient client = new AiProviderClient(HttpClient.newHttpClient(),
                    Map.of(AiProvider.GROQ, URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/models")));

            AiConnectionResult result = client.testConnection(AiProvider.GROQ, token);

            assertTrue(result.success());
            assertEquals("Bearer " + token, authorization.get());
            assertEquals(2, result.modelCount());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void invalidProviderTokenReportsAuthenticationFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/models", exchange -> {
            byte[] body = "{\"error\":\"bad credentials\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            AiProviderClient client = new AiProviderClient(HttpClient.newHttpClient(),
                    Map.of(AiProvider.GROQ, URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/models")));

            AiConnectionResult result = client.testConnection(AiProvider.GROQ, "g" + "sk_bad1234567890");

            assertFalse(result.success());
            assertEquals("Authentication failed", result.message());
            assertEquals(401, result.statusCode());
        } finally {
            server.stop(0);
        }
    }
}
