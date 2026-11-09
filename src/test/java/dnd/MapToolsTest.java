package dnd;

import dnd.map.Grid;
import dnd.map.LightPresets;
import dnd.map.LightingModel;
import dnd.map.LightingModel.Level;
import dnd.model.AreaTemplate;
import dnd.model.AreaTemplate.Shape;
import dnd.model.BattleMap;
import dnd.model.MapLight;
import dnd.model.MapPin;
import dnd.model.MapToken;
import dnd.storage.Json;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MapToolsTest {
    private static BattleMap square() {
        BattleMap m = new BattleMap();
        m.cols = 20;
        m.rows = 20;
        m.cellSize = 50;
        return m;
    }

    private static Set<String> cells(List<int[]> cs) {
        Set<String> out = new HashSet<>();
        for (int[] c : cs) out.add(c[0] + "," + c[1]);
        return out;
    }

    private static AreaTemplate at(Grid g, Shape s, int col, int row, int feet, double angle) {
        double[] c = g.center(col, row);
        AreaTemplate t = new AreaTemplate(s, c[0], c[1], feet);
        t.angle = angle;
        return t;
    }

    // ---------------------------------------------------------------- area templates

    @Test
    void aSmallBurstCoversThePlusShape() {
        BattleMap m = square();
        Grid g = Grid.of(m);
        Set<String> got = cells(at(g, Shape.CIRCLE, 5, 5, 5, 0).coveredCells(g, m.cellSize));
        assertEquals(Set.of("5,5", "4,5", "6,5", "5,4", "5,6"), got);
    }

    @Test
    void aTwentyFootRadiusCovers49Cells() {
        BattleMap m = square();
        Grid g = Grid.of(m);
        assertEquals(49, at(g, Shape.CIRCLE, 10, 10, 20, 0).coveredCells(g, m.cellSize).size());
    }

    @Test
    void aConeIsNarrowNearTheOriginAndWideAtTheFarEnd() {
        BattleMap m = square();
        Grid g = Grid.of(m);
        Set<String> got = cells(at(g, Shape.CONE, 5, 5, 15, 0).coveredCells(g, m.cellSize)); // pointing right
        assertEquals(Set.of("5,5", "6,5", "7,4", "7,5", "7,6", "8,5"), got);
        // pointing down covers the mirror image
        Set<String> down = cells(at(g, Shape.CONE, 5, 5, 15, Math.PI / 2).coveredCells(g, m.cellSize));
        assertEquals(Set.of("5,5", "5,6", "4,7", "5,7", "6,7", "5,8"), down);
    }

    @Test
    void aLineIsOneCellWideUnlessMadeWider() {
        BattleMap m = square();
        Grid g = Grid.of(m);
        AreaTemplate line = at(g, Shape.LINE, 2, 2, 30, 0);
        assertEquals(7, line.coveredCells(g, m.cellSize).size(), "the origin cell plus six more");
        assertTrue(cells(line.coveredCells(g, m.cellSize)).contains("8,2"));
        assertFalse(cells(line.coveredCells(g, m.cellSize)).contains("9,2"));
        line.widthFeet = 15;
        assertEquals(21, line.coveredCells(g, m.cellSize).size(), "three cells wide");
    }

    @Test
    void aCubeExtendsFromItsOriginFace() {
        BattleMap m = square();
        Grid g = Grid.of(m);
        Set<String> got = cells(at(g, Shape.CUBE, 5, 5, 10, 0).coveredCells(g, m.cellSize));
        assertEquals(9, got.size());
        assertTrue(got.contains("5,4") && got.contains("7,6") && !got.contains("4,5") && !got.contains("8,5"));
    }

    @Test
    void hexGridsCoverTheSevenCellFlower() {
        BattleMap m = square();
        m.gridType = BattleMap.GridType.HEX;
        Grid g = Grid.of(m);
        assertEquals(7, at(g, Shape.CIRCLE, 5, 5, 5, 0).coveredCells(g, m.cellSize).size());
    }

    @Test
    void outlinesAndLabelsMatchTheShape() {
        BattleMap m = square();
        Grid g = Grid.of(m);
        assertNull(at(g, Shape.CIRCLE, 5, 5, 20, 0).outline(m.cellSize));
        assertEquals(8, at(g, Shape.CUBE, 5, 5, 10, 0).outline(m.cellSize).length);
        assertEquals(2 + 2 * 13, at(g, Shape.CONE, 5, 5, 15, 0).outline(m.cellSize).length);
        AreaTemplate cone = at(g, Shape.CONE, 5, 5, 30, 0);
        assertEquals("30 ft cone: 4 cells", cone.describe(4));
        assertEquals("1 cell", at(g, Shape.CIRCLE, 5, 5, 5, 0).describe(1).substring(at(g, Shape.CIRCLE, 5, 5, 5, 0).describe(1).indexOf(": ") + 2));
        assertTrue(cone.copy().contains(cone.ox + 100, cone.oy, m.cellSize));
    }

    @Test
    void theTemplateIsNeverSaved(@TempDir Path tmp) throws Exception {
        BattleMap m = square();
        m.template = new AreaTemplate(Shape.CONE, 10, 10, 30);
        assertFalse(Json.write(m).contains("template"));
    }

    // ---------------------------------------------------------------- ruler

    @Test
    void theRulerMeasuresInCellsAndFeet() {
        BattleMap m = square();
        Grid g = Grid.of(m);
        double[] a = g.center(2, 2), b = g.center(7, 2);
        BattleMap.Ruler r = new BattleMap.Ruler(a[0], a[1], false);
        assertEquals(0, r.cells(g));
        r.bx = b[0];
        r.by = b[1];
        assertEquals(5, r.cells(g));
        assertFalse(r.shared);
        r.bx = -500;
        assertEquals(-1, r.cells(g), "an end off the grid has no distance");
        assertFalse(Json.write(m).contains("ruler"), "the ruler is never saved");
    }

    // ---------------------------------------------------------------- lighting

    private static MapLight torchAt(BattleMap m, Grid g, int col, int row) {
        MapLight l = new MapLight("Torch", 20, 40);
        double[] c = g.center(col, row);
        l.x = c[0];
        l.y = c[1];
        m.lights.add(l);
        return l;
    }

    @Test
    void aTorchLightsBrightThenDimThenNothing() {
        BattleMap m = square();
        Grid g = Grid.of(m);
        torchAt(m, g, 5, 5);
        assertEquals(Level.BRIGHT, LightingModel.levelAt(m, g, 5, 5));
        assertEquals(Level.BRIGHT, LightingModel.levelAt(m, g, 9, 5), "4 cells = 20 ft");
        assertEquals(Level.DIM, LightingModel.levelAt(m, g, 10, 5), "5 cells = 25 ft");
        assertEquals(Level.DIM, LightingModel.levelAt(m, g, 13, 5), "8 cells = 40 ft");
        assertEquals(Level.DARK, LightingModel.levelAt(m, g, 14, 5), "9 cells = 45 ft");
        assertEquals(Level.DARK, LightingModel.levelAt(m, g, 0, 19));
    }

    @Test
    void darkvisionSeesDarknessAsDimAndDimAsBright() {
        BattleMap m = square();
        Grid g = Grid.of(m);
        MapToken elf = new MapToken();
        elf.col = 2;
        elf.row = 2;
        elf.visionFeet = 60;
        m.tokens.add(elf);
        assertEquals(Level.DIM, LightingModel.levelAt(m, g, 2, 2), "no light: darkvision gives dim");
        assertEquals(Level.DIM, LightingModel.levelAt(m, g, 14, 2), "12 cells = 60 ft");
        assertEquals(Level.DARK, LightingModel.levelAt(m, g, 15, 2), "beyond its range");

        MapLight l = torchAt(m, g, 8, 2); // dim at 25-40 ft from (8,2): cells 13..16
        assertEquals(Level.BRIGHT, LightingModel.levelAt(m, g, 14, 2), "dim light + darkvision = bright");
        assertEquals(Level.DIM, LightingModel.levelAt(m, g, 16, 2), "dim torch light, but beyond the elf's darkvision");
        assertNotNull(l);
    }

    @Test
    void aCarriedLightFollowsItsToken() {
        BattleMap m = square();
        Grid g = Grid.of(m);
        MapToken carrier = new MapToken();
        carrier.col = 3;
        carrier.row = 3;
        m.tokens.add(carrier);
        MapLight l = new MapLight("Lantern", 30, 60);
        l.tokenId = carrier.id;
        m.lights.add(l);
        assertEquals(Level.BRIGHT, LightingModel.levelAt(m, g, 3, 8));
        carrier.col = 15;
        carrier.row = 15;
        assertEquals(Level.DARK, LightingModel.levelAt(m, g, 3, 8));
        assertEquals(Level.BRIGHT, LightingModel.levelAt(m, g, 15, 20 - 1));
        m.tokens.clear();
        assertNull(LightingModel.position(m, g, l), "a light whose token is gone has no position");
        assertEquals(Level.DARK, LightingModel.levelAt(m, g, 15, 15));
    }

    @Test
    void sightForClearingFogCombinesRadiusVisionAndCarriedLight() {
        BattleMap m = square();
        MapToken t = new MapToken();
        t.revealRadius = 2;
        t.visionFeet = 60;
        m.tokens.add(t);
        MapLight l = new MapLight("Torch", 20, 40);
        l.tokenId = t.id;
        m.lights.add(l);
        assertEquals(2, LightingModel.sightCells(m, t), "a normal map ignores vision and light");
        m.darkMap = true;
        assertEquals(12, LightingModel.sightCells(m, t), "60 ft of darkvision = 12 cells");
        t.visionFeet = 0;
        assertEquals(8, LightingModel.sightCells(m, t), "the torch's dim light reaches 40 ft = 8 cells");
        l.tokenId = null;
        assertEquals(2, LightingModel.sightCells(m, t));
    }

    @Test
    void lightPresetsAreSensible() {
        assertEquals(6, LightPresets.ALL.size());
        for (LightPresets.Preset p : LightPresets.ALL) assertTrue(p.dimFeet() >= p.brightFeet() && p.brightFeet() > 0, p.name());
        assertEquals(20, LightPresets.find("torch").brightFeet());
        assertNull(LightPresets.find("sunbeam"));
    }

    // ---------------------------------------------------------------- saving

    @Test
    void pinsLightsAndDarknessAreSaved(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        BattleMap m = store.createBattleMap(store.project.root, "Crypt", BattleMap.GridType.SQUARE, 10, 10, 50);
        MapPin pin = new MapPin(120, 80, "Pressure plate: poison darts");
        pin.link = store.allBoardIds().get(0);
        m.pins.add(pin);
        MapLight l = new MapLight("Torch", 20, 40);
        l.x = 60;
        l.y = 60;
        m.lights.add(l);
        MapToken t = new MapToken();
        t.visionFeet = 60;
        m.tokens.add(t);
        m.darkMap = true;
        store.markMapDirty(m);
        store.flush();

        BattleMap loaded = ProjectStore.open(tmp, "Camp").battleMap(m.id);
        assertTrue(loaded.darkMap);
        assertEquals(1, loaded.pins.size());
        assertEquals("Pressure plate: poison darts", loaded.pins.get(0).text);
        assertEquals(120, loaded.pins.get(0).x, 1e-9);
        assertEquals(pin.link, loaded.pins.get(0).link);
        assertEquals(1, loaded.lights.size());
        assertEquals(40, loaded.lights.get(0).dimFeet);
        assertEquals(60, loaded.tokens.get(0).visionFeet);
        assertNull(loaded.template);
        assertEquals(60, t.copy().visionFeet);
    }

    @Test
    void oldMapsWithoutPinsOrLightsStillOpen(@TempDir Path tmp) throws Exception {
        BattleMap m = Json.read("{\"name\":\"Old\",\"cols\":5,\"rows\":5}", BattleMap.class);
        assertTrue(m.pins.isEmpty());
        assertTrue(m.lights.isEmpty());
        assertFalse(m.darkMap);
    }
}