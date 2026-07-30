"""
CyvoraX Suite Professional - Main Application Entrypoint
An exact Burp Suite Professional replica UI for web security testing:
Dashboard, Target (Site Map), Proxy (Intercept, HTTP History, Match & Replace),
Intruder, Repeater, Sequencer, Decoder, Comparer, and Scanner.
"""
import sys
import asyncio
import threading

from PyQt6.QtWidgets import (
    QApplication, QMainWindow, QTabWidget, QWidget, QVBoxLayout, QHBoxLayout,
    QPushButton, QTextEdit, QLabel, QListWidget, QListWidgetItem, QLineEdit,
    QComboBox, QSpinBox, QCheckBox, QSplitter, QMessageBox, QFrame, QStatusBar,
    QMenuBar, QMenu, QTableWidget, QTableWidgetItem, QHeaderView, QTreeWidget, QTreeWidgetItem
)
from PyQt6.QtCore import Qt, pyqtSignal, QObject, QTimer
from PyQt6.QtGui import QFont, QColor, QPalette, QAction

from modules.proxy_core import ProxyCore, HTTPMessage
from modules import decoder as dec
from modules import comparer as cmp
from modules.repeater import send_raw_request, parse_target_from_request, request_to_curl, curl_to_request
from modules.scanner import Scanner, COMMON_SERVICE_NAMES
from modules.intruder import run_attack, COMMON_PAYLOADS
from modules.active_scanner import scan_endpoint
from modules.sequencer import analyze_tokens
from modules.match_replace import MatchReplaceEngine, MatchReplaceRule


# ---------------------------------------------------------------------------
# Exact Burp Suite Dark Theme Stylesheet
# ---------------------------------------------------------------------------
STYLESHEET = """
/* ── Base App Theme ── */
QMainWindow, QDialog {
    background-color: #333333;
    color: #e0e0e0;
    font-family: 'Segoe UI', 'Helvetica Neue', Arial, sans-serif;
    font-size: 12px;
}
QWidget {
    color: #e0e0e0;
    font-family: 'Segoe UI', 'Helvetica Neue', Arial, sans-serif;
    font-size: 12px;
}

/* ── Menu Bar ── */
QMenuBar {
    background-color: #262626;
    color: #cccccc;
    border-bottom: 1px solid #1a1a1a;
    padding: 2px 4px;
}
QMenuBar::item {
    background: transparent;
    padding: 4px 10px;
    border-radius: 2px;
}
QMenuBar::item:selected {
    background-color: #3d3d3d;
    color: #ffffff;
}
QMenu {
    background-color: #2b2b2b;
    color: #e0e0e0;
    border: 1px solid #454545;
}
QMenu::item:selected {
    background-color: #e85d04;
    color: #ffffff;
}

/* ── Main & Nested Tab Widgets ── */
QTabWidget::pane {
    border: 1px solid #3c3c3c;
    background: #333333;
    top: -1px;
}
QTabBar {
    background-color: #242424;
    border-bottom: 1px solid #3c3c3c;
}
QTabBar::tab {
    background: #2b2b2b;
    color: #a0a0a0;
    padding: 7px 18px;
    border: 1px solid #3a3a3a;
    border-bottom: none;
    margin-right: 2px;
    font-size: 12px;
    font-weight: 500;
}
QTabBar::tab:selected {
    background: #333333;
    color: #ff6600;
    border-top: 3px solid #ff6600;
    border-bottom: 1px solid #333333;
    font-weight: 600;
}
QTabBar::tab:hover:!selected {
    background: #363636;
    color: #ffffff;
}

/* Sub-level Tab Bars (e.g. Proxy -> Intercept / HTTP History) */
QTabWidget#sub_tabs QTabBar::tab {
    padding: 5px 14px;
    font-size: 11px;
    background: #2d2d2d;
    color: #b0b0b0;
    border-top: 2px solid transparent;
}
QTabWidget#sub_tabs QTabBar::tab:selected {
    background: #333333;
    color: #ffffff;
    border-top: 2px solid #ff6600;
}

/* ── Buttons ── */
QPushButton {
    background-color: #424242;
    color: #e0e0e0;
    border: 1px solid #585858;
    border-radius: 3px;
    padding: 5px 14px;
    font-size: 12px;
    font-weight: 500;
    min-height: 22px;
}
QPushButton:hover {
    background-color: #4f4f4f;
    border-color: #707070;
    color: #ffffff;
}
QPushButton:pressed {
    background-color: #2b2b2b;
}
QPushButton:disabled {
    background-color: #333333;
    color: #666666;
    border-color: #404040;
}

/* Primary Burp Orange Button */
QPushButton#primary_btn {
    background-color: #e85d04;
    color: #ffffff;
    border: 1px solid #c84d00;
    font-weight: 600;
}
QPushButton#primary_btn:hover {
    background-color: #ff6c0a;
    border-color: #e85d04;
}
QPushButton#primary_btn:pressed {
    background-color: #cc4e00;
}

/* Toggle Intercept Button (ON state) */
QPushButton#intercept_on_btn {
    background-color: #e85d04;
    color: #ffffff;
    border: 1px solid #c84d00;
    font-weight: 700;
}
QPushButton#intercept_off_btn {
    background-color: #424242;
    color: #aaaaaa;
    border: 1px solid #585858;
}

/* ── Editors & Inputs ── */
QTextEdit, QPlainTextEdit, QLineEdit {
    background-color: #1e1e1e;
    color: #d4d4d4;
    border: 1px solid #454545;
    border-radius: 2px;
    padding: 5px 7px;
    font-family: 'Consolas', 'Courier New', monospace;
    font-size: 12px;
    selection-background-color: #1c4466;
    selection-color: #ffffff;
}
QTextEdit:focus, QPlainTextEdit:focus, QLineEdit:focus {
    border: 1px solid #ff6600;
}
QTextEdit[readOnly="true"], QPlainTextEdit[readOnly="true"] {
    background-color: #242424;
    color: #bbbbbb;
    border: 1px solid #3c3c3c;
}

/* ── Tables & Lists ── */
QTableWidget, QTreeWidget, QListWidget {
    background-color: #222222;
    color: #dddddd;
    border: 1px solid #404040;
    gridline-color: #333333;
    font-family: 'Consolas', 'Segoe UI', monospace;
    font-size: 11px;
    outline: none;
}
QTableWidget::item:selected, QTreeWidget::item:selected, QListWidget::item:selected {
    background-color: #044570;
    color: #ffffff;
}
QHeaderView::section {
    background-color: #2b2b2b;
    color: #aaaaaa;
    padding: 4px 8px;
    border: 1px solid #3a3a3a;
    font-weight: 600;
    font-size: 11px;
}

/* ── Combo & Spin Boxes ── */
QComboBox, QSpinBox {
    background-color: #3c3c3c;
    color: #e0e0e0;
    border: 1px solid #555555;
    border-radius: 2px;
    padding: 3px 6px;
    font-size: 12px;
    min-height: 24px;
}
QComboBox:hover, QSpinBox:hover {
    border-color: #777777;
}
QComboBox QAbstractItemView {
    background-color: #2b2b2b;
    color: #e0e0e0;
    selection-background-color: #e85d04;
    selection-color: #ffffff;
    border: 1px solid #454545;
}

/* ── Checkboxes & Radio Buttons ── */
QCheckBox, QRadioButton {
    color: #dddddd;
    spacing: 6px;
    font-size: 12px;
}
QCheckBox::indicator, QRadioButton::indicator {
    width: 14px;
    height: 14px;
    border: 1px solid #666666;
    background: #2b2b2b;
    border-radius: 2px;
}
QCheckBox::indicator:checked {
    background: #e85d04;
    border-color: #ff6600;
}

/* ── Splitters ── */
QSplitter::handle {
    background-color: #3c3c3c;
    width: 3px;
    height: 3px;
}
QSplitter::handle:hover {
    background-color: #ff6600;
}

/* ── Status Bar ── */
QStatusBar {
    background-color: #242424;
    color: #999999;
    border-top: 1px solid #3c3c3c;
    font-size: 11px;
}
"""

