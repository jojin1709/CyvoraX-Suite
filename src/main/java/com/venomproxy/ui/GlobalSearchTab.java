package com.venomproxy.ui;

import com.venomproxy.db.Database;
import com.venomproxy.model.SearchResult;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.function.Consumer;

public class GlobalSearchTab extends Tab {
    private final Database database;
    private final Consumer<SearchResult> navigator;
    private final ObservableList<SearchResult> results = FXCollections.observableArrayList();
    private final TextArea preview = UiUtil.codeArea("Selected result preview");
    private final TextField query = new TextField();
    private final Label resultCount = new Label("0 results");

    public GlobalSearchTab(Database database, Consumer<SearchResult> navigator) {
        super("Global Search");
        setClosable(false);
        this.database = database;
        this.navigator = navigator;

        query.setPromptText("Search URLs, requests, responses, headers, bodies, notes, and findings");
        Button run = new Button("Search");
        run.setOnAction(event -> search());
        query.setOnAction(event -> search());
        PauseTransition debounce = new PauseTransition(Duration.millis(180));
        debounce.setOnFinished(event -> search());
        query.textProperty().addListener((obs, old, value) -> debounce.playFromStart());

        TableView<SearchResult> table = new TableView<>(results);
        table.setPlaceholder(UiUtil.emptyState("No search results", "Search URLs, requests, responses, findings, notes, tags, and sessions.", "Focus Search", this::focusSearch));
        table.getColumns().add(column("Type", "type", 110));
        table.getColumns().add(column("#", "recordId", 80));
        table.getColumns().add(column("Field", "matchField", 140));
        table.getColumns().add(column("Target", "target", 360));
        table.getColumns().add(column("Match", "match", 520));
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, result) -> {
            if (result != null) {
                preview.setText(result.getPreview());
            }
        });
        MenuItem open = new MenuItem("Open Result");
        open.setOnAction(event -> openSelected(table));
        table.setContextMenu(new ContextMenu(open));
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                openSelected(table);
            }
        });

        VBox root = new VBox(10, new HBox(8, new Label("Query"), query, run, resultCount), table, preview);
        HBox.setHgrow(query, Priority.ALWAYS);
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(preview, Priority.ALWAYS);
        root.setPadding(new Insets(12));
        setContent(root);
    }

    public void focusSearch() {
        if (getContent() != null) {
            query.requestFocus();
        }
    }

    public String searchQuery() {
        return query.getText() == null ? "" : query.getText();
    }

    public void restoreSearchQuery(String value) {
        query.setText(value == null ? "" : value);
        search();
    }

    private void search() {
        results.clear();
        String needle = query.getText() == null ? "" : query.getText().trim();
        if (needle.isBlank()) {
            resultCount.setText("0 results");
            return;
        }
        results.setAll(database.search(needle, 500));
        resultCount.setText(results.size() + (results.size() == 1 ? " result" : " results"));
    }

    private TableColumn<SearchResult, Object> column(String title, String property, int width) {
        TableColumn<SearchResult, Object> column = new TableColumn<>(title);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        column.setPrefWidth(width);
        return column;
    }

    private void openSelected(TableView<SearchResult> table) {
        SearchResult result = table.getSelectionModel().getSelectedItem();
        if (result != null) {
            navigator.accept(result);
        }
    }
}
