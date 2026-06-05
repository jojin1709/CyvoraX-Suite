package com.venomproxy.ui;

import com.venomproxy.model.HttpTransaction;
import com.venomproxy.proxy.ScopeControl;
import com.venomproxy.util.Exporters;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.nio.file.Path;
import java.util.function.Consumer;

public class HistoryTab extends Tab {
    private final FilteredList<HttpTransaction> filtered;
    private final TextArea requestViewer = UiUtil.codeArea("Request");
    private final TextArea responseViewer = UiUtil.codeArea("Response");

    public HistoryTab(ObservableList<HttpTransaction> history, Consumer<HttpTransaction> sendRepeater,
                      Consumer<HttpTransaction> sendIntruder, Consumer<HttpTransaction> sendScanner,
                      ScopeControl scopeControl) {
        super("HTTP History");
        setClosable(false);
        filtered = new FilteredList<>(history);

        TextField hostFilter = new TextField();
        hostFilter.setPromptText("Host");
        TextField methodFilter = new TextField();
        methodFilter.setPromptText("Method");
        TextField statusFilter = new TextField();
        statusFilter.setPromptText("Status");
        TextField keywordFilter = new TextField();
        keywordFilter.setPromptText("Keyword");
        CheckBox scopeOnly = new CheckBox("Scope only");
        Button exportCsv = new Button("CSV");
        Button exportJson = new Button("JSON");

        TableView<HttpTransaction> table = new TableView<>(filtered);
        table.getColumns().add(column("#", "id", 70));
        table.getColumns().add(column("Method", "method", 90));
        table.getColumns().add(column("Host", "host", 220));
        table.getColumns().add(column("Path", "path", 360));
        table.getColumns().add(column("Status", "status", 80));
        table.getColumns().add(column("Length", "length", 90));
        table.getColumns().add(column("MIME", "mimeType", 160));
        table.getColumns().add(column("Protocol", "protocol", 100));
        table.getColumns().add(column("Time", "timeMs", 90));

        Runnable apply = () -> filtered.setPredicate(tx ->
                contains(tx.getHost(), hostFilter.getText())
                        && contains(tx.getMethod(), methodFilter.getText())
                        && contains(String.valueOf(tx.getStatus()), statusFilter.getText())
                        && (!scopeOnly.isSelected() || scopeControl.isInScope(tx.getUrl()))
                        && (keywordFilter.getText().isBlank()
                        || contains(tx.getRequestRaw(), keywordFilter.getText())
                        || contains(tx.getResponseRaw(), keywordFilter.getText())
                        || contains(tx.getPath(), keywordFilter.getText())));
        hostFilter.textProperty().addListener((obs, old, val) -> apply.run());
        methodFilter.textProperty().addListener((obs, old, val) -> apply.run());
        statusFilter.textProperty().addListener((obs, old, val) -> apply.run());
        keywordFilter.textProperty().addListener((obs, old, val) -> apply.run());
        scopeOnly.selectedProperty().addListener((obs, old, val) -> apply.run());

        table.getSelectionModel().selectedItemProperty().addListener((obs, old, tx) -> {
            if (tx != null) {
                requestViewer.setText(tx.getRequestRaw());
                responseViewer.setText(tx.getResponseRaw());
            }
        });

        MenuItem repeater = new MenuItem("Send to Repeater");
        repeater.setOnAction(event -> selected(table, sendRepeater));
        MenuItem intruder = new MenuItem("Send to Intruder");
        intruder.setOnAction(event -> selected(table, sendIntruder));
        MenuItem scanner = new MenuItem("Send to Scanner");
        scanner.setOnAction(event -> selected(table, sendScanner));
        MenuItem copyUrl = new MenuItem("Copy URL");
        copyUrl.setOnAction(event -> {
            HttpTransaction tx = table.getSelectionModel().getSelectedItem();
            if (tx != null) {
                javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                content.putString(tx.getUrl());
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
            }
        });
        MenuItem save = new MenuItem("Save");
        save.setOnAction(event -> {
            HttpTransaction tx = table.getSelectionModel().getSelectedItem();
            if (tx != null) {
                saveTransaction(tx);
            }
        });
        table.setContextMenu(new ContextMenu(repeater, intruder, scanner, copyUrl, save));

        exportCsv.setOnAction(event -> export(history, true));
        exportJson.setOnAction(event -> export(history, false));

        SplitPane viewers = new SplitPane(requestViewer, responseViewer);
        viewers.setDividerPositions(0.5);
        SplitPane rootSplit = new SplitPane(table, viewers);
        rootSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
        rootSplit.setDividerPositions(0.55);

        VBox root = new VBox(10,
                new HBox(8, new Label("Filter"), hostFilter, methodFilter, statusFilter, keywordFilter, scopeOnly, exportCsv, exportJson),
                rootSplit);
        VBox.setVgrow(rootSplit, Priority.ALWAYS);
        root.setPadding(new Insets(12));
        setContent(root);
    }

    private TableColumn<HttpTransaction, Object> column(String title, String property, int width) {
        TableColumn<HttpTransaction, Object> column = new TableColumn<>(title);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        column.setPrefWidth(width);
        return column;
    }

    private boolean contains(String haystack, String needle) {
        return needle == null || needle.isBlank()
                || (haystack != null && haystack.toLowerCase().contains(needle.toLowerCase()));
    }

    private void selected(TableView<HttpTransaction> table, Consumer<HttpTransaction> consumer) {
        HttpTransaction tx = table.getSelectionModel().getSelectedItem();
        if (tx != null) {
            consumer.accept(tx);
        }
    }

    private void export(ObservableList<HttpTransaction> history, boolean csv) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export History");
        chooser.setInitialFileName(csv ? "history.csv" : "history.json");
        java.io.File file = chooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            try {
                if (csv) {
                    Exporters.historyCsv(history, Path.of(file.toURI()));
                } else {
                    Exporters.historyJson(history, Path.of(file.toURI()));
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void saveTransaction(HttpTransaction tx) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Transaction");
        chooser.setInitialFileName("transaction-" + tx.getId() + ".txt");
        java.io.File file = chooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            try {
                java.nio.file.Files.writeString(Path.of(file.toURI()),
                        "==== REQUEST ====\n" + tx.getRequestRaw() + "\n\n==== RESPONSE ====\n" + tx.getResponseRaw());
            } catch (Exception ignored) {
            }
        }
    }
}
