<div align="center">

# ⬡ CyvoraX Suite Professional v1.6.0

**An Autonomous, Enterprise Web Security & Penetration Testing Workbench**

CyvoraX Suite is a high-performance web security testing workbench designed for ethical hackers, penetration testers, and bug bounty researchers. Built with a high-throughput **Java 17+ JavaFX & Netty Engine** integrated with native **C, C++, C#, Go, and Rust** scanning libraries.

---

</div>

> [!TIP]
> **Quick Launch**:
> - **Desktop App**: Double-click `dist\CyvoraX Suite.exe` or `target\jpackage\CyvoraX Suite\CyvoraX Suite.exe`.
> - **Source Launch**: Run `.\run.ps1` in PowerShell.

---

## Key Features

- **Workspace Launcher**: Manage multiple auditing sessions and project workspaces.
- **MITM Intercepting Proxy**: High-throughput Netty proxy listener with dynamic per-host TLS termination via Bouncy Castle.
- **Repeater Module**: Multi-tab raw HTTP editor with per-tab history, raw/pretty/hex response views, and cURL converters.
- **Intruder & Turbo Intruder**: Multi-payload fuzzing with anomaly filters, HTTP/2 pipeline mode, and RPS controls.
- **Spider / Crawler**: Depth-controlled link crawler with JavaScript endpoint extraction and sitemap export.
- **Active & Passive Scanner**: Real-time auditing for Reflected XSS, SQLi, SSRF, LFI, Command Injection, CORS, and Sensitive Data Exposure.
- **AI Vulnerability Assistant**: Encrypted AI provider profiles (Groq, OpenRouter, Cerebras, Mistral) for auto-triage and analysis.
- **Authentication & Sessions**: Bearer token switching, cookie jar management, and session recovery.
- **Extensibility**: Java `ServiceLoader` Plugin Manager with live reload.
- **Reporting**: HTML and PDF report generator for findings, evidence, and request/response samples.

---

## Quick Start Guide

### Prerequisites
- **Java 17+ JDK** (configured in `%JAVA_HOME%`)
- **Maven 3.9+** (bundled in `tools/apache-maven-3.9.14`)

### Run from Source

```powershell
# Launch CyvoraX Suite directly using the helper script
.\run.ps1

# Or run via Maven commands
mvn clean package
mvn javafx:run
```

---

## Building the Windows Setup Installer (NSIS)

CyvoraX Suite includes an NSIS-based branded Windows installer packaging script:

```powershell
.\package-windows.ps1 -Installer
```

**Outputs**:
- **Application App Image**: `target\jpackage\CyvoraX Suite\CyvoraX Suite.exe`
- **Branded Windows Setup Installer**: `target\CyvoraX-Setup-1.6.0.exe`

---

## Project Structure

```text
cyvorax-suite/
├── src/                        # JavaFX & Netty Proxy Source Code (com.venomproxy)
├── pom.xml                     # Maven Project Descriptor
├── run.ps1                     # One-Click App Launcher Script
├── package-windows.ps1         # jpackage & NSIS Packaging Script
├── installer/                  # NSIS Script & Branded Graphic Assets
├── native/                     # Native C, C++, C#, Go, and Rust Core DLLs
├── modules/                    # Auxiliary Python & JS Helper Modules
├── assets/                     # Unified Icons & Logos (.ico, .png)
├── tools/                      # Downloaded Maven & WiX Build Utilities
├── certs/                      # Dynamic Certificate Authority Store
├── dist/                       # Packaged Standalone Windows Executable
│   └── CyvoraX Suite.exe       # 🚀 Single Executable Launch File
├── README.md                   # Single Master Documentation
├── POLYGLOT_ARCHITECTURE.md    # Multi-Language Architecture Matrix
├── ROADMAP_FULL_GAP_ANALYSIS.md# Master Gap Analysis
└── PROGRESS.md                 # Progress Log
```

---

## License & Ethical Use

Distributed under the MIT License. See `LICENSE` for details.

> [!CAUTION]
> **Ethical Research Notice**: CyvoraX Suite performs active TLS termination, fuzzing, and automated vulnerability scanning. Only use CyvoraX against target systems you own or have explicit written permission to audit.
