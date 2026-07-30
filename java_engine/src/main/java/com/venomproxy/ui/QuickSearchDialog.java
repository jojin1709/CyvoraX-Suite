package com.venomproxy.ui;

import com.venomproxy.db.Database;
import com.venomproxy.model.SearchResult;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.function.Consumer;

public class QuickSearchDialog {
    private final Database database;
    private final Consumer<SearchResult> navigator;
    private final ObservableList<SearchResult> results = FXCollections.observableArrayList();
    private final Label status = new Label("Type to search");

    public QuickSearchDialog(Database database, Consumer<SearchResult> navigator) {
        this.database = database;
        this.navigator = navigator;
    }

    public void show(Window owner) {
        Stage stage = new Stage();
        stage.setTitle("Quick Search");
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        }
        TextField query = new TextField();
        query.setPromptText("Search URLs, requests, responses, notes, findings");
        Button search = new Button("Search");
        TableView<SearchResult> table = new TableView<>(results);
        table.setPlaceholder(UiUtil.emptyState("No quick results", "Search the active workspace database.", null, null));
        table.getColumns().add(column("Type", SearchResult::getType, 110));
        table.getColumns().add(column("Target", SearchResult::getTarget, 320));
        table.getColumns().add(column("Field", SearchResult::getMatchField, 130));
        table.getColumns().add(column("Match", SearchResult::getMatch, 420));
        Runnable performSearch = () -> {
            String needle = query.getText() == null ? "" : query.getText().trim();
            results.setAll(needle.isBlank() ? java.util.List.of() : database.search(needle, 100));
            status.setText(results.size() + (results.size() == 1 ? " result" : " results"));
        };
        search.setOnAction(event -> performSearch.run());
        query.setOnAction(event -> openSelected(stage, table));
        query.textProperty().addListener((obs, old, value) -> performSearch.run());
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                openSelected(stage, table);
            }
        });
        VBox root = new VBox(10, new HBox(8, query, search, status), table);
        HBox.setHgrow(query, Priority.ALWAYS);
        VBox.setVgrow(table, Priority.ALWAYS);
        root.setPadding(new Insets(14));
        stage.setScene(new Scene(root, 980, 560));
        stage.setOnShown(event -> query.requestFocus());
        stage.show();
    }

    private void openSelected(Stage stage, TableView<SearchResult> table) {
        SearchResult result = table.getSelectionModel().getSelectedItem();
        if (result == null && !results.isEmpty()) {
            result = results.get(0);
        }
        if (result != null) {
            navigator.accept(result);
            stage.close();
        }
    }

    private TableColumn<SearchResult, String> column(String title, java.util.function.Function<SearchResult, String> mapper, int width) {
        TableColumn<SearchResult, String> column = new TableColumn<>(title);
        column.setCellValueFactory(value -> new ReadOnlyStringWrapper(mapper.apply(value.getValue())));
        column.setPrefWidth(width);
        return column;
    }
}
