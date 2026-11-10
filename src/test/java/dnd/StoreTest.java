package dnd;

import dnd.model.Board;
import dnd.model.BoardObject;
import dnd.model.TreeNode;
import dnd.nav.NavigationHistory;
import dnd.search.SearchService;
import dnd.storage.Json;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StoreTest {

    @Test
    void projectRoundTripsThroughDisk(@TempDir Path tmp) throws Exception {
        ProjectStore s = ProjectStore.create(tmp, "Curse of Strahd");
        TreeNode folder = s.createFolder(s.project.root, "NPCs");
        Board tavern = s.createBoard(folder, "Tavern");
        Board overview = s.board(s.project.openBoards.get(0));

        BoardObject text = new BoardObject(BoardObject.Type.TEXT);
        text.text = "Ireena";
        text.link = tavern.id;
        overview.objects.add(text);
        BoardObject stroke = new BoardObject(BoardObject.Type.STROKE);
        stroke.points.addAll(List.of(1.0, 2.0, 3.0, 4.0));
        overview.objects.add(stroke);
        s.markDirty(overview);
        s.flush();

        ProjectStore again = ProjectStore.open(tmp, "Curse of Strahd");
        assertEquals(2, again.allBoardIds().size());
        Board loaded = again.board(overview.id);
        assertEquals(2, loaded.objects.size());
        assertEquals(tavern.id, loaded.objects.get(0).link);
        assertEquals(List.of(1.0, 2.0, 3.0, 4.0), loaded.objects.get(1).points);
        assertEquals("Tavern", again.boardName(tavern.id));
        assertEquals(List.of("Curse of Strahd"), ProjectStore.listProjects(tmp));
    }

    @Test
    void deleteAndMoveNodes(@TempDir Path tmp) throws Exception {
        ProjectStore s = ProjectStore.create(tmp, "P");
        TreeNode a = s.createFolder(s.project.root, "A");
        TreeNode b = s.createFolder(a, "B");
        Board board = s.createBoard(b, "Deep");

        assertFalse(s.moveNode(a.id, b), "cannot move a folder into its own descendant");
        assertTrue(s.moveNode(b.id, s.project.root));
        assertSame(s.project.root, s.project.root.parentOf(b.id));

        s.deleteNode(b);
        assertFalse(s.boardExists(board.id));
        assertNull(s.board(board.id));
    }

    @Test
    void searchAndBacklinks(@TempDir Path tmp) throws Exception {
        ProjectStore s = ProjectStore.create(tmp, "P");
        Board home = s.board(s.project.openBoards.get(0));
        Board other = s.createBoard(s.project.root, "Dragon Lair");
        BoardObject o = new BoardObject(BoardObject.Type.TEXT);
        o.text = "Beware the DRAGON";
        o.link = other.id;
        home.objects.add(o);

        assertEquals(2, SearchService.search(s, "dragon", 10).size()); // board name + text
        List<SearchService.Result> back = SearchService.backlinks(s, other.id);
        assertEquals(1, back.size());
        assertEquals(home.id, back.get(0).boardId);
        assertEquals(o.id, back.get(0).objectId);
    }

    @Test
    void objectCopyIsIndependent() {
        BoardObject o = new BoardObject(BoardObject.Type.STROKE);
        o.points.addAll(List.of(0.0, 0.0, 10.0, 10.0));
        BoardObject c = o.copy();
        c.move(5, 5);
        assertNotEquals(o.id, c.id);
        assertEquals(0.0, o.points.get(0));
        assertEquals(5.0, c.points.get(0));
        List<BoardObject> back = Json.readObjects(Json.write(List.of(c)));
        assertEquals(c.id, back.get(0).id);
    }

    @Test
    void navigationHistory() {
        NavigationHistory h = new NavigationHistory();
        h.visit("a");
        h.visit("b");
        h.visit("c");
        assertEquals("b", h.back());
        assertEquals("a", h.back());
        assertNull(h.back());
        assertEquals("b", h.forward());
        h.visit("d"); // drops "c"
        assertFalse(h.canForward());
        assertEquals(List.of("a", "b", "d"), h.trail(5));
        h.forget("b");
        assertEquals(List.of("a", "d"), h.trail(5));
    }

    @Test
    void itemsCanBeMovedUpAndDownWithinTheirFolder(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        dnd.model.TreeNode folder = store.createFolder(store.project.root, "Places");
        dnd.model.Board a = store.createBoard(folder, "A");
        dnd.model.Board b = store.createBoard(folder, "B");
        dnd.model.Board c = store.createBoard(folder, "C");
        assertFalse(store.moveWithinParent(a.id, -1), "already first");
        assertFalse(store.moveWithinParent(c.id, 1), "already last");
        assertTrue(store.moveWithinParent(c.id, -1));
        assertEquals(java.util.List.of(a.id, c.id, b.id), folder.children.stream().map(n -> n.id).toList());
        assertTrue(store.moveWithinParent(a.id, 1));
        assertEquals(java.util.List.of(c.id, a.id, b.id), folder.children.stream().map(n -> n.id).toList());
        assertFalse(store.moveWithinParent(store.project.root.id, 1), "the root cannot move");
        store.flush();
        dnd.model.TreeNode back = ProjectStore.open(tmp, "Camp").project.root.find(folder.id);
        assertEquals(java.util.List.of(c.id, a.id, b.id), back.children.stream().map(n -> n.id).toList(), "the order is saved");
    }


    @Test
    void anItemCanBeDroppedAtAnyPositionInAnyFolder(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        dnd.model.TreeNode one = store.createFolder(store.project.root, "One");
        dnd.model.TreeNode two = store.createFolder(store.project.root, "Two");
        dnd.model.Board a = store.createBoard(one, "A");
        dnd.model.Board b = store.createBoard(one, "B");
        dnd.model.Board c = store.createBoard(one, "C");
        dnd.model.Board x = store.createBoard(two, "X");
        java.util.function.Function<dnd.model.TreeNode, java.util.List<String>> ids = f -> f.children.stream().map(n -> n.id).toList();
        assertTrue(store.moveNodeTo(a.id, one, 3), "to the end of its own folder");
        assertEquals(java.util.List.of(b.id, c.id, a.id), ids.apply(one));
        assertTrue(store.moveNodeTo(a.id, one, 0), "to the front");
        assertEquals(java.util.List.of(a.id, b.id, c.id), ids.apply(one));
        assertFalse(store.moveNodeTo(b.id, one, 1), "dropping it where it already is changes nothing");
        assertFalse(store.moveNodeTo(b.id, one, 2), "just after itself is the same place");
        assertTrue(store.moveNodeTo(c.id, two, 0), "into another folder, first");
        assertEquals(java.util.List.of(c.id, x.id), ids.apply(two));
        assertEquals(java.util.List.of(a.id, b.id), ids.apply(one));
        assertFalse(store.moveNodeTo(one.id, one, 0), "a folder cannot go into itself");
        dnd.model.TreeNode inner = store.createFolder(one, "Inner");
        assertFalse(store.moveNodeTo(one.id, inner, 0), "nor into something inside it");
        assertTrue(store.moveNodeTo(two.id, store.project.root, 0), "a folder can be put first at the top");
        assertEquals(two.id, store.project.root.children.get(0).id);
    }

}
