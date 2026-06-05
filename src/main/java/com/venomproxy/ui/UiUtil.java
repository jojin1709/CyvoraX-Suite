package com.venomproxy.ui;

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
