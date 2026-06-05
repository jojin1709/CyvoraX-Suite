package com.venomproxy.db;

import com.venomproxy.model.AuthAccount;
import com.venomproxy.model.Finding;
import com.venomproxy.model.HttpTransaction;
import com.venomproxy.model.LogEntry;
import com.venomproxy.model.MatchReplaceRule;
import com.venomproxy.model.SessionEntry;
import com.venomproxy.model.SessionRecording;

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
                        timestamp TEXT, websocket INTEGER, in_scope INTEGER,
                        notes TEXT DEFAULT '', comments TEXT DEFAULT '', tags TEXT DEFAULT '',
                        color_label TEXT DEFAULT '', favorite INTEGER DEFAULT 0
                    )
                    """);
            addColumnIfMissing(statement, "history", "protocol", "TEXT DEFAULT 'HTTP/1.1'");
            addColumnIfMissing(statement, "history", "notes", "TEXT DEFAULT ''");
            addColumnIfMissing(statement, "history", "comments", "TEXT DEFAULT ''");
            addColumnIfMissing(statement, "history", "tags", "TEXT DEFAULT ''");
            addColumnIfMissing(statement, "history", "color_label", "TEXT DEFAULT ''");
            addColumnIfMissing(statement, "history", "favorite", "INTEGER DEFAULT 0");
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
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS app_settings (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS match_replace_rules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        enabled INTEGER NOT NULL,
                        phase TEXT NOT NULL,
                        target TEXT NOT NULL,
                        pattern TEXT NOT NULL,
                        replacement TEXT NOT NULL,
                        regex INTEGER NOT NULL,
                        condition_field TEXT DEFAULT '',
                        condition_pattern TEXT DEFAULT '',
                        notes TEXT DEFAULT ''
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS session_recordings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        started_at TEXT NOT NULL,
                        stopped_at TEXT
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS session_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        recording_id INTEGER NOT NULL,
                        transaction_id INTEGER NOT NULL,
                        sequence INTEGER NOT NULL,
                        request_raw TEXT NOT NULL,
                        response_raw TEXT NOT NULL,
                        timestamp TEXT NOT NULL,
                        FOREIGN KEY(recording_id) REFERENCES session_recordings(id) ON DELETE CASCADE
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS auth_accounts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        host_pattern TEXT NOT NULL,
                        bearer_token TEXT DEFAULT '',
                        cookie_jar TEXT DEFAULT '',
                        expires_at TEXT DEFAULT '',
                        active INTEGER DEFAULT 0
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
                        rs.getInt("in_scope") == 1,
                        rs.getString("notes"),
                        rs.getString("comments"),
                        rs.getString("tags"),
                        rs.getString("color_label"),
                        rs.getInt("favorite") == 1
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

    public synchronized void updateTransactionAnnotations(HttpTransaction tx) {
        String sql = """
                UPDATE history
                SET notes = ?, comments = ?, tags = ?, color_label = ?, favorite = ?
                WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tx.getNotes());
            statement.setString(2, tx.getComments());
            statement.setString(3, tx.getTags());
            statement.setString(4, tx.getColorLabel());
            statement.setInt(5, tx.isFavorite() ? 1 : 0);
            statement.setLong(6, tx.getId());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not update transaction annotations", ex);
        }
    }

    public synchronized String getSetting(String key, String defaultValue) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT value FROM app_settings WHERE key = ?")) {
            statement.setString(1, key);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString("value") : defaultValue;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not load setting " + key, ex);
        }
    }

    public synchronized void setSetting(String key, String value) {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_settings(key, value) VALUES(?, ?)
                ON CONFLICT(key) DO UPDATE SET value = excluded.value
                """)) {
            statement.setString(1, key);
            statement.setString(2, value == null ? "" : value);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not save setting " + key, ex);
        }
    }

    public synchronized List<MatchReplaceRule> listMatchReplaceRules() {
        List<MatchReplaceRule> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM match_replace_rules ORDER BY id ASC")) {
            while (rs.next()) {
                MatchReplaceRule rule = new MatchReplaceRule(
                        rs.getInt("enabled") == 1,
                        rs.getString("phase"),
                        rs.getString("target"),
                        rs.getString("pattern"),
                        rs.getString("replacement"),
                        rs.getInt("regex") == 1,
                        rs.getString("condition_field"),
                        rs.getString("condition_pattern"),
                        rs.getString("notes")
                );
                rule.setId(rs.getLong("id"));
                rows.add(rule);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not load match/replace rules", ex);
        }
        return rows;
    }

    public synchronized void saveMatchReplaceRule(MatchReplaceRule rule) {
        if (rule.getId() > 0) {
            updateMatchReplaceRule(rule);
            return;
        }
        String sql = """
                INSERT INTO match_replace_rules(enabled, phase, target, pattern, replacement, regex,
                                                condition_field, condition_pattern, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindRule(statement, rule);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    rule.setId(keys.getLong(1));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not save match/replace rule", ex);
        }
    }

    public synchronized void updateMatchReplaceRule(MatchReplaceRule rule) {
        String sql = """
                UPDATE match_replace_rules
                SET enabled = ?, phase = ?, target = ?, pattern = ?, replacement = ?, regex = ?,
                    condition_field = ?, condition_pattern = ?, notes = ?
                WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindRule(statement, rule);
            statement.setLong(10, rule.getId());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not update match/replace rule", ex);
        }
    }

    public synchronized void deleteMatchReplaceRule(long id) {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM match_replace_rules WHERE id = ?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not delete match/replace rule", ex);
        }
    }

    private void bindRule(PreparedStatement statement, MatchReplaceRule rule) throws SQLException {
        statement.setInt(1, rule.isEnabled() ? 1 : 0);
        statement.setString(2, rule.getPhase());
        statement.setString(3, rule.getTarget());
        statement.setString(4, rule.getPattern());
        statement.setString(5, rule.getReplacement());
        statement.setInt(6, rule.isRegex() ? 1 : 0);
        statement.setString(7, rule.getConditionField());
        statement.setString(8, rule.getConditionPattern());
        statement.setString(9, rule.getNotes());
    }

    public synchronized long createSessionRecording(String name) {
        String startedAt = Instant.now().toString();
        String label = name == null || name.isBlank() ? "Session " + startedAt : name.trim();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO session_recordings(name, started_at) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, label);
            statement.setString(2, startedAt);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not create session recording", ex);
        }
        throw new IllegalStateException("Could not create session recording");
    }

    public synchronized void stopSessionRecording(long id) {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE session_recordings SET stopped_at = ? WHERE id = ?")) {
            statement.setString(1, Instant.now().toString());
            statement.setLong(2, id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not stop session recording", ex);
        }
    }

    public synchronized List<SessionRecording> listSessionRecordings() {
        List<SessionRecording> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM session_recordings ORDER BY id DESC")) {
            while (rs.next()) {
                String stopped = rs.getString("stopped_at");
                SessionRecording recording = new SessionRecording(
                        rs.getString("name"),
                        Instant.parse(rs.getString("started_at")),
                        stopped == null || stopped.isBlank() ? null : Instant.parse(stopped)
                );
                recording.setId(rs.getLong("id"));
                rows.add(recording);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not load session recordings", ex);
        }
        return rows;
    }

    public synchronized void saveSessionEntry(long recordingId, HttpTransaction transaction, int sequence) {
        String sql = """
                INSERT INTO session_entries(recording_id, transaction_id, sequence, request_raw, response_raw, timestamp)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, recordingId);
            statement.setLong(2, transaction.getId());
            statement.setInt(3, sequence);
            statement.setString(4, transaction.getRequestRaw());
            statement.setString(5, transaction.getResponseRaw());
            statement.setString(6, transaction.getTimestamp().toString());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not save session entry", ex);
        }
    }

    public synchronized List<SessionEntry> listSessionEntries(long recordingId) {
        List<SessionEntry> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM session_entries WHERE recording_id = ? ORDER BY sequence ASC")) {
            statement.setLong(1, recordingId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    SessionEntry entry = new SessionEntry(
                            rs.getLong("recording_id"),
                            rs.getLong("transaction_id"),
                            rs.getInt("sequence"),
                            rs.getString("request_raw"),
                            rs.getString("response_raw"),
                            Instant.parse(rs.getString("timestamp"))
                    );
                    entry.setId(rs.getLong("id"));
                    rows.add(entry);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not load session entries", ex);
        }
        return rows;
    }

    public synchronized List<AuthAccount> listAuthAccounts() {
        List<AuthAccount> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM auth_accounts ORDER BY name ASC")) {
            while (rs.next()) {
                AuthAccount account = new AuthAccount(
                        rs.getString("name"),
                        rs.getString("host_pattern"),
                        rs.getString("bearer_token"),
                        rs.getString("cookie_jar"),
                        rs.getString("expires_at"),
                        rs.getInt("active") == 1
                );
                account.setId(rs.getLong("id"));
                rows.add(account);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not load authentication accounts", ex);
        }
        return rows;
    }

    public synchronized void saveAuthAccount(AuthAccount account) {
        if (account.getId() > 0) {
            updateAuthAccount(account);
            return;
        }
        String sql = """
                INSERT INTO auth_accounts(name, host_pattern, bearer_token, cookie_jar, expires_at, active)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindAuthAccount(statement, account);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    account.setId(keys.getLong(1));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not save authentication account", ex);
        }
    }

    public synchronized void updateAuthAccount(AuthAccount account) {
        String sql = """
                UPDATE auth_accounts
                SET name = ?, host_pattern = ?, bearer_token = ?, cookie_jar = ?, expires_at = ?, active = ?
                WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindAuthAccount(statement, account);
            statement.setLong(7, account.getId());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not update authentication account", ex);
        }
    }

    public synchronized void setAuthAccountActive(long id, boolean active) {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE auth_accounts SET active = ? WHERE id = ?")) {
            statement.setInt(1, active ? 1 : 0);
            statement.setLong(2, id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not update authentication account active state", ex);
        }
    }

    public synchronized void deleteAuthAccount(long id) {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM auth_accounts WHERE id = ?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not delete authentication account", ex);
        }
    }

    private void bindAuthAccount(PreparedStatement statement, AuthAccount account) throws SQLException {
        statement.setString(1, account.getName());
        statement.setString(2, account.getHostPattern());
        statement.setString(3, account.getBearerToken());
        statement.setString(4, account.getCookieJar());
        statement.setString(5, account.getExpiresAt());
        statement.setInt(6, account.isActive() ? 1 : 0);
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
