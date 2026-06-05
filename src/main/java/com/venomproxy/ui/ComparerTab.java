package com.venomproxy.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class ComparerTab extends Tab {
    public ComparerTab() {
        super("Comparer");
        setClosable(false);
        TextArea left = UiUtil.codeArea("Left text");
        TextArea right = UiUtil.codeArea("Right text");
        ObservableList<String> diffLines = FXCollections.observableArrayList();
        ListView<String> diff = new ListView<>(diffLines);
        diff.getStyleClass().add("diff-view");
        diff.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                getStyleClass().removeAll("diff-added", "diff-removed", "diff-changed");
                if (!empty && item != null) {
                    if (item.startsWith("+ ")) {
                        getStyleClass().add("diff-added");
                    } else if (item.startsWith("- ")) {
                        getStyleClass().add("diff-removed");
                    } else if (item.startsWith("~ ")) {
                        getStyleClass().add("diff-changed");
                    }
                }
            }
        });
        Button compare = new Button("Compare");
        compare.setOnAction(event -> diffLines.setAll(diff(left.getText(), right.getText())));
        SplitPane top = new SplitPane(left, right);
        top.setDividerPositions(0.5);
        SplitPane rootSplit = new SplitPane(top, diff);
        rootSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
        rootSplit.setDividerPositions(0.65);
        VBox root = new VBox(8, new HBox(8, compare), rootSplit);
        VBox.setVgrow(rootSplit, Priority.ALWAYS);
        root.setPadding(new Insets(12));
        setContent(root);
    }

    private ObservableList<String> diff(String left, String right) {
        ObservableList<String> lines = FXCollections.observableArrayList();
        String[] a = left.split("\\R", -1);
        String[] b = right.split("\\R", -1);
        int max = Math.max(a.length, b.length);
        for (int i = 0; i < max; i++) {
            String leftLine = i < a.length ? a[i] : "";
            String rightLine = i < b.length ? b[i] : "";
            if (leftLine.equals(rightLine)) {
                lines.add("  " + leftLine);
            } else if (!leftLine.isEmpty() && !rightLine.isEmpty()) {
                lines.add("~ - " + leftLine);
                lines.add("~ + " + rightLine);
            } else {
                if (!leftLine.isEmpty()) {
                    lines.add("- " + leftLine);
                }
                if (!rightLine.isEmpty()) {
                    lines.add("+ " + rightLine);
                }
            }
        }
        return lines;
    }
}
