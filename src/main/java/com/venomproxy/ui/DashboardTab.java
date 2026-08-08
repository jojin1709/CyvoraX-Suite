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
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
    private final VBox taskList = new VBox(6);
    private final VBox taskConfig = new VBox(8);
    private final VBox taskProgress = new VBox(6);
    private final TableView<Finding> issuesTable = new TableView<>();
    private final Instant started = Instant.now();
    private final Timeline refreshTicker;
    private final List<TaskEntry> tasks = new ArrayList<>();
    private boolean proxyRunning;
    private boolean interceptEnabled;
    private long liveRequestCount;
    private TaskEntry selectedTask;

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

        tasks.add(new TaskEntry("Live passive crawl", "Add links. Add item itself, same domain and URLs in suite scope.", true));
        tasks.add(new TaskEntry("Live audit from Proxy", "Audit checks - passive", true));

        VBox tasksSidebar = buildTasksSidebar(mainWindow);
        VBox centerPanel = buildCenterPanel();
        VBox rightPanel = buildRightPanel();

        SplitPane mainSplit = new SplitPane(tasksSidebar, centerPanel, rightPanel);
        mainSplit.setDividerPositions(0.22, 0.68);
        UiUtil.bindDividerPositions(database, "layout.dashboard.main", mainSplit, 0.22, 0.68);
        mainSplit.setPadding(new Insets(0));

        setContent(mainSplit);

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

    private VBox buildTasksSidebar(MainWindow mainWindow) {
        Label header = new Label("Tasks");
        header.getStyleClass().add("sidebar-header");

        Button newScan = new Button("New scan");
        newScan.getStyleClass().add("btn-primary");
        newScan.setMaxWidth(Double.MAX_VALUE);
        newScan.setOnAction(e -> {
            TaskEntry task = new TaskEntry("New scan - " + (tasks.size() + 1), "Active scan from proxy traffic", true);
            tasks.add(task);
            selectedTask = task;
            updateTaskCards();
            updateTaskConfig();
            updateTaskProgress();
        });

        Button newLiveTask = new Button("New live task");
        newLiveTask.getStyleClass().add("btn-primary");
        newLiveTask.setMaxWidth(Double.MAX_VALUE);
        newLiveTask.setOnAction(e -> {
            TaskEntry task = new TaskEntry("Live audit - " + (tasks.size() + 1), "Live audit checks", true);
            tasks.add(task);
            selectedTask = task;
            updateTaskCards();
            updateTaskConfig();
            updateTaskProgress();
        });

        HBox buttons = new HBox(6, newScan, newLiveTask);
        buttons.setPadding(new Insets(0, 0, 8, 0));

        taskList.setSpacing(6);
        taskList.getStyleClass().add("task-list");

        ScrollPane scroll = new ScrollPane(taskList);
        scroll.setFitToWidth(true);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("panel-scroll-pane");

        VBox sidebar = new VBox(0, header, buttons, scroll);
        sidebar.getStyleClass().addAll("sidebar-panel");
        sidebar.setPadding(new Insets(0));
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return sidebar;
    }

    private VBox buildCenterPanel() {
        Label header = new Label("Most serious vulnerabilities found (live)");
        header.getStyleClass().add("panel-header");
        Label viewAll = new Label("View all");
        viewAll.getStyleClass().add("link-label");
        viewAll.setOnMouseClicked(e -> {
            if (getTabPane() != null) {
                for (javafx.scene.control.Tab tab : getTabPane().getTabs()) {
                    if (tab.getText() != null && (tab.getText().contains("Scanner") || tab.getText().contains("Target"))) {
                        getTabPane().getSelectionModel().select(tab);
                        break;
                    }
                }
            }
        });

        HBox headerRow = new HBox(8, header, spacer(), viewAll);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.setPadding(new Insets(0, 0, 8, 0));

        issuesTable.setPlaceholder(emptyState("No issues to show", "Any issues found during the audit will be displayed here."));
        issuesTable.setPrefHeight(Double.MAX_VALUE);
        issuesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Finding, String> issueCol = new TableColumn<>("Issue type");
        issueCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getIssue()));
        issueCol.setPrefWidth(280);

        TableColumn<Finding, String> hostCol = new TableColumn<>("Host");
        hostCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getHost()));
        hostCol.setPrefWidth(200);

        TableColumn<Finding, String> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(data -> {
            Finding f = data.getValue();
            String time = f.getTimestamp() != null ? f.getTimestamp().toString() : "";
            return new SimpleStringProperty(time);
        });
        timeCol.setPrefWidth(160);

        issuesTable.getColumns().addAll(issueCol, hostCol, timeCol);
        issuesTable.getStyleClass().add("burp-table");
        VBox.setVgrow(issuesTable, Priority.ALWAYS);

        VBox center = new VBox(0, headerRow, issuesTable);
        center.setPadding(new Insets(10, 10, 0, 10));
        center.getStyleClass().add("center-panel");
        VBox.setVgrow(issuesTable, Priority.ALWAYS);
        return center;
    }

    private VBox buildRightPanel() {
        Label configHeader = new Label("Task configuration");
        configHeader.getStyleClass().add("panel-header");
        Label viewConfig = new Label("View configuration");
        viewConfig.getStyleClass().add("link-label");
        viewConfig.setOnMouseClicked(e -> {
            if (selectedTask != null) {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
                alert.setTitle("Task Configuration");
                alert.setHeaderText(selectedTask.name);
                alert.setContentText("Task Type: Live audit\nScope: Proxy (all traffic)\nStatus: " + (selectedTask.capturing ? "Active" : "Paused") + "\n\nDescription: " + selectedTask.description);
                alert.showAndWait();
            }
        });
        HBox configHeaderRow = new HBox(8, configHeader, spacer(), viewConfig);

        taskConfig.getChildren().clear();
        taskConfig.getChildren().addAll(configHeaderRow,
                configRow("Task name:", "Live passive crawl"),
                configRow("Task type:", "Live audit"),
                configRow("Scope:", "Proxy (all traffic)"));
        taskConfig.setPadding(new Insets(10));
        taskConfig.getStyleClass().add("config-panel");
        VBox.setVgrow(taskConfig, Priority.SOMETIMES);

        Label progressHeader = new Label("Task progress");
        progressHeader.getStyleClass().add("panel-header");

        taskProgress.getChildren().clear();
        taskProgress.getChildren().addAll(progressHeader,
                progressRow("Total findings:", "0"),
                progressRow("Critical:", "0"),
                progressRow("High:", "0"),
                progressRow("Medium:", "0"),
                progressRow("Low:", "0"),
                progressRow("Requests:", "0"));
        taskProgress.setPadding(new Insets(10));
        taskProgress.getStyleClass().add("progress-panel");
        VBox.setVgrow(taskProgress, Priority.ALWAYS);

        VBox right = new VBox(8, taskConfig, taskProgress);
        right.setPadding(new Insets(10, 10, 0, 10));
        right.getStyleClass().add("right-panel");
        return right;
    }

    private HBox progressRow(String name, String value) {
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("progress-label");
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("progress-value");
        HBox row = new HBox(8, nameLabel, spacer(), valueLabel);
        row.getStyleClass().add("desktop-row");
        row.setPadding(new Insets(3, 0, 3, 0));
        return row;
    }

    private HBox configRow(String name, String value) {
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("config-label");
        nameLabel.setMinWidth(110);
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("config-value");
        HBox row = new HBox(8, nameLabel, valueLabel);
        row.setPadding(new Insets(3, 0, 3, 0));
        return row;
    }

    private VBox emptyState(String title, String description) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("empty-state-title");
        Label descLabel = new Label(description);
        descLabel.getStyleClass().add("empty-state-desc");
        descLabel.setWrapText(true);
        VBox box = new VBox(8, titleLabel, descLabel);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(20));
        box.getStyleClass().add("empty-state");
        return box;
    }

    private void updateDashboard() {
        DashboardMetrics metrics = analytics.calculate(List.copyOf(history), List.copyOf(findings), List.copyOf(logs),
                sessionCount(), pluginLoader.statuses().size());
        updateTaskCards();
        updateIssuesTable();
        updateTaskProgress();
    }

    private long sessionCount() {
        try {
            return database.listSessionRecordings().size();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void updateTaskCards() {
        taskList.getChildren().clear();
        for (int i = 0; i < tasks.size(); i++) {
            TaskEntry task = tasks.get(i);
            taskList.getChildren().add(taskCard(i, task));
        }
    }

    private VBox taskCard(int index, TaskEntry task) {
        Label titleLabel = new Label((index + 1) + ". " + task.name);
        titleLabel.getStyleClass().add("task-card-title");
        titleLabel.setWrapText(true);

        Label descLabel = new Label(task.description);
        descLabel.getStyleClass().add("task-card-desc");
        descLabel.setWrapText(true);

        Label capturingLabel = new Label("Capturing");
        capturingLabel.getStyleClass().add("task-card-label");

        ToggleButton captureToggle = new ToggleButton();
        captureToggle.setSelected(task.capturing);
        captureToggle.getStyleClass().add("capture-toggle");
        final int taskIndex = index;
        captureToggle.setOnAction(e -> {
            tasks.get(taskIndex).capturing = captureToggle.isSelected();
        });

        HBox captureRow = new HBox(8, capturingLabel, captureToggle);
        captureRow.setAlignment(Pos.CENTER_LEFT);
        captureRow.setPadding(new Insets(4, 0, 0, 0));

        HBox issues = new HBox(6);
        issues.setAlignment(Pos.CENTER_LEFT);
        issues.setPadding(new Insets(4, 0, 0, 0));

        Label issuesLabel = new Label("Issues:");
        issuesLabel.getStyleClass().add("task-card-label");

        Label criticalBadge = new Label(String.valueOf(findings.stream().filter(f -> "Critical".equals(f.getSeverity())).count()));
        criticalBadge.getStyleClass().addAll("issue-badge", "issue-badge-critical");
        Label highBadge = new Label(String.valueOf(findings.stream().filter(f -> "High".equals(f.getSeverity())).count()));
        highBadge.getStyleClass().addAll("issue-badge", "issue-badge-high");
        Label mediumBadge = new Label(String.valueOf(findings.stream().filter(f -> "Medium".equals(f.getSeverity())).count()));
        mediumBadge.getStyleClass().addAll("issue-badge", "issue-badge-medium");
        Label lowBadge = new Label(String.valueOf(findings.stream().filter(f -> "Low".equals(f.getSeverity())).count()));
        lowBadge.getStyleClass().addAll("issue-badge", "issue-badge-low");

        issues.getChildren().addAll(issuesLabel, criticalBadge, highBadge, mediumBadge, lowBadge);

        VBox card = new VBox(2, titleLabel, descLabel, captureRow, issues);
        card.getStyleClass().add("task-card");
        card.setPadding(new Insets(10, 12, 10, 12));

        boolean isSelected = (selectedTask == task);
        if (isSelected) {
            card.getStyleClass().add("task-card-selected");
        }

        card.setOnMouseClicked(e -> {
            selectedTask = task;
            updateTaskCards();
            updateTaskConfig();
        });
        return card;
    }

    private void updateIssuesTable() {
        issuesTable.getItems().clear();
        issuesTable.getItems().addAll(findings);
    }

    private void updateTaskConfig() {
        Label configHeader = new Label("Task configuration");
        configHeader.getStyleClass().add("panel-header");
        Label viewConfig = new Label("View configuration");
        viewConfig.getStyleClass().add("link-label");
        viewConfig.setOnMouseClicked(e -> {
            if (selectedTask != null) {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
                alert.setTitle("Task Configuration");
                alert.setHeaderText(selectedTask.name);
                alert.setContentText("Task Type: Live audit\nScope: Proxy (all traffic)\nStatus: " + (selectedTask.capturing ? "Active" : "Paused") + "\n\nDescription: " + selectedTask.description);
                alert.showAndWait();
            }
        });
        HBox configHeaderRow = new HBox(8, configHeader, spacer(), viewConfig);

        taskConfig.getChildren().clear();
        if (selectedTask != null) {
            taskConfig.getChildren().addAll(configHeaderRow,
                    configRow("Task name:", selectedTask.name),
                    configRow("Task type:", "Live audit"),
                    configRow("Scope:", "Proxy (all traffic)"),
                    configRow("Status:", selectedTask.capturing ? "Active" : "Paused"));
        } else {
            taskConfig.getChildren().addAll(configHeaderRow,
                    configRow("Task name:", "-"),
                    configRow("Task type:", "-"),
                    configRow("Scope:", "-"));
        }
    }

    private void updateTaskProgress() {
        long critical = findings.stream().filter(f -> "Critical".equals(f.getSeverity())).count();
        long high = findings.stream().filter(f -> "High".equals(f.getSeverity())).count();
        long medium = findings.stream().filter(f -> "Medium".equals(f.getSeverity())).count();
        long low = findings.stream().filter(f -> "Low".equals(f.getSeverity())).count();
        long total = critical + high + medium + low;

        taskProgress.getChildren().clear();
        Label header = new Label("Task progress");
        header.getStyleClass().add("panel-header");
        taskProgress.getChildren().addAll(header,
                progressRow("Total findings:", String.valueOf(total)),
                progressRow("Critical:", String.valueOf(critical)),
                progressRow("High:", String.valueOf(high)),
                progressRow("Medium:", String.valueOf(medium)),
                progressRow("Low:", String.valueOf(low)),
                progressRow("Requests:", String.valueOf(history.size())));
    }

    private Node spacer() {
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private static class TaskEntry {
        String name;
        String description;
        boolean capturing;

        TaskEntry(String name, String description, boolean capturing) {
            this.name = name;
            this.description = description;
            this.capturing = capturing;
        }
    }
}
