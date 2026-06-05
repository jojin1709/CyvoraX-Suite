package com.venomproxy.ui;

import com.venomproxy.model.Finding;
import com.venomproxy.model.HttpTransaction;
import com.venomproxy.model.LogEntry;
import com.venomproxy.proxy.CertManager;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

public class DashboardTab extends Tab {
    private final ObservableList<HttpTransaction> history;
    private final ObservableList<Finding> findings;
    private final ObservableList<LogEntry> logs;
    private final CertManager certManager;
    private final Label proxyLabel = new Label();
    private final Label interceptLabel = new Label();
    private final Label requestsLabel = new Label();
    private final Label hostsLabel = new Label();
    private final Label findingsLabel = new Label();
    private final Label uptimeLabel = new Label("Uptime: 0s");
    private Instant started = Instant.now();

    public DashboardTab(MainWindow mainWindow, ObservableList<HttpTransaction> history,
                        ObservableList<Finding> findings, ObservableList<LogEntry> logs,
                        CertManager certManager) {
        super("Dashboard");
        this.history = history;
        this.findings = findings;
        this.logs = logs;
        this.certManager = certManager;
        setClosable(false);

        Button start = new Button("Start Proxy");
        start.setOnAction(event -> mainWindow.startProxy("127.0.0.1", 8080));
        Button stop = new Button("Stop Proxy");
        stop.setOnAction(event -> mainWindow.stopProxy());
        Button cert = new Button("Download CA Cert");
        cert.setOnAction(event -> exportCert());

        GridPane stats = new GridPane();
        stats.getStyleClass().add("stats-grid");
        stats.setHgap(18);
        stats.setVgap(12);
        stats.add(proxyLabel, 0, 0);
        stats.add(interceptLabel, 1, 0);
        stats.add(requestsLabel, 2, 0);
        stats.add(hostsLabel, 0, 1);
        stats.add(findingsLabel, 1, 1);
        stats.add(uptimeLabel, 2, 1);

        VBox recent = new VBox(8, new Label("Recent activity"), new Label("Traffic and findings will appear as the proxy runs."));
        recent.getStyleClass().add("panel");
        VBox.setVgrow(recent, Priority.ALWAYS);

        VBox root = new VBox(16, new HBox(10, start, stop, cert), stats, recent);
        root.setPadding(new Insets(16));
        setContent(root);
    }

    public void refresh(boolean proxyRunning, boolean interceptEnabled, long requestCount) {
        proxyLabel.setText("Proxy: " + (proxyRunning ? "on" : "off"));
        interceptLabel.setText("Intercept: " + (interceptEnabled ? "on" : "off"));
        requestsLabel.setText("Requests: " + requestCount);
        hostsLabel.setText("Hosts: " + history.stream().map(HttpTransaction::getHost).distinct().count());
        findingsLabel.setText("Findings: " + findings.size());
        uptimeLabel.setText("Uptime: " + Duration.between(started, Instant.now()).toSeconds() + "s");
    }

    private void exportCert() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export CyvoraX Suite CA Certificate");
        chooser.setInitialFileName("cyvorax-suite-ca-cert.pem");
        java.io.File destination = chooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (destination != null) {
            try {
                certManager.exportCertificate(Path.of(destination.toURI()));
            } catch (Exception ignored) {
            }
        }
    }
}
