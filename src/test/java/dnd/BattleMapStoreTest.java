package dnd;

import dnd.model.BattleMap;
import dnd.model.Board;
import dnd.model.BoardObject;
import dnd.model.MapObject;
import dnd.model.MapToken;
import dnd.model.TreeNode;
import dnd.search.SearchService;
import dnd.storage.Json;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BattleMapStoreTest {

    @Test
    void battlemapRoundTripsThroughDisk(@TempDir Path tmp) throws Exception {
        ProjectStore s = ProjectStore.create(tmp, "P");
        BattleMap m = s.createBattleMap(s.project.root, "Goblin Ambush", BattleMap.GridType.HEX, 12, 9, 60);
        m.terrain.put(BattleMap.key(2, 3), "water");
        MapObject o = new MapObject();
        o.assetId = "camp/tent";
        o.x = 120.5;
        o.y = 80;
        o.rotation = 45;
        o.flipX = true;
        m.objects.add(o);
        MapToken t = new MapToken();
        t.label = "G1";
        t.col = 4;
        t.row = 5;
        t.size = 2;
        m.tokens.add(t);
        s.markMapDirty(m);
        s.flush();

        assertTrue(Files.isRegularFile(tmp.resolve("P").resolve("maps").resolve(m.id + ".battlemap")));

        ProjectStore again = ProjectStore.open(tmp, "P");
        assertTrue(again.isBattleMap(m.id));
        assertTrue(again.itemExists(m.id));
        assertFalse(again.boardExists(m.id), "a battlemap is not a whiteboard");
        BattleMap loaded = again.battleMap(m.id);
        assertEquals("Goblin Ambush", loaded.name);
        assertEquals(BattleMap.GridType.HEX, loaded.gridType);
        assertEquals(12, loaded.cols);
        assertEquals(60, loaded.cellSize);
        assertEquals("water", loaded.terrain.get("2,3"));
        assertEquals(1, loaded.objects.size());
        assertEquals("camp/tent", loaded.objects.get(0).assetId);
        assertEquals(45, loaded.objects.get(0).rotation);
        assertTrue(loaded.objects.get(0).flipX);
        assertEquals("G1", loaded.tokens.get(0).label);
        assertEquals(2, loaded.tokens.get(0).size);
    }

    @Test
    void oldProjectsWithoutKindStillLoadAsWhiteboards(@TempDir Path tmp) throws Exception {
        ProjectStore s = ProjectStore.create(tmp, "Old");
        String boardId = s.project.openBoards.get(0);
        // simulate a project.json written before battlemaps existed
        Path pj = tmp.resolve("Old").resolve("project.json");
        String json = Files.readString(pj).replaceAll("\\s*\"kind\"\\s*:\\s*[^,}\\n]+,?", "");
        Files.writeString(pj, json);

        ProjectStore again = ProjectStore.open(tmp, "Old");
        assertTrue(again.boardExists(boardId));
        assertFalse(again.isBattleMap(boardId));
        assertNotNull(again.board(boardId));
    }

    @Test
    void deletingAFolderRemovesBoardAndMapFiles(@TempDir Path tmp) throws Exception {
        ProjectStore s = ProjectStore.create(tmp, "P");
        TreeNode folder = s.createFolder(s.project.root, "Dungeon");
        Board b = s.createBoard(folder, "Notes");
        BattleMap m = s.createBattleMap(folder, "Level 1", BattleMap.GridType.SQUARE, 10, 10, 50);
        s.flush();
        Path boardFile = tmp.resolve("P").resolve("boards").resolve(b.id + ".json");
        Path mapFile = tmp.resolve("P").resolve("maps").resolve(m.id + ".battlemap");
        assertTrue(Files.exists(boardFile));
        assertTrue(Files.exists(mapFile));

        s.deleteNode(folder);
        s.flush();
        assertFalse(Files.exists(boardFile));
        assertFalse(Files.exists(mapFile));
        assertFalse(s.itemExists(m.id));
        assertNull(s.battleMap(m.id));
    }

    @Test
    void renameKeepsMapNameInSync(@TempDir Path tmp) throws Exception {
        ProjectStore s = ProjectStore.create(tmp, "P");
        BattleMap m = s.createBattleMap(s.project.root, "Old name", BattleMap.GridType.SQUARE, 5, 5, 50);
        int before = m.revision;
        s.renameNode(s.project.root.find(m.id), "New name");
        assertEquals("New name", s.battleMap(m.id).name);
        assertEquals("New name", s.itemName(m.id));
        assertTrue(m.revision > before);
    }

    @Test
    void markingAMapDirtyBumpsItsRevisionForPreviews(@TempDir Path tmp) throws Exception {
        ProjectStore s = ProjectStore.create(tmp, "P");
        BattleMap m = s.createBattleMap(s.project.root, "M", BattleMap.GridType.SQUARE, 5, 5, 50);
        int r = m.revision;
        s.markMapDirty(m);
        assertEquals(r + 1, m.revision);
    }

    @Test
    void searchFindsBattlemapNamesAndTokens(@TempDir Path tmp) throws Exception {
        ProjectStore s = ProjectStore.create(tmp, "P");
        BattleMap m = s.createBattleMap(s.project.root, "Ogre Cave", BattleMap.GridType.SQUARE, 5, 5, 50);
        MapToken t = new MapToken();
        t.label = "Boss";
        m.tokens.add(t);

        assertEquals(1, SearchService.search(s, "ogre", 10).size());
        List<SearchService.Result> tok = SearchService.search(s, "boss", 10);
        assertEquals(1, tok.size());
        assertEquals(m.id, tok.get(0).boardId);
    }

    @Test
    void whiteboardPreviewLinksToMapAndShowsAsBacklink(@TempDir Path tmp) throws Exception {
        ProjectStore s = ProjectStore.create(tmp, "P");
        Board home = s.board(s.project.openBoards.get(0));
        BattleMap m = s.createBattleMap(s.project.root, "Arena", BattleMap.GridType.HEX, 8, 8, 50);
        BoardObject preview = new BoardObject(BoardObject.Type.MAP_PREVIEW);
        preview.link = m.id;
        home.objects.add(preview);
        s.markDirty(home);
        s.flush();

        List<SearchService.Result> back = SearchService.backlinks(s, m.id);
        assertEquals(1, back.size());
        assertEquals(home.id, back.get(0).boardId);

        // the preview object survives a save/load cycle
        ProjectStore again = ProjectStore.open(tmp, "P");
        BoardObject loaded = again.board(home.id).objects.get(0);
        assertEquals(BoardObject.Type.MAP_PREVIEW, loaded.type);
        assertEquals(m.id, loaded.link);
    }

    @Test
    void mapObjectHitTestHonoursRotation() {
        MapObject o = new MapObject();
        o.x = 100;
        o.y = 100;
        o.w = 100;
        o.h = 20;
        assertTrue(o.contains(140, 100));
        assertFalse(o.contains(100, 140));
        o.rotation = 90; // now tall instead of wide
        assertFalse(o.contains(140, 100));
        assertTrue(o.contains(100, 140));
    }

    @Test
    void snapshotJsonRoundTrip() {
        BattleMap.Snapshot snap = new BattleMap.Snapshot();
        snap.terrain.put("1,1", "grass");
        MapToken t = new MapToken();
        t.label = "A";
        snap.tokens.add(t);
        BattleMap.Snapshot back = Json.read(Json.write(snap), BattleMap.Snapshot.class);
        assertEquals("grass", back.terrain.get("1,1"));
        assertEquals("A", back.tokens.get(0).label);
    }

    @Test
    void importedAssetsGetUniqueNames(@TempDir Path tmp) throws Exception {
        ProjectStore s = ProjectStore.create(tmp, "P");
        Path src = tmp.resolve("tree.svg");
        Files.writeString(src, "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 10 10'/>");
        String a = s.importAsset(src);
        String b = s.importAsset(src);
        assertNotEquals(a, b);
        assertTrue(Files.exists(s.assetsDir().resolve(a)));
        assertTrue(Files.exists(s.assetsDir().resolve(b)));
    }
}
