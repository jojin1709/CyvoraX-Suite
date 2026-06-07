package com.venomproxy;

import com.venomproxy.auth.AuthenticationManager;
import com.venomproxy.backup.BackupManager;
import com.venomproxy.db.Database;
import com.venomproxy.diagnostics.CrashReporter;
import com.venomproxy.plugins.PluginLoader;
import com.venomproxy.proxy.CertManager;
import com.venomproxy.proxy.MatchReplaceEngine;
import com.venomproxy.proxy.ProxyServer;
import com.venomproxy.proxy.ScopeControl;
import com.venomproxy.recovery.SessionRecoveryManager;
import com.venomproxy.recovery.SessionSnapshot;
import com.venomproxy.scanner.ActiveScanner;
import com.venomproxy.scanner.PassiveScanner;
import com.venomproxy.session.SessionRecorder;
import com.venomproxy.ui.MainWindow;
import com.venomproxy.ui.StartupSplash;
import com.venomproxy.ui.ThemeManager;
import com.venomproxy.ui.WorkspaceLauncher;
import com.venomproxy.update.GitHubReleaseClient;
import com.venomproxy.update.UpdateService;
import com.venomproxy.workspace.WorkspaceInfo;
import com.venomproxy.workspace.WorkspaceManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.concurrent.Task;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class Main extends Application {
    private CrashReporter crashReporter;
    private volatile WorkspaceInfo activeWorkspace;
    private volatile PluginLoader activePluginLoader;

    @Override
    public void start(Stage stage) throws Exception {
        String version = appVersion();
        Path appDir = appDirectory();
        Files.createDirectories(appDir);
        crashReporter = new CrashReporter(appDir.resolve("crash-reports"), version,
                () -> activeWorkspace == null ? "No workspace selected" : activeWorkspace.getName(),
                this::loadedPluginDiagnostics);
        crashReporter.installGlobalHandler();
        StartupSplash splash = new StartupSplash(version);
        Scene splashScene = new Scene(splash, 720, 420);
        addStartupStylesheet(splashScene);
        stage.setTitle("CyvoraX Suite");
        addAppIcons(stage);
        stage.setScene(splashScene);
        stage.setMinWidth(720);
        stage.setMinHeight(420);
        stage.show();

        Task<CommonServices> startupTask = new Task<>() {
            @Override
            protected CommonServices call() throws Exception {
                updateMessage("Loading database");
                updateProgress(0.15, 1.0);
                Files.createDirectories(appDir);
                WorkspaceManager workspaceManager = new WorkspaceManager(appDir);

                updateMessage("Loading certificates");
                updateProgress(0.38, 1.0);
                CertManager certManager = new CertManager(appDir.resolve("certs"));
                certManager.ensureCa();

                updateMessage("Loading plugins");
                updateProgress(0.62, 1.0);
                Files.createDirectories(appDir.resolve("plugins"));

                updateMessage("Loading tools");
                updateProgress(0.82, 1.0);
                Path toolsDirectory = Path.of(System.getProperty("user.dir"), "tools");
                Files.createDirectories(toolsDirectory.resolve("ffuf"));
                Files.createDirectories(toolsDirectory.resolve("katana"));

                updateMessage("Ready");
                updateProgress(1.0, 1.0);

                BackupManager backupManager = new BackupManager(appDir);

                return new CommonServices(appDir, workspaceManager, certManager, toolsDirectory, backupManager);
            }
        };
        splash.bind(startupTask);
        startupTask.setOnSucceeded(event -> showWorkspaceLauncher(stage, startupTask.getValue(), version));
        startupTask.setOnFailed(event -> {
            Throwable throwable = startupTask.getException();
            crashReporter.record(throwable, "Startup failure");
            writeCrashLog(throwable);
            throwable.printStackTrace(System.err);
            splash.showFailure(throwable.getMessage());
        });
        Thread startupThread = new Thread(startupTask, "cyvorax-startup");
        startupThread.setDaemon(true);
        startupThread.start();
    }

    private void showWorkspaceLauncher(Stage stage, CommonServices services, String version) {
        WorkspaceLauncher launcher = new WorkspaceLauncher(version, services.workspaceManager(),
                selection -> openMainApplication(stage, services, selection.workspace()));
        Scene launcherScene = new Scene(launcher, 920, 620);
        addStartupStylesheet(launcherScene);
        stage.setOnCloseRequest(null);
        stage.setMinWidth(820);
        stage.setMinHeight(560);
        stage.setScene(launcherScene);
        stage.centerOnScreen();
    }

    private void openMainApplication(Stage stage, CommonServices commonServices, WorkspaceInfo workspace) {
        activeWorkspace = workspace;
        AppServices services;
        try {
            services = createAppServices(commonServices, workspace);
        } catch (Exception ex) {
            crashReporter.record(ex, "Application service creation failure");
            writeCrashLog(ex);
            ex.printStackTrace(System.err);
            return;
        }
        boolean recoveryAvailable = services.sessionRecoveryManager().shouldPromptRecovery();
        Optional<SessionSnapshot> recoverySnapshot = services.sessionRecoveryManager().loadSnapshot();
        services.sessionRecoveryManager().markStarted();
        final MainWindow[] windowRef = new MainWindow[1];
        MainWindow mainWindow = new MainWindow(services.database(), services.proxyServer(), services.passiveScanner(),
                services.activeScanner(), services.certManager(), services.pluginLoader(), services.scopeControl(),
                services.matchReplaceEngine(), services.authenticationManager(), services.sessionRecorder(),
                services.toolsDirectory(), workspace, crashReporter, services.sessionRecoveryManager(),
                services.updateService(), appVersion(), commonServices.workspaceManager(), commonServices.backupManager(),
                nextWorkspace -> switchWorkspace(stage, commonServices, services, windowRef[0], nextWorkspace));
        windowRef[0] = mainWindow;
        Scene scene = new Scene(mainWindow, 1320, 860);
        ThemeManager themeManager = new ThemeManager(services.database());
        mainWindow.setThemeManager(themeManager);
        themeManager.apply(scene, themeManager.currentTheme());
        mainWindow.installShortcuts(scene);

        stage.setTitle("CyvoraX Suite - " + workspace.getName());
        stage.setScene(scene);
        stage.setMinWidth(1100);
        stage.setMinHeight(720);
        stage.show();
        mainWindow.saveSnapshot(stage);
        mainWindow.startAutoSnapshots(stage);
        Platform.runLater(() -> {
            if (recoveryAvailable) {
                recoverySnapshot.ifPresent(snapshot -> mainWindow.showSessionRecoveryPrompt(snapshot, stage));
            }
            crashReporter.latestReport().ifPresent(mainWindow::showCrashRecoveryIfNeeded);
            mainWindow.runScheduledBackupIfNeeded();
            mainWindow.runStartupUpdateCheck();
        });

        stage.setOnCloseRequest(event -> {
            mainWindow.stopAutoSnapshots();
            mainWindow.saveSnapshot(stage);
            services.sessionRecoveryManager().markCleanShutdown();
            shutdown(services);
        });
    }

    private void switchWorkspace(Stage stage, CommonServices commonServices, AppServices services,
                                 MainWindow mainWindow, WorkspaceInfo nextWorkspace) {
        if (nextWorkspace == null || services.workspace().getId().equals(nextWorkspace.getId())) {
            return;
        }
        if (mainWindow != null) {
            mainWindow.stopAutoSnapshots();
            mainWindow.saveSnapshot(stage);
        }
        services.sessionRecoveryManager().markCleanShutdown();
        shutdown(services);
        openMainApplication(stage, commonServices, nextWorkspace);
    }

    private AppServices createAppServices(CommonServices commonServices, WorkspaceInfo workspace) throws Exception {
        Files.createDirectories(workspace.getPath());
        Database database = new Database(workspace.databasePath());
        ScopeControl scopeControl = new ScopeControl();
        loadScopeSettings(database, scopeControl);
        PluginLoader pluginLoader = new PluginLoader(commonServices.appDir().resolve("plugins"));
        PassiveScanner passiveScanner = new PassiveScanner(scopeControl);
        ActiveScanner activeScanner = new ActiveScanner(scopeControl);
        MatchReplaceEngine matchReplaceEngine = new MatchReplaceEngine(database);
        AuthenticationManager authenticationManager = new AuthenticationManager(database);
        SessionRecorder sessionRecorder = new SessionRecorder(database);
        SessionRecoveryManager sessionRecoveryManager = new SessionRecoveryManager(workspace);
        UpdateService updateService = new UpdateService(appVersion(),
                new GitHubReleaseClient("jojin1709", "CyvoraX-Suite"),
                commonServices.appDir().resolve("updates"));
        ProxyServer proxyServer = new ProxyServer(database, passiveScanner, scopeControl, commonServices.certManager(),
                pluginLoader, matchReplaceEngine, authenticationManager);
        activePluginLoader = pluginLoader;
        return new AppServices(workspace, database, scopeControl, passiveScanner, activeScanner, commonServices.certManager(),
                pluginLoader, matchReplaceEngine, authenticationManager, sessionRecorder, proxyServer,
                commonServices.toolsDirectory(), sessionRecoveryManager, updateService);
    }

    private void loadScopeSettings(Database database, ScopeControl scopeControl) {
        scopeControl.setIncludesFromText(database.getSetting("scope.includes", ""));
        scopeControl.setExcludesFromText(database.getSetting("scope.excludes", ""));
        scopeControl.setIgnoresFromText(database.getSetting("scope.ignores", ""));
        scopeControl.setOutOfScopePassthrough(Boolean.parseBoolean(database.getSetting("scope.outOfScopePassthrough", "true")));
    }

    public static void main(String[] args) {
        try {
            launch(args);
        } catch (Throwable throwable) {
            try {
                CrashReporter.recordStandalone(Path.of(System.getProperty("user.home"), ".cyvorax-suite", "crash-reports"),
                        "unknown", "Fatal launcher failure", throwable, "unknown", List.of());
            } catch (RuntimeException ignored) {
            }
            writeCrashLog(throwable);
            throwable.printStackTrace(System.err);
            throw throwable;
        }
    }

    private static void writeCrashLog(Throwable throwable) {
        Path crashLog = Path.of(System.getProperty("user.home"), "CyvoraX", "crash.log");
        try {
            Files.createDirectories(crashLog.getParent());
            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(crashLog))) {
                throwable.printStackTrace(writer);
            }
        } catch (IOException ioException) {
            ioException.printStackTrace(System.err);
        }
    }

    private void addAppIcons(Stage stage) {
        String[] icons = {
                "/icons/cyvorax-16.png",
                "/icons/cyvorax-32.png",
                "/icons/cyvorax-48.png",
                "/icons/cyvorax-64.png",
                "/icons/cyvorax-128.png",
                "/icons/cyvorax-256.png",
                "/icons/cyvorax-logo.png"
        };
        for (String icon : icons) {
            try (InputStream stream = getClass().getResourceAsStream(icon)) {
                if (stream != null) {
                    stage.getIcons().add(new Image(stream));
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void addStartupStylesheet(Scene scene) {
        scene.getStylesheets().add(getClass().getResource("/styles/navy-teal-theme.css").toExternalForm());
    }

    private String appVersion() {
        String version = getClass().getPackage().getImplementationVersion();
        return version == null || version.isBlank() ? "1.3.0" : version;
    }

    private Path appDirectory() {
        return Path.of(System.getProperty("user.home"), ".cyvorax-suite");
    }

    private List<String> loadedPluginDiagnostics() {
        PluginLoader loader = activePluginLoader;
        if (loader == null) {
            return List.of();
        }
        return loader.statuses().stream()
                .map(status -> status.getName() + " [" + status.getState() + "]"
                        + (status.getError() == null || status.getError().isBlank() ? "" : " - " + status.getError()))
                .toList();
    }

    private void shutdown(AppServices services) {
        services.sessionRecorder().stop();
        services.proxyServer().stop();
        services.database().close();
    }

    private record CommonServices(Path appDir, WorkspaceManager workspaceManager, CertManager certManager,
                                  Path toolsDirectory, BackupManager backupManager) {
    }

    private record AppServices(WorkspaceInfo workspace, Database database, ScopeControl scopeControl,
                               PassiveScanner passiveScanner, ActiveScanner activeScanner,
                               CertManager certManager, PluginLoader pluginLoader,
                               MatchReplaceEngine matchReplaceEngine,
                               AuthenticationManager authenticationManager,
                               SessionRecorder sessionRecorder, ProxyServer proxyServer,
                               Path toolsDirectory, SessionRecoveryManager sessionRecoveryManager,
                               UpdateService updateService) {
    }
}
