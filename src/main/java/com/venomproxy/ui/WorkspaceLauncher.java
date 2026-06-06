package com.venomproxy.ui;

import com.venomproxy.workspace.WorkspaceInfo;
import com.venomproxy.workspace.WorkspaceManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.Consumer;

public class WorkspaceLauncher extends BorderPane {
    private final WorkspaceManager workspaceManager;
    private final ObservableList<WorkspaceInfo> workspaces = FXCollections.observableArrayList();
    private final TableView<WorkspaceInfo> table = new TableView<>(workspaces);

    public WorkspaceLauncher(String version, WorkspaceManager workspaceManager, Consumer<WorkspaceSelection> onOpen) {
        this.workspaceManager = workspaceManager;
        getStyleClass().add("workspace-launcher");

        Label title = new Label("CyvoraX Suite");
        title.getStyleClass().add("launcher-title");
        Label subtitle = new Label("Workspace Launcher");
        subtitle.getStyleClass().add("launcher-subtitle");
        Label versionLabel = new Label("Version " + version);
        versionLabel.getStyleClass().add("launcher-version");

        VBox heading = new VBox(4, title, subtitle, versionLabel);
        HBox brand = new HBox(14, logo(), heading);
        brand.setAlignment(Pos.CENTER_LEFT);

        table.getColumns().add(column("Workspace", "name", 220));
        table.getColumns().add(column("Last Opened", "lastOpenedAt", 210));
        table.getColumns().add(column("Location", "path", 420));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                openSelected(onOpen);
            }
        });

        Button temporary = new Button("Temporary");
        temporary.setOnAction(event -> {
            WorkspaceInfo workspace = workspaceManager.temporaryWorkspace();
            onOpen.accept(new WorkspaceSelection(workspace.getName(), workspace));
        });
        Button newWorkspace = new Button("New");
        newWorkspace.setOnAction(event -> createWorkspace());
        Button openWorkspace = new Button("Open Folder");
        openWorkspace.setOnAction(event -> openExisting(onOpen));
        Button openSelected = new Button("Open");
        openSelected.setOnAction(event -> openSelected(onOpen));
        Button rename = new Button("Rename");
        rename.setOnAction(event -> renameSelected());
        Button duplicate = new Button("Duplicate");
        duplicate.setOnAction(event -> duplicateSelected());
        Button delete = new Button("Delete");
        delete.setOnAction(event -> deleteSelected());

        HBox actions = new HBox(8, temporary, newWorkspace, openWorkspace, openSelected, rename, duplicate, delete);
        actions.setAlignment(Pos.CENTER_LEFT);

        Label recent = new Label("Recent Workspaces");
        recent.getStyleClass().add("launcher-action-title");
        Label profile = new Label("Workspace root: " + workspaceManager.workspacesDirectory());
        profile.getStyleClass().add("launcher-profile");

        VBox panel = new VBox(18, brand, recent, table, actions, profile);
        panel.getStyleClass().add("launcher-panel");
        panel.setPadding(new Insets(28));
        panel.setMaxWidth(980);
        VBox.setVgrow(table, Priority.ALWAYS);

        setCenter(panel);
        BorderPane.setAlignment(panel, Pos.CENTER);
        refresh();
        if (!workspaces.isEmpty()) {
            table.getSelectionModel().select(0);
        }
    }

    private TableColumn<WorkspaceInfo, Object> column(String title, String property, int width) {
        TableColumn<WorkspaceInfo, Object> column = new TableColumn<>(title);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        column.setPrefWidth(width);
        if ("lastOpenedAt".equals(property)) {
            column.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
                @Override
                protected void updateItem(Object item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : DateTimeFormatter.ISO_INSTANT.format((java.time.Instant) item));
                }
            });
        }
        return column;
    }

    private void createWorkspace() {
        TextInputDialog dialog = new TextInputDialog("New Assessment");
        dialog.setTitle("New Workspace");
        dialog.setHeaderText("Create a workspace");
        dialog.setContentText("Workspace name");
        dialog.showAndWait().ifPresent(name -> {
            WorkspaceInfo workspace = workspaceManager.createWorkspace(name);
            refresh();
            selectById(workspace.getId());
        });
    }

    private void openExisting(Consumer<WorkspaceSelection> onOpen) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Open CyvoraX Workspace");
        java.io.File selected = chooser.showDialog(getScene().getWindow());
        if (selected == null) {
            return;
        }
        WorkspaceInfo workspace = workspaceManager.openWorkspace(Path.of(selected.toURI()));
        refresh();
        selectById(workspace.getId());
        onOpen.accept(new WorkspaceSelection(workspace.getName(), workspace));
    }

    private void openSelected(Consumer<WorkspaceSelection> onOpen) {
        WorkspaceInfo selected = table.getSelectionModel().getSelectedItem();
        if (selected != null) {
            WorkspaceInfo opened = workspaceManager.openWorkspace(selected.getId());
            onOpen.accept(new WorkspaceSelection(opened.getName(), opened));
        }
    }

    private void renameSelected() {
        WorkspaceInfo selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        TextInputDialog dialog = new TextInputDialog(selected.getName());
        dialog.setTitle("Rename Workspace");
        dialog.setHeaderText("Rename " + selected.getName());
        dialog.setContentText("Workspace name");
        dialog.showAndWait().ifPresent(name -> {
            WorkspaceInfo renamed = workspaceManager.renameWorkspace(selected.getId(), name);
            refresh();
            selectById(renamed.getId());
        });
    }

    private void duplicateSelected() {
        WorkspaceInfo selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        TextInputDialog dialog = new TextInputDialog(selected.getName() + " Copy");
        dialog.setTitle("Duplicate Workspace");
        dialog.setHeaderText("Duplicate " + selected.getName());
        dialog.setContentText("New workspace name");
        dialog.showAndWait().ifPresent(name -> {
            WorkspaceInfo duplicate = workspaceManager.duplicateWorkspace(selected.getId(), name);
            refresh();
            selectById(duplicate.getId());
        });
    }

    private void deleteSelected() {
        WorkspaceInfo selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Workspace");
        confirm.setHeaderText("Delete workspace " + selected.getName() + "?");
        confirm.setContentText("The workspace folder will be moved to the CyvoraX workspace trash.");
        Optional<ButtonType> response = confirm.showAndWait();
        if (response.isPresent() && response.get() == ButtonType.OK) {
            workspaceManager.deleteWorkspace(selected.getId());
            refresh();
        }
    }

    private void refresh() {
        workspaces.setAll(workspaceManager.listWorkspaces());
        table.refresh();
    }

    private void selectById(String id) {
        for (WorkspaceInfo workspace : workspaces) {
            if (workspace.getId().equals(id)) {
                table.getSelectionModel().select(workspace);
                table.scrollTo(workspace);
                return;
            }
        }
    }

    private ImageView logo() {
        ImageView imageView = new ImageView();
        try (InputStream stream = getClass().getResourceAsStream("/icons/cyvorax-logo.png")) {
            if (stream != null) {
                imageView.setImage(new Image(stream));
            }
        } catch (Exception ignored) {
        }
        imageView.setFitWidth(58);
        imageView.setFitHeight(58);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.getStyleClass().add("launcher-logo");
        return imageView;
    }

    public record WorkspaceSelection(String name, WorkspaceInfo workspace) {
    }
}
