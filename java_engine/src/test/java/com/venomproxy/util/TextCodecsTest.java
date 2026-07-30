package com.venomproxy.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextCodecsTest {
    @Test
    void encodesAndDecodesBase64() {
        String encoded = TextCodecs.apply("Base64 Encode", "cyvorax");
        assertEquals("cyvorax", TextCodecs.apply("Base64 Decode", encoded));
    }

    @Test
    void hashesSha256() {
        assertEquals("3a6eb0790f39ac87c94f3856b2dd2c5d110e6811602261a9a923d3bb23adc8b7",
                TextCodecs.apply("SHA256", "data"));
    }

    @Test
    void decodesJwtHeaderAndPayload() {
        String jwt = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJjeXZvcmF4In0.";
        String decoded = TextCodecs.apply("JWT Decode", jwt);
        assertTrue(decoded.contains("\"alg\":\"none\""));
        assertTrue(decoded.contains("\"sub\":\"cyvorax\""));
    }
}
