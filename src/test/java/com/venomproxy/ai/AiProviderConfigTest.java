package com.venomproxy.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiProviderConfigTest {
    @TempDir
    Path tempDir;

    @Test
    void createsEncryptedLocalConfigAndLoadsToken() throws Exception {
        Path configPath = tempDir.resolve("config").resolve("ai-providers.properties");
        AiProviderConfig config = new AiProviderConfig(configPath, Map.of());
        String token = token("groqLocal");

        config.saveProvider(AiProvider.GROQ, "model-a", token);
        String content = Files.readString(configPath);
        AiProviderSettings.ProviderSettings settings = config.load().providers().get(AiProvider.GROQ);

        assertEquals(token, settings.effectiveToken());
        assertEquals("model-a", settings.model());
        assertTrue(content.contains("backup.exclude=true"));
        assertTrue(content.contains("enc\\:v1") || content.contains("enc:v1"));
        assertFalse(content.contains(token));
    }

    @Test
    void environmentTokenOverridesEncryptedLocalToken() {
        Path configPath = tempDir.resolve("config").resolve("ai-providers.properties");
        String environmentToken = token("groqEnv");
        AiProviderConfig config = new AiProviderConfig(configPath,
                Map.of(AiProvider.GROQ.environmentVariable(), environmentToken));

        config.saveProvider(AiProvider.GROQ, "model-a", token("groqLocal"));

        AiProviderSettings.ProviderSettings settings = config.load().providers().get(AiProvider.GROQ);
        assertEquals(environmentToken, settings.effectiveToken());
        assertEquals("Environment key configured", settings.authenticationStatus());
    }

    @Test
    void maskedTokenInputPreservesExistingEncryptedToken() {
        Path configPath = tempDir.resolve("config").resolve("ai-providers.properties");
        AiProviderConfig config = new AiProviderConfig(configPath, Map.of());
        String token = openRouterToken("openRouterLocal");
        config.saveProvider(AiProvider.OPENROUTER, "model-a", token);

        config.saveProvider(AiProvider.OPENROUTER, "model-b", "sk-or-v1-************");

        AiProviderSettings.ProviderSettings settings = config.load().providers().get(AiProvider.OPENROUTER);
        assertEquals(token, settings.effectiveToken());
        assertEquals("model-b", settings.model());
        assertEquals("sk-or-v1-************", config.maskedToken(AiProvider.OPENROUTER));
    }

    private static String token(String label) {
        return "g" + "sk_" + label + "1234567890";
    }

    private static String openRouterToken(String label) {
        return "sk-or" + "-v1-" + label + "1234567890";
    }
}
