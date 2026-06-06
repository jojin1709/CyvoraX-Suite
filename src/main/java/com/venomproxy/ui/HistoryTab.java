package com.venomproxy.ui;

import com.venomproxy.db.Database;
import com.venomproxy.model.HttpTransaction;
import com.venomproxy.model.RequestData;
import com.venomproxy.proxy.ScopeControl;
import com.venomproxy.util.Exporters;
import com.venomproxy.util.RequestCopyUtil;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
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
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.nio.file.Path;
import java.util.function.Consumer;

public class HistoryTab extends Tab {
    private final Database database;
    private final FilteredList<HttpTransaction> filtered;
    private final TextArea requestViewer = UiUtil.codeArea("Request");
    private final TextArea responseViewer = UiUtil.codeArea("Response");
    private final TextArea notesEditor = UiUtil.codeArea("Notes");
    private final TextArea commentsEditor = UiUtil.codeArea("Comments");
    private final TextField tagsEditor = new TextField();
    private final ComboBox<String> colorEditor = new ComboBox<>();
    private final CheckBox favoriteEditor = new CheckBox("Favorite");
    private HttpTransaction selectedTransaction;

    public HistoryTab(Database database, ObservableList<HttpTransaction> history, Consumer<HttpTransaction> sendRepeater,
                      Consumer<HttpTransaction> sendIntruder, Consumer<HttpTransaction> sendScanner,
                      ScopeControl scopeControl) {
        super("HTTP History");
        this.database = database;
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
        table.getColumns().add(column("Fav", "favorite", 60));
        table.getColumns().add(column("Method", "method", 90));
        table.getColumns().add(column("Host", "host", 220));
        table.getColumns().add(column("Path", "path", 360));
        table.getColumns().add(column("Status", "status", 80));
        table.getColumns().add(column("Length", "length", 90));
        table.getColumns().add(column("MIME", "mimeType", 160));
        table.getColumns().add(column("Protocol", "protocol", 100));
        table.getColumns().add(column("Tags", "tags", 150));
        table.getColumns().add(column("Time", "timeMs", 90));

        Runnable apply = () -> filtered.setPredicate(tx ->
                contains(tx.getHost(), hostFilter.getText())
                        && contains(tx.getMethod(), methodFilter.getText())
                        && contains(String.valueOf(tx.getStatus()), statusFilter.getText())
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
        scopeOnly.selectedProperty().addListener((obs, old, val) -> apply.run());

        table.getSelectionModel().selectedItemProperty().addListener((obs, old, tx) -> {
            selectedTransaction = tx;
            if (tx != null) {
                requestViewer.setText(tx.getRequestRaw());
                responseViewer.setText(tx.getResponseRaw());
                notesEditor.setText(tx.getNotes());
                commentsEditor.setText(tx.getComments());
                tagsEditor.setText(tx.getTags());
                colorEditor.getSelectionModel().select(tx.getColorLabel().isBlank() ? "None" : tx.getColorLabel());
                favoriteEditor.setSelected(tx.isFavorite());
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
        copyCurl.setOnAction(event -> selectedCopy(table, tx -> RequestCopyUtil.asCurl(RequestData.fromRaw(tx.getRequestRaw()))));
        MenuItem copyFetch = new MenuItem("Copy as Fetch");
        copyFetch.setOnAction(event -> selectedCopy(table, tx -> RequestCopyUtil.asFetch(RequestData.fromRaw(tx.getRequestRaw()))));
        MenuItem copyJs = new MenuItem("Copy as JavaScript");
        copyJs.setOnAction(event -> selectedCopy(table, tx -> RequestCopyUtil.asJavaScript(RequestData.fromRaw(tx.getRequestRaw()))));
        MenuItem copyPython = new MenuItem("Copy as Python Requests");
        copyPython.setOnAction(event -> selectedCopy(table, tx -> RequestCopyUtil.asPythonRequests(RequestData.fromRaw(tx.getRequestRaw()))));
        MenuItem favorite = new MenuItem("Toggle Favorite");
        favorite.setOnAction(event -> {
            HttpTransaction tx = table.getSelectionModel().getSelectedItem();
            if (tx != null) {
                tx.setFavorite(!tx.isFavorite());
                database.updateTransactionAnnotations(tx);
                table.refresh();
            }
        });
        MenuItem save = new MenuItem("Save");
        save.setOnAction(event -> {
            HttpTransaction tx = table.getSelectionModel().getSelectedItem();
            if (tx != null) {
                saveTransaction(tx);
            }
        });
        table.setContextMenu(new ContextMenu(repeater, intruder, scanner, copyUrl, copyCurl, copyFetch, copyJs, copyPython, favorite, save));

        exportCsv.setOnAction(event -> export(history, true));
        exportJson.setOnAction(event -> export(history, false));

        colorEditor.getItems().addAll("None", "Red", "Yellow", "Green", "Blue", "Purple");
        colorEditor.getSelectionModel().select("None");
        tagsEditor.setPromptText("comma,separated,tags");
        Button saveAnnotations = new Button("Save Annotation");
        saveAnnotations.setOnAction(event -> saveAnnotations(table));

        VBox annotationBox = new VBox(8,
                new HBox(8, favoriteEditor, new Label("Tags"), tagsEditor, new Label("Color"), colorEditor, saveAnnotations),
                new SplitPane(notesEditor, commentsEditor));
        SplitPane viewers = new SplitPane(requestViewer, responseViewer, annotationBox);
        viewers.setDividerPositions(0.5);
        SplitPane rootSplit = new SplitPane(table, viewers);
        rootSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
        rootSplit.setDividerPositions(0.55);

        HBox filters = new HBox(8, new Label("Filter"), hostFilter, methodFilter, statusFilter, keywordFilter, scopeOnly, exportCsv, exportJson);
        filters.getStyleClass().add("filter-bar");

        VBox root = new VBox(10, filters, rootSplit);
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

    private void selectedCopy(TableView<HttpTransaction> table, java.util.function.Function<HttpTransaction, String> formatter) {
        HttpTransaction tx = table.getSelectionModel().getSelectedItem();
        if (tx != null) {
            ClipboardContent content = new ClipboardContent();
            content.putString(formatter.apply(tx));
            Clipboard.getSystemClipboard().setContent(content);
        }
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
        table.refresh();
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
