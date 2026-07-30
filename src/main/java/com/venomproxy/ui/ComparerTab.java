package com.venomproxy.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
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
        TextArea left = UiUtil.codeArea("Left text, request, or response");
        TextArea right = UiUtil.codeArea("Right text, request, or response");
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
        ComboBox<String> mode = new ComboBox<>(FXCollections.observableArrayList("Line Diff", "Word Diff", "Character Diff", "Request Diff", "Response Diff"));
        mode.getSelectionModel().select("Line Diff");
        Label status = new Label("Ready");
        Button compare = new Button("Compare");
        compare.setOnAction(event -> {
            diffLines.setAll(diff(mode.getValue(), left.getText(), right.getText()));
            long changes = diffLines.stream().filter(line -> line.startsWith("+ ") || line.startsWith("- ") || line.startsWith("~ ")).count();
            status.setText(changes + " changed lines");
        });
        SplitPane top = new SplitPane(left, right);
        top.setDividerPositions(0.5);
        SplitPane rootSplit = new SplitPane(top, diff);
        rootSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
        rootSplit.setDividerPositions(0.65);
        VBox root = new VBox(8, new HBox(8, mode, compare, status), rootSplit);
        VBox.setVgrow(rootSplit, Priority.ALWAYS);
        root.setPadding(new Insets(12));
        setContent(root);
    }

    private ObservableList<String> diff(String mode, String left, String right) {
        return switch (mode) {
            case "Character Diff" -> characterDiff(left, right);
            case "Word Diff" -> wordDiff(left, right);
            case "Request Diff" -> lineDiff(normalizeRequest(left), normalizeRequest(right));
            case "Response Diff" -> lineDiff(normalizeResponse(left), normalizeResponse(right));
            default -> lineDiff(left, right);
        };
    }

    private ObservableList<String> lineDiff(String left, String right) {
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

    private ObservableList<String> characterDiff(String left, String right) {
        ObservableList<String> lines = FXCollections.observableArrayList();
        int max = Math.max(left.length(), right.length());
        for (int i = 0; i < max; i++) {
            String a = i < left.length() ? printable(left.charAt(i)) : "";
            String b = i < right.length() ? printable(right.charAt(i)) : "";
            if (a.equals(b)) {
                lines.add("  [" + i + "] " + a);
            } else if (!a.isEmpty() && !b.isEmpty()) {
                lines.add("~ [" + i + "] " + a + " -> " + b);
            } else if (!a.isEmpty()) {
                lines.add("- [" + i + "] " + a);
            } else {
                lines.add("+ [" + i + "] " + b);
            }
        }
        return lines;
    }

    private ObservableList<String> wordDiff(String left, String right) {
        ObservableList<String> lines = FXCollections.observableArrayList();
        String[] a = left.split("\\s+", -1);
        String[] b = right.split("\\s+", -1);
        int max = Math.max(a.length, b.length);
        for (int i = 0; i < max; i++) {
            String leftWord = i < a.length ? a[i] : "";
            String rightWord = i < b.length ? b[i] : "";
            if (leftWord.equals(rightWord)) {
                lines.add("  " + leftWord);
            } else if (!leftWord.isEmpty() && !rightWord.isEmpty()) {
                lines.add("~ " + leftWord + " -> " + rightWord);
            } else if (!leftWord.isEmpty()) {
                lines.add("- " + leftWord);
            } else {
                lines.add("+ " + rightWord);
            }
        }
        return lines;
    }

    private String normalizeRequest(String raw) {
        return raw.replace("\r\n", "\n").replaceAll("(?m)^Content-Length:.*\\n?", "");
    }

    private String normalizeResponse(String raw) {
        return raw.replace("\r\n", "\n").replaceAll("(?m)^Date:.*\\n?", "").replaceAll("(?m)^Content-Length:.*\\n?", "");
    }

    private String printable(char ch) {
        return switch (ch) {
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            default -> String.valueOf(ch);
        };
    }
}
