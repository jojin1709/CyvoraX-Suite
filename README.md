# CyvoraX Suite

CyvoraX Suite is a Java desktop HTTP/HTTPS proxy workbench for authorized personal security research.

## Stack

- Java 17+ source target, tested with Java 25.
- JavaFX desktop UI.
- Netty proxy listener with HTTP forwarding and HTTPS CONNECT tunneling.
- OkHttp for Repeater, Intruder, and active scan requests.
- SQLite JDBC history/findings/log storage.
- Bouncy Castle CA certificate generation/export.
- Dynamic per-host HTTPS certificates for in-scope interception when Intercept is enabled.
- Maven build and `jpackage` packaging path.
- CyvoraX branded window and Windows package icon.

## Run

```powershell
mvn clean package
mvn javafx:run
```

If Maven is not installed globally, use a local Maven binary and run its `mvn.cmd` from this folder.

This repo also includes a helper:

```powershell
.\run.ps1
```

Optional external engines:

```powershell
.\download-tools.ps1
```

This downloads `ffuf.exe` and `katana.exe` into `tools/ffuf` and `tools/katana`. CyvoraX Suite still works with its Java engines if these binaries are absent.

## Package On Windows

After `mvn clean package`, create a Windows app image:

```powershell
jpackage `
  --type app-image `
  --name "CyvoraX Suite" `
  --input target `
  --main-jar cyvorax-suite-1.0.0.jar `
  --main-class com.venomproxy.Main `
  --icon src\main\resources\icons\cyvorax.ico `
  --dest target\jpackage
```

To create an installer `.exe`, install the WiX Toolset first, then use:

```powershell
jpackage `
  --type exe `
  --name "CyvoraX Suite" `
  --input target `
  --main-jar cyvorax-suite-1.0.0.jar `
  --main-class com.venomproxy.Main `
  --icon src\main\resources\icons\cyvorax.ico `
  --dest target\jpackage
```

Or use the helper:

```powershell
.\package-windows.ps1
.\package-windows.ps1 -Installer
```

## Features

- Dashboard with proxy state, request count, host count, finding count, and CA export.
- Proxy tab with intercept on/off, editable request view, forward, and drop.
- HTTP History with filters, scope-only mode, request/response viewers, context actions, save, CSV/JSON export.
- Repeater with multiple tabs, raw request editor, raw/pretty/hex response views, per-tab history.
- Intruder with attack type selector, simple list, numbers, dates, brute-force payload generation, wordlist loading, anomaly filters, and results table.
- Turbo Intruder with async fuzzing, race mode, HTTP/2 pipeline mode, RPS/concurrency controls, and live results.
- Spider / Crawler with depth control, JavaScript-link extraction toggle, sitemap export, and history/scope integration.
- HTTP History protocol column showing HTTP/1.1, HTTP/2, or WS where detected.
- Scanner with passive findings and active scan trigger for in-scope targets, including reflected XSS, SQLi error, path traversal, SSRF, open redirect, and command injection indicators.
- Decoder with Base64, URL, HTML, Hex, Binary, Gzip, hashes, JWT, and smart decode.
- Comparer with line-level diff output and colored added/removed/changed rows.
- Logger with raw traffic log, search, TXT export, and JSON export.
- Scope control include/exclude rules, wildcard rules, and `regex:` rules.
- Plugin API using Java `ServiceLoader` from the user plugins folder, plus Plugin Manager enable/disable/reload controls.

## HTTPS Notes

CyvoraX Suite exports a local CA certificate and can dynamically issue per-host certificates for in-scope HTTPS interception when Intercept is enabled. Install the exported CA certificate only in browsers or test clients you control. Keep active scanning and interception limited to systems you own or have permission to test.
