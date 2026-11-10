package dnd;

import dnd.battle.ConditionStyle;
import dnd.ui.AppSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AccessibilityTest {
    @BeforeEach
    void freshSettings(@TempDir Path tmp) {
        AppSettings.useFile(tmp.resolve("settings.properties"));
    }

    @AfterEach
    void back() {
        AppSettings.useFile(null);
    }

    @Test
    void theColourBlindSafePaletteCoversEveryConditionWithDifferentColours() {
        assertEquals(ConditionStyle.COLORS.keySet(), ConditionStyle.SAFE_COLORS.keySet());
        Set<String> seen = new HashSet<>();
        for (String c : ConditionStyle.SAFE_COLORS.values()) assertTrue(seen.add(c.toLowerCase()), "two conditions share " + c);
        // and it is different from the normal one
        int different = 0;
        for (String name : ConditionStyle.names()) if (!ConditionStyle.COLORS.get(name).equalsIgnoreCase(ConditionStyle.SAFE_COLORS.get(name))) different++;
        assertTrue(different >= 12, "different colours: " + different);
    }

    @Test
    void everyConditionHasItsOwnTwoLetterCode() {
        Set<String> codes = new HashSet<>();
        for (String name : ConditionStyle.names()) {
            String code = ConditionStyle.codeOf(name);
            assertEquals(2, code.length(), name + " -> " + code);
            assertTrue(codes.add(code), "the code " + code + " is used twice");
        }
        assertEquals("Bl", ConditionStyle.codeOf("blinded"), "ignores case");
        assertEquals("He", ConditionStyle.codeOf("Hexed by owl"), "a custom condition gets its first two letters");
        assertEquals("", ConditionStyle.codeOf(" "));
        assertEquals(List.of("Pr", "Po"), ConditionStyle.codesOf(List.of("Prone", "Poisoned")));
    }

    @Test
    void theSettingSwitchesWhichColoursAreUsed() {
        String normal = ConditionStyle.colorOf("Poisoned");
        assertEquals("#22c55e", normal);
        assertFalse(ConditionStyle.safeColours());
        AppSettings.setBoolean("ui.colourBlind", true);
        assertTrue(ConditionStyle.safeColours());
        assertEquals("#009E73", ConditionStyle.colorOf("Poisoned"));
        assertEquals("#009E73", ConditionStyle.colorOf("poisoned "));
        assertEquals(ConditionStyle.UNKNOWN, ConditionStyle.colorOf("Hexed"));
        assertEquals(List.of("#009E73", "#FFFFFF"), ConditionStyle.colorsOf(List.of("Poisoned", "Prone")));
        AppSettings.remove("ui.colourBlind");
        assertEquals(normal, ConditionStyle.colorOf("Poisoned"));
    }
}
