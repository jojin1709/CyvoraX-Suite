"""
CyvoraX Suite - Proxy Core
An asyncio-based intercepting HTTP/HTTPS proxy (MITM), the equivalent of
Burp's Proxy module. Handles CONNECT tunneling, TLS termination via a
per-host cert issued by our CA, HTTP parsing, and an intercept queue so
requests can be paused/edited before forwarding (Repeater/Intercept style).
"""
import asyncio
import ssl
import time
import uuid
from dataclasses import dataclass, field
from typing import Callable, Optional

from modules.certauthority import CertAuthority
from modules.decoder import auto_decompress

HOP_BY_HOP = {
    "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
    "te", "trailers", "transfer-encoding", "upgrade"
}


@dataclass
class HTTPMessage:
    """Parsed HTTP request or response, editable before it's forwarded."""
    id: str
    method: str = ""
    path: str = ""
    version: str = "HTTP/1.1"
    status_code: int = 0
    status_text: str = ""
    headers: list = field(default_factory=list)  # list[(name, value)]
    body: bytes = b""
    host: str = ""
    scheme: str = "http"
    timestamp: float = field(default_factory=time.time)

    def raw_request(self) -> bytes:
        head = f"{self.method} {self.path} {self.version}\r\n"
        for k, v in self.headers:
            head += f"{k}: {v}\r\n"
        head += "\r\n"
        return head.encode() + self.body

    def raw_response(self) -> bytes:
        head = f"{self.version} {self.status_code} {self.status_text}\r\n"
        for k, v in self.headers:
            head += f"{k}: {v}\r\n"
        head += "\r\n"
        return head.encode() + self.body

    def decoded_body(self) -> bytes:
        """Body with Content-Encoding (gzip/deflate/br) transparently removed,
        for display in Repeater/Proxy/Decoder panes. Does not mutate self.body."""
        ce = next((v for k, v in self.headers if k.lower() == "content-encoding"), "")
        if not ce:
            return self.body
        try:
            return auto_decompress(self.body, ce)
        except Exception:
            return self.body

    def recalc_content_length(self):
        """Fix up the Content-Length header to match the current body after an
        edit (e.g. in Intercept). Burp does this automatically; we do too.
        Only touches Content-Length if it was already present, and drops
        Transfer-Encoding: chunked since we always send a fixed-length body."""
        new_headers = []
        had_cl = False
        for k, v in self.headers:
            lk = k.lower()
            if lk == "content-length":
                new_headers.append((k, str(len(self.body))))
                had_cl = True
            elif lk == "transfer-encoding":
                continue  # we send a complete, non-chunked body below
            else:
                new_headers.append((k, v))
        if not had_cl and self.body:
            new_headers.append(("Content-Length", str(len(self.body))))
        self.headers = new_headers


async def _read_headers(reader: asyncio.StreamReader) -> bytes:
    buf = b""
    while b"\r\n\r\n" not in buf:
        chunk = await reader.read(4096)
        if not chunk:
            break
        buf += chunk
    return buf


def _parse_request_line(head_bytes: bytes):
    lines = head_bytes.split(b"\r\n")
    request_line = lines[0].decode(errors="replace")
    parts = request_line.split(" ")
    method, path, version = (parts + ["", "", ""])[:3]
    headers = []
    body_start = b""
    for i, line in enumerate(lines[1:], start=1):
        if line == b"":
            body_start = b"\r\n".join(lines[i + 1:])
            break
        if b":" in line:
            name, _, val = line.partition(b":")
            headers.append((name.decode(errors="replace").strip(), val.decode(errors="replace").strip()))
    return method, path, version, headers, body_start


async def _read_full_body(reader: asyncio.StreamReader, headers: list, partial: bytes) -> bytes:
    hmap = {k.lower(): v for k, v in headers}
    if "content-length" in hmap:
        need = int(hmap["content-length"])
        body = partial
        while len(body) < need:
            chunk = await reader.read(need - len(body))
            if not chunk:
                break
            body += chunk
        return body[:need]
    if hmap.get("transfer-encoding", "").lower() == "chunked":
        body = bytearray(partial)
        while True:
            # naive chunked reader; good enough for proxy logging/edit purposes
            while b"\r\n" not in body:
                chunk = await reader.read(4096)
                if not chunk:
                    return bytes(body)
                body += chunk
            size_line, _, rest = bytes(body).partition(b"\r\n")
            try:
                size = int(size_line.strip(), 16)
            except ValueError:
                return bytes(body)
            if size == 0:
                return bytes(body)
            body = bytearray(rest)
            while len(body) < size + 2:
                chunk = await reader.read(4096)
                if not chunk:
                    break
                body += chunk
    return partial


