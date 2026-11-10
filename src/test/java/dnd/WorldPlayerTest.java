package dnd;

import dnd.map.world.PngStream;
import dnd.map.world.WorldLegend;
import dnd.model.WorldMap;
import dnd.model.WorldObjects;
import dnd.model.WorldPath;
import dnd.model.WorldStamp;
import dnd.model.WorldStyle;
import dnd.storage.Json;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class WorldPlayerTest {
    private static WorldMap map() {
        return new WorldMap("Aldoria", 256, 192, 1280, WorldStyle.FANTASY_COLOUR);
    }

    private static List<String> features(List<WorldLegend.Entry> entries) {
        return entries.stream().map(e -> e.feature() != null ? e.feature() : "line:" + e.line().name()).collect(Collectors.toList());
    }

    @Test
    void everythingIsVisibleUntilUnexploredAreasAreHidden() {
        WorldMap m = map();
        assertFalse(m.hideUnexplored);
        assertTrue(m.isRevealed(10, 10), "a map that hides nothing shows everything");
        m.hideUnexplored = true;
        assertFalse(m.isRevealed(10, 10), "nothing is revealed at first");
        m.reveal().set(2, 2, (byte) 1); // covers map cells 8..11
        assertTrue(m.isRevealed(8, 8));
        assertTrue(m.isRevealed(11.9, 11.9));
        assertFalse(m.isRevealed(12, 8));
        assertFalse(m.isRevealed(-5, 3), "outside the map is never revealed");
        assertEquals(64, m.revealCols());
        assertEquals(48, m.revealRows());
    }

    @Test
    void theRevealedAreaAndTheSwitchSurviveSavingAndCopying(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        WorldMap m = store.createWorldMap(store.project.root, map());
        m.hideUnexplored = true;
        m.reveal().set(5, 6, (byte) 1);
        m.reveal().set(63, 47, (byte) 1);
        store.markWorldMapDirty(m, true);
        store.flush();

        WorldMap back = ProjectStore.open(tmp, "Camp").worldMap(m.id);
        assertTrue(back.hideUnexplored);
        assertEquals(1, back.reveal().get(5, 6));
        assertEquals(1, back.reveal().get(63, 47));
        assertEquals(0, back.reveal().get(6, 6));

        WorldMap copy = back.copy();
        assertTrue(copy.hideUnexplored);
        assertEquals(1, copy.reveal().get(5, 6));
        copy.reveal().set(5, 6, (byte) 0);
        assertEquals(1, back.reveal().get(5, 6), "the copy has its own revealed area");
    }

    @Test
    void mapsSavedBeforeThePlayerViewStillLoadWithEverythingShown() {
        String old = "{\"name\":\"Old\",\"cols\":128,\"rows\":96,\"milesPerCell\":2.0}";
        WorldMap m = Json.read(old, WorldMap.class);
        assertFalse(m.hideUnexplored);
        assertTrue(m.reveal().isEmpty());
        assertTrue(m.isRevealed(50, 50));
    }

    @Test
    void anUntouchedRevealLayerIsNotSaved(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        WorldMap m = store.createWorldMap(store.project.root, map());
        store.flush();
        String json = java.nio.file.Files.readString(store.dir.resolve("worldmaps").resolve(m.id + ".worldmap"));
        assertFalse(json.contains("\"revealData\" : \""), json);
    }

    @Test
    void theHiddenFlagSurvivesUndoSnapshotsAndCopies() {
        WorldMap m = map();
        WorldStamp s = new WorldStamp("tree-oak", 5, 5);
        s.hidden = true;
        m.stamps.add(s);
        WorldPath p = new WorldPath();
        p.hidden = true;
        m.paths.add(p);
        String snapshot = WorldObjects.capture(m);
        m.stamps.get(0).hidden = false;
        m.paths.get(0).hidden = false;
        WorldObjects.restore(m, snapshot);
        assertTrue(m.stamps.get(0).hidden);
        assertTrue(m.paths.get(0).hidden);
        assertTrue(m.stamps.get(0).copy().hidden);
        assertTrue(m.paths.get(0).copy().hidden);
    }

    @Test
    void thePlayersKeyNeverMentionsWhatTheyMustNotKnowAbout() {
        WorldMap m = map();
        m.hideUnexplored = true;
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) m.reveal().set(x, y, (byte) 1); // the top left 64 x 64 cells
        WorldStamp seen = new WorldStamp("tree-oak", 10, 10);
        WorldStamp secret = new WorldStamp("peak-single", 12, 12);
        secret.hidden = true;
        WorldStamp faraway = new WorldStamp("settlement-capital", 200, 150);
        m.stamps.addAll(List.of(seen, secret, faraway));
        WorldPath road = new WorldPath();
        road.type = WorldPath.Type.ROAD;
        road.add(10, 10);
        road.add(30, 30);
        WorldPath secretRiver = new WorldPath();
        secretRiver.type = WorldPath.Type.RIVER;
        secretRiver.add(10, 10);
        secretRiver.add(30, 30);
        secretRiver.hidden = true;
        WorldPath farRailway = new WorldPath();
        farRailway.type = WorldPath.Type.RAILWAY;
        farRailway.add(200, 150);
        farRailway.add(220, 160);
        m.paths.addAll(List.of(road, secretRiver, farRailway));

        List<String> dm = features(WorldLegend.entries(m));
        assertTrue(dm.contains("peak-single") && dm.contains("settlement-capital"), "the DM's key lists everything: " + dm);
        assertTrue(dm.contains("line:RIVER") && dm.contains("line:RAILWAY"), dm.toString());

        List<String> players = features(WorldLegend.entries(m, true));
        assertTrue(players.contains("tree-oak"), players.toString());
        assertTrue(players.contains("line:ROAD"), players.toString());
        assertFalse(players.contains("peak-single"), "hidden symbols are not in the key: " + players);
        assertFalse(players.contains("settlement-capital"), "symbols in unexplored areas are not in the key: " + players);
        assertFalse(players.contains("line:RIVER"), "hidden lines are not in the key: " + players);
        assertFalse(players.contains("line:RAILWAY"), "lines in unexplored areas are not in the key: " + players);

        m.hideUnexplored = false;
        List<String> noFog = features(WorldLegend.entries(m, true));
        assertTrue(noFog.contains("settlement-capital"), "without the fog only hidden things are left out: " + noFog);
        assertFalse(noFog.contains("peak-single"));
    }

    @Test
    void aPngWrittenInBandsIsAValidPictureWithTheRightPixels() throws Exception {
        int w = 37, h = 23;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PngStream png = new PngStream(out, w, h)) {
            int row = 0;
            for (int rows : new int[]{10, 10, 3}) { // bands of unequal height
                byte[] band = new byte[w * rows * 3];
                for (int r = 0; r < rows; r++) {
                    for (int x = 0; x < w; x++) {
                        int o = (r * w + x) * 3;
                        band[o] = (byte) x;
                        band[o + 1] = (byte) (row + r);
                        band[o + 2] = (byte) 200;
                    }
                }
                png.writeRows(band, rows);
                row += rows;
            }
        }
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(out.toByteArray()));
        assertNotNull(img);
        assertEquals(w, img.getWidth());
        assertEquals(h, img.getHeight());
        for (int y : new int[]{0, 9, 10, 19, 22}) {
            for (int x : new int[]{0, 5, 36}) {
                assertEquals((x << 16) | (y << 8) | 200, img.getRGB(x, y) & 0xffffff, "pixel " + x + "," + y);
            }
        }
    }

    @Test
    void aBigPngStreamsWithoutNeedingTheWholePictureAtOnce() throws Exception {
        int w = 3000, h = 2000;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PngStream png = new PngStream(out, w, h)) {
            byte[] band = new byte[w * 100 * 3];
            for (int i = 0; i < band.length; i++) band[i] = (byte) ((i / 3) % w < 1500 ? 40 : 220);
            for (int y = 0; y < h; y += 100) png.writeRows(band, 100);
        }
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(w, img.getWidth());
        assertEquals(h, img.getHeight());
        assertEquals(0x282828, img.getRGB(10, 1999) & 0xffffff);
        assertEquals(0xdcdcdc, img.getRGB(2999, 0) & 0xffffff);
    }

    @Test
    void anUnfinishedPngIsRefusedAndTooManyRowsAreRefused() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PngStream png = new PngStream(out, 4, 4);
        png.writeRows(new byte[4 * 2 * 3], 2);
        assertThrows(IllegalStateException.class, () -> png.writeRows(new byte[4 * 3 * 3], 3));
        assertThrows(IllegalStateException.class, png::close);
    }
}
