package com.venomproxy.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticVersionTest {
    @Test
    void comparesVersionsAndPrereleases() {
        assertTrue(SemanticVersion.parse("v1.2.0").isNewerThan(SemanticVersion.parse("1.1.9")));
        assertTrue(SemanticVersion.parse("1.2.0").isNewerThan(SemanticVersion.parse("1.2.0-test")));
        assertFalse(SemanticVersion.parse("1.3.0").isNewerThan(SemanticVersion.parse("1.3.0")));
    }
}
