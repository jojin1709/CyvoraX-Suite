package com.venomproxy.proxy;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.IOException;
import java.io.Writer;
import java.math.BigInteger;
import java.net.IDN;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class CertManager {
    private final Path certDirectory;
    private final Path certPath;
    private final Path keyPath;
    private final Map<String, ServerCertificate> serverCertificates = Collections.synchronizedMap(
            new LinkedHashMap<>(512, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ServerCertificate> eldest) {
                    return size() > 512;
                }
            }
    );
    private X509Certificate certificate;
    private KeyPair keyPair;
    private PrivateKey caPrivateKey;

    public CertManager(Path certDirectory) {
        this.certDirectory = certDirectory;
        this.certPath = certDirectory.resolve("cyvorax-suite-ca-cert.pem");
        this.keyPath = certDirectory.resolve("cyvorax-suite-ca-key.pem");
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public synchronized void ensureCa() throws Exception {
        Files.createDirectories(certDirectory);
        if (Files.exists(certPath) && Files.exists(keyPath)) {
            loadExistingCa();
            if (isUsableCa(certificate)) {
                return;
            }
            Files.deleteIfExists(certPath);
            Files.deleteIfExists(keyPath);
            serverCertificates.clear();
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        caPrivateKey = keyPair.getPrivate();

        X500Name subject = new X500Name("CN=CyvoraX Suite Local Research CA,O=CyvoraX Suite");
        Instant now = Instant.now();
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(now.toEpochMilli()),
                Date.from(now.minus(1, ChronoUnit.DAYS)),
                Date.from(now.plus(3650, ChronoUnit.DAYS)),
                subject,
                keyPair.getPublic()
        );
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(keyPair.getPrivate());
        X509CertificateHolder holder = builder.build(signer);
        certificate = new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(holder);
        writePem(certPath, certificate);
        writePem(keyPath, keyPair.getPrivate());
    }

    public synchronized Path exportCertificate(Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        Files.copy(certPath, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return destination;
    }

    public Path getCertificatePath() {
        return certPath;
    }

    public synchronized String healthStatus() {
        try {
            ensureCa();
            certificate.checkValidity();
            return "CA present, valid until " + certificate.getNotAfter();
        } catch (Exception ex) {
            return "CA invalid: " + ex.getMessage();
        }
    }

    public SslContext serverContextFor(String host) throws Exception {
        return serverCertificateFor(host).sslContext();
    }

    public ServerCertificate serverCertificateFor(String host) throws Exception {
        ensureCa();
        String certificateHost = certificateHostFor(host);
        return serverCertificates.computeIfAbsent(certificateHost, name -> {
            try {
                return buildServerCertificate(name);
            } catch (Exception ex) {
                throw new IllegalStateException("Could not create server certificate for " + name, ex);
            }
        });
    }

    public synchronized X509Certificate rootCertificate() throws Exception {
        ensureCa();
        return certificate;
    }

    public List<X509Certificate> certificateChainFor(String host) throws Exception {
        return serverCertificateFor(host).chain();
    }

    public TlsValidationResult validateTlsForHost(String host) {
        try {
            ServerCertificate serverCertificate = serverCertificateFor(host);
            X509Certificate leaf = serverCertificate.leafCertificate();
            X509Certificate root = rootCertificate();
            boolean sanMatches = hasDnsSubjectAlternativeName(leaf, serverCertificate.host());
            boolean hostnameMatches = certificateHostFor(host).equals(serverCertificate.host()) && sanMatches;
            boolean chainValid = validateChain(leaf, root);
            return new TlsValidationResult(serverCertificate.host(), sanMatches, hostnameMatches, chainValid,
                    serverCertificate.chain().size(), "TLS certificate validation passed");
        } catch (Exception ex) {
            return new TlsValidationResult(certificateHostFor(host), false, false, false, 0,
                    "TLS certificate validation failed: " + ex.getMessage());
        }
    }

    public String tlsValidationReport(String host) {
        TlsValidationResult result = validateTlsForHost(host);
        return "Host: " + result.host() + "\n"
                + "SAN DNS match: " + status(result.sanMatches()) + "\n"
                + "Hostname match: " + status(result.hostnameMatches()) + "\n"
                + "Chain valid: " + status(result.chainValid()) + "\n"
                + "Chain certificates: " + result.chainLength() + "\n"
                + result.message();
    }

    public String certificateHostFor(String host) {
        String value = host == null ? "" : host.trim();
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            if (end > 0) {
                value = value.substring(1, end);
            }
        } else {
            int slash = value.indexOf('/');
            if (slash >= 0) {
                value = value.substring(0, slash);
            }
            int colon = value.indexOf(':');
            if (colon > 0 && value.indexOf(':', colon + 1) < 0) {
                value = value.substring(0, colon);
            }
        }
        if (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("Certificate host is blank");
        }
        return IDN.toASCII(value.toLowerCase(java.util.Locale.ROOT));
    }

    private ServerCertificate buildServerCertificate(String host) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair serverKeyPair = generator.generateKeyPair();

        Instant now = Instant.now();
        X500Name issuer = X500Name.getInstance(certificate.getSubjectX500Principal().getEncoded());
        X500Name subject = new X500Name("CN=" + host + ",O=CyvoraX Suite");
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuer,
                BigInteger.valueOf(now.toEpochMilli()),
                Date.from(now.minus(1, ChronoUnit.HOURS)),
                Date.from(now.plus(30, ChronoUnit.DAYS)),
                subject,
                serverKeyPair.getPublic()
        );
        JcaX509ExtensionUtils extensionUtils = new JcaX509ExtensionUtils();
        builder.addExtension(Extension.subjectKeyIdentifier, false, extensionUtils.createSubjectKeyIdentifier(serverKeyPair.getPublic()));
        builder.addExtension(Extension.authorityKeyIdentifier, false, extensionUtils.createAuthorityKeyIdentifier(certificate.getPublicKey()));
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
        builder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
        builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(new GeneralName(GeneralName.dNSName, host)));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(caPrivateKey);
        X509Certificate serverCertificate = new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(builder.build(signer));
        List<X509Certificate> chain = List.of(serverCertificate, certificate);
        SslContext sslContext = SslContextBuilder.forServer(serverKeyPair.getPrivate(), serverCertificate, certificate).build();
        return new ServerCertificate(host, sslContext, serverCertificate, chain);
    }

    private void loadExistingCa() throws Exception {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        try (var input = Files.newInputStream(certPath)) {
            certificate = (X509Certificate) certificateFactory.generateCertificate(input);
        }

        try (PEMParser parser = new PEMParser(Files.newBufferedReader(keyPath))) {
            Object object = parser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME);
            if (object instanceof PEMKeyPair pemKeyPair) {
                keyPair = converter.getKeyPair(pemKeyPair);
                caPrivateKey = keyPair.getPrivate();
            } else if (object instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo privateKeyInfo) {
                caPrivateKey = converter.getPrivateKey(privateKeyInfo);
                keyPair = new KeyPair(certificate.getPublicKey(), caPrivateKey);
            } else {
                throw new IllegalStateException("Unsupported CA private key format.");
            }
        }
    }

    private void writePem(Path path, Object value) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path);
             JcaPEMWriter pemWriter = new JcaPEMWriter(writer)) {
            pemWriter.writeObject(value);
        }
    }

    private boolean isUsableCa(X509Certificate caCertificate) {
        boolean[] keyUsage = caCertificate.getKeyUsage();
        return caCertificate.getPublicKey().getAlgorithm().equalsIgnoreCase("RSA")
                && caCertificate.getPublicKey() instanceof java.security.interfaces.RSAPublicKey rsaPublicKey
                && rsaPublicKey.getModulus().bitLength() >= 2048
                && "SHA256WITHRSA".equalsIgnoreCase(caCertificate.getSigAlgName().replace("ENCRYPTION", ""))
                && caCertificate.getBasicConstraints() >= 0
                && keyUsage != null
                && keyUsage[5]
                && keyUsage[6];
    }

    private boolean hasDnsSubjectAlternativeName(X509Certificate certificate, String host) throws Exception {
        var names = certificate.getSubjectAlternativeNames();
        if (names == null) {
            return false;
        }
        for (List<?> name : names) {
            if (name.size() >= 2 && ((Number) name.get(0)).intValue() == GeneralName.dNSName
                    && host.equalsIgnoreCase(String.valueOf(name.get(1)))) {
                return true;
            }
        }
        return false;
    }

    private boolean validateChain(X509Certificate leaf, X509Certificate root) throws Exception {
        leaf.verify(root.getPublicKey());
        CertPath certPath = CertificateFactory.getInstance("X.509").generateCertPath(List.of(leaf));
        PKIXParameters parameters = new PKIXParameters(Set.of(new TrustAnchor(root, null)));
        parameters.setRevocationEnabled(false);
        CertPathValidator.getInstance("PKIX").validate(certPath, parameters);
        return true;
    }

    private String status(boolean value) {
        return value ? "PASS" : "FAIL";
    }

    public record ServerCertificate(String host, SslContext sslContext, X509Certificate leafCertificate,
                                    List<X509Certificate> chain) {
        public ServerCertificate {
            chain = List.copyOf(chain);
        }
    }

    public record TlsValidationResult(String host, boolean sanMatches, boolean hostnameMatches, boolean chainValid,
                                      int chainLength, String message) {
    }
}
