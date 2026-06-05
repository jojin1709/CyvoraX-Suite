package com.venomproxy.proxy;

import com.venomproxy.model.RequestData;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class InterceptedRequest {
    public enum Decision {
        WAITING,
        FORWARD,
        DROP
    }

    private final String id = UUID.randomUUID().toString();
    private final Instant timestamp = Instant.now();
    private final CountDownLatch latch = new CountDownLatch(1);
    private volatile RequestData requestData;
    private volatile Decision decision = Decision.WAITING;

    public InterceptedRequest(RequestData requestData) {
        this.requestData = requestData;
    }

    public boolean awaitDecision(long timeoutSeconds) throws InterruptedException {
        return latch.await(timeoutSeconds, TimeUnit.SECONDS);
    }

    public void forward(String rawRequest) {
        if (rawRequest != null && !rawRequest.isBlank()) {
            requestData = RequestData.fromRaw(rawRequest);
        }
        decision = Decision.FORWARD;
        latch.countDown();
    }

    public void drop() {
        decision = Decision.DROP;
        latch.countDown();
    }

    public String getId() {
        return id;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public RequestData getRequestData() {
        return requestData;
    }

    public String getRawRequest() {
        return requestData.toRaw();
    }

    public Decision getDecision() {
        return decision;
    }
}