# ---------------------------------------------------------------------------
# Signals & Async Proxy Thread
# ---------------------------------------------------------------------------
class ProxySignals(QObject):
    request_logged = pyqtSignal(object)
    response_logged = pyqtSignal(object, object)
    intercept_hit = pyqtSignal(object)
    passive_finding_logged = pyqtSignal(object, object, object)


class ProxyThread(threading.Thread):
    def __init__(self, signals: ProxySignals, host="127.0.0.1", port=8080, match_replace_engine=None):
        super().__init__(daemon=True)
        self.signals = signals
        self.host = host
        self.port = port
        self.match_replace_engine = match_replace_engine
        self.loop = None
        self.core = None
        self._intercept_enabled = True
        self._pending_futures = {}

    def set_intercept(self, enabled: bool):
        self._intercept_enabled = enabled

    async def _intercept_wait(self, msg: HTTPMessage):
        fut = self.loop.create_future()
        self._pending_futures[msg.id] = fut
        self.signals.intercept_hit.emit(msg)
        result = await fut
        return result

    def forward_intercepted(self, msg_id, edited_msg):
        fut = self._pending_futures.pop(msg_id, None)
        if fut and self.loop:
            self.loop.call_soon_threadsafe(fut.set_result, edited_msg)

    def run(self):
        self.loop = asyncio.new_event_loop()
        asyncio.set_event_loop(self.loop)
        self.core = ProxyCore(
            host=self.host, port=self.port,
            on_request=lambda m: self.signals.request_logged.emit(m),
            on_response=lambda req, resp: self.signals.response_logged.emit(req, resp),
            intercept_enabled=lambda: self._intercept_enabled,
            intercept_wait=self._intercept_wait,
            match_replace_engine=self.match_replace_engine,
            on_passive_scan_finding=lambda req, resp, finding: self.signals.passive_finding_logged.emit(req, resp, finding)
        )
        self.loop.run_until_complete(self.core.start())
        self.loop.run_forever()

    def stop(self):
        if self.loop:
            self.loop.call_soon_threadsafe(self.loop.stop)


