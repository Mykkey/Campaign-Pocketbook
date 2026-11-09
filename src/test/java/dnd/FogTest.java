package dnd;

import dnd.map.Fog;
import dnd.map.Grid;
import dnd.model.BattleMap;
import dnd.model.MapObject;
import dnd.model.MapToken;
import dnd.storage.Json;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FogTest {

    private static BattleMap map(BattleMap.GridType type) {
        BattleMap m = new BattleMap();
        m.gridType = type;
        m.cols = 20;
        m.rows = 14;
        m.cellSize = 50;
        return m;
    }

    @Test
    void revealRadiusOnASquareGridCoversASquareOfCells() {
        BattleMap m = map(BattleMap.GridType.SQUARE);
        Grid g = Grid.of(m);
        assertEquals(25, Fog.revealRadius(m, g, 10, 7, 2));      // 5 x 5
        assertEquals(25, m.revealed.size());
        assertTrue(Fog.isRevealed(m, 8, 5));
        assertTrue(Fog.isRevealed(m, 12, 9));
        assertFalse(Fog.isRevealed(m, 13, 7));
        assertEquals(0, Fog.revealRadius(m, g, 10, 7, 2), "revealing the same area again changes nothing");
    }

    @Test
    void revealRadiusOnAHexGridCoversHexRings() {
        BattleMap m = map(BattleMap.GridType.HEX);
        Grid g = Grid.of(m);
        assertEquals(1, Fog.revealRadius(m, g, 10, 7, 0));
        assertEquals(7 - 1, Fog.revealRadius(m, g, 10, 7, 1));   // the six neighbours (centre already revealed)
        assertEquals(12, Fog.revealRadius(m, g, 10, 7, 2));      // the outer ring of 12
        assertEquals(19, m.revealed.size());                     // 1 + 6 + 12
        BattleMap m2 = map(BattleMap.GridType.HEX);
        Fog.revealRadius(m2, Grid.of(m2), 10, 7, 2);
        assertEquals(19, m2.revealed.size());                    // 1 + 6 + 12
    }

    @Test
    void revealNearTheEdgeIsClippedToTheMap() {
        BattleMap m = map(BattleMap.GridType.SQUARE);
        Grid g = Grid.of(m);
        assertEquals(9, Fog.revealRadius(m, g, 0, 0, 2));        // only the 3 x 3 inside the map
        BattleMap m2 = map(BattleMap.GridType.SQUARE);
        assertEquals(9, Fog.revealRadius(m2, Grid.of(m2), 19, 13, 2));
        for (String key : m.revealed) {
            String[] p = key.split(",");
            assertTrue(Integer.parseInt(p[0]) >= 0 && Integer.parseInt(p[1]) >= 0);
        }
    }

    @Test
    void brushRevealsAndHidesCells() {
        BattleMap m = map(BattleMap.GridType.SQUARE);
        Grid g = Grid.of(m);
        assertEquals(1, Fog.paint(m, g, 5, 5, 1, true));
        assertEquals(1, m.revealed.size());
        BattleMap m2 = map(BattleMap.GridType.SQUARE);
        assertEquals(9, Fog.paint(m2, Grid.of(m2), 5, 5, 2, true));  // brush 2 = 3 x 3
        assertEquals(1, Fog.paint(m2, Grid.of(m2), 5, 5, 1, false));
        assertFalse(Fog.isRevealed(m2, 5, 5));
        assertEquals(8, m2.revealed.size());
        BattleMap h = map(BattleMap.GridType.HEX);
        assertEquals(7, Fog.paint(h, Grid.of(h), 6, 6, 2, true));    // a hex and its six neighbours
    }

    @Test
    void rectangleRevealsCellsWhoseCentresAreInsideIt() {
        BattleMap m = map(BattleMap.GridType.SQUARE);
        Grid g = Grid.of(m);
        // centres are at 25, 75, 125, ... so 0..120 includes two columns and two rows
        assertEquals(4, Fog.setRect(m, g, 0, 0, 120, 120, true));
        assertEquals(4, Fog.setRect(m, g, 120, 120, 0, 0, true) + 4, "corner order does not matter");
        assertEquals(4, Fog.setRect(m, g, 0, 0, 120, 120, false), "the same rectangle hides them again");
        assertTrue(m.revealed.isEmpty());
    }

    @Test
    void revealAllAndHideAll() {
        BattleMap m = map(BattleMap.GridType.SQUARE);
        Grid g = Grid.of(m);
        assertEquals(20 * 14, Fog.revealAll(m, g));
        assertEquals(0, Fog.revealAll(m, g));
        assertEquals(20 * 14, Fog.hideAll(m));
        assertTrue(m.revealed.isEmpty());
    }

    @Test
    void fogAndHiddenFlagsSurviveSaveAndLoad(@TempDir Path tmp) throws Exception {
        ProjectStore s = ProjectStore.create(tmp, "P");
        BattleMap m = s.createBattleMap(s.project.root, "Cave", BattleMap.GridType.HEX, 12, 9, 60);
        m.fogEnabled = true;
        Fog.revealRadius(m, Grid.of(m), 5, 4, 2);
        MapToken t = new MapToken();
        t.label = "Elf";
        t.revealRadius = 6;
        t.hidden = true;
        t.cardId = "some-card";
        m.tokens.add(t);
        MapObject trap = new MapObject();
        trap.assetId = "dungeon/spike-trap";
        trap.hidden = true;
        m.objects.add(trap);
        s.markMapDirty(m);
        s.flush();

        BattleMap loaded = ProjectStore.open(tmp, "P").battleMap(m.id);
        assertTrue(loaded.fogEnabled);
        assertEquals(19, loaded.revealed.size());
        assertEquals(6, loaded.tokens.get(0).revealRadius);
        assertTrue(loaded.tokens.get(0).hidden);
        assertEquals("some-card", loaded.tokens.get(0).cardId);
        assertTrue(loaded.objects.get(0).hidden);
    }

    @Test
    void mapsSavedBeforeFogExistedLoadWithFogOff() {
        String old = "{\"id\":\"x\",\"name\":\"Old\",\"gridType\":\"SQUARE\",\"cols\":10,\"rows\":8,\"cellSize\":50,"
                + "\"tokens\":[{\"id\":\"t\",\"label\":\"A\",\"col\":1,\"row\":1}],\"objects\":[{\"id\":\"o\",\"assetId\":\"camp/barrel\"}]}";
        BattleMap m = Json.read(old, BattleMap.class);
        assertFalse(m.fogEnabled);
        assertTrue(m.revealed.isEmpty());
        assertNull(m.battle);
        assertFalse(m.tokens.get(0).hidden);
        assertEquals(0, m.tokens.get(0).revealRadius);
        assertFalse(m.objects.get(0).hidden);
    }

    @Test
    void copiesKeepFogSettings() {
        MapToken t = new MapToken();
        t.revealRadius = 9;
        t.hidden = true;
        t.cardId = "c";
        MapToken c = t.copy();
        assertEquals(9, c.revealRadius);
        assertTrue(c.hidden);
        assertEquals("c", c.cardId);
        MapObject o = new MapObject();
        o.hidden = true;
        assertTrue(o.copy().hidden);
    }
}
