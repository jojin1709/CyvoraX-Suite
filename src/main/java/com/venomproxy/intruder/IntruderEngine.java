package com.venomproxy.intruder;

import com.venomproxy.model.RequestData;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class IntruderEngine {
    public enum AttackType {
        SNIPER,
        BATTERING_RAM,
        PITCHFORK,
        CLUSTER_BOMB
    }

    private final OkHttpClient client = new OkHttpClient.Builder()
            .followRedirects(false)
            .connectTimeout(Duration.ofSeconds(15))
            .readTimeout(Duration.ofSeconds(45))
            .build();

    public List<IntruderResult> run(String rawRequest, List<String> payloads, AttackType attackType, Consumer<IntruderResult> onResult) {
        List<IntruderResult> results = new CopyOnWriteArrayList<>();
        List<String> generated = buildPayloads(payloads, attackType);
        for (int i = 0; i < generated.size(); i++) {
            String payload = generated.get(i);
            String mutated = rawRequest.replace("§§", payload).replace("§", payload);
            Instant started = Instant.now();
            try {
                RequestData data = RequestData.fromRaw(mutated);
                Response response = send(data);
                ResponseBody body = response.body();
                byte[] bytes = body == null ? new byte[0] : body.bytes();
                IntruderResult result = new IntruderResult(i + 1, payload, response.code(), bytes.length,
                        Duration.between(started, Instant.now()).toMillis(), "");
                results.add(result);
                onResult.accept(result);
                response.close();
            } catch (Exception ex) {
                IntruderResult result = new IntruderResult(i + 1, payload, 0, 0,
                        Duration.between(started, Instant.now()).toMillis(), ex.getMessage());
                results.add(result);
                onResult.accept(result);
            }
        }
        return new ArrayList<>(results);
    }

    private List<String> buildPayloads(List<String> payloads, AttackType attackType) {
        List<String> clean = payloads.stream().filter(s -> s != null && !s.isBlank()).toList();
        if (clean.isEmpty()) {
            return List.of("CYVORAX");
        }
        return clean;
    }

    private Response send(RequestData data) throws Exception {
        Request.Builder builder = new Request.Builder().url(data.getUrl());
        Headers.Builder headers = new Headers.Builder();
        data.getHeaders().forEach((key, value) -> {
            if (!key.equalsIgnoreCase("Host") && !key.equalsIgnoreCase("Content-Length")) {
                headers.add(key, value);
            }
        });
        builder.headers(headers.build());
        RequestBody body = null;
        if (!data.getMethod().equalsIgnoreCase("GET") && !data.getMethod().equalsIgnoreCase("HEAD")) {
            String contentType = data.getHeaders().getOrDefault("Content-Type", "application/octet-stream");
            body = RequestBody.create(data.getBody(), MediaType.parse(contentType));
        }
        builder.method(data.getMethod().toUpperCase(Locale.ROOT), body);
        return client.newCall(builder.build()).execute();
    }

    public record IntruderResult(int number, String payload, int status, int length, long timeMs, String error) {
    }
}
