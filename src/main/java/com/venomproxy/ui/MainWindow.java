package com.venomproxy.ui;

import com.venomproxy.auth.AuthenticationManager;
import com.venomproxy.db.Database;
import com.venomproxy.model.Finding;
import com.venomproxy.model.HttpTransaction;
import com.venomproxy.model.LogEntry;
import com.venomproxy.model.SearchResult;
import com.venomproxy.plugins.PluginLoader;
import com.venomproxy.proxy.CertManager;
import com.venomproxy.proxy.InterceptedRequest;
import com.venomproxy.proxy.MatchReplaceEngine;
import com.venomproxy.proxy.ProxyEventListener;
import com.venomproxy.proxy.ProxyServer;
import com.venomproxy.proxy.ScopeControl;
import com.venomproxy.scanner.ActiveScanner;
import com.venomproxy.scanner.PassiveScanner;
import com.venomproxy.session.SessionRecorder;
import com.venomproxy.workspace.WorkspaceInfo;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainWindow extends BorderPane implements ProxyEventListener {
    private final Database database;
    private final ProxyServer proxyServer;
    private final CertManager certManager;
    private final ScopeControl scopeControl;
    private final WorkspaceInfo workspaceInfo;
    private final ObservableList<HttpTransaction> history;
    private final ObservableList<Finding> findings;
    private final ObservableList<LogEntry> logs;
    private final Label proxyStatus = new Label("Proxy: off");
    private final Label interceptStatus = new Label("Intercept: off");
    private final Label requestCount = new Label("Requests: 0");
    private final TabPane tabs = new TabPane();
    private final Label moduleTitle = new Label("Dashboard");
    private final Map<Tab, ToggleButton> navigationButtons = new LinkedHashMap<>();
    private final Map<Tab, String> tabGroups = new LinkedHashMap<>();
    private final Map<String, ToggleButton> primaryNavigationButtons = new LinkedHashMap<>();
    private final HBox secondaryNavigation = new HBox(6);
    private final DashboardTab dashboardTab;
    private final ProxyTab proxyTab;
    private final RepeaterTab repeaterTab;
    private final IntruderTab intruderTab;
    private final ScannerTab scannerTab;
    private final HistoryTab historyTab;
    private final GlobalSearchTab globalSearchTab;
    private final SessionRecorderTab sessionRecorderTab;
    private final SessionRecorder sessionRecorder;
    private ThemeManager themeManager;

    public MainWindow(Database database, ProxyServer proxyServer, PassiveScanner passiveScanner,
                      ActiveScanner activeScanner, CertManager certManager, PluginLoader pluginLoader,
                      ScopeControl scopeControl, MatchReplaceEngine matchReplaceEngine,
                      AuthenticationManager authenticationManager, SessionRecorder sessionRecorder, Path toolsDirectory,
                      WorkspaceInfo workspaceInfo) {
        this.database = database;
        this.proxyServer = proxyServer;
        this.certManager = certManager;
        this.scopeControl = scopeControl;
        this.workspaceInfo = workspaceInfo;
        this.sessionRecorder = sessionRecorder;
        this.history = FXCollections.observableArrayList(database.listTransactions());
        this.findings = FXCollections.observableArrayList(database.listFindings());
        this.logs = FXCollections.observableArrayList(database.listLogs());
        this.proxyServer.setListener(this);
        pluginLoader.load(database, scopeControl);
        Map<String, List<Tab>> navigationGroups = new LinkedHashMap<>();

        this.dashboardTab = new DashboardTab(this, database, history, findings, logs, certManager, pluginLoader, sessionRecorder);
        this.proxyTab = new ProxyTab(proxyServer);
        this.repeaterTab = new RepeaterTab(database);
        this.intruderTab = new IntruderTab();
        this.scannerTab = new ScannerTab(findings, activeScanner, database);
        this.globalSearchTab = new GlobalSearchTab(database, this::openSearchResult);
        this.historyTab = new HistoryTab(database, history, tx -> repeaterTab.openTransaction(tx),
                tx -> intruderTab.loadTransaction(tx), tx -> scannerTab.scanTransaction(tx), scopeControl);
        this.sessionRecorderTab = new SessionRecorderTab(database, sessionRecorder);

        tabs.getStyleClass().addAll("main-tabs", "sidebar-backed-tabs");
        addModule(navigationGroups, "Dashboard", dashboardTab);
        addModule(navigationGroups, "Proxy", proxyTab);
        addModule(navigationGroups, "Proxy", historyTab);
        addModule(navigationGroups, "Proxy", new MatchReplaceTab(matchReplaceEngine));
        addModule(navigationGroups, "Target", new SiteMapTab(database, history));
        addModule(navigationGroups, "Target", globalSearchTab);
        addModule(navigationGroups, "Target", new OrganizerTab(database, history));
        addModule(navigationGroups, "Repeater", repeaterTab);
        addModule(navigationGroups, "Intruder", intruderTab);
        addModule(navigationGroups, "Intruder", new TurboIntruderTab(toolsDirectory));
        addModule(navigationGroups, "Spider", new SpiderCrawlerTab(database, history, scopeControl, toolsDirectory));
        addModule(navigationGroups, "Scanner", scannerTab);
        addModule(navigationGroups, "Decoder", new DecoderTab());
        addModule(navigationGroups, "Comparer", new ComparerTab());
        addModule(navigationGroups, "Sessions", sessionRecorderTab);
        addModule(navigationGroups, "Sessions", new AuthManagerTab(authenticationManager));
        addModule(navigationGroups, "Reports", new ReportTab(findings, history));
        addModule(navigationGroups, "Sessions", new LoggerTab(logs));
        addModule(navigationGroups, "Extensions", new PluginManagerTab(pluginLoader, database, scopeControl));
        addModule(navigationGroups, "Settings", new SettingsTab(this, scopeControl));
        pluginLoader.plugins().forEach(plugin -> plugin.uiTab().ifPresent(node -> {
            javafx.scene.control.Tab tab = new javafx.scene.control.Tab(plugin.name(), node);
            addModule(navigationGroups, "Extensions", tab);
        }));
        tabs.getTabs().forEach(this::enableDetachableTab);
        tabs.getSelectionModel().selectedItemProperty().addListener((obs, old, tab) -> selectNavigation(tab));

        setTop(topNavigation(navigationGroups));
        setCenter(contentShell());
        setBottom(statusBar());
        selectNavigation(tabs.getSelectionModel().getSelectedItem());
        refreshStatus();
    }

    public void setThemeManager(ThemeManager themeManager) {
        this.themeManager = themeManager;
    }

    public List<String> themes() {
        return List.of("CyvoraX Navy/Teal", "Light", "Dark", "Midnight", "Hacker");
    }

    public String currentTheme() {
        return themeManager == null ? "CyvoraX Navy/Teal" : themeManager.currentTheme();
    }

    public void applyTheme(String theme) {
        if (themeManager != null && getScene() != null) {
            themeManager.apply(getScene(), theme);
        }
    }

    public void installShortcuts(Scene scene) {
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+R"), () -> select(repeaterTab));
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+I"), () -> select(intruderTab));
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+D"), () -> selectByTitle("Decoder"));
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+F"), () -> {
            select(globalSearchTab);
            globalSearchTab.focusSearch();
        });
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+K"), () -> {
            select(globalSearchTab);
            globalSearchTab.focusSearch();
        });
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+S"), () -> select(scannerTab));
    }

    private void select(Tab tab) {
        tabs.getSelectionModel().select(tab);
    }

    private void selectByTitle(String title) {
        tabs.getTabs().stream()
                .filter(tab -> tab.getText().equals(title))
                .findFirst()
                .ifPresent(this::select);
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
        Node content = tab.getContent();
        if (content == null || tab.getTabPane() == null) {
            return;
        }
        int index = tabs.getTabs().indexOf(tab);
        tab.setContent(null);
        tabs.getTabs().remove(tab);
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
            tabs.getTabs().add(Math.min(index, tabs.getTabs().size()), tab);
            if (navigationButton != null) {
                navigationButton.setDisable(false);
            }
            tabs.getSelectionModel().select(tab);
        });
        stage.show();
    }

    private void addModule(Map<String, List<Tab>> navigationGroups, String group, Tab tab) {
        tabs.getTabs().add(tab);
        navigationGroups.computeIfAbsent(group, key -> new ArrayList<>()).add(tab);
        tabGroups.put(tab, group);
    }

    private BorderPane contentShell() {
        BorderPane shell = new BorderPane();
        shell.getStyleClass().add("content-shell");
        shell.setCenter(tabs);
        return shell;
    }

    private VBox topNavigation(Map<String, List<Tab>> navigationGroups) {
        Label workspaceLabel = new Label("Workspace: " + workspaceInfo.getName());
        workspaceLabel.getStyleClass().add("content-kicker");
        moduleTitle.getStyleClass().add("content-title");
        VBox titleBox = new VBox(2, workspaceLabel, moduleTitle);
        Label brand = new Label("CyvoraX");
        brand.getStyleClass().add("topnav-brand");
        Label searchHint = new Label("Ctrl+K Search");
        searchHint.getStyleClass().add("status-pill");
        HBox header = new HBox(16, brand, titleBox, spacer(), searchHint);
        header.getStyleClass().add("content-header");
        header.setPadding(new Insets(14, 18, 12, 18));

        ToggleGroup toggleGroup = new ToggleGroup();
        FlowPane primary = new FlowPane(6, 6);
        primary.getStyleClass().add("topnav-primary");
        for (Map.Entry<String, List<Tab>> group : navigationGroups.entrySet()) {
            ToggleButton button = new ToggleButton(primaryLabel(group.getKey()));
            button.getStyleClass().add("topnav-item");
            button.setToggleGroup(toggleGroup);
            button.setOnAction(event -> select(group.getValue().get(0)));
            primaryNavigationButtons.put(group.getKey(), button);
            primary.getChildren().add(button);
        }
        secondaryNavigation.getStyleClass().add("topnav-secondary");
        secondaryNavigation.setPadding(new Insets(0, 18, 12, 18));
        HBox primaryShell = new HBox(primary);
        primaryShell.getStyleClass().add("topnav-shell");
        primaryShell.setPadding(new Insets(0, 18, 8, 18));
        return new VBox(header, primaryShell, secondaryNavigation);
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
        String group = tabGroups.get(tab);
        if (group != null) {
            updateSecondaryNavigation(group);
            ToggleButton primary = primaryNavigationButtons.get(group);
            if (primary != null && !primary.isSelected()) {
                primary.setSelected(true);
            }
        }
        ToggleButton button = navigationButtons.get(tab);
        if (button != null && !button.isSelected()) {
            button.setSelected(true);
        }
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
        HBox bar = new HBox(18, proxyStatus, interceptStatus, requestCount);
        bar.getStyleClass().add("status-bar");
        bar.setPadding(new Insets(8, 12, 8, 12));
        return bar;
    }

    public void startProxy(String host, int port) {
        proxyServer.start(host, port);
        refreshStatus();
    }

    public void stopProxy() {
        proxyServer.stop();
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
