package com.venomproxy.ui;

import com.venomproxy.proxy.InterceptedRequest;
import com.venomproxy.proxy.ProxyServer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

public class ProxyTab extends Tab {
    private final ProxyServer proxyServer;
    private final TextArea pendingEditor = UiUtil.codeArea("Intercepted request will appear here");
    private final Label pendingLabel = new Label("No intercepted request");
    private final Label status = new Label("Ready");
    private final Button forward = new Button("Forward");
    private final Button drop = new Button("Drop");
    private final Label emptyStateTitle = new Label();
    private final Label emptyStateDetail = new Label();
    private final VBox emptyState = interceptEmptyState();
    private InterceptedRequest pending;

    public ProxyTab(ProxyServer proxyServer) {
        super("Proxy");
        this.proxyServer = proxyServer;
        setClosable(false);

        Button interceptToggle = new Button(proxyServer.isIntercept() ? " Intercept on" : " Intercept off");
        interceptToggle.getStyleClass().addAll("btn-intercept",
                proxyServer.isIntercept() ? "btn-intercept-on" : "btn-intercept-off");
        Tooltip.install(interceptToggle, new Tooltip("Hold matching requests for manual review"));
        interceptToggle.setOnAction(event -> {
            boolean enabled = !proxyServer.isIntercept();
            proxyServer.setIntercept(enabled);
            interceptToggle.setText(enabled ? " Intercept on" : " Intercept off");
            interceptToggle.getStyleClass().setAll("btn-intercept",
                    enabled ? "btn-intercept-on" : "btn-intercept-off");
            updateInterceptState(enabled);
        });

        forward.setText(" Forward");
        forward.getStyleClass().add("btn-forward");
        forward.setDisable(true);
        forward.setOnAction(event -> {
            if (pending != null) {
                pending.forward(pendingEditor.getText());
                clearPending();
                status.setText("Forwarded intercepted request");
            }
        });

        Button forwardDropdown = new Button("▾");
        forwardDropdown.getStyleClass().add("btn-dropdown");

        drop.setText(" Drop");
        drop.getStyleClass().add("btn-drop");
        drop.setDisable(true);
        drop.setOnAction(event -> {
            if (pending != null) {
                pending.drop();
                clearPending();
                status.setText("Dropped intercepted request");
            }
        });

        Button dropDropdown = new Button("▾");
        dropDropdown.getStyleClass().add("btn-dropdown");

        HBox forwardGroup = new HBox(0, forward, forwardDropdown);
        HBox dropGroup = new HBox(0, drop, dropDropdown);

        Button openBrowser = new Button(" Open browser");
        openBrowser.getStyleClass().add("btn-open-browser");
        Tooltip.install(openBrowser, new Tooltip("Open a browser configured for the proxy"));
        openBrowser.setOnAction(event -> status.setText("Configure a browser to use 127.0.0.1:8080"));

        HBox toolbar = new HBox(8, interceptToggle, forwardGroup, dropGroup, pendingLabel, spacer(), openBrowser);
        toolbar.getStyleClass().add("intercept-toolbar");
        toolbar.setPadding(new Insets(8, 12, 8, 12));
        toolbar.setAlignment(Pos.CENTER_LEFT);

        pendingEditor.setVisible(false);
        pendingEditor.setManaged(false);
        VBox.setVgrow(pendingEditor, Priority.ALWAYS);
        VBox.setVgrow(emptyState, Priority.ALWAYS);

        VBox root = new VBox(toolbar, emptyState, pendingEditor, status);
        root.getStyleClass().add("intercept-root");
        VBox.setVgrow(emptyState, Priority.ALWAYS);
        updateEmptyState(proxyServer.isIntercept());
        setContent(root);
    }

    private VBox interceptEmptyState() {
        Label icon = new Label("🚦");
        icon.getStyleClass().add("intercept-icon");
        emptyStateTitle.getStyleClass().add("empty-state-title");
        emptyStateDetail.getStyleClass().add("empty-state-sub");
        emptyStateDetail.setTextAlignment(TextAlignment.CENTER);

        Button learnMore = new Button("Learn more");
        learnMore.getStyleClass().add("btn-learn-more");
        learnMore.setOnAction(e -> status.setText("Configure intercept rules in Proxy settings"));

        Button openBrowserBtn = new Button("Open browser");
        openBrowserBtn.getStyleClass().add("btn-open-browser-center");
        openBrowserBtn.setOnAction(e -> status.setText("Configure a browser to use 127.0.0.1:8080"));

        HBox buttons = new HBox(10, learnMore, openBrowserBtn);
        buttons.setAlignment(Pos.CENTER);

        VBox box = new VBox(14, icon, emptyStateTitle, emptyStateDetail, buttons);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("intercept-empty-state");
        VBox.setVgrow(box, Priority.ALWAYS);
        return box;
    }

    private void updateInterceptState(boolean enabled) {
        status.setText(enabled ? "Intercept enabled" : "Intercept disabled");
        updateEmptyState(enabled);
        if (!enabled && pending == null) {
            emptyState.setVisible(true);
            emptyState.setManaged(true);
            pendingEditor.setVisible(false);
            pendingEditor.setManaged(false);
        }
    }

    private void updateEmptyState(boolean enabled) {
        emptyStateTitle.setText(enabled ? "Intercept is on" : "Intercept is off");
        emptyStateDetail.setText(enabled
                ? "Messages between CyvoraX's browser and your target servers are held here. This enables you to\nanalyze and modify these messages, before you forward them."
                : "If you turn Intercept on, messages between CyvoraX's browser and your target servers are held here.\nThis enables you to analyze and modify these messages, before you forward them.");
    }

    private void clearPending() {
        pending = null;
        pendingEditor.clear();
        pendingEditor.setVisible(false);
        pendingEditor.setManaged(false);
        emptyState.setVisible(true);
        emptyState.setManaged(true);
        updateEmptyState(proxyServer.isIntercept());
        pendingLabel.setText("No intercepted request");
        forward.setDisable(true);
        drop.setDisable(true);
    }

    public void showPending(InterceptedRequest request) {
        this.pending = request;
        pendingLabel.setText(request.getRequestData().getMethod() + " " + request.getRequestData().getUrl());
        pendingEditor.setText(request.getRawRequest());
        pendingEditor.setVisible(true);
        pendingEditor.setManaged(true);
        emptyState.setVisible(false);
        emptyState.setManaged(false);
        forward.setDisable(false);
        drop.setDisable(false);
        status.setText("Request intercepted - edit and Forward or Drop");
    }

    private HBox spacer() {
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}
