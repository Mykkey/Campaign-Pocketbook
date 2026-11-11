package dnd;

import dnd.map.BattleResize;
import dnd.map.BattleResize.Margins;
import dnd.map.Grid;
import dnd.model.BattleMap;
import dnd.model.MapLight;
import dnd.model.MapObject;
import dnd.model.MapPin;
import dnd.model.MapToken;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BattleResizeTest {
    private static BattleMap square() {
        BattleMap m = new BattleMap();
        m.cols = 10;
        m.rows = 8;
        m.cellSize = 50;
        m.terrain.put(BattleMap.key(0, 0), "grass");
        m.terrain.put(BattleMap.key(9, 7), "water");
        m.revealed.add(BattleMap.key(2, 3));
        MapToken t = new MapToken();
        t.col = 4;
        t.row = 4;
        m.tokens.add(t);
        MapObject o = new MapObject();
        o.x = 125;
        o.y = 75;
        m.objects.add(o);
        MapPin p = new MapPin(25, 25, "trap");
        m.pins.add(p);
        return m;
    }

    @Test
    void addingRowsAndColumnsKeepsEverythingInItsPlace() {
        BattleMap m = square();
        assertTrue(BattleResize.resizeCanvas(m, new Margins(2, 3, 1, 4)));
        assertEquals(15, m.cols);
        assertEquals(13, m.rows);
        assertEquals("grass", m.terrain.get("2,1"), "the old corner moved right and down");
        assertEquals("water", m.terrain.get("11,8"));
        assertTrue(m.revealed.contains("4,4"));
        assertEquals(6, m.tokens.get(0).col);
        assertEquals(5, m.tokens.get(0).row);
        assertEquals(225, m.objects.get(0).x, 1e-9);
        assertEquals(125, m.objects.get(0).y, 1e-9);
        assertEquals(125, m.pins.get(0).x, 1e-9);
        assertEquals(75, m.pins.get(0).y, 1e-9);
    }

    @Test
    void croppingRemovesWhatFallsOutsideButNeverTokens() {
        BattleMap m = square();
        assertTrue(BattleResize.resizeCanvas(m, new Margins(-1, -1, -1, -1)));
        assertEquals(8, m.cols);
        assertEquals(6, m.rows);
        assertNull(m.terrain.get("0,0"));
        assertTrue(m.terrain.isEmpty(), "both painted corners were cut off");
        assertTrue(m.revealed.contains("1,2"));
        assertEquals(3, m.tokens.get(0).col);
        assertTrue(m.pins.isEmpty(), "the pin in the cut corner went");
        assertEquals(1, m.objects.size());

        BattleMap n = square();
        Margins cutToken = new Margins(-5, 0, 0, 0); // the token is in column 4
        assertEquals(1, BattleResize.tokensOutside(n, cutToken));
        assertNotNull(BattleResize.problem(n, cutToken));
        assertFalse(BattleResize.resizeCanvas(n, cutToken));
        assertEquals(10, n.cols, "a refused change changes nothing");
        assertEquals(4, n.tokens.get(0).col);
    }

    @Test
    void aBigTokenMustFitWholly() {
        BattleMap m = square();
        m.tokens.get(0).size = 3; // columns 4-6, rows 4-6
        assertEquals(1, BattleResize.tokensOutside(m, new Margins(0, -4, 0, 0)), "only 6 columns remain; the token needs up to column 6");
        assertEquals(0, BattleResize.tokensOutside(m, new Margins(0, -3, 0, 0)));
    }

    @Test
    void sizeLimitsAreEnforced() {
        BattleMap m = square();
        assertNotNull(BattleResize.problem(m, new Margins(-8, 0, 0, 0)), "2 columns is too few");
        assertNotNull(BattleResize.problem(m, new Margins(200, 0, 0, 0)), "210 columns is too many");
        assertNull(BattleResize.problem(m, Margins.NONE));
        assertTrue(BattleResize.resizeCanvas(m, Margins.NONE));
        assertEquals(10, m.cols);
    }

    @Test
    void hexMapsOnlyGrowAtTheTopTwoRowsAtATime() {
        BattleMap m = square();
        m.gridType = BattleMap.GridType.HEX;
        assertNotNull(BattleResize.problem(m, new Margins(0, 0, 1, 0)));
        assertNull(BattleResize.problem(m, new Margins(0, 0, 2, 0)));
        assertNull(BattleResize.problem(m, new Margins(0, 0, 0, 1)), "any number of rows at the bottom");

        Grid before = Grid.of(m);
        double[] was = before.center(3, 3);
        MapObject o = m.objects.get(0);
        o.x = was[0];
        o.y = was[1];
        assertTrue(BattleResize.resizeCanvas(m, new Margins(1, 0, 2, 0)));
        double[] now = Grid.of(m).center(4, 5);
        assertEquals(now[0], o.x, 1e-9, "the piece stays on the same hex");
        assertEquals(now[1], o.y, 1e-9);
    }

    @Test
    void changingTheCellSizeScalesPiecesPinsAndLights() {
        BattleMap m = square();
        MapLight l = new MapLight("Torch", 20, 40);
        l.x = 100;
        l.y = 50;
        m.lights.add(l);
        m.objects.get(0).w = 100;
        BattleResize.setCellSize(m, 100);
        assertEquals(100, m.cellSize, 1e-9);
        assertEquals(250, m.objects.get(0).x, 1e-9);
        assertEquals(200, m.objects.get(0).w, 1e-9);
        assertEquals(50, m.pins.get(0).x, 1e-9);
        assertEquals(200, l.x, 1e-9);
        assertEquals(4, m.tokens.get(0).col, "tokens are in cells, so they do not move");
        BattleResize.setCellSize(m, 5000);
        assertEquals(BattleResize.MAX_CELL_SIZE, m.cellSize, 1e-9);
    }
}
