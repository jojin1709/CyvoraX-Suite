package com.venomproxy.proxy;

import com.venomproxy.db.Database;
import com.venomproxy.model.Finding;
import com.venomproxy.model.HttpTransaction;
import com.venomproxy.model.LogEntry;
import com.venomproxy.model.RequestData;
import com.venomproxy.plugins.PluginLoader;
import com.venomproxy.scanner.PassiveScanner;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.SslContext;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.net.URI;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ProxyServer {
    private static final Set<String> HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade"
    );

    private final Database database;
    private final PassiveScanner passiveScanner;
    private final ScopeControl scopeControl;
    private final CertManager certManager;
    private final PluginLoader pluginLoader;
    private final InterceptHandler interceptHandler = new InterceptHandler();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean intercept = new AtomicBoolean(false);
    private final AtomicLong requestCount = new AtomicLong();
    private final ExecutorService workerPool = Executors.newCachedThreadPool();
    private volatile OkHttpClient client = new OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(Duration.ofSeconds(20))
            .readTimeout(Duration.ofSeconds(60))
            .build();

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private volatile ProxyEventListener listener;
    private volatile String bindHost = "127.0.0.1";
    private volatile int bindPort = 8080;

    public ProxyServer(Database database, PassiveScanner passiveScanner, ScopeControl scopeControl,
                       CertManager certManager, PluginLoader pluginLoader) {
        this.database = database;
        this.passiveScanner = passiveScanner;
        this.scopeControl = scopeControl;
        this.certManager = certManager;
        this.pluginLoader = pluginLoader;
    }

    public synchronized void start(String host, int port) {
        if (running.get()) {
            return;
        }
        this.bindHost = host;
        this.bindPort = port;
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.AUTO_READ, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new HttpServerCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(20 * 1024 * 1024));
                            ch.pipeline().addLast(new ProxyHttpHandler("http", null));
                        }
                    });
            serverChannel = bootstrap.bind(host, port).syncUninterruptibly().channel();
            running.set(true);
            emitLog("SYS", host + ":" + port, "Proxy listener started.");
        } catch (RuntimeException ex) {
            stop();
            throw ex;
        }
    }

    public synchronized void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        emitLog("SYS", bindHost + ":" + bindPort, "Proxy listener stopped.");
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean isIntercept() {
        return intercept.get();
    }

    public void setIntercept(boolean enabled) {
        intercept.set(enabled);
        emitLog("SYS", bindHost + ":" + bindPort, "Intercept " + (enabled ? "enabled." : "disabled."));
    }

    public long getRequestCount() {
        return requestCount.get();
    }

    public void setListener(ProxyEventListener listener) {
        this.listener = listener;
    }

    public void configureNetwork(String upstreamProxy, int timeoutSeconds) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .readTimeout(Duration.ofSeconds(timeoutSeconds));
        if (upstreamProxy != null && !upstreamProxy.isBlank()) {
            String[] parts = upstreamProxy.trim().split(":", 2);
            if (parts.length == 2) {
                builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(parts[0], Integer.parseInt(parts[1]))));
            }
        }
        client = builder.build();
        emitLog("SYS", bindHost + ":" + bindPort, "Network settings updated.");
    }

    private class ProxyHttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        private final String scheme;
        private final String connectHost;

        ProxyHttpHandler(String scheme, String connectHost) {
            this.scheme = scheme;
            this.connectHost = connectHost;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
            requestCount.incrementAndGet();
            if (request.method().equals(HttpMethod.CONNECT)) {
                handleConnect(ctx, request);
                return;
            }

            RequestData requestData = toRequestData(request, scheme, connectHost);
            workerPool.submit(() -> handleHttp(ctx, requestData));
        }

        private void handleHttp(ChannelHandlerContext ctx, RequestData requestData) {
            Instant started = Instant.now();
            String host = extractHost(requestData.getUrl(), requestData.getHeaders().getOrDefault("Host", ""));
            boolean inScope = scopeControl.isInScope(requestData.getUrl());
            boolean ignored = scopeControl.isIgnored(requestData.getUrl());
            emitLog("REQ", host, requestData.getMethod() + " " + requestData.getUrl());

            if (!inScope && !scopeControl.isOutOfScopePassthrough()) {
                writeText(ctx, HttpResponseStatus.FORBIDDEN, "Out of scope");
                return;
            }

            InterceptHandler.InterceptDecision decision = interceptHandler.handle(requestData, intercept.get(), inScope && !ignored, listener);
            if (decision.dropped()) {
                emitLog("DROP", host, requestData.getUrl());
                writeText(ctx, HttpResponseStatus.NO_CONTENT, "Dropped by CyvoraX Suite");
                return;
            }
            requestData = pluginLoader.applyRequestHooks(decision.requestData());

            try {
                ForwardResult result = forward(requestData);
                HttpTransaction transaction = new HttpTransaction(
                        requestData.getMethod(),
                        host,
                        pathFromUrl(requestData.getUrl()),
                        result.status(),
                        result.body().length,
                        result.mimeType(),
                        isWebSocket(requestData.getHeaders()) ? "WS" : result.protocol(),
                        Duration.between(started, Instant.now()).toMillis(),
                        requestData.toRaw(),
                        result.rawResponse(),
                        Instant.now(),
                        isWebSocket(requestData.getHeaders()),
                        inScope
                );
                if (!ignored) {
                    database.saveTransaction(transaction);
                    emitTransaction(transaction);
                    pluginLoader.applyResponseHooks(transaction);
                    List<Finding> allFindings = new java.util.ArrayList<>(passiveScanner.scan(transaction));
                    allFindings.addAll(pluginLoader.applyScannerHooks(transaction));
                    for (Finding finding : allFindings) {
                        database.saveFinding(finding);
                        emitFinding(finding);
                    }
                }
                writeResponse(ctx, result);
                emitLog("RES", host, result.status() + " " + result.body().length + " bytes");
            } catch (Exception ex) {
                emitLog("ERR", host, ex.getMessage());
                writeText(ctx, HttpResponseStatus.BAD_GATEWAY, "CyvoraX Suite upstream error: " + ex.getMessage());
            }
        }

        private void handleConnect(ChannelHandlerContext ctx, FullHttpRequest request) {
            String hostPort = request.uri();
            String[] parts = hostPort.split(":", 2);
            String host = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 443;
            boolean inScope = scopeControl.isInScope(host);
            emitLog("CONNECT", hostPort, inScope ? "HTTPS tunnel requested." : "Out-of-scope HTTPS tunnel requested.");

            if (intercept.get() && inScope && !scopeControl.isIgnored(host)) {
                startMitm(ctx, host);
                return;
            }

            Bootstrap bootstrap = new Bootstrap()
                    .group(ctx.channel().eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.AUTO_READ, false)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new RelayHandler(ctx.channel()));
                        }
                    });

            ChannelFuture connectFuture = bootstrap.connect(host, port);
            connectFuture.addListener((ChannelFutureListener) future -> {
                if (!future.isSuccess()) {
                    writeText(ctx, HttpResponseStatus.BAD_GATEWAY, "Could not connect to " + hostPort);
                    return;
                }

                Channel outbound = future.channel();
                FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                ctx.writeAndFlush(response).addListener(done -> {
                    ChannelPipeline pipeline = ctx.pipeline();
                    removeIfPresent(pipeline, HttpObjectAggregator.class);
                    removeIfPresent(pipeline, HttpServerCodec.class);
                    removeIfPresent(pipeline, ProxyHttpHandler.class);
                    pipeline.addLast(new RelayHandler(outbound));
                    outbound.read();
                    ctx.channel().read();
                });
            });
        }

        private void startMitm(ChannelHandlerContext ctx, String host) {
            try {
                SslContext sslContext = certManager.serverContextFor(host);
                FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                ctx.writeAndFlush(response).addListener(done -> {
                    ChannelPipeline pipeline = ctx.pipeline();
                    removeIfPresent(pipeline, HttpObjectAggregator.class);
                    removeIfPresent(pipeline, HttpServerCodec.class);
                    removeIfPresent(pipeline, ProxyHttpHandler.class);
                    pipeline.addLast(sslContext.newHandler(ctx.alloc()));
                    pipeline.addLast(new HttpServerCodec());
                    pipeline.addLast(new HttpObjectAggregator(20 * 1024 * 1024));
                    pipeline.addLast(new ProxyHttpHandler("https", host));
                    ctx.channel().read();
                    emitLog("MITM", host, "HTTPS interception enabled for host.");
                });
            } catch (Exception ex) {
                emitLog("ERR", host, "HTTPS interception failed: " + ex.getMessage());
                writeText(ctx, HttpResponseStatus.BAD_GATEWAY, "Could not start HTTPS interception: " + ex.getMessage());
            }
        }

        private void removeIfPresent(ChannelPipeline pipeline, Class<? extends ChannelHandler> type) {
            String name = pipeline.context(type) == null ? null : pipeline.context(type).name();
            if (name != null) {
                pipeline.remove(name);
            }
        }
    }

    private RequestData toRequestData(FullHttpRequest request, String scheme, String connectHost) {
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        for (Map.Entry<String, String> header : request.headers()) {
            headers.put(header.getKey(), header.getValue());
        }
        byte[] body = new byte[request.content().readableBytes()];
        request.content().getBytes(request.content().readerIndex(), body);
        String url = request.uri();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            String host = headers.getOrDefault("Host", connectHost == null ? "localhost" : connectHost);
            url = scheme + "://" + host + url;
        }
        return new RequestData(request.method().name(), url, headers, body);
    }

    private ForwardResult forward(RequestData data) throws Exception {
        Request.Builder builder = new Request.Builder().url(data.getUrl());
        Headers.Builder headers = new Headers.Builder();
        data.getHeaders().forEach((key, value) -> {
            if (!HOP_HEADERS.contains(key.toLowerCase(Locale.ROOT))) {
                headers.add(key, value);
            }
        });
        builder.headers(headers.build());

        RequestBody body = null;
        if (methodAllowsBody(data.getMethod())) {
            String contentType = data.getHeaders().getOrDefault("Content-Type", "application/octet-stream");
            body = RequestBody.create(data.getBody(), MediaType.parse(contentType));
        }
        builder.method(data.getMethod(), body);

        try (Response response = client.newCall(builder.build()).execute()) {
            ResponseBody responseBody = response.body();
            byte[] bytes = responseBody == null ? new byte[0] : responseBody.bytes();
            String mime = response.header("Content-Type", "");
            String raw = rawResponse(response, bytes);
            return new ForwardResult(response.code(), response.message(), response.headers(), bytes, mime, protocolName(response.protocol()), raw);
        }
    }

    private String protocolName(okhttp3.Protocol protocol) {
        return switch (protocol) {
            case HTTP_2 -> "HTTP/2";
            case H2_PRIOR_KNOWLEDGE -> "HTTP/2";
            case HTTP_1_0 -> "HTTP/1.0";
            case HTTP_1_1 -> "HTTP/1.1";
            default -> protocol.toString();
        };
    }

    private boolean methodAllowsBody(String method) {
        String upper = method.toUpperCase(Locale.ROOT);
        return !(upper.equals("GET") || upper.equals("HEAD"));
    }

    private String rawResponse(Response response, byte[] body) {
        StringBuilder builder = new StringBuilder();
        builder.append("HTTP/1.1 ").append(response.code()).append(' ').append(response.message()).append("\r\n");
        for (String name : response.headers().names()) {
            for (String value : response.headers(name)) {
                builder.append(name).append(": ").append(value).append("\r\n");
            }
        }
        builder.append("\r\n");
        builder.append(new String(body, StandardCharsets.UTF_8));
        return builder.toString();
    }

    private void writeResponse(ChannelHandlerContext ctx, ForwardResult result) {
        FullHttpResponse nettyResponse = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.valueOf(result.status(), result.message()),
                Unpooled.wrappedBuffer(result.body())
        );
        for (String name : result.headers().names()) {
            if (!HOP_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                nettyResponse.headers().set(name, result.headers().values(name));
            }
        }
        nettyResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, result.body().length);
        ctx.writeAndFlush(nettyResponse).addListener(ChannelFutureListener.CLOSE);
    }

    private void writeText(ChannelHandlerContext ctx, HttpResponseStatus status, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=utf-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private String extractHost(String url, String fallback) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? fallback : host;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private String pathFromUrl(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath();
            return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
        } catch (RuntimeException ex) {
            return url;
        }
    }

    private boolean isWebSocket(Map<String, String> headers) {
        return headers.entrySet().stream().anyMatch(entry ->
                entry.getKey().equalsIgnoreCase("Upgrade") && entry.getValue().equalsIgnoreCase("websocket"));
    }

    private void emitTransaction(HttpTransaction transaction) {
        ProxyEventListener currentListener = listener;
        if (currentListener != null) {
            currentListener.onTransaction(transaction);
        }
    }

    private void emitFinding(Finding finding) {
        ProxyEventListener currentListener = listener;
        if (currentListener != null) {
            currentListener.onFinding(finding);
        }
    }

    private void emitLog(String direction, String host, String message) {
        LogEntry entry = new LogEntry(Instant.now(), direction, host, message);
        try {
            database.saveLog(entry);
        } catch (RuntimeException ignored) {
        }
        ProxyEventListener currentListener = listener;
        if (currentListener != null) {
            currentListener.onLog(entry);
        }
    }

    private record ForwardResult(int status, String message, Headers headers, byte[] body, String mimeType, String protocol, String rawResponse) {
    }

    private static class RelayHandler extends ChannelInboundHandlerAdapter {
        private final Channel target;

        RelayHandler(Channel target) {
            this.target = target;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.read();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object message) {
            if (target.isActive()) {
                target.writeAndFlush(message).addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        ctx.channel().read();
                    } else {
                        closeOnFlush(ctx.channel());
                    }
                });
            } else if (message instanceof ByteBuf byteBuf) {
                byteBuf.release();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            closeOnFlush(target);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            closeOnFlush(ctx.channel());
        }

        private static void closeOnFlush(Channel channel) {
            if (channel.isActive()) {
                channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
        }
    }
}
