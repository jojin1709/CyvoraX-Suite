package com.venomproxy.plugins;

public class PluginStatus {
    private final String name;
    private final String description;
    private final String state;
    private final String error;
    private boolean enabled;

    public PluginStatus(String name, String description, boolean enabled) {
        this(name, description, enabled, "Loaded", "");
    }

    public PluginStatus(String name, String description, boolean enabled, String state, String error) {
        this.name = name;
        this.description = description;
        this.enabled = enabled;
        this.state = state;
        this.error = error;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getState() {
        return state;
    }

    public String getError() {
        return error;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
