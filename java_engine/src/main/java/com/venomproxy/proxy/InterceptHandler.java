package com.venomproxy.proxy;

import com.venomproxy.model.RequestData;

public class InterceptHandler {
    public InterceptDecision handle(RequestData requestData, boolean enabled, boolean inScope, ProxyEventListener listener) {
        if (!enabled || !inScope) {
            return InterceptDecision.forward(requestData);
        }

        InterceptedRequest pending = new InterceptedRequest(requestData);
        if (listener != null) {
            listener.onInterceptPending(pending);
        }

        try {
            pending.awaitDecision(120);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return InterceptDecision.drop();
        }

        if (pending.getDecision() == InterceptedRequest.Decision.DROP) {
            return InterceptDecision.drop();
        }
        return InterceptDecision.forward(pending.getRequestData());
    }

    public record InterceptDecision(RequestData requestData, boolean dropped) {
        public static InterceptDecision forward(RequestData requestData) {
            return new InterceptDecision(requestData, false);
        }

        public static InterceptDecision drop() {
            return new InterceptDecision(null, true);
        }
    }
}
