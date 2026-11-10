package dnd;

import dnd.map.world.WorldPalette;
import dnd.model.PaintLayer;
import dnd.model.TreeNode;
import dnd.model.WorldBiome;
import dnd.model.WorldMap;
import dnd.model.WorldStyle;
import dnd.model.WorldTerrain;
import dnd.search.SearchService;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorldMapTest {
    @Test
    void aPaintLayerSurvivesBeingSavedAndIsSmallWhenMostlyUniform() {
        PaintLayer l = new PaintLayer(512, 384);
        java.util.Arrays.fill(l.cells, (byte) 1);
        l.set(10, 20, (byte) 3);
        l.set(511, 383, (byte) 4);
        String text = l.encode();
        assertTrue(text.length() < 2000, "a mostly uniform 512x384 layer is tiny: " + text.length());
        PaintLayer back = PaintLayer.decode(512, 384, text);
        assertArrayEquals(l.cells, back.cells);
        assertEquals(3, back.get(10, 20));
        assertEquals(0, back.get(-1, 5), "outside the map is nothing");
    }

    @Test
    void aMissingOrDamagedLayerLoadsEmptyAndRectanglesCanBeSavedAndPutBack() {
        assertTrue(PaintLayer.decode(10, 10, null).isEmpty());
        assertTrue(PaintLayer.decode(10, 10, "not base64 at all!!").isEmpty());
        assertTrue(PaintLayer.decode(10, 10, "AAAA").isEmpty());

        PaintLayer l = new PaintLayer(8, 8);
        for (int y = 2; y < 5; y++) for (int x = 3; x < 7; x++) l.set(x, y, (byte) 2);
        byte[] patch = l.copyRect(3, 2, 4, 3);
        PaintLayer other = new PaintLayer(8, 8);
        other.putRect(3, 2, 4, 3, patch);
        assertArrayEquals(l.cells, other.cells);
    }

    @Test
    void worldMapsAreSavedWithTheirPaintAndReloaded(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        WorldMap m = new WorldMap("Aldoria", 256, 192, 1280, WorldStyle.RETRO_PIXEL);
        store.createWorldMap(store.project.root, m);
        m.base().set(5, 6, WorldTerrain.LAND.code);
        m.base().set(6, 6, WorldTerrain.LAVA.code);
        m.biome().set(5, 6, WorldBiome.SNOW.code);
        store.markWorldMapDirty(m, true);
        store.flush();
        assertTrue(Files.isRegularFile(store.dir.resolve("worldmaps").resolve(m.id + ".worldmap")));

        WorldMap back = ProjectStore.open(tmp, "Camp").worldMap(m.id);
        assertNotNull(back);
        assertEquals("Aldoria", back.name);
        assertEquals(WorldStyle.RETRO_PIXEL, back.style);
        assertEquals(256, back.cols);
        assertEquals(5.0, back.milesPerCell, 1e-9);
        assertEquals(WorldTerrain.LAND.code, back.base().get(5, 6));
        assertEquals(WorldTerrain.LAVA.code, back.base().get(6, 6));
        assertEquals(WorldBiome.SNOW.code, back.biome().get(5, 6));
        assertEquals(0, back.base().get(7, 6));
    }

    @Test
    void anEmptyMapIsSavedWithoutLayerData(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        WorldMap m = store.createWorldMap(store.project.root, new WorldMap("Blank", 64, 64, 100, WorldStyle.CLASSIC_INK));
        store.flush();
        String json = Files.readString(store.dir.resolve("worldmaps").resolve(m.id + ".worldmap"));
        assertFalse(json.contains("\"baseData\" : \""), json);
        assertEquals(0, ProjectStore.open(tmp, "Camp").worldMap(m.id).base().get(3, 3));
    }

    @Test
    void worldMapsAreItemsInTheTreeAndCanBeRenamedDuplicatedSearchedAndDeleted(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        WorldMap m = store.createWorldMap(store.project.root, new WorldMap("Aldoria", 64, 48, 640, WorldStyle.FANTASY_COLOUR));
        m.base().set(1, 1, WorldTerrain.OCEAN.code);
        TreeNode node = store.project.root.find(m.id);
        assertTrue(node.isWorldMap());
        assertFalse(node.isBoard());
        assertTrue(store.itemExists(m.id));
        assertEquals(List.of(m.id), store.allWorldMapIds());

        store.renameNode(node, "Aldoria Prime");
        assertEquals("Aldoria Prime", store.worldMap(m.id).name);
        assertEquals("World map", SearchService.search(store, "prime", 10).get(0).snippet);

        WorldMap copy = store.duplicateWorldMap(node);
        assertEquals("Aldoria Prime (copy)", copy.name);
        assertNotEquals(m.id, copy.id);
        assertEquals(WorldTerrain.OCEAN.code, copy.base().get(1, 1));
        copy.base().set(1, 1, WorldTerrain.LAND.code);
        assertEquals(WorldTerrain.OCEAN.code, m.base().get(1, 1), "the copy has its own paint");

        store.flush();
        store.deleteNode(node);
        assertNull(store.worldMap(m.id));
        assertFalse(Files.exists(store.dir.resolve("worldmaps").resolve(m.id + ".worldmap")));
        assertEquals(List.of(copy.id), store.allWorldMapIds());
    }

    @Test
    void everyStyleHasAColourForEveryBiomeAndTheScaleAddsUp() {
        for (WorldStyle s : WorldStyle.values()) {
            WorldPalette p = WorldPalette.of(s);
            for (WorldBiome b : WorldBiome.values()) assertNotEquals(0, p.biomeColor(b), s + " " + b);
            assertNotEquals(0, p.oceanDeep);
            assertNotEquals(0, p.ink);
        }
        assertEquals(WorldBiome.values().length, new java.util.HashSet<>(java.util.Arrays.stream(WorldBiome.values()).map(b -> b.code).toList()).size());
        WorldMap m = new WorldMap("x", 1024, 768, 2048, WorldStyle.FANTASY_COLOUR);
        assertEquals(2.0, m.milesPerCell, 1e-9);
        assertEquals(1536.0, m.milesDown(), 1e-9);
    }
}