# ---------------------------------------------------------------------------
# Dashboard Tab (Burp Professional Style)
# ---------------------------------------------------------------------------
class DashboardTab(QWidget):
    def __init__(self):
        super().__init__()
        layout = QHBoxLayout(self)
        layout.setContentsMargins(8, 8, 8, 8)
        layout.setSpacing(8)

        splitter = QSplitter(Qt.Orientation.Horizontal)

        # Left Column: Tasks Panel
        left_panel = QWidget()
        l_box = QVBoxLayout(left_panel)
        l_box.setContentsMargins(0, 0, 0, 0)
        l_box.setSpacing(6)

        t_header = QHBoxLayout()
        t_header.addWidget(QLabel("<b>Tasks</b>"))
        t_header.addStretch()
        l_box.addLayout(t_header)

        btn_row = QHBoxLayout()
        scan_btn = QPushButton("New scan")
        scan_btn.setObjectName("primary_btn")
        task_btn = QPushButton("New live task")
        btn_row.addWidget(scan_btn)
        btn_row.addWidget(task_btn)
        l_box.addLayout(btn_row)

        tasks_list = QListWidget()
        tasks_list.addItem("2. Live audit from Proxy (all traffic)")
        tasks_list.addItem("1. Live passive crawl from Proxy")
        l_box.addWidget(tasks_list, 1)

        splitter.addWidget(left_panel)

        # Right Column: Vulnerability & Task Summary
        right_panel = QWidget()
        r_box = QVBoxLayout(right_panel)
        r_box.setContentsMargins(0, 0, 0, 0)
        r_box.setSpacing(6)

        r_box.addWidget(QLabel("<b>Most serious vulnerabilities found (live)</b>"))

        self.table = QTableWidget(0, 3)
        self.table.setHorizontalHeaderLabels(["Issue type", "Host", "Time"])
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Stretch)
        r_box.addWidget(self.table, 1)

        r_box.addWidget(QLabel("<b>Task configuration & progress</b>"))
        task_info = QTextEdit()
        task_info.setReadOnly(True)
        task_info.setPlainText("Task type: Live audit\nScope: Proxy (all traffic)\nConfiguration: Audit checks - passive\nCapturing: Active\n\nTotal audit items: 0\nAudit items pending: 0\nAudit items completed: 0")
        task_info.setMaximumHeight(140)
        r_box.addWidget(task_info)

        splitter.addWidget(right_panel)
        splitter.setSizes([320, 780])

        layout.addWidget(splitter)

    def add_finding(self, req, resp, finding):
        row = self.table.rowCount()
        self.table.insertRow(row)
        self.table.setItem(row, 0, QTableWidgetItem(f"[{finding.severity.upper()}] {finding.check}"))
        self.table.setItem(row, 1, QTableWidgetItem(req.host))
        self.table.setItem(row, 2, QTableWidgetItem(time.strftime("%H:%M:%S")))


# ---------------------------------------------------------------------------
# Target Tab (Burp Site Map Style)
# ---------------------------------------------------------------------------
class TargetTab(QWidget):
    def __init__(self, main_win_ref=None):
        super().__init__()
        self.main_win_ref = main_win_ref
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)

        sub_tabs = QTabWidget()
        sub_tabs.setObjectName("sub_tabs")

        # Site map tab
        sm_widget = QWidget()
        sm_box = QVBoxLayout(sm_widget)
        sm_box.setContentsMargins(8, 8, 8, 8)

        self.tree = QTreeWidget()
        self.tree.setHeaderLabels(["Host / Target URL", "Status", "Length", "MIME"])
        self.tree.header().setSectionResizeMode(0, QHeaderView.ResizeMode.Stretch)
        sm_box.addWidget(self.tree)

        sub_tabs.addTab(sm_widget, "Site map")
        sub_tabs.addTab(QWidget(), "Scope")
        sub_tabs.addTab(QWidget(), "Issue definitions")

        layout.addWidget(sub_tabs)

    def add_target_item(self, method, host, path, status="", length="", mime=""):
        # Add host root node if doesn't exist
        items = self.tree.findItems(host, Qt.MatchFlag.MatchExactly, 0)
        if items:
            host_node = items[0]
        else:
            host_node = QTreeWidgetItem(self.tree, [host, "", "", ""])
            host_node.setExpanded(True)
        
        path_node = QTreeWidgetItem(host_node, [f"{method} {path}", str(status), str(length), mime])


