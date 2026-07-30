package com.venomproxy.proxy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopeControlTest {
    @Test
    void wildcardRulesStillMatchAfterPatternCachingAndCacheClearsOnRuleUpdate() {
        ScopeControl scope = new ScopeControl();
        scope.setIncludesFromText("*.example.test");

        assertTrue(scope.isInScope("https://api.example.test/path"));

        scope.setIncludesFromText("*.other.test");

        assertFalse(scope.isInScope("https://api.example.test/path"));
        assertTrue(scope.isInScope("https://api.other.test/path"));
    }
}
