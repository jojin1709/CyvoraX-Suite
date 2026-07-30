# CyvoraX Suite - Feature Build Progress

Tracking real, verified (compiled + runtime-tested) work against ROADMAP_FULL_GAP_ANALYSIS.md (215 items).

## DONE
### Section 4 - Active & Passive Scanner Engine
- [x] CORS Misconfiguration Audit -> modules/active_scanner.py: check_cors_misconfiguration
- [x] Clickjacking Test Generator (detection half) -> check_clickjacking
- [x] SSTI polyglot detection -> check_ssti (Jinja2/Twig/Freemarker/ERB markers)
- [x] Command Injection (blind, time-based) -> check_command_injection_timing
- [x] Sensitive Data Exposure Scanner -> check_sensitive_data_exposure (AWS keys, private keys, JWT, Slack tokens, generic API keys)

### Section 9 - Decoder, Encoder & Cryptography Suite
- [x] Base32/Base58/Base85 encode+decode -> modules/decoder.py
- [x] Binary/Octal conversions
- [x] SHA224/SHA384/SHA512/SHA3-256/BLAKE2b/RIPEMD160(best-effort) hashes
- [x] HMAC Calculator
- [x] Gzip/Deflate/Zlib decompressor + auto_decompress() with magic-byte sniffing + brotli hook
- [x] Unicode Normalization (NFC/NFD/NFKC/NFKD)
- [x] JWT re-sign (HS256/384/512) + JWT secret dictionary cracker

All items above verified via live python execution on jojin's machine (not just py_compile).

### Section 1 - Core Proxy & Intercept Engine
- [x] Auto-Decode Gzip/Brotli/Zstd -> modules/proxy_core.py: HTTPMessage.decoded_body()
- [x] Automatic Content-Length Recalculation -> HTTPMessage.recalc_content_length(),
      wired into _process_and_forward() so intercept edits auto-fix on forward

- [x] Open Redirect (upgraded pre-existing stub to be host-aware) -> check_open_redirect
- [x] Host Header Injection -> check_host_header_injection
- [x] XXE -> check_xxe
- [x] LFI / Path Traversal -> check_lfi
- [x] CSRF (missing SameSite + missing token detection) -> check_csrf_protection
- [x] Missing Subresource Integrity (SRI) -> check_sri_missing
- [x] GraphQL Introspection detection -> check_graphql_introspection
- [x] Mass Assignment / Over-Posting (payload builder + diff detector) -> build_mass_assignment_payload, check_mass_assignment
- [x] JWT none-algorithm exploit generator -> jwt_none_alg_exploit
- [x] JWT RS256->HS256 key-confusion exploit generator -> jwt_key_confusion_exploit
  (found + fixed 2 missing imports - base64, hashlib - live via runtime test failures)

### Section 6 - Repeater & HTTP Client
- [x] Copy as cURL (`request_to_curl`) & Paste from cURL (`curl_to_request`) -> modules/repeater.py & main.py UI buttons

### Section 11 - Match & Replace Engine
- [x] Built-in Presets (Strip CSP, Strip X-Frame-Options, Spoof Mobile UA, Emulate Admin Cookie, Strip HSTS) -> modules/match_replace.py: BUILTIN_PRESETS

### Section 4 - Active & Passive Scanner Engine
- [x] Live Passive Scan Wiring -> `proxy_core.py` automatically runs passive checks (security headers, sensitive data exposure, CORS misconfigurations) on every response and emits findings live to the Dashboard table.

## NEXT UP (in order)
1. Section 1: WebSocket interception (upgrade handshake detect + frame parse)
2. Section 2: Tree-based Site Map hierarchy view in Target Tab
3. Section 5: Intruder regex extraction & anomaly charting
4. Section 7: Macro recorder & auto session re-login handler

## NOT STARTED
Everything else in ROADMAP_FULL_GAP_ANALYSIS.md - full parity with Burp Pro is an ongoing effort; this file tracks real verified implementation progress.
