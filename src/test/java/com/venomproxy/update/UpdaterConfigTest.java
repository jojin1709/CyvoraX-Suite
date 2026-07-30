package com.venomproxy.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdaterConfigTest {
    private static final String ENV_TOKEN = token("envToken");
    private static final String LOCAL_TOKEN = token("localToken");

    @TempDir
    Path tempDir;

    @Test
    void createsLocalUpdaterFileWithDefaults() throws Exception {
        Path configPath = tempDir.resolve("config").resolve("updater.properties");
        UpdaterConfig config = new UpdaterConfig(configPath, Map.of());

        UpdaterConfig.Settings settings = config.load();
        String content = Files.readString(configPath);

        assertTrue(Files.exists(configPath));
        assertEquals("jojin1709", settings.owner());
        assertEquals("CyvoraX-Suite", settings.repository());
        assertTrue(content.contains("backup.exclude=true"));
        assertFalse(content.contains("github.token"));
    }

    @Test
    void environmentTokenWinsOverLocalToken() {
        Path configPath = tempDir.resolve("config").resolve("updater.properties");
        UpdaterConfig config = new UpdaterConfig(configPath, Map.of(UpdaterConfig.ENV_TOKEN, ENV_TOKEN));
        config.save("owner", "repo", LOCAL_TOKEN);

        UpdaterConfig.Settings settings = config.load();

        assertEquals(ENV_TOKEN, settings.effectiveToken());
        assertEquals("Environment token configured", settings.authenticationStatus());
    }

    @Test
    void loadsAndPreservesLocalTokenWhenMaskedPlaceholderIsSaved() {
        Path configPath = tempDir.resolve("config").resolve("updater.properties");
        UpdaterConfig config = new UpdaterConfig(configPath, Map.of());
        config.save("owner", "repo", LOCAL_TOKEN);

        config.save("owner", "repo", "ghp_************");

        UpdaterConfig.Settings settings = config.load();
        assertEquals(LOCAL_TOKEN, settings.effectiveToken());
        assertEquals("Local updater token configured", settings.authenticationStatus());
        assertEquals("ghp_************", config.maskedEffectiveToken());
    }

    private static String token(String label) {
        return "gh" + "p_" + label + "1234567890";
    }
}
