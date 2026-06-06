package com.venomproxy.ui;

import com.venomproxy.proxy.InterceptedRequest;
import com.venomproxy.proxy.ProxyServer;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class ProxyTab extends Tab {
    private final ProxyServer proxyServer;
    private final TextArea pendingEditor = UiUtil.codeArea("Intercepted request will appear here");
    private final Label pendingLabel = new Label("No intercepted request");
    private final Label status = new Label("Ready");
    private InterceptedRequest pending;

    public ProxyTab(ProxyServer proxyServer) {
        super("Proxy");
        this.proxyServer = proxyServer;
        setClosable(false);

        Button intercept = new Button("Intercept Off");
        intercept.setOnAction(event -> {
            boolean enabled = !proxyServer.isIntercept();
            proxyServer.setIntercept(enabled);
            intercept.setText(enabled ? "Intercept On" : "Intercept Off");
            status.setText(enabled ? "Intercept enabled" : "Intercept disabled");
        });

        Button forward = new Button("Forward");
        forward.setOnAction(event -> {
            if (pending != null) {
                pending.forward(pendingEditor.getText());
                pending = null;
                pendingEditor.clear();
                pendingLabel.setText("No intercepted request");
                status.setText("Forwarded intercepted request");
            } else {
                status.setText("No pending request to forward");
            }
        });

        Button drop = new Button("Drop");
        drop.setOnAction(event -> {
            if (pending != null) {
                pending.drop();
                pending = null;
                pendingEditor.clear();
                pendingLabel.setText("No intercepted request");
                status.setText("Dropped intercepted request");
            } else {
                status.setText("No pending request to drop");
            }
        });

        VBox root = new VBox(10, new HBox(10, intercept, forward, drop, pendingLabel, status), pendingEditor);
        root.setPadding(new Insets(12));
        VBox.setVgrow(pendingEditor, Priority.ALWAYS);
        setContent(root);
    }

    public void showPending(InterceptedRequest request) {
        this.pending = request;
        pendingLabel.setText("Pending: " + request.getRequestData().getMethod() + " " + request.getRequestData().getUrl());
        pendingEditor.setText(request.getRawRequest());
        status.setText("Intercepted request waiting");
    }
}
