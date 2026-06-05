package com.venomproxy.plugins;

import com.venomproxy.db.Database;
import com.venomproxy.model.Finding;
import com.venomproxy.model.HttpTransaction;
import com.venomproxy.model.RequestData;
import com.venomproxy.proxy.ScopeControl;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

public class PluginLoader {
    private final Path pluginDirectory;
    private final List<VenomPlugin> plugins = new ArrayList<>();
    private final Map<String, Boolean> enabledByName = new HashMap<>();

    public PluginLoader(Path pluginDirectory) {
        this.pluginDirectory = pluginDirectory;
    }

    public synchronized List<VenomPlugin> load(Database database, ScopeControl scopeControl) {
        plugins.clear();
        try {
            Files.createDirectories(pluginDirectory);
            List<URL> urls = new ArrayList<>();
            try (var stream = Files.list(pluginDirectory)) {
                stream.filter(path -> path.toString().endsWith(".jar")).forEach(path -> {
                    try {
                        urls.add(path.toUri().toURL());
                    } catch (Exception ignored) {
                    }
                });
            }
            URLClassLoader classLoader = new URLClassLoader(urls.toArray(URL[]::new), getClass().getClassLoader());
            ServiceLoader<VenomPlugin> serviceLoader = ServiceLoader.load(VenomPlugin.class, classLoader);
            VenomPluginContext context = new VenomPluginContext(database, scopeControl, pluginDirectory);
            for (VenomPlugin plugin : serviceLoader) {
                plugin.onLoad(context);
                plugins.add(plugin);
                enabledByName.putIfAbsent(plugin.name(), true);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Could not load plugins", ex);
        }
        return List.copyOf(plugins);
    }

    public synchronized List<VenomPlugin> plugins() {
        return List.copyOf(plugins);
    }

    public synchronized List<VenomPlugin> enabledPlugins() {
        return plugins.stream().filter(plugin -> enabledByName.getOrDefault(plugin.name(), true)).toList();
    }

    public synchronized List<PluginStatus> statuses() {
        return plugins.stream()
                .map(plugin -> new PluginStatus(plugin.name(), plugin.description(), enabledByName.getOrDefault(plugin.name(), true)))
                .toList();
    }

    public synchronized void setEnabled(String pluginName, boolean enabled) {
        enabledByName.put(pluginName, enabled);
    }

    public RequestData applyRequestHooks(RequestData requestData) {
        RequestData current = requestData;
        for (VenomPlugin plugin : enabledPlugins()) {
            current = plugin.onRequest(current);
        }
        return current;
    }

    public void applyResponseHooks(HttpTransaction transaction) {
        for (VenomPlugin plugin : enabledPlugins()) {
            plugin.onResponse(transaction);
        }
    }

    public List<Finding> applyScannerHooks(HttpTransaction transaction) {
        List<Finding> findings = new ArrayList<>();
        for (VenomPlugin plugin : enabledPlugins()) {
            findings.addAll(plugin.scan(transaction));
        }
        return findings;
    }

    public Path getPluginDirectory() {
        return pluginDirectory;
    }
}
