package com.venomproxy.ui;

import com.venomproxy.db.Database;
import com.venomproxy.model.HttpTransaction;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableRow;
import javafx.scene.control.TextInputDialog;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

public final class RequestAnnotationActions {
    public static final List<String> HIGHLIGHT_COLORS = List.of("Red", "Blue", "Green", "Yellow", "Purple");

    private RequestAnnotationActions() {
    }

    public static MenuItem addNote(Supplier<HttpTransaction> selected, Database database, Runnable afterSave) {
        MenuItem item = new MenuItem("Add Note");
        item.setOnAction(event -> editNote(selected, database, afterSave, false));
        return item;
    }

    public static MenuItem editNote(Supplier<HttpTransaction> selected, Database database, Runnable afterSave) {
        MenuItem item = new MenuItem("Edit Note");
        item.setOnAction(event -> editNote(selected, database, afterSave, true));
        return item;
    }

    public static MenuItem deleteNote(Supplier<HttpTransaction> selected, Database database, Runnable afterSave) {
        MenuItem item = new MenuItem("Delete Note");
        item.setOnAction(event -> {
            HttpTransaction tx = selected.get();
            if (tx == null) {
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Delete Note");
            confirm.setHeaderText("Delete note for request #" + tx.getId() + "?");
            confirm.setContentText(tx.getUrl());
            Optional<ButtonType> response = confirm.showAndWait();
            if (response.isPresent() && response.get() == ButtonType.OK) {
                tx.setNotes("");
                database.updateTransactionAnnotations(tx);
                afterSave.run();
            }
        });
        return item;
    }

    public static Menu highlightMenu(Supplier<HttpTransaction> selected, Database database, Runnable afterSave) {
        Menu menu = new Menu("Highlight");
        for (String color : HIGHLIGHT_COLORS) {
            MenuItem item = new MenuItem(color);
            item.setOnAction(event -> {
                HttpTransaction tx = selected.get();
                if (tx != null) {
                    tx.setColorLabel(color);
                    database.updateTransactionAnnotations(tx);
                    afterSave.run();
                }
            });
            menu.getItems().add(item);
        }
        MenuItem clear = new MenuItem("Clear Highlight");
        clear.setOnAction(event -> {
            HttpTransaction tx = selected.get();
            if (tx != null) {
                tx.setColorLabel("");
                database.updateTransactionAnnotations(tx);
                afterSave.run();
            }
        });
        menu.getItems().add(clear);
        return menu;
    }

    public static void applyHighlightStyle(TableRow<?> row, String color) {
        if (row.isEmpty()) {
            row.setStyle("");
            return;
        }
        row.setStyle(switch (normalizeColor(color).toLowerCase(Locale.ROOT)) {
            case "red" -> "-fx-background-color: rgba(239, 68, 68, 0.18);";
            case "blue" -> "-fx-background-color: rgba(59, 130, 246, 0.18);";
            case "green" -> "-fx-background-color: rgba(34, 197, 94, 0.18);";
            case "yellow" -> "-fx-background-color: rgba(234, 179, 8, 0.20);";
            case "purple" -> "-fx-background-color: rgba(168, 85, 247, 0.18);";
            default -> "";
        });
    }

    public static String normalizeColor(String color) {
        if (color == null || color.isBlank() || "None".equalsIgnoreCase(color)) {
            return "";
        }
        for (String allowed : HIGHLIGHT_COLORS) {
            if (allowed.equalsIgnoreCase(color.trim())) {
                return allowed;
            }
        }
        return "";
    }

    private static void editNote(Supplier<HttpTransaction> selected, Database database, Runnable afterSave, boolean allowExisting) {
        HttpTransaction tx = selected.get();
        if (tx == null) {
            return;
        }
        TextInputDialog dialog = new TextInputDialog(allowExisting ? tx.getNotes() : "");
        dialog.setTitle(allowExisting ? "Edit Note" : "Add Note");
        dialog.setHeaderText((allowExisting ? "Edit note for " : "Add note to ") + "request #" + tx.getId());
        dialog.setContentText(tx.getUrl());
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(note -> {
            tx.setNotes(note);
            database.updateTransactionAnnotations(tx);
            afterSave.run();
        });
    }
}
