# ⬡ CyvoraX Suite v1.6.2 - Polyglot Language Architecture Matrix

This document details the multi-language polyglot architecture for **CyvoraX Suite 1.6.2**, outlining how each programming language and native subsystem is integrated into the primary **Java 17+ JavaFX & Netty Enterprise Workbench**.

---

## 1. Multi-Language Overview Matrix

| Language / Tech | Subsystem & Role | Integration Mechanism | Key Libraries / Native Assets |
| :--- | :--- | :--- | :--- |
| **Java** ☕ | **Primary Workbench & Proxy Engine** | Enterprise Java 17+ JavaFX UI & Netty MITM Proxy Listener | JavaFX 21, Netty 4.1, BouncyCastle TLS, SQLite, Jackson JSON |
| **C** ⚡ | **High-Speed Socket Connect Scanner** | Native DLL (`native/libscanner.dll`) called via JNI / process bridge | `winsock2.h`, `windows.h`, multi-threaded socket discovery |
| **C++** 🛡️ | **SYN Stealth Scanner & Packet Crafting** | Native DLL (`native/libsynscanner.dll`) with raw socket capabilities | Npcap / libpcap C++ API, Winsock raw packets |
| **C# / .NET** 🟦 | **Windows System & Certificate Helper** | C# binary (`native/win_sys_helper.cs`) | `System.Security.Cryptography.X509Certificates` CA store installer |
| **Go (Golang)** 🐹 | **Nuclei Vulnerability Template Runner** | Go binary (`native/nuclei_runner.exe`) built from `native/nuclei_bridge.go` | Nuclei Engine, Go YAML HTTP parser |
| **Rust** 🦀 | **Memory-Safe High-Throughput Core** | Async C-ABI crate (`native/rust_proxy_core/`) | `tokio`, `hyper`, `rustls` high-concurrency proxy crate |
| **JS / Node.js** 🌐 | **Headless SPA Crawler & DOM Inspector** | Node.js Playwright engine (`modules/playwright_crawler.js`) | Playwright Chromium DevTools Protocol (CDP) |
| **Python** 🐍 | **Auxiliary Security Modules & Scanners** | Embedded execution scripts (`modules/`) | Security check routines & standalone test tools |
| **PowerShell** 📜 | **Launch & Packaging Automation** | PowerShell scripts (`run.ps1`, `package-windows.ps1`) | `jlink`, `jpackage`, Maven build automation |
| **NSIS** ⚙️ | **Branded Windows Setup Installer** | Nullsoft Scriptable Install System (`installer/cyvorax.nsi`) | Branded Windows setup installer generator |
| **CSS / QSS** 🎨 | **Theme System & Design Tokens** | JavaFX CSS Stylesheets (`src/main/resources/styles/`) | Dark Navy/Teal, Midnight, Hacker, Light, and OLED themes |
| **XML / Maven** 📦 | **Project Descriptor & Build Engine** | Maven Project Object Model (`pom.xml`) | Apache Maven 3.9+ dependency management |

---

## 2. Polyglot Architecture Diagram

```text
                               ┌────────────────────────────────────────┐
                               │   CyvoraX Suite 1.6.2 Core Workbench   │
                               │  (Java 17+ JavaFX & Netty Engine Main) │
                               └───────────────────┬────────────────────┘
                                                   │
        ┌───────────────┬───────────────┬──────────┴────┬───────────────┬───────────────┬───────────────┐
        ▼               ▼               ▼               ▼               ▼               ▼               ▼
  ┌───────────┐   ┌───────────┐   ┌───────────┐   ┌───────────┐   ┌───────────┐   ┌───────────┐   ┌───────────┐
  │  C DLL    │   │  C++ DLL  │   │ C# .NET   │   │  Go Binary│   │ Rust Core │   │ JS Crawler│   │  Python   │
  │ Connect   │   │ SYN Stealth│   │ Root CA   │   │ Nuclei    │   │ Async     │   │ Playwright│   │ Security  │
  │ Scanner   │   │ Scanner   │   │ Installer │   │ Engine    │   │ Proxy     │   │ SPA Engine│   │ Modules   │
  └───────────┘   └───────────┘   └───────────┘   └───────────┘   └───────────┘   └───────────┘   └───────────┘
```

---

## 3. Subsystem Breakdown

### ☕ 1. Java Subsystem (Primary Engine & UI)
- **Role**: Serves as the main desktop user interface, Netty proxy listener, workspace launcher, SQLite database store, and plugin manager.
- **Entry Point**: `com.venomproxy.Main` (`src/main/java/com/venomproxy/Main.java`)
- **Packaging**: Packaged into `dist/CyvoraX Suite.exe` via `package-windows.ps1`.

### ⚡ 2. C Native Scanner
- **Role**: Multi-threaded TCP connect port scanner.
- **File**: `native/scanner_win.c` -> Compiled to `native/libscanner.dll`.

### 🛡️ 3. C++ Raw Packet & SYN Stealth Scanner
- **Role**: Crafts raw TCP SYN packets for non-blocking stealth port discovery.
- **File**: `native/syn_scanner.cpp` -> Compiled to `native/libsynscanner.dll`.

### 🟦 4. C# / .NET Windows CA Helper
- **Role**: Automatically installs `cyvorax-ca.crt` into the Windows Trusted Root Certification Authorities store.
- **File**: `native/win_sys_helper.cs`.

### 🐹 5. Go Nuclei Template Runner
- **Role**: Executes 5,000+ community Nuclei YAML vulnerability templates against target endpoints.
- **File**: `native/nuclei_bridge.go` -> Compiled to `native/nuclei_runner.exe`.

### 🦀 6. Rust High-Throughput Proxy Core
- **Role**: Async memory-safe proxy crate for low-overhead traffic forwarding.
- **File**: `native/rust_proxy_core/`.

### 🌐 7. JavaScript Playwright SPA Crawler
- **Role**: Headless Chrome crawler for React/Vue/Angular Single Page Applications.
- **File**: `modules/playwright_crawler.js`.

---

<div align="center">
  <b>CyvoraX Suite 1.6.2 Polyglot Architecture Updated</b>
</div>
