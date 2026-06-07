package com.venomproxy.ui;

import com.venomproxy.model.Finding;
import com.venomproxy.model.HttpTransaction;
import com.venomproxy.notifications.NotificationService;
import com.venomproxy.util.ReportExporter;
import com.venomproxy.util.ReportTemplate;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.nio.file.Path;

public class ReportTab extends Tab {
    private final ObservableList<Finding> findings;
    private final ObservableList<HttpTransaction> history;
    private final NotificationService notificationService;
    private final Label summary = new Label();
    private final Label status = new Label("Ready");
    private final ComboBox<ReportTemplate> template = new ComboBox<>();
    private final ComboBox<String> format = new ComboBox<>();

    public ReportTab(ObservableList<Finding> findings, ObservableList<HttpTransaction> history,
                     NotificationService notificationService) {
        super("Reports");
        setClosable(false);
        this.findings = findings;
        this.history = history;
        this.notificationService = notificationService;

        Button html = new Button("Export HTML");
        Button pdf = new Button("Export PDF");
        pdf.setOnAction(event -> export("PDF"));
        Button markdown = new Button("Export Markdown");
        markdown.setOnAction(event -> export("Markdown"));
        html.setOnAction(event -> export("HTML"));
        template.getItems().addAll(ReportTemplate.values());
        template.getSelectionModel().select(ReportTemplate.BUG_BOUNTY);
        format.getItems().addAll("HTML", "PDF", "Markdown");
        format.getSelectionModel().select("HTML");
        Button templated = new Button("Export Template");
        templated.setOnAction(event -> exportTemplate());
        findings.addListener((javafx.collections.ListChangeListener<Finding>) change -> refresh());
        history.addListener((javafx.collections.ListChangeListener<HttpTransaction>) change -> refresh());

        VBox root = new VBox(12, summary,
                new HBox(8, html, pdf, markdown),
                new HBox(8, new Label("Template"), template, new Label("Format"), format, templated, status));
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
            notificationService.publish("Report Exported", "Report exported", type + " report saved to " + destination.getName());
        } catch (Exception ex) {
            status.setText("Export failed: " + ex.getMessage());
        }
    }

    public void exportSelectedTemplate() {
        exportTemplate();
    }

    private void exportTemplate() {
        ReportTemplate selectedTemplate = template.getSelectionModel().getSelectedItem();
        String selectedFormat = format.getSelectionModel().getSelectedItem();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export " + selectedTemplate.displayName());
        chooser.setInitialFileName(defaultName(selectedTemplate, selectedFormat));
        java.io.File destination = chooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (destination == null) {
            return;
        }
        try {
            Path path = Path.of(destination.toURI());
            switch (selectedFormat) {
                case "PDF" -> ReportExporter.templatePdf(selectedTemplate, findings, history, path);
                case "Markdown" -> ReportExporter.templateMarkdown(selectedTemplate, findings, history, path);
                default -> ReportExporter.templateHtml(selectedTemplate, findings, history, path);
            }
            status.setText("Exported " + selectedTemplate.displayName());
            notificationService.publish("Report Exported", "Report template exported",
                    selectedTemplate.displayName() + " saved to " + destination.getName());
        } catch (Exception ex) {
            status.setText("Template export failed: " + ex.getMessage());
        }
    }

    private String defaultName(ReportTemplate selectedTemplate, String selectedFormat) {
        String base = selectedTemplate.displayName().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return switch (selectedFormat) {
            case "PDF" -> base + ".pdf";
            case "Markdown" -> base + ".md";
            default -> base + ".html";
        };
    }
}
