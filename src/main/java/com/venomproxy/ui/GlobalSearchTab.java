package com.venomproxy.ui;

import com.venomproxy.model.Finding;
import com.venomproxy.model.HttpTransaction;
import com.venomproxy.model.LogEntry;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Locale;

public class GlobalSearchTab extends Tab {
    private final ObservableList<HttpTransaction> history;
    private final ObservableList<Finding> findings;
    private final ObservableList<LogEntry> logs;
    private final ObservableList<SearchResult> results = FXCollections.observableArrayList();
    private final TextArea preview = UiUtil.codeArea("Selected result preview");
    private final TextField query = new TextField();

    public GlobalSearchTab(ObservableList<HttpTransaction> history, ObservableList<Finding> findings, ObservableList<LogEntry> logs) {
        super("Global Search");
        setClosable(false);
        this.history = history;
        this.findings = findings;
        this.logs = logs;

        query.setPromptText("Search requests, responses, notes, comments, tags, findings, and logs");
        Button run = new Button("Search");
        run.setOnAction(event -> search());
        query.setOnAction(event -> search());

        TableView<SearchResult> table = new TableView<>(results);
        table.getColumns().add(column("Type", "type", 110));
        table.getColumns().add(column("Target", "target", 360));
        table.getColumns().add(column("Match", "match", 520));
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, result) -> {
            if (result != null) {
                preview.setText(result.preview());
            }
        });

        VBox root = new VBox(10, new HBox(8, new Label("Query"), query, run), table, preview);
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

    private void search() {
        String needle = query.getText() == null ? "" : query.getText().trim().toLowerCase(Locale.ROOT);
        results.clear();
        if (needle.isBlank()) {
            return;
        }
        for (HttpTransaction tx : history) {
            addIfMatch(needle, "History", tx.getMethod() + " " + tx.getUrl(), tx.getRequestRaw(), tx.getResponseRaw(),
                    tx.getNotes(), tx.getComments(), tx.getTags());
        }
        for (Finding finding : findings) {
            addIfMatch(needle, "Finding", finding.getSeverity() + " " + finding.getIssue(), finding.getUrl(),
                    finding.getEvidence(), finding.getRequestRaw(), finding.getResponseRaw());
        }
        for (LogEntry log : logs) {
            addIfMatch(needle, "Log", log.getDirection() + " " + log.getHost(), log.getMessage(), log.getTimestamp().toString());
        }
    }

    private void addIfMatch(String needle, String type, String target, String... values) {
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(needle)) {
                results.add(new SearchResult(type, target, snippet(value, needle), String.join("\n\n", values)));
                return;
            }
        }
    }

    private String snippet(String value, String needle) {
        String lower = value.toLowerCase(Locale.ROOT);
        int index = Math.max(0, lower.indexOf(needle));
        int start = Math.max(0, index - 80);
        int end = Math.min(value.length(), index + needle.length() + 160);
        return value.substring(start, end).replace('\n', ' ').replace('\r', ' ');
    }

    private TableColumn<SearchResult, Object> column(String title, String property, int width) {
        TableColumn<SearchResult, Object> column = new TableColumn<>(title);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        column.setPrefWidth(width);
        return column;
    }

    public static class SearchResult {
        private final String type;
        private final String target;
        private final String match;
        private final String preview;

        public SearchResult(String type, String target, String match, String preview) {
            this.type = type;
            this.target = target;
            this.match = match;
            this.preview = preview;
        }

        public String getType() {
            return type;
        }

        public String getTarget() {
            return target;
        }

        public String getMatch() {
            return match;
        }

        public String preview() {
            return preview;
        }
    }
}
