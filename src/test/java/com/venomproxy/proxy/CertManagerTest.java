package com.venomproxy.proxy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CertManagerTest {
    @TempDir
    Path tempDir;

    @Test
    @SuppressWarnings("unchecked")
    void serverContextCacheIsBoundedTo512Entries() throws Exception {
        CertManager certManager = new CertManager(tempDir);
        Field field = CertManager.class.getDeclaredField("serverContexts");
        field.setAccessible(true);
        Map<String, Object> cache = (Map<String, Object>) field.get(certManager);

        for (int i = 0; i < 600; i++) {
            cache.put("host-" + i + ".example.test", null);
        }

        assertEquals(512, cache.size());
    }
}
