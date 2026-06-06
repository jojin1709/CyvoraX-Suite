package com.venomproxy.ui;

import com.venomproxy.proxy.ScopeControl;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class SettingsTab extends Tab {
    public SettingsTab(MainWindow mainWindow, ScopeControl scopeControl) {
        super("Settings");
        setClosable(false);

        TextField host = new TextField(mainWindow.setting("proxy.host", "127.0.0.1"));
        TextField port = new TextField(mainWindow.setting("proxy.port", "8080"));
        TextField upstream = new TextField(mainWindow.setting("proxy.upstream", ""));
        upstream.setPromptText("host:port");
        TextField timeout = new TextField(mainWindow.setting("proxy.timeoutSeconds", "60"));
        TextField tlsProtocols = new TextField(mainWindow.setting("tls.protocols", "TLSv1.2,TLSv1.3"));
        TextField ignoreMime = new TextField(mainWindow.setting("ignore.mimePrefixes", "image/,font/,video/"));
        ComboBox<String> theme = new ComboBox<>();
        theme.getItems().addAll(mainWindow.themes());
        theme.getSelectionModel().select(mainWindow.currentTheme());
        theme.setOnAction(event -> mainWindow.applyTheme(theme.getSelectionModel().getSelectedItem()));
        CheckBox autoSave = new CheckBox("Auto-save history");
        autoSave.setSelected(Boolean.parseBoolean(mainWindow.setting("history.autoSave", "true")));
        CheckBox passthrough = new CheckBox("Out-of-scope passthrough");
        passthrough.setSelected(scopeControl.isOutOfScopePassthrough());
        TextArea includes = UiUtil.codeArea("Include domains, wildcards, IPs, regex:...");
        TextArea excludes = UiUtil.codeArea("Exclude domains, wildcards, IPs, regex:...");
        TextArea ignores = UiUtil.codeArea("Ignore domains, IPs, regex:... Traffic passes but is not saved/scanned.");
        includes.setText(scopeControl.includesAsText());
        excludes.setText(scopeControl.excludesAsText());
        ignores.setText(scopeControl.ignoresAsText());
        Label status = new Label("Settings loaded");
        bindSetting(mainWindow, "proxy.host", host);
        bindSetting(mainWindow, "proxy.port", port);
        bindSetting(mainWindow, "proxy.upstream", upstream);
        bindSetting(mainWindow, "proxy.timeoutSeconds", timeout);
        bindSetting(mainWindow, "tls.protocols", tlsProtocols);
        bindSetting(mainWindow, "ignore.mimePrefixes", ignoreMime);
        autoSave.selectedProperty().addListener((obs, old, value) -> mainWindow.saveSetting("history.autoSave", String.valueOf(value)));

        Button start = new Button("Start Listener");
        start.setOnAction(event -> {
            Integer parsedPort = parseInt(port.getText(), "Port", status);
            if (parsedPort == null) {
                return;
            }
            saveSettings(mainWindow, host, port, upstream, timeout, tlsProtocols, ignoreMime, autoSave);
            try {
                mainWindow.startProxy(host.getText(), parsedPort);
                status.setText("Proxy listener started");
            } catch (Exception ex) {
                status.setText("Start failed: " + ex.getMessage());
            }
        });
        Button stop = new Button("Stop Listener");
        stop.setOnAction(event -> {
            mainWindow.stopProxy();
            status.setText("Proxy listener stopped");
        });
        Button intercept = new Button("Toggle Intercept");
        intercept.setOnAction(event -> {
            mainWindow.setIntercept(!mainWindow.isInterceptEnabled());
            status.setText("Intercept " + (mainWindow.isInterceptEnabled() ? "enabled" : "disabled"));
        });
        Button applyScope = new Button("Apply Scope");
        applyScope.setOnAction(event -> {
            Integer parsedTimeout = parseInt(timeout.getText(), "Timeout", status);
            if (parsedTimeout == null) {
                return;
            }
            scopeControl.setIncludesFromText(includes.getText());
            scopeControl.setExcludesFromText(excludes.getText());
            scopeControl.setIgnoresFromText(ignores.getText());
            scopeControl.setOutOfScopePassthrough(passthrough.isSelected());
            mainWindow.saveScopeSettings(scopeControl);
            saveSettings(mainWindow, host, port, upstream, timeout, tlsProtocols, ignoreMime, autoSave);
            try {
                mainWindow.configureNetwork(upstream.getText(), parsedTimeout);
                status.setText("Scope and network settings applied");
            } catch (Exception ex) {
                status.setText("Apply failed: " + ex.getMessage());
            }
        });

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.add(new Label("Listener IP"), 0, 0);
        form.add(host, 1, 0);
        form.add(new Label("Port"), 0, 1);
        form.add(port, 1, 1);
        form.add(new Label("Upstream proxy"), 0, 2);
        form.add(upstream, 1, 2);
        form.add(new Label("Timeout seconds"), 0, 3);
        form.add(timeout, 1, 3);
        form.add(new Label("TLS protocols"), 0, 4);
        form.add(tlsProtocols, 1, 4);
        form.add(new Label("Ignore MIME prefixes"), 0, 5);
        form.add(ignoreMime, 1, 5);
        form.add(new Label("Theme"), 0, 6);
        form.add(theme, 1, 6);
        form.add(autoSave, 1, 7);
        form.add(passthrough, 1, 8);
        form.add(start, 2, 0);
        form.add(stop, 2, 1);
        form.add(intercept, 2, 2);

        VBox scope = new VBox(8,
                new Label("Include Scope"), includes,
                new Label("Exclude Scope"), excludes,
                new Label("Ignore List"), ignores,
                applyScope, status);
        VBox.setVgrow(includes, Priority.ALWAYS);
        VBox.setVgrow(excludes, Priority.ALWAYS);
        VBox.setVgrow(ignores, Priority.ALWAYS);
        VBox root = new VBox(14, form, scope);
        root.setPadding(new Insets(16));
        setContent(root);
    }

    private void saveSettings(MainWindow mainWindow, TextField host, TextField port, TextField upstream,
                              TextField timeout, TextField tlsProtocols, TextField ignoreMime, CheckBox autoSave) {
        mainWindow.saveSetting("proxy.host", host.getText());
        mainWindow.saveSetting("proxy.port", port.getText());
        mainWindow.saveSetting("proxy.upstream", upstream.getText());
        mainWindow.saveSetting("proxy.timeoutSeconds", timeout.getText());
        mainWindow.saveSetting("tls.protocols", tlsProtocols.getText());
        mainWindow.saveSetting("ignore.mimePrefixes", ignoreMime.getText());
        mainWindow.saveSetting("history.autoSave", String.valueOf(autoSave.isSelected()));
    }

    private void bindSetting(MainWindow mainWindow, String key, TextField field) {
        field.textProperty().addListener((obs, old, value) -> mainWindow.saveSetting(key, value));
    }

    private Integer parseInt(String value, String label, Label status) {
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0) {
                status.setText(label + " must be greater than zero.");
                return null;
            }
            return parsed;
        } catch (Exception ex) {
            status.setText(label + " must be a number.");
            return null;
        }
    }
}
