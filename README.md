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

Requirements:

- Java 17+ JDK in `%JAVA_HOME%`.
- JavaFX jmods either under `%JAVA_HOME%\jmods`, in `%JAVAFX_JMODS%`, or in `tools\javafx-jmods-21.0.6\jmods`.
- NSIS from https://nsis.sourceforge.io for the branded, upgrade-aware setup wizard.
- The `installer/` folder must stay present because it contains `CyvoraX.ico`, `cyvorax_banner.bmp`, `cyvorax_dialog.bmp`, `LICENSE.txt`, and `cyvorax.nsi`.

Build the jar:

```powershell
mvn clean package
```

Build a bundled app image with a jlink runtime:

```powershell
.\package-windows.ps1
```

Output:

```text
target\jpackage\CyvoraX Suite\CyvoraX Suite.exe
```

Build the NSIS setup wizard:

```powershell
.\package-windows.ps1 -Installer
```

Output:

```text
target\CyvoraX-Setup-1.1.1.exe
```

The helper creates `runtime\` with `jlink`, runs `jpackage` with `--runtime-image runtime`, then signs generated `.exe` files with a self-signed `CN=CyvoraX Suite` certificate when possible. WiX is no longer used for the setup wizard.

If the app image does not open, run:

```powershell
.\launch.bat
```

Startup crashes are written to `%USERPROFILE%\CyvoraX\crash.log`.

## Building The Setup Wizard

Install NSIS from https://nsis.sourceforge.io, then run:

```powershell
.\package-windows.ps1 -Installer
```

The setup wizard output is `target\CyvoraX-Setup-<version>.exe`, for example `target\CyvoraX-Setup-1.1.1.exe`. NSIS is required because the setup wizard performs real upgrade detection, version compatibility checks, shortcut selection, process and locked-file preflight checks, and `%USERPROFILE%\.cyvorax-suite` profile backups before replacing an existing installation.

## GitHub Windows Releases

Creating a tag like `v1.1.1` runs the Windows release workflow. The workflow builds with Maven, downloads JavaFX jmods, creates a trimmed runtime with `jlink`, builds the app image with `jpackage`, builds the NSIS setup wizard, removes old JAR release assets, and uploads only the Windows installer:

```text
CyvoraX-Setup-1.1.1.exe
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
- Match & Replace with persisted header/cookie/body/regex rules and TSV import/export.
- Request annotations with notes, comments, tags, colors, and favorites.
- Global Search across requests, responses, findings, logs, notes, comments, and tags.
- Target Site Map with host/path hierarchy and endpoint metadata.
- Organizer for saved requests, notes, categorized entries, and exports.
- Session Recorder with start/stop/save, persisted entries, replay, and export.
- Authentication Manager with multiple accounts, bearer-token switching, cookie jars, host patterns, and expiration monitoring.
- Reports tab with HTML/PDF exports for findings, evidence, notes, and request/response samples.
- Runtime theme switching for CyvoraX Navy/Teal, Dark, and Light themes.
- Detachable tabs with remembered window sizes and keyboard shortcuts for core modules.
- Scanner with passive findings and active scan trigger for in-scope targets, including reflected XSS, SQLi error, path traversal, SSRF, open redirect, and command injection indicators.
- Decoder with Base64, URL, HTML, Hex, Binary, Gzip, hashes, JWT, and smart decode.
- Comparer with line-level diff output and colored added/removed/changed rows.
- Logger with raw traffic log, search, TXT export, and JSON export.
- Scope control include/exclude rules, wildcard rules, and `regex:` rules.
- Plugin API using Java `ServiceLoader` from the user plugins folder, plus Plugin Manager enable/disable/reload controls.

## HTTPS Notes

CyvoraX Suite exports a local CA certificate and can dynamically issue per-host certificates for in-scope HTTPS interception when Intercept is enabled. Install the exported CA certificate only in browsers or test clients you control. Keep active scanning and interception limited to systems you own or have permission to test.

`src/main/resources/certs/cacert.der` is bundled as a project certificate asset. The interception CA still requires a matching private key, so CyvoraX generates and stores its local signing CA under the user profile unless a future build adds secure private-key import.