# ---------------------------------------------------------------------------
# Proxy Tab (Burp Professional Replica)
# ---------------------------------------------------------------------------
class ProxyTab(QWidget):
    def __init__(self, target_tab_ref=None):
        super().__init__()
        self.target_tab_ref = target_tab_ref
        self.signals = ProxySignals()
        self.proxy_thread = None
        self._history = {}
        self._intercept_is_on = True

        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)

        sub_tabs = QTabWidget()
        sub_tabs.setObjectName("sub_tabs")

        # --- 1. Intercept Sub-Tab ---
        intercept_widget = QWidget()
        i_box = QVBoxLayout(intercept_widget)
        i_box.setContentsMargins(8, 8, 8, 8)
        i_box.setSpacing(8)

        # Action Toolbar (Burp Exact Row)
        tb = QHBoxLayout()
        tb.setSpacing(6)

        self.forward_btn = QPushButton("Forward")
        self.forward_btn.setObjectName("primary_btn")
        self.forward_btn.setEnabled(False)

        self.drop_btn = QPushButton("Drop")
        self.drop_btn.setEnabled(False)

        self.intercept_toggle_btn = QPushButton("Intercept is on")
        self.intercept_toggle_btn.setObjectName("intercept_on_btn")

        self.action_btn = QPushButton("Action  ▼")
        self.browser_btn = QPushButton("Open browser")

        tb.addWidget(self.forward_btn)
        tb.addWidget(self.drop_btn)
        tb.addWidget(self.intercept_toggle_btn)
        tb.addWidget(self.action_btn)
        tb.addWidget(self.browser_btn)

        tb.addSpacing(20)
        tb.addWidget(QLabel("Port:"))
        self.port_spin = QSpinBox()
        self.port_spin.setRange(1, 65535)
        self.port_spin.setValue(8080)
        self.port_spin.setFixedWidth(70)
        tb.addWidget(self.port_spin)

        self.start_proxy_btn = QPushButton("Start Proxy")
        self.stop_proxy_btn = QPushButton("Stop")
        self.stop_proxy_btn.setEnabled(False)
        tb.addWidget(self.start_proxy_btn)
        tb.addWidget(self.stop_proxy_btn)

        self.status_lbl = QLabel("Stopped")
        self.status_lbl.setStyleSheet("color: #888888; font-weight: bold; margin-left: 10px;")
        tb.addWidget(self.status_lbl)

        tb.addStretch()
        i_box.addLayout(tb)

        # Editor Splitter (Request on Top / Response below or Side by Side)
        splitter = QSplitter(Qt.Orientation.Vertical)

        # Request Inspector Pane
        req_box_w = QWidget()
        rb_layout = QVBoxLayout(req_box_w)
        rb_layout.setContentsMargins(0, 0, 0, 0)
        rb_layout.addWidget(QLabel("<b>Raw Request</b>"))
        self.req_edit = QTextEdit()
        self.req_edit.setPlaceholderText("Intercepted HTTP requests will appear here...")
        rb_layout.addWidget(self.req_edit)
        splitter.addWidget(req_box_w)

        # Response Inspector Pane
        resp_box_w = QWidget()
        rs_layout = QVBoxLayout(resp_box_w)
        rs_layout.setContentsMargins(0, 0, 0, 0)
        rs_layout.addWidget(QLabel("<b>Response</b>"))
        self.resp_view = QTextEdit()
        self.resp_view.setReadOnly(True)
        self.resp_view.setPlaceholderText("Response preview...")
        rs_layout.addWidget(self.resp_view)
        splitter.addWidget(resp_box_w)

        splitter.setSizes([450, 350])
        i_box.addWidget(splitter, 1)

        sub_tabs.addTab(intercept_widget, "Intercept")

        # --- 2. HTTP History Sub-Tab ---
        history_widget = QWidget()
        h_box = QVBoxLayout(history_widget)
        h_box.setContentsMargins(8, 8, 8, 8)

        h_splitter = QSplitter(Qt.Orientation.Vertical)

        # History Table
        self.history_table = QTableWidget(0, 7)
        self.history_table.setHorizontalHeaderLabels(["#", "Host", "Method", "URL", "Status", "Length", "MIME"])
        self.history_table.horizontalHeader().setSectionResizeMode(3, QHeaderView.ResizeMode.Stretch)
        self.history_table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        h_splitter.addWidget(self.history_table)

        # Detailed req/resp viewer for history
        h_detail_splitter = QSplitter(Qt.Orientation.Horizontal)
        self.hist_req_view = QTextEdit()
        self.hist_req_view.setReadOnly(True)
        self.hist_resp_view = QTextEdit()
        self.hist_resp_view.setReadOnly(True)
        h_detail_splitter.addWidget(self.hist_req_view)
        h_detail_splitter.addWidget(self.hist_resp_view)
        h_splitter.addWidget(h_detail_splitter)

        h_splitter.setSizes([350, 350])
        h_box.addWidget(h_splitter)

        sub_tabs.addTab(history_widget, "HTTP history")
        sub_tabs.addTab(QWidget(), "WebSockets history")
        sub_tabs.addTab(QWidget(), "Options")

        layout.addWidget(sub_tabs)

        # Wire Signals & Slots
        self.start_proxy_btn.clicked.connect(self.start_proxy)
        self.stop_proxy_btn.clicked.connect(self.stop_proxy)
        self.intercept_toggle_btn.clicked.connect(self.toggle_intercept_btn)
        self.forward_btn.clicked.connect(self.forward_intercepted)
        self.drop_btn.clicked.connect(self.drop_intercepted)
        self.history_table.itemSelectionChanged.connect(self.on_history_row_selected)

        self.signals.request_logged.connect(self.on_request_logged)
        self.signals.response_logged.connect(self.on_response_logged)
        self.signals.intercept_hit.connect(self.on_intercept_hit)
        self._current_intercept_msg = None

        # Auto-start proxy by default
        QTimer.singleShot(500, self.start_proxy)

    def start_proxy(self):
        if self.proxy_thread and self.proxy_thread.is_alive():
            return
        port = self.port_spin.value()
        self.match_replace_engine = MatchReplaceEngine(load_defaults=True)
        self.proxy_thread = ProxyThread(self.signals, port=port, match_replace_engine=self.match_replace_engine)
        self.proxy_thread.set_intercept(self._intercept_is_on)
        self.proxy_thread.start()
        self.status_lbl.setText(f"Listening on 127.0.0.1:{port}")
        self.status_lbl.setStyleSheet("color: #55aa55; font-weight: bold; margin-left: 10px;")
        self.start_proxy_btn.setEnabled(False)
        self.stop_proxy_btn.setEnabled(True)

    def stop_proxy(self):
        if self.proxy_thread:
            self.proxy_thread.stop()
        self.status_lbl.setText("Stopped")
        self.status_lbl.setStyleSheet("color: #e05050; font-weight: bold; margin-left: 10px;")
        self.start_proxy_btn.setEnabled(True)
        self.stop_proxy_btn.setEnabled(False)

    def toggle_intercept_btn(self):
        self._intercept_is_on = not self._intercept_is_on
        if self._intercept_is_on:
            self.intercept_toggle_btn.setText("Intercept is on")
            self.intercept_toggle_btn.setObjectName("intercept_on_btn")
        else:
            self.intercept_toggle_btn.setText("Intercept is off")
            self.intercept_toggle_btn.setObjectName("intercept_off_btn")
        
        self.intercept_toggle_btn.style().unpolish(self.intercept_toggle_btn)
        self.intercept_toggle_btn.style().polish(self.intercept_toggle_btn)
        
        if self.proxy_thread:
            self.proxy_thread.set_intercept(self._intercept_is_on)

    def on_request_logged(self, msg: HTTPMessage):
        self._history[msg.id] = {"req": msg, "resp": None}
        row = self.history_table.rowCount()
        self.history_table.insertRow(row)
        
        self.history_table.setItem(row, 0, QTableWidgetItem(str(row + 1)))
        self.history_table.setItem(row, 1, QTableWidgetItem(msg.host))
        self.history_table.setItem(row, 2, QTableWidgetItem(msg.method))
        self.history_table.setItem(row, 3, QTableWidgetItem(msg.path))
        self.history_table.setItem(row, 4, QTableWidgetItem("..."))
        self.history_table.setItem(row, 5, QTableWidgetItem(str(len(msg.body))))
        self.history_table.setItem(row, 6, QTableWidgetItem("HTML/Text"))

        if self.target_tab_ref:
            self.target_tab_ref.add_target_item(msg.method, msg.host, msg.path, "", len(msg.body))

    def on_response_logged(self, req_msg: HTTPMessage, resp_msg: HTTPMessage):
        if req_msg.id in self._history:
            self._history[req_msg.id]["resp"] = resp_msg
            for r in range(self.history_table.rowCount()):
                item_id = self.history_table.item(r, 0)
                if item_id and item_id.text() == str(req_msg.id):
                    self.history_table.setItem(r, 4, QTableWidgetItem(str(resp_msg.status_code)))
                    self.history_table.setItem(r, 5, QTableWidgetItem(str(len(resp_msg.body))))

    def on_history_row_selected(self):
        selected = self.history_table.selectedItems()
        if not selected:
            return
        row = selected[0].row()
        item_id_str = self.history_table.item(row, 0).text()
        try:
            msg_id = int(item_id_str)
            entry = self._history.get(msg_id)
            if entry:
                self.hist_req_view.setPlainText(entry["req"].raw_request().decode(errors="replace"))
                if entry["resp"]:
                    self.hist_resp_view.setPlainText(entry["resp"].raw_response().decode(errors="replace"))
                else:
                    self.hist_resp_view.setPlainText("(no response)")
        except ValueError:
            pass

    def on_intercept_hit(self, msg: HTTPMessage):
        self._current_intercept_msg = msg
        self.req_edit.setPlainText(msg.raw_request().decode(errors="replace"))
        self.forward_btn.setEnabled(True)
        self.drop_btn.setEnabled(True)

    def forward_intercepted(self):
        if not self._current_intercept_msg or not self.proxy_thread:
            return
        # Rebuild edited message
        raw = self.req_edit.toPlainText()
        lines = raw.split("\n")
        first = lines[0].split(" ")
        method, path, version = (first + ["", "", ""])[:3]
        headers, body_lines, in_body = [], [], False
        for line in lines[1:]:
            if in_body:
                body_lines.append(line)
                continue
            if line.strip() == "":
                in_body = True
                continue
            if ":" in line:
                k, _, v = line.partition(":")
                headers.append((k.strip(), v.strip()))
        
        msg = self._current_intercept_msg
        msg.method, msg.path, msg.version, msg.headers, msg.body = method, path, version, headers, "\n".join(body_lines).encode()
        self.proxy_thread.forward_intercepted(msg.id, msg)
        self.forward_btn.setEnabled(False)
        self.drop_btn.setEnabled(False)
        self.req_edit.clear()

    def drop_intercepted(self):
        if not self._current_intercept_msg or not self.proxy_thread:
            return
        self.proxy_thread.forward_intercepted(self._current_intercept_msg.id, None)
        self.forward_btn.setEnabled(False)
        self.drop_btn.setEnabled(False)
        self.req_edit.clear()


