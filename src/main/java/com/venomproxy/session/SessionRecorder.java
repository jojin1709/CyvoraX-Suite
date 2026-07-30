package com.venomproxy.session;

import com.venomproxy.db.Database;
import com.venomproxy.model.HttpTransaction;
import com.venomproxy.model.RequestData;
import com.venomproxy.model.SessionEntry;
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
import java.util.concurrent.atomic.AtomicInteger;

public class SessionRecorder {
    private final Database database;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .followRedirects(false)
            .connectTimeout(Duration.ofSeconds(20))
            .readTimeout(Duration.ofSeconds(60))
            .build();
    private volatile long activeRecordingId;
    private final AtomicInteger sequence = new AtomicInteger();

    public SessionRecorder(Database database) {
        this.database = database;
    }

    public synchronized long start(String name) {
        if (activeRecordingId > 0) {
            return activeRecordingId;
        }
        activeRecordingId = database.createSessionRecording(name);
        sequence.set(database.listSessionEntries(activeRecordingId).size());
        return activeRecordingId;
    }

    public synchronized void stop() {
        if (activeRecordingId == 0) {
            return;
        }
        database.stopSessionRecording(activeRecordingId);
        activeRecordingId = 0;
        sequence.set(0);
    }

    public boolean isRecording() {
        return activeRecordingId > 0;
    }

    public long activeRecordingId() {
        return activeRecordingId;
    }

    public void record(HttpTransaction transaction) {
        long recordingId = activeRecordingId;
        if (recordingId == 0 || transaction == null) {
            return;
        }
        database.saveSessionEntry(recordingId, transaction, sequence.incrementAndGet());
    }

    public List<ReplayResult> replay(List<SessionEntry> entries) {
        List<ReplayResult> results = new ArrayList<>();
        for (SessionEntry entry : entries) {
            Instant started = Instant.now();
            try {
                RequestData data = RequestData.fromRaw(entry.getRequestRaw());
                try (Response response = client.newCall(toOkHttp(data)).execute()) {
                    ResponseBody body = response.body();
                    long length = body == null ? 0 : body.bytes().length;
                    long timeMs = Duration.between(started, Instant.now()).toMillis();
                    results.add(new ReplayResult(entry.getSequence(), response.code(), length, timeMs, ""));
                }
            } catch (Exception ex) {
                long timeMs = Duration.between(started, Instant.now()).toMillis();
                results.add(new ReplayResult(entry.getSequence(), 0, 0, timeMs, ex.getMessage()));
            }
        }
        return results;
    }

    private Request toOkHttp(RequestData data) {
        Headers.Builder headers = new Headers.Builder();
        data.getHeaders().forEach((key, value) -> {
            if (!key.equalsIgnoreCase("Host") && !key.equalsIgnoreCase("Content-Length")) {
                headers.add(key, value);
            }
        });
        RequestBody body = null;
        if (!data.getMethod().equalsIgnoreCase("GET") && !data.getMethod().equalsIgnoreCase("HEAD")) {
            String contentType = data.getHeaders().getOrDefault("Content-Type", "application/octet-stream");
            body = RequestBody.create(data.getBody(), MediaType.parse(contentType));
        }
        return new Request.Builder()
                .url(data.getUrl())
                .headers(headers.build())
                .method(data.getMethod().toUpperCase(Locale.ROOT), body)
                .build();
    }

    public String exportText(List<SessionEntry> entries) {
        StringBuilder builder = new StringBuilder();
        for (SessionEntry entry : entries) {
            builder.append("=== Entry ").append(entry.getSequence()).append(" @ ").append(entry.getTimestamp()).append(" ===\n");
            builder.append(entry.getRequestRaw()).append("\n\n");
            builder.append(entry.getResponseRaw()).append("\n\n");
        }
        return builder.toString();
    }

    public record ReplayResult(int sequence, int status, long length, long timeMs, String error) {
        public String toLine() {
            if (error != null && !error.isBlank()) {
                return "#" + sequence + " error in " + timeMs + " ms: " + error;
            }
            return "#" + sequence + " " + status + " " + length + " bytes in " + timeMs + " ms";
        }
    }
}
