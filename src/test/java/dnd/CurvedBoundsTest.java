package dnd;

import dnd.model.BoardObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CurvedBoundsTest {
    @Test
    void aCurvedArrowsBoundsIncludeItsBulge() {
        BoardObject a = new BoardObject(BoardObject.Type.ARROW);
        a.x = 100; a.y = 200; a.w = 300; a.h = 0;
        assertEquals(200, a.top(), 1e-9);
        assertEquals(200, a.bottom(), 1e-9);
        a.bendTo(250, 120, 1); // pulled 80 up
        assertTrue(a.curved());
        assertTrue(a.top() <= 125, "the box reaches up to the curve: " + a.top());
        assertEquals(200, a.bottom(), 1e-6);
        assertEquals(100, a.left(), 1e-6);
        assertEquals(400, a.right(), 1e-6);
        a.bendTo(250, 199, 5);
        assertFalse(a.curved());
        assertEquals(200, a.top(), 1e-9, "straight again: back to the plain box");
    }
}