# ---------------------------------------------------------------------------
# Repeater Tab (Burp Style)
# ---------------------------------------------------------------------------
class RepeaterTab(QWidget):
    def __init__(self):
        super().__init__()
        layout = QVBoxLayout(self)
        layout.setContentsMargins(8, 8, 8, 8)
        layout.setSpacing(8)

        tb = QHBoxLayout()
        self.send_btn = QPushButton("Send")
        self.send_btn.setObjectName("primary_btn")
        self.tls_chk = QCheckBox("HTTPS")
        self.tls_chk.setChecked(True)
        self.copy_curl_btn = QPushButton("Copy as cURL")
        self.paste_curl_btn = QPushButton("Paste cURL")
        
        tb.addWidget(self.send_btn)
        tb.addWidget(self.tls_chk)
        tb.addWidget(self.copy_curl_btn)
        tb.addWidget(self.paste_curl_btn)
        tb.addStretch()
        layout.addLayout(tb)

        splitter = QSplitter(Qt.Orientation.Horizontal)

        req_w = QWidget()
        rl = QVBoxLayout(req_w)
        rl.setContentsMargins(0, 0, 0, 0)
        rl.addWidget(QLabel("<b>Request</b>"))
        self.req_edit = QTextEdit()
        self.req_edit.setPlainText("GET / HTTP/1.1\nHost: example.com\nUser-Agent: Mozilla/5.0\nAccept: */*\nConnection: close\n\n")
        rl.addWidget(self.req_edit)
        splitter.addWidget(req_w)

        resp_w = QWidget()
        rsl = QVBoxLayout(resp_w)
        rsl.setContentsMargins(0, 0, 0, 0)
        rsl.addWidget(QLabel("<b>Response</b>"))
        self.resp_view = QTextEdit()
        self.resp_view.setReadOnly(True)
        rsl.addWidget(self.resp_view)
        splitter.addWidget(resp_w)

        splitter.setSizes([500, 500])
        layout.addWidget(splitter)

        self.send_btn.clicked.connect(self.send_request)
        self.copy_curl_btn.clicked.connect(self.copy_as_curl)
        self.paste_curl_btn.clicked.connect(self.paste_from_curl)

    def send_request(self):
        raw = self.req_edit.toPlainText()
        self.resp_view.setPlainText("Sending request...")
        QApplication.processEvents()
        try:
            host, port, tls = parse_target_from_request(raw, default_tls=self.tls_chk.isChecked())
            resp = send_raw_request(host, port, raw, use_tls=tls)
            self.resp_view.setPlainText(resp)
        except Exception as e:
            self.resp_view.setPlainText(f"Error: {e}")

    def copy_as_curl(self):
        raw = self.req_edit.toPlainText()
        curl_cmd = request_to_curl(raw, use_tls=self.tls_chk.isChecked())
        QApplication.clipboard().setText(curl_cmd)
        QMessageBox.information(self, "cURL Copied", "cURL command copied to clipboard!")

    def paste_from_curl(self):
        clip_text = QApplication.clipboard().text().strip()
        if not clip_text.lower().startswith("curl"):
            QMessageBox.warning(self, "Invalid cURL", "Clipboard content is not a valid cURL command.")
            return
        try:
            req_text, use_tls = curl_to_request(clip_text)
            self.req_edit.setPlainText(req_text)
            self.tls_chk.setChecked(use_tls)
        except Exception as e:
            QMessageBox.critical(self, "Error Parsing cURL", f"Failed to parse cURL: {e}")


