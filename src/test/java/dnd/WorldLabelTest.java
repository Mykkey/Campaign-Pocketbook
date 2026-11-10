package dnd;

import dnd.map.world.WorldAssets;
import dnd.map.world.WorldLabels;
import dnd.model.WorldMap;
import dnd.model.WorldObjects;
import dnd.model.WorldStamp;
import dnd.model.WorldStyle;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorldLabelTest {
    @Test
    void settlementsDecorationsAndBannersAreInTheLibrary() {
        assertTrue(WorldAssets.categories().containsAll(List.of("Settlements", "Decorations", "Banners")));
        for (String id : List.of("settlement-cottage", "settlement-capital", "settlement-elven", "settlement-dwarven", "settlement-orc",
                "deco-compass-rose", "deco-kraken", "deco-sea-serpent", "banner-scroll", "banner-ribbon")) {
            assertNotNull(WorldAssets.get(id), id);
            for (WorldStyle style : WorldStyle.values()) {
                assertNotNull(WorldLabelTest.class.getResource("/assets/" + WorldAssets.assetId(style, id) + ".svg"), id + " in " + style);
            }
        }
        assertTrue(WorldAssets.get("settlement-cottage").w() < WorldAssets.get("settlement-capital").w(), "a cottage is smaller than a capital");
        assertTrue(WorldAssets.get("deco-compass-rose").centered());
    }

    @Test
    void labelsAreRecognisedAndKeepTheirKindAndBendWhenSavedCopiedAndRestored(@TempDir Path tmp) throws Exception {
        WorldStamp label = new WorldStamp(WorldLabels.FEATURE, 10, 20);
        label.text = "Sea of Storms";
        label.labelKind = WorldLabels.Kind.SEA.name();
        label.bend = -0.2;
        WorldStamp banner = new WorldStamp("banner-scroll", 30, 40);
        banner.text = "Realm";
        WorldStamp tree = new WorldStamp("tree-oak", 5, 5);
        assertTrue(WorldLabels.isLabel(label));
        assertTrue(WorldLabels.hasText(label));
        assertTrue(WorldLabels.isBanner(banner));
        assertTrue(WorldLabels.hasText(banner));
        assertFalse(WorldLabels.hasText(tree));
        assertEquals(WorldLabels.Kind.SEA, WorldLabels.kindOf(label));
        assertEquals(WorldLabels.Kind.TOWN, WorldLabels.kindOf(tree), "no kind means a plain place name");
        assertEquals(WorldLabels.Kind.SEA.size, WorldLabels.sizeOf(label), 1e-9);
        label.size = 7;
        assertEquals(7, WorldLabels.sizeOf(label), 1e-9);

        WorldStamp copy = label.copy();
        assertEquals("SEA", copy.labelKind);
        assertEquals(-0.2, copy.bend, 1e-9);

        ProjectStore store = ProjectStore.create(tmp, "Camp");
        WorldMap m = store.createWorldMap(store.project.root, new WorldMap("W", 64, 48, 640, WorldStyle.FANTASY_COLOUR));
        m.stamps.add(label);
        m.stamps.add(banner);
        String snapshot = WorldObjects.capture(m);
        store.markWorldMapDirty(m, true);
        store.flush();
        WorldMap back = ProjectStore.open(tmp, "Camp").worldMap(m.id);
        WorldStamp l2 = back.stamps.stream().filter(WorldLabels::isLabel).findFirst().orElseThrow();
        assertEquals("Sea of Storms", l2.text);
        assertEquals("SEA", l2.labelKind);
        assertEquals(-0.2, l2.bend, 1e-9);
        assertEquals(7, l2.size, 1e-9);
        m.stamps.clear();
        WorldObjects.restore(m, snapshot);
        assertEquals(2, m.stamps.size());
        assertEquals("Realm", m.stamps.stream().filter(WorldLabels::isBanner).findFirst().orElseThrow().text);
    }

    @Test
    void textIsDrawnAndPickedAboveSymbolsEvenWhenItIsFurtherUpTheMap() {
        WorldMap m = new WorldMap("Z", 200, 150, 1000, WorldStyle.FANTASY_COLOUR);
        WorldStamp label = new WorldStamp("label", 50, 40);
        label.text = "The Spine";
        WorldStamp mountain = new WorldStamp("peak-single", 50, 60);
        mountain.size = 40;
        WorldStamp banner = new WorldStamp("banner-scroll", 120, 20);
        banner.text = "Realm";
        WorldStamp tree = new WorldStamp("tree-oak", 120, 100);
        m.stamps.addAll(List.of(mountain, label, tree, banner));
        List<WorldStamp> order = dnd.map.world.WorldStampRenderer.inOrder(m);
        assertEquals(List.of(mountain, tree), order.subList(0, 2).stream().sorted(java.util.Comparator.comparingDouble(s -> s.y)).toList());
        assertSame(banner, order.get(2), "text comes after every symbol, though it is higher up the map");
        assertSame(label, order.get(3));
        // the label sits on the mountain's picture, and a click there picks the label first
        assertSame(label, dnd.map.world.WorldStampRenderer.hit(m, 50, 38, true));
    }
}
