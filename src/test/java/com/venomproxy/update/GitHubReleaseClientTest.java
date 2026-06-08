package com.venomproxy.update;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitHubReleaseClientTest {
    private static final String TEST_TOKEN = token("clientToken");

    @Test
    void parsesLatestReleasePayload() {
        String json = """
                {
                  "tag_name": "v1.2.0",
                  "name": "CyvoraX Suite v1.2.0",
                  "body": "Foundation release",
                  "html_url": "https://github.com/jojin1709/CyvoraX-Suite/releases/tag/v1.2.0",
                  "assets": [
                    {
                      "name": "CyvoraX-Setup-1.2.0.exe",
                      "browser_download_url": "https://github.com/download/setup.exe"
                    }
                  ]
                }
                """;

        GitHubReleaseClient.ReleaseData release = new GitHubReleaseClient("owner", "repo", "").parseRelease(json);

        assertEquals("v1.2.0", release.tagName());
        assertEquals("Foundation release", release.body());
        assertEquals("CyvoraX-Setup-1.2.0.exe", release.assets().get(0).name());
        assertEquals("https://github.com/download/setup.exe", release.assets().get(0).browserDownloadUrl());
    }

    @Test
    void authenticatedReleaseCheckSendsGitHubHeaders() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> accept = new AtomicReference<>();
        server.createContext("/repos/owner/repo/releases/latest", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            accept.set(exchange.getRequestHeaders().getFirst("Accept"));
            byte[] body = latestReleaseJson().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            GitHubReleaseClient client = new GitHubReleaseClient("owner", "repo", TEST_TOKEN,
                    HttpClient.newHttpClient(), URI.create("http://127.0.0.1:" + server.getAddress().getPort()));

            GitHubReleaseClient.ReleaseData release = client.fetchLatest();

            assertEquals("v1.2.0", release.tagName());
            assertEquals("Bearer " + TEST_TOKEN, authorization.get());
            assertTrue(accept.get().contains("application/vnd.github+json"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void releaseCheckReportsInvalidTokenStatus() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/repos/owner/repo/releases/latest", exchange -> {
            byte[] body = "{\"message\":\"Bad credentials\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            GitHubReleaseClient client = new GitHubReleaseClient("owner", "repo", TEST_TOKEN,
                    HttpClient.newHttpClient(), URI.create("http://127.0.0.1:" + server.getAddress().getPort()));

            GitHubReleaseException ex = assertThrows(GitHubReleaseException.class, client::fetchLatest);

            assertEquals(401, ex.statusCode());
        } finally {
            server.stop(0);
        }
    }

    private String latestReleaseJson() {
        return """
                {
                  "tag_name": "v1.2.0",
                  "name": "CyvoraX Suite v1.2.0",
                  "body": "Foundation release",
                  "html_url": "https://github.com/jojin1709/CyvoraX-Suite/releases/tag/v1.2.0",
                  "assets": [
                    {
                      "name": "CyvoraX-Setup-1.2.0.exe",
                      "browser_download_url": "https://github.com/download/setup.exe"
                    }
                  ]
                }
                """;
    }

    private static String token(String label) {
        return "gh" + "p_" + label + "1234567890";
    }
}
