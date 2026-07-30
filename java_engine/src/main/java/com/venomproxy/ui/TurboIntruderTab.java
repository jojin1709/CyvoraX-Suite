package com.venomproxy.ui;

import com.venomproxy.model.RequestData;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class TurboIntruderTab extends Tab {
    private final TextArea requestEditor = UiUtil.codeArea("Use FUZZ as the insertion marker");
    private final TextArea payloadEditor = UiUtil.codeArea("One payload per line");
    private final ObservableList<TurboResult> results = FXCollections.observableArrayList();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Label status = new Label("Ready");
    private final Label throughput = new Label("0 r/s");
    private final ProgressBar progress = new ProgressBar(0);
    private ScheduledExecutorService scheduler;

    public TurboIntruderTab(Path toolsDirectory) {
        super("Turbo Intruder");
        setClosable(false);
        Path ffuf = toolsDirectory.resolve("ffuf").resolve("ffuf.exe");
        Label engine = new Label(Files.exists(ffuf) ? "ffuf: bundled" : "ffuf: not installed, Java async engine active");

        requestEditor.setText("GET http://example.com/FUZZ HTTP/1.1\r\nHost: example.com\r\n\r\n");
        payloadEditor.setText("admin\nlogin\napi\nbackup\nhealth");

        ComboBox<String> mode = new ComboBox<>(FXCollections.observableArrayList("Async", "Race Condition", "Pipeline HTTP/2"));
        mode.getSelectionModel().select("Async");
        Spinner<Integer> rps = new Spinner<>(1, 500, 25);
        rps.setEditable(true);
        Spinner<Integer> concurrency = new Spinner<>(1, 250, 25);
        concurrency.setEditable(true);
        Button loadWordlist = new Button("Load Wordlist");
        Button start = new Button("Start");
        Button stop = new Button("Stop");
        Button clear = new Button("Clear");

        loadWordlist.setOnAction(event -> loadWordlist());
        start.setOnAction(event -> start(mode.getValue(), rps.getValue(), concurrency.getValue()));
        stop.setOnAction(event -> stop());
        clear.setOnAction(event -> {
            results.clear();
            progress.setProgress(0);
            throughput.setText("0 r/s");
            status.setText("Results cleared");
        });

        TableView<TurboResult> table = new TableView<>(results);
        UiUtil.constrainTable(table);
        table.setPlaceholder(UiUtil.emptyState("No turbo results", "Load or enter payloads, then start an async fuzzing run.", null, null));
        table.getColumns().add(column("#", TurboResult::number, 70));
        table.getColumns().add(column("Payload", TurboResult::payload, 220));
        table.getColumns().add(column("Status", TurboResult::status, 90));
        table.getColumns().add(column("Length", TurboResult::length, 90));
        table.getColumns().add(column("Time", TurboResult::timeMs, 90));
        table.getColumns().add(column("Protocol", TurboResult::protocol, 100));
        table.getColumns().add(column("Error", TurboResult::error, 280));

        SplitPane editorSplit = new SplitPane(requestEditor, payloadEditor);
        editorSplit.setDividerPositions(0.65);
        SplitPane rootSplit = new SplitPane(editorSplit, table);
        rootSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
        rootSplit.setDividerPositions(0.5);

        VBox root = new VBox(8,
                new HBox(8, engine, mode, new Label("RPS"), rps, new Label("Concurrency"), concurrency,
                        loadWordlist, start, stop, clear, progress, throughput, status),
                rootSplit);
        VBox.setVgrow(rootSplit, Priority.ALWAYS);
        root.setPadding(new Insets(12));
        setContent(root);
    }

    private void start(String mode, int rps, int concurrency) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        List<String> payloads = Arrays.stream(payloadEditor.getText().split("\\R"))
                .filter(line -> !line.isBlank()).toList();
        if (payloads.isEmpty()) {
            running.set(false);
            status.setText("No payloads to send");
            return;
        }
        progress.setProgress(0);
        status.setText("Running " + payloads.size() + " payloads");
        Instant runStarted = Instant.now();
        AtomicInteger completed = new AtomicInteger();
        scheduler = Executors.newScheduledThreadPool(Math.max(2, concurrency));
        Semaphore semaphore = new Semaphore(concurrency);
        HttpClient client = HttpClient.newBuilder()
                .version(mode.equals("Pipeline HTTP/2") ? HttpClient.Version.HTTP_2 : HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(15))
                .executor(Executors.newFixedThreadPool(concurrency))
                .build();

        long delayMs = Math.max(1, 1000 / Math.max(1, rps));
        for (int i = 0; i < payloads.size(); i++) {
            int number = i + 1;
            String payload = payloads.get(i);
            long delay = mode.equals("Race Condition") ? 0 : delayMs * i;
            scheduler.schedule(() -> sendOne(client, semaphore, number, payload, payloads.size(), completed, runStarted), delay, TimeUnit.MILLISECONDS);
        }
    }

    private void sendOne(HttpClient client, Semaphore semaphore, int number, String payload,
                         int total, AtomicInteger completed, Instant runStarted) {
        if (!running.get()) {
            return;
        }
        boolean acquired = false;
        try {
            semaphore.acquire();
            acquired = true;
            Instant started = Instant.now();
            RequestData data = RequestData.fromRaw(requestEditor.getText().replace("FUZZ", payload));
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(data.getUrl()))
                    .timeout(Duration.ofSeconds(30));
            data.getHeaders().forEach((key, value) -> {
                if (!key.equalsIgnoreCase("Host") && !key.equalsIgnoreCase("Content-Length")) {
                    builder.header(key, value);
                }
            });
            if (data.getMethod().equalsIgnoreCase("GET") || data.getMethod().equalsIgnoreCase("HEAD")) {
                builder.method(data.getMethod().toUpperCase(Locale.ROOT), HttpRequest.BodyPublishers.noBody());
            } else {
                builder.method(data.getMethod().toUpperCase(Locale.ROOT), HttpRequest.BodyPublishers.ofByteArray(data.getBody()));
            }
            CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            future.whenComplete((response, error) -> {
                semaphore.release();
                long timeMs = Duration.between(started, Instant.now()).toMillis();
                TurboResult result = error == null
                        ? new TurboResult(number, payload, response.statusCode(), response.body().length, timeMs, protocolName(response.version()), "")
                        : new TurboResult(number, payload, 0, 0, timeMs, "", error.getMessage());
                Platform.runLater(() -> {
                    results.add(result);
                    updateProgress(total, completed.incrementAndGet(), runStarted);
                });
            });
        } catch (Exception ex) {
            if (acquired) {
                semaphore.release();
            }
            Platform.runLater(() -> {
                results.add(new TurboResult(number, payload, 0, 0, 0, "", ex.getMessage()));
                updateProgress(total, completed.incrementAndGet(), runStarted);
            });
        }
    }

    private void stop() {
        running.set(false);
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        status.setText("Cancelled at " + results.size() + " results");
    }

    private void updateProgress(int total, int completed, Instant runStarted) {
        progress.setProgress(completed / (double) total);
        double seconds = Math.max(0.001, Duration.between(runStarted, Instant.now()).toMillis() / 1000.0);
        throughput.setText("%.1f r/s".formatted(completed / seconds));
        if (completed >= total) {
            running.set(false);
            if (scheduler != null) {
                scheduler.shutdown();
            }
            status.setText("Completed " + completed + " payloads");
        } else {
            status.setText("Running " + completed + "/" + total);
        }
    }

    private void loadWordlist() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load Turbo Intruder Wordlist");
        java.io.File file = chooser.showOpenDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            try {
                payloadEditor.setText(Files.readString(file.toPath()));
                status.setText("Loaded wordlist: " + file.getName());
            } catch (Exception ex) {
                status.setText("Wordlist load failed: " + ex.getMessage());
            }
        }
    }

    private String protocolName(HttpClient.Version version) {
        return version == HttpClient.Version.HTTP_2 ? "HTTP/2" : "HTTP/1.1";
    }

    private TableColumn<TurboResult, Object> column(String title, Function<TurboResult, Object> mapper, int width) {
        TableColumn<TurboResult, Object> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(mapper.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    public record TurboResult(int number, String payload, int status, int length, long timeMs, String protocol, String error) {
    }
}
