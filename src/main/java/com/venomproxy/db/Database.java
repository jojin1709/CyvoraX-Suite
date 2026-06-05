package com.venomproxy.db;

import com.venomproxy.model.Finding;
import com.venomproxy.model.HttpTransaction;
import com.venomproxy.model.LogEntry;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Database implements AutoCloseable {
    private final Connection connection;

    public Database(Path dbPath) throws SQLException {
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
        this.connection.setAutoCommit(true);
        migrate();
    }

    private void migrate() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        method TEXT, host TEXT, path TEXT, status INTEGER, length INTEGER,
                        mime_type TEXT, protocol TEXT DEFAULT 'HTTP/1.1', time_ms INTEGER, request_raw TEXT, response_raw TEXT,
                        timestamp TEXT, websocket INTEGER, in_scope INTEGER
                    )
                    """);
            addColumnIfMissing(statement, "history", "protocol", "TEXT DEFAULT 'HTTP/1.1'");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS findings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        severity TEXT, issue TEXT, url TEXT, confidence TEXT, evidence TEXT,
                        request_raw TEXT, response_raw TEXT, timestamp TEXT
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        timestamp TEXT, direction TEXT, host TEXT, message TEXT
                    )
                    """);
        }
    }

    public synchronized void saveTransaction(HttpTransaction tx) {
        String sql = """
                INSERT INTO history(method, host, path, status, length, mime_type, protocol, time_ms, request_raw,
                                    response_raw, timestamp, websocket, in_scope)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, tx.getMethod());
            statement.setString(2, tx.getHost());
            statement.setString(3, tx.getPath());
            statement.setInt(4, tx.getStatus());
            statement.setInt(5, tx.getLength());
            statement.setString(6, tx.getMimeType());
            statement.setString(7, tx.getProtocol());
            statement.setLong(8, tx.getTimeMs());
            statement.setString(9, tx.getRequestRaw());
            statement.setString(10, tx.getResponseRaw());
            statement.setString(11, tx.getTimestamp().toString());
            statement.setInt(12, tx.isWebsocket() ? 1 : 0);
            statement.setInt(13, tx.isInScope() ? 1 : 0);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    tx.setId(keys.getLong(1));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not save transaction", ex);
        }
    }

    public synchronized List<HttpTransaction> listTransactions() {
        List<HttpTransaction> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM history ORDER BY id DESC LIMIT 5000")) {
            while (rs.next()) {
                HttpTransaction tx = new HttpTransaction(
                        rs.getString("method"),
                        rs.getString("host"),
                        rs.getString("path"),
                        rs.getInt("status"),
                        rs.getInt("length"),
                        rs.getString("mime_type"),
                        rs.getString("protocol"),
                        rs.getLong("time_ms"),
                        rs.getString("request_raw"),
                        rs.getString("response_raw"),
                        Instant.parse(rs.getString("timestamp")),
                        rs.getInt("websocket") == 1,
                        rs.getInt("in_scope") == 1
                );
                tx.setId(rs.getLong("id"));
                rows.add(tx);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not load history", ex);
        }
        return rows;
    }

    private void addColumnIfMissing(Statement statement, String table, String column, String definition) throws SQLException {
        try (ResultSet rs = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return;
                }
            }
        }
        statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }

    public synchronized void saveFinding(Finding finding) {
        String sql = """
                INSERT INTO findings(severity, issue, url, confidence, evidence, request_raw, response_raw, timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, finding.getSeverity());
            statement.setString(2, finding.getIssue());
            statement.setString(3, finding.getUrl());
            statement.setString(4, finding.getConfidence());
            statement.setString(5, finding.getEvidence());
            statement.setString(6, finding.getRequestRaw());
            statement.setString(7, finding.getResponseRaw());
            statement.setString(8, finding.getTimestamp().toString());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    finding.setId(keys.getLong(1));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not save finding", ex);
        }
    }

    public synchronized List<Finding> listFindings() {
        List<Finding> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM findings ORDER BY id DESC LIMIT 5000")) {
            while (rs.next()) {
                Finding finding = new Finding(
                        rs.getString("severity"),
                        rs.getString("issue"),
                        rs.getString("url"),
                        rs.getString("confidence"),
                        rs.getString("evidence"),
                        rs.getString("request_raw"),
                        rs.getString("response_raw"),
                        Instant.parse(rs.getString("timestamp"))
                );
                finding.setId(rs.getLong("id"));
                rows.add(finding);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not load findings", ex);
        }
        return rows;
    }

    public synchronized void saveLog(LogEntry entry) {
        String sql = "INSERT INTO logs(timestamp, direction, host, message) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, entry.getTimestamp().toString());
            statement.setString(2, entry.getDirection());
            statement.setString(3, entry.getHost());
            statement.setString(4, entry.getMessage());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    entry.setId(keys.getLong(1));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not save log entry", ex);
        }
    }

    public synchronized List<LogEntry> listLogs() {
        List<LogEntry> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM logs ORDER BY id DESC LIMIT 10000")) {
            while (rs.next()) {
                LogEntry entry = new LogEntry(
                        Instant.parse(rs.getString("timestamp")),
                        rs.getString("direction"),
                        rs.getString("host"),
                        rs.getString("message")
                );
                entry.setId(rs.getLong("id"));
                rows.add(entry);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not load log entries", ex);
        }
        return rows;
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}
