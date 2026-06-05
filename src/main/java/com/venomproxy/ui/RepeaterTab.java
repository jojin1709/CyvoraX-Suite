package com.venomproxy.ui;

import com.venomproxy.model.HttpTransaction;
import com.venomproxy.model.RequestData;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RepeaterTab extends Tab {
    private final TabPane requestTabs = new TabPane();
    private final OkHttpClient client = new OkHttpClient.Builder()
            .followRedirects(false)
            .connectTimeout(Duration.ofSeconds(20))
            .readTimeout(Duration.ofSeconds(60))
            .build();
    private int counter = 1;

    public RepeaterTab() {
        super("Repeater");
        setClosable(false);
        Button newTab = new Button("New");
        newTab.setOnAction(event -> addRequestTab("GET http://example.com/ HTTP/1.1\r\nHost: example.com\r\n\r\n"));
        VBox root = new VBox(8, new HBox(8, newTab), requestTabs);
        VBox.setVgrow(requestTabs, Priority.ALWAYS);
        root.setPadding(new Insets(12));
        setContent(root);
        addRequestTab("GET http://example.com/ HTTP/1.1\r\nHost: example.com\r\n\r\n");
    }

    public void openTransaction(HttpTransaction tx) {
        addRequestTab(tx.getRequestRaw());
    }

    private void addRequestTab(String raw) {
        TextArea request = UiUtil.codeArea("Raw request");
        request.setText(raw);
        TextArea rawResponse = UiUtil.codeArea("Raw response");
        TextArea prettyResponse = UiUtil.codeArea("Pretty response");
        TextArea hexResponse = UiUtil.codeArea("Hex response");
        Label status = new Label("Ready");
        Button send = new Button("Send");
        Button back = new Button("<");
        Button forward = new Button(">");
        List<String> localHistory = new ArrayList<>();
        final int[] historyIndex = {-1};

        TabPane responseTabs = new TabPane(
                new Tab("Raw", rawResponse),
                new Tab("Pretty", prettyResponse),
                new Tab("Hex", hexResponse)
        );
        responseTabs.getTabs().forEach(tab -> tab.setClosable(false));
        SplitPane split = new SplitPane(request, responseTabs);
        split.setDividerPositions(0.5);

        send.setOnAction(event -> {
            localHistory.add(request.getText());
            historyIndex[0] = localHistory.size() - 1;
            status.setText("Sending...");
            new Thread(() -> sendRequest(request.getText(), rawResponse, prettyResponse, hexResponse, status), "repeater-send").start();
        });
        back.setOnAction(event -> {
            if (historyIndex[0] > 0) {
                historyIndex[0]--;
                request.setText(localHistory.get(historyIndex[0]));
            }
        });
        forward.setOnAction(event -> {
            if (historyIndex[0] < localHistory.size() - 1) {
                historyIndex[0]++;
                request.setText(localHistory.get(historyIndex[0]));
            }
        });

        VBox content = new VBox(8, new HBox(8, send, back, forward, status), split);
        VBox.setVgrow(split, Priority.ALWAYS);
        Tab tab = new Tab("Req " + counter++, content);
        requestTabs.getTabs().add(tab);
        requestTabs.getSelectionModel().select(tab);
    }

    private void sendRequest(String raw, TextArea rawResponse, TextArea prettyResponse, TextArea hexResponse, Label status) {
        Instant started = Instant.now();
        try {
            RequestData data = RequestData.fromRaw(raw);
            try (Response response = client.newCall(toOkHttp(data)).execute()) {
                ResponseBody body = response.body();
                byte[] bytes = body == null ? new byte[0] : body.bytes();
                String responseText = rawResponse(response, bytes);
                javafx.application.Platform.runLater(() -> {
                    rawResponse.setText(responseText);
                    prettyResponse.setText(new String(bytes, StandardCharsets.UTF_8));
                    hexResponse.setText(UiUtil.hex(bytes));
                    status.setText(response.code() + " " + protocolName(response.protocol()) + " in " + Duration.between(started, Instant.now()).toMillis() + " ms");
                });
            }
        } catch (Exception ex) {
            javafx.application.Platform.runLater(() -> status.setText("Error: " + ex.getMessage()));
        }
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
        builder.append("\r\n").append(new String(body, StandardCharsets.UTF_8));
        return builder.toString();
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
