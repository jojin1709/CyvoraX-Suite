package com.venomproxy.ui;

import com.venomproxy.model.MatchReplaceRule;
import com.venomproxy.proxy.MatchReplaceEngine;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.nio.file.Files;
import java.nio.file.Path;

public class MatchReplaceTab extends Tab {
    private final MatchReplaceEngine engine;
    private final ObservableList<MatchReplaceRule> rules;
    private final CheckBox enabled = new CheckBox("Enabled");
    private final ComboBox<String> phase = new ComboBox<>();
    private final ComboBox<String> target = new ComboBox<>();
    private final TextField pattern = new TextField();
    private final TextField replacement = new TextField();
    private final CheckBox regex = new CheckBox("Regex");
    private final TextField conditionField = new TextField();
    private final TextField conditionPattern = new TextField();
    private final TextArea notes = UiUtil.codeArea("Notes");
    private final Label status = new Label("Ready");
    private MatchReplaceRule selected;

    public MatchReplaceTab(MatchReplaceEngine engine) {
        super("Match & Replace");
        setClosable(false);
        this.engine = engine;
        this.rules = FXCollections.observableArrayList(engine.rules());

        phase.getItems().addAll("Request");
        phase.getSelectionModel().select("Request");
        target.getItems().addAll("URL", "Method", "Header", "Header:Host", "Header:Authorization", "Cookie", "Body");
        target.getSelectionModel().select("Body");
        pattern.setPromptText("Text or regex pattern");
        replacement.setPromptText("Replacement");
        conditionField.setPromptText("optional: URL, Method, Body, Cookie, Header:Name");
        conditionPattern.setPromptText("optional condition pattern");

        TableView<MatchReplaceRule> table = new TableView<>(rules);
        UiUtil.constrainTable(table);
        table.getColumns().add(column("On", "enabled", 60));
        table.getColumns().add(column("Phase", "phase", 90));
        table.getColumns().add(column("Target", "target", 170));
        table.getColumns().add(column("Pattern", "pattern", 220));
        table.getColumns().add(column("Replacement", "replacement", 220));
        table.getColumns().add(column("Regex", "regex", 70));
        table.getColumns().add(column("Condition", "conditionPattern", 180));
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, rule) -> load(rule));

        Button add = new Button("New");
        add.setOnAction(event -> clearForm());
        Button save = new Button("Save");
        save.setOnAction(event -> {
            MatchReplaceRule rule = selected == null
                    ? new MatchReplaceRule(true, "Request", "Body", "", "", false, "", "", "")
                    : selected;
            writeForm(rule);
            engine.save(rule);
            refresh();
            table.getSelectionModel().select(rule);
            status.setText("Saved rule");
        });
        Button delete = new Button("Delete");
        delete.setOnAction(event -> {
            MatchReplaceRule rule = table.getSelectionModel().getSelectedItem();
            if (rule != null) {
                engine.delete(rule);
                clearForm();
                refresh();
                status.setText("Deleted rule");
            }
        });
        Button importRules = new Button("Import");
        importRules.setOnAction(event -> importRules());
        Button exportRules = new Button("Export");
        exportRules.setOnAction(event -> exportRules());

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.add(enabled, 0, 0);
        form.add(new Label("Phase"), 0, 1);
        form.add(phase, 1, 1);
        form.add(new Label("Target"), 0, 2);
        form.add(target, 1, 2);
        form.add(new Label("Pattern"), 0, 3);
        form.add(pattern, 1, 3);
        form.add(new Label("Replacement"), 0, 4);
        form.add(replacement, 1, 4);
        form.add(regex, 1, 5);
        form.add(new Label("Condition Field"), 0, 6);
        form.add(conditionField, 1, 6);
        form.add(new Label("Condition Pattern"), 0, 7);
        form.add(conditionPattern, 1, 7);
        form.add(notes, 0, 8, 2, 1);
        form.add(new HBox(8, add, save, delete, importRules, exportRules), 1, 9);

        VBox root = new VBox(10, table, form, status);
        VBox.setVgrow(table, Priority.ALWAYS);
        root.setPadding(new Insets(12));
        setContent(root);
    }

    private TableColumn<MatchReplaceRule, Object> column(String title, String property, int width) {
        TableColumn<MatchReplaceRule, Object> column = new TableColumn<>(title);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        column.setPrefWidth(width);
        return column;
    }

    private void load(MatchReplaceRule rule) {
        selected = rule;
        if (rule == null) {
            clearForm();
            return;
        }
        enabled.setSelected(rule.isEnabled());
        phase.getSelectionModel().select(rule.getPhase());
        target.getSelectionModel().select(rule.getTarget());
        pattern.setText(rule.getPattern());
        replacement.setText(rule.getReplacement());
        regex.setSelected(rule.isRegex());
        conditionField.setText(rule.getConditionField());
        conditionPattern.setText(rule.getConditionPattern());
        notes.setText(rule.getNotes());
    }

    private void writeForm(MatchReplaceRule rule) {
        rule.setEnabled(enabled.isSelected());
        rule.setPhase(phase.getSelectionModel().getSelectedItem());
        rule.setTarget(target.getSelectionModel().getSelectedItem());
        rule.setPattern(pattern.getText());
        rule.setReplacement(replacement.getText());
        rule.setRegex(regex.isSelected());
        rule.setConditionField(conditionField.getText());
        rule.setConditionPattern(conditionPattern.getText());
        rule.setNotes(notes.getText());
    }

    private void clearForm() {
        selected = null;
        enabled.setSelected(true);
        phase.getSelectionModel().select("Request");
        target.getSelectionModel().select("Body");
        pattern.clear();
        replacement.clear();
        regex.setSelected(false);
        conditionField.clear();
        conditionPattern.clear();
        notes.clear();
    }

    private void refresh() {
        rules.setAll(engine.rules());
    }

    private void exportRules() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Match & Replace Rules");
        chooser.setInitialFileName("cyvorax-match-replace.tsv");
        java.io.File destination = chooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (destination == null) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (MatchReplaceRule rule : rules) {
            builder.append(rule.isEnabled()).append('\t')
                    .append(rule.getPhase()).append('\t')
                    .append(rule.getTarget()).append('\t')
                    .append(rule.isRegex()).append('\t')
                    .append(escape(rule.getPattern())).append('\t')
                    .append(escape(rule.getReplacement())).append('\t')
                    .append(escape(rule.getConditionField())).append('\t')
                    .append(escape(rule.getConditionPattern())).append('\t')
                    .append(escape(rule.getNotes())).append('\n');
        }
        try {
            Files.writeString(Path.of(destination.toURI()), builder.toString());
            status.setText("Exported " + rules.size() + " rules");
        } catch (Exception ex) {
            status.setText("Export failed: " + ex.getMessage());
        }
    }

    private void importRules() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Match & Replace Rules");
        java.io.File source = chooser.showOpenDialog(getTabPane().getScene().getWindow());
        if (source == null) {
            return;
        }
        try {
            for (String line : Files.readAllLines(Path.of(source.toURI()))) {
                String[] parts = line.split("\t", -1);
                if (parts.length >= 9) {
                    engine.save(new MatchReplaceRule(
                            Boolean.parseBoolean(parts[0]),
                            parts[1],
                            parts[2],
                            unescape(parts[4]),
                            unescape(parts[5]),
                            Boolean.parseBoolean(parts[3]),
                            unescape(parts[6]),
                            unescape(parts[7]),
                            unescape(parts[8])
                    ));
                }
            }
            refresh();
            status.setText("Imported rules from " + source.getName());
        } catch (Exception ex) {
            status.setText("Import failed: " + ex.getMessage());
        }
    }

    private String escape(String value) {
        return (value == null ? "" : value).replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
    }

    private String unescape(String value) {
        return value.replace("\\r", "\r").replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\");
    }
}