# ---------------------------------------------------------------------------
# Intruder Tab (Burp Style)
# ---------------------------------------------------------------------------
class IntruderTab(QWidget):
    def __init__(self):
        super().__init__()
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)

        sub_tabs = QTabWidget()
        sub_tabs.setObjectName("sub_tabs")

        # Positions & Template
        pos_widget = QWidget()
        p_box = QVBoxLayout(pos_widget)
        p_box.setContentsMargins(8, 8, 8, 8)
        p_box.addWidget(QLabel("<b>Request Template (Add §marker§ around attack payloads):</b>"))
        self.template_edit = QTextEdit()
        self.template_edit.setPlainText("GET /login?user=§admin§&pass=§1234§ HTTP/1.1\nHost: example.com\nConnection: close\n\n")
        p_box.addWidget(self.template_edit)

        ctrl_row = QHBoxLayout()
        ctrl_row.addWidget(QLabel("Host:"))
        self.host_edit = QLineEdit("example.com")
        ctrl_row.addWidget(self.host_edit)
        ctrl_row.addWidget(QLabel("Port:"))
        self.port_spin = QSpinBox()
        self.port_spin.setRange(1, 65535)
        self.port_spin.setValue(443)
        ctrl_row.addWidget(self.port_spin)
        self.tls_chk = QCheckBox("HTTPS")
        self.tls_chk.setChecked(True)
        ctrl_row.addWidget(self.tls_chk)
        ctrl_row.addWidget(QLabel("Attack:"))
        self.attack_combo = QComboBox()
        self.attack_combo.addItems(["sniper", "battering_ram", "pitchfork"])
        ctrl_row.addWidget(self.attack_combo)
        p_box.addLayout(ctrl_row)

        sub_tabs.addTab(pos_widget, "Positions")

        # Payloads Tab
        pay_widget = QWidget()
        pay_box = QVBoxLayout(pay_widget)
        pay_box.setContentsMargins(8, 8, 8, 8)
        pay_box.addWidget(QLabel("<b>Select Payload List:</b>"))
        self.payload_combo = QComboBox()
        self.payload_combo.addItems(list(COMMON_PAYLOADS.keys()) + ["custom"])
        pay_box.addWidget(self.payload_combo)
        self.custom_pay_edit = QTextEdit()
        self.custom_pay_edit.setPlaceholderText("Custom payloads (one per line)...")
        pay_box.addWidget(self.custom_pay_edit)
        
        self.start_attack_btn = QPushButton("Start attack")
        self.start_attack_btn.setObjectName("primary_btn")
        pay_box.addWidget(self.start_attack_btn)

        sub_tabs.addTab(pay_widget, "Payloads")

        # Results Tab
        res_widget = QWidget()
        res_box = QVBoxLayout(res_widget)
        res_box.setContentsMargins(8, 8, 8, 8)
        self.results_table = QTableWidget(0, 4)
        self.results_table.setHorizontalHeaderLabels(["Payload", "Status", "Length", "Time (ms)"])
        self.results_table.horizontalHeader().setSectionResizeMode(0, QHeaderView.ResizeMode.Stretch)
        res_box.addWidget(self.results_table)

        sub_tabs.addTab(res_widget, "Results")

        layout.addWidget(sub_tabs)
        self.start_attack_btn.clicked.connect(self.run_attack_action)

    def run_attack_action(self):
        template = self.template_edit.toPlainText()
        host = self.host_edit.text().strip()
        port = self.port_spin.value()
        use_tls = self.tls_chk.isChecked()
        attack_type = self.attack_combo.currentText()
        choice = self.payload_combo.currentText()
        
        if choice == "custom":
            payloads = [l for l in self.custom_pay_edit.toPlainText().splitlines() if l.strip()]
        else:
            payloads = COMMON_PAYLOADS.get(choice, [])
        
        if not host or not template:
            return

        try:
            results = run_attack(template, host, port, use_tls, payload_lists=[payloads], attack_type=attack_type)
            self.results_table.setRowCount(0)
            for r in results:
                row = self.results_table.rowCount()
                self.results_table.insertRow(row)
                self.results_table.setItem(row, 0, QTableWidgetItem(r.payload))
                self.results_table.setItem(row, 1, QTableWidgetItem(str(r.status_code)))
                self.results_table.setItem(row, 2, QTableWidgetItem(str(r.length)))
                self.results_table.setItem(row, 3, QTableWidgetItem(str(r.time_ms)))
        except Exception as e:
            QMessageBox.critical(self, "Error", str(e))


