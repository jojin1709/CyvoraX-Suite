package com.venomproxy.ai;

import com.venomproxy.util.SecretMasker;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AiProviderClient {
    private static final Pattern MODEL_ID = Pattern.compile("\"id\"\\s*:");

    private final HttpClient client;
    private final Map<AiProvider, URI> endpointOverrides;

    public AiProviderClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build(), Map.of());
    }

    AiProviderClient(HttpClient client, Map<AiProvider, URI> endpointOverrides) {
        this.client = client == null ? HttpClient.newHttpClient() : client;
        this.endpointOverrides = endpointOverrides == null ? Map.of() : endpointOverrides;
    }

    public AiConnectionResult testConnection(AiProvider provider, String token) {
        if (provider == null) {
            return new AiConnectionResult(null, false, "Provider is not selected", 0, 0);
        }
        String cleanToken = token == null ? "" : token.trim();
        if (cleanToken.isBlank()) {
            return new AiConnectionResult(provider, false, "API key not configured", 0, 0);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint(provider))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + cleanToken)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                int modelCount = countModels(response.body());
                return new AiConnectionResult(provider, true,
                        "Connected successfully" + (modelCount > 0 ? " (" + modelCount + " models)" : ""),
                        status, modelCount);
            }
            if (status == 401 || status == 403) {
                return new AiConnectionResult(provider, false, "Authentication failed", status, 0);
            }
            return new AiConnectionResult(provider, false,
                    SecretMasker.maskSecrets("Connection failed with HTTP " + status), status, 0);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new AiConnectionResult(provider, false, "Connection interrupted", 0, 0);
        } catch (Exception ex) {
            return new AiConnectionResult(provider, false,
                    SecretMasker.maskSecrets("Connection failed: " + safeMessage(ex)), 0, 0);
        }
    }

    private URI endpoint(AiProvider provider) {
        return endpointOverrides.getOrDefault(provider, provider.modelsEndpoint());
    }

    private int countModels(String body) {
        if (body == null || body.isBlank()) {
            return 0;
        }
        Matcher matcher = MODEL_ID.matcher(body);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }
}
