package dnd.model;

import dnd.storage.Json;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything placed on a world map (symbols, lines, regions), taken as a snapshot of text so an edit can be undone.
 * Painting is undone separately, with small patches.
 */
public class WorldObjects {
    public List<WorldStamp> stamps = new ArrayList<>();
    public List<WorldPath> paths = new ArrayList<>();
    public List<WorldRegion> regions = new ArrayList<>();

    public static String capture(WorldMap map) {
        WorldObjects o = new WorldObjects();
        o.stamps = map.stamps;
        o.paths = map.paths;
        o.regions = map.regions;
        return Json.write(o);
    }

    public static void restore(WorldMap map, String json) {
        WorldObjects o = Json.read(json, WorldObjects.class);
        map.stamps.clear();
        map.stamps.addAll(o.stamps);
        map.paths.clear();
        map.paths.addAll(o.paths);
        map.regions.clear();
        map.regions.addAll(o.regions);
    }
}
