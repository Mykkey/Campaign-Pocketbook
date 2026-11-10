package dnd;

import dnd.map.world.WorldGrid;
import dnd.map.world.WorldTravel;
import dnd.model.WorldGridKind;
import dnd.model.WorldMap;
import dnd.model.WorldStyle;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorldGridTest {
    private static WorldMap map(WorldGridKind kind) {
        WorldMap m = new WorldMap("W", 100, 80, 1000, WorldStyle.FANTASY_COLOUR); // 10 miles a cell
        m.gridKind = kind;
        m.gridMiles = 50; // 5 cells
        return m;
    }

    @Test
    void squareCellsAreFoundAndNamedLikeASpreadsheet() {
        WorldMap m = map(WorldGridKind.SQUARE);
        assertEquals(5, WorldGrid.cellSize(m), 1e-9);
        assertArrayEquals(new int[]{0, 0}, WorldGrid.cellAt(m, 2, 2));
        assertArrayEquals(new int[]{3, 1}, WorldGrid.cellAt(m, 17, 6));
        assertArrayEquals(new double[]{17.5, 7.5}, WorldGrid.centreOf(m, 3, 1), 1e-9);
        assertEquals("A1", WorldGrid.name(0, 0));
        assertEquals("D2", WorldGrid.name(3, 1));
        assertEquals("Z1", WorldGrid.name(25, 0));
        assertEquals("AA1", WorldGrid.name(26, 0));
        assertEquals("AB12", WorldGrid.name(27, 11));
        assertNull(WorldGrid.cellAt(map(WorldGridKind.NONE), 5, 5));
    }

    @Test
    void everyPointOfAHexBelongsToTheHexWhoseCentreIsNearest() {
        WorldMap m = map(WorldGridKind.HEX);
        for (double x = 1; x < 90; x += 3.7) {
            for (double y = 1; y < 70; y += 3.1) {
                int[] c = WorldGrid.cellAt(m, x, y);
                double[] mid = WorldGrid.centreOf(m, c[0], c[1]);
                double best = Math.hypot(x - mid[0], y - mid[1]);
                for (int dc = -2; dc <= 2; dc++) {
                    for (int dr = -2; dr <= 2; dr++) {
                        double[] o = WorldGrid.centreOf(m, c[0] + dc, c[1] + dr);
                        assertTrue(best <= Math.hypot(x - o[0], y - o[1]) + 1e-9, "(" + x + "," + y + ") is nearest to its own hex");
                    }
                }
            }
        }
        assertArrayEquals(new double[]{0, 0}, WorldGrid.centreOf(m, 0, 0), 1e-9);
        assertArrayEquals(new int[]{0, 0}, WorldGrid.cellAt(m, 0.1, 0.1));
    }

    @Test
    void travelTimesAreWorkedOutFromTheDistance() {
        assertEquals(100, WorldTravel.length(List.of(0.0, 0.0, 60.0, 80.0)), 1e-9);
        assertEquals(10, WorldTravel.length(List.of(0.0, 0.0, 10.0, 0.0, 10.0, 0.0)), 1e-9);
        assertEquals("5 days", WorldTravel.duration(120, 24));
        assertEquals("2 days 4 hr", WorldTravel.duration(60, 24));
        assertEquals("3 hr", WorldTravel.duration(9, 24));
        assertEquals("under an hour", WorldTravel.duration(0.5, 24));
        assertEquals("-", WorldTravel.duration(0, 24));
        assertEquals(24, WorldTravel.defaultPaces().get("On foot"), 1e-9);
        assertEquals("1,240 mi", WorldTravel.distance(1240));
        assertEquals("0.4 mi", WorldTravel.distance(0.4));
        assertEquals(50, WorldTravel.niceLength(63), 1e-9);
        assertEquals(20, WorldTravel.niceLength(39), 1e-9);
        assertEquals(100, WorldTravel.niceLength(100), 1e-9);
        assertEquals(2, WorldTravel.niceLength(2.9), 1e-9);
    }

    @Test
    void gridScaleAndSpeedsAreSaved(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        WorldMap m = store.createWorldMap(store.project.root, new WorldMap("W", 64, 48, 640, WorldStyle.CLASSIC_INK));
        m.gridKind = WorldGridKind.HEX;
        m.gridMiles = 6;
        m.gridCoords = true;
        m.showScale = false;
        m.showLegend = true;
        m.legendX = 5.0;
        m.legendY = 7.0;
        m.paces.put("On foot", 20.0);
        store.markWorldMapDirty(m, true);
        store.flush();
        WorldMap back = ProjectStore.open(tmp, "Camp").worldMap(m.id);
        assertEquals(WorldGridKind.HEX, back.gridKind);
        assertEquals(6, back.gridMiles, 1e-9);
        assertTrue(back.gridCoords);
        assertFalse(back.showScale);
        assertTrue(back.showLegend);
        assertEquals(5.0, back.legendX, 1e-9);
        assertEquals(20, back.paces.get("On foot"), 1e-9);
        assertEquals(6, back.paces.size());
        WorldMap copy = m.copy();
        assertEquals(WorldGridKind.HEX, copy.gridKind);
        assertEquals(20, copy.paces.get("On foot"), 1e-9);
    }
}
