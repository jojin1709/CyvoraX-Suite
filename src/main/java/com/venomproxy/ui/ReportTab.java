package com.venomproxy.ui;

import com.venomproxy.model.Finding;
import com.venomproxy.model.HttpTransaction;
import com.venomproxy.util.ReportExporter;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.nio.file.Path;

public class ReportTab extends Tab {
    private final ObservableList<Finding> findings;
    private final ObservableList<HttpTransaction> history;
    private final Label summary = new Label();
    private final Label status = new Label("Ready");

    public ReportTab(ObservableList<Finding> findings, ObservableList<HttpTransaction> history) {
        super("Reports");
        setClosable(false);
        this.findings = findings;
        this.history = history;

        Button html = new Button("Export HTML");
        Button pdf = new Button("Export PDF");
        pdf.setOnAction(event -> export("PDF"));
        Button markdown = new Button("Export Markdown");
        markdown.setOnAction(event -> export("Markdown"));
        html.setOnAction(event -> export("HTML"));
        findings.addListener((javafx.collections.ListChangeListener<Finding>) change -> refresh());
        history.addListener((javafx.collections.ListChangeListener<HttpTransaction>) change -> refresh());

        VBox root = new VBox(12, summary, new HBox(8, html, pdf, markdown, status));
        root.setPadding(new Insets(16));
        setContent(root);
        refresh();
    }

    private void refresh() {
        long annotated = history.stream()
                .filter(tx -> tx.isFavorite() || !tx.getNotes().isBlank() || !tx.getComments().isBlank() || !tx.getTags().isBlank())
                .count();
        summary.setText("Findings: " + findings.size() + " | Annotated requests: " + annotated);
    }

    private void export(String type) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export " + type + " Report");
        chooser.setInitialFileName(switch (type) {
            case "PDF" -> "cyvorax-report.pdf";
            case "Markdown" -> "cyvorax-report.md";
            default -> "cyvorax-report.html";
        });
        java.io.File destination = chooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (destination == null) {
            return;
        }
        try {
            switch (type) {
                case "PDF" -> ReportExporter.pdf(findings, history, Path.of(destination.toURI()));
                case "Markdown" -> ReportExporter.markdown(findings, history, Path.of(destination.toURI()));
                default -> ReportExporter.html(findings, history, Path.of(destination.toURI()));
            }
            status.setText("Exported " + type + " report");
        } catch (Exception ex) {
            status.setText("Export failed: " + ex.getMessage());
        }
    }
}
