package com.venomproxy.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalSecretProtectorTest {
    @Test
    void encryptsAndDecryptsLocalSecrets() {
        String secret = "secret-value-" + System.nanoTime();

        String encrypted = LocalSecretProtector.encrypt(secret, "unit-test");

        assertTrue(LocalSecretProtector.isProtected(encrypted));
        assertFalse(encrypted.contains(secret));
        assertEquals(secret, LocalSecretProtector.decrypt(encrypted, "unit-test"));
    }

    @Test
    void encryptedSecretsSurviveOsNameChanges() {
        String originalOs = System.getProperty("os.name");
        String encrypted = LocalSecretProtector.encrypt("token-value", "unit-test");
        try {
            System.setProperty("os.name", "Changed OS Name For Test");

            assertEquals("token-value", LocalSecretProtector.decrypt(encrypted, "unit-test"));
        } finally {
            if (originalOs == null) {
                System.clearProperty("os.name");
            } else {
                System.setProperty("os.name", originalOs);
            }
        }
    }
}
