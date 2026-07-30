package com.venomproxy.ui;

import com.venomproxy.db.Database;
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
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Spinner;
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
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class IntruderTab extends Tab {
    private static final String MARKER = "\u00A7";
    private static final String EMPTY_MARKER = MARKER + MARKER;

    private final Database database;
    private final TextArea requestEditor = UiUtil.codeArea("Mark insertion points with " + MARKER + "payload" + MARKER + " or " + EMPTY_MARKER);
    private final TextArea payloadEditor = UiUtil.codeArea("One payload per line. Separate payload sets with a blank line.");
    private final ObservableList<IntruderEngine.IntruderResult> results = FXCollections.observableArrayList();
    private final FilteredList<IntruderEngine.IntruderResult> filteredResults = new FilteredList<>(results);
    private final IntruderEngine engine = new IntruderEngine();
    private final Label status = new Label("Ready");
    private final ProgressBar progress = new ProgressBar(0);
    private volatile String defaultScheme = "http";
    private volatile IntruderEngine.RunControl activeControl;
    private volatile Thread activeThread;

    public IntruderTab(Database database) {
        super("Intruder");
        this.database = database;
        setClosable(false);
        payloadEditor.setText("admin\n' OR '1'='1\n<script>alert(1)</script>\n../../../../etc/passwd");
        requestEditor.setText("GET http://example.com/search?q=" + EMPTY_MARKER + " HTTP/1.1\r\nHost: example.com\r\n\r\n");

        ComboBox<IntruderEngine.AttackType> attackType = new ComboBox<>(FXCollections.observableArrayList(IntruderEngine.AttackType.values()));
        attackType.getSelectionModel().select(IntruderEngine.AttackType.SNIPER);
        TextField statusFilter = new TextField();
        statusFilter.setPromptText("Status");
        TextField lengthFilter = new TextField();
        lengthFilter.setPromptText("Min length");
        Spinner<Integer> threads = new Spinner<>(1, 50, 5);
        threads.setPrefWidth(75);
        Spinner<Integer> delayMs = new Spinner<>(0, 10_000, 0, 100);
        delayMs.setPrefWidth(85);
        Button start = new Button("Start");
        Button pause = new Button("Pause");
        Button resume = new Button("Resume");
        Button stop = new Button("Stop");
        Button loadWordlist = new Button("Load Wordlist");
        Button numbers = new Button("Numbers");
        Button dates = new Button("Dates");
        Button brute = new Button("Brute Force");
        Button clear = new Button("Clear");
        Button export = new Button("Export Results");

        Runnable applyFilters = () -> filteredResults.setPredicate(result ->
                (statusFilter.getText().isBlank() || String.valueOf(result.status()).equals(statusFilter.getText().trim()))
                        && (lengthFilter.getText().isBlank() || result.length() >= parseInt(lengthFilter.getText(), 0)));
        statusFilter.textProperty().addListener((obs, old, value) -> applyFilters.run());
        lengthFilter.textProperty().addListener((obs, old, value) -> applyFilters.run());

        start.setOnAction(event -> startAttack(attackType.getValue(), threads.getValue(), delayMs.getValue()));
        pause.setOnAction(event -> {
            IntruderEngine.RunControl control = activeControl;
            if (control != null) {
                control.pause();
                status.setText("Paused at " + results.size() + " results");
            }
        });
        resume.setOnAction(event -> {
            IntruderEngine.RunControl control = activeControl;
            if (control != null) {
                control.resume();
                status.setText("Running");
            }
        });
        stop.setOnAction(event -> stopAttack());
        loadWordlist.setOnAction(event -> loadWordlist());
        numbers.setOnAction(event -> payloadEditor.setText(numberPayloads()));
        dates.setOnAction(event -> payloadEditor.setText(datePayloads()));
        brute.setOnAction(event -> payloadEditor.setText(bruteForcePayloads()));
        clear.setOnAction(event -> {
            results.clear();
            progress.setProgress(0);
            status.setText("Results cleared");
        });
        export.setOnAction(event -> exportResults());

        TableView<IntruderEngine.IntruderResult> table = new TableView<>(filteredResults);
        UiUtil.constrainTable(table);
        table.setPlaceholder(UiUtil.emptyState("No attack results", "Mark payload positions, choose an attack type, then start Intruder.", "Start", () -> start.fire()));
        table.getColumns().add(column("#", IntruderEngine.IntruderResult::number, 70));
        table.getColumns().add(column("Payload", IntruderEngine.IntruderResult::payload, 260));
        table.getColumns().add(column("Status", IntruderEngine.IntruderResult::status, 90));
        table.getColumns().add(column("Length", IntruderEngine.IntruderResult::length, 90));
        table.getColumns().add(column("Time", IntruderEngine.IntruderResult::timeMs, 90));
        table.getColumns().add(column("Errors", IntruderEngine.IntruderResult::error, 260));

        Label tableHeader = new Label("Payload Set: Position 1 — Attack Results");
        tableHeader.getStyleClass().add("filter-label");
        VBox tableContainer = new VBox(4, tableHeader, table);
        VBox.setVgrow(table, Priority.ALWAYS);

        progress.setPrefWidth(160);
        SplitPane editorSplit = new SplitPane(requestEditor, payloadEditor);
        editorSplit.setDividerPositions(0.65);
        UiUtil.bindDividerPositions(database, "layout.intruder.editors", editorSplit, 0.65);
        SplitPane rootSplit = new SplitPane(editorSplit, tableContainer);
        rootSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
        rootSplit.setDividerPositions(0.55);
        UiUtil.bindDividerPositions(database, "layout.intruder.main", rootSplit, 0.55);
        HBox controls = new HBox(8, attackType,
                new Label("Threads:"), threads,
                new Label("Delay (ms):"), delayMs,
                start, pause, resume, stop, loadWordlist, numbers, dates, brute, clear, export,
                new Label("Anomaly"), statusFilter, lengthFilter, progress, status);
        controls.getStyleClass().add("filter-bar");
        VBox root = new VBox(8, controls, rootSplit);
        VBox.setVgrow(rootSplit, Priority.ALWAYS);
        root.setPadding(new Insets(12));
        setContent(root);
    }

    public void loadTransaction(HttpTransaction tx) {
        requestEditor.setText(tx.getRequestRaw().replaceFirst("(?s)([?&][^=]+=)([^&\\s]+)", "$1" + MARKER + "$2" + MARKER));
        defaultScheme = "https".equalsIgnoreCase(tx.getScheme()) ? "https" : "http";
        status.setText("Loaded transaction #" + tx.getId());
    }

    private void startAttack(IntruderEngine.AttackType attackType, int threadCount, int delayMs) {
        if (activeThread != null && activeThread.isAlive()) {
            status.setText("Attack already running");
            return;
        }
        results.clear();
        List<String> payloads = Arrays.stream(payloadEditor.getText().split("\\R", -1)).toList();
        int total = engine.mutationsFor(requestEditor.getText(), payloads, attackType).size();
        if (total == 0) {
            status.setText("No payloads to send");
            return;
        }
        progress.setProgress(0);
        AtomicInteger completed = new AtomicInteger();
        IntruderEngine.RunControl control = new IntruderEngine.RunControl();
        control.setThreadCount(threadCount);
        control.setDelayMs(delayMs);
        activeControl = control;
        status.setText("Running " + total + " requests | " + threadCount + " threads | " + delayMs + " ms delay");
        activeThread = new Thread(() -> {
            engine.run(requestEditor.getText(), payloads, attackType, defaultScheme, control, result -> Platform.runLater(() -> {
                results.add(result);
                int done = completed.incrementAndGet();
                progress.setProgress(done / (double) total);
                status.setText("Running " + done + "/" + total);
            }));
            Platform.runLater(() -> {
                progress.setProgress(control.isCancelled() ? progress.getProgress() : 1);
                status.setText(control.isCancelled() ? "Stopped at " + results.size() + "/" + total : "Completed " + results.size() + " requests");
                activeControl = null;
            });
        }, "intruder-run");
        activeThread.start();
    }

    private void stopAttack() {
        IntruderEngine.RunControl control = activeControl;
        if (control != null) {
            control.cancel();
            status.setText("Stopping...");
        }
    }

    private void loadWordlist() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load Wordlist");
        java.io.File file = chooser.showOpenDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            try {
                payloadEditor.setText(Files.readString(file.toPath()));
                status.setText("Loaded wordlist: " + file.getName());
            } catch (Exception ex) {
                status.setText("Wordlist load failed: " + ex.getMessage());
            }
        }
    }

    private void exportResults() {
        if (results.isEmpty()) {
            status.setText("No Intruder results to export");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Intruder Results");
        chooser.setInitialFileName("intruder-results.tsv");
        java.io.File file = chooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file == null) {
            return;
        }
        StringBuilder builder = new StringBuilder("#\tpayload\tstatus\tlength\ttime_ms\terror\n");
        for (IntruderEngine.IntruderResult result : results) {
            builder.append(result.number()).append('\t')
                    .append(escape(result.payload())).append('\t')
                    .append(result.status()).append('\t')
                    .append(result.length()).append('\t')
                    .append(result.timeMs()).append('\t')
                    .append(escape(result.error())).append('\n');
        }
        try {
            Files.writeString(Path.of(file.toURI()), builder.toString());
            status.setText("Exported " + results.size() + " Intruder results");
        } catch (Exception ex) {
            status.setText("Export failed: " + ex.getMessage());
        }
    }

    private String escape(String value) {
        return (value == null ? "" : value).replace("\t", " ").replace("\r", " ").replace("\n", " ");
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
        UiUtil.addTooltipCellFactory(column);
        return column;
    }
}
