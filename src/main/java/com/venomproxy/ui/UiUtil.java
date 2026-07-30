package com.venomproxy.ui;

import com.venomproxy.db.Database;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class UiUtil {
    private UiUtil() {
    }

    public static TextArea codeArea(String prompt) {
        TextArea area = new TextArea();
        area.setPromptText(prompt);
        area.getStyleClass().add("code-area");
        area.setWrapText(true);
        VBox.setVgrow(area, Priority.ALWAYS);
        return area;
    }

    public static <T, V> void addTooltipCellFactory(javafx.scene.control.TableColumn<T, V> column) {
        column.setCellFactory(col -> new javafx.scene.control.TableCell<T, V>() {
            private final javafx.scene.control.Tooltip tooltip = new javafx.scene.control.Tooltip();
            {
                tooltip.setWrapText(true);
                tooltip.setMaxWidth(600);
            }

            @Override
            protected void updateItem(V item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    String str = String.valueOf(item);
                    setText(str);
                    if (!str.isBlank()) {
                        tooltip.setText(str);
                        setTooltip(tooltip);
                    } else {
                        setTooltip(null);
                    }
                }
            }
        });
    }

    public static String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long remainingSecs = seconds % 60;
        if (minutes < 60) {
            return String.format(java.util.Locale.ROOT, "%dm %02ds", minutes, remainingSecs);
        }
        long hours = minutes / 60;
        long remainingMins = minutes % 60;
        return String.format(java.util.Locale.ROOT, "%dh %02dm %02ds", hours, remainingMins, remainingSecs);
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void constrainTable(TableView<?> table) {
        if (table != null) {
            ((TableView) table).setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        }
    }

    public static void bindDividerPositions(Database database, String key, SplitPane splitPane, double... defaults) {
        if (database == null || key == null || key.isBlank() || splitPane == null) {
            return;
        }
        Platform.runLater(() -> {
            double[] positions = parsePositions(database.getSetting(key, ""), defaults);
            if (positions.length > 0) {
                splitPane.setDividerPositions(positions);
            }
            splitPane.getDividers().forEach(divider -> divider.positionProperty().addListener((obs, old, value) ->
                    database.setSetting(key, Arrays.stream(splitPane.getDividerPositions())
                            .mapToObj(position -> String.format(java.util.Locale.ROOT, "%.4f", position))
                            .collect(Collectors.joining(",")))));
        });
    }

    private static double[] parsePositions(String saved, double[] defaults) {
        if (saved == null || saved.isBlank()) {
            return defaults == null ? new double[0] : defaults;
        }
        try {
            String[] parts = saved.split(",");
            double[] positions = new double[parts.length];
            for (int i = 0; i < parts.length; i++) {
                positions[i] = Math.max(0.05, Math.min(0.95, Double.parseDouble(parts[i].trim())));
            }
            return positions;
        } catch (RuntimeException ex) {
            return defaults == null ? new double[0] : defaults;
        }
    }
}
