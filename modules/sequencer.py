"""
CyvoraX Suite - Sequencer module
Analyzes a set of session tokens (cookies, CSRF tokens, reset tokens, etc.)
for randomness quality - equivalent to Burp's Sequencer.
"""
import math
import statistics
from collections import Counter
from dataclasses import dataclass


@dataclass
class SequencerReport:
    sample_count: int
    avg_length: float
    length_variance: float
    shannon_entropy_bits_per_char: float
    estimated_total_entropy_bits: float
    charset_size: int
    duplicate_count: int
    verdict: str
    notes: list


def shannon_entropy(s: str) -> float:
    if not s:
        return 0.0
    counts = Counter(s)
    length = len(s)
    return -sum((c / length) * math.log2(c / length) for c in counts.values())


def analyze_tokens(tokens: list) -> SequencerReport:
    tokens = [t.strip() for t in tokens if t.strip()]
    if len(tokens) < 2:
        raise ValueError("Need at least 2 tokens to analyze")

    lengths = [len(t) for t in tokens]
    avg_len = statistics.mean(lengths)
    len_var = statistics.pvariance(lengths) if len(lengths) > 1 else 0.0

    all_chars = "".join(tokens)
    charset = set(all_chars)
    per_char_entropy = statistics.mean(shannon_entropy(t) for t in tokens)

    # rough total entropy estimate: per-char entropy * average length
    est_total_bits = per_char_entropy * avg_len

    dup_count = len(tokens) - len(set(tokens))

    notes = []
    if len_var > 0.5:
        notes.append("Token lengths vary - tokens may be inconsistently generated or contain variable padding")
    if dup_count > 0:
        notes.append(f"{dup_count} duplicate token(s) found in the sample - strong sign of weak randomness")
    if len(charset) < 10:
        notes.append(f"Small character set used ({len(charset)} distinct chars) - reduces entropy per char")

    if est_total_bits < 40 or dup_count > 0:
        verdict = "WEAK - predictable / brute-forceable in realistic timeframes"
    elif est_total_bits < 80:
        verdict = "MODERATE - may be feasible to attack with sufficient resources/time"
    else:
        verdict = "STRONG - entropy appears sufficient against brute-force"

    return SequencerReport(
        sample_count=len(tokens),
        avg_length=round(avg_len, 2),
        length_variance=round(len_var, 3),
        shannon_entropy_bits_per_char=round(per_char_entropy, 3),
        estimated_total_entropy_bits=round(est_total_bits, 1),
        charset_size=len(charset),
        duplicate_count=dup_count,
        verdict=verdict,
        notes=notes,
    )
