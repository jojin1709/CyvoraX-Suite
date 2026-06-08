package com.venomproxy.ui;

import com.venomproxy.ai.AiProviderConfig;
import com.venomproxy.ai.AiProviderSettings;
import com.venomproxy.auth.AuthenticationManager;
import com.venomproxy.backup.BackupManager;
import com.venomproxy.db.Database;
import com.venomproxy.diagnostics.CrashReport;
import com.venomproxy.diagnostics.CrashReporter;
import com.venomproxy.model.Finding;
import com.venomproxy.model.HttpTransaction;
import com.venomproxy.model.LogEntry;
import com.venomproxy.model.NotificationEntry;
import com.venomproxy.model.SearchResult;
import com.venomproxy.notifications.NotificationService;
import com.venomproxy.plugins.PluginLoader;
import com.venomproxy.proxy.CertManager;
import com.venomproxy.proxy.InterceptedRequest;
import com.venomproxy.proxy.MatchReplaceEngine;
import com.venomproxy.proxy.ProxyEventListener;
import com.venomproxy.proxy.ProxyServer;
import com.venomproxy.proxy.ScopeControl;
import com.venomproxy.recovery.SessionRecoveryManager;
import com.venomproxy.recovery.SessionSnapshot;
import com.venomproxy.scanner.ActiveScanner;
import com.venomproxy.scanner.PassiveScanner;
import com.venomproxy.session.SessionRecorder;
import com.venomproxy.update.UpdateInfo;
import com.venomproxy.update.UpdateService;
import com.venomproxy.util.SecretMasker;
import com.venomproxy.workspace.WorkspaceInfo;
import com.venomproxy.workspace.WorkspaceManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class MainWindow extends BorderPane implements ProxyEventListener {
    private final Database database;
    private final ProxyServer proxyServer;
    private final CertManager certManager;
    private final ScopeControl scopeControl;
    private final WorkspaceInfo workspaceInfo;
    private final CrashReporter crashReporter;
    private final SessionRecoveryManager sessionRecoveryManager;
    private final UpdateService updateService;
    private final AiProviderConfig aiProviderConfig;
    private final WorkspaceManager workspaceManager;
    private final BackupManager backupManager;
    private final Consumer<WorkspaceInfo> workspaceSwitchHandler;
    private final NotificationService notificationService;
    private final String appVersion;
    private final ObservableList<HttpTransaction> history;
    private final ObservableList<Finding> findings;
    private final ObservableList<LogEntry> logs;
    private final Label proxyStatus = new Label("Proxy: off");
    private final Label interceptStatus = new Label("Intercept: off");
    private final Label requestCount = new Label("Requests: 0");
    private final TabPane tabs = new TabPane();
    private final Label moduleTitle = new Label("Dashboard");
    private final Label workspaceStatus = new Label();
    private final Label aiStatus = new Label("AI: not configured");
    private final Label selectedModuleStatus = new Label("Module: Dashboard");
    private final Map<Tab, TabPane> moduleHosts = new LinkedHashMap<>();
    private final Map<Tab, Tab> moduleWorkspaces = new LinkedHashMap<>();
    private final Map<Tab, ToggleButton> navigationButtons = new LinkedHashMap<>();
    private final Map<Tab, String> tabGroups = new LinkedHashMap<>();
    private final Map<String, ToggleButton> primaryNavigationButtons = new LinkedHashMap<>();
    private final Map<String, Tab> modulesByTitle = new LinkedHashMap<>();
    private final HBox secondaryNavigation = new HBox(6);
    private final ComboBox<String> moduleSelector = new ComboBox<>();
    private final ComboBox<WorkspaceInfo> workspaceSelector = new ComboBox<>();
    private final Button notificationButton = new Button("!");
    private final DashboardTab dashboardTab;
    private final ProxyTab proxyTab;
    private final RepeaterTab repeaterTab;
    private final IntruderTab intruderTab;
    private final ScannerTab scannerTab;
    private final HistoryTab historyTab;
    private final GlobalSearchTab globalSearchTab;
    private final SessionRecorderTab sessionRecorderTab;
    private final ReportTab reportTab;
    private final SessionRecorder sessionRecorder;
    private Timeline snapshotTimeline;
    private ThemeManager themeManager;
    private boolean updatingWorkspaceSelector;

    public MainWindow(Database database, ProxyServer proxyServer, PassiveScanner passiveScanner,
                      ActiveScanner activeScanner, CertManager certManager, PluginLoader pluginLoader,
                      ScopeControl scopeControl, MatchReplaceEngine matchReplaceEngine,
                      AuthenticationManager authenticationManager, SessionRecorder sessionRecorder, Path toolsDirectory,
                      WorkspaceInfo workspaceInfo, CrashReporter crashReporter,
                      SessionRecoveryManager sessionRecoveryManager, UpdateService updateService, String appVersion,
                      WorkspaceManager workspaceManager, BackupManager backupManager, AiProviderConfig aiProviderConfig,
                      Consumer<WorkspaceInfo> workspaceSwitchHandler) {
        this.database = database;
        this.proxyServer = proxyServer;
        this.certManager = certManager;
        this.scopeControl = scopeControl;
        this.workspaceInfo = workspaceInfo;
        this.crashReporter = crashReporter;
        this.sessionRecoveryManager = sessionRecoveryManager;
        this.updateService = updateService;
        this.aiProviderConfig = aiProviderConfig;
        this.workspaceManager = workspaceManager;
        this.backupManager = backupManager;
        this.workspaceSwitchHandler = workspaceSwitchHandler;
        this.appVersion = appVersion;
        this.sessionRecorder = sessionRecorder;
        this.history = FXCollections.observableArrayList(database.listTransactions());
        this.findings = FXCollections.observableArrayList(database.listFindings());
        this.logs = FXCollections.observableArrayList(database.listLogs());
        this.notificationService = new NotificationService(database);
        this.proxyServer.setListener(this);
        pluginLoader.load(database, scopeControl);
        if (!pluginLoader.statuses().isEmpty()) {
            notificationService.publish("Plugin Events", "Plugin system initialized",
                    pluginLoader.statuses().size() + " plugin status records loaded");
        }
        this.dashboardTab = new DashboardTab(this, database, history, findings, logs, certManager, pluginLoader, sessionRecorder);
        this.proxyTab = new ProxyTab(proxyServer);
        this.repeaterTab = new RepeaterTab(database);
        this.intruderTab = new IntruderTab(database);
        this.scannerTab = new ScannerTab(findings, activeScanner, database,
                (title, message) -> notificationService.publish("Scan Complete", title, message));
        this.globalSearchTab = new GlobalSearchTab(database, this::openSearchResult);
        this.historyTab = new HistoryTab(database, history, tx -> repeaterTab.openTransaction(tx),
                tx -> intruderTab.loadTransaction(tx), tx -> scannerTab.scanTransaction(tx), scopeControl);
        this.sessionRecorderTab = new SessionRecorderTab(database, sessionRecorder);
        this.reportTab = new ReportTab(findings, history, notificationService);

        this.proxyTab.setText("Intercept");
        MatchReplaceTab matchReplaceTab = new MatchReplaceTab(matchReplaceEngine);
        SiteMapTab siteMapTab = new SiteMapTab(database, history);
        OrganizerTab organizerTab = new OrganizerTab(database, history);
        TurboIntruderTab turboIntruderTab = new TurboIntruderTab(toolsDirectory);
        SpiderCrawlerTab spiderCrawlerTab = new SpiderCrawlerTab(database, history, scopeControl, toolsDirectory,
                (title, message) -> notificationService.publish("Spider Complete", title, message));
        spiderCrawlerTab.setText("Spider");
        DecoderTab decoderTab = new DecoderTab();
        ComparerTab comparerTab = new ComparerTab();
        LoggerTab loggerTab = new LoggerTab(logs);
        loggerTab.setText("Traffic Log");
        AuthManagerTab authManagerTab = new AuthManagerTab(authenticationManager);
        PluginManagerTab pluginManagerTab = new PluginManagerTab(pluginLoader, database, scopeControl);
        SettingsTab settingsTab = new SettingsTab(this, scopeControl, updateService, crashReporter, appVersion, aiProviderConfig);

        List<Tab> extensionTabs = new ArrayList<>();
        extensionTabs.add(pluginManagerTab);
        pluginLoader.plugins().forEach(plugin -> plugin.uiTab().ifPresent(node -> {
            javafx.scene.control.Tab tab = new javafx.scene.control.Tab(plugin.name(), node);
            extensionTabs.add(tab);
        }));

        tabs.getStyleClass().addAll("main-tabs", "desktop-main-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        addTopModule(dashboardTab);
        addTopModule(workspace("Proxy", historyTab, proxyTab, matchReplaceTab));
        addTopModule(workspace("Target", siteMapTab, globalSearchTab, organizerTab));
        addTopModule(repeaterTab);
        addTopModule(workspace("Intruder", intruderTab, turboIntruderTab));
        addTopModule(spiderCrawlerTab);
        addTopModule(scannerTab);
        addTopModule(decoderTab);
        addTopModule(comparerTab);
        addTopModule(workspace("Logger", loggerTab, sessionRecorderTab, authManagerTab));
        addTopModule(reportTab);
        addTopModule(workspace("Extensions", extensionTabs.toArray(new Tab[0])));
        addTopModule(settingsTab);

        configureWorkspaceSelector();
        notificationService.notifications().addListener((ListChangeListener<NotificationEntry>) change -> updateNotificationBadge());
        updateNotificationBadge();
        refreshAiStatus();
        tabs.getSelectionModel().selectedItemProperty().addListener((obs, old, tab) -> selectNavigation(activeModuleTab()));
        tabs.getSelectionModel().select(dashboardTab);

        setTop(topNavigation());
        setCenter(contentShell());
        setBottom(statusBar());
        selectNavigation(activeModuleTab());
        refreshStatus();
    }

    public void setThemeManager(ThemeManager themeManager) {
        this.themeManager = themeManager;
    }

    public List<String> themes() {
        return List.of("CyvoraX Navy/Teal", "Light", "Dark", "Midnight", "Hacker", "CyvoraX OLED");
    }

    public String currentTheme() {
        return themeManager == null ? "CyvoraX Navy/Teal" : themeManager.currentTheme();
    }

    public void applyTheme(String theme) {
        if (themeManager != null && getScene() != null) {
            themeManager.apply(getScene(), theme);
        }
    }

    public void refreshAiStatus() {
        if (aiProviderConfig == null) {
            aiStatus.setText("AI: unavailable");
            return;
        }
        AiProviderSettings settings = aiProviderConfig.load();
        AiProviderSettings.ProviderSettings active = settings.active();
        aiStatus.setText("AI: " + settings.activeProvider().displayName()
                + (active.hasToken() ? " ready" : " no key"));
    }

    public String appVersion() {
        return appVersion;
    }

    public void showNotificationCenter() {
        new NotificationCenterDialog(notificationService).show(getScene() == null ? null : getScene().getWindow());
        updateNotificationBadge();
    }

    public void showBackupManager() {
        new BackupManagerDialog(backupManager, workspaceManager, workspaceInfo, notificationService, workspaceSwitchHandler)
                .show(getScene() == null ? null : getScene().getWindow(),
                        database.getSetting("backup.schedule", "Off"),
                        value -> database.setSetting("backup.schedule", value));
    }

    public void runScheduledBackupIfNeeded() {
        String schedule = database.getSetting("backup.schedule", "Off");
        Instant lastBackup = parseInstant(database.getSetting("backup.lastBackupAt", ""));
        backupManager.maybeRunScheduledBackup(workspaceInfo, schedule, lastBackup).ifPresent(backup -> {
            database.setSetting("backup.lastBackupAt", backup.createdAt().toString());
            notificationService.publish("Backup Complete", "Scheduled backup complete",
                    backup.workspaceName() + " saved to " + backup.backupPath());
        });
    }

    private void configureWorkspaceSelector() {
        workspaceSelector.setCellFactory(list -> workspaceCell());
        workspaceSelector.setButtonCell(workspaceCell());
        refreshWorkspaceSelector();
        workspaceSelector.valueProperty().addListener((obs, old, selected) -> {
            if (!updatingWorkspaceSelector && selected != null && !selected.getId().equals(workspaceInfo.getId())) {
                switchWorkspace(workspaceManager.openWorkspace(selected.getId()));
            }
        });
    }

    private ListCell<WorkspaceInfo> workspaceCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(WorkspaceInfo workspace, boolean empty) {
                super.updateItem(workspace, empty);
                setText(empty || workspace == null ? "" : workspace.getName());
            }
        };
    }

    private void refreshWorkspaceSelector() {
        updatingWorkspaceSelector = true;
        try {
            List<WorkspaceInfo> workspaces = workspaceManager.listWorkspaces();
            workspaceSelector.getItems().setAll(workspaces);
            workspaces.stream()
                    .filter(workspace -> workspace.getId().equals(workspaceInfo.getId()))
                    .findFirst()
                    .ifPresent(workspace -> workspaceSelector.getSelectionModel().select(workspace));
        } finally {
            updatingWorkspaceSelector = false;
        }
    }

    private Button toolbarButton(String text, String tooltip, Runnable action) {
        Button button = new Button(text);
        button.setTooltip(new Tooltip(tooltip));
        button.getStyleClass().add("toolbar-button");
        button.setOnAction(event -> action.run());
        return button;
    }

    private void createWorkspace() {
        TextInputDialog dialog = new TextInputDialog("New Assessment");
        dialog.setTitle("Create Workspace");
        dialog.setHeaderText("Create a CyvoraX workspace");
        dialog.setContentText("Workspace name");
        dialog.showAndWait().ifPresent(name -> {
            WorkspaceInfo workspace = workspaceManager.createWorkspace(name);
            notificationService.publish("Recovery Events", "Workspace created", workspace.getName() + " is ready");
            switchWorkspace(workspaceManager.openWorkspace(workspace.getId()));
        });
    }

    private void renameWorkspace() {
        TextInputDialog dialog = new TextInputDialog(workspaceInfo.getName());
        dialog.setTitle("Rename Workspace");
        dialog.setHeaderText("Rename " + workspaceInfo.getName());
        dialog.setContentText("Workspace name");
        dialog.showAndWait().ifPresent(name -> {
            WorkspaceInfo renamed = workspaceManager.renameWorkspace(workspaceInfo.getId(), name);
            notificationService.publish("Recovery Events", "Workspace renamed", renamed.getName());
            switchWorkspace(workspaceManager.openWorkspace(renamed.getId()));
        });
    }

    private void duplicateWorkspace() {
        TextInputDialog dialog = new TextInputDialog(workspaceInfo.getName() + " Copy");
        dialog.setTitle("Duplicate Workspace");
        dialog.setHeaderText("Duplicate " + workspaceInfo.getName());
        dialog.setContentText("New workspace name");
        dialog.showAndWait().ifPresent(name -> {
            WorkspaceInfo duplicate = workspaceManager.duplicateWorkspace(workspaceInfo.getId(), name);
            notificationService.publish("Recovery Events", "Workspace duplicated", duplicate.getName());
            switchWorkspace(workspaceManager.openWorkspace(duplicate.getId()));
        });
    }

    private void deleteWorkspace() {
        List<WorkspaceInfo> workspaces = workspaceManager.listWorkspaces();
        if (workspaces.size() <= 1) {
            new Alert(Alert.AlertType.INFORMATION, "Create or open another workspace before deleting the current one.").showAndWait();
            return;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Workspace");
        alert.setHeaderText("Delete " + workspaceInfo.getName() + "?");
        alert.setContentText("The workspace folder will be moved to the CyvoraX workspace trash.");
        alert.showAndWait().ifPresent(choice -> {
            if (choice == ButtonType.OK) {
                workspaceManager.deleteWorkspace(workspaceInfo.getId());
                notificationService.publish("Recovery Events", "Workspace deleted", workspaceInfo.getName() + " moved to workspace trash");
                workspaceManager.listWorkspaces().stream().findFirst().ifPresent(next -> switchWorkspace(workspaceManager.openWorkspace(next.getId())));
            }
        });
    }

    private void switchWorkspace(WorkspaceInfo workspace) {
        workspaceSwitchHandler.accept(workspace);
    }

    private void updateNotificationBadge() {
        long unread = notificationService.unreadCount();
        notificationButton.setText(unread > 0 ? "!" + unread : "!");
    }

    private int parsePort(String value) {
        try {
            int port = Integer.parseInt(value == null ? "" : value.trim());
            return port > 0 && port <= 65535 ? port : 8080;
        } catch (RuntimeException ex) {
            return 8080;
        }
    }

    private Instant parseInstant(String value) {
        try {
            return value == null || value.isBlank() ? null : Instant.parse(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public void showCrashReports() {
        Window owner = getScene() == null ? null : getScene().getWindow();
        new CrashReportViewerDialog(crashReporter).show(owner);
    }

    public void showShortcutHelp() {
        ShortcutHelpDialog.show(getScene() == null ? null : getScene().getWindow());
    }

    public void checkForUpdates(boolean notifyWhenCurrent, Consumer<UpdateInfo> callback) {
        if (updateService == null) {
            return;
        }
        Task<UpdateInfo> task = new Task<>() {
            @Override
            protected UpdateInfo call() throws Exception {
                return updateService.checkForUpdates();
            }
        };
        task.setOnSucceeded(event -> {
            UpdateInfo info = task.getValue();
            database.setSetting("updates.latestVersion", info.latestVersion());
            if (callback != null) {
                callback.accept(info);
            }
            if (info.updateAvailable()) {
                notificationService.publish("Update Available", "Update available",
                        "Latest version " + info.latestVersion() + " is available from GitHub Releases");
                new UpdateDialog(updateService, info).show(getScene() == null ? null : getScene().getWindow());
            } else if (notifyWhenCurrent) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "CyvoraX Suite is up to date.");
                alert.setTitle("Updates");
                alert.setHeaderText("No update available");
                alert.showAndWait();
            }
        });
        task.setOnFailed(event -> {
            if (notifyWhenCurrent) {
                Alert alert = new Alert(Alert.AlertType.ERROR,
                        "Update check failed: " + SecretMasker.maskSecrets(task.getException().getMessage()));
                alert.setTitle("Updates");
                alert.setHeaderText("Could not check GitHub Releases");
                alert.showAndWait();
            }
        });
        Thread thread = new Thread(task, "cyvorax-update-check");
        thread.setDaemon(true);
        thread.start();
    }

    public void runStartupUpdateCheck() {
        if (Boolean.parseBoolean(database.getSetting("updates.checkOnStartup", "true"))) {
            checkForUpdates(false, null);
        }
    }

    public void showCrashRecoveryIfNeeded(CrashReport latestReport) {
        if (latestReport == null) {
            return;
        }
        String seen = database.getSetting("crash.lastSeenReport", "");
        String path = latestReport.path().toAbsolutePath().toString();
        if (path.equals(seen)) {
            return;
        }
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Crash Recovery");
        alert.setHeaderText("CyvoraX detected a previous crash");
        alert.setContentText("A crash report was saved. You can review it now or continue working.");
        ButtonType view = new ButtonType("View Report");
        ButtonType dismiss = new ButtonType("Dismiss");
        alert.getButtonTypes().setAll(view, dismiss);
        alert.showAndWait().ifPresent(choice -> {
            database.setSetting("crash.lastSeenReport", path);
            if (choice == view) {
                showCrashReports();
            }
            notificationService.publish("Recovery Events", "Crash report reviewed", latestReport.path().getFileName().toString());
        });
    }

    public void showSessionRecoveryPrompt(SessionSnapshot snapshot, Stage stage) {
        if (snapshot == null) {
            return;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Session Recovery");
        alert.setHeaderText("Restore previous CyvoraX session?");
        alert.setContentText("CyvoraX did not shut down cleanly. Restore the saved window layout, selected module, repeater tab, search state, and scanner filter?");
        ButtonType restore = new ButtonType("Restore Session");
        ButtonType skip = new ButtonType("Skip");
        alert.getButtonTypes().setAll(restore, skip);
        alert.showAndWait().ifPresent(choice -> {
            if (choice == restore) {
                restoreSnapshot(snapshot, stage);
                notificationService.publish("Recovery Events", "Session restored",
                        "Restored " + snapshot.selectedModule() + " from " + snapshot.timestamp());
            } else {
                notificationService.publish("Recovery Events", "Session recovery skipped",
                        "Snapshot from " + snapshot.timestamp() + " was not restored");
            }
        });
    }

    public void startAutoSnapshots(Stage stage) {
        if (snapshotTimeline != null) {
            snapshotTimeline.stop();
        }
        snapshotTimeline = new Timeline(new KeyFrame(Duration.seconds(30), event -> saveSnapshot(stage)));
        snapshotTimeline.setCycleCount(Timeline.INDEFINITE);
        snapshotTimeline.play();
    }

    public void stopAutoSnapshots() {
        if (snapshotTimeline != null) {
            snapshotTimeline.stop();
            snapshotTimeline = null;
        }
    }

    public void saveSnapshot(Stage stage) {
        sessionRecoveryManager.saveSnapshot(captureSnapshot(stage));
    }

    public void saveWorkspaceState() {
        Window window = getScene() == null ? null : getScene().getWindow();
        if (window instanceof Stage stage) {
            saveSnapshot(stage);
        }
        database.setSetting("workspace.lastSavedAt", Instant.now().toString());
        requestCount.setText("Workspace saved");
    }

    public SessionSnapshot captureSnapshot(Stage stage) {
        return new SessionSnapshot(
                Instant.now(),
                workspaceInfo.getId(),
                selectedModuleTitle(),
                repeaterTab.selectedRequestTabIndex(),
                globalSearchTab.searchQuery(),
                scannerTab.scannerUrl(),
                scannerTab.selectedSeverityFilter(),
                stage == null ? Double.NaN : stage.getX(),
                stage == null ? Double.NaN : stage.getY(),
                stage == null ? 1320 : stage.getWidth(),
                stage == null ? 860 : stage.getHeight(),
                stage != null && stage.isMaximized()
        );
    }

    public void restoreSnapshot(SessionSnapshot snapshot, Stage stage) {
        if (!Double.isNaN(snapshot.windowX())) {
            stage.setX(snapshot.windowX());
        }
        if (!Double.isNaN(snapshot.windowY())) {
            stage.setY(snapshot.windowY());
        }
        stage.setWidth(Math.max(900, snapshot.windowWidth()));
        stage.setHeight(Math.max(620, snapshot.windowHeight()));
        stage.setMaximized(snapshot.maximized());
        repeaterTab.selectRequestTabIndex(snapshot.repeaterSelectedIndex());
        globalSearchTab.restoreSearchQuery(snapshot.searchQuery());
        scannerTab.restoreScannerState(snapshot.scannerUrl(), snapshot.scannerSeverity());
        selectByTitle(snapshot.selectedModule());
    }

    private void showQuickSearch() {
        new QuickSearchDialog(database, this::openSearchResult)
                .show(getScene() == null ? null : getScene().getWindow());
    }

    private void showCommandPalette() {
        new CommandPaletteDialog(commands()).show(getScene() == null ? null : getScene().getWindow());
    }

    private List<CommandPaletteDialog.Command> commands() {
        List<CommandPaletteDialog.Command> commands = new ArrayList<>();
        modulesByTitle.forEach((title, tab) -> commands.add(new CommandPaletteDialog.Command(
                "Open Module: " + title, "", "Open " + title, () -> select(tab))));
        commands.add(new CommandPaletteDialog.Command("Start Proxy", "", "Start the configured proxy listener", () -> {
            int port = parsePort(database.getSetting("proxy.port", "8080"));
            startProxy(database.getSetting("proxy.host", "127.0.0.1"), port);
        }));
        commands.add(new CommandPaletteDialog.Command("Stop Proxy", "", "Stop the proxy listener", this::stopProxy));
        commands.add(new CommandPaletteDialog.Command("Create Workspace", "", "Create and switch to a new workspace", this::createWorkspace));
        commands.add(new CommandPaletteDialog.Command("Rename Workspace", "", "Rename the active workspace", this::renameWorkspace));
        commands.add(new CommandPaletteDialog.Command("Duplicate Workspace", "", "Duplicate the active workspace", this::duplicateWorkspace));
        commands.add(new CommandPaletteDialog.Command("Delete Workspace", "", "Move the active workspace to trash", this::deleteWorkspace));
        workspaceManager.listWorkspaces().forEach(workspace -> commands.add(new CommandPaletteDialog.Command(
                "Switch Workspace: " + workspace.getName(), "", "Open recent workspace " + workspace.getPath(),
                () -> switchWorkspace(workspaceManager.openWorkspace(workspace.getId())))));
        commands.add(new CommandPaletteDialog.Command("Open Settings", "Ctrl+,", "Open application settings", () -> selectByTitle("Settings")));
        commands.add(new CommandPaletteDialog.Command("Export Report", "", "Export the selected report template", () -> {
            select(reportTab);
            reportTab.exportSelectedTemplate();
        }));
        commands.add(new CommandPaletteDialog.Command("Search Requests", "Ctrl+K", "Search requests, responses, notes, and findings", this::showQuickSearch));
        commands.add(new CommandPaletteDialog.Command("Run Commands", "Ctrl+Shift+P", "Open the command palette", this::showCommandPalette));
        commands.add(new CommandPaletteDialog.Command("Backup Manager", "", "Create, browse, and restore workspace backups", this::showBackupManager));
        commands.add(new CommandPaletteDialog.Command("Notification Center", "", "Review saved workflow notifications", this::showNotificationCenter));
        commands.add(new CommandPaletteDialog.Command("Save Workspace", "Ctrl+S", "Save recovery snapshot and workspace state", this::saveWorkspaceState));
        commands.add(new CommandPaletteDialog.Command("Check for Updates", "", "Check GitHub Releases for a newer installer", () -> checkForUpdates(true, null)));
        commands.add(new CommandPaletteDialog.Command("Crash Reports", "", "Open the crash report viewer", this::showCrashReports));
        commands.add(new CommandPaletteDialog.Command("Keyboard Shortcuts", "", "Show shortcut documentation", this::showShortcutHelp));
        return commands;
    }

    public void installShortcuts(Scene scene) {
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+R"), () -> select(repeaterTab));
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+I"), () -> select(intruderTab));
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+D"), () -> selectByTitle("Decoder"));
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+Shift+P"), this::showCommandPalette);
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+F"), () -> {
            select(globalSearchTab);
            globalSearchTab.focusSearch();
        });
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+Shift+F"), () -> {
            select(globalSearchTab);
            globalSearchTab.focusSearch();
        });
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+K"), this::showQuickSearch);
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+S"), this::saveWorkspaceState);
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+COMMA"), () -> selectByTitle("Settings"));
        for (int i = 1; i <= 9; i++) {
            int index = i - 1;
            scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+" + i), () -> {
                if (index < tabs.getTabs().size()) {
                    select(tabs.getTabs().get(index));
                }
            });
        }
    }

    private Tab activeModuleTab() {
        Tab top = tabs.getSelectionModel().getSelectedItem();
        if (top == null) {
            return dashboardTab;
        }
        if (top.getContent() instanceof TabPane workspaceTabs
                && workspaceTabs.getSelectionModel().getSelectedItem() != null) {
            return workspaceTabs.getSelectionModel().getSelectedItem();
        }
        return top;
    }

    private String selectedModuleTitle() {
        Tab active = activeModuleTab();
        return active == null ? "Dashboard" : active.getText();
    }

    private void select(Tab tab) {
        if (tab == null) {
            return;
        }
        Tab workspace = moduleWorkspaces.get(tab);
        if (workspace != null) {
            tabs.getSelectionModel().select(workspace);
            TabPane host = moduleHosts.get(tab);
            if (host != null && host.getTabs().contains(tab)) {
                host.getSelectionModel().select(tab);
            }
            selectNavigation(tab);
            return;
        }
        if (tabs.getTabs().contains(tab)) {
            tabs.getSelectionModel().select(tab);
            selectNavigation(activeModuleTab());
        }
    }

    private void selectByTitle(String title) {
        if (title == null) {
            return;
        }
        Tab tab = modulesByTitle.get(title);
        if (tab != null) {
            select(tab);
        }
    }

    private void enableDetachableTab(Tab tab) {
        if (tab == dashboardTab) {
            return;
        }
        MenuItem detach = new MenuItem("Detach");
        detach.setOnAction(event -> detach(tab));
        tab.setContextMenu(new ContextMenu(detach));
    }

    private void detach(Tab tab) {
        TabPane owner = tab.getTabPane();
        Node content = tab.getContent();
        if (content == null || owner == null) {
            return;
        }
        int index = owner.getTabs().indexOf(tab);
        tab.setContent(null);
        owner.getTabs().remove(tab);
        ToggleButton navigationButton = navigationButtons.get(tab);
        if (navigationButton != null) {
            navigationButton.setDisable(true);
        }
        Stage stage = new Stage();
        stage.setTitle("CyvoraX Suite - " + tab.getText());
        Scene scene = new Scene(new BorderPane(content), 1100, 760);
        if (themeManager != null) {
            themeManager.apply(scene, themeManager.currentTheme());
        }
        String prefix = "window." + tab.getText().replaceAll("\\W+", ".").toLowerCase();
        stage.setWidth(Double.parseDouble(database.getSetting(prefix + ".width", "1100")));
        stage.setHeight(Double.parseDouble(database.getSetting(prefix + ".height", "760")));
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            database.setSetting(prefix + ".width", String.valueOf(stage.getWidth()));
            database.setSetting(prefix + ".height", String.valueOf(stage.getHeight()));
            tab.setContent(content);
            owner.getTabs().add(Math.min(index, owner.getTabs().size()), tab);
            if (navigationButton != null) {
                navigationButton.setDisable(false);
            }
            select(tab);
        });
        stage.show();
    }

    private void addTopModule(Tab tab) {
        tab.setClosable(false);
        tabs.getTabs().add(tab);
        modulesByTitle.put(tab.getText(), tab);
        enableDetachableTab(tab);
    }

    private Tab workspace(String title, Tab... children) {
        TabPane workspaceTabs = new TabPane();
        workspaceTabs.getStyleClass().addAll("workspace-tabs", "desktop-workspace-tabs");
        workspaceTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Tab workspace = new Tab(title, workspaceTabs);
        workspace.setClosable(false);
        modulesByTitle.put(title, workspace);
        for (Tab child : children) {
            child.setClosable(false);
            workspaceTabs.getTabs().add(child);
            moduleHosts.put(child, workspaceTabs);
            moduleWorkspaces.put(child, workspace);
            modulesByTitle.put(child.getText(), child);
            enableDetachableTab(child);
        }
        workspaceTabs.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (tabs.getSelectionModel().getSelectedItem() == workspace) {
                selectNavigation(selected);
            }
        });
        return workspace;
    }

    private BorderPane contentShell() {
        BorderPane shell = new BorderPane();
        shell.getStyleClass().add("content-shell");
        shell.setCenter(tabs);
        return shell;
    }

    private VBox topNavigation() {
        moduleTitle.getStyleClass().add("content-title");
        Label brand = new Label("CyvoraX");
        brand.getStyleClass().add("topnav-brand");
        Label version = new Label(appVersion);
        version.getStyleClass().add("status-pill");
        workspaceSelector.setMinWidth(190);
        workspaceSelector.setPrefWidth(240);
        Button newWorkspace = toolbarButton("+", "Create Workspace", this::createWorkspace);
        Button renameWorkspace = toolbarButton("Ren", "Rename Workspace", this::renameWorkspace);
        Button duplicateWorkspace = toolbarButton("Dup", "Duplicate Workspace", this::duplicateWorkspace);
        Button deleteWorkspace = toolbarButton("Del", "Delete Workspace", this::deleteWorkspace);
        Button backup = toolbarButton("Bak", "Open Backup Manager", this::showBackupManager);
        notificationButton.setTooltip(new Tooltip("Notification Center"));
        notificationButton.setOnAction(event -> showNotificationCenter());
        notificationButton.getStyleClass().add("notification-button");
        Label searchHint = new Label("Ctrl+Shift+P");
        searchHint.getStyleClass().add("status-pill");
        aiStatus.getStyleClass().add("status-pill");
        HBox header = new HBox(8, brand, version, workspaceSelector, newWorkspace, renameWorkspace,
                duplicateWorkspace, deleteWorkspace, backup, notificationButton, aiStatus, spacer(), searchHint);
        header.getStyleClass().addAll("content-header", "desktop-toolbar");
        header.setPadding(new Insets(6, 10, 6, 10));
        return new VBox(header);
    }

    private Node spacer() {
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private ToggleButton navigationButton(Tab tab) {
        ToggleButton button = new ToggleButton(tab.getText());
        button.getStyleClass().add("subnav-item");
        button.setOnAction(event -> {
            if (!button.isDisabled()) {
                select(tab);
            }
        });
        if (tab != dashboardTab) {
            MenuItem detach = new MenuItem("Detach");
            detach.setOnAction(event -> detach(tab));
            button.setContextMenu(new ContextMenu(detach));
        }
        navigationButtons.put(tab, button);
        return button;
    }

    private void selectNavigation(Tab tab) {
        if (tab == null) {
            return;
        }
        moduleTitle.setText(tab.getText());
        selectedModuleStatus.setText("Module: " + tab.getText());
        workspaceStatus.setText("Workspace: " + workspaceInfo.getName());
        database.setSetting("layout.selectedModule", tab.getText());
    }

    private void updateSecondaryNavigation(String group) {
        List<Tab> groupTabs = tabs.getTabs().stream()
                .filter(tab -> group.equals(tabGroups.get(tab)))
                .toList();
        secondaryNavigation.getChildren().clear();
        for (Tab tab : groupTabs) {
            secondaryNavigation.getChildren().add(navigationButton(tab));
        }
    }

    private String primaryLabel(String group) {
        return group;
    }

    private HBox statusBar() {
        workspaceStatus.setText("Workspace: " + workspaceInfo.getName());
        HBox bar = new HBox(18, workspaceStatus, selectedModuleStatus, proxyStatus, interceptStatus, requestCount);
        bar.getStyleClass().add("status-bar");
        bar.setPadding(new Insets(8, 12, 8, 12));
        return bar;
    }

    public void startProxy(String host, int port) {
        proxyServer.start(host, port);
        notificationService.publish("Recovery Events", "Proxy started", host + ":" + port);
        refreshStatus();
    }

    public void stopProxy() {
        proxyServer.stop();
        notificationService.publish("Recovery Events", "Proxy stopped", "Proxy listener stopped");
        refreshStatus();
    }

    public void setIntercept(boolean enabled) {
        proxyServer.setIntercept(enabled);
        refreshStatus();
    }

    public void configureNetwork(String upstreamProxy, int timeoutSeconds) {
        proxyServer.configureNetwork(upstreamProxy, timeoutSeconds);
        refreshStatus();
    }

    public String setting(String key, String defaultValue) {
        return database.getSetting(key, defaultValue);
    }

    public void saveSetting(String key, String value) {
        database.setSetting(key, value);
    }

    public void saveScopeSettings(ScopeControl scopeControl) {
        database.setSetting("scope.includes", scopeControl.includesAsText());
        database.setSetting("scope.excludes", scopeControl.excludesAsText());
        database.setSetting("scope.ignores", scopeControl.ignoresAsText());
        database.setSetting("scope.outOfScopePassthrough", String.valueOf(scopeControl.isOutOfScopePassthrough()));
    }

    public boolean isProxyRunning() {
        return proxyServer.isRunning();
    }

    public boolean isInterceptEnabled() {
        return proxyServer.isIntercept();
    }

    public CertManager getCertManager() {
        return certManager;
    }

    private void refreshStatus() {
        proxyStatus.setText("Proxy: " + (proxyServer.isRunning() ? "on" : "off"));
        interceptStatus.setText("Intercept: " + (proxyServer.isIntercept() ? "on" : "off"));
        requestCount.setText("Requests: " + proxyServer.getRequestCount());
        dashboardTab.refresh(proxyServer.isRunning(), proxyServer.isIntercept(), proxyServer.getRequestCount());
    }

    private void openSearchResult(SearchResult result) {
        if ("History".equals(result.getType())) {
            select(historyTab);
            historyTab.selectTransaction(result.getRecordId());
            return;
        }
        if ("Finding".equals(result.getType())) {
            select(scannerTab);
            scannerTab.selectFinding(result.getRecordId());
            return;
        }
        if ("Session".equals(result.getType())) {
            select(sessionRecorderTab);
            sessionRecorderTab.selectRecording(result.getRecordId());
        }
    }

    @Override
    public void onTransaction(HttpTransaction transaction) {
        sessionRecorder.record(transaction);
        Platform.runLater(() -> {
            history.add(0, transaction);
            refreshStatus();
        });
    }

    @Override
    public void onFinding(Finding finding) {
        Platform.runLater(() -> findings.add(0, finding));
    }

    @Override
    public void onLog(LogEntry entry) {
        Platform.runLater(() -> logs.add(0, entry));
    }

    @Override
    public void onInterceptPending(InterceptedRequest request) {
        Platform.runLater(() -> proxyTab.showPending(request));
    }
}
