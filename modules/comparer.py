"""
CyvoraX Suite - Comparer module
Word/byte-level diff between two pieces of text (requests, responses,
tokens, etc.) - equivalent to Burp's Comparer tab.
"""
import difflib


def diff_lines(text_a: str, text_b: str):
    """Return list of (tag, line_a, line_b) for line-based diff."""
    a_lines = text_a.splitlines()
    b_lines = text_b.splitlines()
    sm = difflib.SequenceMatcher(a=a_lines, b=b_lines)
    out = []
    for tag, i1, i2, j1, j2 in sm.get_opcodes():
        a_chunk = a_lines[i1:i2]
        b_chunk = b_lines[j1:j2]
        max_len = max(len(a_chunk), len(b_chunk))
        for i in range(max_len):
            la = a_chunk[i] if i < len(a_chunk) else ""
            lb = b_chunk[i] if i < len(b_chunk) else ""
            out.append((tag, la, lb))
    return out


def diff_html(text_a: str, text_b: str, label_a="A", label_b="B") -> str:
    """Render a side-by-side HTML diff for the GUI's Comparer tab."""
    d = difflib.HtmlDiff(wrapcolumn=80)
    return d.make_table(text_a.splitlines(), text_b.splitlines(), label_a, label_b, context=False)


def similarity_ratio(text_a: str, text_b: str) -> float:
    return difflib.SequenceMatcher(a=text_a, b=text_b).ratio()
