"""
CyvoraX Suite - Target Scope & Site Map Engine
Tracks hierarchical host/path endpoints, in-scope/out-of-scope regex rules,
and parameter structures observed during proxying and crawling.
"""
import re
from urllib.parse import urlparse, parse_qs


class ScopeRule:
    def __init__(self, pattern: str, include: bool = True):
        self.pattern_str = pattern
        self.regex = re.compile(pattern, re.IGNORECASE)
        self.include = include  # True for in-scope, False for out-of-scope


class SiteNode:
    def __init__(self, name: str, is_dir: bool = False, full_url: str = ""):
        self.name = name
        self.is_dir = is_dir
        self.full_url = full_url
        self.children = {}  # name -> SiteNode
        self.requests = []  # List of HTTPMessage / dict metadata
        self.params = set()


class SiteMapEngine:
    def __init__(self):
        self.roots = {}  # host -> SiteNode (root)
        self.scope_rules = []

    def add_scope_rule(self, pattern: str, include: bool = True):
        self.scope_rules.append(ScopeRule(pattern, include))

    def is_in_scope(self, url: str) -> bool:
        if not self.scope_rules:
            return True  # Default: everything in scope if no rules defined
        
        in_scope = False
        for rule in self.scope_rules:
            if rule.regex.search(url):
                if rule.include:
                    in_scope = True
                else:
                    return False  # Explicit exclude overrides
        return in_scope

    def record_request(self, method: str, host: str, path: str, status_code: int = 0, length: int = 0, req_body: str = "", resp_body: str = ""):
        url = f"http://{host}{path}"
        if not self.is_in_scope(url):
            return None

        if host not in self.roots:
            self.roots[host] = SiteNode(host, is_dir=True, full_url=f"http://{host}")

        current = self.roots[host]
        parsed = urlparse(path)
        path_parts = [p for p in parsed.path.split('/') if p]

        # Extract parameters
        query_params = parse_qs(parsed.query)
        for param in query_params.keys():
            current.params.add(param)

        # Traverse / build tree
        running_path = ""
        for i, part in enumerate(path_parts):
            running_path += "/" + part
            is_last = (i == len(path_parts) - 1)
            
            if part not in current.children:
                current.children[part] = SiteNode(
                    name=part,
                    is_dir=not is_last,
                    full_url=f"http://{host}{running_path}"
                )
            current = current.children[part]

        # Log request metadata
        req_entry = {
            "method": method,
            "host": host,
            "path": path,
            "status": status_code,
            "length": length,
            "query_params": list(query_params.keys())
        }
        current.requests.append(req_entry)
        return current
