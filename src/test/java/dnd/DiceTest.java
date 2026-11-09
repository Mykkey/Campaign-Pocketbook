package dnd;

import dnd.rules.Dice;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class DiceTest {

    @Test
    void parsesCommonNotation() {
        assertEquals(5, Dice.parse("1d6+2").orElseThrow().average());     // 3.5 + 2 = 5.5 -> 5
        assertEquals(7, Dice.parse("2d4+2").orElseThrow().average());     // 5 + 2
        assertEquals(9, Dice.parse("1d12+3").orElseThrow().average());    // 6.5 + 3 = 9.5 -> 9
        assertEquals(4, Dice.parse("D8").orElseThrow().average());        // 4.5 -> 4
        assertEquals(7, Dice.parse("7").orElseThrow().average());
        assertEquals(2, Dice.parse(" 1d6 - 1 ").orElseThrow().average()); // 3.5 - 1 = 2.5 -> 2
    }

    @Test
    void handlesMultipleTermsAndSigns() {
        Dice d = Dice.parse("1d8+2+1d6").orElseThrow();
        assertEquals(10, d.average());       // 4.5 + 2 + 3.5
        assertEquals(4, d.min());
        assertEquals(16, d.max());
        Dice minus = Dice.parse("2d6-3").orElseThrow();
        assertEquals(-1, minus.min());
        assertEquals(9, minus.max());
    }

    @Test
    void rejectsNonsense() {
        for (String s : new String[]{"", "  ", "abc", "d", "2d", "d0", "0d6", "1d6+", "+", "1d6++2", "1d6 plus 2", "1.5d6", null}) {
            assertFalse(Dice.isValid(s), "should be invalid: " + s);
        }
        assertTrue(Dice.isValid("1d20"));
        assertTrue(Dice.isValid("-2"));
    }

    @Test
    void rollsStayInRangeAndAreDeterministicWithASeed() {
        Dice d = Dice.parse("2d6+3").orElseThrow();
        Random r = new Random(5);
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int i = 0; i < 2000; i++) {
            int v = d.roll(r);
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
            assertTrue(v >= d.min() && v <= d.max());
        }
        assertEquals(5, lo);
        assertEquals(15, hi);
        assertEquals(d.roll(new Random(9)), d.roll(new Random(9)));
    }
}
