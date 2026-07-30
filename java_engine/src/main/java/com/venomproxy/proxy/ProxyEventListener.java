package com.venomproxy.proxy;

import com.venomproxy.model.Finding;
import com.venomproxy.model.HttpTransaction;
import com.venomproxy.model.LogEntry;

public interface ProxyEventListener {
    void onTransaction(HttpTransaction transaction);

    void onFinding(Finding finding);

    void onLog(LogEntry entry);

    void onInterceptPending(InterceptedRequest request);
}
