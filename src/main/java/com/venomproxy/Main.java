package com.venomproxy;

import com.venomproxy.auth.AuthenticationManager;
import com.venomproxy.db.Database;
import com.venomproxy.plugins.PluginLoader;
import com.venomproxy.proxy.CertManager;
import com.venomproxy.proxy.MatchReplaceEngine;
import com.venomproxy.proxy.ProxyServer;
import com.venomproxy.proxy.ScopeControl;
import com.venomproxy.scanner.ActiveScanner;
import com.venomproxy.scanner.PassiveScanner;
import com.venomproxy.session.SessionRecorder;
import com.venomproxy.ui.MainWindow;
import com.venomproxy.ui.StartupSplash;
import com.venomproxy.ui.ThemeManager;
import com.venomproxy.ui.WorkspaceLauncher;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.concurrent.Task;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        String version = appVersion();
        StartupSplash splash = new StartupSplash(version);
        Scene splashScene = new Scene(splash, 720, 420);
        addStartupStylesheet(splashScene);
        stage.setTitle("CyvoraX Suite");
        addAppIcons(stage);
        stage.setScene(splashScene);
        stage.setMinWidth(720);
        stage.setMinHeight(420);
        stage.show();

        Task<AppServices> startupTask = new Task<>() {
            @Override
            protected AppServices call() throws Exception {
                updateMessage("Loading database");
                updateProgress(0.15, 1.0);
                Path appDir = Path.of(System.getProperty("user.home"), ".cyvorax-suite");
                Files.createDirectories(appDir);
                Database database = new Database(appDir.resolve("cyvorax-suite.db"));

                updateMessage("Loading certificates");
                updateProgress(0.38, 1.0);
                CertManager certManager = new CertManager(appDir.resolve("certs"));
                certManager.ensureCa();

                updateMessage("Loading plugins");
                updateProgress(0.62, 1.0);
                ScopeControl scopeControl = new ScopeControl();
                PluginLoader pluginLoader = new PluginLoader(appDir.resolve("plugins"));

                updateMessage("Loading tools");
                updateProgress(0.82, 1.0);
                Path toolsDirectory = Path.of(System.getProperty("user.dir"), "tools");
                Files.createDirectories(toolsDirectory.resolve("ffuf"));
                Files.createDirectories(toolsDirectory.resolve("katana"));

                PassiveScanner passiveScanner = new PassiveScanner(scopeControl);
                ActiveScanner activeScanner = new ActiveScanner(scopeControl);
                MatchReplaceEngine matchReplaceEngine = new MatchReplaceEngine(database);
                AuthenticationManager authenticationManager = new AuthenticationManager(database);
                SessionRecorder sessionRecorder = new SessionRecorder(database);
                ProxyServer proxyServer = new ProxyServer(database, passiveScanner, scopeControl, certManager, pluginLoader,
                        matchReplaceEngine, authenticationManager);
                updateMessage("Ready");
                updateProgress(1.0, 1.0);

                return new AppServices(appDir, database, scopeControl, passiveScanner, activeScanner, certManager,
                        pluginLoader, matchReplaceEngine, authenticationManager, sessionRecorder, proxyServer, toolsDirectory);
            }
        };
        splash.bind(startupTask);
        startupTask.setOnSucceeded(event -> showWorkspaceLauncher(stage, startupTask.getValue(), version));
        startupTask.setOnFailed(event -> {
            Throwable throwable = startupTask.getException();
            writeCrashLog(throwable);
            throwable.printStackTrace(System.err);
            splash.showFailure(throwable.getMessage());
        });
        Thread startupThread = new Thread(startupTask, "cyvorax-startup");
        startupThread.setDaemon(true);
        startupThread.start();
    }

    private void showWorkspaceLauncher(Stage stage, AppServices services, String version) {
        WorkspaceLauncher launcher = new WorkspaceLauncher(version, services.appDir(), selection -> openMainApplication(stage, services));
        Scene launcherScene = new Scene(launcher, 920, 620);
        addStartupStylesheet(launcherScene);
        stage.setOnCloseRequest(event -> shutdown(services));
        stage.setMinWidth(820);
        stage.setMinHeight(560);
        stage.setScene(launcherScene);
        stage.centerOnScreen();
    }

    private void openMainApplication(Stage stage, AppServices services) {
        MainWindow mainWindow = new MainWindow(services.database(), services.proxyServer(), services.passiveScanner(),
                services.activeScanner(), services.certManager(), services.pluginLoader(), services.scopeControl(),
                services.matchReplaceEngine(), services.authenticationManager(), services.sessionRecorder(),
                services.toolsDirectory());
        Scene scene = new Scene(mainWindow, 1320, 860);
        ThemeManager themeManager = new ThemeManager(services.database());
        mainWindow.setThemeManager(themeManager);
        themeManager.apply(scene, themeManager.currentTheme());
        mainWindow.installShortcuts(scene);

        stage.setTitle("CyvoraX Suite");
        stage.setScene(scene);
        stage.setMinWidth(1100);
        stage.setMinHeight(720);
        stage.show();

        stage.setOnCloseRequest(event -> {
            shutdown(services);
        });
    }

    public static void main(String[] args) {
        try {
            launch(args);
        } catch (Throwable throwable) {
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
        return version == null || version.isBlank() ? "1.0.1" : version;
    }

    private void shutdown(AppServices services) {
        services.sessionRecorder().stop();
        services.proxyServer().stop();
        services.database().close();
    }

    private record AppServices(Path appDir, Database database, ScopeControl scopeControl,
                               PassiveScanner passiveScanner, ActiveScanner activeScanner,
                               CertManager certManager, PluginLoader pluginLoader,
                               MatchReplaceEngine matchReplaceEngine,
                               AuthenticationManager authenticationManager,
                               SessionRecorder sessionRecorder, ProxyServer proxyServer,
                               Path toolsDirectory) {
    }
}
