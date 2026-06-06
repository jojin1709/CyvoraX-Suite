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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

public class PluginLoader {
    private final Path pluginDirectory;
    private final List<VenomPlugin> plugins = new ArrayList<>();
    private final List<PluginStatus> lastStatuses = new ArrayList<>();
    private final Map<String, Boolean> enabledByName = new HashMap<>();
    private Database database;

    public PluginLoader(Path pluginDirectory) {
        this.pluginDirectory = pluginDirectory;
    }

    public synchronized List<VenomPlugin> load(Database database, ScopeControl scopeControl) {
        this.database = database;
        plugins.clear();
        lastStatuses.clear();
        try {
            Files.createDirectories(pluginDirectory);
            List<URL> urls = new ArrayList<>();
            try (var stream = Files.list(pluginDirectory)) {
                stream.filter(path -> path.toString().endsWith(".jar")).forEach(path -> {
                    try {
                        urls.add(path.toUri().toURL());
                    } catch (Exception ex) {
                        lastStatuses.add(new PluginStatus(path.getFileName().toString(), "Could not read plugin jar.",
                                false, "Error", ex.getMessage()));
                    }
                });
            }
            URLClassLoader classLoader = new URLClassLoader(urls.toArray(URL[]::new), getClass().getClassLoader());
            ServiceLoader<VenomPlugin> serviceLoader = ServiceLoader.load(VenomPlugin.class, classLoader);
            VenomPluginContext context = new VenomPluginContext(database, scopeControl, pluginDirectory);
            Iterator<VenomPlugin> iterator = serviceLoader.iterator();
            while (true) {
                VenomPlugin plugin;
                try {
                    if (!iterator.hasNext()) {
                        break;
                    }
                    plugin = iterator.next();
                    plugin.onLoad(context);
                    plugins.add(plugin);
                    boolean enabled = Boolean.parseBoolean(database.getSetting(pluginSettingKey(plugin.name()), "true"));
                    enabledByName.put(plugin.name(), enabled);
                    lastStatuses.add(new PluginStatus(plugin.name(), plugin.description(), enabled, "Loaded", ""));
                } catch (ServiceConfigurationError | RuntimeException ex) {
                    lastStatuses.add(new PluginStatus("Plugin load error", "A plugin failed during discovery or startup.",
                            false, "Error", ex.getMessage()));
                    break;
                }
            }
        } catch (Exception ex) {
            lastStatuses.add(new PluginStatus("Plugin system", "Could not scan plugin directory.", false, "Error", ex.getMessage()));
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
        return List.copyOf(lastStatuses);
    }

    public synchronized void setEnabled(String pluginName, boolean enabled) {
        enabledByName.put(pluginName, enabled);
        if (database != null) {
            database.setSetting(pluginSettingKey(pluginName), String.valueOf(enabled));
        }
        for (PluginStatus status : lastStatuses) {
            if (status.getName().equals(pluginName)) {
                status.setEnabled(enabled);
            }
        }
    }

    public RequestData applyRequestHooks(RequestData requestData) {
        RequestData current = requestData;
        for (VenomPlugin plugin : enabledPlugins()) {
            try {
                current = plugin.onRequest(current);
            } catch (RuntimeException ex) {
                recordHookError(plugin, "Request hook", ex);
            }
        }
        return current;
    }

    public void applyResponseHooks(HttpTransaction transaction) {
        for (VenomPlugin plugin : enabledPlugins()) {
            try {
                plugin.onResponse(transaction);
            } catch (RuntimeException ex) {
                recordHookError(plugin, "Response hook", ex);
            }
        }
    }

    public List<Finding> applyScannerHooks(HttpTransaction transaction) {
        List<Finding> findings = new ArrayList<>();
        for (VenomPlugin plugin : enabledPlugins()) {
            try {
                findings.addAll(plugin.scan(transaction));
            } catch (RuntimeException ex) {
                recordHookError(plugin, "Scanner hook", ex);
            }
        }
        return findings;
    }

    public Path getPluginDirectory() {
        return pluginDirectory;
    }

    private String pluginSettingKey(String pluginName) {
        return "plugin." + pluginName.replaceAll("[^A-Za-z0-9_.-]", "_") + ".enabled";
    }

    private synchronized void recordHookError(VenomPlugin plugin, String state, RuntimeException ex) {
        lastStatuses.add(new PluginStatus(plugin.name(), plugin.description(),
                enabledByName.getOrDefault(plugin.name(), true), state + " error", ex.getMessage()));
    }
}
