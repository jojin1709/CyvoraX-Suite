package com.venomproxy.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class UiUtil {
    private UiUtil() {
    }

    public static TextArea codeArea(String prompt) {
        TextArea area = new TextArea();
        area.setPromptText(prompt);
        area.getStyleClass().add("code-area");
        area.setWrapText(false);
        VBox.setVgrow(area, Priority.ALWAYS);
        return area;
    }

    public static Node emptyState(String title, String detail, String actionLabel, Runnable action) {
        Label mark = new Label("[ ]");
        mark.getStyleClass().add("empty-state-mark");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("empty-state-title");
        Label detailLabel = new Label(detail);
        detailLabel.getStyleClass().add("empty-state-detail");
        detailLabel.setWrapText(true);
        VBox box = new VBox(8, mark, titleLabel, detailLabel);
        box.getStyleClass().add("empty-state");
        box.setAlignment(Pos.CENTER);
        if (actionLabel != null && !actionLabel.isBlank() && action != null) {
            Button button = new Button(actionLabel);
            button.setOnAction(event -> action.run());
            box.getChildren().add(button);
        }
        return box;
    }

    public static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0 && i % 16 == 0) {
                builder.append('\n');
            }
            builder.append(String.format("%02x ", bytes[i]));
        }
        return builder.toString();
    }
}
