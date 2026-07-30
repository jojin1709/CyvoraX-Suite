"""
CyvoraX Suite - Project Database Engine
SQLite persistence backend (.cyvorax project files) for proxy logs, site map, and findings.
"""
import sqlite3
import json
import os


class ProjectDB:
    def __init__(self, db_path: str = "cyvorax_project.cyvorax"):
        self.db_path = db_path
        self.conn = sqlite3.connect(self.db_path, check_same_thread=False)
        self._init_tables()

    def _init_tables(self):
        cursor = self.conn.cursor()
        
        # Proxy History Table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS proxy_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
                host TEXT,
                method TEXT,
                path TEXT,
                status_code INTEGER,
                req_headers TEXT,
                req_body BLOB,
                resp_headers TEXT,
                resp_body BLOB
            )
        """)

        # Vulnerabilities Table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS findings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                severity TEXT,
                title TEXT,
                host TEXT,
                path TEXT,
                detail TEXT,
                evidence TEXT
            )
        """)

        self.conn.commit()

    def log_proxy_item(self, host: str, method: str, path: str, status_code: int, req_headers: list, req_body: bytes, resp_headers: list, resp_body: bytes) -> int:
        cursor = self.conn.cursor()
        cursor.execute("""
            INSERT INTO proxy_history (host, method, path, status_code, req_headers, req_body, resp_headers, resp_body)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """, (host, method, path, status_code, json.dumps(req_headers), req_body, json.dumps(resp_headers), resp_body))
        self.conn.commit()
        return cursor.lastrowid

    def add_finding(self, severity: str, title: str, host: str, path: str, detail: str, evidence: str):
        cursor = self.conn.cursor()
        cursor.execute("""
            INSERT INTO findings (severity, title, host, path, detail, evidence)
            VALUES (?, ?, ?, ?, ?, ?)
        """, (severity, title, host, path, detail, evidence))
        self.conn.commit()

    def fetch_all_proxy_items(self):
        cursor = self.conn.cursor()
        cursor.execute("SELECT id, timestamp, host, method, path, status_code FROM proxy_history ORDER BY id ASC")
        return cursor.fetchall()

    def close(self):
        self.conn.close()
