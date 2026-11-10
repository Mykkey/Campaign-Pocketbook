package dnd;

import dnd.map.world.WorldPathGeometry;
import dnd.model.WorldMap;
import dnd.model.WorldObjects;
import dnd.model.WorldPath;
import dnd.model.WorldRegion;
import dnd.model.WorldStyle;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorldPathTest {
    private static WorldPath line(double... xy) {
        WorldPath p = new WorldPath(WorldPath.Type.ROAD);
        for (int i = 0; i < xy.length; i += 2) p.add(xy[i], xy[i + 1]);
        return p;
    }

    @Test
    void theSmoothCurvePassesThroughItsPointsAndStartsAndEndsOnThem() {
        WorldPath p = line(0, 0, 10, 8, 20, 0, 30, 10);
        List<double[]> pts = WorldPathGeometry.smooth(p, 10);
        assertEquals(3 * 10 + 1, pts.size());
        assertArrayEquals(new double[]{0, 0}, pts.get(0), 1e-9);
        assertArrayEquals(new double[]{30, 10}, pts.get(pts.size() - 1), 1e-9);
        assertArrayEquals(new double[]{10, 8}, pts.get(10), 1e-9);
        assertArrayEquals(new double[]{20, 0}, pts.get(20), 1e-9);
        assertEquals(1, WorldPathGeometry.smooth(line(5, 5), 10).size());
        assertTrue(WorldPathGeometry.smooth(new WorldPath(), 10).isEmpty());
    }

    @Test
    void distancesAndInsertionPointsFollowTheLine() {
        WorldPath p = line(0, 0, 10, 0, 10, 10);
        List<double[]> pts = List.of(new double[]{0, 0}, new double[]{10, 0}, new double[]{10, 10});
        assertEquals(3, WorldPathGeometry.distance(pts, 5, 3), 1e-9);
        assertEquals(0, WorldPathGeometry.distance(pts, 10, 4), 1e-9);
        assertEquals(20, WorldPathGeometry.length(pts), 1e-9);
        assertEquals(1, WorldPathGeometry.insertIndex(p, 5, 1), "between the first two points");
        assertEquals(2, WorldPathGeometry.insertIndex(p, 11, 6), "between the last two");
        p.insert(1, 5, 0);
        assertEquals(4, p.count());
        assertEquals(5, p.x(1), 1e-9);
        p.remove(1);
        assertEquals(3, p.count());
        assertEquals(10, p.x(1), 1e-9);
    }

    @Test
    void aRiverRibbonGrowsFromSourceToMouth() {
        List<double[]> pts = new ArrayList<>();
        for (int i = 0; i <= 10; i++) pts.add(new double[]{i * 10, 0});
        List<List<double[]>> sides = WorldPathGeometry.ribbon(pts, 4, 0.25);
        double startWidth = Math.abs(sides.get(0).get(0)[1] - sides.get(1).get(0)[1]);
        double endWidth = Math.abs(sides.get(0).get(10)[1] - sides.get(1).get(10)[1]);
        assertEquals(1.0, startWidth, 1e-9);
        assertEquals(4.0, endWidth, 1e-9);
    }

    @Test
    void freehandLinesAreThinnedButKeepTheirShape() {
        List<Double> flat = new ArrayList<>();
        for (int i = 0; i <= 40; i++) {
            flat.add(i * 1.0);
            flat.add(i < 20 ? 0.0 : (i - 20) * 1.0); // straight, then a bend
        }
        List<Double> simple = WorldPathGeometry.simplify(flat, 0.3);
        assertTrue(simple.size() / 2 <= 5, "a few points are enough: " + simple.size() / 2);
        assertEquals(0, simple.get(0), 1e-9);
        assertEquals(40, simple.get(simple.size() - 2), 1e-9);
        assertEquals(20, simple.get(simple.size() - 1), 1e-9);
        assertTrue(simple.contains(19.0) || simple.contains(20.0), "the corner is kept");
        assertEquals(4, WorldPathGeometry.simplify(List.of(0.0, 0.0, 5.0, 5.0), 1).size(), "two points stay");
    }

    @Test
    void regionsKnowWhatIsInsideThem() {
        WorldRegion r = new WorldRegion();
        r.add(0, 0);
        r.add(10, 0);
        r.add(10, 10);
        r.add(0, 10);
        assertTrue(r.contains(5, 5));
        assertFalse(r.contains(15, 5));
        assertEquals(100, r.area(), 1e-9);
        assertArrayEquals(new double[]{5, 5}, r.centre(), 1e-9);
        WorldRegion tri = new WorldRegion();
        tri.add(0, 0);
        tri.add(10, 0);
        tri.add(0, 10);
        assertTrue(tri.contains(2, 2));
        assertFalse(tri.contains(8, 8));
    }

    @Test
    void linesAndRegionsAreSavedAndSnapshotsRestoreThem(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        WorldMap m = store.createWorldMap(store.project.root, new WorldMap("W", 64, 48, 640, WorldStyle.WATERCOLOUR));
        WorldPath river = line(1, 1, 20, 10, 30, 30);
        river.type = WorldPath.Type.RIVER;
        river.width = 2.5;
        river.name = "Silverflow";
        m.paths.add(river);
        WorldRegion r = new WorldRegion();
        r.name = "Vale";
        r.tint = "#aa3322";
        r.add(2, 2);
        r.add(30, 2);
        r.add(30, 30);
        m.regions.add(r);
        store.markWorldMapDirty(m, true);
        store.flush();
        WorldMap back = ProjectStore.open(tmp, "Camp").worldMap(m.id);
        assertEquals(1, back.paths.size());
        assertEquals(WorldPath.Type.RIVER, back.paths.get(0).type);
        assertEquals(3, back.paths.get(0).count());
        assertEquals(2.5, back.paths.get(0).width, 1e-9);
        assertEquals("Vale", back.regions.get(0).name);
        assertEquals("#aa3322", back.regions.get(0).tint);

        String before = WorldObjects.capture(m);
        m.paths.clear();
        m.regions.clear();
        assertTrue(m.paths.isEmpty());
        WorldObjects.restore(m, before);
        assertEquals(1, m.paths.size());
        assertEquals("Silverflow", m.paths.get(0).name);
        assertEquals(1, m.regions.size());
        assertEquals(1, m.copy().paths.size());
    }

    @Test
    void aRegionNameCanBeMovedAndStaysMoved() {
        WorldRegion r = new WorldRegion();
        r.add(0, 0);
        r.add(10, 0);
        r.add(10, 10);
        r.add(0, 10);
        assertArrayEquals(new double[]{5, 5}, r.labelAt(), 1e-9, "in the middle until moved");
        r.labelX = 2.0;
        r.labelY = 8.0;
        assertArrayEquals(new double[]{2, 8}, r.labelAt(), 1e-9);
        assertArrayEquals(new double[]{2, 8}, r.copy().labelAt(), 1e-9);
        WorldRegion back = dnd.storage.Json.read(dnd.storage.Json.write(r), WorldRegion.class);
        assertArrayEquals(new double[]{2, 8}, back.labelAt(), 1e-9);
    }

}
