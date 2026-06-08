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
}
