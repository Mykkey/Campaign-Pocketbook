package dnd;

import dnd.map.world.ScatterPlanner;
import dnd.map.world.WorldAssets;
import dnd.map.world.WorldStampRenderer;
import dnd.model.WorldMap;
import dnd.model.WorldStamp;
import dnd.model.WorldStyle;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class WorldStampTest {
    @Test
    void scatteredPointsStayInsideTheBrushAndKeepTheirDistance() {
        List<double[]> existing = new ArrayList<>(List.of(new double[]{50, 50}));
        List<double[]> pts = ScatterPlanner.plan(50, 50, 20, 4, existing, 400, new Random(7));
        assertTrue(pts.size() > 10, "a decent number: " + pts.size());
        for (double[] p : pts) {
            assertTrue(Math.hypot(p[0] - 50, p[1] - 50) <= 20.0001, "inside the circle");
            assertTrue(Math.hypot(p[0] - 50, p[1] - 50) >= 4, "not on the existing symbol");
        }
        for (int i = 0; i < pts.size(); i++) {
            for (int j = i + 1; j < pts.size(); j++) {
                assertTrue(Math.hypot(pts.get(i)[0] - pts.get(j)[0], pts.get(i)[1] - pts.get(j)[1]) >= 4 - 1e-9, "spread out");
            }
        }
        assertTrue(ScatterPlanner.plan(50, 50, 20, 4, existing, 400, new Random(7)).size() == pts.size(), "same seed, same result");
        assertTrue(ScatterPlanner.plan(0, 0, 5, 100, List.of(), 50, new Random(1)).size() <= 1, "huge spacing allows only one");
    }

    @Test
    void everySymbolHasAPictureInEveryStyleAndASaneSize() {
        assertTrue(WorldAssets.features().size() >= 60);
        for (WorldAssets.Feature f : WorldAssets.features()) {
            assertTrue(f.w() > 0 && f.h() > 0, f.id());
            for (WorldStyle s : WorldStyle.values()) {
                String res = "/assets/" + WorldAssets.assetId(s, f.id()) + ".svg";
                assertNotNull(WorldAssets.class.getResource(res), res);
            }
        }
        assertTrue(WorldAssets.categories().contains("Forests") && WorldAssets.categories().contains("Mountains and hills"));
        assertTrue(WorldAssets.variants("tree-oak").size() >= 3, "oaks and birches mix");
        assertEquals(1, WorldAssets.variants("tower").stream().filter(f -> f.id().equals("tower")).count());
        assertTrue(WorldAssets.variants("no-such-symbol").isEmpty());
    }

    @Test
    void hitTestingAndTheResizeHandleFollowRotationAndFlip() {
        WorldStamp s = new WorldStamp("peak-single", 100, 100);
        s.size = 8;
        double w = WorldStampRenderer.widthOf(s), h = WorldStampRenderer.heightOf(s);
        assertEquals(8, w, 1e-9);
        assertTrue(WorldStampRenderer.contains(s, 100, 100 - h / 2), "a point in the middle of its picture");
        assertFalse(WorldStampRenderer.contains(s, 100, 100 + 1), "below its foot");
        assertFalse(WorldStampRenderer.contains(s, 100 + w, 100 - h / 2), "beside it");

        double[] handle = WorldStampRenderer.handleOf(s);
        assertEquals(100 + w / 2, handle[0], 1e-9);
        assertEquals(100, handle[1], 1e-9);

        s.rotation = 90; // clockwise a quarter turn: what was above the foot is now to its right
        assertTrue(WorldStampRenderer.contains(s, 100 + h / 2, 100));
        assertFalse(WorldStampRenderer.contains(s, 95, 100 - h / 2));
        double[] turned = WorldStampRenderer.handleOf(s);
        assertEquals(100, turned[0], 1e-9);
        assertEquals(100 + w / 2, turned[1], 1e-9);
        double[] local = WorldStampRenderer.toLocal(s, turned[0], turned[1]);
        assertEquals(w / 2, local[0], 1e-9);
        assertEquals(0, local[1], 1e-9);

        s.rotation = 0;
        s.flipX = true;
        assertEquals(100 - w / 2, WorldStampRenderer.handleOf(s)[0], 1e-9, "flipped, the handle is on the left");
        double[][] c = WorldStampRenderer.corners(s);
        assertEquals(4, c.length);
    }

    @Test
    void theTopmostSymbolIsTheOneNearestTheViewer() {
        WorldMap m = new WorldMap("x", 100, 100, 100, WorldStyle.FANTASY_COLOUR);
        WorldStamp behind = new WorldStamp("tree-oak", 50, 40);
        WorldStamp front = new WorldStamp("tree-oak", 50, 44);
        behind.size = front.size = 10;
        m.stamps.add(front);
        m.stamps.add(behind);
        assertSame(front, WorldStampRenderer.hit(m, 50, 40, true), "further down the map is in front");
        front.hidden = true;
        assertSame(behind, WorldStampRenderer.hit(m, 50, 40, false), "hidden ones are skipped for players");
        assertNull(WorldStampRenderer.hit(m, 5, 5, true));
        assertEquals(List.of(behind, front), WorldStampRenderer.inOrder(m));
    }

    @Test
    void symbolsAreSavedWithTheMap(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        WorldMap m = store.createWorldMap(store.project.root, new WorldMap("W", 64, 48, 640, WorldStyle.DARK_FANTASY));
        WorldStamp s = new WorldStamp("tower", 12.5, 20);
        s.size = 6;
        s.rotation = 30;
        s.flipX = true;
        s.style = WorldStyle.CLASSIC_INK;
        s.note = "Where the mage lives";
        m.stamps.add(s);
        store.markWorldMapDirty(m, true);
        store.flush();
        WorldMap back = ProjectStore.open(tmp, "Camp").worldMap(m.id);
        assertEquals(1, back.stamps.size());
        WorldStamp b = back.stamps.get(0);
        assertEquals("tower", b.feature);
        assertEquals(12.5, b.x, 1e-9);
        assertEquals(WorldStyle.CLASSIC_INK, b.style);
        assertTrue(b.flipX);
        assertEquals("Where the mage lives", b.note);
        assertEquals(1, m.copy().stamps.size());
        assertNotSame(s, m.copy().stamps.get(0));
    }
}
