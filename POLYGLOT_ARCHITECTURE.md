# CyvoraX Suite - Polyglot Language Architecture Matrix

This document details the multi-language polyglot architecture for **CyvoraX Suite**, outlining how each programming language is integrated into the core engine.

---

## 1. Multi-Language Overview Matrix

| Language | Role / Purpose | Integration Technique | Core Libraries / Dependecies |
| :--- | :--- | :--- | :--- |
| **Python** 🐍 | Desktop App Controller, GUI, Basic Proxy, Module Logic | Native Execution | `PyQt6`, `asyncio`, `cryptography`, `ctypes` |
| **C** ⚡ | High-Speed Connect Port Scanner | Native Shared Library (`.dll` / `.so`) via `ctypes` | `winsock2.h`, `windows.h`, `pthread.h`, `sys/socket.h` |
| **Java** ☕ | Burp Suite Extension API Bridge (`.jar` compatibility) | `JPype1` / `Py4J` Embedded JVM Bridge | `IBurpExtender`, `IHttpListener`, `IScannerCheck` |
| **C++** 🛡️ | Raw Packet Crafting, SYN Stealth Scanning, OS Fingerprinting | Native DLL / Shared Lib via `ctypes` | `Npcap`, `libpcap`, C++17 Standard Library |
| **C# / .NET** 🟦 | Windows OS Integration, Root CA Auto-Install, WFP Proxy | C# Executable / COM Interop | `System.Security.Cryptography.X509Certificates`, WFP API |
| **JS / TS** 🌐 | Headless SPA Crawling, DOM XSS & CDP Sink Tracing | Playwright Node.js Process IPC | Playwright, Chromium DevTools Protocol (CDP) |
| **Go (Golang)** 🐹 | Nuclei Template Engine Runner & Recon Modules | C-Go Shared Library (`c-archive` / `c-shared`) | Nuclei Engine, Go HTTP/YAML Parser |
| **Rust** 🦀 | Ultra-Fast Memory-Safe Proxy Core Engine | PyO3 Native Python Extension Crate | `tokio`, `hyper`, `rustls`, `pyo3` |
| **QSS / CSS** 🎨 | Burp Suite Professional Dark Theme Stylesheet | Qt Engine Property System | Qt Stylesheet Selectors |

---

## 2. Polyglot Architecture Diagram

```text
                               ┌────────────────────────────────┐
                               │   PyQt6 GUI Main Controller    │
                               │   (Python 3.10+ / main.py)     │
                               └───────────────┬────────────────┘
                                               │
        ┌───────────────┬───────────────┬──────┴────────┬───────────────┬───────────────┐
        ▼               ▼               ▼               ▼               ▼               ▼
  ┌───────────┐   ┌───────────┐   ┌───────────┐   ┌───────────┐   ┌───────────┐   ┌───────────┐
  │   Java    │   │    C++    │   │  C# / .NET│   │  JS / Node│   │    Go     │   │   Rust    │
  │ Extensions│   │ SYN Scan  │   │ Windows   │   │ Playwright│   │ Nuclei    │   │ Proxy Core│
  │ (Burp JAR)│   │ (Npcap)   │   │ Cert/WFP  │   │ SPA Engine│   │ Templates │   │ Engine    │
  └───────────┘   └───────────┘   └───────────┘   └───────────┘   └───────────┘   └───────────┘
```

---

## 3. Subsystem Breakdown

### ☕ 1. Java Subsystem (Netty Proxy Engine & Burp Extension Compatibility)
- **Goal**: Autonomous JavaFX & Netty desktop workbench (`java_engine/`) and running official Burp Suite `.jar` extensions (e.g. *Logger++*, *Turbo Intruder*, *Autorize*).
- **Files**: `java_engine/` (Full Netty/JavaFX proxy engine) & `modules/java_bridge.py` (JPype embedded JVM bridge)
- **Mechanism**: Utilizes `JPype1` to initialize a Java Virtual Machine inside the Python process and bind Burp Java interfaces (`IBurpExtender`, `IHttpListener`) to PyQt6 GUI signals, or runs directly as an enterprise JavaFX proxy client.

### 🛡️ 2. C++ Subsystem (Raw Packet & SYN Stealth Scanning)
- **Goal**: High-speed SYN scanning, OS fingerprinting, and raw packet crafting without establishing full TCP connections.
- **File**: `native/syn_scanner.cpp`
- **Mechanism**: Uses `Npcap` (Windows) / `libpcap` (Linux/Mac) to assemble custom IP/TCP headers and sniff response packets.

### 🟦 3. C# / .NET Subsystem (Windows System Helper)
- **Goal**: Seamless Windows root CA certificate installation and Windows Filtering Platform (WFP) invisible proxying.
- **File**: `native/win_sys_helper.cs`
- **Mechanism**: Compiled `.NET` binary interacting directly with `X509Store` to place `cyvorax-ca.crt` in the Trusted Root Certification Authorities store silently.

### 🌐 4. JavaScript / TypeScript Subsystem (SPA Crawling & DOM XSS)
- **Goal**: Crawl complex React/Vue/Angular Single Page Applications and trace DOM XSS payload sinks.
- **File**: `modules/playwright_crawler.py`
- **Mechanism**: Node.js Playwright engine controlled via Python IPC to automate Chromium browser sessions.

### 🐹 5. Go Subsystem (Nuclei Template Runner)
- **Goal**: Execute 5000+ community Nuclei YAML vulnerability scanning templates.
- **File**: `modules/nuclei_bridge.py`
- **Mechanism**: C-Go bridge exposing `scan_target()` from compiled C-shared library (`libnuclei.dll` / `libnuclei.so`).

### 🦀 6. Rust Subsystem (High-Throughput Memory-Safe Proxy Core)
- **Goal**: Zero-overhead async HTTP/1.1 & HTTP/2 MITM proxying for heavy load testing.
- **File**: `native/rust_proxy_core`
- **Mechanism**: Rust `tokio` + `hyper` crate compiled as a native Python C-extension via `PyO3`.

---

<div align="center">
  <b>CyvoraX Suite Polyglot Architecture Defined</b>
</div>
