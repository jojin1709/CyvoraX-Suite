"""
CyvoraX Suite - Scanner module
Tries to load native/libscanner DLL for speed; falls back to a pure-Python
multi-threaded connect scanner if the native lib is unavailable or incompatible.
"""
import ctypes
import os
import socket
import threading
import platform as _platform

_LIB_NAME = "libscanner.dll" if _platform.system() == "Windows" else "libscanner.so"
_LIB_PATH = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "native", _LIB_NAME)

COMMON_PORTS = [21, 22, 23, 25, 53, 80, 110, 111, 135, 139, 143, 443, 445, 993, 995,
                1723, 3306, 3389, 5900, 8000, 8080, 8443, 8888, 9090, 27017]


# ---------------------------------------------------------------------------
# Pure-Python fallback scanner (same semantics as the C version)
# ---------------------------------------------------------------------------
def _py_check_port(host: str, port: int, timeout_ms: int, results: dict):
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(timeout_ms / 1000.0)
        rc = sock.connect_ex((host, port))
        results[port] = (rc == 0)
        sock.close()
    except Exception:
        results[port] = False


def _py_scan(host: str, ports: list, timeout_ms: int, nthreads: int) -> dict:
    results = {}
    sem = threading.Semaphore(nthreads)
    threads = []

    def _worker(p):
        with sem:
            _py_check_port(host, p, timeout_ms, results)

    for port in ports:
        t = threading.Thread(target=_worker, args=(port,), daemon=True)
        t.start()
        threads.append(t)
    for t in threads:
        t.join()
    return {p: v for p, v in results.items() if v}


# ---------------------------------------------------------------------------
# Scanner class – native first, pure-Python fallback
# ---------------------------------------------------------------------------
class Scanner:
    def __init__(self):
        self._lib = None
        try:
            lib = ctypes.CDLL(_LIB_PATH)
            lib.scan_ports.argtypes = [
                ctypes.c_char_p, ctypes.POINTER(ctypes.c_int), ctypes.c_int,
                ctypes.c_int, ctypes.POINTER(ctypes.c_int), ctypes.c_int
            ]
            lib.scan_ports.restype = None
            self._lib = lib
        except OSError:
            # Native lib unavailable or wrong arch – use pure-Python fallback
            pass

    def scan(self, host: str, ports=None, timeout_ms=300, threads=64):
        if ports is None:
            ports = COMMON_PORTS
        try:
            ip = socket.gethostbyname(host)
        except socket.gaierror as e:
            raise ValueError(f"Could not resolve {host}: {e}")

        if self._lib is not None:
            # Fast path: native C library
            n = len(ports)
            c_ports = (ctypes.c_int * n)(*ports)
            c_results = (ctypes.c_int * n)()
            self._lib.scan_ports(ip.encode(), c_ports, n, timeout_ms, c_results, threads)
            return {ports[i]: bool(c_results[i]) for i in range(n) if c_results[i]}
        else:
            # Fallback: pure Python threading
            return _py_scan(ip, ports, timeout_ms, threads)


COMMON_SERVICE_NAMES = {
    21: "ftp", 22: "ssh", 23: "telnet", 25: "smtp", 53: "dns", 80: "http",
    110: "pop3", 111: "rpcbind", 135: "msrpc", 139: "netbios-ssn", 143: "imap",
    443: "https", 445: "microsoft-ds", 993: "imaps", 995: "pop3s",
    1723: "pptp", 3306: "mysql", 3389: "rdp", 5900: "vnc", 8080: "http-alt",
    8443: "https-alt", 27017: "mongodb"
}
