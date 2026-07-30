"""
CyvoraX Suite - Cookie Jar & Session Manager
Tracks domain cookies, expiration, path, HttpOnly, and Secure flags.
"""
from http.cookies import SimpleCookie
import time


class CookieItem:
    def __init__(self, name: str, value: str, domain: str, path: str = "/", secure: bool = False, httponly: bool = False, expires: float = None):
        self.name = name
        self.value = value
        self.domain = domain
        self.path = path
        self.secure = secure
        self.httponly = httponly
        self.expires = expires

    def is_expired(self) -> bool:
        if self.expires and time.time() > self.expires:
            return True
        return False


class CookieJar:
    def __init__(self):
        self.cookies = {}  # (domain, name) -> CookieItem

    def extract_from_response(self, host: str, set_cookie_headers: list):
        for header in set_cookie_headers:
            cookie = SimpleCookie()
            try:
                cookie.load(header)
            except Exception:
                continue

            for name, morsel in cookie.items():
                domain = morsel["domain"] if morsel["domain"] else host
                path = morsel["path"] if morsel["path"] else "/"
                secure = bool(morsel["secure"])
                httponly = bool(morsel["httponly"])
                
                item = CookieItem(
                    name=name,
                    value=morsel.value,
                    domain=domain.lstrip('.'),
                    path=path,
                    secure=secure,
                    httponly=httponly
                )
                self.cookies[(item.domain, item.name)] = item

    def get_cookies_for_url(self, host: str, path: str = "/", is_secure: bool = False) -> str:
        matched = []
        for (domain, name), item in list(self.cookies.items()):
            if item.is_expired():
                del self.cookies[(domain, name)]
                continue
            
            if host.endswith(domain) and path.startswith(item.path):
                if item.secure and not is_secure:
                    continue
                matched.append(f"{item.name}={item.value}")
        
        return "; ".join(matched)
