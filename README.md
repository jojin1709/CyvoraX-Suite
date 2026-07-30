<div align="center">

# ⬡ CyvoraX Suite Professional

**An Autonomous, Polyglot Burp-Style Web Security & Penetration Testing Workbench**

CyvoraX Suite is a high-performance, open-source web security toolkit and desktop proxy workbench designed for ethical hackers, penetration testers, and bug bounty researchers. It combines a **Python (PyQt6 + asyncio) & Native C Scanner Core** with an enterprise-grade **Java 17+ JavaFX & Netty Engine**.

---

</div>

> [!TIP]
> **Windows Quick Launch**:
> - **CyvoraX Suite 1.6.0 Executable**: Double-click `dist\CyvoraX Suite.exe` to launch the full JavaFX & Netty desktop workbench.
> - **Developer Launch**: Run `.\java_engine\run.ps1` or build the NSIS installer with `.\java_engine\package-windows.ps1 -Installer`.

---

## Table of Contents

- [Overview](#overview)
- [Architecture & Engine Options](#architecture--engine-options)
- [Unified Feature Matrix](#unified-feature-matrix)
- [Quick Start Guide](#quick-start-guide)
  - [Engine 1: Python PyQt6 + Native C Client](#engine-1-python-pyqt6--native-c-client)
  - [Engine 2: Java 17+ JavaFX Netty Workbench](#engine-2-java-17-javafx-netty-workbench)
- [Building the Windows Setup Wizard (NSIS)](#building-the-windows-setup-wizard-nsis)
- [Interception & Root CA Installation](#interception--root-ca-installation)
- [Project Layout](#project-layout)
- [License & Ethical Use](#license--ethical-use)

---

## Overview

CyvoraX Suite replicates and enhances the core workflow of Burp Suite Professional in a customizable, polyglot application stack. Whether analyzing web APIs, fuzzing parameters, evaluating token randomness, running automated vulnerability scans, or generating audit reports, CyvoraX provides dual runtime environments tailored for speed and flexibility.

---

## Architecture & Engine Options

CyvoraX Suite provides two complementary runtime engines:

1. **Python Engine (`main.py` & `modules/`)**:
   - **Frontend**: PyQt6 with dark mode styling and responsive request/response split inspectors.
   - **Proxy**: `asyncio` MITM HTTP/HTTPS proxy with dynamic per-host TLS termination via `cryptography`.
   - **Native Core**: C multi-threaded socket connect scanner for lightning-fast port discovery (`native/libscanner.dll`).

2. **Java Engine (`java_engine/`)**:
   - **Frontend**: JavaFX desktop UI with multi-theme support (CyvoraX Navy/Teal, Dark, Light).
   - **Proxy Engine**: High-throughput Netty proxy listener with OkHttp client forwarding and SQLite database storage.
   - **Extended Modules**: Async Turbo Intruder, Auth Manager, Session Recorder, Spider crawler, AI assistant settings, and HTML/PDF reporting.

---

## Unified Feature Matrix

| Subsystem / Feature | Python PyQt6 Engine | Java Netty/JavaFX Engine |
| :--- | :--- | :--- |
| **MITM Intercepting Proxy** | `asyncio` proxy, dynamic CA TLS termination, live request pause/edit/drop | Netty proxy, HTTP/1.1 & HTTP/2, dynamic per-host CA, scope filters |
| **Repeater** | Multi-tab raw HTTP editor with response diffing | Multi-tab raw HTTP editor with per-tab history and raw/pretty/hex views |
| **Intruder Engine** | Sniper, Battering Ram, Pitchfork attacks with §marker§ placement | Simple list, numbers, dates, brute-force generators + anomaly filters |
| **Turbo Intruder** | High-concurrency async fuzzing | Async fuzzing, HTTP/2 pipeline mode, RPS/concurrency controls |
| **Active & Passive Scanner** | XSS, SQLi, SSRF, LFI, Open Redirect, Sensitive Data, CORS, SRI | Passive findings + active scan triggers for XSS, SQLi, LFI, SSRF, Command Injection |
| **Sequencer** | Shannon entropy analysis on token samples | Token randomness evaluation |
| **Decoder / Encoder Suite** | Base32/58/85, Hex, JWT re-sign & dictionary cracker, HMAC, Gzip/Brotli | Base64, URL, HTML, Hex, Binary, Gzip, hashes, JWT, smart decode |
| **Comparer & Match/Replace** | Line diffing & live regex body/header rewrite rules | Colored row diffing + persisted header/cookie/body TSV rules |
| **Auth Manager & Sessions** | Bearer token switching & cookie jar stub | Accounts manager, cookie jars, host patterns, expiration monitoring |
| **Spider / Site Map** | Site map hierarchy inspector | Depth-controlled link crawler, JS-link extraction toggle, sitemap export |
| **AI Vulnerability Assistant**| Planned Python UI bindings | Groq, OpenRouter, Cerebras, & Mistral encrypted local AI profile integration |
| **Extensibility & Plugins** | `JPype1` JVM bridge stub (`modules/java_bridge.py`) | Java `ServiceLoader` Plugin Manager with live reload |
| **Report Generation** | Text & JSON exports | HTML & PDF report generator for findings, evidence, and notes |

---

## Quick Start Guide

### Engine 1: Python PyQt6 + Native C Client

#### Prerequisites
- **Python 3.10+**
- **PyQt6 & Cryptography**

```bash
# 1. Install Python dependencies
pip install -r requirements.txt

# 2. (Optional) Compile native C scanner DLL
# Windows:
gcc -O2 -shared -o native/libscanner.dll native/scanner_win.c -lws2_32 -mwindows

# 3. Launch Python CyvoraX Suite
python main.py
```

### Engine 2: Java 17+ JavaFX Netty Workbench

#### Prerequisites
- **Java 17+ JDK** (configured in `%JAVA_HOME%`)
- **Maven** (bundled in `tools/apache-maven-3.9.14` or system Maven)

```powershell
# Navigate to the Java Engine directory
cd java_engine

# Run using Maven
mvn clean package
mvn javafx:run

# Or run via helper script
.\run.ps1
```

---

## Building the Windows Setup Wizard (NSIS)

CyvoraX Suite includes an NSIS-based branded Windows installer builder:

```powershell
cd java_engine
.\package-windows.ps1 -Installer
```

**Output**: `java_engine\target\CyvoraX-Setup-1.6.0.exe`

The setup builder detects pre-existing installations, creates profile backups under `%USERPROFILE%\.cyvorax-suite`, and configures start menu shortcuts with branded assets from `assets/logos/CyvoraX.ico`.

---

## Interception & Root CA Installation

1. Launch either CyvoraX engine and navigate to the **Proxy** tab (default port: `8080`).
2. Export or locate the CA certificate:
   - **Python**: `certs/cyvorax-ca.crt` (generated on first launch)
   - **Java**: Export CA button on the Dashboard tab
3. Import the generated certificate into your web browser or system **Trusted Root Certification Authorities** store.
4. Configure your browser proxy to `127.0.0.1:8080`.

---

## Project Layout

```text
cyvorax-suite/
├── main.py                     # Python PyQt6 GUI & Main App Controller
├── modules/                    # Python Modules (proxy_core, intruder, active_scanner, decoder)
├── native/                     # High-Speed Native C Scanner DLLs
├── java_engine/                # Enterprise Java 17+ JavaFX & Netty Proxy Engine
│   ├── pom.xml                 # Maven project descriptor
│   ├── src/                    # Java source code (com.venomproxy)
│   ├── installer/              # NSIS installer script & configurations
│   ├── package-windows.ps1     # jpackage & NSIS packaging script
│   └── run.ps1                 # Launch helper script
├── assets/                     # Unified Visual & Branding Assets
│   ├── logos/                  # Desktop icons & PNG logo variants
│   └── images/                 # Installer banners & dialog bitmaps
├── tools/                      # External Tools (Maven, ffuf, katana)
├── certs/                      # Dynamic CA Certificate Store
├── test_target/                # Intentionally Vulnerable Server for Testing
├── dist/                       # PyInstaller Standalone Windows Build
├── README.md                   # Single Master CyvoraX Suite Documentation
├── POLYGLOT_ARCHITECTURE.md    # Multi-Language Architecture Matrix
├── ROADMAP_FULL_GAP_ANALYSIS.md# Burp Suite Pro Parity Gap Analysis
└── PROGRESS.md                 # Real Progress Tracking Log
```

---

## License & Ethical Use

Distributed under the MIT License.

> [!CAUTION]
> **Ethical Research Notice**: CyvoraX Suite performs active TLS termination, fuzzing, and automated vulnerability scanning. Only use CyvoraX against target systems you own or have explicit written permission to audit.
