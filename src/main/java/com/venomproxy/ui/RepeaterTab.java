package com.venomproxy.ui;

import com.venomproxy.db.Database;
import com.venomproxy.model.HttpTransaction;
import com.venomproxy.model.RequestData;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class RepeaterTab extends Tab {
    private static final String SETTING_TABS = "repeater.tabs";
    private static final String DEFAULT_REQUEST = "GET http://example.com/ HTTP/1.1\r\nHost: example.com\r\n\r\n";

    private final Database database;
    private final TabPane requestTabs = new TabPane();
    private final OkHttpClient client = new OkHttpClient.Builder()
            .followRedirects(false)
            .connectTimeout(Duration.ofSeconds(20))
            .readTimeout(Duration.ofSeconds(60))
            .build();
    private final AtomicBoolean restoring = new AtomicBoolean(false);
    private int counter = 1;

    public RepeaterTab(Database database) {
        super("Repeater");
        this.database = database;
        setClosable(false);
        Button newTab = new Button("New");
        newTab.setOnAction(event -> addRequestTab(DEFAULT_REQUEST, true));
        VBox root = new VBox(8, new HBox(8, newTab), requestTabs);
        VBox.setVgrow(requestTabs, Priority.ALWAYS);
        root.setPadding(new Insets(12));
        setContent(root);
        restoreTabs();
    }

    public void openTransaction(HttpTransaction tx) {
        addRequestTab(tx.getRequestRaw(), schemeFromTransaction(tx), true);
    }

    public int selectedRequestTabIndex() {
        return Math.max(0, requestTabs.getSelectionModel().getSelectedIndex());
    }

    public void selectRequestTabIndex(int index) {
        if (requestTabs.getTabs().isEmpty()) {
            return;
        }
        int safeIndex = Math.max(0, Math.min(index, requestTabs.getTabs().size() - 1));
        requestTabs.getSelectionModel().select(safeIndex);
    }

    private void restoreTabs() {
        restoring.set(true);
        try {
            List<String> restored = decodeTabs(database.getSetting(SETTING_TABS, ""));
            if (restored.isEmpty()) {
                addRequestTab(DEFAULT_REQUEST, false);
            } else {
                restored.forEach(raw -> addRequestTab(raw, false));
            }
        } finally {
            restoring.set(false);
            persistTabs();
        }
    }

    private void addRequestTab(String raw, boolean persist) {
        addRequestTab(raw, "http", persist);
    }

    private void addRequestTab(String raw, String defaultScheme, boolean persist) {
        TextArea request = UiUtil.codeArea("Raw request");
        request.setText(raw);
        TextArea rawResponse = UiUtil.codeArea("Raw response");
        TextArea prettyResponse = UiUtil.codeArea("Pretty response");
        TextArea hexResponse = UiUtil.codeArea("Hex response");
        HttpInspectorPane inspector = new HttpInspectorPane();
        Label status = new Label("Ready");
        Label responseSummary = new Label("-");
        responseSummary.getStyleClass().add("response-summary");
        TextField targetField = new TextField(targetFromRaw(raw, defaultScheme));
        targetField.setPromptText("https://example.com:443");
        targetField.getStyleClass().add("repeater-target-field");
        Label targetLabel = new Label("Target:");
        targetLabel.getStyleClass().add("filter-label");
        ComboBox<String> httpVersion = new ComboBox<>();
        httpVersion.getItems().addAll("HTTP/1.1", "HTTP/2");
        httpVersion.getSelectionModel().select("HTTP/1.1");
        httpVersion.getStyleClass().add("toolbar-icon-button");
        httpVersion.setMinWidth(115);
        httpVersion.setPrefWidth(130);
        Button send = new Button("Send");
        send.getStyleClass().add("btn-send");
        send.setDefaultButton(true);
        Button back = new Button("<");
        Button forward = new Button(">");
        TextField find = new TextField();
        find.setPromptText("Find");
        TextField replace = new TextField();
        replace.setPromptText("Replace");
        Button replaceAll = new Button("Replace All");
        List<String> localHistory = new ArrayList<>();
        final int[] historyIndex = {-1};

        request.textProperty().addListener((obs, old, value) -> {
            persistTabs();
            inspector.inspect(value, rawResponse.getText(), "");
        });
        replaceAll.setOnAction(event -> {
            if (!find.getText().isBlank()) {
                request.setText(request.getText().replace(find.getText(), replace.getText()));
                status.setText("Replaced matches for " + find.getText());
            }
        });
        inspector.inspect(raw, "", "");

        TabPane responseTabs = new TabPane(
                new Tab("Raw", rawResponse),
                new Tab("Pretty", prettyResponse),
                new Tab("Hex", hexResponse)
        );
        responseTabs.getTabs().forEach(tab -> tab.setClosable(false));
        VBox responsePane = new VBox(6, responseSummary, responseTabs);
        VBox.setVgrow(responseTabs, Priority.ALWAYS);
        SplitPane requestResponse = new SplitPane(request, responsePane);
        requestResponse.setDividerPositions(0.5);
        UiUtil.bindDividerPositions(database, "layout.repeater.requestResponse", requestResponse, 0.5);
        SplitPane split = new SplitPane(requestResponse, inspector);
        split.setDividerPositions(0.74);
        UiUtil.bindDividerPositions(database, "layout.repeater.inspector", split, 0.74);

        send.setOnAction(event -> {
            localHistory.add(request.getText());
            historyIndex[0] = localHistory.size() - 1;
            status.setText("Sending...");
            String selectedVersion = httpVersion.getSelectionModel().getSelectedItem();
            String scheme = schemeFromTarget(targetField.getText(), defaultScheme);
            new Thread(() -> sendRequest(request.getText(), scheme, selectedVersion, rawResponse, prettyResponse,
                    hexResponse, inspector, status, responseSummary), "repeater-send").start();
        });
        back.setOnAction(event -> {
            if (historyIndex[0] > 0) {
                historyIndex[0]--;
                request.setText(localHistory.get(historyIndex[0]));
                status.setText("History " + (historyIndex[0] + 1) + "/" + localHistory.size());
            }
        });
        forward.setOnAction(event -> {
            if (historyIndex[0] < localHistory.size() - 1) {
                historyIndex[0]++;
                request.setText(localHistory.get(historyIndex[0]));
                status.setText("History " + (historyIndex[0] + 1) + "/" + localHistory.size());
            }
        });

        HBox requestToolbar = new HBox(8, targetLabel, targetField, httpVersion, spacer(), send, back, forward,
                find, replace, replaceAll, status);
        requestToolbar.getStyleClass().add("repeater-toolbar");
        requestToolbar.setPadding(new Insets(6, 10, 6, 10));
        HBox.setHgrow(targetField, Priority.ALWAYS);
        VBox content = new VBox(8, requestToolbar, split);
        VBox.setVgrow(split, Priority.ALWAYS);
        Tab tab = new Tab("Req " + counter++, content);
        tab.setClosable(true);
        tab.setOnClosed(event -> persistTabs());
        requestTabs.getTabs().add(tab);
        requestTabs.getSelectionModel().select(tab);
        if (persist) {
            persistTabs();
        }
    }

    private void persistTabs() {
        if (restoring.get() || database == null) {
            return;
        }
        List<String> raws = requestTabs.getTabs().stream()
                .map(this::requestText)
                .filter(value -> value != null && !value.isBlank())
                .toList();
        database.setSetting(SETTING_TABS, encodeTabs(raws));
    }

    private String requestText(Tab tab) {
        if (!(tab.getContent() instanceof VBox vbox) || vbox.getChildren().size() < 2) {
            return "";
        }
        if (!(vbox.getChildren().get(1) instanceof SplitPane split) || split.getItems().isEmpty()) {
            return "";
        }
        if (!(split.getItems().get(0) instanceof SplitPane requestResponse) || requestResponse.getItems().isEmpty()) {
            return "";
        }
        return requestResponse.getItems().get(0) instanceof TextArea area ? area.getText() : "";
    }

    private String encodeTabs(List<String> raws) {
        return raws.stream()
                .map(value -> Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private List<String> decodeTabs(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> tabs = new ArrayList<>();
        for (String line : value.split("\\R")) {
            if (!line.isBlank()) {
                try {
                    tabs.add(new String(Base64.getDecoder().decode(line.trim()), StandardCharsets.UTF_8));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return tabs;
    }

    private void sendRequest(String raw, String defaultScheme, String httpVersion, TextArea rawResponse,
                             TextArea prettyResponse, TextArea hexResponse, HttpInspectorPane inspector,
                             Label status, Label responseSummary) {
        Instant started = Instant.now();
        try {
            RequestData data = RequestData.fromRaw(raw, defaultScheme);
            OkHttpClient selectedClient = clientFor(httpVersion);
            try (Response response = selectedClient.newCall(toOkHttp(data)).execute()) {
                ResponseBody body = response.body();
                byte[] bytes = body == null ? new byte[0] : body.bytes();
                String responseText = rawResponse(response, bytes);
                long elapsedMs = Duration.between(started, Instant.now()).toMillis();
                Platform.runLater(() -> {
                    rawResponse.setText(responseText);
                    prettyResponse.setText(new String(bytes, StandardCharsets.UTF_8));
                    hexResponse.setText(UiUtil.hex(bytes));
                    inspector.inspect(raw, responseText, "");
                    status.setText(response.code() + " " + protocolName(response.protocol()) + " in "
                            + elapsedMs + " ms | " + bytes.length + " bytes");
                    responseSummary.setText(response.code() + " " + response.message()
                            + " | " + formatBytes(bytes.length)
                            + " | " + elapsedMs + " ms");
                });
            }
        } catch (Exception ex) {
            Platform.runLater(() -> status.setText("Error: " + ex.getMessage()));
        }
    }

    private OkHttpClient clientFor(String httpVersion) {
        if ("HTTP/2".equals(httpVersion)) {
            return client.newBuilder()
                    .protocols(List.of(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
                    .build();
        }
        return client.newBuilder()
                .protocols(List.of(okhttp3.Protocol.HTTP_1_1))
                .build();
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
            body = RequestBody.create(data.getBody(), MediaType.parse(data.getHeaders().getOrDefault("Content-Type", "application/octet-stream")));
        }
        return new Request.Builder().url(data.getUrl()).headers(headers.build())
                .method(data.getMethod().toUpperCase(Locale.ROOT), body).build();
    }

    private String rawResponse(Response response, byte[] body) {
        StringBuilder builder = new StringBuilder();
        builder.append("HTTP/1.1 ").append(response.code()).append(' ').append(response.message()).append("\r\n");
        response.headers().forEach(pair -> builder.append(pair.getFirst()).append(": ").append(pair.getSecond()).append("\r\n"));
        builder.append("\r\n").append(new String(body, StandardCharsets.ISO_8859_1));
        return builder.toString();
    }

    private String schemeFromTransaction(HttpTransaction tx) {
        if (tx != null && "https".equalsIgnoreCase(tx.getScheme())) {
            return "https";
        }
        return "http";
    }

    private String targetFromRaw(String raw, String defaultScheme) {
        try {
            URI uri = URI.create(RequestData.fromRaw(raw, defaultScheme).getUrl());
            int port = uri.getPort();
            String host = uri.getHost() == null ? "" : uri.getHost();
            if (host.isBlank()) {
                return "";
            }
            return uri.getScheme() + "://" + host + (port > 0 ? ":" + port : "");
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private String schemeFromTarget(String target, String fallback) {
        if (target != null && target.toLowerCase(Locale.ROOT).startsWith("https://")) {
            return "https";
        }
        if (target != null && target.toLowerCase(Locale.ROOT).startsWith("http://")) {
            return "http";
        }
        return fallback;
    }

    private String formatBytes(int bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes / 1024.0;
        if (value < 1024) {
            return String.format(Locale.ROOT, "%.1f KB", value);
        }
        return String.format(Locale.ROOT, "%.2f MB", value / 1024.0);
    }

    private HBox spacer() {
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private String protocolName(okhttp3.Protocol protocol) {
        return switch (protocol) {
            case HTTP_2, H2_PRIOR_KNOWLEDGE -> "HTTP/2";
            case HTTP_1_0 -> "HTTP/1.0";
            case HTTP_1_1 -> "HTTP/1.1";
            default -> protocol.toString();
        };
    }
}
