package dnd;

import dnd.map.Grid;
import dnd.map.HexGrid;
import dnd.map.SquareGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GridTest {

    @Test
    void squareCellAtAndCenterRoundTrip() {
        Grid g = new SquareGrid(10, 8, 50);
        for (int c = 0; c < 10; c++) {
            for (int r = 0; r < 8; r++) {
                double[] ctr = g.center(c, r);
                assertArrayEquals(new int[]{c, r}, g.cellAt(ctr[0], ctr[1]));
            }
        }
        assertNull(g.cellAt(-1, 10));
        assertNull(g.cellAt(500.1, 10));
        assertEquals(500, g.width());
        assertEquals(400, g.height());
    }

    @Test
    void squareDiagonalCountsAsOneStep() {
        Grid g = new SquareGrid(10, 10, 50);
        assertEquals(3, g.distance(0, 0, 3, 3));
        assertEquals(5, g.distance(1, 1, 6, 3));
    }

    @Test
    void hexCellAtAndCenterRoundTrip() {
        Grid g = new HexGrid(12, 9, 60);
        for (int c = 0; c < 12; c++) {
            for (int r = 0; r < 9; r++) {
                double[] ctr = g.center(c, r);
                assertArrayEquals(new int[]{c, r}, g.cellAt(ctr[0], ctr[1]), "cell " + c + "," + r);
                // slightly off-centre but still inside the hex
                assertArrayEquals(new int[]{c, r}, g.cellAt(ctr[0] + 10, ctr[1] + 5), "offset " + c + "," + r);
            }
        }
    }

    @Test
    void hexPolygonHasSixCornersAroundTheCenter() {
        Grid g = new HexGrid(5, 5, 60);
        double[] p = g.polygon(2, 3);
        double[] c = g.center(2, 3);
        assertEquals(12, p.length);
        double radius = 60 / Math.sqrt(3);
        for (int i = 0; i < 6; i++) {
            assertEquals(radius, Math.hypot(p[2 * i] - c[0], p[2 * i + 1] - c[1]), 1e-9);
        }
    }

    @Test
    void hexNeighboursAreOneStepAway() {
        Grid g = new HexGrid(10, 10, 60);
        // even row 4: neighbours of (4,4) are (3,4) (5,4) and the four diagonals
        int[][] even = {{3, 4}, {5, 4}, {3, 3}, {4, 3}, {3, 5}, {4, 5}};
        for (int[] n : even) assertEquals(1, g.distance(4, 4, n[0], n[1]), "even " + n[0] + "," + n[1]);
        // odd row 5: diagonal neighbours shift right
        int[][] odd = {{4, 5}, {6, 5}, {5, 4}, {6, 4}, {5, 6}, {6, 6}};
        for (int[] n : odd) assertEquals(1, g.distance(5, 5, n[0], n[1]), "odd " + n[0] + "," + n[1]);
        assertEquals(0, g.distance(2, 2, 2, 2));
        assertEquals(2, g.distance(2, 2, 4, 2));
    }

    @Test
    void outsideHexGridIsNull() {
        Grid g = new HexGrid(4, 4, 60);
        assertNull(g.cellAt(-100, -100));
        assertNull(g.cellAt(10000, 10000));
    }
}
