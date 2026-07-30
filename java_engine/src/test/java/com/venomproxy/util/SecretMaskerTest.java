package com.venomproxy.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretMaskerTest {
    @Test
    void masksGitHubTokensAndAuthorizationHeaders() {
        String token = classicToken("maskToken");
        String text = "Authorization: Bearer " + token + "\n"
                + "github.token=" + token + "\n"
                + "raw=" + token + "\n"
                + "fine=visible";

        String masked = SecretMasker.maskSecrets(text);

        assertFalse(masked.contains(token));
        assertTrue(masked.contains("Authorization: Bearer ************"));
        assertTrue(masked.contains("github.token=************"));
        assertTrue(masked.contains("raw=ghp_************"));
        assertTrue(masked.contains("fine=visible"));
    }

    @Test
    void masksFineGrainedGitHubTokens() {
        String token = fineGrainedToken("maskToken");

        String masked = SecretMasker.maskSecrets("token=" + token);

        assertFalse(masked.contains(token));
        assertTrue(masked.contains("github_pat_************"));
    }

    @Test
    void masksAiProviderTokensAndEnvironmentLines() {
        String groq = "g" + "sk_maskToken1234567890";
        String cerebras = "c" + "sk-maskToken1234567890";
        String openRouter = "sk-or" + "-v1-maskToken1234567890";
        String mistral = "MISTRAL" + "_API_KEY=plainMistralKey1234567890";

        String masked = SecretMasker.maskSecrets("raw=" + groq + "\n"
                + "raw2=" + cerebras + "\n"
                + "raw3=" + openRouter + "\n"
                + mistral);

        assertFalse(masked.contains(groq));
        assertFalse(masked.contains(cerebras));
        assertFalse(masked.contains(openRouter));
        assertFalse(masked.contains("plainMistralKey1234567890"));
        assertTrue(masked.contains("raw=gsk_************"));
        assertTrue(masked.contains("raw2=csk-************"));
        assertTrue(masked.contains("raw3=sk-or-v1-************"));
        assertTrue(masked.contains("MISTRAL" + "_API_KEY=************"));
    }

    private static String classicToken(String label) {
        return "gh" + "p_" + label + "1234567890";
    }

    private static String fineGrainedToken(String label) {
        return "github" + "_pat_" + label + "1234567890";
    }
}
