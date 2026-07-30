# CyvoraX Suite - Comprehensive Gap Analysis & Master Feature Roadmap

This document outlines the complete list of missing features, enhancements, and advanced capabilities required to elevate **CyvoraX Suite** from a functional core toolkit into a full enterprise-grade application security testing suite (on par with Burp Suite Professional, OWASP ZAP, and Caido).

---

## Table of Contents
1. [Core Proxy & Intercept Engine (25 Items)](#1-core-proxy--intercept-engine)
2. [Target, Site Map & Scope Management (20 Items)](#2-target-site-map--scope-management)
3. [Automated Crawler & Spider Engine (15 Items)](#3-automated-crawler--spider-engine)
4. [Active & Passive Scanner Engine (30 Items)](#4-active--passive-scanner-engine)
5. [Intruder & Fuzzing Engine (25 Items)](#5-intruder--fuzzing-engine)
6. [Repeater & HTTP Client (15 Items)](#6-repeater--http-client)
7. [Session Handling, Authentication & Macros (15 Items)](#7-session-handling-authentication--macros)
8. [Extender, Scripting & Plugin API (15 Items)](#8-extender-scripting--plugin-api)
9. [Decoder, Encoder & Cryptography Suite (15 Items)](#9-decoder-encoder--cryptography-suite)
10. [Sequencer & Token Analysis (10 Items)](#10-sequencer--token-analysis)
11. [Match & Replace / Rule Engine (10 Items)](#11-match--replace--rule-engine)
12. [Out-of-Band Vulnerability Testing / Collaborator (10 Items)](#12-out-of-band-vulnerability-testing--collaborator)
13. [Reporting, Vuln Management & Integrations (15 Items)](#13-reporting-vuln-management--integrations)
14. [UI/UX, Performance & Enterprise Engine (15 Items)](#14-uiux-performance--enterprise-engine)

---

## 1. Core Proxy & Intercept Engine
- [ ] **HTTP/2 Support**: Intercept, decode, and modify HTTP/2 multiplexed streams and binary frames.
- [ ] **HTTP/3 (QUIC) Support**: Intercept UDP-based HTTP/3 traffic.
- [ ] **WebSocket Interception**: Real-time intercept, pause, edit, and drop of WebSocket text and binary frames.
- [ ] **Server-Sent Events (SSE) Inspection**: Live streaming event parser and logger.
- [ ] **gRPC & Protobuf Inspection**: Native deserialization and editing of gRPC binary payloads.
- [ ] **GraphQL Support**: Query auto-parsing, operation name extraction, and GraphQL IDE integration.
- [ ] **Upstream Proxy Chaining**: SOCKS4, SOCKS5, and HTTP proxy forwarding with authentication (NTLM/Kerberos).
- [ ] **Client SSL Certificate Management**: Client PKCS#12 (.p12/.pfx) certificate support per host.
- [ ] **SNI & Hostname Spoofing**: Support for custom TLS Server Name Indication header manipulation.
- [ ] **TLS Fingerprint (JA3/JA4) Spoofing**: Custom TLS handshake signatures to bypass bot protection (Cloudflare, Akamai).
- [ ] **Custom CA Export/Import**: Export root CA keys in PEM, DER, and PKCS12 formats for mobile device configuration.
- [ ] **Invisible Proxy Mode**: Non-proxy aware client interception via IPTables/NFTables or Windows WFP routing.
- [ ] **Response Interception Rules**: Configurable rules to intercept responses based on status code, mime-type, or header content.
- [ ] **Bandwidth Throttling / Rate Limiting Simulator**: Simulate 3G, 4G, and slow connections.
- [ ] **Auto-Decode Gzip/Brotli/Zstd**: Automatic decompressed view of compressed bodies with transparent re-compression.
- [ ] **Automatic Content-Length Recalculation**: Update Content-Length header when body content is edited in Intercept.
- [ ] **Automatic Chunked Transfer Encoding Handling**: Parse and stitch `Transfer-Encoding: chunked` bodies.
- [ ] **Raw Hex / Binary Inspector Editor**: Full hex editor pane for binary request/response bodies.
- [ ] **Interception Keybinding Shortcuts**: Global hotkeys for Forward (`Ctrl+F`), Drop (`Ctrl+D`), Intercept Toggle (`Ctrl+I`).
- [ ] **Proxy Listener Configuration UI**: Bind multiple listeners on custom interfaces and ports.
- [ ] **Regex History Search & Filter Bar**: Filter HTTP history by Method, Status Code, Extension, and Search terms.
- [ ] **Color Highlight History Rows**: Tag history items with custom highlight colors (Red, Green, Blue, Yellow).
- [ ] **Annotations / Notes on History Items**: Add custom analyst notes to any proxy history entry.
- [ ] **Export/Import History to CSV/HAR**: Export proxy logs as HTTP Archive (HAR) or CSV formats.
- [ ] **Replay Selected Request**: One-click option to resend any history item straight to Repeater or Intruder.

---

## 2. Target, Site Map & Scope Management
- [ ] **Tree-Based Site Map**: Full hierarchical folder structure tree breakdown of target hosts and path endpoints.
- [ ] **Include/Exclude Target Scope Rules**: Regex and prefix-based scope definition (In-Scope vs Out-of-Scope).
- [ ] **Advanced Scope Filtering**: Option to hide out-of-scope items from Proxy History, Intruder, and Logger.
- [ ] **URL Query Parameter Inspector**: Breakdown table of query parameters for selected endpoints.
- [ ] **Form Parameter Inspector**: Table view of POST body parameters (`application/x-www-form-urlencoded` and `multipart/form-data`).
- [ ] **JSON Structure Tree Visualizer**: Interactive JSON tree viewer for API request/response bodies.
- [ ] **Cookie Store Viewer**: Live persistent cookie jar showing domain, path, expiry, and HttpOnly/Secure flags.
- [ ] **Site Map Delta / Differences**: Compare site map snapshots between different test runs or role accounts.
- [ ] **Endpoint Status Tracking**: Mark endpoints as "Not Tested", "Audited", "Vulnerable", or "Ignored".
- [ ] **Export Site Map as OpenAPI/Swagger**: Auto-generate OpenAPI 3.0 specification from observed traffic.
- [ ] **Auto-Discovery of Unlinked API Endpoints**: Infer hidden paths from JS source code parsing.
- [ ] **Scope-Based Interception**: Automatically drop or ignore interception for out-of-scope hosts.
- [ ] **Target Technology Fingerprinting**: Identify frameworks (React, Angular, Django, Spring) and server versions.
- [ ] **Favicon MD5/MMH3 Hash Matching**: Automatic framework identification via favicon hashes.
- [ ] **Host IP & DNS Resolution Panel**: Display DNS records (A, AAAA, CNAME, MX) per target domain.
- [ ] **robots.txt & sitemap.xml Auto-Parser**: Automatic extraction and parsing of discovery files.
- [ ] **Security.txt Parser**: Auto-fetch security contacts and policy files.
- [ ] **CORS Policy Inspection Panel**: Highlight wildcard (`*`) or unsafe `Access-Control-Allow-Origin` setups.
- [ ] **Content Security Policy (CSP) Evaluator**: Identify bypassable CSP directives.
- [ ] **Subdomain Takeover Checker**: Identify CNAME records pointing to unclaimed cloud services (S3, GitHub Pages).

---

## 3. Automated Crawler & Spider Engine
- [ ] **Automated Headless Browser Crawler**: Chromium-based SPA (Single Page Application) crawler for React/Vue/Angular apps.
- [ ] **DOM Event Triggering**: Auto-click buttons, submit forms, hover elements, and trigger JS event listeners.
- [ ] **Form Auto-Filling**: Intelligent form parameter generation (emails, passwords, search terms, numbers).
- [ ] **Form Submission Handling**: Configurable safety rules to prevent destructive form actions (Delete Account, Logout).
- [ ] **Crawl Depth & Link Count Limits**: Configurable max depth, max requests, and max execution time settings.
- [ ] **JavaScript Link Extractor**: Extract hidden API endpoints and URLs from static `.js` bundle files.
- [ ] **Robots.txt & Sitemap Guided Crawling**: Feed crawler queue directly from robots and sitemaps.
- [ ] **Crawl Status & Queue Progress Dashboard**: Real-time stats on queued URLs, crawled pages, errors, and rate.
- [ ] **Duplicate Page Elimination (De-duplication)**: Cluster pages with identical layout structure but different dynamic parameters.
- [ ] **Automated Login & Auth Persistence during Crawl**: Auto-detect session termination and re-authenticate mid-crawl.
- [ ] **Custom User-Agent & Header Injection**: Custom headers added to all crawler HTTP requests.
- [ ] **Crawl Pause/Resume State**: Save and restore crawler state to disk.
- [ ] **Wayback Machine & AlienVault OTX Integration**: Seed target URLs from historical archive datasets.
- [ ] **Parameter Discovery Engine (Param Miner)**: Uncover hidden GET/POST parameters via brute-force headers and params.
- [ ] **Backup File & Hidden Path Finder**: Wordlist-based fuzzing for `.bak`, `.old`, `.git`, `.env`, and config files.

---

## 4. Active & Passive Scanner Engine
- [ ] **Nuclei Template Engine Support**: Native execution of YAML-based Nuclei vulnerability templates.
- [ ] **Passive Scanning Engine**: Real-time traffic analysis without making extra network requests.
- [ ] **SQL Injection (SQLi) Audit Engine**:
  - Time-based blind SQLi (`SLEEP()`, `WAITFOR DELAY`).
  - Error-based SQLi (MySQL, PostgreSQL, MSSQL, Oracle).
  - Boolean-based blind SQLi validation.
  - Second-order SQL injection checks.
- [ ] **Cross-Site Scripting (XSS) Audit Engine**:
  - Reflected XSS with context-aware payload injection (HTML, Attribute, Script block).
  - DOM-based XSS analysis using headless browser execution traces.
  - Stored XSS validation across multi-step requests.
- [ ] **Server-Side Request Forgery (SSRF) Engine**: Internal network probing and out-of-band DNS/HTTP callback validation.
- [ ] **XML External Entity (XXE) Scanner**: External entity injection testing with local file retrieval probes.
- [ ] **Local & Remote File Inclusion (LFI/RFI)**: Path traversal payloads (`../../../../etc/passwd`) with OS evasion variations.
- [ ] **Command Injection (OS Command Execution)**: Blind time-based and output-reflected command injection payloads.
- [ ] **Server-Side Template Injection (SSTI)**: Polyglot payloads for Jinja2, Twig, Freemarker, Velocity, EJS, Pug.
- [ ] **Insecure Direct Object Reference (IDOR) Scanner**: Auto-swap account tokens/IDs to flag unauthorized access.
- [ ] **Broken Object Level Authorization (BOLA/BFLA)**: Automated API role-permission boundary tests.
- [ ] **JWT Vulnerability Scanner**:
  - `none` algorithm exploit generator.
  - HMAC key brute-forcer using dictionary lists.
  - Key Confusion (RS256 -> HS256) exploit engine.
  - Header injection (JKU / X5U / KID) checks.
- [ ] **CORS Misconfiguration Audit**: Null origin, wildcard origin with credentials, and arbitrary subdomains.
- [ ] **CSRF Vulnerability Inspector**: Check for missing Anti-CSRF tokens, SameSite cookie attributes, and Origin validation.
- [ ] **Clickjacking Test Generator**: Auto-generate HTML PoC page with target iframe and opacity controls.
- [ ] **Host Header Injection Scanner**: Reset password poisoning and cache poisoning via modified `Host` & `X-Forwarded-Host`.
- [ ] **HTTP Request Smuggling (CL.TE / TE.CL)**: Differential response detection for HTTP smuggling techniques.
- [ ] **Open Redirect Scanner**: Parameter manipulation leading to external domain redirects.
- [ ] **SSL/TLS Configuration Auditor**: Weak ciphers, expired certs, Heartbleed, ROBOT, CRIME, POODLE checks.
- [ ] **Subresource Integrity (SRI) Check**: Flag missing integrity hashes on external scripts.
- [ ] **Sensitive Data Exposure Scanner**: Match regex patterns for API Keys, AWS Secret Keys, Private Keys, SSNs, JWTs.
- [ ] **GraphQL Introspection Audit**: Detect enabled introspection queries and suggest schema dumping.
- [ ] **Web Cache Poisoning Audit**: Identify unkeyed headers affecting cached HTTP responses.
- [ ] **File Upload Vulnerability Audit**: Extension bypass (`.php5`, `.phtml`), MIME spoofing, SVG XSS, path traversal in filename.
- [ ] **Mass Assignment / Over-Posting Audit**: Inject extra JSON properties into API payloads.
- [ ] **Rate Limiting / Brute Force Protection Audit**: Identify endpoints lacking rate limits.
- [ ] **Audit Speed Controls**: Fast, Normal, Thorough scanning modes with thread throttle settings.
- [ ] **Custom Passive Inspection Rules Engine**: Write custom regex rules to flag response header/body anomalies.
- [ ] **Scan Pause, Resume & Cancellation**: Full lifecycle state management for long-running audit jobs.
- [ ] **False-Positive Marking**: Mark findings as False Positive, Fixed, or Ignored with analyst notes.

---

## 5. Intruder & Fuzzing Engine
- [ ] **Cluster Bomb Attack Type**: Multi-payload set Cartesian product iteration.
- [ ] **Pitchfork Attack Type**: Multi-payload set synchronized parallel iteration.
- [ ] **Battering Ram Attack Type**: Single payload set duplicated into multiple position markers simultaneously.
- [ ] **Sniper Attack Type**: Sequential single-position replacement.
- [ ] **Payload Encoders & Modifiers**: Apply URL-encoding, Base64, Hex, Hashing, or Prefix/Suffix on the fly per payload item.
- [ ] **Grep - Match Rules**: Match response content using string search or regex patterns.
- [ ] **Grep - Extract Rules**: Extract specific data (e.g. CSRF tokens, session IDs) from responses and display in results table.
- [ ] **Grep - Payloads Rules**: Flag responses that reflect the exact payload injected.
- [ ] **Payload Sets Types**:
  - Simple List (Wordlists).
  - Numbers (Sequential / Random range with step and format).
  - Dates (Date range with custom formatting).
  - Brute Forcer (Custom character set and length range).
  - Null Payloads (Generate N empty requests for load testing).
  - Character Fuzzer (ASCII character ranges).
  - Bit Flipper (Flip bits of a base token).
  - Extension Fuzzer.
- [ ] **Payload Processing Rules Chain**: Chain transformations (e.g., Prefix -> Base64 -> URL Encode).
- [ ] **Intruder Results Filtering & Sorting**: Sort table by status, length, response time, or custom column matches.
- [ ] **Export Attack Results**: Export attack tables to CSV, JSON, or HTML reports.
- [ ] **Intruder Attack Pause & Resume**: Stop and resume fuzzing attacks dynamically.
- [ ] **Concurrency & Thread Throttle Control**: Adjust worker threads (1 to 200) and insert delay intervals (ms) during runtime.
- [ ] **Auto-Scroll to Selected Result**: View live request/response breakdown for selected result row.
- [ ] **Diff Result against Baseline**: Show exact visual diff between baseline response and attack response.
- [ ] **Resource Pool Management**: Share rate-limiting and connection pools across Intruder attacks.
- [ ] **Macro Payload Generation**: Regenerate fresh CSRF token via macro prior to issuing each Intruder request.
- [ ] **Wordlist Manager & Built-in SecLists**: Integrated SecLists dictionary repository (SQLi, XSS, Discovery, Usernames, Passwords).
- [ ] **Payload Bounding / Length Filters**: Skip payloads shorter/longer than given length constraints.
- [ ] **Save Attack Configuration**: Save attack specs (payloads, positions, rules) as `.json` presets.
- [ ] **Request Timing Histogram**: Graphical presentation of server response times across attack payloads.
- [ ] **Status Code Chart**: Pie chart distribution of 2xx, 3xx, 4xx, 5xx responses during attack.

---

## 6. Repeater & HTTP Client
- [ ] **Multi-Tab Repeater Workspace**: Unlimited named tabs (`Repeater 1`, `Repeater 2`, `Auth Test`).
- [ ] **Tab Groups & Color Tags**: Group tabs into color-coded categories (e.g., Admin API, User API).
- [ ] **Request History per Tab (Undo / Redo)**: Step backward and forward through previous request iterations in a tab.
- [ ] **Raw / Headers / Hex / Rendered Views**:
  - **Raw**: Pure text HTTP request/response.
  - **Headers**: Structured table editor for headers.
  - **Hex**: Hexadecimal byte viewer.
  - **Render**: HTML web-view preview pane for response bodies.
- [ ] **Auto-Follow Redirects Toggle**: Option to automatically follow 301, 302, 307, 308 redirects.
- [ ] **Change Request Method Quick Action**: Right-click toggle between GET, POST, PUT, DELETE, PATCH, OPTIONS.
- [ ] **Change Body Encoding Quick Action**: One-click convert URL-encoded form data to JSON and vice-versa.
- [ ] **Copy as cURL / Fetch / Python Request**: Right-click export request to `curl`, `fetch()`, Python `requests`, or PowerShell syntax.
- [ ] **Paste from cURL**: Auto-parse cURL command into Repeater request editor.
- [ ] **Search & Highlight in Request/Response**: `Ctrl+F` text search with regex, case-sensitivity, and count matches.
- [ ] **Response Inspector Tree Viewer**: Interactive JSON/XML tree element inspector.
- [ ] **Timing Breakdown Stats**: DNS resolution, TLS handshake, request upload, first byte (TTFB), and total download time.
- [ ] **Format JSON / Pretty Print**: Auto-indent JSON, XML, or HTML in response view.
- [ ] **Compare with another Repeater Tab**: Side-by-side diff between two Repeater tab responses.
- [ ] **Send to Other Tools Menu**: Right-click send request to Intruder, Comparer, Decoder, or Sequencer.

---

## 7. Session Handling, Authentication & Macros
- [ ] **Macro Recorder**: Record multi-step HTTP request sequences (e.g., Login -> Get OTP -> Enter OTP -> Obtain Token).
- [ ] **Session Handling Rules Engine**: Run macros automatically when specified conditions are met (e.g., 401 Unauthorized received).
- [ ] **Automatic CSRF Token Extraction**: Automatically parse fresh anti-CSRF token from previous response and inject into next request.
- [ ] **Cookie Jar Management**: Centralized session cookie persistence across all tools.
- [ ] **OAuth2 / OIDC Flow Helper**: Auto-fetch and refresh Bearer tokens via Client Credentials or Password grant flows.
- [ ] **AWS SigV4 Signing Automator**: Auto-sign requests with AWS Access Key, Secret Key, and Region.
- [ ] **HMAC Request Signer**: Custom secret key signing algorithm runner for API headers.
- [ ] **Multi-User / Multi-Role Session Manager**: Switch between user roles (Admin, Manager, Victim) with 1 click.
- [ ] **Session Validity Checker**: Periodically test if session is alive; trigger re-login macro if expired.
- [ ] **HTTP Basic / Digest Authentication Handler**: Automatically supply configured HTTP credentials.
- [ ] **NTLM / Kerberos Windows Auth Handler**: Auto-negotiate NTLM/Kerberos handshake headers.
- [ ] **Custom Header Injection Rule**: Universally add headers (e.g., `X-Forwarded-For: 127.0.0.1`) to all outgoing requests.
- [ ] **TOTP (Time-Based 2FA) Generator**: Automatically calculate 6-digit TOTP codes during automated login steps.
- [ ] **Email OTP Interceptor Macro**: Poll local IMAP/POP3 inbox or webhook to fetch 2FA login verification codes.
- [ ] **Session Export & Import**: Save active session state to file and load into future testing sessions.

---

## 8. Extender, Scripting & Plugin API
- [ ] **Python Scripting Extensions (Jython / Pure Python)**: Write custom extension modules in Python.
- [ ] **JavaScript / Node.js Plugin Support**: Write extensions using JavaScript/TypeScript.
- [ ] **Burp Suite Extender API Compatibility Layer**: Adapter to run existing Burp Java extensions (`.jar`).
- [ ] **Event Hooks API**:
  - `on_request(req)` hook.
  - `on_response(req, resp)` hook.
  - `on_intercept(msg)` hook.
  - `on_scanner_finding(finding)` hook.
- [ ] **BApp Store / Plugin Manager UI**: Browse, install, update, and disable extensions from a GUI repository.
- [ ] **Custom Tab GUI Extension API**: Allow plugins to register custom tabs and UI panels.
- [ ] **Custom Context Menu Actions**: Plugins can add entries to the right-click menu.
- [ ] **Custom Payload Generator Plugins**: Register custom payload generation routines for Intruder.
- [ ] **Custom Scanner Check Plugins**: Add domain-specific vulnerability checks via plugins.
- [ ] **Custom Decoder Transform Plugins**: Add custom encryption/decryption modules (e.g. proprietary corporate AES).
- [ ] **REST API for Remote Automation**: Expose HTTP REST API to control CyvoraX programmatically from CI/CD pipelines.
- [ ] **WebSocket API for Live Stream**: Broadcast real-time security events over WebSockets to external tools.
- [ ] **CLI Execution Mode**: Run scans, intruder attacks, and reports entirely headless from terminal scripts.
- [ ] **SDK / Developer Kit Documentation**: Complete developer guide and sample plugin template repository.
- [ ] **Plugin Sandboxing & Permissions**: Limit plugin access to filesystem and network resources for safety.

---

## 9. Decoder, Encoder & Cryptography Suite
- [ ] **Live Interactive Decoding Grid**: Multi-step decoder pipeline showing transform operations in connected chain.
- [ ] **URL Encoding / Decoding**: Standard and All-character URL encoding variants.
- [ ] **HTML Entity Encoding / Decoding**: Decimal, Hex, and Named HTML entity conversions.
- [ ] **Base64 / Base64URL / Base32 / Base58 / Base85**: Universal base conversion suite.
- [ ] **Hex / Octal / Binary / ASCII Conversions**: Integer and byte string formatting.
- [ ] **JWT (JSON Web Token) Editor & Cracker**:
  - Decode Header, Payload, and Signature.
  - Live edit payload claims and re-sign with key.
  - Secret key dictionary brute-force cracker.
- [ ] **Crypto Hash Generators**: MD5, SHA-1, SHA-224, SHA-256, SHA-384, SHA-512, SHA3, RIPEMD160, BLAKE2b.
- [ ] **HMAC Calculator**: Compute HMAC signatures using custom keys and hash functions.
- [ ] **Symmetric Encryption / Decryption Tools**: AES-CBC, AES-GCM, DES, 3DES, Blowfish, RC4 with custom IVs and keys.
- [ ] **Asymmetric RSA / ECC Tool**: RSA encrypt/decrypt, Sign/Verify, and public key conversion.
- [ ] **Gzip / Deflate / Zlib / Brotli Decompressor**: Raw compression stream decoder.
- [ ] **Protobuf Wire Format Decoder**: Convert raw Protobuf binary streams into human-readable text syntax.
- [ ] **Unicode Normalization Tools**: NFC, NFD, NFKC, NFKD transformations for Unicode bypass testing.
- [ ] **Smart Auto-Decode**: Automatically identify encoding types (e.g. Base64 vs Hex vs URL) and decode recursively.
- [ ] **Send Selection to Decoder**: Right-click selected text in any request viewer and send straight to Decoder.

---

## 10. Sequencer & Token Analysis
- [ ] **Live Sample Collector**: Automatically harvest hundreds of session cookies or tokens via HTTP requests.
- [ ] **Manual Token Import**: Paste token lists or upload `.txt` files for offline analysis.
- [ ] **FIPS 140-2 Statistical Randomness Tests**:
  - Monobit test.
  - Poker test.
  - Runs test.
  - Longest run test.
- [ ] **Shannon Entropy Calculation**: Detailed bit-level and character-level entropy analysis.
- [ ] **Character Set Distribution Graph**: Visual bar chart showing character occurrence frequency.
- [ ] **Bit Trend Diagram**: Graph showing randomness distribution per bit position across tokens.
- [ ] **Pattern & Format Auto-Detection**: Detect structured components (e.g. Timestamp + Counter + Hash).
- [ ] **Token Predictability Estimator**: Highlight predictable sequence increments or fixed prefix/suffix bytes.
- [ ] **Comprehensive PDF Randomness Report**: Export statistical charts and verdict to a publication-ready report.
- [ ] **Comparison between Token Sets**: Compare entropy and distribution between two different token sources.

---

## 11. Match & Replace / Rule Engine
- [ ] **Match & Replace Rule Presets**: Pre-configured rules (e.g., Emulate iPhone User-Agent, Disable CSP, Force HTTP/1.0).
- [ ] **Request Header Rewrite Rules**: Match and replace headers, or inject new headers if missing.
- [ ] **Request Body Rewrite Rules**: Regex replacements in request bodies before sending to server.
- [ ] **Response Header Rewrite Rules**: Strip security headers (`Content-Security-Policy`, `X-Frame-Options`, `HSTS`).
- [ ] **Response Body Rewrite Rules**: Enable disabled HTML buttons, unhide hidden input fields, reveal client-side checks.
- [ ] **Scope-Restricted Rules**: Limit match & replace rules to specific in-scope hosts.
- [ ] **Regex Capture Groups in Replacements**: Use `$1`, `$2` back-references in replacement strings.
- [ ] **Rule Enable/Disable Checkbox Table**: Easily toggle individual rules on or off.
- [ ] **Rule Import / Export**: Save and share custom rule sets as `.json` configuration files.
- [ ] **Test Rule Workspace**: Live testing pad to preview regex operations on input text before enabling.

---

## 12. Out-of-Band Vulnerability Testing / Collaborator
- [ ] **Private Out-of-Band (OAST) Server**: Dedicated DNS, HTTP, HTTPS, and SMTP callback listener server.
- [ ] **Unique Subdomain Generator**: Generate unique lookup payloads (e.g. `x82f1a.collaborator.mydomain.com`).
- [ ] **Real-Time Polling & Notifications**: Alert analyst immediately when DNS lookup or HTTP request hits callback server.
- [ ] **DNS Query Logger**: Capture A, AAAA, TXT, MX, and CNAME queries with source IP addresses.
- [ ] **HTTP / HTTPS Request Logger**: Capture full HTTP callback headers, bodies, and client IPs.
- [ ] **SMTP Mail Collector**: Intercept email verification callbacks sent by target servers.
- [ ] **Correlate OAST Payload to Vulnerability**: Link received callback automatically to originating vulnerability check.
- [ ] **Custom Domain Support**: Allow analysts to host their own custom domain for OAST payload delivery.
- [ ] **Interaction History Viewer**: Table view of all historical OAST interactions.
- [ ] **Interactsh / ProjectDiscovery Integration**: Native support for public or self-hosted `interactsh` servers.

---

## 13. Reporting, Vuln Management & Integrations
- [ ] **Executive PDF Vulnerability Report**: Professional PDF generator with executive summary, charts, and severity breakdowns.
- [ ] **Developer Markdown / HTML Reports**: Export audit findings with step-by-step reproduction steps and code remediation tips.
- [ ] **Proof-of-Concept (PoC) Code Generator**: Auto-generate executable PoC scripts in Python (`requests`), JavaScript, and cURL.
- [ ] **Jira Integration**: Create and sync vulnerability tickets directly in Jira from the UI.
- [ ] **GitHub Issues Integration**: One-click create GitHub issue for discovered findings.
- [ ] **DefectDojo Integration**: Push audit findings directly into OWASP DefectDojo ASPM platform.
- [ ] **SARIF (Static Analysis Results Interchange Format) Export**: Export findings in standard SARIF format for CI/CD tools.
- [ ] **Custom Severity Rating (CVSS v3.1 / v4.0 Calculator)**: Embedded CVSS vector calculator for every finding.
- [ ] **Remediation Recommendation Database**: Pre-written remediation instructions for common vulnerability types.
- [ ] **Vulnerability Evidence Screenshots & Logs**: Attach request/response proof snippets and screenshots to findings.
- [ ] **Report Template Customizer**: Add custom company logo, auditor name, and custom disclaimer text.
- [ ] **Finding Deduplication Engine**: Automatically merge duplicate vulnerabilities discovered across multiple paths.
- [ ] **Compliance Mapping**: Map findings to OWASP Top 10, PCI-DSS, NIST SP 800-53, and ISO 27001 controls.
- [ ] **Audit Trail Log**: Track all actions taken by analysts during testing for compliance records.
- [ ] **Export Raw HTTP Traffic Log**: Package full HTTP request/response traces into zip archives for client delivery.

---

## 14. UI/UX, Performance & Enterprise Engine
- [ ] **Dark & Light Mode Themes**: Authentic Burp Suite Dark, Burp Light, and Custom High-Contrast themes.
- [ ] **Flexible Docking / Tab Window Detaching**: Drag out tabs (Repeater, Proxy, Logger) into standalone multi-monitor windows.
- [ ] **Global Keyboard Shortcuts Manager**: Customize hotkeys for every menu item and action.
- [ ] **Project File Database (SQLite / RocksDB Backend)**: Save full session state, proxy history, site map, and findings to `.cyvorax` project files.
- [ ] **Automatic Project Auto-Save**: Background periodic auto-saving to prevent data loss.
- [ ] **Memory & Disk Management UI**: Real-time RAM & CPU usage monitor with cache clearing actions.
- [ ] **High-DPI / 4K Display Scaling**: Flawless crisp UI rendering on high-resolution monitors.
- [ ] **Multi-Language Localization**: Internationalization support (English, Spanish, Chinese, Japanese, German).
- [ ] **AI-Assisted Payload & Remediation Generator**: Optional OpenAI / Anthropic integration to analyze complex responses and suggest bypasses.
- [ ] **Collaborative Team Testing (Real-time Sync)**: Shared proxy history and site map across multiple analysts in real time.
- [ ] **Command Palette (`Ctrl+P` or `Ctrl+Shift+P`)**: Quick search and jump to any feature, tab, or setting.
- [ ] **Custom Notification Sound & Native OS Toasts**: Optional OS alerts when scan completes or intercept hits.
- [ ] **Session Lock / Password Protection**: Lock UI with master password during analyst absence.
- [ ] **Air-Gapped / Offline Execution Mode**: Ensure zero telemetry or external network calls when running in sensitive environments.
- [ ] **Self-Updating Engine**: Automated background check for software updates and native library updates.

---

<div align="center">
  <b>CyvoraX Suite Gap Analysis Complete — 215 Features Identified</b>
</div>
