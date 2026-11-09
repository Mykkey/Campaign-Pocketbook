package dnd;

import dnd.battle.ConditionStyle;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConditionStyleTest {
    @Test
    void everyStandardConditionHasItsOwnColour() {
        assertEquals(16, ConditionStyle.names().size());
        assertEquals(16, new HashSet<>(ConditionStyle.COLORS.values()).size(), "colours must be distinct");
    }

    @Test
    void lookupIgnoresCaseAndFallsBackToGrey() {
        assertEquals(ConditionStyle.colorOf("Poisoned"), ConditionStyle.colorOf("  poisoned "));
        assertEquals(ConditionStyle.UNKNOWN, ConditionStyle.colorOf("Cursed by a hag"));
        assertEquals(ConditionStyle.UNKNOWN, ConditionStyle.colorOf(null));
    }

    @Test
    void colorsKeepTheOrderOfTheConditions() {
        List<String> cols = ConditionStyle.colorsOf(List.of("Stunned", "Poisoned", "Custom"));
        assertEquals(List.of(ConditionStyle.colorOf("Stunned"), ConditionStyle.colorOf("Poisoned"), ConditionStyle.UNKNOWN), cols);
        assertTrue(ConditionStyle.colorsOf(null).isEmpty());
    }

    @Test
    void ringSlicesAreEqualAndStartAtTheTop() {
        assertEquals(0, ConditionStyle.sliceStarts(0).length);
        assertArrayEquals(new double[]{90}, ConditionStyle.sliceStarts(1));
        assertArrayEquals(new double[]{90, 270}, ConditionStyle.sliceStarts(2));
        assertArrayEquals(new double[]{90, 210, 330}, ConditionStyle.sliceStarts(3), 1e-9);
    }
}
