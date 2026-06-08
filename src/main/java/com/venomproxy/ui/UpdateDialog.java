package com.venomproxy.ui;

import com.venomproxy.update.UpdateInfo;
import com.venomproxy.update.UpdateService;
import com.venomproxy.util.SecretMasker;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public class UpdateDialog {
    private final UpdateService updateService;
    private final UpdateInfo updateInfo;
    private final Label status = new Label("Ready");
    private final ProgressBar progress = new ProgressBar(0);

    public UpdateDialog(UpdateService updateService, UpdateInfo updateInfo) {
        this.updateService = updateService;
        this.updateInfo = updateInfo;
    }

    public void show(Window owner) {
        Stage stage = new Stage();
        stage.setTitle(updateInfo.updateAvailable() ? "Update Available" : "CyvoraX Updates");
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        }

        GridPane details = new GridPane();
        details.setHgap(10);
        details.setVgap(8);
        details.add(new Label("Current Version"), 0, 0);
        details.add(new Label(updateInfo.currentVersion()), 1, 0);
        details.add(new Label("Latest Version"), 0, 1);
        details.add(new Label(updateInfo.latestVersion()), 1, 1);
        details.add(new Label("Release"), 0, 2);
        details.add(new Label(blankDefault(updateInfo.releaseUrl(), "Unavailable")), 1, 2);
        details.add(new Label("Installer"), 0, 3);
        details.add(new Label(blankDefault(updateInfo.assetName(), "No installer asset found")), 1, 3);

        TextArea notes = UiUtil.codeArea("Release notes");
        notes.setEditable(false);
        notes.setText(blankDefault(updateInfo.releaseNotes(), "No release notes were provided."));

        Button download = new Button("Download Update");
        download.setDisable(updateInfo.downloadUrl() == null || updateInfo.downloadUrl().isBlank());
        download.setOnAction(event -> download(download));
        Button close = new Button("Close");
        close.setOnAction(event -> stage.close());
        HBox actions = new HBox(8, download, close, status);
        progress.setMaxWidth(Double.MAX_VALUE);

        VBox root = new VBox(12, new Label(updateInfo.updateAvailable()
                ? "A newer CyvoraX Suite installer is available."
                : "CyvoraX Suite is up to date."), details, notes, progress, actions);
        VBox.setVgrow(notes, Priority.ALWAYS);
        root.setPadding(new Insets(14));
        stage.setScene(new Scene(root, 720, 560));
        stage.show();
    }

    private void download(Button button) {
        button.setDisable(true);
        status.setText("Downloading...");
        Task<java.nio.file.Path> task = new Task<>() {
            @Override
            protected java.nio.file.Path call() throws Exception {
                return updateService.downloadInstaller(updateInfo, value -> Platform.runLater(() -> progress.setProgress(value)));
            }
        };
        task.setOnSucceeded(event -> {
            status.setText("Downloaded: " + task.getValue());
            button.setDisable(false);
        });
        task.setOnFailed(event -> {
            status.setText("Download failed: " + SecretMasker.maskSecrets(task.getException().getMessage()));
            button.setDisable(false);
        });
        Thread thread = new Thread(task, "cyvorax-update-download");
        thread.setDaemon(true);
        thread.start();
    }

    private String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