# ---------------------------------------------------------------------------
# Decoder, Comparer, Scanner, Sequencer Tabs
# ---------------------------------------------------------------------------
class DecoderTab(QWidget):
    def __init__(self):
        super().__init__()
        layout = QVBoxLayout(self)
        layout.setContentsMargins(8, 8, 8, 8)
        
        row = QHBoxLayout()
        row.addWidget(QLabel("Transform:"))
        self.combo = QComboBox()
        self.combo.addItems(list(dec.TRANSFORMS.keys()))
        row.addWidget(self.combo)
        self.run_btn = QPushButton("Decode / Encode")
        self.run_btn.setObjectName("primary_btn")
        row.addWidget(self.run_btn)
        row.addStretch()
        layout.addLayout(row)

        layout.addWidget(QLabel("<b>Input</b>"))
        self.input_edit = QTextEdit()
        layout.addWidget(self.input_edit)

        layout.addWidget(QLabel("<b>Output</b>"))
        self.output_edit = QTextEdit()
        self.output_edit.setReadOnly(True)
        layout.addWidget(self.output_edit)

        self.run_btn.clicked.connect(self.run_transform)

    def run_transform(self):
        fn = dec.TRANSFORMS[self.combo.currentText()]
        try:
            self.output_edit.setPlainText(fn(self.input_edit.toPlainText()))
        except Exception as e:
            self.output_edit.setPlainText(f"Error: {e}")


