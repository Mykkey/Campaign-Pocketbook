package dnd;

import dnd.map.world.WorldRelief;
import dnd.map.world.WorldResize;
import dnd.model.PaintLayer;
import dnd.model.WorldMap;
import dnd.model.WorldStyle;
import dnd.storage.Json;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WorldReliefTest {
    /** A hill: a cone of the given radius in the middle of a w x h grid of heights (in cells). */
    private static float[] cone(int w, int h, double radius, double peak) {
        float[] a = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double d = Math.hypot(x - w / 2.0, y - h / 2.0);
                a[y * w + x] = (float) Math.max(0, peak * (1 - d / radius));
            }
        }
        return a;
    }

    @Test
    void flatGroundHasNoShade() {
        float[] flat = new float[20 * 20];
        for (float s : WorldRelief.shade(flat, 20, 20, 1)) assertEquals(0, s, 1e-9);
        float[] sloped = new float[20 * 20];
        java.util.Arrays.fill(sloped, 9f);
        assertEquals(0, WorldRelief.shadeAt(sloped, 20, 20, 10, 10, 1), 1e-9, "a high plateau is still flat");
    }

    @Test
    void slopesFacingTheSunAreLitAndTheOthersAreInShadow() {
        int w = 41, h = 41;
        float[] hill = cone(w, h, 15, 12);
        // the sun is at the top left, so the north west flank is lit and the south east flank is dark
        double nw = WorldRelief.shadeAt(hill, w, h, 20 - 6, 20 - 6, 1);
        double se = WorldRelief.shadeAt(hill, w, h, 20 + 6, 20 + 6, 1);
        assertTrue(nw > 0.1, "north west " + nw);
        assertTrue(se < -0.1, "south east " + se);
        assertEquals(0, WorldRelief.shadeAt(hill, w, h, 2, 2, 1), 1e-9, "outside the hill");
    }

    @Test
    void steeperSlopesAndStrongerShadingGiveMoreContrast() {
        int w = 41, h = 41;
        double gentle = Math.abs(WorldRelief.shadeAt(cone(w, h, 15, 4), w, h, 14, 14, 1));
        double steep = Math.abs(WorldRelief.shadeAt(cone(w, h, 15, 12), w, h, 14, 14, 1));
        assertTrue(steep > gentle);
        float[] hill = cone(w, h, 15, 6);
        assertTrue(Math.abs(WorldRelief.shadeAt(hill, w, h, 14, 14, 3)) > Math.abs(WorldRelief.shadeAt(hill, w, h, 14, 14, 0.5)));
        for (float s : WorldRelief.shade(cone(w, h, 15, 30), w, h, 4)) assertTrue(s >= -1 && s <= 1);
    }

    @Test
    void pixelArtUsesThreeBands() {
        assertEquals(-1, WorldRelief.band(-0.5));
        assertEquals(0, WorldRelief.band(0.05));
        assertEquals(1, WorldRelief.band(0.4));
    }

    @Test
    void elevationValuesBecomeHeightsInCells() {
        assertEquals(0, WorldRelief.heightOf(0), 1e-9);
        assertEquals(WorldRelief.MAX_HEIGHT_CELLS, WorldRelief.heightOf(255), 1e-6);
        assertEquals(WorldRelief.heightOf(200), WorldRelief.heightOf((byte) 200), 1e-9, "bytes above 127 are read as unsigned");
    }

    @Test
    void elevationIsSavedCopiedAndOldMapsStayFlat(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        WorldMap m = store.createWorldMap(store.project.root, new WorldMap("Hills", 64, 48, 640, WorldStyle.FANTASY_COLOUR));
        store.flush();
        String json = java.nio.file.Files.readString(store.dir.resolve("worldmaps").resolve(m.id + ".worldmap"));
        assertFalse(json.contains("\"elevationData\" : \""), "no elevation, nothing written");

        m.elevation().set(10, 12, (byte) 200);
        m.relief = false;
        m.reliefStrength = 2.5;
        store.markWorldMapDirty(m, true);
        store.flush();
        WorldMap back = ProjectStore.open(tmp, "Camp").worldMap(m.id);
        assertEquals(200, back.elevation().get(10, 12) & 0xFF);
        assertFalse(back.relief);
        assertEquals(2.5, back.reliefStrength, 1e-9);

        WorldMap copy = back.copy();
        assertEquals(200, copy.elevation().get(10, 12) & 0xFF);
        copy.elevation().set(10, 12, (byte) 1);
        assertEquals(200, back.elevation().get(10, 12) & 0xFF, "the copy has its own heights");

        WorldMap old = Json.read("{\"name\":\"Old\",\"cols\":64,\"rows\":48,\"milesPerCell\":2.0}", WorldMap.class);
        assertTrue(old.elevation().isEmpty());
        assertTrue(old.relief);
        assertEquals(1.0, old.reliefStrength, 1e-9);
    }

    @Test
    void resizingCarriesTheHeightsAndUndoRestoresThem() {
        WorldMap m = new WorldMap("Hills", 400, 300, 2000, WorldStyle.FANTASY_COLOUR);
        for (int y = 100; y < 200; y++) for (int x = 200; x < 300; x++) m.elevation().set(x, y, (byte) 180);
        WorldResize.State before = WorldResize.capture(m);
        WorldResize.scale(m, 200, 10);
        assertEquals(200, m.elevation().w);
        assertEquals(180, m.elevation().get(125, 75) & 0xFF);
        assertEquals(0, m.elevation().get(10, 10));
        WorldResize.restore(m, before);
        assertEquals(400, m.elevation().w);
        assertEquals(180, m.elevation().get(250, 150) & 0xFF);

        WorldResize.canvas(m, -150, -50, 200, 200, 5);
        assertEquals(180, m.elevation().get(50, 50) & 0xFF);
        assertEquals(0, m.elevation().get(49, 50));
    }
}
