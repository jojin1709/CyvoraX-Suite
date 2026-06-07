package com.venomproxy.ui;

import com.venomproxy.db.Database;
import com.venomproxy.model.HttpTransaction;
import com.venomproxy.proxy.ScopeControl;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
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
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpiderCrawlerTab extends Tab {
    private static final Pattern LINK_PATTERN = Pattern.compile("(?i)(?:href|src|action)\\s*=\\s*['\\\"]([^'\\\"#]+)['\\\"]");

    private final Database database;
    private final ObservableList<HttpTransaction> history;
    private final ScopeControl scopeControl;
    private final ObservableList<CrawlUrl> found = FXCollections.observableArrayList();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final TextField target = new TextField("https://example.com/");
    private final Label status = new Label("Ready");
    private final ProgressBar progress = new ProgressBar(0);
    private final BiConsumer<String, String> crawlNotification;

    public SpiderCrawlerTab(Database database, ObservableList<HttpTransaction> history, ScopeControl scopeControl,
                            Path toolsDirectory, BiConsumer<String, String> crawlNotification) {
        super("Spider / Crawler");
        this.database = database;
        this.history = history;
        this.scopeControl = scopeControl;
        this.crawlNotification = crawlNotification;
        setClosable(false);

        Path katana = toolsDirectory.resolve("katana").resolve("katana.exe");
        Label engine = new Label(Files.exists(katana) ? "katana: bundled" : "katana: not installed, Java crawler active");
        Spinner<Integer> depth = new Spinner<>(1, 10, 2);
        depth.setEditable(true);
        CheckBox jsRendering = new CheckBox("JavaScript rendering");
        Button start = new Button("Start");
        Button pause = new Button("Pause");
        Button resume = new Button("Resume");
        Button stop = new Button("Stop");
        Button export = new Button("Export Sitemap");
        Button clear = new Button("Clear");

        TableView<CrawlUrl> table = new TableView<>(found);
        table.setPlaceholder(UiUtil.emptyState("No crawl results", "Enter a target URL, choose depth, and start crawling to discover URLs.", null, null));
        table.getColumns().add(column("Depth", "depth", 70));
        table.getColumns().add(column("Status", "status", 80));
        table.getColumns().add(column("Protocol", "protocol", 100));
        table.getColumns().add(column("URL", "url", 760));

        start.setOnAction(event -> crawl(target.getText(), depth.getValue(), jsRendering.isSelected()));
        pause.setOnAction(event -> {
            paused.set(true);
            status.setText("Crawl paused");
        });
        resume.setOnAction(event -> {
            paused.set(false);
            status.setText("Crawl resumed");
        });
        stop.setOnAction(event -> {
            running.set(false);
            paused.set(false);
            status.setText("Stopping crawl...");
        });
        export.setOnAction(event -> exportSitemap());
        clear.setOnAction(event -> {
            found.clear();
            status.setText("Results cleared");
        });

        progress.setPrefWidth(140);
        HBox controls = new HBox(8, engine, target, new Label("Depth"), depth, jsRendering, start, pause, resume, stop, export, clear, progress, status);
        HBox.setHgrow(target, Priority.ALWAYS);
        VBox root = new VBox(8, controls, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        root.setPadding(new Insets(12));
        setContent(root);
    }

    private void crawl(String startUrl, int maxDepth, boolean jsRendering) {
        if (!running.compareAndSet(false, true)) {
            status.setText("Crawler already running");
            return;
        }
        progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        paused.set(false);
        status.setText("Starting crawl...");
        new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_2)
                        .connectTimeout(Duration.ofSeconds(15))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
                Queue<CrawlUrl> queue = new ArrayDeque<>();
                Set<String> seen = new HashSet<>();
                URI start = URI.create(startUrl);
                queue.add(new CrawlUrl(start.toString(), 0, 0, ""));
                seen.add(normalize(start));
                scopeControl.addInclude(start.getHost());
                Platform.runLater(() -> status.setText("Queued 1 URL for " + start.getHost()));

                while (running.get() && !queue.isEmpty()) {
                    waitIfPaused();
                    CrawlUrl current = queue.poll();
                    Platform.runLater(() -> status.setText("Crawling depth " + current.depth() + " | queue " + queue.size() + " | found " + found.size()));
                    if (current.depth() > maxDepth) {
                        continue;
                    }
                    Instant started = Instant.now();
                    HttpResponse<String> response;
                    try {
                        response = client.send(HttpRequest.newBuilder(URI.create(current.url()))
                                .timeout(Duration.ofSeconds(20))
                                .GET()
                                .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    } catch (Exception ex) {
                        addFound(new CrawlUrl(current.url(), current.depth(), 0, "ERR"));
                        continue;
                    }
                    String protocol = response.version() == HttpClient.Version.HTTP_2 ? "HTTP/2" : "HTTP/1.1";
                    CrawlUrl row = new CrawlUrl(current.url(), current.depth(), response.statusCode(), protocol);
                    addFound(row);
                    addToHistory(row, response, Duration.between(started, Instant.now()).toMillis());

                    if (current.depth() >= maxDepth || !isHtml(response)) {
                        continue;
                    }
                    for (String link : extractLinks(URI.create(current.url()), response.body(), jsRendering)) {
                        URI resolved = URI.create(link);
                        if (!sameHost(start, resolved)) {
                            continue;
                        }
                        String normalized = normalize(resolved);
                        if (seen.add(normalized)) {
                            scopeControl.addInclude(resolved.getHost());
                            queue.add(new CrawlUrl(resolved.toString(), current.depth() + 1, 0, ""));
                        }
                    }
                }
            } finally {
                boolean completed = running.get();
                running.set(false);
                Platform.runLater(() -> {
                    progress.setProgress(0);
                    status.setText((completed ? "Crawl complete. " : "Crawl stopped. ") + "Found " + found.size() + " URLs");
                    crawlNotification.accept(completed ? "Spider complete" : "Spider stopped",
                            startUrl + " discovered " + found.size() + " URLs");
                });
            }
        }, "venom-crawler").start();
    }

    private void waitIfPaused() {
        while (running.get() && paused.get()) {
            try {
                Thread.sleep(150);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                running.set(false);
                paused.set(false);
                return;
            }
        }
    }

    private void addFound(CrawlUrl row) {
        Platform.runLater(() -> {
            if (found.stream().noneMatch(existing -> existing.url().equals(row.url()))) {
                found.add(row);
            }
        });
    }

    private void addToHistory(CrawlUrl row, HttpResponse<String> response, long timeMs) {
        URI uri = URI.create(row.url());
        String path = (uri.getRawPath() == null || uri.getRawPath().isBlank()) ? "/" : uri.getRawPath();
        if (uri.getRawQuery() != null) {
            path += "?" + uri.getRawQuery();
        }
        String body = response.body() == null ? "" : response.body();
        StringBuilder rawResponse = new StringBuilder("HTTP ")
                .append(response.statusCode()).append("\r\n");
        response.headers().map().forEach((key, values) -> values.forEach(value -> rawResponse.append(key).append(": ").append(value).append("\r\n")));
        rawResponse.append("\r\n").append(body);
        HttpTransaction tx = new HttpTransaction(
                "GET",
                uri.getHost(),
                path,
                response.statusCode(),
                body.length(),
                response.headers().firstValue("Content-Type").orElse(""),
                row.protocol(),
                timeMs,
                "GET " + row.url() + " HTTP/1.1\r\nHost: " + uri.getHost() + "\r\n\r\n",
                rawResponse.toString(),
                Instant.now(),
                false,
                scopeControl.isInScope(row.url())
        );
        database.saveTransaction(tx);
        Platform.runLater(() -> history.add(0, tx));
    }

    private Set<String> extractLinks(URI base, String body, boolean jsRendering) {
        Set<String> links = new LinkedHashSet<>();
        Matcher matcher = LINK_PATTERN.matcher(body == null ? "" : body);
        while (matcher.find()) {
            addResolved(base, matcher.group(1), links);
        }
        if (jsRendering) {
            Matcher urls = Pattern.compile("(?i)['\\\"]((?:https?://|/)[^'\\\"\\s<>]+)['\\\"]").matcher(body == null ? "" : body);
            while (urls.find()) {
                addResolved(base, urls.group(1), links);
            }
        }
        return links;
    }

    private void addResolved(URI base, String link, Set<String> links) {
        try {
            if (link.startsWith("mailto:") || link.startsWith("tel:") || link.startsWith("javascript:")) {
                return;
            }
            links.add(base.resolve(link).normalize().toString());
        } catch (Exception ignored) {
        }
    }

    private boolean isHtml(HttpResponse<String> response) {
        return response.headers().firstValue("Content-Type").orElse("").toLowerCase().contains("html");
    }

    private boolean sameHost(URI start, URI other) {
        return start.getHost() != null && start.getHost().equalsIgnoreCase(other.getHost());
    }

    private String normalize(URI uri) {
        return uri.normalize().toString().replaceAll("#.*$", "");
    }

    private void exportSitemap() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Sitemap");
        chooser.setInitialFileName("sitemap.txt");
        java.io.File file = chooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            try {
                StringBuilder builder = new StringBuilder();
                for (CrawlUrl url : found) {
                    builder.append(url.url()).append('\n');
                }
                Files.writeString(file.toPath(), builder.toString());
                status.setText("Exported sitemap: " + file.getName());
            } catch (Exception ex) {
                status.setText("Export failed: " + ex.getMessage());
            }
        }
    }

    private TableColumn<CrawlUrl, Object> column(String title, String property, int width) {
        TableColumn<CrawlUrl, Object> column = new TableColumn<>(title);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        column.setPrefWidth(width);
        return column;
    }

    public record CrawlUrl(String url, int depth, int status, String protocol) {
        public String getUrl() {
            return url;
        }

        public int getDepth() {
            return depth;
        }

        public int getStatus() {
            return status;
        }

        public String getProtocol() {
            return protocol;
        }
    }
}
