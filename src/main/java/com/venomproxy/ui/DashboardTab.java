package com.venomproxy.ui;

import com.venomproxy.analytics.DashboardAnalytics;
import com.venomproxy.analytics.DashboardMetrics;
import com.venomproxy.db.Database;
import com.venomproxy.model.Finding;
import com.venomproxy.model.HttpTransaction;
import com.venomproxy.model.LogEntry;
import com.venomproxy.plugins.PluginLoader;
import com.venomproxy.proxy.CertManager;
import com.venomproxy.scanner.ActiveScanner;
import com.venomproxy.session.SessionRecorder;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
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
    private final ActiveScanner activeScanner;
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
    private final VBox recentActivity = new VBox(5);
    private final VBox recentFindings = new VBox(5);
    private final VBox runningTasks = new VBox(5);
    private final VBox findingsBySeverity = new VBox(5);
    private final VBox scannerStatistics = new VBox(5);
    private final VBox spiderStatistics = new VBox(5);
    private final Instant started = Instant.now();
    private final Timeline refreshTicker;
    private boolean proxyRunning;
    private boolean interceptEnabled;
    private long liveRequestCount;

    public DashboardTab(MainWindow mainWindow, Database database, ObservableList<HttpTransaction> history,
                        ObservableList<Finding> findings, ObservableList<LogEntry> logs,
                        CertManager certManager, PluginLoader pluginLoader, SessionRecorder sessionRecorder,
                        ActiveScanner activeScanner) {
        super("Dashboard");
        this.database = database;
        this.history = history;
        this.findings = findings;
        this.logs = logs;
        this.certManager = certManager;
        this.pluginLoader = pluginLoader;
        this.sessionRecorder = sessionRecorder;
        this.activeScanner = activeScanner;
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

        HBox header = new HBox(10, logoView(), titleBlock(), statusStrip(), spacer(), start, stop, cert);
        header.getStyleClass().addAll("dashboard-header", "desktop-toolbar");
        header.setPadding(new Insets(8));

        VBox metrics = panel("Live Metrics", metricsRows());
        VBox scannerSpider = new VBox(8, panel("Scanner Statistics", scannerStatistics), panel("Spider Statistics", spiderStatistics));
        VBox.setVgrow(scannerSpider.getChildren().get(0), Priority.ALWAYS);
        VBox.setVgrow(scannerSpider.getChildren().get(1), Priority.ALWAYS);

        SplitPane left = verticalSplit(metrics, panel("Running Tasks", runningTasks), "layout.dashboard.left", 0.46);
        SplitPane center = verticalSplit(panel("Recent Activity", recentActivity), panel("Recent Findings", recentFindings),
                "layout.dashboard.center", 0.5);
        SplitPane right = verticalSplit(panel("Findings By Severity", findingsBySeverity), scannerSpider,
                "layout.dashboard.right", 0.42);
        SplitPane body = new SplitPane(left, center, right);
        body.setDividerPositions(0.32, 0.66);
        UiUtil.bindDividerPositions(database, "layout.dashboard.main", body, 0.32, 0.66);

        VBox root = new VBox(8, header, body);
        root.setPadding(new Insets(8));
        VBox.setVgrow(body, Priority.ALWAYS);
        setContent(root);

        history.addListener((ListChangeListener<HttpTransaction>) change -> updateDashboard());
        findings.addListener((ListChangeListener<Finding>) change -> updateDashboard());
        logs.addListener((ListChangeListener<LogEntry>) change -> updateDashboard());
        refreshTicker = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), event -> updateDashboard()));
        refreshTicker.setCycleCount(Timeline.INDEFINITE);
        refreshTicker.play();
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
        Label subtitle = new Label("Professional security testing workspace");
        subtitle.getStyleClass().add("dashboard-subtitle");
        return new VBox(2, title, subtitle);
    }

    private HBox statusStrip() {
        proxyState.getStyleClass().add("status-pill");
        interceptState.getStyleClass().add("status-pill");
        uptimeValue.getStyleClass().add("status-pill");
        actionStatus.getStyleClass().add("status-pill");
        return new HBox(6, proxyState, interceptState, uptimeValue, actionStatus);
    }

    private VBox metricsRows() {
        return new VBox(5,
                metricRow("Requests", requestsValue),
                metricRow("Hosts", hostsValue),
                metricRow("Findings", findingsValue),
                metricRow("Sessions", sessionsValue),
                metricRow("Certificate", certificateValue),
                metricRow("Plugins", pluginValue),
                metricRow("Requests/hour", requestsPerHourValue));
    }

    private HBox metricRow(String name, Label value) {
        Label label = new Label(name);
        label.getStyleClass().add("metric-title");
        label.setMinWidth(130);
        value.getStyleClass().add("metric-value-compact");
        HBox row = new HBox(10, label, spacer(), value);
        row.getStyleClass().add("desktop-row");
        return row;
    }

    private VBox panel(String title, Node content) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("panel-title");
        VBox box = new VBox(8, titleLabel, content);
        box.getStyleClass().add("desktop-panel");
        VBox.setVgrow(content, Priority.ALWAYS);
        return box;
    }

    private SplitPane verticalSplit(Node first, Node second, String setting, double divider) {
        SplitPane split = new SplitPane(first, second);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(divider);
        UiUtil.bindDividerPositions(database, setting, split, divider);
        return split;
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
        uptimeValue.setText("Uptime: " + UiUtil.formatDuration(Duration.between(started, Instant.now()).toSeconds()));
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
            recentActivity.getChildren().add(activityRow(badge, title, detail));
        }
        if (recentActivity.getChildren().isEmpty()) {
            recentActivity.getChildren().add(emptyRow("No activity yet"));
        }
    }

    private void updateRecentFindings() {
        recentFindings.getChildren().clear();
        findings.stream()
                .limit(8)
                .map(finding -> row(finding.getSeverity(), finding.getIssue(), finding.getUrl()))
                .forEach(recentFindings.getChildren()::add);
        if (recentFindings.getChildren().isEmpty()) {
            recentFindings.getChildren().add(emptyRow("No findings yet"));
        }
    }

    private void updateRunningTasks() {
        runningTasks.getChildren().clear();
        runningTasks.getChildren().add(taskRow(proxyRunning ? "Active" : "Idle", "Proxy", liveRequestCount + " requests this run"));
        runningTasks.getChildren().add(taskRow(interceptEnabled ? "Active" : "Idle", "Intercept",
                interceptEnabled ? "Manual review enabled" : "Pass-through mode"));
        runningTasks.getChildren().add(taskRow(sessionRecorder.isRecording() ? "Active" : "Idle", "Session recorder",
                sessionRecorder.isRecording() ? "Recording #" + sessionRecorder.activeRecordingId() : "Not recording"));
        ActiveScanner.ScanActivity scannerActivity = activeScanner.activity();
        runningTasks.getChildren().add(taskRow(scannerActivity.active() ? "Active" : "Idle", "Active Scanner",
                scannerActivity.summary()));
        runningTasks.getChildren().add(taskRow("Ready", "Certificates", certManager.healthStatus()));
        runningTasks.getChildren().add(taskRow("Loaded", "Plugins", pluginLoader.statuses().size() + " available"));
    }

    private void updateFindingsBySeverity(Map<String, Long> grouped) {
        findingsBySeverity.getChildren().clear();
        grouped.forEach((severity, count) -> findingsBySeverity.getChildren().add(severityRow(severity, count)));
        if (findingsBySeverity.getChildren().isEmpty()) {
            findingsBySeverity.getChildren().add(emptyRow("No findings grouped yet"));
        }
    }

    private HBox severityRow(String severity, long count) {
        Label sevBadge = new Label(severity);
        sevBadge.getStyleClass().addAll("sev-badge", "sev-" + severity.toLowerCase());
        Label countLabel = new Label(count + " findings");
        countLabel.getStyleClass().add("row-detail");
        countLabel.setMaxWidth(Double.MAX_VALUE);
        HBox row = new HBox(12, sevBadge, spacer(), countLabel);
        row.getStyleClass().add("desktop-row");
        HBox.setHgrow(countLabel, Priority.ALWAYS);
        return row;
    }

    private void updateTextRows(VBox target, List<String> rows, String empty) {
        target.getChildren().clear();
        for (String value : rows) {
            String[] parts = value.split(":", 2);
            String labelText = parts[0].trim();
            String detailText = parts.length > 1 ? parts[1].trim() : value;
            target.getChildren().add(statRow(labelText, detailText));
        }
        if (target.getChildren().isEmpty()) {
            target.getChildren().add(emptyRow(empty));
        }
    }

    private HBox statRow(String name, String value) {
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().addAll("task-badge", "task-badge-active");
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("row-detail");
        valueLabel.setMaxWidth(Double.MAX_VALUE);
        HBox row = new HBox(12, nameLabel, spacer(), valueLabel);
        row.getStyleClass().add("desktop-row");
        HBox.setHgrow(valueLabel, Priority.ALWAYS);
        return row;
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
        row.getStyleClass().add("desktop-row");
        HBox.setHgrow(detailLabel, Priority.ALWAYS);
        return row;
    }

    private HBox taskRow(String state, String title, String detail) {
        Label badgeLabel = new Label(state);
        badgeLabel.getStyleClass().addAll("task-badge", "task-badge-" + state.toLowerCase());
        Label titleLabel = new Label(title == null || title.isBlank() ? "-" : title);
        titleLabel.getStyleClass().add("row-title");
        Label detailLabel = new Label(detail == null || detail.isBlank() ? "-" : detail);
        detailLabel.getStyleClass().add("row-detail");
        detailLabel.setMaxWidth(Double.MAX_VALUE);
        HBox row = new HBox(8, badgeLabel, titleLabel, detailLabel);
        row.getStyleClass().add("desktop-row");
        HBox.setHgrow(detailLabel, Priority.ALWAYS);
        return row;
    }

    private HBox activityRow(String direction, String host, String detail) {
        Label directionBadge = new Label(direction == null || direction.isBlank() ? "-" : direction);
        directionBadge.getStyleClass().addAll("row-badge", "badge-" + directionBadge.getText().toLowerCase());
        directionBadge.setMinWidth(46);
        Label hostLabel = new Label(host == null || host.isBlank() ? "-" : host);
        hostLabel.getStyleClass().add("row-title");
        hostLabel.setMaxWidth(160);
        Label detailLabel = new Label(detail == null || detail.isBlank() ? "-" : detail);
        detailLabel.getStyleClass().add("row-detail");
        detailLabel.setMaxWidth(260);
        HBox row = new HBox(8, directionBadge, hostLabel, detailLabel);
        row.getStyleClass().add("desktop-row");
        return row;
    }

    private Label emptyRow(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("empty-row");
        return label;
    }

    private Node spacer() {
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
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
        logo.setFitWidth(40);
        logo.setFitHeight(40);
        logo.setPreserveRatio(true);
        logo.setSmooth(true);
        return logo;
    }
}
