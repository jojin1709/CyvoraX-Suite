package com.venomproxy.ui;

import com.venomproxy.db.Database;
import com.venomproxy.model.Finding;
import com.venomproxy.model.HttpTransaction;
import com.venomproxy.model.LogEntry;
import com.venomproxy.plugins.PluginLoader;
import com.venomproxy.proxy.CertManager;
import com.venomproxy.proxy.InterceptedRequest;
import com.venomproxy.proxy.ProxyEventListener;
import com.venomproxy.proxy.ProxyServer;
import com.venomproxy.proxy.ScopeControl;
import com.venomproxy.scanner.ActiveScanner;
import com.venomproxy.scanner.PassiveScanner;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import java.nio.file.Path;

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
    private final DashboardTab dashboardTab;
    private final ProxyTab proxyTab;
    private final RepeaterTab repeaterTab;
    private final IntruderTab intruderTab;
    private final ScannerTab scannerTab;

    public MainWindow(Database database, ProxyServer proxyServer, PassiveScanner passiveScanner,
                      ActiveScanner activeScanner, CertManager certManager, PluginLoader pluginLoader,
                      ScopeControl scopeControl, Path toolsDirectory) {
        this.database = database;
        this.proxyServer = proxyServer;
        this.certManager = certManager;
        this.scopeControl = scopeControl;
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
        HistoryTab historyTab = new HistoryTab(history, tx -> repeaterTab.openTransaction(tx),
                tx -> intruderTab.loadTransaction(tx), tx -> scannerTab.scanTransaction(tx), scopeControl);

        TabPane tabs = new TabPane();
        tabs.getStyleClass().add("main-tabs");
        tabs.getTabs().addAll(
                dashboardTab,
                proxyTab,
                historyTab,
                repeaterTab,
                intruderTab,
                new TurboIntruderTab(toolsDirectory),
                new SpiderCrawlerTab(database, history, scopeControl, toolsDirectory),
                scannerTab,
                new DecoderTab(),
                new ComparerTab(),
                new LoggerTab(logs),
                new PluginManagerTab(pluginLoader, database, scopeControl),
                new SettingsTab(this, scopeControl)
        );
        pluginLoader.plugins().forEach(plugin -> plugin.uiTab().ifPresent(node -> {
            javafx.scene.control.Tab tab = new javafx.scene.control.Tab(plugin.name(), node);
            tabs.getTabs().add(tab);
        }));

        setCenter(tabs);
        setBottom(statusBar());
        refreshStatus();
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
