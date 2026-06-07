package com.venomproxy.ui;

import com.venomproxy.diagnostics.CrashReporter;
import com.venomproxy.proxy.ScopeControl;
import com.venomproxy.update.UpdateInfo;
import com.venomproxy.update.UpdateService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class SettingsTab extends Tab {
    public SettingsTab(MainWindow mainWindow, ScopeControl scopeControl, UpdateService updateService,
                       CrashReporter crashReporter, String appVersion) {
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

        GridPane listenerForm = form();
        listenerForm.add(new Label("Listener IP"), 0, 0);
        listenerForm.add(host, 1, 0);
        listenerForm.add(new Label("Port"), 0, 1);
        listenerForm.add(port, 1, 1);
        listenerForm.add(new Label("Upstream proxy"), 0, 2);
        listenerForm.add(upstream, 1, 2);
        listenerForm.add(new Label("Timeout seconds"), 0, 3);
        listenerForm.add(timeout, 1, 3);
        listenerForm.add(new Label("TLS protocols"), 0, 4);
        listenerForm.add(tlsProtocols, 1, 4);
        listenerForm.add(new Label("Ignore MIME prefixes"), 0, 5);
        listenerForm.add(ignoreMime, 1, 5);
        listenerForm.add(new Label("Theme"), 0, 6);
        listenerForm.add(theme, 1, 6);
        listenerForm.add(autoSave, 1, 7);
        listenerForm.add(passthrough, 1, 8);
        listenerForm.add(new HBox(8, start, stop, intercept), 1, 9);

        VBox listenerSection = section("Listener & Appearance", listenerForm, status);
        VBox scopeSection = section("Scope Control",
                new Label("Include Scope"), includes,
                new Label("Exclude Scope"), excludes,
                new Label("Ignore List"), ignores,
                applyScope);
        VBox.setVgrow(includes, Priority.ALWAYS);
        VBox.setVgrow(excludes, Priority.ALWAYS);
        VBox.setVgrow(ignores, Priority.ALWAYS);
        VBox updates = updatesSection(mainWindow, updateService, appVersion);
        VBox diagnostics = diagnosticsSection(mainWindow, crashReporter);
        VBox shortcuts = shortcutsSection(mainWindow);

        ListView<String> categories = new ListView<>(FXCollections.observableArrayList(
                "Listener", "Scope", "Updates", "Diagnostics", "Shortcuts"));
        categories.getStyleClass().add("settings-sidebar");
        StackPane content = new StackPane(listenerSection);
        content.getStyleClass().add("settings-content");
        categories.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            Node panel = switch (selected == null ? "Listener" : selected) {
                case "Scope" -> scopeSection;
                case "Updates" -> updates;
                case "Diagnostics" -> diagnostics;
                case "Shortcuts" -> shortcuts;
                default -> listenerSection;
            };
            content.getChildren().setAll(panel);
            mainWindow.saveSetting("settings.category", selected == null ? "Listener" : selected);
        });
        categories.getSelectionModel().select(mainWindow.setting("settings.category", "Listener"));

        SplitPane split = new SplitPane(categories, content);
        split.setDividerPositions(readDivider(mainWindow.setting("layout.settings.main", "0.20"), 0.20));
        Platform.runLater(() -> split.getDividers().forEach(divider -> divider.positionProperty().addListener((obs, old, value) ->
                mainWindow.saveSetting("layout.settings.main", String.format(java.util.Locale.ROOT, "%.4f", value.doubleValue())))));
        VBox root = new VBox(split);
        VBox.setVgrow(split, Priority.ALWAYS);
        root.setPadding(new Insets(8));
        setContent(root);
    }

    private GridPane form() {
        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        return form;
    }

    private VBox section(String title, Node... nodes) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("panel-title");
        VBox box = new VBox(10, titleLabel);
        box.getChildren().addAll(nodes);
        box.getStyleClass().add("desktop-panel");
        box.setPadding(new Insets(12));
        return box;
    }

    private double readDivider(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (RuntimeException ex) {
            return fallback;
        }
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

    private VBox updatesSection(MainWindow mainWindow, UpdateService updateService, String appVersion) {
        Label currentVersion = new Label(appVersion);
        Label latestVersion = new Label(mainWindow.setting("updates.latestVersion", "Unknown"));
        Label status = new Label("Update checks preserve workspaces, settings, plugins, and reports. Installers are downloaded only.");
        TextArea notes = UiUtil.codeArea("Release notes");
        notes.setEditable(false);
        notes.setPrefRowCount(9);
        ProgressBar progress = new ProgressBar(0);
        progress.setMaxWidth(Double.MAX_VALUE);
        CheckBox startupCheck = new CheckBox("Check for updates on startup");
        startupCheck.setSelected(Boolean.parseBoolean(mainWindow.setting("updates.checkOnStartup", "true")));
        startupCheck.selectedProperty().addListener((obs, old, value) -> mainWindow.saveSetting("updates.checkOnStartup", String.valueOf(value)));
        Button check = new Button("Check for Updates");
        Button download = new Button("Download Update");
        download.setDisable(true);
        final UpdateInfo[] lastUpdate = new UpdateInfo[1];

        check.setOnAction(event -> {
            status.setText("Checking GitHub Releases...");
            check.setDisable(true);
            Task<UpdateInfo> task = new Task<>() {
                @Override
                protected UpdateInfo call() throws Exception {
                    return updateService.checkForUpdates();
                }
            };
            task.setOnSucceeded(done -> {
                UpdateInfo info = task.getValue();
                lastUpdate[0] = info;
                latestVersion.setText(info.latestVersion());
                mainWindow.saveSetting("updates.latestVersion", info.latestVersion());
                notes.setText(info.releaseNotes() == null || info.releaseNotes().isBlank()
                        ? "No release notes were provided." : info.releaseNotes());
                download.setDisable(info.downloadUrl() == null || info.downloadUrl().isBlank());
                status.setText(info.updateAvailable() ? "Update available: " + info.latestVersion() : "CyvoraX Suite is up to date.");
                check.setDisable(false);
            });
            task.setOnFailed(done -> {
                status.setText("Update check failed: " + task.getException().getMessage());
                check.setDisable(false);
            });
            Thread thread = new Thread(task, "settings-update-check");
            thread.setDaemon(true);
            thread.start();
        });

        download.setOnAction(event -> {
            if (lastUpdate[0] == null) {
                status.setText("Check for updates before downloading.");
                return;
            }
            download.setDisable(true);
            status.setText("Downloading installer...");
            Task<java.nio.file.Path> task = new Task<>() {
                @Override
                protected java.nio.file.Path call() throws Exception {
                    return updateService.downloadInstaller(lastUpdate[0], value -> Platform.runLater(() -> progress.setProgress(value)));
                }
            };
            task.setOnSucceeded(done -> {
                status.setText("Downloaded: " + task.getValue());
                download.setDisable(false);
            });
            task.setOnFailed(done -> {
                status.setText("Download failed: " + task.getException().getMessage());
                download.setDisable(false);
            });
            Thread thread = new Thread(task, "settings-update-download");
            thread.setDaemon(true);
            thread.start();
        });

        GridPane versions = form();
        versions.add(new Label("Current Version"), 0, 0);
        versions.add(currentVersion, 1, 0);
        versions.add(new Label("Latest Version"), 0, 1);
        versions.add(latestVersion, 1, 1);
        return section("Updates", versions, startupCheck, new HBox(8, check, download), progress, notes, status);
    }

    private VBox diagnosticsSection(MainWindow mainWindow, CrashReporter crashReporter) {
        Label folder = new Label(crashReporter.getReportsDirectory().toString());
        Button reports = new Button("View Crash Reports");
        reports.setOnAction(event -> mainWindow.showCrashReports());
        return section("Diagnostics", new Label("Crash reports folder"), folder, reports);
    }

    private VBox shortcutsSection(MainWindow mainWindow) {
        Button help = new Button("Show Shortcuts");
        help.setOnAction(event -> mainWindow.showShortcutHelp());
        return section("Keyboard Shortcuts",
                new Label("Quick Search, Command Palette, module switching, and workspace save shortcuts are active."),
                help);
    }
}
