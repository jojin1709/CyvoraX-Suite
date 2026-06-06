package com.venomproxy.ui;

import com.venomproxy.db.Database;
import com.venomproxy.model.Finding;
import com.venomproxy.model.HttpTransaction;
import com.venomproxy.scanner.ActiveScanner;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
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

public class ScannerTab extends Tab {
    private final ObservableList<Finding> findings;
    private final ActiveScanner activeScanner;
    private final Database database;
    private final TextField urlField = new TextField();

    public ScannerTab(ObservableList<Finding> findings, ActiveScanner activeScanner, Database database) {
        super("Scanner");
        this.findings = findings;
        this.activeScanner = activeScanner;
        this.database = database;
        setClosable(false);

        urlField.setPromptText("https://target.example/path?param=value");
        Button scan = new Button("Active Scan");
        scan.setOnAction(event -> scanUrl(urlField.getText()));
        TextArea request = UiUtil.codeArea("Evidence request");
        TextArea response = UiUtil.codeArea("Evidence response");
        Label status = new Label("Ready");

        TableView<Finding> table = new TableView<>(findings);
        table.getColumns().add(severityColumn());
        table.getColumns().add(column("Issue", "issue", 280));
        table.getColumns().add(column("URL", "url", 420));
        table.getColumns().add(column("Confidence", "confidence", 120));
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, finding) -> {
            if (finding != null) {
                request.setText(finding.getRequestRaw());
                response.setText(finding.getResponseRaw());
            }
        });

        SplitPane evidence = new SplitPane(request, response);
        evidence.setDividerPositions(0.5);
        SplitPane rootSplit = new SplitPane(table, evidence);
        rootSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
        rootSplit.setDividerPositions(0.55);
        HBox controls = new HBox(8, urlField, scan, status);
        controls.getStyleClass().add("filter-bar");
        VBox root = new VBox(8, controls, rootSplit);
        HBox.setHgrow(urlField, Priority.ALWAYS);
        VBox.setVgrow(rootSplit, Priority.ALWAYS);
        root.setPadding(new Insets(12));
        setContent(root);
    }

    public void scanTransaction(HttpTransaction tx) {
        urlField.setText(tx.getUrl());
        scanUrl(tx.getUrl());
    }

    private void scanUrl(String url) {
        new Thread(() -> {
            for (Finding finding : activeScanner.scanUrl(url)) {
                database.saveFinding(finding);
                Platform.runLater(() -> findings.add(0, finding));
            }
        }, "active-scan").start();
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
