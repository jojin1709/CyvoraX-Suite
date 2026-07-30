"""
Local-only test target for verifying Intruder works end to end.
NOT part of CyvoraX Suite - this simulates a vulnerable endpoint so we can
prove the attack engine actually detects something real.
"""
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs

FAKE_DB_ERROR = "SQL syntax error near"


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass

    def do_GET(self):
        parsed = urlparse(self.path)
        qs = parse_qs(parsed.query)

        if parsed.path == "/search":
            q = qs.get("q", [""])[0]
            body = f"<html><body>Results for: {q}</body></html>".encode()
            self.send_response(200)
            self.send_header("Content-Type", "text/html")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return

        id_val = qs.get("id", [""])[0]

        if "'" in id_val or '"' in id_val:
            body = f"Internal Server Error: {FAKE_DB_ERROR} '{id_val}'".encode()
            self.send_response(500)
        elif id_val == "1337":
            body = b"Admin panel unlocked - secret flag: CYVORAX_OK"
            self.send_response(200)
        else:
            body = f"User record for id={id_val}".encode()
            self.send_response(200)

        self.send_header("Content-Type", "text/plain")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


if __name__ == "__main__":
    server = HTTPServer(("127.0.0.1", 9091), Handler)
    server.serve_forever()
