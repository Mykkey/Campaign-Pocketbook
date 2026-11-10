package dnd;

import dnd.map.world.WorldEra;
import dnd.map.world.WorldLegend;
import dnd.map.world.WorldPalette;
import dnd.map.world.WorldPathRenderer;
import dnd.map.world.WorldStampRenderer;
import dnd.model.WorldBiome;
import dnd.model.WorldMap;
import dnd.model.WorldObjects;
import dnd.model.WorldPath;
import dnd.model.WorldRegion;
import dnd.model.WorldSeason;
import dnd.model.WorldStamp;
import dnd.model.WorldStyle;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WorldEraTest {
    private static int brightness(int c) {
        return ((c >> 16) & 255) + ((c >> 8) & 255) + (c & 255);
    }

    @Test
    void thingsExistOnlyInTheirYears() {
        assertTrue(WorldEra.visible(null, null, 500), "no limits: always");
        assertTrue(WorldEra.visible(100, 300, null), "no year chosen: everything shows");
        assertFalse(WorldEra.visible(100, 300, 99));
        assertTrue(WorldEra.visible(100, 300, 100));
        assertTrue(WorldEra.visible(100, 300, 300));
        assertFalse(WorldEra.visible(100, 300, 301));
        assertTrue(WorldEra.visible(100, null, 5000), "founded and never destroyed");
        assertFalse(WorldEra.visible(100, null, 50));
        assertTrue(WorldEra.visible(null, 300, -40), "gone after year 300");
        assertFalse(WorldEra.visible(null, 300, 400));
        assertEquals("years 100 to 300", WorldEra.describe(100, 300));
        assertEquals("from year 100", WorldEra.describe(100, null));
        assertEquals("until year 300", WorldEra.describe(null, 300));
        assertEquals("", WorldEra.describe(null, null));
    }

    @Test
    void onlyThingsOfTheChosenYearAreFoundDrawnOrListed() {
        WorldMap m = new WorldMap("Eras", 200, 150, 1000, WorldStyle.FANTASY_COLOUR);
        WorldStamp old = new WorldStamp("settlement-capital", 50, 50);
        old.size = 20;
        old.toYear = 300;
        WorldStamp young = new WorldStamp("settlement-town", 120, 50);
        young.size = 20;
        young.fromYear = 300;
        WorldStamp always = new WorldStamp("tree-oak", 50, 120);
        always.size = 20;
        m.stamps.add(old);
        m.stamps.add(young);
        m.stamps.add(always);
        WorldPath road = new WorldPath(WorldPath.Type.ROAD);
        road.add(10, 100);
        road.add(190, 100);
        road.fromYear = 400;
        m.paths.add(road);
        WorldRegion realm = new WorldRegion();
        realm.add(0, 0);
        realm.add(190, 0);
        realm.add(190, 40);
        realm.toYear = 100;
        m.regions.add(realm);

        m.year = null;
        assertSame(old, WorldStampRenderer.hit(m, 50, 45, true));
        assertEquals(4, WorldLegend.entries(m).size(), "with no year everything is listed: capital, town, tree and road");

        m.year = 50;
        assertSame(realm, WorldPathRenderer.hitRegion(m, 150, 10, true));

        m.year = 200;
        assertSame(old, WorldStampRenderer.hit(m, 50, 45, true));
        assertNull(WorldStampRenderer.hit(m, 120, 45, true), "the town is not founded yet");
        assertNull(WorldPathRenderer.hitPath(m, 100, 100, 4, true), "the road is not built yet");
        assertNull(WorldPathRenderer.hitRegion(m, 150, 10, true), "the realm fell in year 100");
        assertTrue(WorldLegend.entries(m).stream().noneMatch(e -> "settlement-town".equals(e.feature())));
        assertTrue(WorldLegend.entries(m).stream().noneMatch(e -> e.line() == WorldPath.Type.ROAD));

        m.year = 450;
        assertNull(WorldStampRenderer.hit(m, 50, 45, true), "the capital has fallen");
        assertSame(young, WorldStampRenderer.hit(m, 120, 45, true));
        assertNotNull(WorldPathRenderer.hitPath(m, 100, 100, 4, true));
        assertNull(WorldPathRenderer.hitRegion(m, 150, 10, true), "the realm is gone");
        assertTrue(WorldLegend.entries(m).stream().anyMatch(e -> "settlement-town".equals(e.feature())));
        assertTrue(WorldLegend.entries(m).stream().noneMatch(e -> "settlement-capital".equals(e.feature())));
        assertTrue(WorldLegend.entries(m, true).stream().anyMatch(e -> e.line() == WorldPath.Type.ROAD));
    }

    @Test
    void yearsSeasonsAndTheYearSurviveSavingCopyingAndUndoSnapshots(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        WorldMap m = store.createWorldMap(store.project.root, new WorldMap("Eras", 64, 48, 640, WorldStyle.FANTASY_COLOUR));
        WorldStamp s = new WorldStamp("tree-oak", 5, 5);
        s.fromYear = 12;
        s.toYear = 99;
        m.stamps.add(s);
        WorldPath p = new WorldPath(WorldPath.Type.RIVER);
        p.fromYear = -5;
        m.paths.add(p);
        WorldRegion r = new WorldRegion();
        r.toYear = 7;
        m.regions.add(r);
        m.season = WorldSeason.WINTER;
        m.year = 50;
        store.markWorldMapDirty(m, true);
        store.flush();

        WorldMap back = ProjectStore.open(tmp, "Camp").worldMap(m.id);
        assertEquals(WorldSeason.WINTER, back.season);
        assertEquals(50, back.year);
        assertEquals(12, back.stamps.get(0).fromYear);
        assertEquals(99, back.stamps.get(0).toYear);
        assertEquals(-5, back.paths.get(0).fromYear);
        assertNull(back.paths.get(0).toYear);
        assertEquals(7, back.regions.get(0).toYear);

        WorldMap copy = back.copy();
        assertEquals(WorldSeason.WINTER, copy.season);
        assertEquals(50, copy.year);
        assertEquals(12, copy.stamps.get(0).fromYear);
        assertEquals(-5, copy.paths.get(0).fromYear);
        assertEquals(7, copy.regions.get(0).toYear);

        String snapshot = WorldObjects.capture(back);
        back.stamps.get(0).fromYear = null;
        WorldObjects.restore(back, snapshot);
        assertEquals(12, back.stamps.get(0).fromYear, "undo keeps the years");

        WorldMap old = dnd.storage.Json.read("{\"name\":\"Old\",\"cols\":64,\"rows\":48,\"milesPerCell\":2.0}", WorldMap.class);
        assertEquals(WorldSeason.NONE, old.season);
        assertNull(old.year);
    }

    @Test
    void everyStyleHasAPaletteForEverySeasonAndWinterIsPaler() {
        for (WorldStyle style : WorldStyle.values()) {
            assertSame(WorldPalette.of(style), WorldPalette.of(style, WorldSeason.NONE), "no season: the plain style");
            assertSame(WorldPalette.of(style), WorldPalette.of(style, null));
            WorldPalette plain = WorldPalette.of(style), winter = WorldPalette.of(style, WorldSeason.WINTER);
            assertSame(winter, WorldPalette.of(style, WorldSeason.WINTER), "palettes are cached");
            for (WorldSeason season : WorldSeason.values()) {
                WorldPalette p = WorldPalette.of(style, season);
                assertEquals(plain.parchment, p.parchment, "the paper never changes");
                assertEquals(plain.ink, p.ink);
                for (int i = 0; i < p.biome.length; i++) assertNotEquals(0, p.biome[i], style + " " + season + " " + i);
            }
            int grass = WorldBiome.GRASS.code;
            assertTrue(brightness(winter.biome[grass]) > brightness(plain.biome[grass]), style + ": winter grass is snowy");
            assertTrue(brightness(winter.land) > brightness(plain.land), style + ": winter plain land is snowy");
            assertNotEquals(plain.biome[WorldBiome.FOREST.code], WorldPalette.of(style, WorldSeason.AUTUMN).biome[WorldBiome.FOREST.code], style + ": autumn forest changes");
            boolean springDiffers = false;
            for (int i = 0; i < plain.biome.length; i++) if (plain.biome[i] != WorldPalette.of(style, WorldSeason.SPRING).biome[i]) springDiffers = true;
            assertTrue(springDiffers, style + ": spring changes some ground");
            assertEquals(plain.biome[WorldBiome.SNOW.code], winter.biome[WorldBiome.SNOW.code], "snow stays snow");
        }
    }
}
