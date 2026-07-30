"""
CyvoraX Suite - Repeater module
Send an arbitrary, editable HTTP request and view the raw response,
equivalent to Burp's Repeater tab. Independent of the proxy - talks
directly to the target so you can replay/tweak requests freely.
"""
import socket
import ssl
import re
import urllib.parse


def send_raw_request(host: str, port: int, raw_request: str, use_tls: bool = True, timeout: float = 10.0) -> str:
    """
    raw_request: full HTTP request text, e.g.
        GET /path HTTP/1.1\r\nHost: example.com\r\n\r\n
    Returns the raw response text (headers + body).
    """
    if "\r\n" not in raw_request:
        raw_request = raw_request.replace("\n", "\r\n")
    if not raw_request.endswith("\r\n\r\n"):
        if raw_request.endswith("\r\n"):
            raw_request += "\r\n"
        else:
            raw_request += "\r\n\r\n"

    sock = socket.create_connection((host, port), timeout=timeout)
    try:
        if use_tls:
            ctx = ssl.create_default_context()
            ctx.check_hostname = False
            ctx.verify_mode = ssl.CERT_NONE
            sock = ctx.wrap_socket(sock, server_hostname=host)

        sock.sendall(raw_request.encode())

        chunks = []
        sock.settimeout(timeout)
        try:
            while True:
                chunk = sock.recv(65536)
                if not chunk:
                    break
                chunks.append(chunk)
        except socket.timeout:
            pass
        return b"".join(chunks).decode(errors="replace")
    finally:
        sock.close()


def parse_target_from_request(raw_request: str, default_tls: bool = True):
    """Pull host/port/tls out of a raw request's Host header for convenience."""
    m = re.search(r"^Host:\s*(.+)$", raw_request, re.MULTILINE | re.IGNORECASE)
    if not m:
        raise ValueError("No Host header found in request")
    host_val = m.group(1).strip()
    if ":" in host_val:
        host, port_s = host_val.split(":", 1)
        return host, int(port_s), default_tls
    return host_val, (443 if default_tls else 80), default_tls


def request_to_curl(raw_request: str, use_tls: bool = True) -> str:
    """Convert raw HTTP request string into an executable cURL command."""
    lines = raw_request.replace("\r\n", "\n").split("\n")
    if not lines or not lines[0]:
        return "curl"
    
    parts = lines[0].split(" ")
    method = parts[0] if len(parts) > 0 else "GET"
    path = parts[1] if len(parts) > 1 else "/"

    headers = []
    body_lines = []
    in_body = False
    host = "localhost"

    for line in lines[1:]:
        if in_body:
            body_lines.append(line)
            continue
        if line == "":
            in_body = True
            continue
        if ":" in line:
            k, _, v = line.partition(":")
            k, v = k.strip(), v.strip()
            if k.lower() == "host":
                host = v
            headers.append((k, v))

    scheme = "https" if use_tls else "http"
    url = f"{scheme}://{host}{path}"

    cmd = [f"curl -X {method} '{url}'"]
    for k, v in headers:
        cmd.append(f"  -H '{k}: {v}'")
    
    body = "\n".join(body_lines).strip()
    if body:
        escaped_body = body.replace("'", "'\\''")
        cmd.append(f"  --data-raw '{escaped_body}'")
    
    return " \\\n".join(cmd)


def curl_to_request(curl_cmd: str) -> tuple[str, bool]:
    """Parse cURL command string back into a raw HTTP request and tls bool."""
    use_tls = "https://" in curl_cmd.lower()
    
    # Extract URL
    url_m = re.search(r"curl\s+(?:-[A-Za-z0-9]+\s+)*['\"]?(https?://[^\s'\"]+)['\"]?", curl_cmd, re.IGNORECASE)
    url_str = url_m.group(1) if url_m else "http://example.com/"
    
    parsed_url = urllib.parse.urlparse(url_str)
    host = parsed_url.netloc or "example.com"
    path = parsed_url.path or "/"
    if parsed_url.query:
        path += f"?{parsed_url.query}"
        
    use_tls = (parsed_url.scheme.lower() == "https")

    # Extract method
    method_m = re.search(r"-X\s+([A-Z]+)", curl_cmd)
    method = method_m.group(1) if method_m else ("POST" if "--data" in curl_cmd or "-d" in curl_cmd else "GET")

    # Extract headers
    headers = [("Host", host), ("User-Agent", "Mozilla/5.0 (CyvoraX Repeater)"), ("Accept", "*/*")]
    header_matches = re.findall(r"-H\s+['\"]([^'\"]+)['\"]", curl_cmd)
    for h in header_matches:
        if ":" in h:
            k, _, v = h.partition(":")
            k, v = k.strip(), v.strip()
            if k.lower() != "host":
                headers.append((k, v))

    # Extract body
    data_m = re.search(r"(?:--data|--data-raw|-d)\s+['\"]([^'\"]+)['\"]", curl_cmd)
    body = data_m.group(1) if data_m else ""

    req = f"{method} {path} HTTP/1.1\r\n"
    for k, v in headers:
        req += f"{k}: {v}\r\n"
    if body and not any(k.lower() == "content-length" for k, _ in headers):
        req += f"Content-Length: {len(body)}\r\n"
    req += f"\r\n{body}"
    return req, use_tls

