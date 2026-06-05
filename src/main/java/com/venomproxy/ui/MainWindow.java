package com.venomproxy.ui;

import com.venomproxy.auth.AuthenticationManager;
import com.venomproxy.db.Database;
import com.venomproxy.model.Finding;
import com.venomproxy.model.HttpTransaction;
import com.venomproxy.model.LogEntry;
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
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.List;

public class MainWindow extends BorderPane implements ProxyEventListener {
    private final Database database;
    private final ProxyServer proxyServer;
    private final CertManager certManager;
    private final ScopeControl scopeControl;
    private final ObservableList<HttpTransaction> history;
    private final ObservableList<Finding> findings;
    private final ObservableList<LogEntry> logs;
    private final Label proxyStatus = new Label("Proxy: off");
    private final Label interceptStatus = new Label("Intercept: off");
    private final Label requestCount = new Label("Requests: 0");
    private final TabPane tabs = new TabPane();
    private final DashboardTab dashboardTab;
    private final ProxyTab proxyTab;
    private final RepeaterTab repeaterTab;
    private final IntruderTab intruderTab;
    private final ScannerTab scannerTab;
    private final GlobalSearchTab globalSearchTab;
    private final SessionRecorder sessionRecorder;
    private ThemeManager themeManager;

    public MainWindow(Database database, ProxyServer proxyServer, PassiveScanner passiveScanner,
                      ActiveScanner activeScanner, CertManager certManager, PluginLoader pluginLoader,
                      ScopeControl scopeControl, MatchReplaceEngine matchReplaceEngine,
                      AuthenticationManager authenticationManager, SessionRecorder sessionRecorder, Path toolsDirectory) {
        this.database = database;
        this.proxyServer = proxyServer;
        this.certManager = certManager;
        this.scopeControl = scopeControl;
        this.sessionRecorder = sessionRecorder;
        this.history = FXCollections.observableArrayList(database.listTransactions());
        this.findings = FXCollections.observableArrayList(database.listFindings());
        this.logs = FXCollections.observableArrayList(database.listLogs());
        this.proxyServer.setListener(this);
        pluginLoader.load(database, scopeControl);

        this.dashboardTab = new DashboardTab(this, history, findings, logs, certManager);
        this.proxyTab = new ProxyTab(proxyServer);
        this.repeaterTab = new RepeaterTab();
        this.intruderTab = new IntruderTab();
        this.scannerTab = new ScannerTab(findings, activeScanner, database);
        this.globalSearchTab = new GlobalSearchTab(history, findings, logs);
        HistoryTab historyTab = new HistoryTab(database, history, tx -> repeaterTab.openTransaction(tx),
                tx -> intruderTab.loadTransaction(tx), tx -> scannerTab.scanTransaction(tx), scopeControl);

        tabs.getStyleClass().add("main-tabs");
        tabs.getTabs().addAll(
                dashboardTab,
                proxyTab,
                historyTab,
                globalSearchTab,
                new SiteMapTab(history),
                new OrganizerTab(database, history),
                repeaterTab,
                intruderTab,
                new SessionRecorderTab(database, sessionRecorder),
                new AuthManagerTab(authenticationManager),
                new MatchReplaceTab(matchReplaceEngine),
                new TurboIntruderTab(toolsDirectory),
                new SpiderCrawlerTab(database, history, scopeControl, toolsDirectory),
                scannerTab,
                new DecoderTab(),
                new ComparerTab(),
                new ReportTab(findings, history),
                new LoggerTab(logs),
                new PluginManagerTab(pluginLoader, database, scopeControl),
                new SettingsTab(this, scopeControl)
        );
        pluginLoader.plugins().forEach(plugin -> plugin.uiTab().ifPresent(node -> {
            javafx.scene.control.Tab tab = new javafx.scene.control.Tab(plugin.name(), node);
            tabs.getTabs().add(tab);
        }));
        tabs.getTabs().forEach(this::enableDetachableTab);

        setCenter(tabs);
        setBottom(statusBar());
        refreshStatus();
    }

    public void setThemeManager(ThemeManager themeManager) {
        this.themeManager = themeManager;
    }

    public List<String> themes() {
        return List.of("CyvoraX Navy/Teal", "Dark", "Light");
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
            tabs.getSelectionModel().select(tab);
        });
        stage.show();
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
