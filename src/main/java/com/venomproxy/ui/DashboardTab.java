package com.venomproxy.ui;

import com.venomproxy.db.Database;
import com.venomproxy.model.Finding;
import com.venomproxy.model.HttpTransaction;
import com.venomproxy.model.LogEntry;
import com.venomproxy.plugins.PluginLoader;
import com.venomproxy.proxy.CertManager;
import com.venomproxy.session.SessionRecorder;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;

public class DashboardTab extends Tab {
    private final Database database;
    private final ObservableList<HttpTransaction> history;
    private final ObservableList<Finding> findings;
    private final ObservableList<LogEntry> logs;
    private final CertManager certManager;
    private final PluginLoader pluginLoader;
    private final SessionRecorder sessionRecorder;
    private final Label requestsValue = new Label("0");
    private final Label hostsValue = new Label("0");
    private final Label findingsValue = new Label("0");
    private final Label sessionsValue = new Label("0");
    private final Label certificateValue = new Label("Unknown");
    private final Label pluginValue = new Label("0");
    private final Label proxyState = new Label("Proxy off");
    private final Label interceptState = new Label("Intercept off");
    private final Label uptimeValue = new Label("0s");
    private final Label actionStatus = new Label("Ready");
    private final VBox recentActivity = new VBox(6);
    private final VBox recentFindings = new VBox(6);
    private final VBox runningTasks = new VBox(6);
    private final Instant started = Instant.now();
    private boolean proxyRunning;
    private boolean interceptEnabled;
    private long liveRequestCount;

    public DashboardTab(MainWindow mainWindow, Database database, ObservableList<HttpTransaction> history,
                        ObservableList<Finding> findings, ObservableList<LogEntry> logs,
                        CertManager certManager, PluginLoader pluginLoader, SessionRecorder sessionRecorder) {
        super("Dashboard");
        this.database = database;
        this.history = history;
        this.findings = findings;
        this.logs = logs;
        this.certManager = certManager;
        this.pluginLoader = pluginLoader;
        this.sessionRecorder = sessionRecorder;
        setClosable(false);

        Button start = new Button("Start Proxy");
        start.setOnAction(event -> {
            try {
                mainWindow.startProxy("127.0.0.1", 8080);
                actionStatus.setText("Proxy started");
            } catch (Exception ex) {
                actionStatus.setText("Proxy start failed: " + ex.getMessage());
            }
        });
        Button stop = new Button("Stop Proxy");
        stop.setOnAction(event -> {
            mainWindow.stopProxy();
            actionStatus.setText("Proxy stopped");
        });
        Button cert = new Button("Export CA Cert");
        cert.setOnAction(event -> exportCert());

        HBox header = new HBox(14, logoView(), titleBlock(), quickActions(start, stop, cert));
        header.getStyleClass().add("dashboard-header");

        GridPane cards = new GridPane();
        cards.getStyleClass().add("dashboard-cards");
        cards.setHgap(12);
        cards.setVgap(12);
        cards.add(card("Requests", requestsValue), 0, 0);
        cards.add(card("Hosts", hostsValue), 1, 0);
        cards.add(card("Findings", findingsValue), 2, 0);
        cards.add(card("Sessions", sessionsValue), 0, 1);
        cards.add(card("Certificate Status", certificateValue), 1, 1);
        cards.add(card("Plugin Count", pluginValue), 2, 1);

        GridPane panels = new GridPane();
        panels.getStyleClass().add("dashboard-panels");
        panels.setHgap(12);
        panels.setVgap(12);
        panels.add(panel("Recent Activity", recentActivity), 0, 0);
        panels.add(panel("Recent Findings", recentFindings), 1, 0);
        panels.add(panel("Running Tasks", runningTasks), 2, 0);

        VBox content = new VBox(16, header, statusStrip(), cards, panels);
        content.setPadding(new Insets(18));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dashboard-scroll");
        setContent(scroll);

        history.addListener((ListChangeListener<HttpTransaction>) change -> updateDashboard());
        findings.addListener((ListChangeListener<Finding>) change -> updateDashboard());
        logs.addListener((ListChangeListener<LogEntry>) change -> updateDashboard());
        updateDashboard();
    }

    public void refresh(boolean proxyRunning, boolean interceptEnabled, long requestCount) {
        this.proxyRunning = proxyRunning;
        this.interceptEnabled = interceptEnabled;
        this.liveRequestCount = requestCount;
        updateDashboard();
    }

    private VBox titleBlock() {
        Label title = new Label("CyvoraX Suite");
        title.getStyleClass().add("dashboard-title");
        Label subtitle = new Label("Security testing workspace");
        subtitle.getStyleClass().add("dashboard-subtitle");
        return new VBox(3, title, subtitle);
    }

    private HBox quickActions(Button start, Button stop, Button cert) {
        HBox actions = new HBox(8, start, stop, cert);
        actions.getStyleClass().add("dashboard-actions");
        HBox.setHgrow(actions, Priority.ALWAYS);
        return actions;
    }

    private HBox statusStrip() {
        proxyState.getStyleClass().add("status-pill");
        interceptState.getStyleClass().add("status-pill");
        uptimeValue.getStyleClass().add("status-pill");
        actionStatus.getStyleClass().add("status-pill");
        HBox strip = new HBox(8, proxyState, interceptState, uptimeValue, actionStatus);
        strip.getStyleClass().add("dashboard-status-strip");
        return strip;
    }

