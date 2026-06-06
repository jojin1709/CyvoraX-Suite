package com.venomproxy.ui;

import com.venomproxy.db.Database;
import com.venomproxy.model.HttpTransaction;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class OrganizerTab extends Tab {
    private final Database database;
    private final FilteredList<HttpTransaction> organized;
    private final TextArea notes = UiUtil.codeArea("Notes");
    private final TextArea comments = UiUtil.codeArea("Comments");
    private final TextField tags = new TextField();
    private final Label status = new Label("Ready");
    private HttpTransaction selected;
    private Runnable refreshFilter = () -> {
    };

    public OrganizerTab(Database database, ObservableList<HttpTransaction> history) {
        super("Organizer");
        setClosable(false);
        this.database = database;
        this.organized = new FilteredList<>(history, this::isOrganized);

        TextField filter = new TextField();
        filter.setPromptText("Filter important requests, notes, comments, tags");
        ComboBox<String> highlightFilter = new ComboBox<>();
        highlightFilter.getItems().add("Any Highlight");
        highlightFilter.getItems().addAll(RequestAnnotationActions.HIGHLIGHT_COLORS);
        highlightFilter.getSelectionModel().select("Any Highlight");
        Runnable applyFilter = () -> organized.setPredicate(tx -> isOrganized(tx)
                && matches(tx, filter.getText())
                && matchesHighlight(tx, highlightFilter.getSelectionModel().getSelectedItem()));
        refreshFilter = applyFilter;
        filter.textProperty().addListener((obs, old, value) -> applyFilter.run());
        highlightFilter.valueProperty().addListener((obs, old, value) -> applyFilter.run());

        TableView<HttpTransaction> table = new TableView<>(organized);
        table.setPlaceholder(UiUtil.emptyState("No organized requests", "Favorite, tag, note, or highlight requests from History or Site Map to collect them here.", null, null));
        table.getColumns().add(column("#", "id", 70));
        table.getColumns().add(column("Note", "noteIndicator", 70));
        table.getColumns().add(column("Method", "method", 90));
        table.getColumns().add(column("Host", "host", 220));
        table.getColumns().add(column("Path", "path", 360));
        table.getColumns().add(column("Tags", "tags", 180));
        table.getColumns().add(column("Color", "colorLabel", 90));
        table.setRowFactory(view -> new TableRow<>() {
            @Override
            protected void updateItem(HttpTransaction tx, boolean empty) {
                super.updateItem(tx, empty);
                RequestAnnotationActions.applyHighlightStyle(this, empty || tx == null ? "" : tx.getColorLabel());
            }
        });
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, tx) -> {
            selected = tx;
            if (tx != null) {
                notes.setText(tx.getNotes());
                comments.setText(tx.getComments());
                tags.setText(tx.getTags());
            }
        });

        Button save = new Button("Save Notes");
        save.setOnAction(event -> saveSelected(table));
        Button export = new Button("Export Selected");
        export.setOnAction(event -> exportSelected(table));
        Menu highlight = RequestAnnotationActions.highlightMenu(() -> table.getSelectionModel().getSelectedItem(), database,
                () -> refresh(table));
        table.setContextMenu(new ContextMenu(
                RequestAnnotationActions.addNote(() -> table.getSelectionModel().getSelectedItem(), database, () -> refresh(table)),
                RequestAnnotationActions.editNote(() -> table.getSelectionModel().getSelectedItem(), database, () -> refresh(table)),
                RequestAnnotationActions.deleteNote(() -> table.getSelectionModel().getSelectedItem(), database, () -> refresh(table)),
                highlight));

        VBox editors = new VBox(8, new HBox(8, new Label("Tags"), tags, save, export, status), notes, comments);
        HBox.setHgrow(tags, Priority.ALWAYS);
        VBox.setVgrow(notes, Priority.ALWAYS);
        VBox.setVgrow(comments, Priority.ALWAYS);

        javafx.scene.control.SplitPane split = new javafx.scene.control.SplitPane(table, editors);
        split.setDividerPositions(0.58);
        VBox root = new VBox(10, new HBox(8, new Label("Search"), filter, new Label("Highlight"), highlightFilter), split);
        HBox.setHgrow(filter, Priority.ALWAYS);
        VBox.setVgrow(split, Priority.ALWAYS);
        root.setPadding(new Insets(12));
        setContent(root);
    }

    private boolean isOrganized(HttpTransaction tx) {
        return tx.isFavorite()
                || !tx.getNotes().isBlank()
                || !tx.getComments().isBlank()
                || !tx.getTags().isBlank()
                || !tx.getColorLabel().isBlank();
    }

    private boolean matches(HttpTransaction tx, String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return needle.isBlank()
                || contains(tx.getUrl(), needle)
                || contains(tx.getNotes(), needle)
                || contains(tx.getComments(), needle)
                || contains(tx.getTags(), needle);
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private boolean matchesHighlight(HttpTransaction tx, String color) {
        return color == null || color.equals("Any Highlight")
                || RequestAnnotationActions.normalizeColor(tx.getColorLabel()).equals(color);
    }

    private void saveSelected(TableView<HttpTransaction> table) {
        if (selected == null) {
            return;
        }
        selected.setNotes(notes.getText());
        selected.setComments(comments.getText());
        selected.setTags(tags.getText());
        selected.setFavorite(true);
        database.updateTransactionAnnotations(selected);
        refreshFilter.run();
        table.refresh();
        status.setText("Saved organizer entry #" + selected.getId());
    }

    private void refresh(TableView<HttpTransaction> table) {
        if (selected != null) {
            notes.setText(selected.getNotes());
            comments.setText(selected.getComments());
            tags.setText(selected.getTags());
        }
        refreshFilter.run();
        table.refresh();
    }

    private void exportSelected(TableView<HttpTransaction> table) {
        HttpTransaction tx = table.getSelectionModel().getSelectedItem();
        if (tx == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Organizer Entry");
        chooser.setInitialFileName("organizer-" + tx.getId() + ".txt");
        java.io.File destination = chooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (destination == null) {
            return;
        }
        String content = """
                URL: %s
                Tags: %s
                Color: %s
                Favorite: %s

                Notes:
                %s

                Comments:
                %s

                ==== REQUEST ====
                %s

                ==== RESPONSE ====
                %s
                """.formatted(tx.getUrl(), tx.getTags(), tx.getColorLabel(), tx.isFavorite(),
                tx.getNotes(), tx.getComments(), tx.getRequestRaw(), tx.getResponseRaw());
        try {
            Files.writeString(Path.of(destination.toURI()), content);
            status.setText("Exported organizer entry #" + tx.getId());
        } catch (Exception ex) {
            status.setText("Export failed: " + ex.getMessage());
        }
    }

    private TableColumn<HttpTransaction, Object> column(String title, String property, int width) {
        TableColumn<HttpTransaction, Object> column = new TableColumn<>(title);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        column.setPrefWidth(width);
        return column;
    }
}
