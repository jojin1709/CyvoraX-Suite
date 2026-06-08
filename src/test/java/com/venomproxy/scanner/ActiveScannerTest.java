package com.venomproxy.scanner;

import com.venomproxy.proxy.ScopeControl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveScannerTest {
    @Test
    void replaceFirstParameterFallsBackForUnparseableUrls() throws Exception {
        ActiveScanner scanner = new ActiveScanner(new ScopeControl());
        Method method = ActiveScanner.class.getDeclaredMethod("replaceFirstParameter", String.class, String.class);
        method.setAccessible(true);

        String mutated = (String) method.invoke(scanner, "https://example.test/search?q=bad space", "x y");

        assertTrue(mutated.contains("cyvorax=x+y"));
    }
}
