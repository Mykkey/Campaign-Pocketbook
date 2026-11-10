package dnd;

import dnd.export.PrintMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrintMathTest {
    @Test
    void contentIsScaledToThePageWidthButNeverBlownUpTooFar() {
        assertEquals(1.0, PrintMath.scaleFor(500, 500, 1.6), 1e-9);
        assertEquals(0.5, PrintMath.scaleFor(1000, 500, 1.6), 1e-9, "wide content shrinks");
        assertEquals(1.6, PrintMath.scaleFor(300, 500, 1.6), 1e-9, "a small card is enlarged, but only so far");
        assertEquals(1.0, PrintMath.scaleFor(0, 500, 1.6), 1e-9, "nothing to scale");
    }

    @Test
    void tallContentNeedsAsManyPagesAsItTakes() {
        assertEquals(1, PrintMath.pageCount(0, 700));
        assertEquals(1, PrintMath.pageCount(700, 700), "exactly one page");
        assertEquals(2, PrintMath.pageCount(701, 700));
        assertEquals(3, PrintMath.pageCount(1500, 700));
        assertEquals(1, PrintMath.pageCount(100, 0), "a broken page size still gives one page");
        assertEquals(500, PrintMath.pageSpan(800, 1.6), 1e-9, "at 1.6 times, 500 units of content fill an 800 high page");
    }
}
