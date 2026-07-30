package com.venomproxy.proxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.bouncycastle.asn1.x509.GeneralName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CertManagerTest {
    @TempDir
    Path tempDir;

    @Test
    @SuppressWarnings("unchecked")
    void serverContextCacheIsBoundedTo512Entries() throws Exception {
        CertManager certManager = new CertManager(tempDir);
        Field field = CertManager.class.getDeclaredField("serverCertificates");
        field.setAccessible(true);
        Map<String, Object> cache = (Map<String, Object>) field.get(certManager);

        for (int i = 0; i < 600; i++) {
            cache.put("host-" + i + ".example.test", null);
        }

        assertEquals(512, cache.size());
    }

    @Test
    void rootCaUsesBrowserCompatibleConstraints() throws Exception {
        CertManager certManager = new CertManager(tempDir);

        X509Certificate root = certManager.rootCertificate();
        boolean[] keyUsage = root.getKeyUsage();

        assertEquals("RSA", root.getPublicKey().getAlgorithm());
        assertTrue(((java.security.interfaces.RSAPublicKey) root.getPublicKey()).getModulus().bitLength() >= 2048);
        assertEquals("SHA256WITHRSA", root.getSigAlgName().replace("ENCRYPTION", "").toUpperCase(java.util.Locale.ROOT));
        assertTrue(root.getBasicConstraints() >= 0);
        assertNotNull(keyUsage);
        assertTrue(keyUsage[5]);
        assertTrue(keyUsage[6]);
    }

    @Test
    void generatedLeafCertificateHasSanServerAuthAndClientTlsUsages() throws Exception {
        CertManager certManager = new CertManager(tempDir);

        List<X509Certificate> chain = certManager.certificateChainFor("example.com");
        X509Certificate leaf = chain.get(0);
        boolean[] keyUsage = leaf.getKeyUsage();

        assertEquals(2, chain.size());
        assertEquals(-1, leaf.getBasicConstraints());
        assertNotNull(keyUsage);
        assertTrue(keyUsage[0]);
        assertTrue(keyUsage[2]);
        assertEquals(List.of("1.3.6.1.5.5.7.3.1"), leaf.getExtendedKeyUsage());
        assertTrue(hasDnsSan(leaf, "example.com"));
    }

    @Test
    void generatedCertificateChainValidatesAgainstCyvoraxRoot() throws Exception {
        CertManager certManager = new CertManager(tempDir);

        List<X509Certificate> chain = certManager.certificateChainFor("example.com");
        X509Certificate leaf = chain.get(0);
        X509Certificate root = chain.get(1);

        leaf.verify(root.getPublicKey());
        CertPath certPath = CertificateFactory.getInstance("X.509").generateCertPath(List.of(leaf));
        PKIXParameters parameters = new PKIXParameters(Set.of(new TrustAnchor(root, null)));
        parameters.setRevocationEnabled(false);

        assertDoesNotThrow(() -> CertPathValidator.getInstance("PKIX").validate(certPath, parameters));
    }

    @Test
    void tlsValidationConfirmsHostnameMatchesSan() {
        CertManager certManager = new CertManager(tempDir);

        CertManager.TlsValidationResult result = certManager.validateTlsForHost("example.com");

        assertEquals("example.com", result.host());
        assertTrue(result.sanMatches());
        assertTrue(result.hostnameMatches());
        assertTrue(result.chainValid());
        assertEquals(2, result.chainLength());
    }

    @Test
    void generatedCertificateCompletesHostnameVerifiedTlsHandshake() throws Exception {
        CertManager certManager = new CertManager(tempDir);
        CertManager.ServerCertificate serverCertificate = certManager.serverCertificateFor("example.com");
        NioEventLoopGroup boss = new NioEventLoopGroup(1);
        NioEventLoopGroup worker = new NioEventLoopGroup(1);
        Channel server = null;
        try {
            server = new ServerBootstrap()
                    .group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(serverCertificate.sslContext().newHandler(ch.alloc()));
                            ch.pipeline().addLast(new SimpleChannelInboundHandler<io.netty.buffer.ByteBuf>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, io.netty.buffer.ByteBuf msg) {
                                    ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    ctx.close();
                                }
                            });
                        }
                    })
                    .bind("127.0.0.1", 0)
                    .sync()
                    .channel();
            int port = ((InetSocketAddress) server.localAddress()).getPort();

            SSLContext clientContext = trustedClientContext(certManager.rootCertificate());
            try (Socket socket = new Socket("127.0.0.1", port);
                 SSLSocket sslSocket = (SSLSocket) clientContext.getSocketFactory()
                         .createSocket(socket, "example.com", port, true)) {
                SSLParameters parameters = sslSocket.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                parameters.setServerNames(List.of(new SNIHostName("example.com")));
                sslSocket.setSSLParameters(parameters);

                assertDoesNotThrow(sslSocket::startHandshake);
            }
        } finally {
            if (server != null) {
                server.close().sync();
            }
            boss.shutdownGracefully().sync();
            worker.shutdownGracefully().sync();
        }
    }

    private boolean hasDnsSan(X509Certificate certificate, String host) throws Exception {
        for (List<?> name : certificate.getSubjectAlternativeNames()) {
            if (((Number) name.get(0)).intValue() == GeneralName.dNSName && host.equals(name.get(1))) {
                return true;
            }
        }
        return false;
    }

    private SSLContext trustedClientContext(X509Certificate rootCertificate) throws Exception {
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("cyvorax-root", rootCertificate);
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustManagerFactory.getTrustManagers(), null);
        return context;
    }
}
