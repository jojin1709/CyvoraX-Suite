package com.venomproxy.ui;

import com.venomproxy.update.GitHubReleaseClient;
import com.venomproxy.update.UpdateInfo;
import com.venomproxy.update.UpdateService;
import com.venomproxy.util.SecretMasker;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class UpdateDialog {
    private final UpdateService updateService;
    private final UpdateInfo updateInfo;
    private final Label status = new Label("Ready");
    private final Label speed = new Label("Speed: --");
    private final Label transferred = new Label("Downloaded: --");
    private final Label eta = new Label("ETA: --");
    private final ProgressBar progress = new ProgressBar(0);
    private final Button openInstaller = new Button("Open Installer");
    private final Button openFolder = new Button("Open Download Folder");
    private Path downloadedInstaller;

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

        Label title = new Label(updateInfo.updateAvailable() ? "Update Available" : "CyvoraX Suite is up to date");
        title.setFont(Font.font("System", FontWeight.BOLD, 22));
        Label subtitle = new Label("Installer metadata is read directly from the GitHub release asset.");
        VBox headerText = new VBox(4, title, subtitle);

        GridPane summary = summaryGrid();
        HBox header = new HBox(24, headerText, summary);
        header.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(summary, Priority.ALWAYS);

        Label whatsNew = new Label("What's New");
        whatsNew.setFont(Font.font("System", FontWeight.BOLD, 15));
        ScrollPane notes = new ScrollPane(renderMarkdown(updateInfo.releaseNotes()));
        notes.setFitToWidth(true);
        notes.setPrefViewportHeight(260);
        notes.setMinHeight(180);

        GridPane diagnostics = diagnosticsGrid();
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setMinHeight(12);
        progress.setVisible(false);
        progress.setManaged(false);
        HBox downloadMetrics = new HBox(18, speed, transferred, eta);
        downloadMetrics.setAlignment(Pos.CENTER_LEFT);

        Button download = new Button("Download");
        download.setDisable(updateInfo.downloadUrl() == null || updateInfo.downloadUrl().isBlank());
        download.setOnAction(event -> download(download));
        Button ignore = new Button("Ignore Version");
        ignore.setOnAction(event -> {
            status.setText("Version ignored for this session.");
            stage.close();
        });
        Button remind = new Button("Remind Later");
        remind.setOnAction(event -> stage.close());
        openInstaller.setDisable(true);
        openInstaller.setOnAction(event -> openPath(downloadedInstaller));
        openFolder.setDisable(true);
        openFolder.setOnAction(event -> openPath(updateService.downloadDirectory()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(8, download, ignore, remind, spacer, openInstaller, openFolder);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(14, header, whatsNew, notes, diagnostics, progress, downloadMetrics, actions, status);
        VBox.setVgrow(notes, Priority.ALWAYS);
        root.setPadding(new Insets(18));
        root.setStyle("-fx-background-color: -fx-control-inner-background;");
        stage.setScene(new Scene(root, 820, 620));
        stage.show();
    }

    private GridPane summaryGrid() {
        GridPane grid = compactGrid();
        grid.add(new Label("Current version"), 0, 0);
        grid.add(value(updateInfo.currentVersion()), 1, 0);
        grid.add(new Label("Latest version"), 0, 1);
        grid.add(value(updateInfo.latestVersion()), 1, 1);
        grid.add(new Label("Release date"), 0, 2);
        grid.add(value(formatDate(updateInfo.releaseDate())), 1, 2);
        grid.add(new Label("Asset name"), 0, 3);
        grid.add(value(blankDefault(updateInfo.assetName(), "No installer asset found")), 1, 3);
        grid.add(new Label("Asset size"), 0, 4);
        grid.add(value(formatBytes(updateInfo.assetSizeBytes())), 1, 4);
        grid.add(new Label("SHA256"), 0, 5);
        grid.add(value(blankDefault(updateInfo.sha256(), "Not published")), 1, 5);
        return grid;
    }

    private GridPane diagnosticsGrid() {
        GridPane grid = compactGrid();
        grid.add(new Label("API URL used"), 0, 0);
        grid.add(value(blankDefault(updateInfo.releaseApiUrl(), "Unavailable")), 1, 0);
        grid.add(new Label("Asset URL used"), 0, 1);
        grid.add(value(blankDefault(updateInfo.downloadUrl(), "Unavailable")), 1, 1);
        grid.add(new Label("HTTP status"), 0, 2);
        grid.add(value("200"), 1, 2);
        grid.add(new Label("Asset count detected"), 0, 3);
        grid.add(value(String.valueOf(updateInfo.assetCount())), 1, 3);
        return grid;
    }

    private GridPane compactGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(6);
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(120);
        ColumnConstraints values = new ColumnConstraints();
        values.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, values);
        return grid;
    }

    private Label value(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        return label;
    }

    private TextFlow renderMarkdown(String markdown) {
        TextFlow flow = new TextFlow();
        flow.setLineSpacing(4);
        flow.setPadding(new Insets(12));
        String source = blankDefault(markdown, "No release notes were provided.");
        for (String line : source.split("\\R")) {
            String trimmed = line.trim();
            Text text;
            if (trimmed.startsWith("### ")) {
                text = new Text(trimmed.substring(4) + "\n");
                text.setFont(Font.font("System", FontWeight.BOLD, 15));
            } else if (trimmed.startsWith("## ")) {
                text = new Text(trimmed.substring(3) + "\n");
                text.setFont(Font.font("System", FontWeight.BOLD, 16));
            } else if (trimmed.startsWith("# ")) {
                text = new Text(trimmed.substring(2) + "\n");
                text.setFont(Font.font("System", FontWeight.BOLD, 18));
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                text = new Text("- " + inlineMarkdown(trimmed.substring(2)) + "\n");
            } else if (trimmed.isBlank()) {
                text = new Text("\n");
            } else {
                text = new Text(inlineMarkdown(trimmed) + "\n");
            }
            flow.getChildren().add(text);
        }
        return flow;
    }

    private String inlineMarkdown(String text) {
        return text.replace("**", "").replace("__", "").replace("`", "");
    }

    private void download(Button button) {
        button.setDisable(true);
        progress.setVisible(true);
        progress.setManaged(true);
        status.setText("Downloading " + updateInfo.assetName() + "...");
        Task<Path> task = new Task<>() {
            @Override
            protected Path call() throws Exception {
                return updateService.downloadInstallerWithProgress(updateInfo, update ->
                        Platform.runLater(() -> showProgress(update)));
            }
        };
        task.setOnSucceeded(event -> {
            downloadedInstaller = task.getValue();
            status.setText("Downloaded: " + downloadedInstaller);
            openInstaller.setDisable(false);
            openFolder.setDisable(false);
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

    private void showProgress(GitHubReleaseClient.DownloadProgress update) {
        progress.setProgress(update.progress() < 0 ? ProgressBar.INDETERMINATE_PROGRESS : update.progress());
        speed.setText("Speed: " + formatBytes(Math.round(update.bytesPerSecond())) + "/s");
        transferred.setText("Downloaded: " + formatBytes(update.downloadedBytes()) + " / " + formatBytes(update.totalBytes()));
        eta.setText("ETA: " + (update.etaSeconds() < 0 ? "--" : formatDuration(update.etaSeconds())));
    }

    private void openPath(Path path) {
        if (path == null) {
            return;
        }
        try {
            Path target = Files.isDirectory(path) ? path : path.toAbsolutePath();
            Desktop.getDesktop().open(target.toFile());
        } catch (IOException | UnsupportedOperationException ex) {
            status.setText("Could not open path: " + SecretMasker.maskSecrets(ex.getMessage()));
        }
    }

    private String formatDate(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }
        try {
            return OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z", Locale.ROOT));
        } catch (RuntimeException ex) {
            return value;
        }
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) {
            return "Unknown";
        }
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB"};
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return new DecimalFormat(value >= 10 ? "0.0" : "0.00").format(value) + " " + units[unit];
    }

    private String formatDuration(long seconds) {
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        if (minutes <= 0) {
            return remainingSeconds + "s";
        }
        return minutes + "m " + remainingSeconds + "s";
    }

    private String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
