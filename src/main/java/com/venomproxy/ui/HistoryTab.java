package com.venomproxy.ui;

import com.venomproxy.db.Database;
import com.venomproxy.model.HttpTransaction;
import com.venomproxy.model.RequestData;
import com.venomproxy.proxy.ScopeControl;
import com.venomproxy.util.Exporters;
import com.venomproxy.util.RequestCopyUtil;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.FileChooser;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public class HistoryTab extends Tab {
    private final Database database;
    private final ObservableList<HttpTransaction> history;
    private final FilteredList<HttpTransaction> filtered;
    private final TableView<HttpTransaction> table;
    private final TextArea requestViewer = UiUtil.codeArea("Request");
    private final TextArea responseViewer = UiUtil.codeArea("Response");
    private final HttpInspectorPane inspector = new HttpInspectorPane();
    private final TextArea notesEditor = UiUtil.codeArea("Notes");
    private final TextArea commentsEditor = UiUtil.codeArea("Comments");
    private final TextField tagsEditor = new TextField();
    private final ComboBox<String> colorEditor = new ComboBox<>();
    private final CheckBox favoriteEditor = new CheckBox("Favorite");
    private final Label status = new Label("Ready");
    private HttpTransaction selectedTransaction;

    public HistoryTab(Database database, ObservableList<HttpTransaction> history, Consumer<HttpTransaction> sendRepeater,
                      Consumer<HttpTransaction> sendIntruder, Consumer<HttpTransaction> sendScanner,
                      ScopeControl scopeControl) {
        super("HTTP History");
        this.database = database;
        this.history = history;
        setClosable(false);
        filtered = new FilteredList<>(history);

        TextField hostFilter = new TextField();
        hostFilter.setPromptText("Host");
        hostFilter.getStyleClass().add("filter-field");
        TextField methodFilter = new TextField();
        methodFilter.setPromptText("Method");
        methodFilter.getStyleClass().add("filter-field");
        TextField statusFilter = new TextField();
        statusFilter.setPromptText("Status");
        statusFilter.getStyleClass().add("filter-field");
        TextField keywordFilter = new TextField();
        keywordFilter.setPromptText("Keyword");
        keywordFilter.getStyleClass().add("filter-field");
        ComboBox<String> highlightFilter = new ComboBox<>();
        highlightFilter.getItems().add("Any Highlight");
        highlightFilter.getItems().addAll(RequestAnnotationActions.HIGHLIGHT_COLORS);
        highlightFilter.getSelectionModel().select("Any Highlight");
        CheckBox scopeOnly = new CheckBox("Scope only");
        Button exportCsv = new Button("CSV All");
        Button exportJson = new Button("JSON All");
        Button exportSelectedCsv = new Button("CSV Selected");
        Button exportSelectedJson = new Button("JSON Selected");

        table = new TableView<>(filtered);
        UiUtil.constrainTable(table);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setPlaceholder(UiUtil.emptyState("No HTTP traffic yet", "Start the proxy or run the crawler to capture requests and responses.", null, null));
        table.getColumns().add(column("#", "id", 70));
        table.getColumns().add(favoriteColumn());
        table.getColumns().add(column("Note", "noteIndicator", 70));
        table.getColumns().add(column("Color", "colorLabel", 90));
        table.getColumns().add(column("Method", "method", 90));
        table.getColumns().add(column("Host", "host", 220));
        table.getColumns().add(column("Path", "path", 360));
        table.getColumns().add(statusColumn());
        table.getColumns().add(column("Length", "length", 90));
        table.getColumns().add(column("MIME", "mimeType", 160));
        table.getColumns().add(column("Protocol", "protocol", 100));
        table.getColumns().add(column("Tags", "tags", 150));
        table.getColumns().add(column("Time", "timeMs", 90));

        Runnable apply = () -> filtered.setPredicate(tx ->
                contains(tx.getHost(), hostFilter.getText())
                        && contains(tx.getMethod(), methodFilter.getText())
                        && contains(String.valueOf(tx.getStatus()), statusFilter.getText())
                        && matchesHighlight(tx, highlightFilter.getSelectionModel().getSelectedItem())
                        && (!scopeOnly.isSelected() || scopeControl.isInScope(tx.getUrl()))
                        && (keywordFilter.getText().isBlank()
                        || contains(tx.getRequestRaw(), keywordFilter.getText())
                        || contains(tx.getResponseRaw(), keywordFilter.getText())
                        || contains(tx.getPath(), keywordFilter.getText())
                        || contains(tx.getNotes(), keywordFilter.getText())
                        || contains(tx.getComments(), keywordFilter.getText())
                        || contains(tx.getTags(), keywordFilter.getText())));
        hostFilter.textProperty().addListener((obs, old, val) -> apply.run());
        methodFilter.textProperty().addListener((obs, old, val) -> apply.run());
        statusFilter.textProperty().addListener((obs, old, val) -> apply.run());
        keywordFilter.textProperty().addListener((obs, old, val) -> apply.run());
        highlightFilter.valueProperty().addListener((obs, old, val) -> apply.run());
        scopeOnly.selectedProperty().addListener((obs, old, val) -> apply.run());

        table.setRowFactory(view -> {
            TableRow<HttpTransaction> row = new TableRow<>() {
                @Override
                protected void updateItem(HttpTransaction tx, boolean empty) {
                    super.updateItem(tx, empty);
                    RequestAnnotationActions.applyHighlightStyle(this, empty || tx == null ? "" : tx.getColorLabel());
                }
            };
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    showFullViewer(row.getItem());
                }
            });
            return row;
        });

        table.getSelectionModel().selectedItemProperty().addListener((obs, old, tx) -> {
            selectedTransaction = tx;
            if (tx != null) {
                requestViewer.setText(tx.getRequestRaw());
                responseViewer.setText(tx.getResponseRaw());
                inspector.inspect(tx.getRequestRaw(), tx.getResponseRaw(), tx.getNotes());
                notesEditor.setText(tx.getNotes());
                commentsEditor.setText(tx.getComments());
                tagsEditor.setText(tx.getTags());
                colorEditor.getSelectionModel().select(tx.getColorLabel().isBlank() ? "None" : tx.getColorLabel());
                favoriteEditor.setSelected(tx.isFavorite());
            } else {
                requestViewer.clear();
                responseViewer.clear();
                inspector.inspect("", "", "");
                notesEditor.clear();
                commentsEditor.clear();
                tagsEditor.clear();
                colorEditor.getSelectionModel().select("None");
                favoriteEditor.setSelected(false);
            }
        });

        MenuItem repeater = new MenuItem("Send to Repeater");
        repeater.setOnAction(event -> selected(table, sendRepeater));
        MenuItem intruder = new MenuItem("Send to Intruder");
        intruder.setOnAction(event -> selected(table, sendIntruder));
        MenuItem scanner = new MenuItem("Send to Scanner");
        scanner.setOnAction(event -> selected(table, sendScanner));
        MenuItem copyUrl = new MenuItem("Copy URL");
        copyUrl.setOnAction(event -> selectedCopy(table, tx -> tx.getUrl()));
        MenuItem copyCurl = new MenuItem("Copy as cURL");
        copyCurl.setOnAction(event -> selectedCopy(table, tx -> RequestCopyUtil.asCurl(RequestData.fromRaw(tx.getRequestRaw(), schemeFromTransaction(tx)))));
        MenuItem copyFetch = new MenuItem("Copy as Fetch");
        copyFetch.setOnAction(event -> selectedCopy(table, tx -> RequestCopyUtil.asFetch(RequestData.fromRaw(tx.getRequestRaw(), schemeFromTransaction(tx)))));
        MenuItem copyJs = new MenuItem("Copy as JavaScript");
        copyJs.setOnAction(event -> selectedCopy(table, tx -> RequestCopyUtil.asJavaScript(RequestData.fromRaw(tx.getRequestRaw(), schemeFromTransaction(tx)))));
        MenuItem copyPython = new MenuItem("Copy as Python Requests");
        copyPython.setOnAction(event -> selectedCopy(table, tx -> RequestCopyUtil.asPythonRequests(RequestData.fromRaw(tx.getRequestRaw(), schemeFromTransaction(tx)))));
        MenuItem favorite = new MenuItem("Toggle Favorite");
        favorite.setOnAction(event -> {
            HttpTransaction tx = table.getSelectionModel().getSelectedItem();
            if (tx != null) {
                tx.setFavorite(!tx.isFavorite());
                database.updateTransactionAnnotations(tx);
                table.refresh();
            }
        });
        Menu highlight = RequestAnnotationActions.highlightMenu(() -> table.getSelectionModel().getSelectedItem(), database,
                () -> refreshAnnotations(table));
        MenuItem save = new MenuItem("Save");
        save.setOnAction(event -> {
            HttpTransaction tx = table.getSelectionModel().getSelectedItem();
            if (tx != null) {
                saveTransaction(tx);
            }
        });
        table.setContextMenu(new ContextMenu(repeater, intruder, scanner, copyUrl, copyCurl, copyFetch, copyJs, copyPython,
                RequestAnnotationActions.addNote(() -> table.getSelectionModel().getSelectedItem(), database, () -> refreshAnnotations(table)),
                RequestAnnotationActions.editNote(() -> table.getSelectionModel().getSelectedItem(), database, () -> refreshAnnotations(table)),
                RequestAnnotationActions.deleteNote(() -> table.getSelectionModel().getSelectedItem(), database, () -> refreshAnnotations(table)),
                highlight, favorite, save));

        exportCsv.setOnAction(event -> export(List.copyOf(filtered), true, "history.csv"));
        exportJson.setOnAction(event -> export(List.copyOf(filtered), false, "history.json"));
        exportSelectedCsv.setOnAction(event -> exportSelected(true));
        exportSelectedJson.setOnAction(event -> exportSelected(false));

        colorEditor.getItems().add("None");
        colorEditor.getItems().addAll(RequestAnnotationActions.HIGHLIGHT_COLORS);
        colorEditor.getSelectionModel().select("None");
        tagsEditor.setPromptText("comma,separated,tags");
        Button saveAnnotations = new Button("Save Annotation");
        saveAnnotations.setOnAction(event -> saveAnnotations(table));

        SplitPane annotations = new SplitPane(notesEditor, commentsEditor);
        annotations.setDividerPositions(0.5);
        UiUtil.bindDividerPositions(database, "layout.proxy.history.annotations", annotations, 0.5);
        VBox annotationBox = new VBox(8,
                new HBox(8, favoriteEditor, new Label("Tags"), tagsEditor, new Label("Color"), colorEditor, saveAnnotations),
                annotations);
        VBox.setVgrow(annotations, Priority.ALWAYS);

        SplitPane requestResponse = new SplitPane(requestViewer, responseViewer);
        requestResponse.setDividerPositions(0.5);
        UiUtil.bindDividerPositions(database, "layout.proxy.history.requestResponse", requestResponse, 0.5);
        SplitPane evidence = new SplitPane(requestResponse, inspector);
        evidence.setDividerPositions(0.74);
        UiUtil.bindDividerPositions(database, "layout.proxy.history.inspector", evidence, 0.74);
        SplitPane detail = new SplitPane(evidence, annotationBox);
        detail.setOrientation(javafx.geometry.Orientation.VERTICAL);
        detail.setDividerPositions(0.74);
        UiUtil.bindDividerPositions(database, "layout.proxy.history.detail", detail, 0.74);
        SplitPane rootSplit = new SplitPane(table, detail);
        rootSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
        rootSplit.setDividerPositions(0.55);
        UiUtil.bindDividerPositions(database, "layout.proxy.history.main", rootSplit, 0.55);

        HBox filterSpacer = new HBox();
        HBox.setHgrow(filterSpacer, Priority.ALWAYS);
        HBox filters = new HBox(8,
                filterLabel("Host"), hostFilter,
                filterLabel("Method"), methodFilter,
                filterLabel("Status"), statusFilter,
                filterLabel("Search"), keywordFilter,
                highlightFilter, scopeOnly, filterSpacer,
                exportCsv, exportJson, exportSelectedCsv, exportSelectedJson, status);
        filters.getStyleClass().addAll("filter-bar", "history-filter-bar");
        filters.setPadding(new Insets(8, 12, 8, 12));

        VBox root = new VBox(10, filters, rootSplit);
        VBox.setVgrow(rootSplit, Priority.ALWAYS);
        root.setPadding(new Insets(12));
        setContent(root);
        Platform.runLater(() -> {
            if (!filtered.isEmpty()) {
                table.getSelectionModel().select(0);
            }
        });
    }

    private TableColumn<HttpTransaction, Object> column(String title, String property, int width) {
        TableColumn<HttpTransaction, Object> column = new TableColumn<>(title);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        column.setPrefWidth(width);
        UiUtil.addTooltipCellFactory(column);
        return column;
    }

    private TableColumn<HttpTransaction, Boolean> favoriteColumn() {
        TableColumn<HttpTransaction, Boolean> column = new TableColumn<>("*");
        column.setPrefWidth(42);
        column.setCellValueFactory(new PropertyValueFactory<>("favorite"));
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean favorite, boolean empty) {
                super.updateItem(favorite, empty);
                setText(empty || favorite == null || !favorite ? "" : "*");
                setStyle(empty || favorite == null || !favorite ? "" : "-fx-text-fill: #FBBF24; -fx-font-size: 15px; -fx-font-weight: bold;");
            }
        });
        return column;
    }

    private TableColumn<HttpTransaction, Integer> statusColumn() {
        TableColumn<HttpTransaction, Integer> column = new TableColumn<>("Status");
        column.setCellValueFactory(new PropertyValueFactory<>("status"));
        column.setPrefWidth(80);
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer status, boolean empty) {
                super.updateItem(status, empty);
                getStyleClass().removeAll("status-2xx", "status-3xx", "status-4xx", "status-5xx");
                if (empty || status == null) {
                    setText(null);
                    return;
                }
                setText(String.valueOf(status));
                if (status < 300) {
                    getStyleClass().add("status-2xx");
                } else if (status < 400) {
                    getStyleClass().add("status-3xx");
                } else if (status < 500) {
                    getStyleClass().add("status-4xx");
                } else {
                    getStyleClass().add("status-5xx");
                }
            }
        });
        return column;
    }

    private Label filterLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("filter-label");
        return label;
    }

    private boolean contains(String haystack, String needle) {
        return needle == null || needle.isBlank()
                || (haystack != null && haystack.toLowerCase().contains(needle.toLowerCase()));
    }

    private boolean matchesHighlight(HttpTransaction tx, String color) {
        return color == null || color.equals("Any Highlight")
                || RequestAnnotationActions.normalizeColor(tx.getColorLabel()).equals(color);
    }

    private void selected(TableView<HttpTransaction> table, Consumer<HttpTransaction> consumer) {
        HttpTransaction tx = table.getSelectionModel().getSelectedItem();
        if (tx != null) {
            consumer.accept(tx);
        }
    }

    private void selectedCopy(TableView<HttpTransaction> table, java.util.function.Function<HttpTransaction, String> formatter) {
        HttpTransaction tx = table.getSelectionModel().getSelectedItem();
        if (tx != null) {
            ClipboardContent content = new ClipboardContent();
            content.putString(formatter.apply(tx));
            Clipboard.getSystemClipboard().setContent(content);
        }
    }

    private String schemeFromTransaction(HttpTransaction tx) {
        return tx != null && "https".equalsIgnoreCase(tx.getScheme()) ? "https" : "http";
    }

    private void saveAnnotations(TableView<HttpTransaction> table) {
        if (selectedTransaction == null) {
            return;
        }
        selectedTransaction.setNotes(notesEditor.getText());
        selectedTransaction.setComments(commentsEditor.getText());
        selectedTransaction.setTags(tagsEditor.getText());
        String color = colorEditor.getSelectionModel().getSelectedItem();
        selectedTransaction.setColorLabel(color == null || color.equals("None") ? "" : color);
        selectedTransaction.setFavorite(favoriteEditor.isSelected());
        database.updateTransactionAnnotations(selectedTransaction);
        inspector.inspect(selectedTransaction.getRequestRaw(), selectedTransaction.getResponseRaw(), selectedTransaction.getNotes());
        table.refresh();
    }

    private void refreshAnnotations(TableView<HttpTransaction> table) {
        if (selectedTransaction != null) {
            notesEditor.setText(selectedTransaction.getNotes());
            commentsEditor.setText(selectedTransaction.getComments());
            tagsEditor.setText(selectedTransaction.getTags());
            colorEditor.getSelectionModel().select(selectedTransaction.getColorLabel().isBlank() ? "None" : selectedTransaction.getColorLabel());
            favoriteEditor.setSelected(selectedTransaction.isFavorite());
            inspector.inspect(selectedTransaction.getRequestRaw(), selectedTransaction.getResponseRaw(), selectedTransaction.getNotes());
        }
        table.refresh();
    }

    public void selectTransaction(long id) {
        for (HttpTransaction tx : history) {
            if (tx.getId() == id) {
                table.getSelectionModel().select(tx);
                table.scrollTo(tx);
                return;
            }
        }
    }

    private void exportSelected(boolean csv) {
        List<HttpTransaction> selectedRows = List.copyOf(table.getSelectionModel().getSelectedItems());
        if (selectedRows.isEmpty()) {
            status.setText("Select one or more rows to export.");
            return;
        }
        export(selectedRows, csv, csv ? "history-selected.csv" : "history-selected.json");
    }

    private void export(List<HttpTransaction> rows, boolean csv, String fileName) {
        if (rows.isEmpty()) {
            status.setText("No history rows to export.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export History");
        chooser.setInitialFileName(fileName);
        java.io.File file = chooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            try {
                if (csv) {
                    Exporters.historyCsv(rows, Path.of(file.toURI()));
                } else {
                    Exporters.historyJson(rows, Path.of(file.toURI()));
                }
                status.setText("Exported " + rows.size() + " rows to " + file.getName());
            } catch (Exception ex) {
                status.setText("Export failed: " + ex.getMessage());
            }
        }
    }

    private void showFullViewer(HttpTransaction tx) {
        TextArea request = UiUtil.codeArea("Request");
        request.setText(tx.getRequestRaw());
        TextArea response = UiUtil.codeArea("Response");
        response.setText(tx.getResponseRaw());
        SplitPane split = new SplitPane(request, response);
        split.setDividerPositions(0.5);
        Stage stage = new Stage();
        stage.setTitle("Transaction #" + tx.getId() + " - " + tx.getMethod() + " " + tx.getHost());
        stage.setScene(new javafx.scene.Scene(split, 1100, 720));
        stage.show();
        status.setText("Opened transaction #" + tx.getId());
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
                status.setText("Saved transaction #" + tx.getId());
            } catch (Exception ex) {
                status.setText("Save failed: " + ex.getMessage());
            }
        }
    }
}
