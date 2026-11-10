package dnd;

import dnd.map.world.WorldGenerator;
import dnd.map.world.WorldGenerator.Climate;
import dnd.map.world.WorldGenerator.Options;
import dnd.map.world.WorldLabels;
import dnd.names.PlaceNameGenerator;
import dnd.model.WorldBiome;
import dnd.model.WorldMap;
import dnd.model.WorldPath;
import dnd.model.WorldStamp;
import dnd.model.WorldStyle;
import dnd.model.WorldTerrain;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WorldGeneratorTest {
    private static WorldMap blank() {
        return new WorldMap("Gen", 384, 288, 1920, WorldStyle.FANTASY_COLOUR);
    }

    private static WorldMap make(Options o) {
        WorldMap m = blank();
        WorldGenerator.generate(m, o);
        return m;
    }

    private static int count(byte[] cells, byte value) {
        int n = 0;
        for (byte b : cells) if (b == value) n++;
        return n;
    }

    @Test
    void theSameSeedGivesTheSameWorldAndAnotherSeedADifferentOne() {
        WorldMap a = make(Options.defaults(42)), b = make(Options.defaults(42)), c = make(Options.defaults(43));
        assertArrayEquals(a.base().cells, b.base().cells);
        assertArrayEquals(a.biome().cells, b.biome().cells);
        assertArrayEquals(a.elevation().cells, b.elevation().cells);
        assertEquals(a.stamps.size(), b.stamps.size());
        assertEquals(a.paths.size(), b.paths.size());
        for (int i = 0; i < a.stamps.size(); i++) {
            assertEquals(a.stamps.get(i).feature, b.stamps.get(i).feature);
            assertEquals(a.stamps.get(i).x, b.stamps.get(i).x, 0);
            assertEquals(a.stamps.get(i).text, b.stamps.get(i).text);
        }
        assertFalse(java.util.Arrays.equals(a.base().cells, c.base().cells));
    }

    @Test
    void theAmountOfLandFollowsTheOption() {
        for (double want : new double[]{0.25, 0.5, 0.7}) {
            WorldMap m = make(new Options(7, want, 0.5, Climate.TEMPERATE, false, false, false));
            double land = 1 - count(m.base().cells, WorldTerrain.OCEAN.code) / (double) m.base().cells.length;
            assertEquals(want, land, 0.04, "wanted " + want);
        }
    }

    @Test
    void biomesAndHeightsOnlyAppearOnLand() {
        WorldMap m = make(Options.defaults(11));
        for (int i = 0; i < m.base().cells.length; i++) {
            byte b = m.base().cells[i];
            if (m.biome().cells[i] != 0) assertTrue(b == WorldTerrain.LAND.code || b == WorldTerrain.ICE.code, "biome on " + b);
            if (b == WorldTerrain.OCEAN.code) assertEquals(0, m.elevation().cells[i], "height under the sea");
        }
        assertFalse(m.biome().isEmpty());
        assertFalse(m.elevation().isEmpty());
        assertTrue(count(m.base().cells, WorldTerrain.OCEAN.code) > 0);
        assertTrue(count(m.base().cells, WorldTerrain.LAND.code) > 0);
    }

    @Test
    void moreMountainsMeanMoreHighGround() {
        WorldMap flat = make(new Options(5, 0.4, 0.0, Climate.TEMPERATE, false, false, false));
        WorldMap rugged = make(new Options(5, 0.4, 1.0, Climate.TEMPERATE, false, false, false));
        int high = 0, highFlat = 0;
        for (byte b : rugged.elevation().cells) if ((b & 0xFF) > 170) high++;
        for (byte b : flat.elevation().cells) if ((b & 0xFF) > 170) highFlat++;
        assertTrue(high > highFlat * 2 + 20, high + " vs " + highFlat);
    }

    @Test
    void coldWorldsHaveMoreSnowAndWarmOnesMoreJungleAndDesert() {
        WorldMap cold = make(new Options(9, 0.4, 0.3, Climate.COLD, false, false, false));
        WorldMap warm = make(new Options(9, 0.4, 0.3, Climate.WARM, false, false, false));
        int coldWhite = count(cold.biome().cells, WorldBiome.SNOW.code) + count(cold.biome().cells, WorldBiome.TUNDRA.code);
        int warmWhite = count(warm.biome().cells, WorldBiome.SNOW.code) + count(warm.biome().cells, WorldBiome.TUNDRA.code);
        assertTrue(coldWhite > warmWhite * 2, coldWhite + " vs " + warmWhite);
        int warmHot = count(warm.biome().cells, WorldBiome.JUNGLE.code) + count(warm.biome().cells, WorldBiome.SAVANNA.code)
                + count(warm.biome().cells, WorldBiome.DUNES.code) + count(warm.biome().cells, WorldBiome.SAND.code);
        int coldHot = count(cold.biome().cells, WorldBiome.JUNGLE.code) + count(cold.biome().cells, WorldBiome.SAVANNA.code)
                + count(cold.biome().cells, WorldBiome.DUNES.code);
        assertTrue(warmHot > coldHot, warmHot + " vs " + coldHot);
    }

    @Test
    void riversStartHighAndFlowDownhillToWater() {
        WorldMap m = make(Options.defaults(42));
        assertFalse(m.paths.isEmpty(), "a default world has rivers");
        int reachedWater = 0;
        for (WorldPath p : m.paths) {
            assertEquals(WorldPath.Type.RIVER, p.type);
            assertTrue(p.count() >= 2);
            int sx = (int) p.x(0), sy = (int) p.y(0), ex = (int) Math.min(m.cols - 1, p.x(p.count() - 1)), ey = (int) Math.min(m.rows - 1, p.y(p.count() - 1));
            int startElev = m.elevation().get(sx, sy) & 0xFF, endElev = m.elevation().get(ex, ey) & 0xFF;
            assertTrue(startElev >= endElev, "the mouth is not higher than the spring: " + startElev + " vs " + endElev);
            assertTrue(startElev > 100, "springs are in the hills " + startElev);
            byte end = m.base().get(ex, ey);
            if (end == WorldTerrain.OCEAN.code || end == WorldTerrain.LAKE.code) reachedWater++;
            for (int i = 0; i < p.count(); i++) {
                assertTrue(p.x(i) >= 0 && p.x(i) <= m.cols && p.y(i) >= 0 && p.y(i) <= m.rows);
            }
        }
        assertTrue(reachedWater > 0, "at least one river reaches the sea or a lake");
    }

    @Test
    void symbolsStandOnLandInsideTheMapAndSettlementsExist() {
        WorldMap m = make(Options.defaults(21));
        int settlements = 0, trees = 0, mountains = 0;
        for (WorldStamp s : m.stamps) {
            assertTrue(s.x >= 0 && s.x < m.cols && s.y >= 0 && s.y < m.rows, "inside the map");
            if (WorldLabels.isLabel(s)) continue;
            assertEquals(WorldTerrain.LAND.code, m.base().get((int) s.x, (int) s.y), s.feature + " on land");
            assertTrue(s.size > 0);
            if (s.feature.startsWith("settlement-")) settlements++;
            if (s.feature.contains("tree") || s.feature.contains("cluster")) trees++;
            if (s.feature.startsWith("peak") || s.feature.startsWith("range")) mountains++;
        }
        assertTrue(settlements >= 3, "settlements " + settlements);
        assertTrue(trees > 20, "trees " + trees);
        assertTrue(mountains > 3, "mountains " + mountains);
        assertTrue(m.stamps.size() < 3600);
    }

    @Test
    void labelsAreNamedAndNotRepeated() {
        WorldMap m = make(Options.defaults(21));
        int labels = 0;
        Set<String> kinds = new HashSet<>();
        for (WorldStamp s : m.stamps) {
            if (!WorldLabels.isLabel(s)) continue;
            labels++;
            assertNotNull(s.text);
            assertFalse(s.text.isBlank());
            kinds.add(s.labelKind);
        }
        assertTrue(labels >= 6, "labels " + labels);
        assertTrue(kinds.size() >= 3, "kinds of label " + kinds);
        long distinct = m.stamps.stream().filter(WorldLabels::isLabel).map(s -> s.text).distinct().count();
        assertEquals(labels, distinct, "no name is used twice");
    }

    @Test
    void theOptionsSwitchTheExtrasOff() {
        WorldMap m = make(new Options(3, 0.4, 0.5, Climate.TEMPERATE, false, false, false));
        assertTrue(m.stamps.isEmpty());
        assertTrue(m.paths.isEmpty());
        WorldMap noNames = make(new Options(3, 0.4, 0.5, Climate.TEMPERATE, true, true, false));
        assertTrue(noNames.stamps.stream().noneMatch(WorldLabels::isLabel));
        assertFalse(noNames.stamps.isEmpty());
        assertTrue(noNames.paths.stream().allMatch(p -> p.name == null));
    }

    @Test
    void generatingReplacesWhatWasThereAndKeepsTheMapsSize() {
        WorldMap m = blank();
        m.stamps.add(new WorldStamp("tree-oak", 5, 5));
        m.paths.add(new WorldPath(WorldPath.Type.ROAD));
        m.base().set(1, 1, WorldTerrain.LAVA.code);
        WorldGenerator.generate(m, new Options(3, 0.4, 0.5, Climate.TEMPERATE, false, false, false));
        assertEquals(384, m.cols);
        assertEquals(288, m.rows);
        assertEquals(384, m.base().w);
        assertEquals(384 / 4, m.reveal().w);
        assertTrue(m.stamps.isEmpty());
        assertTrue(m.paths.isEmpty());
        assertNotEquals(WorldTerrain.LAVA.code, m.base().get(1, 1));
    }

    @Test
    void placeNamesAreRepeatableAndUnique() {
        PlaceNameGenerator a = new PlaceNameGenerator(1), b = new PlaceNameGenerator(1);
        Set<String> all = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String t = a.town();
            assertEquals(t, b.town());
            assertTrue(all.add(t), "repeated " + t);
            assertFalse(t.isBlank());
        }
        assertTrue(a.region().length() > 3);
        assertTrue(a.sea().length() > 3);
        assertTrue(a.river().length() > 3);
        assertTrue(a.mountains().length() > 3);
    }
}