class ProxyCore:
    """
    Runs the listening proxy socket. For each connection:
      - Plain HTTP: parse request, run intercept hook, forward, parse response.
      - HTTPS: handle CONNECT, TLS-terminate both sides using per-host CA cert,
        then treat as HTTP within the tunnel.
    on_request / on_response are hooks the GUI wires up for logging + intercept.
    """

    def __init__(self, host="127.0.0.1", port=8080,
                 on_request: Optional[Callable] = None,
                 on_response: Optional[Callable] = None,
                 intercept_enabled: Callable[[], bool] = lambda: False,
                 intercept_wait: Optional[Callable] = None,
                 match_replace_engine=None,
                 on_passive_scan_finding: Optional[Callable] = None):
        self.host = host
        self.port = port
        self.ca = CertAuthority()
        self.on_request = on_request
        self.on_response = on_response
        self.intercept_enabled = intercept_enabled
        self.intercept_wait = intercept_wait  # async callable(HTTPMessage) -> HTTPMessage
        self.match_replace_engine = match_replace_engine
        self.on_passive_scan_finding = on_passive_scan_finding
        self._server = None

    async def start(self):
        self._server = await asyncio.start_server(self._handle_client, self.host, self.port)
        return self._server

    def stop(self):
        if self._server:
            self._server.close()

    async def _handle_client(self, reader: asyncio.StreamReader, writer: asyncio.StreamWriter):
        try:
            head = await _read_headers(reader)
            if not head:
                writer.close()
                return
            method, path, version, headers, partial_body = _parse_request_line(head)

            if method == "CONNECT":
                await self._handle_connect(path, reader, writer)
                return

            # plain HTTP absolute-form request (proxy semantics)
            body = await _read_full_body(reader, headers, partial_body)
            host_header = next((v for k, v in headers if k.lower() == "host"), "")
            msg = HTTPMessage(id=str(uuid.uuid4()), method=method, path=path, version=version,
                               headers=headers, body=body, host=host_header, scheme="http")
            await self._process_and_forward(msg, reader, writer, use_tls=False)
        except (ConnectionResetError, asyncio.IncompleteReadError):
            pass
        finally:
            try:
                writer.close()
            except Exception:
                pass

    async def _handle_connect(self, target: str, client_reader, client_writer):
        host, _, port_s = target.partition(":")
        port = int(port_s or 443)

        client_writer.write(b"HTTP/1.1 200 Connection Established\r\n\r\n")
        await client_writer.drain()

        cert_path, key_path = self.ca.get_host_cert(host)
        server_ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        server_ctx.load_cert_chain(cert_path, key_path)

        loop = asyncio.get_event_loop()
        try:
            transport = await loop.start_tls(
                client_writer.transport, protocol=client_writer.transport.get_protocol(),
                sslcontext=server_ctx, server_side=True)
        except Exception:
            client_writer.close()
            return

        tls_reader = asyncio.StreamReader()
        tls_protocol = asyncio.StreamReaderProtocol(tls_reader)
        transport.set_protocol(tls_protocol)
        tls_writer = asyncio.StreamWriter(transport, tls_protocol, tls_reader, loop)

        while True:
            head = await _read_headers(tls_reader)
            if not head:
                break
            method, path, version, headers, partial_body = _parse_request_line(head)
            if not method:
                break
            body = await _read_full_body(tls_reader, headers, partial_body)
            msg = HTTPMessage(id=str(uuid.uuid4()), method=method, path=path, version=version,
                               headers=headers, body=body, host=host, scheme="https")
            keep_alive = await self._process_and_forward(
                msg, tls_reader, tls_writer, use_tls=True, connect_host=host, connect_port=port)
            if not keep_alive:
                break
        try:
            tls_writer.close()
        except Exception:
            pass

    async def _process_and_forward(self, msg: HTTPMessage, client_reader, client_writer,
                                    use_tls: bool, connect_host: str = None, connect_port: int = None) -> bool:
        if self.on_request:
            self.on_request(msg)

        if self.intercept_enabled() and self.intercept_wait:
            original_body = msg.body
            msg = await self.intercept_wait(msg)
            if msg is None:  # dropped by user
                return False
            if msg.body != original_body:
                msg.recalc_content_length()

        target_host = connect_host or msg.host.split(":")[0]
        target_port = connect_port or (443 if use_tls else 80)
        if not connect_host and ":" in msg.host:
            target_port = int(msg.host.split(":")[1])

        try:
            if use_tls:
                ctx = ssl.create_default_context()
                ctx.check_hostname = False
                ctx.verify_mode = ssl.CERT_NONE
                remote_reader, remote_writer = await asyncio.open_connection(
                    target_host, target_port, ssl=ctx, server_hostname=target_host)
            else:
                remote_reader, remote_writer = await asyncio.open_connection(target_host, target_port)
        except Exception as e:
            client_writer.write(f"HTTP/1.1 502 Bad Gateway\r\n\r\nCyvoraX: {e}".encode())
            await client_writer.drain()
            return False

        # Apply Match & Replace to outgoing request
        if self.match_replace_engine:
            raw_req_str = msg.raw_request().decode(errors="replace")
            new_req_str = self.match_replace_engine.apply(raw_req_str, "request")
            if new_req_str != raw_req_str:
                m_lines = new_req_str.split("\r\n" if "\r\n" in new_req_str else "\n")
                if m_lines:
                    first_p = m_lines[0].split(" ")
                    msg.method = first_p[0] if len(first_p) > 0 else msg.method
                    msg.path = first_p[1] if len(first_p) > 1 else msg.path
                    msg.version = first_p[2] if len(first_p) > 2 else msg.version

        clean_headers = [(k, v) for k, v in msg.headers if k.lower() not in HOP_BY_HOP]
        request_line = f"{msg.method} {msg.path} {msg.version}\r\n"
        head_bytes = request_line.encode()
        for k, v in clean_headers:
            head_bytes += f"{k}: {v}\r\n".encode()
        head_bytes += b"\r\n"

        remote_writer.write(head_bytes + msg.body)
        await remote_writer.drain()

        resp_head = await _read_headers(remote_reader)
        if not resp_head:
            remote_writer.close()
            return False
        status_line, _, rest = resp_head.partition(b"\r\n")
        status_parts = status_line.decode(errors="replace").split(" ", 2)
        resp_version = status_parts[0] if len(status_parts) > 0 else "HTTP/1.1"
        resp_code = int(status_parts[1]) if len(status_parts) > 1 and status_parts[1].isdigit() else 0
        resp_text = status_parts[2] if len(status_parts) > 2 else ""

        resp_lines = rest.split(b"\r\n")
        resp_headers = []
        body_partial = b""
        for i, line in enumerate(resp_lines):
            if line == b"":
                body_partial = b"\r\n".join(resp_lines[i + 1:])
                break
            if b":" in line:
                name, _, val = line.partition(b":")
                resp_headers.append((name.decode(errors="replace").strip(), val.decode(errors="replace").strip()))

        resp_body = await _read_full_body(remote_reader, resp_headers, body_partial)

        resp_msg = HTTPMessage(id=msg.id, version=resp_version, status_code=resp_code,
                                status_text=resp_text, headers=resp_headers, body=resp_body,
                                host=msg.host, scheme=msg.scheme)

        # Apply Match & Replace to response
        if self.match_replace_engine:
            raw_resp_str = resp_msg.raw_response().decode(errors="replace")
            new_resp_str = self.match_replace_engine.apply(raw_resp_str, "response")
            if new_resp_str != raw_resp_str:
                if "\r\n\r\n" in new_resp_str:
                    r_head, r_body = new_resp_str.split("\r\n\r\n", 1)
                    resp_msg.body = r_body.encode()
                    resp_msg.recalc_content_length()

        # Passive Scanner Auto-Check on Response
        if self.on_passive_scan_finding:
            try:
                from modules.active_scanner import check_security_headers, check_sensitive_data_exposure, check_cors_misconfiguration
                raw_resp_text = resp_msg.raw_response().decode(errors="replace")
                findings = (
                    check_security_headers(raw_resp_text) +
                    check_sensitive_data_exposure(raw_resp_text) +
                    check_cors_misconfiguration(raw_resp_text, "*")
                )
                for f in findings:
                    self.on_passive_scan_finding(msg, resp_msg, f)
            except Exception:
                pass

        if self.on_response:
            self.on_response(msg, resp_msg)

        client_writer.write(resp_msg.raw_response())
        await client_writer.drain()
        remote_writer.close()
        return True
