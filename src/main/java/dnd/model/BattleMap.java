package dnd.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A battlemap: a square or hex grid with painted terrain, placed vector assets and tokens.
 * Stored as maps/&lt;id&gt;.battlemap (JSON).
 */
public class BattleMap {
    public enum GridType { SQUARE, HEX }

    public String id = UUID.randomUUID().toString();
    public String name = "Battlemap";
    public GridType gridType = GridType.SQUARE;
    public int cols = 30, rows = 20;
    /** Size of one cell in map units (for hexes: the distance between opposite flat sides). */
    public double cellSize = 50;
    public boolean showGrid = true;
    public double offsetX = 40, offsetY = 40, zoom = 1;

    /** "col,row" -> terrain id (see {@link Terrain}). Cells not listed are empty. */
    public Map<String, String> terrain = new LinkedHashMap<>();
    public List<MapObject> objects = new ArrayList<>();
    public List<MapToken> tokens = new ArrayList<>();

    /** When on, cells not in {@link #revealed} are hidden from the players (and shaded for the DM). */
    public boolean fogEnabled;
    /** Revealed cells, as "col,row" keys (same format as {@link #terrain}). */
    public Set<String> revealed = new LinkedHashSet<>();

    /** Combat state for Battle mode; null until a battle is first started. */
    public Battle battle;

    /** Bumped on every change so cached thumbnails know to refresh. Not saved. */
    public transient int revision;

    public BattleMap() { }

    public static String key(int col, int row) { return col + "," + row; }

    public MapObject findObject(String objectId) {
        for (MapObject o : objects) if (o.id.equals(objectId)) return o;
        return null;
    }

    public MapToken findToken(String tokenId) {
        for (MapToken t : tokens) if (t.id.equals(tokenId)) return t;
        return null;
    }

    /** A copy of everything that can be undone (terrain, objects, tokens) as JSON-friendly data. */
    public static class Snapshot {
        public Map<String, String> terrain = new LinkedHashMap<>();
        public List<MapObject> objects = new ArrayList<>();
        public List<MapToken> tokens = new ArrayList<>();
        public Set<String> revealed = new LinkedHashSet<>();
    }
}
