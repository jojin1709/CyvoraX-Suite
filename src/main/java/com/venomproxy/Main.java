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
import com.venomproxy.ui.ThemeManager;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        Path appDir = Path.of(System.getProperty("user.home"), ".cyvorax-suite");
        Files.createDirectories(appDir);

        Database database = new Database(appDir.resolve("cyvorax-suite.db"));
        ScopeControl scopeControl = new ScopeControl();
        PassiveScanner passiveScanner = new PassiveScanner(scopeControl);
        ActiveScanner activeScanner = new ActiveScanner(scopeControl);
        CertManager certManager = new CertManager(appDir.resolve("certs"));
        certManager.ensureCa();
        PluginLoader pluginLoader = new PluginLoader(appDir.resolve("plugins"));
        MatchReplaceEngine matchReplaceEngine = new MatchReplaceEngine(database);
        AuthenticationManager authenticationManager = new AuthenticationManager(database);
        SessionRecorder sessionRecorder = new SessionRecorder(database);
        Path toolsDirectory = Path.of(System.getProperty("user.dir"), "tools");
        Files.createDirectories(toolsDirectory.resolve("ffuf"));
        Files.createDirectories(toolsDirectory.resolve("katana"));
        ProxyServer proxyServer = new ProxyServer(database, passiveScanner, scopeControl, certManager, pluginLoader,
                matchReplaceEngine, authenticationManager);

        MainWindow mainWindow = new MainWindow(database, proxyServer, passiveScanner, activeScanner, certManager, pluginLoader,
                scopeControl, matchReplaceEngine, authenticationManager, sessionRecorder, toolsDirectory);
        Scene scene = new Scene(mainWindow, 1320, 860);
        ThemeManager themeManager = new ThemeManager(database);
        mainWindow.setThemeManager(themeManager);
        themeManager.apply(scene, themeManager.currentTheme());
        mainWindow.installShortcuts(scene);

        stage.setTitle("CyvoraX Suite");
        addAppIcons(stage);
        stage.setScene(scene);
        stage.setMinWidth(1100);
        stage.setMinHeight(720);
        stage.show();

        stage.setOnCloseRequest(event -> {
            sessionRecorder.stop();
            proxyServer.stop();
            database.close();
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
}
