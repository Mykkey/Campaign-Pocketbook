package dnd;

import dnd.model.WorldMap;
import dnd.model.WorldPath;
import dnd.model.WorldRegion;
import dnd.model.WorldStamp;
import dnd.model.WorldStyle;
import dnd.search.SearchService;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorldLinksTest {
    @Test
    void notesLinksAndLabelsAreFoundBySearchAndBacklinksAndSurviveSaving(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        WorldMap target = store.createWorldMap(store.project.root, new WorldMap("Valdoria region", 64, 48, 640, WorldStyle.FANTASY_COLOUR));
        WorldMap world = store.createWorldMap(store.project.root, new WorldMap("The World", 64, 48, 640, WorldStyle.FANTASY_COLOUR));

        WorldStamp city = new WorldStamp("settlement-capital", 10, 10);
        city.link = target.id;
        city.note = "The thieves' guild meets under the old mill";
        WorldStamp label = new WorldStamp("label", 20, 20);
        label.text = "Sea of Storms";
        WorldPath river = new WorldPath(WorldPath.Type.RIVER);
        river.add(1, 1);
        river.add(5, 9);
        river.name = "Silverflow";
        river.link = target.id;
        WorldRegion vale = new WorldRegion();
        vale.name = "Vale of Mists";
        vale.note = "Haunted since the war";
        world.stamps.add(city);
        world.stamps.add(label);
        world.paths.add(river);
        world.regions.add(vale);
        store.markWorldMapDirty(world, true);
        store.flush();

        ProjectStore again = ProjectStore.open(tmp, "Camp");
        WorldMap back = again.worldMap(world.id);
        assertEquals(target.id, back.stamps.get(0).link);
        assertEquals("The thieves' guild meets under the old mill", back.stamps.get(0).note);
        assertEquals(target.id, back.paths.get(0).link);

        List<SearchService.Result> links = SearchService.backlinks(again, target.id);
        assertEquals(2, links.size(), "the capital and the river link to the region map");
        assertTrue(links.stream().allMatch(r -> r.boardId.equals(world.id)));
        assertTrue(links.stream().anyMatch(r -> r.objectId.equals(city.id)));
        assertTrue(links.stream().anyMatch(r -> r.snippet.contains("Silverflow")));
        assertTrue(SearchService.backlinks(again, world.id).isEmpty(), "nothing links to the world map");

        List<SearchService.Result> storms = SearchService.search(again, "storms", 20);
        assertTrue(storms.stream().anyMatch(r -> r.boardId.equals(world.id) && r.objectId != null && r.snippet.startsWith("label")));
        List<SearchService.Result> guild = SearchService.search(again, "thieves", 20);
        assertEquals(1, guild.size());
        assertEquals(city.id, guild.get(0).objectId);
        assertTrue(guild.get(0).snippet.startsWith("note"));
        assertEquals(1, SearchService.search(again, "silverflow", 20).size());
        List<SearchService.Result> haunted = SearchService.search(again, "haunted", 20);
        assertEquals(1, haunted.size());
        assertEquals(vale.id, haunted.get(0).objectId);
    }
}
