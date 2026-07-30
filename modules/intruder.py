"""
CyvoraX Suite - Intruder module
Automated payload injection across marked positions in a raw request.
Positions are marked with §marker§ (same convention as Burp).
Attack types: sniper (one position at a time, others held static),
battering_ram (same payload in every position simultaneously),
pitchfork (parallel payload lists, one per position, iterated together).
"""
import time
import itertools
from dataclasses import dataclass, field
from typing import List

from modules.repeater import send_raw_request, parse_target_from_request

MARKER = "\u00a7"  # §


@dataclass
class IntruderResult:
    payload: str
    status_code: int
    length: int
    time_ms: float
    raw_response: str = field(repr=False, default="")


def _extract_positions(template: str):
    """Return list of (start, end) marker pairs and the base string with markers stripped."""
    parts = template.split(MARKER)
    if len(parts) % 2 == 0:
        raise ValueError("Unbalanced § markers - each position needs a start and end §")
    return parts  # odd-indexed parts (1,3,5..) are the marked segments


def _build_request(parts: List[str], payloads_for_each_marker: List[str]) -> str:
    """Reassemble template with each marked segment replaced by its payload."""
    out = parts[0]
    marker_idx = 0
    for i in range(1, len(parts), 2):
        out += payloads_for_each_marker[marker_idx]
        marker_idx += 1
        out += parts[i + 1]
    return out


def _num_positions(template: str) -> int:
    return (template.count(MARKER)) // 2


def run_attack(template: str, host: str, port: int, use_tls: bool,
                payload_lists: List[List[str]], attack_type: str = "sniper",
                delay_s: float = 0.0) -> List[IntruderResult]:
    """
    template: raw request text with §marker§ around injection points
    payload_lists: for sniper/battering_ram, a single list (payload_lists[0]) is used.
                   for pitchfork, one list per marked position, iterated in lockstep.
    """
    parts = _extract_positions(template)
    n_positions = _num_positions(template)
    if n_positions == 0:
        raise ValueError("No §marker§ positions found in the request template")

    results = []

    def send_one(payloads_for_each_marker, label):
        req = _build_request(parts, payloads_for_each_marker)
        start = time.perf_counter()
        try:
            resp = send_raw_request(host, port, req, use_tls=use_tls, timeout=10)
        except Exception as e:
            resp = f"ERROR: {e}"
        elapsed = (time.perf_counter() - start) * 1000

        status_line = resp.split("\r\n", 1)[0] if resp else ""
        status_code = 0
        sp = status_line.split(" ")
        if len(sp) > 1 and sp[1].isdigit():
            status_code = int(sp[1])
        results.append(IntruderResult(payload=label, status_code=status_code,
                                       length=len(resp), time_ms=round(elapsed, 1),
                                       raw_response=resp))
        if delay_s:
            time.sleep(delay_s)

    if attack_type == "sniper":
        base_payloads = payload_lists[0]
        for pos in range(n_positions):
            for payload in base_payloads:
                fill = ["" for _ in range(n_positions)]
                # sniper leaves other positions empty (matches Burp's "no payload" convention
                # would normally be the original value; here template positions carry
                # placeholder text the caller can set to the original value if desired)
                fill[pos] = payload
                send_one(fill, f"pos{pos}:{payload}")

    elif attack_type == "battering_ram":
        base_payloads = payload_lists[0]
        for payload in base_payloads:
            fill = [payload for _ in range(n_positions)]
            send_one(fill, payload)

    elif attack_type == "pitchfork":
        if len(payload_lists) != n_positions:
            raise ValueError(f"pitchfork needs one payload list per position ({n_positions} positions)")
        for combo in zip(*payload_lists):
            send_one(list(combo), "|".join(combo))

    else:
        raise ValueError(f"Unknown attack_type: {attack_type}")

    return results


# Small built-in payload sets useful for quick fuzzing
COMMON_PAYLOADS = {
    "sqli_probe": ["'", "\"", "' OR '1'='1", "1' AND '1'='2", "'; DROP TABLE x--", "1 OR 1=1"],
    "xss_probe": ["<script>alert(1)</script>", "\"><script>alert(1)</script>", "'><img src=x onerror=alert(1)>"],
    "path_traversal": ["../../../etc/passwd", "..%2f..%2f..%2fetc%2fpasswd", "....//....//etc/passwd"],
    "command_injection": ["; id", "| id", "`id`", "$(id)"],
    "numbers_0_10": [str(i) for i in range(11)],
}
