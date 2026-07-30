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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        Button importRecording = new Button("Import");
        importRecording.setOnAction(event -> importRecording());
        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> refresh());

        configureRecordingsTable();
        configureEntriesTable();
        UiUtil.constrainTable(recordingsTable);
        UiUtil.constrainTable(entriesTable);
        recordingsTable.setPlaceholder(UiUtil.emptyState("No saved sessions", "Start recording proxy traffic to create a replayable session.", null, null));
        entriesTable.setPlaceholder(UiUtil.emptyState("No session entries", "Select a recording to inspect requests and responses.", null, null));
        recordingsTable.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> loadEntries(selected));

        SplitPane split = new SplitPane(recordingsTable, entriesTable, replayLog);
        split.setDividerPositions(0.28, 0.68);
        VBox.setVgrow(split, Priority.ALWAYS);
        VBox root = new VBox(8, new HBox(8, new Label("Name"), name, start, stop, replay, export, importRecording, refresh, state), split);
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

    public void selectRecording(long id) {
        for (SessionRecording recording : recordings) {
            if (recording.getId() == id) {
                recordingsTable.getSelectionModel().select(recording);
                recordingsTable.scrollTo(recording);
                loadEntries(recording);
                return;
            }
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

    private void importRecording() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Session Recording");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text", "*.txt"));
        java.io.File file = chooser.showOpenDialog(getTabPane().getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            String content = Files.readString(file.toPath());
            Pattern marker = Pattern.compile("(?m)^=== Entry (\\d+) @ ([^=]+) ===\\R");
            Matcher matcher = marker.matcher(content);
            java.util.ArrayList<Match> matches = new java.util.ArrayList<>();
            while (matcher.find()) {
                matches.add(new Match(Integer.parseInt(matcher.group(1)), matcher.group(2).trim(), matcher.end(), matcher.start()));
            }
            if (matches.isEmpty()) {
                replayLog.setText("Import failed: no session entries found.");
                return;
            }
            long recordingId = database.createSessionRecording("Imported " + file.getName());
            int imported = 0;
            for (int i = 0; i < matches.size(); i++) {
                Match current = matches.get(i);
                int blockEnd = i + 1 < matches.size() ? matches.get(i + 1).markerStart() : content.length();
                String block = content.substring(current.contentStart(), blockEnd).strip();
                int responseStart = responseStart(block);
                if (responseStart <= 0) {
                    continue;
                }
                String requestRaw = block.substring(0, responseStart).strip();
                String responseRaw = block.substring(responseStart).strip();
                Instant timestamp = parseInstant(current.timestamp());
                database.saveSessionEntryRaw(recordingId, 0, current.sequence(), requestRaw, responseRaw, timestamp);
                imported++;
            }
            database.stopSessionRecording(recordingId);
            refresh();
            replayLog.setText("Imported " + imported + " entries from " + file.getName());
        } catch (Exception ex) {
            replayLog.setText("Import failed: " + ex.getMessage());
        }
    }

    private int responseStart(String block) {
        int lf = block.indexOf("\nHTTP/");
        if (lf >= 0) {
            return lf + 1;
        }
        int crlf = block.indexOf("\r\nHTTP/");
        return crlf >= 0 ? crlf + 2 : -1;
    }

    private Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return Instant.now();
        }
    }

    private record Match(int sequence, String timestamp, int contentStart, int markerStart) {
    }
}
