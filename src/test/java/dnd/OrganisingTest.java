package dnd;

import dnd.model.RecentItems;
import dnd.model.Card;
import dnd.model.TreeNode;
import dnd.storage.ProjectStore;
import dnd.ui.NodeColors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OrganisingTest {
    // ---------------------------------------------------------------- colours

    @Test
    void paletteColoursAreValidAndDistinct() {
        assertEquals(10, NodeColors.PALETTE.size());
        Set<String> hexes = new HashSet<>(), names = new HashSet<>();
        for (NodeColors.Swatch s : NodeColors.PALETTE) {
            assertTrue(NodeColors.isValid(s.hex()), s.name());
            assertTrue(hexes.add(s.hex().toLowerCase()), "duplicate colour " + s.hex());
            assertTrue(names.add(s.name()), "duplicate name " + s.name());
        }
    }

    @Test
    void eachKindHasItsOwnDefaultColour() {
        TreeNode folder = new TreeNode("F", true);
        TreeNode board = new TreeNode("B", false);
        TreeNode map = new TreeNode("M", false);
        map.kind = TreeNode.KIND_BATTLEMAP;
        TreeNode card = new TreeNode("C", false);
        card.kind = TreeNode.KIND_CARD;
        TreeNode enc = new TreeNode("E", false);
        enc.kind = TreeNode.KIND_ENCOUNTER;
        Set<String> seen = new HashSet<>();
        for (TreeNode n : List.of(folder, board, map, card, enc)) {
            String c = NodeColors.colorFor(n);
            assertTrue(NodeColors.isValid(c));
            assertTrue(seen.add(c), "kinds should be told apart by colour: " + c);
        }
    }

    @Test
    void aChosenColourWinsAndABadOneIsIgnored() {
        TreeNode card = new TreeNode("C", false);
        card.kind = TreeNode.KIND_CARD;
        card.color = "#3b82f6";
        assertEquals("#3b82f6", NodeColors.colorFor(card));
        card.color = "not a colour";
        assertEquals(NodeColors.CARD, NodeColors.colorFor(card));
        card.color = null;
        assertEquals(NodeColors.CARD, NodeColors.colorFor(card));
        assertEquals("Blue", NodeColors.nameOf("#3B82F6"));
        assertNull(NodeColors.nameOf("#123456"));
    }

    @Test
    void coloursSurviveSavingAndReopening(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        TreeNode folder = store.createFolder(store.project.root, "NPCs");
        folder.color = "#8b5cf6";
        Card card = store.createCard(folder, new Card());
        store.project.root.find(card.id).color = "#e5484d";
        store.markProjectDirty();
        store.flush();

        ProjectStore again = ProjectStore.open(tmp, "Camp");
        assertEquals("#8b5cf6", again.project.root.find(folder.id).color);
        assertEquals("#e5484d", again.project.root.find(card.id).color);
        assertNull(again.project.root.find(again.allBoardIds().get(0)).color, "untouched items keep the default");
    }

    @Test
    void oldProjectsWithoutColoursStillOpen(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Old");
        store.flush();
        Path json = tmp.resolve("Old").resolve("project.json");
        String text = java.nio.file.Files.readString(json);
        assertFalse(text.contains("\"color\":\"#"), "no colour was ever set");
        ProjectStore again = ProjectStore.open(tmp, "Old");
        assertNull(again.project.root.color);
        assertEquals(NodeColors.FOLDER, NodeColors.colorFor(again.project.root));
    }

    // ---------------------------------------------------------------- folder counts

    @Test
    void countItemsCountsNestedItemsButNotFolders(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        TreeNode root = store.project.root;
        int start = root.countItems(); // the starter whiteboard
        TreeNode a = store.createFolder(root, "A");
        TreeNode b = store.createFolder(a, "B");
        assertEquals(0, a.countItems());
        store.createCard(a, new Card());
        store.createCard(b, new Card());
        store.createEncounter(b, "Fight");
        assertEquals(1, b.countItems() - 1);
        assertEquals(2, b.countItems());
        assertEquals(3, a.countItems(), "nested items count, folders do not");
        assertEquals(start + 3, root.countItems());
    }

    // ---------------------------------------------------------------- recent

    @Test
    void recentIsNewestFirstWithoutRepeatsAndCapped() {
        List<String> recent = new ArrayList<>();
        for (int i = 1; i <= 12; i++) RecentItems.add(recent, "item" + i);
        assertEquals(RecentItems.MAX, recent.size());
        assertEquals("item12", recent.get(0));
        assertEquals("item3", recent.get(RecentItems.MAX - 1));

        RecentItems.add(recent, "item5");
        assertEquals("item5", recent.get(0));
        assertEquals(RecentItems.MAX, recent.size());
        assertEquals(1, recent.stream().filter("item5"::equals).count());

        RecentItems.add(recent, null);
        assertEquals(RecentItems.MAX, recent.size());
    }

    @Test
    void recentSkipsItemsThatNoLongerExist() {
        List<String> recent = new ArrayList<>(List.of("a", "b", "c", "d"));
        List<String> shown = RecentItems.existing(recent, id -> !id.equals("b") && !id.equals("d"));
        assertEquals(List.of("a", "c"), shown);
        assertEquals(4, recent.size(), "the stored list is not changed");
    }

    @Test
    void recentIsSavedWithTheProject(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        RecentItems.add(store.project.recent, "x");
        RecentItems.add(store.project.recent, "y");
        store.markProjectDirty();
        store.flush();
        assertEquals(List.of("y", "x"), ProjectStore.open(tmp, "Camp").project.recent);
    }
}