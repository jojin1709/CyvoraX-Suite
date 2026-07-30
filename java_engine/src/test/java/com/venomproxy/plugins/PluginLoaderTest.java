package com.venomproxy.plugins;

import com.venomproxy.proxy.ScopeControl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.net.URLClassLoader;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotSame;

class PluginLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void reloadReplacesActiveClassLoader() throws Exception {
        PluginLoader loader = new PluginLoader(tempDir);
        loader.load(null, new ScopeControl());
        URLClassLoader first = activeClassLoader(loader);

        loader.load(null, new ScopeControl());

        assertNotSame(first, activeClassLoader(loader));
    }

    private URLClassLoader activeClassLoader(PluginLoader loader) throws Exception {
        Field field = PluginLoader.class.getDeclaredField("activeClassLoader");
        field.setAccessible(true);
        return (URLClassLoader) field.get(loader);
    }
}
