package com.venomproxy.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.stage.Window;

public final class ShortcutHelpDialog {
    private ShortcutHelpDialog() {
    }

    public static void show(Window owner) {
        TextArea text = UiUtil.codeArea("Keyboard shortcuts");
        text.setEditable(false);
        text.setText("""
                Ctrl+K        Quick Search
                Ctrl+Shift+P  Command Palette
                Ctrl+R        Open Repeater
                Ctrl+I        Open Intruder
                Ctrl+S        Save Workspace
                Ctrl+Shift+F  Global Search
                Ctrl+,        Settings
                Ctrl+1-9      Switch modules
                """);
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Keyboard Shortcuts");
        alert.setHeaderText("CyvoraX Suite Shortcuts");
        alert.getDialogPane().setContent(text);
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.showAndWait();
    }
}
