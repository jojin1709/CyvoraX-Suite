<div align="center">

<img src="assets/logos/CyvoraX_128x128.png" alt="CyvoraX Suite Logo" width="110" />

# CyvoraX Suite Professional v1.6.1

**An Autonomous, Enterprise Web Security & Penetration Testing Workbench**

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE-MIT)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)
[![Build](https://img.shields.io/badge/Build-v1.6.1%20Passing-brightgreen.svg)](#quick-start)
[![Polyglot Engine](https://img.shields.io/badge/Polyglot-Java%20%7C%20C%2F%2B%2B%20%7C%20Go%20%7C%20Rust%20%7C%20C%23-blue.svg)](#polyglot-architecture)
[![JavaFX](https://img.shields.io/badge/JavaFX-21-orange.svg)](#architecture)

CyvoraX Suite is a high-performance web security testing workbench designed for ethical hackers, penetration testers, and bug bounty researchers. Built with a high-throughput **Java 17+ JavaFX & Netty Engine** integrated with 12 native **C, C++, C#, Go, and Rust** security scanning engines.

**This repository contains the full CyvoraX Suite core engine, launcher, and polyglot native module specifications.**

---

</div>

> [!TIP]
> **Quick Launch**:
> - **Desktop Setup Installer**: Run `target\CyvoraX-Setup-1.6.1.exe` for full installation with desktop shortcuts and version-aware upgrade management.
> - **Standalone Executable**: Double-click `dist\CyvoraX Suite.exe` or `target\jpackage\CyvoraX Suite\CyvoraX Suite.exe`.
> - **Developer Source Launch**: Run `.\run.ps1` in PowerShell or `mvn javafx:run`.

---

## Table of Contents

- [What is CyvoraX Suite?](#what-is-cyvorax-suite)
- [Why CyvoraX Suite Exists](#why-cyvorax-suite-exists)
- [CyvoraX Suite in Action](#cyvorax-suite-in-action)
- [Quick Start](#quick-start)
- [Key Capabilities](#key-capabilities)
- [Polyglot Architecture](#polyglot-architecture)
- [Documentation](#documentation)
- [Safety & Rules of Engagement](#safety--rules-of-engagement)
- [License and Proprietary Notice](#license)
- [Contributing](#contributing)

---

## What is CyvoraX Suite?

CyvoraX Suite is an advanced penetration testing and interception proxy workbench developed for professional security auditors and ethical researchers. It provides full control over HTTP/HTTPS traffic, automated parameter fuzzing, target site mapping, dynamic SSL/TLS certificate generation, and polyglot scanning integrations.

Combining a zero-copy **Netty 4.1 MITM Proxy Engine** with a sleek **JavaFX 21 Dark Navy/Teal UI**, CyvoraX Suite delivers real-time HTTP inspection, Turbo Intruder multi-threaded payload generation, automated vulnerability detection, and AI-assisted payload analysis.

---

### Why CyvoraX Suite Exists

Modern web security testing demands ultra-high throughput without sacrificing visual precision or multi-language flexibility. Traditional proxy workbenches suffer from memory bloat and single-language limitations.

CyvoraX Suite closes this gap by coupling a lightweight Java core with compiled native binaries (**C, C++, C#, Go, Rust, Python, JavaScript**). This polyglot architecture lets researchers harness fast native tools like `ffuf` and `katana` directly within a unified GUI workspace.

---

## CyvoraX Suite in Action

| Module | Description | Core Capabilities |
| --- | --- | --- |
| **Dashboard** | Real-time security metrics & active scan monitoring | Uptime, request volume, active proxy sessions, vulnerability findings overview. |
| **Proxy & Intercept** | Netty-powered MITM HTTP/HTTPS proxy listener | Per-host Bouncy Castle CA certificate generation, request/response intercept, drop/forward controls. |
| **Target & Site Map** | Hierarchical host & endpoint structure mapping | Tree navigation, response history, in-scope filtering, endpoint parameter analysis. |
| **Repeater** | Raw HTTP request & response editor | Multi-tab editing, cURL converter, protocol selection (HTTP/1.1 & HTTP/2), pretty/raw/hex viewers. |
| **Intruder** | Automated payload fuzzing engine | Attack modes (Sniper, Pitchfork, Cluster Bomb), anomaly detection, speed controls. |
| **Spider & Scanner** | Dynamic web crawler & active vulnerability scanner | Katana & Ffuf integration, XSS, SQLi, SSRF, IDOR, and auth bypass detection. |
| **Decoder & Comparer** | Encoding utility & visual text/binary diffing | URL, Base64, HTML, Hex, JWT decoder, side-by-side visual diff tool. |
| **Session Recorder** | Macro & automated login flow playback | Step-by-step transaction recording and session token refresh loops. |
| **AI Assistant** | Integrated LLM security provider | Supports Groq, OpenRouter, Cerebras, and Mistral for automated payload analysis. |

---

## Quick Start

### Prerequisites

- **Java 17+ OpenJDK**: Installed and set as `JAVA_HOME`.
- **Maven 3.9+**: Included in `./tools/apache-maven-3.9.14` or system `PATH`.
- **Windows OS**: Primary supported platform for `.exe` and setup installers.

### Running from Source

```powershell
# Clone the repository
git clone https://github.com/jojin1709/CyvoraX-Suite.git
cd CyvoraX-Suite

# Run using the automated PowerShell script
.\run.ps1
```

### Building the Setup Installer

```powershell
# Build standalone JPackage app image and NSIS Installer
.\package-windows.ps1 -Installer
```

---

## Key Capabilities

- **MITM Interception Proxy**: Dynamic Bouncy Castle Root CA generation with transparent SSL/TLS interception for all modern browsers.
- **Turbo Fuzzing Intruder**: Multi-threaded payload generator with custom anomaly detection, status filters, and length comparison.
- **Polyglot Execution Engine**: Run native C, C++, C#, Go, and Rust binary tools seamlessly inside the JavaFX runtime.
- **Rich Navy/Teal Theme**: Modern high-contrast dark UI with responsive `.cx-panel` card elevation, status bar action toasts, and TableView zebra striping.
- **Session Management**: Automatic state persistence across database workspaces with SQLite database backing.

---

## Polyglot Architecture

CyvoraX Suite uses a decoupled polyglot framework that orchestrates specialized native tools across 7 core programming languages:

```text
               ┌──────────────────────────────────────────────┐
               │    CyvoraX Suite Core Engine (Java 17)       │
               │    JavaFX 21 UI  |  Netty MITM Proxy         │
               └──────────────────────┬───────────────────────┘
                                      │
           ┌──────────────────────────┼──────────────────────────┐
           │                          │                          │
           ▼                          ▼                          ▼
 ┌──────────────────┐       ┌──────────────────┐       ┌──────────────────┐
 │ C / C++ Engine   │       │ Go Native Tools  │       │ Rust Scanner     │
 │ Raw Socket Scan  │       │ Katana / Ffuf    │       │ Memory Safety    │
 └──────────────────┘       └──────────────────┘       └──────────────────┘
           │                          │                          │
           ▼                          ▼                          ▼
 ┌──────────────────┐       ┌──────────────────┐       ┌──────────────────┐
 │ C# .NET Module   │       │ Python Scripting │       │ Node.js Analyzer │
 │ Windows Auth     │       │ Exploitation     │       │ DOM Analysis     │
 └──────────────────┘       └──────────────────┘       └──────────────────┘
```

---

## Contributing

We welcome community contributions to improve CyvoraX Suite! For guidelines on bug reports, code contributions, and rules of engagement, please read our [CONTRIBUTING.md](CONTRIBUTING.md) guide.

1. **Fork the Repository**: Create your own feature branch (`git checkout -b feature/amazing-feature`).
2. **Commit Changes**: Follow clear commit messages (`git commit -m 'Add new vulnerability rule'`).
3. **Verify Build**: Ensure code compiles cleanly with `.\tools\apache-maven-3.9.14\bin\mvn.cmd test-compile`.
4. **Submit Pull Request**: Open a PR describing your changes and test coverage.

---

## License

**Copyright © 2026 CyvoraX (Jojin).**

CyvoraX Suite is dual-licensed under your choice of either:
- **[MIT License](LICENSE-MIT)**: Permissive open-source license.
- **[Apache License, Version 2.0](LICENSE)**: Open-source license with patent protection.

Licensed strictly for authorized penetration testing and security research on targets you own or have explicit written permission to audit.
