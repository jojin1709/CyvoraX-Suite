package com.venomproxy.ui;

import com.venomproxy.backup.BackupInfo;
import com.venomproxy.backup.BackupManager;
import com.venomproxy.notifications.NotificationService;
import com.venomproxy.workspace.WorkspaceInfo;
import com.venomproxy.workspace.WorkspaceManager;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public class BackupManagerDialog {
    private final BackupManager backupManager;
    private final WorkspaceManager workspaceManager;
    private final WorkspaceInfo activeWorkspace;
    private final NotificationService notificationService;
    private final Consumer<WorkspaceInfo> switchWorkspace;
    private final ObservableList<BackupInfo> backups = FXCollections.observableArrayList();

    public BackupManagerDialog(BackupManager backupManager, WorkspaceManager workspaceManager, WorkspaceInfo activeWorkspace,
                               NotificationService notificationService, Consumer<WorkspaceInfo> switchWorkspace) {
        this.backupManager = backupManager;
        this.workspaceManager = workspaceManager;
        this.activeWorkspace = activeWorkspace;
        this.notificationService = notificationService;
        this.switchWorkspace = switchWorkspace;
    }

    public void show(Window owner, String currentSchedule, Consumer<String> saveSchedule) {
        Stage stage = new Stage();
        stage.setTitle("Backup Manager");
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        }
        TableView<BackupInfo> table = new TableView<>(backups);
        table.setPlaceholder(UiUtil.emptyState("No backups", "Create a manual backup or enable scheduled backups.", null, null));
        table.getColumns().add(column("Created", backup -> DateTimeFormatter.ISO_INSTANT.format(backup.createdAt()), 180));
        table.getColumns().add(column("Workspace", BackupInfo::workspaceName, 220));
        table.getColumns().add(column("Size", backup -> humanBytes(backup.sizeBytes()), 100));
        table.getColumns().add(column("Location", backup -> backup.backupPath().toString(), 480));

        ComboBox<String> schedule = new ComboBox<>();
        schedule.getItems().addAll("Off", "Daily", "Weekly");
        schedule.getSelectionModel().select(currentSchedule == null || currentSchedule.isBlank() ? "Off" : currentSchedule);
        schedule.setOnAction(event -> saveSchedule.accept(schedule.getSelectionModel().getSelectedItem()));
        Label status = new Label("Backups: " + backupManager.backupsDirectory());
        Button manual = new Button("Manual Backup");
        manual.setOnAction(event -> {
            BackupInfo backup = backupManager.createBackup(activeWorkspace, "Manual");
            notificationService.publish("Backup Complete", "Backup complete", backup.workspaceName() + " saved to " + backup.backupPath());
            refresh();
            status.setText("Backup complete: " + backup.backupPath().getFileName());
        });
        Button restore = new Button("Restore Backup");
        restore.setOnAction(event -> {
            BackupInfo selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                status.setText("Select a backup to restore.");
                return;
            }
            TextInputDialog dialog = new TextInputDialog(selected.workspaceName() + " Restored");
            dialog.setTitle("Restore Backup");
            dialog.setHeaderText("Restore backup as a new workspace");
            dialog.setContentText("Workspace name");
            dialog.showAndWait().ifPresent(name -> {
                WorkspaceInfo restored = backupManager.restoreToNewWorkspace(selected, workspaceManager, name);
                notificationService.publish("Backup Complete", "Backup restored", restored.getName() + " restored from " + selected.id());
                status.setText("Restored workspace: " + restored.getName());
                switchWorkspace.accept(restored);
                stage.close();
            });
        });
        Button delete = new Button("Delete Backup");
        delete.setOnAction(event -> {
            BackupInfo selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                backupManager.deleteBackup(selected);
                refresh();
                status.setText("Deleted backup " + selected.id());
            }
        });
        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> refresh());

        HBox controls = new HBox(8, new Label("Schedule"), schedule, manual, restore, delete, refresh, status);
        VBox root = new VBox(10, controls, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        root.setPadding(new Insets(14));
        stage.setScene(new Scene(root, 1060, 620));
        refresh();
        stage.show();
    }

    private void refresh() {
        backups.setAll(backupManager.listBackups());
    }

    private TableColumn<BackupInfo, String> column(String title, java.util.function.Function<BackupInfo, String> mapper, int width) {
        TableColumn<BackupInfo, String> column = new TableColumn<>(title);
        column.setCellValueFactory(value -> new ReadOnlyStringWrapper(mapper.apply(value.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private String humanBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return "%.1f KB".formatted(bytes / 1024.0);
        }
        return "%.1f MB".formatted(bytes / (1024.0 * 1024.0));
    }
}
