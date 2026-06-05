package com.venomproxy.plugins;

import com.venomproxy.model.Finding;
import com.venomproxy.model.HttpTransaction;
import com.venomproxy.model.RequestData;
import javafx.scene.Node;

import java.util.List;
import java.util.Optional;

public interface VenomPlugin {
    String name();

    default String description() {
        return "CyvoraX Suite plugin";
    }

    default void onLoad(VenomPluginContext context) {
    }

    default RequestData onRequest(RequestData request) {
        return request;
    }

    default void onResponse(HttpTransaction transaction) {
    }

    default List<Finding> scan(HttpTransaction transaction) {
        return List.of();
    }

    default Optional<Node> uiTab() {
        return Optional.empty();
    }
}
