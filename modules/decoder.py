"""
CyvoraX Suite - Decoder module
Encode/decode utilities equivalent to Burp's Decoder tab.
"""
import base64
import urllib.parse
import html
import hashlib
import json
import gzip
import zlib
import hmac
import unicodedata


def encode_base64(data: str) -> str:
    return base64.b64encode(data.encode()).decode()


def decode_base64(data: str) -> str:
    padded = data + "=" * (-len(data) % 4)
    return base64.b64decode(padded).decode(errors="replace")


def encode_url(data: str) -> str:
    return urllib.parse.quote(data, safe="")


def decode_url(data: str) -> str:
    return urllib.parse.unquote(data)


def encode_html(data: str) -> str:
    return html.escape(data)


def decode_html(data: str) -> str:
    return html.unescape(data)


def encode_hex(data: str) -> str:
    return data.encode().hex()


def decode_hex(data: str) -> str:
    return bytes.fromhex(data.strip()).decode(errors="replace")


def hash_md5(data: str) -> str:
    return hashlib.md5(data.encode()).hexdigest()


def hash_sha1(data: str) -> str:
    return hashlib.sha1(data.encode()).hexdigest()


def hash_sha256(data: str) -> str:
    return hashlib.sha256(data.encode()).hexdigest()


def decode_jwt(token: str) -> str:
    """Decode (not verify) a JWT's header + payload for inspection."""
    parts = token.split(".")
    if len(parts) < 2:
        raise ValueError("Not a valid JWT (expected header.payload.signature)")

    def _b64url_decode(seg):
        padded = seg + "=" * (-len(seg) % 4)
        return base64.urlsafe_b64decode(padded)

    header = json.loads(_b64url_decode(parts[0]))
    payload = json.loads(_b64url_decode(parts[1]))
    return json.dumps({"header": header, "payload": payload}, indent=2)



# --- Base encodings ---
def encode_base32(data: str) -> str:
    return base64.b32encode(data.encode()).decode()

def decode_base32(data: str) -> str:
    padded = data + "=" * (-len(data) % 8)
    return base64.b32decode(padded).decode(errors="replace")

def encode_base85(data: str) -> str:
    return base64.b85encode(data.encode()).decode()

def decode_base85(data: str) -> str:
    return base64.b85decode(data).decode(errors="replace")

_B58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

def encode_base58(data: str) -> str:
    b = data.encode()
    n = int.from_bytes(b, "big")
    out = ""
    while n > 0:
        n, rem = divmod(n, 58)
        out = _B58_ALPHABET[rem] + out
    pad = len(b) - len(b.lstrip(b"\x00"))
    return "1" * pad + (out or "1")

