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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class IntruderEngine {
    private static final char MARKER = '\u00A7';
    private static final String EMPTY_MARKER = "\u00A7\u00A7";

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

    public List<IntruderResult> run(String rawRequest, List<String> payloads, AttackType attackType,
                                    Consumer<IntruderResult> onResult) {
        return run(rawRequest, payloads, attackType, new RunControl(), onResult);
    }

    public List<IntruderResult> run(String rawRequest, List<String> payloads, AttackType attackType,
                                    RunControl control, Consumer<IntruderResult> onResult) {
        return run(rawRequest, payloads, attackType, "http", control, onResult);
    }

    public List<IntruderResult> run(String rawRequest, List<String> payloads, AttackType attackType, String defaultScheme,
                                    RunControl control, Consumer<IntruderResult> onResult) {
        List<IntruderResult> results = new CopyOnWriteArrayList<>();
        List<Mutation> generated = mutationsFor(rawRequest, payloads, attackType);
        for (int i = 0; i < generated.size(); i++) {
            if (control.isCancelled()) {
                break;
            }
            control.waitIfPaused();
            control.delayBeforeRequest(i);
            Mutation mutation = generated.get(i);
            Instant started = Instant.now();
            try {
                RequestData data = RequestData.fromRaw(mutation.requestRaw(), defaultScheme);
                try (Response response = send(data)) {
                    ResponseBody body = response.body();
                    byte[] bytes = body == null ? new byte[0] : body.bytes();
                    IntruderResult result = new IntruderResult(i + 1, mutation.payloadLabel(), response.code(), bytes.length,
                            Duration.between(started, Instant.now()).toMillis(), "");
                    results.add(result);
                    onResult.accept(result);
                }
            } catch (Exception ex) {
                IntruderResult result = new IntruderResult(i + 1, mutation.payloadLabel(), 0, 0,
                        Duration.between(started, Instant.now()).toMillis(), ex.getMessage());
                results.add(result);
                onResult.accept(result);
            }
        }
        return new ArrayList<>(results);
    }

    public List<Mutation> mutationsFor(String rawRequest, List<String> payloads, AttackType attackType) {
        String source = rawRequest == null ? "" : rawRequest;
        List<Marker> markers = markers(source);
        if (markers.isEmpty()) {
            source = source.contains("FUZZ") ? source.replace("FUZZ", EMPTY_MARKER) : source + EMPTY_MARKER;
            markers = markers(source);
        }
        List<List<String>> sets = payloadSets(payloads);
        return switch (attackType == null ? AttackType.SNIPER : attackType) {
            case SNIPER -> sniper(source, markers, sets.get(0));
            case BATTERING_RAM -> batteringRam(source, markers, sets.get(0));
            case PITCHFORK -> pitchfork(source, markers, sets);
            case CLUSTER_BOMB -> clusterBomb(source, markers, sets);
        };
    }

    private List<Mutation> sniper(String rawRequest, List<Marker> markers, List<String> payloads) {
        List<Mutation> mutations = new ArrayList<>();
        for (int position = 0; position < markers.size(); position++) {
            for (String payload : payloads) {
                List<String> replacements = new ArrayList<>(baseline(markers));
                replacements.set(position, payload);
                mutations.add(new Mutation(render(rawRequest, markers, replacements),
                        "pos " + (position + 1) + ": " + payload));
            }
        }
        return mutations;
    }

    private List<Mutation> batteringRam(String rawRequest, List<Marker> markers, List<String> payloads) {
        List<Mutation> mutations = new ArrayList<>();
        for (String payload : payloads) {
            mutations.add(new Mutation(render(rawRequest, markers, repeat(payload, markers.size())), payload));
        }
        return mutations;
    }

    private List<Mutation> pitchfork(String rawRequest, List<Marker> markers, List<List<String>> sets) {
        List<Mutation> mutations = new ArrayList<>();
        int count = markers.stream()
                .mapToInt(marker -> setForPosition(sets, marker.index()).size())
                .min()
                .orElse(0);
        for (int row = 0; row < count; row++) {
            List<String> replacements = new ArrayList<>();
            for (Marker marker : markers) {
                replacements.add(setForPosition(sets, marker.index()).get(row));
            }
            mutations.add(new Mutation(render(rawRequest, markers, replacements), String.join(" | ", replacements)));
        }
        return mutations;
    }

    private List<Mutation> clusterBomb(String rawRequest, List<Marker> markers, List<List<String>> sets) {
        List<Mutation> mutations = new ArrayList<>();
        buildCluster(rawRequest, markers, sets, 0, new ArrayList<>(), mutations);
        return mutations;
    }

    private void buildCluster(String rawRequest, List<Marker> markers, List<List<String>> sets,
                              int position, List<String> replacements, List<Mutation> mutations) {
        if (position == markers.size()) {
            mutations.add(new Mutation(render(rawRequest, markers, replacements), String.join(" | ", replacements)));
            return;
        }
        for (String payload : setForPosition(sets, position)) {
            replacements.add(payload);
            buildCluster(rawRequest, markers, sets, position + 1, replacements, mutations);
            replacements.remove(replacements.size() - 1);
        }
    }

    private List<List<String>> payloadSets(List<String> payloads) {
        List<List<String>> sets = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String line : payloads == null ? List.<String>of() : payloads) {
            if (line == null || line.isBlank()) {
                if (!current.isEmpty()) {
                    sets.add(current);
                    current = new ArrayList<>();
                }
                continue;
            }
            current.add(line);
        }
        if (!current.isEmpty()) {
            sets.add(current);
        }
        if (sets.isEmpty()) {
            sets.add(List.of("CYVORAX"));
        }
        return sets;
    }

    private List<String> setForPosition(List<List<String>> sets, int position) {
        return position < sets.size() ? sets.get(position) : sets.get(0);
    }

    private List<Marker> markers(String rawRequest) {
        List<Marker> markers = new ArrayList<>();
        int index = 0;
        for (int cursor = 0; cursor < rawRequest.length(); cursor++) {
            if (rawRequest.charAt(cursor) != MARKER) {
                continue;
            }
            int end = rawRequest.indexOf(MARKER, cursor + 1);
            if (end < 0) {
                break;
            }
            markers.add(new Marker(index++, cursor, end + 1, rawRequest.substring(cursor + 1, end)));
            cursor = end;
        }
        return markers;
    }

    private List<String> baseline(List<Marker> markers) {
        return markers.stream().map(Marker::defaultValue).toList();
    }

    private List<String> repeat(String payload, int count) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            values.add(payload);
        }
        return values;
    }

    private String render(String rawRequest, List<Marker> markers, List<String> replacements) {
        StringBuilder builder = new StringBuilder();
        int cursor = 0;
        for (int i = 0; i < markers.size(); i++) {
            Marker marker = markers.get(i);
            builder.append(rawRequest, cursor, marker.start());
            builder.append(i < replacements.size() ? replacements.get(i) : marker.defaultValue());
            cursor = marker.end();
        }
        builder.append(rawRequest.substring(cursor));
        return builder.toString();
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

    public record Mutation(String requestRaw, String payloadLabel) {
    }

    private record Marker(int index, int start, int end, String defaultValue) {
    }

    public static class RunControl {
        private final AtomicBoolean paused = new AtomicBoolean(false);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile int delayMs;
        private volatile int threadCount = 1;

        public void pause() {
            paused.set(true);
        }

        public void resume() {
            paused.set(false);
        }

        public void cancel() {
            cancelled.set(true);
            paused.set(false);
        }

        public boolean isPaused() {
            return paused.get();
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        public void setDelayMs(int delayMs) {
            this.delayMs = Math.max(0, delayMs);
        }

        public void setThreadCount(int threadCount) {
            this.threadCount = Math.max(1, threadCount);
        }

        public int threadCount() {
            return threadCount;
        }

        void waitIfPaused() {
            while (paused.get() && !cancelled.get()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    cancel();
                    return;
                }
            }
        }

        void delayBeforeRequest(int requestIndex) {
            if (requestIndex <= 0 || delayMs <= 0 || cancelled.get()) {
                return;
            }
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                cancel();
            }
        }
    }
}
