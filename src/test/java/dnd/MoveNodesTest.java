package dnd;

import dnd.model.Card;
import dnd.model.TreeNode;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MoveNodesTest {
    private static Card card(ProjectStore s, TreeNode parent, String name) {
        Card c = new Card();
        c.name = name;
        return s.createCard(parent, c);
    }

    private static List<String> names(TreeNode folder) {
        return folder.children.stream().map(n -> n.name).toList();
    }

    @Test
    void severalItemsMoveIntoAFolderTogetherInTheOrderGiven(@TempDir Path tmp) throws Exception {
        ProjectStore s = ProjectStore.create(tmp, "Camp");
        TreeNode root = s.project.root;
        Card a = card(s, root, "A"), b = card(s, root, "B"), c = card(s, root, "C"), d = card(s, root, "D");
        TreeNode folder = s.createFolder(root, "Cast");
        assertEquals(3, s.moveNodesTo(List.of(c.id, a.id, d.id), folder, folder.children.size()));
        assertEquals(List.of("C", "A", "D"), names(folder));
        assertTrue(names(root).contains("B"));
        assertFalse(names(root).contains("A") || names(root).contains("C") || names(root).contains("D"));
    }

    @Test
    void aBlockCanBeDroppedBetweenItemsInTheSameFolder(@TempDir Path tmp) throws Exception {
        ProjectStore s = ProjectStore.create(tmp, "Camp");
        TreeNode root = s.project.root;
        TreeNode overview = root.children.get(0); // the board every campaign starts with
        Card a = card(s, root, "A"), b = card(s, root, "B"), c = card(s, root, "C"), d = card(s, root, "D"), e = card(s, root, "E");
        // before: Overview A B C D E. Move A and B to sit before E (index 5 = before E, as the list is before the move)
        int at = root.children.indexOf(s.project.root.find(e.id));
        assertEquals(2, s.moveNodesTo(List.of(a.id, b.id), root, at));
        assertEquals(List.of(overview.name, "C", "D", "A", "B", "E"), names(root));
        // move D and E to the very front
        assertEquals(2, s.moveNodesTo(List.of(d.id, e.id), root, 0));
        assertEquals(List.of("D", "E", overview.name, "C", "A", "B"), names(root));
    }

    @Test
    void anItemInsideAMovedFolderTravelsWithItAndNothingMovesIntoItself(@TempDir Path tmp) throws Exception {
        ProjectStore s = ProjectStore.create(tmp, "Camp");
        TreeNode root = s.project.root;
        TreeNode outer = s.createFolder(root, "Outer");
        TreeNode inner = s.createFolder(outer, "Inner");
        Card x = card(s, inner, "X");
        TreeNode other = s.createFolder(root, "Other");
        assertEquals(List.of(outer.id), s.movable(List.of(x.id, outer.id, inner.id, outer.id, "nothing", root.id)));
        assertEquals(0, s.moveNodesTo(List.of(outer.id), inner, 0), "a folder cannot go inside itself");
        assertEquals(0, s.moveNodesTo(List.of(x.id), s.project.root.find(x.id), 0), "the target must be a folder");
        assertEquals(1, s.moveNodesTo(List.of(outer.id, x.id), other, 0));
        assertEquals(List.of("Outer"), names(other));
        assertNotNull(s.project.root.find(x.id), "X still exists, inside Inner");
        assertEquals(inner.id, s.project.root.parentOf(x.id).id);
    }

    @Test
    void movingNothingOrNothingNewReportsNothingMoved(@TempDir Path tmp) throws Exception {
        ProjectStore s = ProjectStore.create(tmp, "Camp");
        TreeNode root = s.project.root;
        Card a = card(s, root, "A");
        assertEquals(0, s.moveNodesTo(List.of(), root, 0));
        assertEquals(0, s.moveNodesTo(List.of("nope"), root, 0));
        assertEquals(1, s.moveNodesTo(List.of(a.id), root, 99), "a position past the end means the end");
        assertEquals("A", root.children.get(root.children.size() - 1).name);
    }
}
