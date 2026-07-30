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
