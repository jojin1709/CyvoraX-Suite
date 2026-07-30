package com.venomproxy.util;

import com.venomproxy.model.HttpTransaction;
import com.venomproxy.model.LogEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class Exporters {
    private Exporters() {
    }

    public static void historyCsv(List<HttpTransaction> rows, Path path) throws IOException {
        StringBuilder builder = new StringBuilder("#,method,host,path,status,length,mime,protocol,time_ms\n");
        for (HttpTransaction row : rows) {
            builder.append(row.getId()).append(',')
                    .append(csv(row.getMethod())).append(',')
                    .append(csv(row.getHost())).append(',')
                    .append(csv(row.getPath())).append(',')
                    .append(row.getStatus()).append(',')
                    .append(row.getLength()).append(',')
                    .append(csv(row.getMimeType())).append(',')
                    .append(csv(row.getProtocol())).append(',')
                    .append(row.getTimeMs()).append('\n');
        }
        Files.writeString(path, builder.toString());
    }

    public static void historyJson(List<HttpTransaction> rows, Path path) throws IOException {
        StringBuilder builder = new StringBuilder("[\n");
        for (int i = 0; i < rows.size(); i++) {
            HttpTransaction row = rows.get(i);
            builder.append("  {")
                    .append("\"id\":").append(row.getId()).append(',')
                    .append("\"method\":\"").append(json(row.getMethod())).append("\",")
                    .append("\"host\":\"").append(json(row.getHost())).append("\",")
                    .append("\"path\":\"").append(json(row.getPath())).append("\",")
                    .append("\"status\":").append(row.getStatus()).append(',')
                    .append("\"length\":").append(row.getLength()).append(',')
                    .append("\"mime\":\"").append(json(row.getMimeType())).append("\",")
                    .append("\"protocol\":\"").append(json(row.getProtocol())).append("\",")
                    .append("\"timeMs\":").append(row.getTimeMs())
                    .append("}");
            builder.append(i == rows.size() - 1 ? "\n" : ",\n");
        }
        builder.append("]\n");
        Files.writeString(path, builder.toString());
    }

    public static void logsTxt(List<LogEntry> rows, Path path) throws IOException {
        StringBuilder builder = new StringBuilder();
        for (LogEntry row : rows) {
            builder.append(row.getTimestamp()).append(' ')
                    .append(row.getDirection()).append(' ')
                    .append(row.getHost()).append(" - ")
                    .append(row.getMessage()).append('\n');
        }
        Files.writeString(path, builder.toString());
    }

    public static void logsJson(List<LogEntry> rows, Path path) throws IOException {
        StringBuilder builder = new StringBuilder("[\n");
        for (int i = 0; i < rows.size(); i++) {
            LogEntry row = rows.get(i);
            builder.append("  {")
                    .append("\"id\":").append(row.getId()).append(',')
                    .append("\"timestamp\":\"").append(json(row.getTimestamp().toString())).append("\",")
                    .append("\"direction\":\"").append(json(row.getDirection())).append("\",")
                    .append("\"host\":\"").append(json(row.getHost())).append("\",")
                    .append("\"message\":\"").append(json(row.getMessage())).append("\"")
                    .append("}");
            builder.append(i == rows.size() - 1 ? "\n" : ",\n");
        }
        builder.append("]\n");
        Files.writeString(path, builder.toString());
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static String json(String value) {
        String safe = value == null ? "" : value;
        return safe.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
