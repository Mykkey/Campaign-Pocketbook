package dnd;

import dnd.map.world.WorldResize;
import dnd.model.WorldMap;
import dnd.model.WorldPath;
import dnd.model.WorldRegion;
import dnd.model.WorldStamp;
import dnd.model.WorldStyle;
import dnd.model.WorldTerrain;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldResizeTest {
    private static WorldMap map() {
        WorldMap m = new WorldMap("Big", 400, 300, 2000, WorldStyle.FANTASY_COLOUR); // 5 miles a cell
        for (int y = 100; y < 200; y++) for (int x = 200; x < 300; x++) m.base().set(x, y, WorldTerrain.LAND.code);
        WorldStamp town = new WorldStamp("settlement-capital", 250, 150);
        town.size = 20;
        WorldStamp def = new WorldStamp("tree-oak", 40, 40); // default size
        m.stamps.add(town);
        m.stamps.add(def);
        WorldPath road = new WorldPath(WorldPath.Type.ROAD);
        road.add(100, 100);
        road.add(300, 200);
        m.paths.add(road);
        WorldRegion r = new WorldRegion();
        r.add(200, 100);
        r.add(300, 100);
        r.add(300, 200);
        m.regions.add(r);
        m.legendX = 20.0;
        m.legendY = 240.0;
        for (int y = 24; y < 28; y++) for (int x = 48; x < 52; x++) m.reveal().set(x, y, (byte) 1);
        return m;
    }

    @Test
    void scalingDownShrinksPaintSymbolsLinesAndTheLegendTogether() {
        WorldMap m = map();
        double defaultWidth = dnd.map.world.WorldStampRenderer.widthOf(m.stamps.get(1), m.style);
        WorldResize.scale(m, 200, 10); // half size, 10 miles a cell: same 2000 miles across
        assertEquals(200, m.cols);
        assertEquals(150, m.rows);
        assertEquals(10, m.milesPerCell, 1e-9);
        assertEquals(WorldTerrain.LAND.code, m.base().get(125, 75));
        assertEquals(0, m.base().get(50, 75));
        assertEquals(125, m.stamps.get(0).x, 1e-9);
        assertEquals(10, m.stamps.get(0).size, 1e-9);
        assertEquals(defaultWidth / 2, m.stamps.get(1).size, 1e-9, "a default-sized symbol shrinks with the map");
        assertEquals(50, m.paths.get(0).x(0), 1e-9);
        assertEquals(WorldPath.Type.ROAD.defaultWidth / 2, m.paths.get(0).width, 1e-9);
        assertEquals(150, m.regions.get(0).x(1), 1e-9);
        assertEquals(10, m.legendX, 1e-9);
        assertEquals(120, m.legendY, 1e-9);
        assertEquals(50, m.reveal().w);
        assertEquals(1, m.reveal().get(25, 12), "the revealed patch moved with the map");
        assertEquals(0, m.reveal().get(50, 25));
    }

    @Test
    void scalingUpAndBackKeepsTheShapeOfThePaint() {
        WorldMap m = map();
        WorldResize.scale(m, 800, 2.5);
        assertEquals(WorldTerrain.LAND.code, m.base().get(500, 300));
        assertEquals(0, m.base().get(399, 300));
        WorldResize.scale(m, 400, 5);
        assertEquals(WorldTerrain.LAND.code, m.base().get(250, 150));
        assertEquals(0, m.base().get(150, 150));
        assertEquals(250, m.stamps.get(0).x, 1e-9);
    }

    @Test
    void croppingTheCanvasKeepsScaleAndRemovesSymbolsOutsideButKeepsLinesAtTheEdge() {
        WorldMap m = map();
        assertEquals(1, WorldResize.symbolsLost(m, -150, -50, 200, 200), "the tree at 40,40 would be cropped away");
        WorldResize.canvas(m, -150, -50, 200, 200, m.milesPerCell);
        assertEquals(200, m.cols);
        assertEquals(200, m.rows);
        assertEquals(5, m.milesPerCell, 1e-9);
        assertEquals(1, m.stamps.size());
        assertEquals(100, m.stamps.get(0).x, 1e-9);
        assertEquals(100, m.stamps.get(0).y, 1e-9);
        assertEquals(20, m.stamps.get(0).size, 1e-9, "nothing changes size");
        assertEquals(WorldTerrain.LAND.code, m.base().get(50, 50));
        assertEquals(0, m.base().get(49, 50));
        assertEquals(0, m.paths.get(0).x(0), 1e-9, "the road start is held at the edge");
        assertEquals(150, m.paths.get(0).x(1), 1e-9);
        assertEquals(1, m.reveal().get(12, 12), "the revealed patch moved with the paint");
    }

    @Test
    void extendingTheCanvasAddsEmptyMarginsAndSizesAreLimited() {
        WorldMap m = map();
        WorldResize.canvas(m, 100, 0, 600, 300, m.milesPerCell);
        assertEquals(WorldTerrain.LAND.code, m.base().get(350, 150));
        assertEquals(0, m.base().get(50, 150));
        assertEquals(350, m.stamps.get(0).x, 1e-9);
        WorldResize.scale(m, 99999, 1);
        assertEquals(WorldResize.MAX_COLS, m.cols);
    }

    @Test
    void aResizeCanBeUndoneExactly() {
        WorldMap m = map();
        String id = m.stamps.get(0).id;
        WorldResize.State before = WorldResize.capture(m);
        WorldResize.canvas(m, -150, -50, 200, 200, 7);
        WorldResize.restore(m, before);
        assertEquals(400, m.cols);
        assertEquals(300, m.rows);
        assertEquals(5, m.milesPerCell, 1e-9);
        assertEquals(2, m.stamps.size());
        assertEquals(id, m.stamps.get(0).id, "ids survive so selections and links keep working");
        assertEquals(250, m.stamps.get(0).x, 1e-9);
        assertEquals(WorldTerrain.LAND.code, m.base().get(250, 150));
        assertEquals(20.0, m.legendX, 1e-9);
        assertEquals(1, m.reveal().get(50, 25));
        assertEquals(300, m.paths.get(0).x(1), 1e-9);
    }
}
