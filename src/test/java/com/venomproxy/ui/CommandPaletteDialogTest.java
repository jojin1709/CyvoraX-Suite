package com.venomproxy.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandPaletteDialogTest {
    @Test
    void fuzzySearchMatchesAbbreviatedCommands() {
        int switchScore = CommandPaletteDialog.fuzzyScore("sw ws", "switch workspace client portal");
        int noMatch = CommandPaletteDialog.fuzzyScore("zzq", "switch workspace client portal");

        assertTrue(switchScore < Integer.MAX_VALUE);
        assertTrue(noMatch == Integer.MAX_VALUE);
        assertTrue(CommandPaletteDialog.fuzzyScore("exp rep", "export report bug bounty") < Integer.MAX_VALUE);
    }
}
