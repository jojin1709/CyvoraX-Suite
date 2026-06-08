package com.venomproxy.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

public final class LocalSecretProtector {
    private static final String PREFIX = "enc:v1:";
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private LocalSecretProtector() {
    }

    public static String encrypt(String plainText, String purpose) {
        if (plainText == null || plainText.isBlank()) {
            return "";
        }
        try {
            byte[] salt = randomBytes(SALT_BYTES);
            byte[] iv = randomBytes(IV_BYTES);
            SecretKeySpec key = key(salt, purpose);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            return PREFIX + encoder.encodeToString(salt) + ":"
                    + encoder.encodeToString(iv) + ":"
                    + encoder.encodeToString(encrypted);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not encrypt local secret", ex);
        }
    }

    public static String decrypt(String protectedValue, String purpose) {
        if (protectedValue == null || protectedValue.isBlank()) {
            return "";
        }
        if (!protectedValue.startsWith(PREFIX)) {
            return protectedValue;
        }
        try {
            String[] parts = protectedValue.substring(PREFIX.length()).split(":", -1);
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid protected secret format");
            }
            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] salt = decoder.decode(parts[0]);
            byte[] iv = decoder.decode(parts[1]);
            byte[] encrypted = decoder.decode(parts[2]);
            SecretKeySpec key = key(salt, purpose);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not decrypt local secret", ex);
        }
    }

    public static boolean isProtected(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    private static SecretKeySpec key(byte[] salt, String purpose) throws Exception {
        String material = System.getProperty("user.name", "user") + "|"
                + System.getProperty("user.home", "") + "|"
                + (purpose == null ? "CyvoraX" : purpose);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(material.toCharArray(), salt, ITERATIONS, KEY_BITS);
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
