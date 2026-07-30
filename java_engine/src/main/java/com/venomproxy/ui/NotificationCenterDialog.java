package com.venomproxy.ui;

import com.venomproxy.model.NotificationEntry;
import com.venomproxy.notifications.NotificationService;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.time.format.DateTimeFormatter;

public class NotificationCenterDialog {
    private final NotificationService notificationService;

    public NotificationCenterDialog(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void show(Window owner) {
        Stage stage = new Stage();
        stage.setTitle("Notification Center");
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        }
        ObservableList<NotificationEntry> notifications = notificationService.notifications();
        TableView<NotificationEntry> table = new TableView<>(notifications);
        table.setPlaceholder(UiUtil.emptyState("No notifications", "Workflow events appear here and are saved with the workspace.", null, null));
        table.getColumns().add(column("Time", entry -> DateTimeFormatter.ISO_INSTANT.format(entry.getTimestamp()), 180));
        table.getColumns().add(column("Type", NotificationEntry::getType, 130));
        table.getColumns().add(column("Title", NotificationEntry::getTitle, 260));
        table.getColumns().add(column("State", entry -> entry.isRead() ? "Read" : "Unread", 90));

        TextArea detail = UiUtil.codeArea("Notification detail");
        detail.setEditable(false);
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, entry) -> {
            if (entry != null) {
                detail.setText(entry.getTitle() + "\n\n" + entry.getMessage());
                notificationService.markRead(entry);
                table.refresh();
            }
        });
        Button markAll = new Button("Mark All Read");
        markAll.setOnAction(event -> {
            notificationService.markAllRead();
            table.refresh();
        });
        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> {
            notificationService.refresh();
            table.refresh();
        });
        HBox actions = new HBox(8, markAll, refresh);
        SplitPane split = new SplitPane(table, detail);
        split.setDividerPositions(0.62);
        VBox root = new VBox(10, new Label("Notification Center"), actions, split);
        VBox.setVgrow(split, Priority.ALWAYS);
        root.setPadding(new Insets(14));
        stage.setScene(new Scene(root, 980, 620));
        stage.show();
    }

    private TableColumn<NotificationEntry, String> column(String title,
                                                         java.util.function.Function<NotificationEntry, String> mapper,
                                                         int width) {
        TableColumn<NotificationEntry, String> column = new TableColumn<>(title);
        column.setCellValueFactory(value -> new ReadOnlyStringWrapper(mapper.apply(value.getValue())));
        column.setPrefWidth(width);
        return column;
    }
}
