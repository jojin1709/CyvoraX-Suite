package com.venomproxy.ui;

import com.venomproxy.db.Database;
import com.venomproxy.model.SessionEntry;
import com.venomproxy.model.SessionRecording;
import com.venomproxy.session.SessionRecorder;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;

public class SessionRecorderTab extends Tab {
    private final Database database;
    private final SessionRecorder recorder;
    private final ObservableList<SessionRecording> recordings = FXCollections.observableArrayList();
    private final ObservableList<SessionEntry> entries = FXCollections.observableArrayList();
    private final TableView<SessionRecording> recordingsTable = new TableView<>(recordings);
    private final TableView<SessionEntry> entriesTable = new TableView<>(entries);
    private final TextArea replayLog = UiUtil.codeArea("Replay output");
    private final Label state = new Label("Recorder: stopped");

    public SessionRecorderTab(Database database, SessionRecorder recorder) {
        super("Sessions");
        this.database = database;
        this.recorder = recorder;
        setClosable(false);

        TextField name = new TextField("Session " + Instant.now());
        Button start = new Button("Start Recording");
        start.setOnAction(event -> {
            recorder.start(name.getText());
            refresh();
        });
        Button stop = new Button("Stop & Save");
        stop.setOnAction(event -> {
            recorder.stop();
            refresh();
        });
        Button replay = new Button("Replay Selected");
        replay.setOnAction(event -> replaySelected());
        Button export = new Button("Export Selected");
        export.setOnAction(event -> exportSelected());
        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> refresh());

        configureRecordingsTable();
        configureEntriesTable();
        recordingsTable.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> loadEntries(selected));

        SplitPane split = new SplitPane(recordingsTable, entriesTable, replayLog);
        split.setDividerPositions(0.28, 0.68);
        VBox.setVgrow(split, Priority.ALWAYS);
        VBox root = new VBox(8, new HBox(8, new Label("Name"), name, start, stop, replay, export, refresh, state), split);
        root.setPadding(new Insets(12));
        setContent(root);
        refresh();
    }

    private void configureRecordingsTable() {
        TableColumn<SessionRecording, Long> id = new TableColumn<>("#");
        id.setCellValueFactory(new PropertyValueFactory<>("id"));
        TableColumn<SessionRecording, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(new PropertyValueFactory<>("name"));
        TableColumn<SessionRecording, Instant> started = new TableColumn<>("Started");
        started.setCellValueFactory(new PropertyValueFactory<>("startedAt"));
        TableColumn<SessionRecording, String> status = new TableColumn<>("Status");
        status.setCellValueFactory(new PropertyValueFactory<>("status"));
        recordingsTable.getColumns().addAll(id, name, started, status);
    }

    private void configureEntriesTable() {
        TableColumn<SessionEntry, Integer> sequence = new TableColumn<>("#");
        sequence.setCellValueFactory(new PropertyValueFactory<>("sequence"));
        TableColumn<SessionEntry, Long> transaction = new TableColumn<>("Tx");
        transaction.setCellValueFactory(new PropertyValueFactory<>("transactionId"));
        TableColumn<SessionEntry, Instant> timestamp = new TableColumn<>("Timestamp");
        timestamp.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        entriesTable.getColumns().addAll(sequence, transaction, timestamp);
        entriesTable.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                replayLog.setText(selected.getRequestRaw() + "\n\n" + selected.getResponseRaw());
            }
        });
    }

    private void refresh() {
        recordings.setAll(database.listSessionRecordings());
        state.setText("Recorder: " + (recorder.isRecording() ? "recording #" + recorder.activeRecordingId() : "stopped"));
        SessionRecording selected = recordingsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            loadEntries(selected);
        }
    }

    private void loadEntries(SessionRecording recording) {
        if (recording == null) {
            entries.clear();
            return;
        }
        entries.setAll(database.listSessionEntries(recording.getId()));
    }

    private void replaySelected() {
        List<SessionEntry> snapshot = List.copyOf(entries);
        if (snapshot.isEmpty()) {
            replayLog.setText("No recorded entries selected.");
            return;
        }
        replayLog.setText("Replaying " + snapshot.size() + " entries...\n");
        new Thread(() -> {
            List<SessionRecorder.ReplayResult> results = recorder.replay(snapshot);
            StringBuilder output = new StringBuilder();
            results.forEach(result -> output.append(result.toLine()).append('\n'));
            Platform.runLater(() -> replayLog.setText(output.toString()));
        }, "session-replay").start();
    }

    private void exportSelected() {
        if (entries.isEmpty()) {
            replayLog.setText("No entries to export.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Session Recording");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text", "*.txt"));
        java.io.File file = chooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            Files.writeString(file.toPath(), recorder.exportText(List.copyOf(entries)));
            replayLog.setText("Exported: " + file);
        } catch (IOException ex) {
            replayLog.setText("Export failed: " + ex.getMessage());
        }
    }
}