def decode_base58(data: str) -> str:
    n = 0
    for ch in data:
        n = n * 58 + _B58_ALPHABET.index(ch)
    b = n.to_bytes((n.bit_length() + 7) // 8, "big") if n else b""
    pad = len(data) - len(data.lstrip("1"))
    return (b"\x00" * pad + b).decode(errors="replace")

# --- Numeric / binary conversions ---
def encode_binary(data: str) -> str:
    return " ".join(format(b, "08b") for b in data.encode())

def decode_binary(data: str) -> str:
    return bytes(int(x, 2) for x in data.split()).decode(errors="replace")

def encode_octal(data: str) -> str:
    return " ".join(format(b, "o") for b in data.encode())

def decode_octal(data: str) -> str:
    return bytes(int(x, 8) for x in data.split()).decode(errors="replace")

# --- Extra hashes ---
def hash_sha224(data: str) -> str:
    return hashlib.sha224(data.encode()).hexdigest()

def hash_sha384(data: str) -> str:
    return hashlib.sha384(data.encode()).hexdigest()

def hash_sha512(data: str) -> str:
    return hashlib.sha512(data.encode()).hexdigest()

def hash_sha3_256(data: str) -> str:
    return hashlib.sha3_256(data.encode()).hexdigest()

def hash_blake2b(data: str) -> str:
    return hashlib.blake2b(data.encode()).hexdigest()

def hash_ripemd160(data: str) -> str:
    try:
        h = hashlib.new("ripemd160")
        h.update(data.encode())
        return h.hexdigest()
    except (ValueError, AttributeError):
        raise ValueError("RIPEMD160 not available in this OpenSSL build")

# --- HMAC ---
def hmac_calculate(data: str, key: str, algo: str = "sha256") -> str:
    return hmac.HMAC(key.encode(), data.encode(), getattr(hashlib, algo)).hexdigest()

# --- Compression ---
def decompress_gzip(data: bytes) -> bytes:
    return gzip.decompress(data)

def decompress_zlib(data: bytes) -> bytes:
    return zlib.decompress(data)

def decompress_deflate(data: bytes) -> bytes:
    # raw deflate has no zlib header; try raw, then zlib-wrapped as fallback
    try:
        return zlib.decompress(data, -zlib.MAX_WBITS)
    except zlib.error:
        return zlib.decompress(data)

def auto_decompress(data: bytes, content_encoding: str = "") -> bytes:
    ce = content_encoding.lower()
    try:
        if "br" in ce:
            import brotli
            return brotli.decompress(data)
        if "gzip" in ce:
            return decompress_gzip(data)
        if "deflate" in ce:
            return decompress_deflate(data)
    except Exception:
        pass
    # magic-byte sniffing fallback when no/incorrect Content-Encoding header
    if data[:2] == b"\x1f\x8b":
        return decompress_gzip(data)
    try:
        return decompress_zlib(data)
    except Exception:
        return data

# --- Unicode normalization (bypass testing) ---
def unicode_normalize(data: str, form: str = "NFKC") -> str:
    return unicodedata.normalize(form, data)

# --- JWT edit/re-sign + secret cracking ---
def jwt_resign(header: dict, payload: dict, secret: str) -> str:
    def _b64url(obj_bytes: bytes) -> str:
        return base64.urlsafe_b64encode(obj_bytes).rstrip(b"=").decode()
    alg = header.get("alg", "HS256").upper()
    algo_map = {"HS256": hashlib.sha256, "HS384": hashlib.sha384, "HS512": hashlib.sha512}
    if alg not in algo_map:
        raise ValueError(f"Only HMAC algs supported for local re-sign (got {alg})")
    header_b64 = _b64url(json.dumps(header, separators=(",", ":")).encode())
    payload_b64 = _b64url(json.dumps(payload, separators=(",", ":")).encode())
    signing_input = f"{header_b64}.{payload_b64}".encode()
    sig = hmac.HMAC(secret.encode(), signing_input, algo_map[alg]).digest()
    return f"{header_b64}.{payload_b64}.{_b64url(sig)}"

def jwt_crack(token: str, wordlist: list) -> str:
    """Brute-force an HS256/384/512 JWT secret against a candidate wordlist. Returns the
    secret if found, else None. Real HMAC comparison per candidate, not a stub."""
    parts = token.split(".")
    if len(parts) != 3:
        raise ValueError("Not a valid JWT")
    header = json.loads(base64.urlsafe_b64decode(parts[0] + "=" * (-len(parts[0]) % 4)))
    alg = header.get("alg", "HS256").upper()
    algo_map = {"HS256": hashlib.sha256, "HS384": hashlib.sha384, "HS512": hashlib.sha512}
    if alg not in algo_map:
        return None
    signing_input = f"{parts[0]}.{parts[1]}".encode()
    target_sig = parts[2]
    for candidate in wordlist:
        candidate = candidate.strip()
        if not candidate:
            continue
        sig = base64.urlsafe_b64encode(
            hmac.HMAC(candidate.encode(), signing_input, algo_map[alg]).digest()
        ).rstrip(b"=").decode()
        if hmac.compare_digest(sig, target_sig):
            return candidate
    return None


TRANSFORMS = {
    "Base64 Encode": encode_base64,
    "Base64 Decode": decode_base64,
    "URL Encode": encode_url,
    "URL Decode": decode_url,
    "HTML Encode": encode_html,
    "HTML Decode": decode_html,
    "Hex Encode": encode_hex,
    "Hex Decode": decode_hex,
    "MD5 Hash": hash_md5,
    "SHA1 Hash": hash_sha1,
    "SHA256 Hash": hash_sha256,
    "JWT Decode": decode_jwt,
    "Base32 Encode": encode_base32,
    "Base32 Decode": decode_base32,
    "Base85 Encode": encode_base85,
    "Base85 Decode": decode_base85,
    "Base58 Encode": encode_base58,
    "Base58 Decode": decode_base58,
    "Binary Encode": encode_binary,
    "Binary Decode": decode_binary,
    "Octal Encode": encode_octal,
    "Octal Decode": decode_octal,
    "SHA224 Hash": hash_sha224,
    "SHA384 Hash": hash_sha384,
    "SHA512 Hash": hash_sha512,
    "SHA3-256 Hash": hash_sha3_256,
    "BLAKE2b Hash": hash_blake2b,
}
