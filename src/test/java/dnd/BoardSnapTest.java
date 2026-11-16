package dnd;

import dnd.map.BoardSnap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoardSnapTest {
    @Test
    void anEdgeSnapsToAnotherObjectsEdgeOrMiddleAndShowsAGuide() {
        double[] xs = {100, 150, 200};
        double[] r = BoardSnap.axis(103, 160, xs, 0, 8); // left edge 3 off 100; middle 131.5; right 160
        assertEquals(-3, r[0], 1e-9);
        assertEquals(100, r[1], 1e-9);
        double[] mid = BoardSnap.axis(130, 172, xs, 0, 8); // the middle (151) is 1 off 150
        assertEquals(-1, mid[0], 1e-9);
        assertEquals(150, mid[1], 1e-9);
        double[] right = BoardSnap.axis(130, 197, xs, 0, 8); // right edge 3 off 200
        assertEquals(3, right[0], 1e-9);
        assertEquals(200, right[1], 1e-9);
    }

    @Test
    void theClosestSnapWinsAndNothingFarAwayIsTouched() {
        double[] xs = {100, 106};
        double[] r = BoardSnap.axis(104, 150, xs, 0, 8);
        assertEquals(2, r[0], 1e-9, "106 is 2 away, 100 is 4 away");
        double[] none = BoardSnap.axis(300, 350, xs, 0, 8);
        assertEquals(0, none[0], 1e-9);
        assertTrue(Double.isNaN(none[1]));
        assertEquals(0, BoardSnap.axis(0, 10, null, 0, 8)[0], 1e-9, "no targets and no grid: no snap");
    }

    @Test
    void theLeftOrRightEdgeCanSnapToTheDotGridWithoutAGuideLine() {
        double[] left = BoardSnap.axis(83, 140, null, 40, 8); // left 83 -> 80
        assertEquals(-3, left[0], 1e-9);
        assertTrue(Double.isNaN(left[1]), "a grid snap draws no guide");
        double[] right = BoardSnap.axis(30, 122, null, 40, 8); // right 122 -> 120 (left 30 is 10 from 40: too far)
        assertEquals(-2, right[0], 1e-9);
        assertEquals(0, BoardSnap.axis(20, 60, null, 40, 5)[0], 1e-9, "20 is 20 from the nearest grid line... right edge 60 is 20 away: no snap");
    }

    @Test
    void anObjectBeatsTheGridWhenItIsCloserAndTheGridWinsWhenItIs() {
        double[] xs = {102};
        double[] a = BoardSnap.axis(101, 170, xs, 40, 8); // object: 1 away; grid left edge 101 -> 120? 101 -> 120 is 19: too far; 80 is 21: too far
        assertEquals(1, a[0], 1e-9);
        assertEquals(102, a[1], 1e-9);
        double[] b = BoardSnap.axis(118, 170, new double[]{125}, 40, 8); // object 7 away (left to 125), grid 2 away (118 -> 120)
        assertEquals(2, b[0], 1e-9);
        assertTrue(Double.isNaN(b[1]));
    }

    @Test
    void aBoxSnapsInBothDirectionsAndCanLeaveObjectsOut() {
        double[] xs = {100}, ys = {50};
        BoardSnap.Result r = BoardSnap.box(102, 160, 47, 90, xs, ys, 0, true, 8);
        assertEquals(-2, r.dx(), 1e-9);
        assertEquals(3, r.dy(), 1e-9);
        assertEquals(100, r.guideX(), 1e-9);
        assertEquals(50, r.guideY(), 1e-9);
        assertTrue(r.any());
        BoardSnap.Result off = BoardSnap.box(102, 160, 47, 90, xs, ys, 0, false, 8);
        assertFalse(off.any(), "objects switched off and no grid");
        assertEquals(BoardSnap.Result.NONE, off.dx() == 0 ? BoardSnap.Result.NONE : off);
    }

    @Test
    void aPointSnapsLikeASmallBox() {
        BoardSnap.Result r = BoardSnap.point(203, 77, new double[]{200}, new double[]{80}, 0, true, 8);
        assertEquals(-3, r.dx(), 1e-9);
        assertEquals(3, r.dy(), 1e-9);
        BoardSnap.Result g = BoardSnap.point(41, 79, null, null, 40, false, 8);
        assertEquals(-1, g.dx(), 1e-9);
        assertEquals(1, g.dy(), 1e-9);
        assertTrue(Double.isNaN(g.guideX()));
    }
}
