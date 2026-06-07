package com.venomproxy.ui;

import com.venomproxy.db.Database;
import com.venomproxy.model.Finding;
import com.venomproxy.model.HttpTransaction;
import com.venomproxy.scanner.ActiveScanner;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.BiConsumer;

public class ScannerTab extends Tab {
    private final ObservableList<Finding> findings;
    private final ActiveScanner activeScanner;
    private final Database database;
    private final TextField urlField = new TextField();
    private final TableView<Finding> table = new TableView<>();
    private final FilteredList<Finding> filteredFindings;
    private final Label status = new Label("Ready");
    private final ComboBox<String> severityFilter = new ComboBox<>();
    private final BiConsumer<String, String> scanNotification;

    public ScannerTab(ObservableList<Finding> findings, ActiveScanner activeScanner, Database database,
                      BiConsumer<String, String> scanNotification) {
        super("Scanner");
        this.findings = findings;
        this.activeScanner = activeScanner;
        this.database = database;
        this.scanNotification = scanNotification;
        this.filteredFindings = new FilteredList<>(findings);
        setClosable(false);

        urlField.setPromptText("https://target.example/path?param=value");
        Button scan = new Button("Active Scan");
        scan.setOnAction(event -> scanUrl(urlField.getText()));
        TextArea request = UiUtil.codeArea("Evidence request");
        TextArea response = UiUtil.codeArea("Evidence response");
        HttpInspectorPane inspector = new HttpInspectorPane();
        severityFilter.getItems().addAll("All Severities", "Critical", "High", "Medium", "Low", "Info");
        severityFilter.getSelectionModel().select("All Severities");
        severityFilter.valueProperty().addListener((obs, old, value) ->
                filteredFindings.setPredicate(finding -> matchesSeverity(finding, value)));

        table.setItems(filteredFindings);
        UiUtil.constrainTable(table);
        table.setPlaceholder(UiUtil.emptyState("No findings yet", "Passive findings appear from captured traffic. Active scan a scoped URL to test specific parameters.", null, null));
        table.getColumns().add(severityColumn());
        table.getColumns().add(column("Issue", "issue", 280));
        table.getColumns().add(column("URL", "url", 420));
        table.getColumns().add(column("Confidence", "confidence", 120));
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, finding) -> {
            if (finding != null) {
                request.setText(finding.getRequestRaw());
                response.setText(finding.getResponseRaw());
                inspector.inspect(finding.getRequestRaw(), finding.getResponseRaw(), finding.getEvidence());
            } else {
                request.clear();
                response.clear();
                inspector.inspect("", "", "");
            }
        });

        SplitPane requestResponse = new SplitPane(request, response);
        requestResponse.setDividerPositions(0.5);
        UiUtil.bindDividerPositions(database, "layout.scanner.requestResponse", requestResponse, 0.5);
        SplitPane evidence = new SplitPane(requestResponse, inspector);
        evidence.setDividerPositions(0.74);
        UiUtil.bindDividerPositions(database, "layout.scanner.inspector", evidence, 0.74);
        SplitPane rootSplit = new SplitPane(table, evidence);
        rootSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
        rootSplit.setDividerPositions(0.55);
        UiUtil.bindDividerPositions(database, "layout.scanner.main", rootSplit, 0.55);
        HBox controls = new HBox(8, urlField, scan, new Label("Severity"), severityFilter, status);
        controls.getStyleClass().add("filter-bar");
        VBox root = new VBox(8, controls, rootSplit);
        HBox.setHgrow(urlField, Priority.ALWAYS);
        VBox.setVgrow(rootSplit, Priority.ALWAYS);
        root.setPadding(new Insets(12));
        setContent(root);
        Platform.runLater(() -> {
            if (!filteredFindings.isEmpty()) {
                table.getSelectionModel().select(0);
            }
        });
    }

    public void scanTransaction(HttpTransaction tx) {
        urlField.setText(tx.getUrl());
        scanUrl(tx.getUrl());
    }

    public void selectFinding(long id) {
        for (Finding finding : findings) {
            if (finding.getId() == id) {
                table.getSelectionModel().select(finding);
                table.scrollTo(finding);
                return;
            }
        }
    }

    public String scannerUrl() {
        return urlField.getText() == null ? "" : urlField.getText();
    }

    public String selectedSeverityFilter() {
        return severityFilter.getSelectionModel().getSelectedItem();
    }

    public void restoreScannerState(String url, String severity) {
        urlField.setText(url == null ? "" : url);
        if (severity != null && severityFilter.getItems().contains(severity)) {
            severityFilter.getSelectionModel().select(severity);
        }
    }

    private void scanUrl(String url) {
        if (url == null || url.isBlank()) {
            status.setText("Enter a URL to scan.");
            return;
        }
        new Thread(() -> {
            Platform.runLater(() -> {
                urlField.setDisable(true);
                status.setText("Scanning " + url);
            });
            int added = 0;
            for (Finding finding : activeScanner.scanUrl(url)) {
                database.saveFinding(finding);
                Platform.runLater(() -> findings.add(0, finding));
                added++;
            }
            int count = added;
            Platform.runLater(() -> {
                urlField.setDisable(false);
                status.setText("Scan complete: " + count + " findings");
                scanNotification.accept("Scan complete", url + " produced " + count + " findings");
            });
        }, "active-scan").start();
    }

    private boolean matchesSeverity(Finding finding, String selected) {
        return selected == null || selected.equals("All Severities")
                || finding.getSeverity().toLowerCase().contains(selected.toLowerCase());
    }

    private TableColumn<Finding, Object> column(String title, String property, int width) {
        TableColumn<Finding, Object> column = new TableColumn<>(title);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        column.setPrefWidth(width);
        return column;
    }

    private TableColumn<Finding, Object> severityColumn() {
        TableColumn<Finding, Object> column = column("Severity", "severity", 110);
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label badge = new Label(String.valueOf(item));
                badge.getStyleClass().addAll("severity-badge", severityClass(String.valueOf(item)));
                setText(null);
                setGraphic(badge);
            }
        });
        return column;
    }

    private String severityClass(String severity) {
        String normalized = severity == null ? "" : severity.toLowerCase();
        if (normalized.contains("high") || normalized.contains("critical")) {
            return "severity-high";
        }
        if (normalized.contains("medium")) {
            return "severity-medium";
        }
        if (normalized.contains("low")) {
            return "severity-low";
        }
        return "severity-info";
    }
}
