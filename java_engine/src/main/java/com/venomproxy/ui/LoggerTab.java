package com.venomproxy.ui;

import com.venomproxy.model.LogEntry;
import com.venomproxy.util.Exporters;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.nio.file.Path;

public class LoggerTab extends Tab {
    private final Label status = new Label("Ready");

    public LoggerTab(ObservableList<LogEntry> logs) {
        super("Logger");
        setClosable(false);
        FilteredList<LogEntry> filtered = new FilteredList<>(logs);
        TextField filter = new TextField();
        filter.setPromptText("Search log");
        filter.textProperty().addListener((obs, old, value) -> filtered.setPredicate(entry ->
                value == null || value.isBlank()
                        || entry.getHost().toLowerCase().contains(value.toLowerCase())
                        || entry.getMessage().toLowerCase().contains(value.toLowerCase())
                        || entry.getDirection().toLowerCase().contains(value.toLowerCase())));
        Button export = new Button("Export TXT");
        export.setOnAction(event -> export(logs, true));
        Button exportJson = new Button("Export JSON");
        exportJson.setOnAction(event -> export(logs, false));

        TableView<LogEntry> table = new TableView<>(filtered);
        UiUtil.constrainTable(table);
        table.setPlaceholder(UiUtil.emptyState("No log entries", "Proxy, scanner, plugin, and system events will appear here as modules run.", null, null));
        table.getColumns().add(column("Time", "timestamp", 220));
        table.getColumns().add(column("Direction", "direction", 100));
        table.getColumns().add(column("Host", "host", 240));
        table.getColumns().add(column("Message", "message", 620));

        VBox root = new VBox(8, new HBox(8, filter, export, exportJson, status), table);
        HBox.setHgrow(filter, Priority.ALWAYS);
        VBox.setVgrow(table, Priority.ALWAYS);
        root.setPadding(new Insets(12));
        setContent(root);
    }

    private void export(ObservableList<LogEntry> logs, boolean txt) {
            FileChooser chooser = new FileChooser();
            chooser.setInitialFileName(txt ? "cyvorax-suite-log.txt" : "cyvorax-suite-log.json");
            java.io.File file = chooser.showSaveDialog(getTabPane().getScene().getWindow());
            if (file != null) {
                try {
                    if (txt) {
                        Exporters.logsTxt(logs, Path.of(file.toURI()));
                    } else {
                        Exporters.logsJson(logs, Path.of(file.toURI()));
                    }
                    status.setText("Exported " + logs.size() + " log entries");
                } catch (Exception ex) {
                    status.setText("Export failed: " + ex.getMessage());
                }
            }
    }

    private TableColumn<LogEntry, Object> column(String title, String property, int width) {
        TableColumn<LogEntry, Object> column = new TableColumn<>(title);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        column.setPrefWidth(width);
        return column;
    }
}
