package com.venomproxy.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class ProxySettingsTab extends Tab {
    private final MainWindow mainWindow;
    private final TextField listenAddress;
    private final TextField listenPort;
    private final TextField upstreamHost;
    private final TextField upstreamPort;
    private final TextField timeoutSeconds;
    private final Label status = new Label("Proxy settings loaded");

    public ProxySettingsTab(MainWindow mainWindow) {
        super("Proxy Settings");
        this.mainWindow = mainWindow;
        setClosable(false);

        listenAddress = new TextField(mainWindow.setting("proxy.host", "127.0.0.1"));
        listenPort = new TextField(mainWindow.setting("proxy.port", "8080"));
        String[] upstream = splitUpstream(mainWindow.setting("proxy.upstream", ""));
        upstreamHost = new TextField(upstream[0]);
        upstreamPort = new TextField(upstream[1]);
        timeoutSeconds = new TextField(mainWindow.setting("proxy.timeoutSeconds", "60"));

        listenAddress.setPromptText("127.0.0.1");
        listenPort.setPromptText("8080");
        upstreamHost.setPromptText("Optional upstream host");
        upstreamPort.setPromptText("Optional port");
        timeoutSeconds.setPromptText("60");

        Button applyNetwork = new Button("Apply Network");
        applyNetwork.setOnAction(event -> applyNetworkSettings(false));
        Button applyRestart = new Button("Apply & Restart");
        applyRestart.getStyleClass().add("button-primary");
        applyRestart.setOnAction(event -> applyNetworkSettings(true));
        Button start = new Button("Start Listener");
        start.setOnAction(event -> startListener());
        Button stop = new Button("Stop Listener");
        stop.setOnAction(event -> {
            mainWindow.stopProxy();
            status.setText("Proxy listener stopped");
        });

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.add(label("Listen address"), 0, 0);
        form.add(listenAddress, 1, 0);
        form.add(label("Port"), 0, 1);
        form.add(listenPort, 1, 1);
        form.add(label("Upstream host"), 0, 2);
        form.add(upstreamHost, 1, 2);
        form.add(label("Upstream port"), 0, 3);
        form.add(upstreamPort, 1, 3);
        form.add(label("Timeout seconds"), 0, 4);
        form.add(timeoutSeconds, 1, 4);
        form.add(new HBox(8, applyNetwork, applyRestart, start, stop), 1, 5);

        HBox.setHgrow(listenAddress, Priority.ALWAYS);
        HBox.setHgrow(upstreamHost, Priority.ALWAYS);
        listenAddress.setMaxWidth(Double.MAX_VALUE);
        upstreamHost.setMaxWidth(Double.MAX_VALUE);

        Label title = new Label("Proxy Listener");
        title.getStyleClass().add("panel-title");
        VBox panel = new VBox(10, title, form, status);
        panel.getStyleClass().addAll("desktop-panel", "cx-panel");
        panel.setMaxWidth(640);
        panel.setPadding(new Insets(16));

        Label hint = new Label("Apply & Restart saves the listener, upstream proxy, and timeout values, then restarts the local proxy listener.");
        hint.getStyleClass().add("empty-state-sub");
        VBox root = new VBox(12, panel, hint);
        root.setPadding(new Insets(16));
        setContent(root);
    }

    private Label label(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("filter-label");
        return label;
    }

    private void applyNetworkSettings(boolean restart) {
        Integer port = parsePort(listenPort.getText(), "Listener port");
        Integer timeout = parsePositiveInt(timeoutSeconds.getText(), "Timeout seconds");
        if (port == null || timeout == null || !validateUpstream()) {
            return;
        }
        saveSettings();
        try {
            mainWindow.configureNetwork(upstreamValue(), timeout);
            if (restart) {
                mainWindow.stopProxy();
                mainWindow.startProxy(listenAddress.getText().trim(), port);
                status.setText("Proxy restarted on " + listenAddress.getText().trim() + ":" + port);
            } else {
                status.setText("Network settings applied");
            }
        } catch (Exception ex) {
            status.setText("Apply failed: " + ex.getMessage());
        }
    }

    private void startListener() {
        Integer port = parsePort(listenPort.getText(), "Listener port");
        if (port == null || !validateUpstream()) {
            return;
        }
        saveSettings();
        try {
            mainWindow.startProxy(listenAddress.getText().trim(), port);
            status.setText("Proxy listener started on " + listenAddress.getText().trim() + ":" + port);
        } catch (Exception ex) {
            status.setText("Start failed: " + ex.getMessage());
        }
    }

    private void saveSettings() {
        mainWindow.saveSetting("proxy.host", listenAddress.getText().trim());
        mainWindow.saveSetting("proxy.port", listenPort.getText().trim());
        mainWindow.saveSetting("proxy.upstream", upstreamValue());
        mainWindow.saveSetting("proxy.timeoutSeconds", timeoutSeconds.getText().trim());
    }

    private boolean validateUpstream() {
        boolean hostBlank = upstreamHost.getText() == null || upstreamHost.getText().isBlank();
        boolean portBlank = upstreamPort.getText() == null || upstreamPort.getText().isBlank();
        if (hostBlank && portBlank) {
            return true;
        }
        if (hostBlank || portBlank) {
            status.setText("Upstream proxy requires both host and port.");
            return false;
        }
        return parsePort(upstreamPort.getText(), "Upstream port") != null;
    }

    private String upstreamValue() {
        if (upstreamHost.getText() == null || upstreamHost.getText().isBlank()) {
            return "";
        }
        return upstreamHost.getText().trim() + ":" + upstreamPort.getText().trim();
    }

    private Integer parsePort(String value, String label) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            if (parsed > 0 && parsed <= 65535) {
                return parsed;
            }
        } catch (RuntimeException ignored) {
        }
        status.setText(label + " must be a number from 1 to 65535.");
        return null;
    }

    private Integer parsePositiveInt(String value, String label) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            if (parsed > 0) {
                return parsed;
            }
        } catch (RuntimeException ignored) {
        }
        status.setText(label + " must be greater than zero.");
        return null;
    }

    private String[] splitUpstream(String upstream) {
        if (upstream == null || upstream.isBlank()) {
            return new String[]{"", ""};
        }
        String[] parts = upstream.trim().split(":", 2);
        return new String[]{parts[0], parts.length > 1 ? parts[1] : ""};
    }
}
