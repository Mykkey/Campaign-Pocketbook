package dnd;

import dnd.model.MagicItem;
import dnd.model.TreeNode;
import dnd.search.SearchService;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MagicItemTest {
    private static MagicItem sample() {
        MagicItem m = new MagicItem();
        m.name = "Cloak of Quiet Steps";
        m.type = "Wondrous item";
        m.rarity = "Rare";
        m.attunement = true;
        m.attunementNote = "by a rogue";
        m.description = "While worn you have advantage on Dexterity (Stealth) checks.";
        m.charges = 3;
        m.recharge = "Dawn";
        m.curse = "You cannot speak above a whisper.";
        m.value = "2,500";
        m.notes = "Stolen from the guild vault.";
        return m;
    }

    @Test
    void anItemIsSavedAndLoadedAgain(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        MagicItem made = store.createMagicItem(store.project.root, sample());
        store.flush();
        assertTrue(Files.isRegularFile(store.dir.resolve("items").resolve(made.id + ".item")));

        ProjectStore reopened = ProjectStore.open(tmp, "Camp");
        MagicItem back = reopened.magicItem(made.id);
        assertNotNull(back);
        assertEquals(made.id, back.id);
        assertEquals("Cloak of Quiet Steps", back.name);
        assertEquals("Rare", back.rarity);
        assertTrue(back.attunement);
        assertEquals(3, back.charges);
        assertTrue(reopened.isMagicItem(made.id));
        assertEquals(List.of(made.id), reopened.allMagicItemIds());
    }

    @Test
    void itemsAreNotWhiteboardsAndCanBeRenamedDuplicatedAndDeleted(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        MagicItem made = store.createMagicItem(store.project.root, sample());
        TreeNode node = store.project.root.find(made.id);
        assertTrue(node.isMagicItem());
        assertFalse(node.isBoard());
        assertFalse(store.boardExists(made.id));
        assertTrue(store.itemExists(made.id));

        store.renameNode(node, "Cloak of Loud Steps");
        assertEquals("Cloak of Loud Steps", store.magicItem(made.id).name);

        MagicItem copy = store.duplicateMagicItem(node);
        assertEquals("Cloak of Loud Steps (copy)", copy.name);
        assertNotEquals(made.id, copy.id);
        assertEquals(2, store.allMagicItemIds().size());

        store.flush();
        store.deleteNode(node);
        assertNull(store.magicItem(made.id));
        assertFalse(Files.exists(store.dir.resolve("items").resolve(made.id + ".item")));
        assertEquals(List.of(copy.id), store.allMagicItemIds());
    }

    @Test
    void subtitleAndTextDescribeTheItem() {
        MagicItem m = sample();
        assertEquals("Wondrous item, rare (requires attunement by a rogue)", m.subtitle());
        String text = m.toText();
        assertTrue(text.startsWith("Cloak of Quiet Steps\nWondrous item, rare"));
        assertTrue(text.contains("Charges: 3 (recharges dawn)"));
        assertTrue(text.contains("Curse: You cannot speak above a whisper."));
        assertTrue(text.contains("Value: 2,500 gp"));
        MagicItem plain = new MagicItem();
        plain.name = "Bead";
        plain.rarity = "";
        assertEquals("Wondrous item", plain.subtitle());
    }

    @Test
    void searchFindsItemsByNameAndByWhatIsWrittenOnThem(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        store.createMagicItem(store.project.root, sample());

        List<SearchService.Result> byName = SearchService.search(store, "quiet steps", 10);
        assertEquals(1, byName.size());
        assertEquals("Magic item", byName.get(0).snippet);
        List<SearchService.Result> byText = SearchService.search(store, "advantage on dexterity", 10);
        assertEquals(1, byText.size());
        assertTrue(byText.get(0).snippet.startsWith("Magic item - text:"), byText.get(0).snippet);
        assertTrue(SearchService.search(store, "whisper", 10).get(0).snippet.contains("curse:"));
        assertTrue(SearchService.search(store, "guild vault", 10).get(0).snippet.contains("notes:"));
    }

    @Test
    void rarityColoursAreDistinctAndUnknownRaritiesFallBack() {
        java.util.Set<String> colours = new java.util.HashSet<>();
        for (String r : MagicItem.RARITIES) colours.add(dnd.ui.MagicItemCardView.rarityColor(r));
        assertEquals(MagicItem.RARITIES.size(), colours.size());
        assertEquals(dnd.ui.MagicItemCardView.rarityColor("Common"), dnd.ui.MagicItemCardView.rarityColor("homebrew"));
    }
}
