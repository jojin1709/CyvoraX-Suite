package com.venomproxy.ui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
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

import java.util.List;
import java.util.Locale;
import java.util.Comparator;

public class CommandPaletteDialog {
    private final ObservableList<Command> commands;
    private final ObservableList<Command> visibleCommands = FXCollections.observableArrayList();

    public CommandPaletteDialog(List<Command> commands) {
        this.commands = FXCollections.observableArrayList(commands);
        this.visibleCommands.setAll(commands);
    }

    public void show(Window owner) {
        Stage stage = new Stage();
        stage.setTitle("Command Palette");
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        }
        TextField filter = new TextField();
        filter.setPromptText("Run command");
        filter.textProperty().addListener((obs, old, value) -> updateVisible(value));
        TableView<Command> table = new TableView<>(visibleCommands);
        table.setPlaceholder(UiUtil.emptyState("No commands", "Try a different command filter.", null, null));
        table.getColumns().add(column("Command", Command::name, 330));
        table.getColumns().add(column("Shortcut", Command::shortcut, 160));
        table.getColumns().add(column("Description", Command::description, 390));
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                runSelected(stage, table);
            }
        });
        filter.setOnAction(event -> runSelected(stage, table));
        VBox root = new VBox(10, new HBox(8, new Label("Command"), filter), table);
        HBox.setHgrow(filter, Priority.ALWAYS);
        VBox.setVgrow(table, Priority.ALWAYS);
        root.setPadding(new Insets(14));
        stage.setScene(new Scene(root, 940, 540));
        stage.setOnShown(event -> filter.requestFocus());
        stage.show();
    }

    private void updateVisible(String query) {
        String needle = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        if (needle.isBlank()) {
            visibleCommands.setAll(commands);
            return;
        }
        visibleCommands.setAll(commands.stream()
                .map(command -> new ScoredCommand(command, fuzzyScore(needle, command.searchText())))
                .filter(scored -> scored.score() < Integer.MAX_VALUE)
                .sorted(Comparator.comparingInt(ScoredCommand::score)
                        .thenComparing(scored -> scored.command().name(), String.CASE_INSENSITIVE_ORDER))
                .map(ScoredCommand::command)
                .toList());
    }

    private void runSelected(Stage stage, TableView<Command> table) {
        Command selected = table.getSelectionModel().getSelectedItem();
        if (selected == null && !table.getItems().isEmpty()) {
            selected = table.getItems().get(0);
        }
        if (selected != null) {
            selected.action().run();
            stage.close();
        }
    }

    private TableColumn<Command, String> column(String title, java.util.function.Function<Command, String> mapper, int width) {
        TableColumn<Command, String> column = new TableColumn<>(title);
        column.setCellValueFactory(value -> new ReadOnlyStringWrapper(mapper.apply(value.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    public record Command(String name, String shortcut, String description, Runnable action) {
        String searchText() {
            return (name + " " + shortcut + " " + description).toLowerCase(Locale.ROOT);
        }
    }

    private record ScoredCommand(Command command, int score) {
    }

    public static int fuzzyScore(String query, String target) {
        if (query == null || query.isBlank()) {
            return 0;
        }
        if (target == null || target.isBlank()) {
            return Integer.MAX_VALUE;
        }
        String needle = query.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        String haystack = target.toLowerCase(Locale.ROOT);
        int score = 0;
        int last = -1;
        for (char c : needle.toCharArray()) {
            int index = haystack.indexOf(c, last + 1);
            if (index < 0) {
                return Integer.MAX_VALUE;
            }
            score += index - last;
            if (index > 0 && haystack.charAt(index - 1) == ' ') {
                score -= 2;
            }
            last = index;
        }
        if (haystack.contains(query.toLowerCase(Locale.ROOT))) {
            score -= 8;
        }
        return Math.max(0, score);
    }
}