class ComparerTab(QWidget):
    def __init__(self):
        super().__init__()
        layout = QVBoxLayout(self)
        layout.setContentsMargins(8, 8, 8, 8)

        splitter = QSplitter(Qt.Orientation.Horizontal)
        self.a_edit = QTextEdit()
        self.a_edit.setPlaceholderText("Item A...")
        self.b_edit = QTextEdit()
        self.b_edit.setPlaceholderText("Item B...")
        splitter.addWidget(self.a_edit)
        splitter.addWidget(self.b_edit)
        layout.addWidget(splitter)

        self.cmp_btn = QPushButton("Words / Words comparison")
        self.cmp_btn.setObjectName("primary_btn")
        layout.addWidget(self.cmp_btn)

        self.res_view = QTextEdit()
        self.res_view.setReadOnly(True)
        layout.addWidget(self.res_view)

        self.cmp_btn.clicked.connect(self.run_cmp)

    def run_cmp(self):
        a, b = self.a_edit.toPlainText(), self.b_edit.toPlainText()
        lines = cmp.diff_lines(a, b)
        out = [f"Similarity: {cmp.similarity_ratio(a, b):.2%}", "-" * 40]
        for tag, la, lb in lines:
            if tag == "equal": out.append(f"  {la}")
            elif tag == "replace": out.append(f"- {la}\n+ {lb}")
            elif tag == "delete": out.append(f"- {la}")
            elif tag == "insert": out.append(f"+ {lb}")
        self.res_view.setPlainText("\n".join(out))


class ScannerTab(QWidget):
    def __init__(self):
        super().__init__()
        self.scanner = Scanner()
        layout = QVBoxLayout(self)
        layout.setContentsMargins(8, 8, 8, 8)

        row = QHBoxLayout()
        row.addWidget(QLabel("Target Host / IP:"))
        self.target_edit = QLineEdit("scanme.nmap.org")
        row.addWidget(self.target_edit)
        self.scan_btn = QPushButton("Start port scan")
        self.scan_btn.setObjectName("primary_btn")
        row.addWidget(self.scan_btn)
        layout.addLayout(row)

        self.results_view = QTextEdit()
        self.results_view.setReadOnly(True)
        layout.addWidget(self.results_view)

        self.scan_btn.clicked.connect(self.run_scan)

    def run_scan(self):
        target = self.target_edit.text().strip()
        if not target: return
        self.results_view.setPlainText(f"Scanning {target}...")
        QApplication.processEvents()
        try:
            ports = self.scanner.scan(target, timeout_ms=500, threads=64)
            lines = [f"Open ports on {target}:"]
            for p in sorted(ports):
                lines.append(f"Port {p:<5} ({COMMON_SERVICE_NAMES.get(p, 'unknown')})")
            self.results_view.setPlainText("\n".join(lines))
        except Exception as e:
            self.results_view.setPlainText(f"Error: {e}")


# ---------------------------------------------------------------------------
# Main Window (Burp Suite Professional Interface)
# ---------------------------------------------------------------------------
class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("Burp Suite Professional v2025.12.5 - Temporary Project - licensed to cyvorax")
        self.resize(1280, 850)

        # Menu Bar
        menu_bar = self.menuBar()
        burp_menu = menu_bar.addMenu("Burp")
        burp_menu.addAction("User options")
        burp_menu.addAction("Exit")

        proj_menu = menu_bar.addMenu("Project")
        proj_menu.addAction("Save project")

        menu_bar.addMenu("Intruder")
        menu_bar.addMenu("Repeater")
        menu_bar.addMenu("View")
        menu_bar.addMenu("Help")

        # Main Workspace Container
        central = QWidget()
        vbox = QVBoxLayout(central)
        vbox.setContentsMargins(0, 0, 0, 0)
        vbox.setSpacing(0)

        # Primary Tabs (Burp Suite Exact Navigation Bar)
        self.main_tabs = QTabWidget()
        
        self.dashboard_tab = DashboardTab()
        self.target_tab = TargetTab(self)
        self.proxy_tab = ProxyTab(self.target_tab)

        self.proxy_tab.signals.passive_finding_logged.connect(self.dashboard_tab.add_finding)

        self.main_tabs.addTab(self.dashboard_tab, "Dashboard")
        self.main_tabs.addTab(self.target_tab, "Target")
        self.main_tabs.addTab(self.proxy_tab, "Proxy")
        self.main_tabs.addTab(IntruderTab(), "Intruder")
        self.main_tabs.addTab(RepeaterTab(), "Repeater")
        self.main_tabs.addTab(DecoderTab(), "Decoder")
        self.main_tabs.addTab(ComparerTab(), "Comparer")
        self.main_tabs.addTab(ScannerTab(), "Scanner")

        vbox.addWidget(self.main_tabs)
        self.setCentralWidget(central)

        # Status Bar
        sb = QStatusBar()
        sb.showMessage("Event log (0) | All issues | Memory: 185.5MB of 3.91GB")
        self.setStatusBar(sb)


def main():
    app = QApplication(sys.argv)
    app.setStyleSheet(STYLESHEET)
    win = MainWindow()
    win.show()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()
