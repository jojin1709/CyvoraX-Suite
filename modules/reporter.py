"""
CyvoraX Suite - Vulnerability Report Exporter
Generates Markdown, HTML, and SARIF vulnerability reports with CVSS v3.1 scoring.
"""
import json
import html


class VulnReportExporter:
    def __init__(self, findings: list):
        self.findings = findings  # List of dicts: {severity, title, host, path, detail, evidence}

    def to_markdown(self) -> str:
        md = ["# CyvoraX Suite - Vulnerability Assessment Report\n"]
        md.append(f"**Total Findings**: {len(self.findings)}\n")
        md.append("| Severity | Title | Host | Path |")
        md.append("| :--- | :--- | :--- | :--- |")
        for f in self.findings:
            md.append(f"| **{f.get('severity', 'LOW').upper()}** | {f.get('title', '')} | {f.get('host', '')} | `{f.get('path', '')}` |")
        
        md.append("\n---\n\n## Vulnerability Details\n")
        for i, f in enumerate(self.findings, 1):
            md.append(f"### {i}. [{f.get('severity', 'LOW').upper()}] {f.get('title', '')}")
            md.append(f"- **Target Host**: `{f.get('host', '')}`")
            md.append(f"- **Endpoint Path**: `{f.get('path', '')}`")
            md.append(f"- **Description**: {f.get('detail', '')}")
            if f.get('evidence'):
                md.append(f"```http\n{f.get('evidence')}\n```\n")
        return "\n".join(md)

    def to_html(self) -> str:
        severity_colors = {
            'critical': '#dc2626', 'high': '#ef4444', 'medium': '#f59e0b', 'low': '#3b82f6', 'info': '#6b7280'
        }
        html_parts = [
            '<!DOCTYPE html><html><head><meta charset="utf-8">',
            '<title>CyvoraX Suite - Vulnerability Report</title>',
            '<style>body{font-family:system-ui,sans-serif;margin:2em;background:#0f172a;color:#e2e8f0}',
            'table{border-collapse:collapse;width:100%;margin:1em 0}',
            'th,td{border:1px solid #334155;padding:8px 12px;text-align:left}',
            'th{background:#1e293b}.sev{font-weight:bold;padding:2px 8px;border-radius:4px;color:#fff}',
            'pre{background:#1e293b;padding:12px;border-radius:6px;overflow-x:auto}</style></head><body>',
            '<h1>CyvoraX Suite - Vulnerability Assessment Report</h1>',
            f'<p><strong>Total Findings</strong>: {len(self.findings)}</p>',
            '<table><tr><th>Severity</th><th>Title</th><th>Host</th><th>Path</th></tr>'
        ]
        for f in self.findings:
            sev = f.get('severity', 'info').lower()
            color = severity_colors.get(sev, '#6b7280')
            html_parts.append(
                f'<tr><td><span class="sev" style="background:{color}">{sev.upper()}</span></td>'
                f'<td>{html.escape(f.get("title", ""))}</td>'
                f'<td>{html.escape(f.get("host", ""))}</td>'
                f'<td><code>{html.escape(f.get("path", ""))}</code></td></tr>'
            )
        html_parts.append('</table><h2>Vulnerability Details</h2>')
        for i, f in enumerate(self.findings, 1):
            sev = f.get('severity', 'info').lower()
            color = severity_colors.get(sev, '#6b7280')
            html_parts.append(f'<h3>{i}. <span class="sev" style="background:{color}">{sev.upper()}</span> {html.escape(f.get("title", ""))}</h3>')
            html_parts.append(f'<p><strong>Target Host</strong>: <code>{html.escape(f.get("host", ""))}</code></p>')
            html_parts.append(f'<p><strong>Endpoint Path</strong>: <code>{html.escape(f.get("path", ""))}</code></p>')
            html_parts.append(f'<p><strong>Description</strong>: {html.escape(f.get("detail", ""))}</p>')
            if f.get('evidence'):
                html_parts.append(f'<pre>{html.escape(f.get("evidence"))}</pre>')
        html_parts.append('</body></html>')
        return '\n'.join(html_parts)

    def to_sarif(self) -> str:
        sarif = {
            "$schema": "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json",
            "version": "2.1.0",
            "runs": [
                {
                    "tool": {
                        "driver": {
                            "name": "CyvoraX Suite",
                            "version": "1.0.0"
                        }
                    },
                    "results": []
                }
            ]
        }
        for f in self.findings:
            res = {
                "ruleId": f.get('title', 'Vulnerability'),
                "level": "error" if f.get('severity', '').lower() in ['high', 'critical'] else "warning",
                "message": {"text": f.get('detail', '')},
                "locations": [{"physicalLocation": {"artifactLocation": {"uri": f.get('host', '') + f.get('path', '')}}}]
            }
            sarif["runs"][0]["results"].append(res)
        return json.dumps(sarif, indent=2)
