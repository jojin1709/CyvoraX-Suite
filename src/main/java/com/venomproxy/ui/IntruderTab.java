package com.venomproxy.ui;

import com.venomproxy.intruder.IntruderEngine;
import com.venomproxy.model.HttpTransaction;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Function;

public class IntruderTab extends Tab {
    private final TextArea requestEditor = UiUtil.codeArea("Mark insertion points with §payload§ or §§");
    private final TextArea payloadEditor = UiUtil.codeArea("One payload per line");
    private final ObservableList<IntruderEngine.IntruderResult> results = FXCollections.observableArrayList();
    private final FilteredList<IntruderEngine.IntruderResult> filteredResults = new FilteredList<>(results);
    private final IntruderEngine engine = new IntruderEngine();

    public IntruderTab() {
        super("Intruder");
        setClosable(false);
        payloadEditor.setText("admin\n' OR '1'='1\n<script>alert(1)</script>\n../../../../etc/passwd");
        requestEditor.setText("GET http://example.com/search?q=§§ HTTP/1.1\r\nHost: example.com\r\n\r\n");

        ComboBox<IntruderEngine.AttackType> attackType = new ComboBox<>(FXCollections.observableArrayList(IntruderEngine.AttackType.values()));
        attackType.getSelectionModel().select(IntruderEngine.AttackType.SNIPER);
        TextField statusFilter = new TextField();
        statusFilter.setPromptText("Status");
        TextField lengthFilter = new TextField();
        lengthFilter.setPromptText("Min length");
        Button start = new Button("Start");
        Button loadWordlist = new Button("Load Wordlist");
        Button numbers = new Button("Numbers");
        Button dates = new Button("Dates");
        Button brute = new Button("Brute Force");
        Button clear = new Button("Clear");

        Runnable applyFilters = () -> filteredResults.setPredicate(result ->
                (statusFilter.getText().isBlank() || String.valueOf(result.status()).equals(statusFilter.getText().trim()))
                        && (lengthFilter.getText().isBlank() || result.length() >= parseInt(lengthFilter.getText(), 0)));
        statusFilter.textProperty().addListener((obs, old, value) -> applyFilters.run());
        lengthFilter.textProperty().addListener((obs, old, value) -> applyFilters.run());

        start.setOnAction(event -> {
            results.clear();
            List<String> payloads = Arrays.stream(payloadEditor.getText().split("\\R")).toList();
            new Thread(() -> engine.run(requestEditor.getText(), payloads, attackType.getValue(),
                    result -> Platform.runLater(() -> results.add(result))), "intruder-run").start();
        });
        loadWordlist.setOnAction(event -> loadWordlist());
        numbers.setOnAction(event -> payloadEditor.setText(numberPayloads()));
        dates.setOnAction(event -> payloadEditor.setText(datePayloads()));
        brute.setOnAction(event -> payloadEditor.setText(bruteForcePayloads()));
        clear.setOnAction(event -> results.clear());

        TableView<IntruderEngine.IntruderResult> table = new TableView<>(filteredResults);
        table.getColumns().add(column("#", IntruderEngine.IntruderResult::number, 70));
        table.getColumns().add(column("Payload", IntruderEngine.IntruderResult::payload, 260));
        table.getColumns().add(column("Status", IntruderEngine.IntruderResult::status, 90));
        table.getColumns().add(column("Length", IntruderEngine.IntruderResult::length, 90));
        table.getColumns().add(column("Time", IntruderEngine.IntruderResult::timeMs, 90));
        table.getColumns().add(column("Errors", IntruderEngine.IntruderResult::error, 260));

        SplitPane editorSplit = new SplitPane(requestEditor, payloadEditor);
        editorSplit.setDividerPositions(0.65);
        SplitPane rootSplit = new SplitPane(editorSplit, table);
        rootSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
        rootSplit.setDividerPositions(0.55);
        VBox root = new VBox(8,
                new HBox(8, attackType, start, loadWordlist, numbers, dates, brute, clear, new Label("Anomaly"), statusFilter, lengthFilter),
                rootSplit);
        VBox.setVgrow(rootSplit, Priority.ALWAYS);
        root.setPadding(new Insets(12));
        setContent(root);
    }

    public void loadTransaction(HttpTransaction tx) {
        requestEditor.setText(tx.getRequestRaw().replaceFirst("(?s)([?&][^=]+=)([^&\\s]+)", "$1§$2§"));
    }

    private void loadWordlist() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load Wordlist");
        java.io.File file = chooser.showOpenDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            try {
                payloadEditor.setText(Files.readString(file.toPath()));
            } catch (Exception ignored) {
            }
        }
    }

    private String numberPayloads() {
        StringJoiner joiner = new StringJoiner("\n");
        for (int i = 0; i <= 100; i++) {
            joiner.add(String.valueOf(i));
        }
        return joiner.toString();
    }

    private String datePayloads() {
        StringJoiner joiner = new StringJoiner("\n");
        LocalDate start = LocalDate.now().minusDays(15);
        for (int i = 0; i <= 30; i++) {
            joiner.add(start.plusDays(i).toString());
        }
        return joiner.toString();
    }

    private String bruteForcePayloads() {
        String alphabet = "abc123";
        StringJoiner joiner = new StringJoiner("\n");
        for (char a : alphabet.toCharArray()) {
            for (char b : alphabet.toCharArray()) {
                joiner.add("" + a + b);
            }
        }
        return joiner.toString();
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private TableColumn<IntruderEngine.IntruderResult, Object> column(String title, Function<IntruderEngine.IntruderResult, Object> mapper, int width) {
        TableColumn<IntruderEngine.IntruderResult, Object> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(mapper.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }
}
