package com.venomproxy.util;

import com.venomproxy.model.Finding;
import com.venomproxy.model.HttpTransaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ReportExporter {
    private ReportExporter() {
    }

    public static void html(List<Finding> findings, List<HttpTransaction> history, Path path) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("""
                <!doctype html>
                <html><head><meta charset="utf-8">
                <title>CyvoraX Suite Report</title>
                <style>
                body{font-family:Arial,sans-serif;background:#0a0f1a;color:#ecfeff;line-height:1.45}
                section{border:1px solid #1e3a4a;border-radius:4px;margin:16px 0;padding:12px;background:#101927}
                pre{white-space:pre-wrap;background:#07101d;padding:10px;border-radius:4px;overflow:auto}
                .sev{font-weight:bold;color:#00f5c8}
                </style></head><body>
                <h1>CyvoraX Suite Security Report</h1>
                """);
        builder.append("<p><b>Findings:</b> ").append(findings.size())
                .append(" <b>Annotated Requests:</b> ").append(annotated(history).size()).append("</p>");
        builder.append("<h2>Findings</h2>");
        for (Finding finding : findings) {
            builder.append("<section><div class=\"sev\">")
                    .append(escape(finding.getSeverity())).append(" - ")
                    .append(escape(finding.getIssue())).append("</div>")
                    .append("<p><b>URL:</b> ").append(escape(finding.getUrl())).append("</p>")
                    .append("<p><b>Confidence:</b> ").append(escape(finding.getConfidence())).append("</p>")
                    .append("<p><b>Evidence:</b> ").append(escape(finding.getEvidence())).append("</p>")
                    .append("<h3>Request</h3><pre>").append(escape(finding.getRequestRaw())).append("</pre>")
                    .append("<h3>Response</h3><pre>").append(escape(finding.getResponseRaw())).append("</pre></section>");
        }
        builder.append("<h2>Annotated Requests</h2>");
        history.stream()
                .filter(tx -> tx.isFavorite() || !tx.getNotes().isBlank() || !tx.getComments().isBlank() || !tx.getTags().isBlank())
                .forEach(tx -> builder.append("<section><h3>").append(escape(tx.getMethod())).append(' ')
                        .append(escape(tx.getUrl())).append("</h3>")
                        .append("<p><b>Tags:</b> ").append(escape(tx.getTags())).append("</p>")
                        .append("<p><b>Notes:</b> ").append(escape(tx.getNotes())).append("</p>")
                        .append("<p><b>Comments:</b> ").append(escape(tx.getComments())).append("</p>")
                        .append("<h4>Request</h4><pre>").append(escape(tx.getRequestRaw())).append("</pre>")
                        .append("<h4>Response</h4><pre>").append(escape(tx.getResponseRaw())).append("</pre></section>"));
        builder.append("<h2>Screenshots</h2><p>No screenshots are attached to this workspace report.</p>");
        builder.append("</body></html>");
        Files.writeString(path, builder.toString(), StandardCharsets.UTF_8);
    }

    public static void pdf(List<Finding> findings, List<HttpTransaction> history, Path path) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("CyvoraX Suite Security Report");
        lines.add("");
        lines.add("Findings: " + findings.size());
        lines.add("Annotated requests: " + annotated(history).size());
        for (Finding finding : findings) {
            lines.add(finding.getSeverity() + " - " + finding.getIssue());
            lines.add("URL: " + finding.getUrl());
            lines.add("Confidence: " + finding.getConfidence());
            lines.add("Evidence: " + finding.getEvidence());
            lines.add("");
        }
        lines.add("Annotated Requests");
        for (HttpTransaction tx : history) {
            if (tx.isFavorite() || !tx.getNotes().isBlank() || !tx.getComments().isBlank() || !tx.getTags().isBlank()) {
                lines.add(tx.getMethod() + " " + tx.getUrl());
                lines.add("Tags: " + tx.getTags());
                lines.add("Notes: " + tx.getNotes());
                lines.add("Comments: " + tx.getComments());
                lines.add("Request sample: " + firstLine(tx.getRequestRaw()));
                lines.add("Response sample: " + firstLine(tx.getResponseRaw()));
                lines.add("");
            }
        }
        lines.add("Screenshots: none attached to this workspace report.");
        writeSimplePdf(lines, path);
    }

    public static void markdown(List<Finding> findings, List<HttpTransaction> history, Path path) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("# CyvoraX Suite Security Report\n\n");
        builder.append("- Findings: ").append(findings.size()).append('\n');
        builder.append("- Annotated requests: ").append(annotated(history).size()).append("\n\n");
        builder.append("## Findings\n\n");
        for (Finding finding : findings) {
            builder.append("### ").append(markdownEscape(finding.getSeverity())).append(" - ")
                    .append(markdownEscape(finding.getIssue())).append("\n\n")
                    .append("- URL: ").append(markdownEscape(finding.getUrl())).append('\n')
                    .append("- Confidence: ").append(markdownEscape(finding.getConfidence())).append('\n')
                    .append("- Evidence: ").append(markdownEscape(finding.getEvidence())).append("\n\n")
                    .append("#### Request\n\n```http\n").append(finding.getRequestRaw()).append("\n```\n\n")
                    .append("#### Response\n\n```http\n").append(finding.getResponseRaw()).append("\n```\n\n");
        }
        builder.append("## Annotated Requests\n\n");
        for (HttpTransaction tx : annotated(history)) {
            builder.append("### ").append(markdownEscape(tx.getMethod())).append(' ')
                    .append(markdownEscape(tx.getUrl())).append("\n\n")
                    .append("- Tags: ").append(markdownEscape(tx.getTags())).append('\n')
                    .append("- Color: ").append(markdownEscape(tx.getColorLabel())).append('\n')
                    .append("- Favorite: ").append(tx.isFavorite()).append("\n\n")
                    .append("#### Notes\n\n").append(markdownEscape(tx.getNotes())).append("\n\n")
                    .append("#### Comments\n\n").append(markdownEscape(tx.getComments())).append("\n\n")
                    .append("#### Request\n\n```http\n").append(tx.getRequestRaw()).append("\n```\n\n")
                    .append("#### Response\n\n```http\n").append(tx.getResponseRaw()).append("\n```\n\n");
        }
        builder.append("## Screenshots\n\nNo screenshots are attached to this workspace report.\n");
        Files.writeString(path, builder.toString(), StandardCharsets.UTF_8);
    }

    private static void writeSimplePdf(List<String> lines, Path path) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        write(out, "%PDF-1.4\n");
        offsets.add(out.size());
        write(out, "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n");
        offsets.add(out.size());
        write(out, "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n");
        offsets.add(out.size());
        write(out, "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj\n");
        String stream = textStream(lines);
        offsets.add(out.size());
        write(out, "4 0 obj << /Length " + stream.getBytes(StandardCharsets.UTF_8).length + " >> stream\n");
        write(out, stream);
        write(out, "\nendstream endobj\n");
        offsets.add(out.size());
        write(out, "5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n");
        int xref = out.size();
        write(out, "xref\n0 6\n0000000000 65535 f \n");
        for (int offset : offsets) {
            write(out, "%010d 00000 n \n".formatted(offset));
        }
        write(out, "trailer << /Root 1 0 R /Size 6 >>\nstartxref\n" + xref + "\n%%EOF\n");
        Files.write(path, out.toByteArray());
    }

    private static String textStream(List<String> lines) {
        StringBuilder builder = new StringBuilder("BT\n/F1 11 Tf\n50 750 Td\n");
        int lineCount = 0;
        for (String line : lines) {
            for (String part : wrap(line, 92)) {
                if (lineCount++ > 55) {
                    builder.append("(Report truncated for PDF compact view) Tj\n");
                    builder.append("ET");
                    return builder.toString();
                }
                builder.append('(').append(pdfEscape(part)).append(") Tj\n0 -14 Td\n");
            }
        }
        builder.append("ET");
        return builder.toString();
    }

    private static List<String> wrap(String value, int width) {
        String safe = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ');
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < safe.length(); i += width) {
            parts.add(safe.substring(i, Math.min(safe.length(), i + width)));
        }
        if (parts.isEmpty()) {
            parts.add("");
        }
        return parts;
    }

    private static void write(ByteArrayOutputStream out, String value) throws IOException {
        out.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String escape(String value) {
        return (value == null ? "" : value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static List<HttpTransaction> annotated(List<HttpTransaction> history) {
        return history.stream()
                .filter(tx -> tx.isFavorite() || !tx.getNotes().isBlank() || !tx.getComments().isBlank() || !tx.getTags().isBlank())
                .toList();
    }

    private static String firstLine(String value) {
        return value == null ? "" : value.lines().findFirst().orElse("");
    }

    private static String markdownEscape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\");
    }

    private static String pdfEscape(String value) {
        return (value == null ? "" : value)
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)");
    }
}
