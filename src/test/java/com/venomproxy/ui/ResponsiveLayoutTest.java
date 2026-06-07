package com.venomproxy.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponsiveLayoutTest {
    @Test
    void mapsCommonDesktopWidthsToUsefulColumns() {
        assertEquals(4, ResponsiveLayout.cardColumns(1366));
        assertEquals(5, ResponsiveLayout.cardColumns(1920));
        assertEquals(6, ResponsiveLayout.cardColumns(2560));
        assertEquals(3, ResponsiveLayout.panelColumns(1920));
        assertTrue(ResponsiveLayout.tileWidth(1366, 4, 10, 170) >= 170);
    }
}
