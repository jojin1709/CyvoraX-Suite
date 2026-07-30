"""
CyvoraX Suite - Certificate Authority module
Generates a root CA (once) and per-host leaf certs signed by it on the fly,
so the proxy can MITM TLS connections (same technique Burp/ZAP use).
"""
import os
import datetime
import ipaddress
from cryptography import x509
from cryptography.x509.oid import NameOID, ExtendedKeyUsageOID
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa

CERT_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "certs")
CA_KEY_PATH = os.path.join(CERT_DIR, "cyvorax-ca.key")
CA_CERT_PATH = os.path.join(CERT_DIR, "cyvorax-ca.crt")
HOST_CERT_DIR = os.path.join(CERT_DIR, "hosts")


class CertAuthority:
    def __init__(self):
        os.makedirs(CERT_DIR, exist_ok=True)
        os.makedirs(HOST_CERT_DIR, exist_ok=True)
        self._cache = {}
        self.ca_key, self.ca_cert = self._load_or_create_ca()

    def _load_or_create_ca(self):
        if os.path.exists(CA_KEY_PATH) and os.path.exists(CA_CERT_PATH):
            with open(CA_KEY_PATH, "rb") as f:
                key = serialization.load_pem_private_key(f.read(), password=None)
            with open(CA_CERT_PATH, "rb") as f:
                cert = x509.load_pem_x509_certificate(f.read())
            return key, cert

        key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        subject = issuer = x509.Name([
            x509.NameAttribute(NameOID.COUNTRY_NAME, "IN"),
            x509.NameAttribute(NameOID.ORGANIZATION_NAME, "CyvoraX Suite"),
            x509.NameAttribute(NameOID.COMMON_NAME, "CyvoraX Suite Root CA"),
        ])
        cert = (
            x509.CertificateBuilder()
            .subject_name(subject)
            .issuer_name(issuer)
            .public_key(key.public_key())
            .serial_number(x509.random_serial_number())
            .not_valid_before(datetime.datetime.utcnow() - datetime.timedelta(days=1))
            .not_valid_after(datetime.datetime.utcnow() + datetime.timedelta(days=3650))
            .add_extension(x509.BasicConstraints(ca=True, path_length=0), critical=True)
            .add_extension(x509.KeyUsage(
                digital_signature=True, content_commitment=False, key_encipherment=False,
                data_encipherment=False, key_agreement=False, key_cert_sign=True,
                crl_sign=True, encipher_only=False, decipher_only=False), critical=True)
            .sign(key, hashes.SHA256())
        )
        with open(CA_KEY_PATH, "wb") as f:
            f.write(key.private_bytes(
                encoding=serialization.Encoding.PEM,
                format=serialization.PrivateFormat.TraditionalOpenSSL,
                encryption_algorithm=serialization.NoEncryption()))
        with open(CA_CERT_PATH, "wb") as f:
            f.write(cert.public_bytes(serialization.Encoding.PEM))
        return key, cert

    def get_host_cert(self, hostname: str):
        """Return (cert_path, key_path) for hostname, generating+caching if needed."""
        if hostname in self._cache:
            return self._cache[hostname]

        cert_path = os.path.join(HOST_CERT_DIR, f"{hostname}.crt")
        key_path = os.path.join(HOST_CERT_DIR, f"{hostname}.key")
        if os.path.exists(cert_path) and os.path.exists(key_path):
            self._cache[hostname] = (cert_path, key_path)
            return cert_path, key_path

        key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        subject = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, hostname)])

        try:
            san = x509.SubjectAlternativeName([x509.IPAddress(ipaddress.ip_address(hostname))])
        except ValueError:
            san = x509.SubjectAlternativeName([x509.DNSName(hostname)])

        cert = (
            x509.CertificateBuilder()
            .subject_name(subject)
            .issuer_name(self.ca_cert.subject)
            .public_key(key.public_key())
            .serial_number(x509.random_serial_number())
            .not_valid_before(datetime.datetime.utcnow() - datetime.timedelta(days=1))
            .not_valid_after(datetime.datetime.utcnow() + datetime.timedelta(days=825))
            .add_extension(san, critical=False)
            .add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH]), critical=False)
            .sign(self.ca_key, hashes.SHA256())
        )
        with open(key_path, "wb") as f:
            f.write(key.private_bytes(
                encoding=serialization.Encoding.PEM,
                format=serialization.PrivateFormat.TraditionalOpenSSL,
                encryption_algorithm=serialization.NoEncryption()))
        with open(cert_path, "wb") as f:
            f.write(cert.public_bytes(serialization.Encoding.PEM))

        self._cache[hostname] = (cert_path, key_path)
        return cert_path, key_path
