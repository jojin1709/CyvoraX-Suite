package com.venomproxy.ui;

import com.venomproxy.diagnostics.CrashReport;
import com.venomproxy.diagnostics.CrashReporter;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
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
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

public class CrashReportViewerDialog {
    private final CrashReporter crashReporter;
    private final ObservableList<CrashReport> reports = FXCollections.observableArrayList();
    private final TextArea reportText = UiUtil.codeArea("Crash report details");
    private final Label status = new Label("Ready");

    public CrashReportViewerDialog(CrashReporter crashReporter) {
        this.crashReporter = crashReporter;
    }

    public void show(Window owner) {
        Stage stage = new Stage();
        stage.setTitle("CyvoraX Crash Reports");
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        }

        TableView<CrashReport> table = new TableView<>(reports);
        table.setPlaceholder(UiUtil.emptyState("No crash reports", "Crash reports will appear here after application failures.", null, null));
        table.getColumns().add(column("Timestamp", report -> DateTimeFormatter.ISO_INSTANT.format(report.timestamp()), 190));
        table.getColumns().add(column("Summary", CrashReport::summary, 380));
        table.getColumns().add(column("File", report -> report.path().getFileName().toString(), 260));
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, report) -> {
            reportText.setText(report == null ? "" : report.content());
        });

        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> loadReports());
        Button export = new Button("Export Selected");
        export.setOnAction(event -> exportSelected(owner, table.getSelectionModel().getSelectedItem()));
        Button openFolder = new Button("Open Folder");
        openFolder.setOnAction(event -> openReportsFolder());
        HBox actions = new HBox(8, refresh, export, openFolder, status);
        SplitPane split = new SplitPane(table, reportText);
        split.setDividerPositions(0.42);
        VBox root = new VBox(10, new Label("Crash Reports"), actions, split);
        VBox.setVgrow(split, Priority.ALWAYS);
        root.setPadding(new Insets(14));

        stage.setScene(new Scene(root, 1040, 680));
        loadReports();
        stage.show();
    }

    private void loadReports() {
        reports.setAll(crashReporter.listReports());
        status.setText(reports.size() + (reports.size() == 1 ? " report" : " reports"));
        if (!reports.isEmpty()) {
            reportText.setText(reports.get(0).content());
        }
    }

    private TableColumn<CrashReport, String> column(String title, java.util.function.Function<CrashReport, String> mapper, int width) {
        TableColumn<CrashReport, String> column = new TableColumn<>(title);
        column.setCellValueFactory(value -> new ReadOnlyStringWrapper(mapper.apply(value.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private void exportSelected(Window owner, CrashReport selected) {
        if (selected == null) {
            status.setText("Select a crash report to export.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Crash Report");
        chooser.setInitialFileName(selected.path().getFileName().toString());
        java.io.File destination = chooser.showSaveDialog(owner);
        if (destination == null) {
            return;
        }
        try {
            Files.writeString(Path.of(destination.toURI()), selected.content(), StandardCharsets.UTF_8);
            status.setText("Exported crash report");
        } catch (Exception ex) {
            status.setText("Export failed: " + ex.getMessage());
        }
    }

    private void openReportsFolder() {
        try {
            java.awt.Desktop.getDesktop().open(crashReporter.getReportsDirectory().toFile());
            status.setText("Crash report folder opened");
        } catch (Exception ex) {
            status.setText("Open folder failed: " + ex.getMessage());
        }
    }
}
