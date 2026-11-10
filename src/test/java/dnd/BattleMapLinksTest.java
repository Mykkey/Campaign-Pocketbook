package dnd;

import dnd.model.BattleMap;
import dnd.model.MapObject;
import dnd.model.MapToken;
import dnd.model.WorldMap;
import dnd.model.WorldStyle;
import dnd.search.SearchService;
import dnd.storage.Json;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BattleMapLinksTest {
    @Test
    void tokensAndPiecesCarryLinksAndNotesThroughSavingCopyingAndSnapshots(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        WorldMap region = store.createWorldMap(store.project.root, new WorldMap("Region", 64, 48, 640, WorldStyle.FANTASY_COLOUR));
        BattleMap map = store.createBattleMap(store.project.root, "Inn", BattleMap.GridType.SQUARE, 20, 20, 50);
        MapToken tok = new MapToken();
        tok.label = "Innkeeper";
        tok.link = region.id;
        tok.note = "Knows about the smugglers";
        MapObject door = new MapObject();
        door.assetId = "x";
        door.link = region.id;
        door.note = "Secret cellar door";
        map.tokens.add(tok);
        map.objects.add(door);

        assertEquals(region.id, tok.copy().link);
        assertEquals("Knows about the smugglers", tok.copy().note);
        assertEquals(region.id, door.copy().link);
        assertEquals("Secret cellar door", door.copy().note);
        MapToken viaJson = Json.read(Json.write(tok), MapToken.class);
        assertEquals(region.id, viaJson.link);

        store.markMapDirty(map);
        store.flush();
        BattleMap back = ProjectStore.open(tmp, "Camp").battleMap(map.id);
        assertEquals(region.id, back.tokens.get(0).link);
        assertEquals("Secret cellar door", back.objects.get(0).note);

        assertTrue(SearchService.search(store, "smugglers", 10).stream().anyMatch(r -> r.snippet.contains("Token note")));
        var links = SearchService.backlinks(store, region.id);
        assertEquals(2, links.size(), links.toString());
        assertTrue(links.stream().allMatch(r -> r.boardId.equals(map.id)));
    }
}
