"""
CyvoraX Suite - Match & Replace module
Regex-based rules that auto-rewrite requests/responses as they pass
through the proxy - equivalent to Burp's Match & Replace.
"""
import re
from dataclasses import dataclass


@dataclass
class MatchReplaceRule:
    name: str
    scope: str       # "request" or "response"
    pattern: str      # regex
    replacement: str
    enabled: bool = True

    def compiled(self):
        return re.compile(self.pattern)


BUILTIN_PRESETS = [
    MatchReplaceRule("Strip Content-Security-Policy", "response", r"(?i)content-security-policy:[^\r\n]*\r\n", "", enabled=True),
    MatchReplaceRule("Strip X-Frame-Options", "response", r"(?i)x-frame-options:[^\r\n]*\r\n", "", enabled=True),
    MatchReplaceRule("Spoof Mobile User-Agent", "request", r"(?i)User-Agent:[^\r\n]*", "User-Agent: Mozilla/5.0 (iPhone; CPU iPhone OS 16_5 like Mac OS X) AppleWebKit/605.1.15", enabled=False),
    MatchReplaceRule("Emulate Admin Cookie", "request", r"(?i)Cookie: (.*)", r"Cookie: admin=true; \1", enabled=False),
    MatchReplaceRule("Strip HSTS Header", "response", r"(?i)strict-transport-security:[^\r\n]*\r\n", "", enabled=True),
]


class MatchReplaceEngine:
    def __init__(self, load_defaults: bool = True):
        self.rules = []
        if load_defaults:
            for r in BUILTIN_PRESETS:
                self.add_rule(r)

    def add_rule(self, rule: MatchReplaceRule):
        self.rules.append(rule)

    def remove_rule(self, name: str):
        self.rules = [r for r in self.rules if r.name != name]

    def apply(self, raw_text: str, scope: str) -> str:
        for rule in self.rules:
            if not rule.enabled or rule.scope != scope:
                continue
            try:
                raw_text = rule.compiled().sub(rule.replacement, raw_text)
            except re.error:
                continue
        return raw_text

