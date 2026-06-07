package com.venomproxy.ui;

import com.venomproxy.analytics.DashboardAnalytics;
import com.venomproxy.analytics.DashboardMetrics;
import com.venomproxy.db.Database;
import com.venomproxy.model.Finding;
import com.venomproxy.model.HttpTransaction;
import com.venomproxy.model.LogEntry;
import com.venomproxy.plugins.PluginLoader;
import com.venomproxy.proxy.CertManager;
import com.venomproxy.session.SessionRecorder;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class DashboardTab extends Tab {
    private final Database database;
    private final ObservableList<HttpTransaction> history;
    private final ObservableList<Finding> findings;
    private final ObservableList<LogEntry> logs;
    private final CertManager certManager;
    private final PluginLoader pluginLoader;
    private final SessionRecorder sessionRecorder;
    private final DashboardAnalytics analytics = new DashboardAnalytics();
    private final Label requestsValue = new Label("0");
    private final Label hostsValue = new Label("0");
    private final Label findingsValue = new Label("0");
    private final Label sessionsValue = new Label("0");
    private final Label certificateValue = new Label("Unknown");
    private final Label pluginValue = new Label("0");
    private final Label requestsPerHourValue = new Label("0");
    private final Label proxyState = new Label("Proxy off");
    private final Label interceptState = new Label("Intercept off");
    private final Label uptimeValue = new Label("0s");
    private final Label actionStatus = new Label("Ready");
    private final VBox recentActivity = new VBox(6);
    private final VBox recentFindings = new VBox(6);
    private final VBox runningTasks = new VBox(6);
    private final VBox findingsBySeverity = new VBox(6);
    private final VBox scannerStatistics = new VBox(6);
    private final VBox spiderStatistics = new VBox(6);
    private final FlowPane cards = new FlowPane(10, 10);
    private final FlowPane panels = new FlowPane(10, 10);
    private final List<VBox> metricCards = new java.util.ArrayList<>();
    private final List<VBox> dashboardPanels = new java.util.ArrayList<>();
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

        HBox header = new HBox(12, logoView(), titleBlock(), quickActions(start, stop, cert));
        header.getStyleClass().add("dashboard-header");

        cards.getStyleClass().add("dashboard-cards");
        cards.getChildren().addAll(
                card("Requests", requestsValue),
                card("Hosts", hostsValue),
                card("Findings", findingsValue),
                card("Sessions", sessionsValue),
                card("Certificate Status", certificateValue),
                card("Plugin Count", pluginValue),
                card("Requests Per Hour", requestsPerHourValue)
        );
        panels.getStyleClass().add("dashboard-panels");
        panels.getChildren().addAll(
                panel("Recent Activity", recentActivity),
                panel("Recent Findings", recentFindings),
                panel("Findings Overview", findingsBySeverity),
                panel("Running Tasks", runningTasks),
                panel("Scanner Statistics", scannerStatistics),
                panel("Spider Statistics", spiderStatistics)
        );

        VBox content = new VBox(10, header, statusStrip(), cards, panels);
        content.setPadding(new Insets(14));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dashboard-scroll");
        setContent(scroll);
        scroll.viewportBoundsProperty().addListener((obs, old, bounds) -> resizeDashboard(bounds.getWidth()));
        Platform.runLater(() -> resizeDashboard(scroll.getViewportBounds().getWidth()));

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
        value.setWrapText(true);
        value.setMaxWidth(Double.MAX_VALUE);
        VBox card = new VBox(6, titleLabel, value);
        card.getStyleClass().add("metric-card");
        card.setMinWidth(170);
        card.setPrefWidth(220);
        metricCards.add(card);
        return card;
    }

    private VBox panel(String title, VBox rows) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("panel-title");
        VBox box = new VBox(10, titleLabel, rows);
        box.getStyleClass().add("dashboard-panel");
        box.setMinHeight(150);
        box.setPrefWidth(360);
        dashboardPanels.add(box);
        return box;
    }

    private void resizeDashboard(double width) {
        double innerWidth = Math.max(720, width - 28);
        cards.setPrefWrapLength(innerWidth);
        cards.setMaxWidth(innerWidth);
        double cardWidth = Math.min(260, ResponsiveLayout.tileWidth(innerWidth, ResponsiveLayout.cardColumns(innerWidth), 10, 170));
        metricCards.forEach(card -> {
            card.setPrefWidth(cardWidth);
            card.setMaxWidth(cardWidth);
        });
        panels.setPrefWrapLength(innerWidth);
        panels.setMaxWidth(innerWidth);
        double panelWidth = Math.min(560, ResponsiveLayout.tileWidth(innerWidth, ResponsiveLayout.panelColumns(innerWidth), 10, 340));
        dashboardPanels.forEach(panel -> {
            panel.setPrefWidth(panelWidth);
            panel.setMaxWidth(panelWidth);
        });
    }

    private void updateDashboard() {
        DashboardMetrics metrics = analytics.calculate(List.copyOf(history), List.copyOf(findings), List.copyOf(logs),
                sessionCount(), pluginLoader.statuses().size());
        requestsValue.setText(String.valueOf(metrics.requests()));
        hostsValue.setText(String.valueOf(metrics.hosts()));
        findingsValue.setText(String.valueOf(metrics.findings()));
        sessionsValue.setText(String.valueOf(metrics.sessions()));
        certificateValue.setText(certManager.healthStatus());
        pluginValue.setText(String.valueOf(metrics.plugins()));
        requestsPerHourValue.setText(String.valueOf(metrics.requestsPerHour()));
        proxyState.setText(proxyRunning ? "Proxy on" : "Proxy off");
        interceptState.setText(interceptEnabled ? "Intercept on" : "Intercept off");
        uptimeValue.setText("Uptime " + Duration.between(started, Instant.now()).toSeconds() + "s");
        updateRecentActivity(metrics.recentActivity());
        updateRecentFindings();
        updateRunningTasks();
        updateFindingsBySeverity(metrics.findingsBySeverity());
        updateTextRows(scannerStatistics, metrics.scannerStatistics(), "No scanner statistics yet");
        updateTextRows(spiderStatistics, metrics.spiderStatistics(), "No spider statistics yet");
    }

    private long sessionCount() {
        try {
            return database.listSessionRecordings().size();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void updateRecentActivity(List<String> rows) {
        recentActivity.getChildren().clear();
        for (String activity : rows) {
            String[] parts = activity.split("\\|", 3);
            String badge = parts.length > 0 ? parts[0].trim() : "Activity";
            String title = parts.length > 1 ? parts[1].trim() : activity;
            String detail = parts.length > 2 ? parts[2].trim() : "";
            recentActivity.getChildren().add(row(badge, title, detail));
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

    private void updateFindingsBySeverity(Map<String, Long> grouped) {
        findingsBySeverity.getChildren().clear();
        grouped.forEach((severity, count) -> findingsBySeverity.getChildren().add(severityRow(severity, severity, count + " findings")));
        if (findingsBySeverity.getChildren().isEmpty()) {
            findingsBySeverity.getChildren().add(emptyRow("No findings grouped yet"));
        }
    }

    private void updateTextRows(VBox target, List<String> rows, String empty) {
        target.getChildren().clear();
        for (String value : rows) {
            String[] parts = value.split(":", 2);
            target.getChildren().add(row(parts[0].trim(), parts[0].trim(), parts.length > 1 ? parts[1].trim() : value));
        }
        if (target.getChildren().isEmpty()) {
            target.getChildren().add(emptyRow(empty));
        }
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
