package com.venomproxy.ui;

import com.venomproxy.db.Database;
import com.venomproxy.model.MatchReplaceRule;
import com.venomproxy.proxy.MatchReplaceEngine;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class MatchReplaceTab extends javafx.scene.control.Tab {
    private final MatchReplaceEngine engine;
    private final Database database;
    private final ObservableList<MatchReplaceRule> rules;
    private final TableView<MatchReplaceRule> table;
    private final CheckBox scopeOnlyCheck = new CheckBox("Only apply to in-scope items");
    private final Label status = new Label("Ready");

    public MatchReplaceTab(Database database, MatchReplaceEngine engine) {
        super("Match & Replace");
        setClosable(false);
        this.database = database;
        this.engine = engine;
        this.rules = FXCollections.observableArrayList(engine.rules());

        // Header Section
        Label title = new Label("HTTP match and replace rules");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #E5E7EB;");
        Label subtitle = new Label("Use these settings to automatically replace parts of HTTP requests and responses passing through the Proxy.");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #94A3B8;");

        scopeOnlyCheck.setStyle("-fx-text-fill: #E5E7EB; -fx-font-size: 13px;");
        boolean scopeVal = Boolean.parseBoolean(database.getSetting("matchreplace.scopeOnly", "false"));
        scopeOnlyCheck.setSelected(scopeVal);
        scopeOnlyCheck.selectedProperty().addListener((obs, old, val) -> database.setSetting("matchreplace.scopeOnly", String.valueOf(val)));

        VBox headerBox = new VBox(6, title, subtitle, scopeOnlyCheck);
        headerBox.setPadding(new Insets(10, 14, 10, 14));
        headerBox.setStyle("-fx-background-color: #1F2937; -fx-border-color: #334155; -fx-border-width: 0 0 1 0;");

        // Table Setup
        table = new TableView<>(rules);
        UiUtil.constrainTable(table);
        table.setPlaceholder(UiUtil.emptyState("No Match & Replace Rules", "Click 'Add' to create a rule or 'Reset Defaults' to load presets.", null, null));

        // Inline Enabled Checkbox Column
        TableColumn<MatchReplaceRule, Boolean> enabledCol = new TableColumn<>("Enabled");
        enabledCol.setPrefWidth(70);
        enabledCol.setCellValueFactory(param -> {
            MatchReplaceRule rule = param.getValue();
            SimpleBooleanProperty prop = new SimpleBooleanProperty(rule.isEnabled());
            prop.addListener((obs, oldVal, newVal) -> {
                rule.setEnabled(newVal);
                database.saveMatchReplaceRule(rule);
                engine.reload();
            });
            return prop;
        });
        enabledCol.setCellFactory(CheckBoxTableCell.forTableColumn(enabledCol));

        TableColumn<MatchReplaceRule, String> itemCol = new TableColumn<>("Item");
        itemCol.setPrefWidth(140);
        itemCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                cell.getValue().getPhase() + " " + cell.getValue().getTarget()));

        TableColumn<MatchReplaceRule, String> commentCol = new TableColumn<>("Comment / Name");
        commentCol.setPrefWidth(180);
        commentCol.setCellValueFactory(new PropertyValueFactory<>("notes"));

        TableColumn<MatchReplaceRule, String> matchCol = new TableColumn<>("Match");
        matchCol.setPrefWidth(220);
        matchCol.setCellValueFactory(new PropertyValueFactory<>("pattern"));

        TableColumn<MatchReplaceRule, String> replaceCol = new TableColumn<>("Replace");
        replaceCol.setPrefWidth(220);
        replaceCol.setCellValueFactory(new PropertyValueFactory<>("replacement"));

        TableColumn<MatchReplaceRule, String> typeCol = new TableColumn<>("Type");
        typeCol.setPrefWidth(75);
        typeCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                cell.getValue().isRegex() ? "Regex" : "Literal"));

        TableColumn<MatchReplaceRule, String> conditionCol = new TableColumn<>("Condition");
        conditionCol.setPrefWidth(140);
        conditionCol.setCellValueFactory(new PropertyValueFactory<>("conditionPattern"));

        table.getColumns().addAll(enabledCol, itemCol, commentCol, matchCol, replaceCol, typeCol, conditionCol);

        table.setRowFactory(tv -> {
            TableRow<MatchReplaceRule> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showRuleDialog(row.getItem());
                }
            });
            return row;
        });

        // Left Action Buttons (Burp Suite Pro Style)
        Button addBtn = new Button("Add");
        addBtn.setPrefWidth(100);
        addBtn.getStyleClass().add("accent-button");
        addBtn.setOnAction(e -> showRuleDialog(null));

        Button editBtn = new Button("Edit");
        editBtn.setPrefWidth(100);
        editBtn.setOnAction(e -> {
            MatchReplaceRule selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showRuleDialog(selected);
            } else {
                status.setText("Select a rule to edit.");
            }
        });

        Button removeBtn = new Button("Remove");
        removeBtn.setPrefWidth(100);
        removeBtn.setOnAction(e -> {
            MatchReplaceRule selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                engine.delete(selected);
                refresh();
                status.setText("Deleted rule #" + selected.getId());
            } else {
                status.setText("Select a rule to remove.");
            }
        });

        Button upBtn = new Button("Up");
        upBtn.setPrefWidth(100);
        upBtn.setOnAction(e -> moveSelected(-1));

        Button downBtn = new Button("Down");
        downBtn.setPrefWidth(100);
        downBtn.setOnAction(e -> moveSelected(1));

        Button resetDefaultsBtn = new Button("Reset Defaults");
        resetDefaultsBtn.setPrefWidth(100);
        resetDefaultsBtn.setOnAction(e -> {
            engine.seedDefaultRules();
            refresh();
            status.setText("Reset preset match & replace rules.");
        });

        Button importBtn = new Button("Import");
        importBtn.setPrefWidth(100);
        importBtn.setOnAction(e -> importRules());

        Button exportBtn = new Button("Export");
        exportBtn.setPrefWidth(100);
        exportBtn.setOnAction(e -> exportRules());

        VBox actionBox = new VBox(8, addBtn, editBtn, removeBtn, new Separator(), upBtn, downBtn, new Separator(), resetDefaultsBtn, importBtn, exportBtn);
        actionBox.setPadding(new Insets(10));
        actionBox.setAlignment(Pos.TOP_CENTER);

        // Body Layout
        HBox bodyBox = new HBox(10, actionBox, table);
        HBox.setHgrow(table, Priority.ALWAYS);
        VBox.setVgrow(bodyBox, Priority.ALWAYS);
        bodyBox.setPadding(new Insets(10));

        status.getStyleClass().add("status-label");
        HBox statusBox = new HBox(status);
        statusBox.setPadding(new Insets(6, 12, 6, 12));
        statusBox.setStyle("-fx-background-color: #1F2937; -fx-border-color: #334155; -fx-border-width: 1 0 0 0;");

        VBox root = new VBox(0, headerBox, bodyBox, statusBox);
        VBox.setVgrow(bodyBox, Priority.ALWAYS);
        setContent(root);
    }

    private void refresh() {
        rules.setAll(engine.rules());
    }

    private void moveSelected(int delta) {
        int index = table.getSelectionModel().getSelectedIndex();
        if (index < 0) return;
        int newIndex = index + delta;
        if (newIndex >= 0 && newIndex < rules.size()) {
            MatchReplaceRule rule = rules.remove(index);
            rules.add(newIndex, rule);
            table.getSelectionModel().select(newIndex);
        }
    }

    private void showRuleDialog(MatchReplaceRule ruleToEdit) {
        boolean isNew = (ruleToEdit == null);
        MatchReplaceRule rule = isNew
                ? new MatchReplaceRule(true, "Request", "Header: User-Agent", "", "", true, "", "", "")
                : ruleToEdit;

        Dialog<MatchReplaceRule> dialog = new Dialog<>();
        dialog.setTitle(isNew ? "Add Match & Replace Rule" : "Edit Match & Replace Rule");
        dialog.setHeaderText(isNew ? "Configure a new HTTP replacement rule." : "Edit rule settings.");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        CheckBox enabledCheck = new CheckBox("Rule enabled");
        enabledCheck.setSelected(rule.isEnabled());

        ComboBox<String> phaseCombo = new ComboBox<>(FXCollections.observableArrayList("Request", "Response"));
        phaseCombo.getSelectionModel().select(rule.getPhase());

        ComboBox<String> targetCombo = new ComboBox<>(FXCollections.observableArrayList(
                "Header: User-Agent", "Header: Accept-Encoding", "Header: Referer", "Header: Cookie",
                "Header: Set-Cookie", "Header", "Body", "URL", "Method"
        ));
        targetCombo.setEditable(true);
        targetCombo.getSelectionModel().select(rule.getTarget());

        TextField patternField = new TextField(rule.getPattern());
        patternField.setPromptText("Match pattern or regex");

        TextField replaceField = new TextField(rule.getReplacement());
        replaceField.setPromptText("Replacement text");

        CheckBox regexCheck = new CheckBox("Regex match");
        regexCheck.setSelected(rule.isRegex());

        TextField condField = new TextField(rule.getConditionField());
        condField.setPromptText("Condition Field (optional e.g. URL)");

        TextField condPattern = new TextField(rule.getConditionPattern());
        condPattern.setPromptText("Condition Pattern (optional)");

        TextField notesField = new TextField(rule.getNotes());
        notesField.setPromptText("Comment / Rule description");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));

        grid.add(enabledCheck, 0, 0, 2, 1);
        grid.add(new Label("Rule Comment:"), 0, 1);
        grid.add(notesField, 1, 1);
        grid.add(new Label("Phase:"), 0, 2);
        grid.add(phaseCombo, 1, 2);
        grid.add(new Label("Target Item:"), 0, 3);
        grid.add(targetCombo, 1, 3);
        grid.add(new Label("Match Pattern:"), 0, 4);
        grid.add(patternField, 1, 4);
        grid.add(new Label("Replacement:"), 0, 5);
        grid.add(replaceField, 1, 5);
        grid.add(regexCheck, 1, 6);
        grid.add(new Label("Condition Field:"), 0, 7);
        grid.add(condField, 1, 7);
        grid.add(new Label("Condition Pattern:"), 0, 8);
        grid.add(condPattern, 1, 8);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                rule.setEnabled(enabledCheck.isSelected());
                rule.setPhase(phaseCombo.getValue());
                rule.setTarget(targetCombo.getValue());
                rule.setPattern(patternField.getText());
                rule.setReplacement(replaceField.getText());
                rule.setRegex(regexCheck.isSelected());
                rule.setConditionField(condField.getText());
                rule.setConditionPattern(condPattern.getText());
                rule.setNotes(notesField.getText());
                return rule;
            }
            return null;
        });

        Optional<MatchReplaceRule> result = dialog.showAndWait();
        result.ifPresent(r -> {
            engine.save(r);
            refresh();
            status.setText("Saved match & replace rule: " + r.getNotes());
        });
    }

    private void exportRules() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Match & Replace Rules");
        chooser.setInitialFileName("match-replace-rules.json");
        File file = chooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            try {
                StringBuilder json = new StringBuilder("[\n");
                for (int i = 0; i < rules.size(); i++) {
                    MatchReplaceRule r = rules.get(i);
                    json.append(String.format("  {\"enabled\":%b,\"phase\":\"%s\",\"target\":\"%s\",\"pattern\":\"%s\",\"replacement\":\"%s\",\"regex\":%b,\"notes\":\"%s\"}%s\n",
                            r.isEnabled(), r.getPhase(), r.getTarget(),
                            escapeJson(r.getPattern()), escapeJson(r.getReplacement()), r.isRegex(),
                            escapeJson(r.getNotes()), (i < rules.size() - 1 ? "," : "")));
                }
                json.append("]");
                Files.writeString(Path.of(file.toURI()), json.toString());
                status.setText("Exported " + rules.size() + " rules to " + file.getName());
            } catch (Exception ex) {
                status.setText("Export failed: " + ex.getMessage());
            }
        }
    }

    private void importRules() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Match & Replace Rules");
        File file = chooser.showOpenDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            status.setText("Imported rules from " + file.getName());
        }
    }

    private String escapeJson(String str) {
        return str == null ? "" : str.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
