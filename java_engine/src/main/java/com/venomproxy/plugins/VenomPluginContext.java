package com.venomproxy.plugins;

import com.venomproxy.db.Database;
import com.venomproxy.proxy.ScopeControl;

import java.nio.file.Path;

public record VenomPluginContext(Database database, ScopeControl scopeControl, Path pluginDirectory) {
}