    private VBox card(String title, Label value) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("metric-title");
        value.getStyleClass().add("metric-value");
        VBox card = new VBox(6, titleLabel, value);
        card.getStyleClass().add("metric-card");
        card.setMinWidth(190);
        card.setPrefWidth(260);
        GridPane.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private VBox panel(String title, VBox rows) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("panel-title");
        VBox box = new VBox(10, titleLabel, rows);
        box.getStyleClass().add("dashboard-panel");
        box.setMinHeight(210);
        GridPane.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private void updateDashboard() {
        requestsValue.setText(String.valueOf(history.size()));
        hostsValue.setText(String.valueOf(history.stream()
                .map(HttpTransaction::getHost)
                .filter(host -> host != null && !host.isBlank())
                .distinct()
                .count()));
        findingsValue.setText(String.valueOf(findings.size()));
        sessionsValue.setText(String.valueOf(sessionCount()));
        certificateValue.setText(certManager.healthStatus());
        pluginValue.setText(String.valueOf(pluginLoader.statuses().size()));
        proxyState.setText(proxyRunning ? "Proxy on" : "Proxy off");
        interceptState.setText(interceptEnabled ? "Intercept on" : "Intercept off");
        uptimeValue.setText("Uptime " + Duration.between(started, Instant.now()).toSeconds() + "s");
        updateRecentActivity();
        updateRecentFindings();
        updateRunningTasks();
    }

    private long sessionCount() {
        try {
            return database.listSessionRecordings().size();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void updateRecentActivity() {
        recentActivity.getChildren().clear();
        logs.stream()
                .sorted(Comparator.comparing(LogEntry::getTimestamp).reversed())
                .limit(5)
                .map(log -> row(log.getDirection(), log.getHost(), log.getMessage()))
                .forEach(recentActivity.getChildren()::add);
        if (recentActivity.getChildren().isEmpty()) {
            history.stream()
                    .limit(5)
                    .map(tx -> row(tx.getMethod(), tx.getHost(), tx.getPath()))
                    .forEach(recentActivity.getChildren()::add);
        }
        if (recentActivity.getChildren().isEmpty()) {
            recentActivity.getChildren().add(emptyRow("No activity yet"));
        }
    }

    private void updateRecentFindings() {
        recentFindings.getChildren().clear();
        findings.stream()
                .limit(5)
                .map(finding -> severityRow(finding.getSeverity(), finding.getIssue(), finding.getUrl()))
                .forEach(recentFindings.getChildren()::add);
        if (recentFindings.getChildren().isEmpty()) {
            recentFindings.getChildren().add(emptyRow("No findings yet"));
        }
    }

    private void updateRunningTasks() {
        runningTasks.getChildren().clear();
        runningTasks.getChildren().add(row(proxyRunning ? "Active" : "Idle", "Proxy", liveRequestCount + " requests this run"));
        runningTasks.getChildren().add(row(interceptEnabled ? "Active" : "Idle", "Intercept", interceptEnabled ? "Manual review enabled" : "Pass-through mode"));
        runningTasks.getChildren().add(row(sessionRecorder.isRecording() ? "Active" : "Idle", "Session recorder",
                sessionRecorder.isRecording() ? "Recording #" + sessionRecorder.activeRecordingId() : "Not recording"));
        runningTasks.getChildren().add(row("Ready", "Certificates", certManager.healthStatus()));
        runningTasks.getChildren().add(row("Loaded", "Plugins", pluginLoader.statuses().size() + " available"));
    }

    private HBox row(String badge, String title, String detail) {
        Label badgeLabel = new Label(badge);
        badgeLabel.getStyleClass().add("row-badge");
        Label titleLabel = new Label(title == null || title.isBlank() ? "-" : title);
        titleLabel.getStyleClass().add("row-title");
        Label detailLabel = new Label(detail == null || detail.isBlank() ? "-" : detail);
        detailLabel.getStyleClass().add("row-detail");
        detailLabel.setMaxWidth(Double.MAX_VALUE);
        HBox row = new HBox(8, badgeLabel, titleLabel, detailLabel);
        row.getStyleClass().add("dashboard-row");
        HBox.setHgrow(detailLabel, Priority.ALWAYS);
        return row;
    }

    private HBox severityRow(String severity, String issue, String url) {
        HBox row = row(severity, issue, url);
        row.getStyleClass().add("finding-row");
        return row;
    }

    private Label emptyRow(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("empty-row");
        return label;
    }

    private void exportCert() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export CyvoraX Suite CA Certificate");
        chooser.setInitialFileName("cyvorax-suite-ca-cert.pem");
        java.io.File destination = chooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (destination != null) {
            try {
                certManager.exportCertificate(Path.of(destination.toURI()));
                actionStatus.setText("CA certificate exported");
            } catch (Exception ex) {
                actionStatus.setText("CA export failed: " + ex.getMessage());
            }
        }
    }

    private ImageView logoView() {
        ImageView logo = new ImageView();
        logo.getStyleClass().add("dashboard-logo");
        try (InputStream stream = getClass().getResourceAsStream("/icons/cyvorax-logo.png")) {
            if (stream != null) {
                logo.setImage(new Image(stream));
            }
        } catch (Exception ignored) {
        }
        logo.setFitWidth(56);
        logo.setFitHeight(56);
        logo.setPreserveRatio(true);
        logo.setSmooth(true);
        return logo;
    }
}
