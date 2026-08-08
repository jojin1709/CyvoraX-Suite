# CyvoraX Suite v1.6.2 - Feature Progress & Capabilities

CyvoraX Suite 1.6.2 is a feature-complete enterprise web security testing workbench built on Java 17+ JavaFX & Netty.

---

## 🟢 Complete & Verified Modules in v1.6.0

### 1. 📊 Dashboard & Workspace Management
- [x] Multi-workspace launcher & profile persistence (SQLite)
- [x] Live activity logger & real-time metrics panel
- [x] Findings grouped by severity (High, Medium, Low, Info)
- [x] Running task indicators & CA certificate status monitor
- [x] Quick Start / Stop proxy & Root CA export

### 2. 🔌 Proxy Engine (HTTP History & Intercept)
- [x] High-throughput Netty MITM proxy listener with HTTP/1.1 & HTTP/2 support
- [x] Dynamic per-host HTTPS certificate generation (BouncyCastle)
- [x] Intercept sub-tab with live pause, edit, forward, drop, and browser launcher
- [x] HTTP History table with filters, search bar, scope-only toggle, and highlight tags
- [x] CSV & JSON history exports (All / Selected)
- [x] Rich inspector panes: Headers, Cookies, Params, JSON, JWT, Forms, Meta, and Notes

### 3. 🎯 Target & Site Map
- [x] Tree-view site map endpoint hierarchy
- [x] Global Search across endpoints, requests, responses, notes, and tags
- [x] Endpoint organizer and metadata inspector

### 4. 🔁 Repeater Workbench
- [x] Multi-tab request workspace with custom tab creation
- [x] Raw, Pretty, and Hex response inspectors
- [x] Structured request/response tabs: Headers, Cookies, Params, JSON, JWT, Forms, Meta, Notes
- [x] Copy as cURL and Paste from cURL integration

### 5. 🎯 Intruder & Turbo Intruder
- [x] Fuzzing modes with §marker§ positions
- [x] Payload generators: Simple list, numbers, dates, brute-force
- [x] Async Turbo Intruder with HTTP/2 pipeline mode, RPS controls, and live results grid

### 6. 🕷️ Spider / Crawler & Active Scanner
- [x] Depth-controlled link crawler with JavaScript endpoint extraction
- [x] Passive findings engine & active scanner triggers for XSS, SQLi, LFI, SSRF, Command Injection

### 7. 🧮 Decoder, Comparer & Logger
- [x] Transform suite: Base64, URL, HTML, Hex, Binary, Gzip, hashes, JWT decoder & secret cracker
- [x] Line-level colored row diffing (Comparer)
- [x] Raw traffic logging and text/JSON export (Logger)

### 8. 🤖 AI Vulnerability Assistant & Extensions
- [x] Encrypted provider credentials for Groq, OpenRouter, Cerebras, and Mistral
- [x] Java `ServiceLoader` Plugin API with live reload controls
- [x] HTML & PDF report exporter
