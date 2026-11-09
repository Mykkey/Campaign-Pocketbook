package dnd.model;

import java.util.UUID;

/** A labelled marker for a player or monster, sitting on a grid cell. */
public class MapToken {
    public String id = UUID.randomUUID().toString();
    public String label = "";
    public String color = "#d33f49";
    public int col, row;
    /** Footprint in cells (1 = medium, 2 = large, ...). */
    public int size = 1;

    /** Card this token represents (stat block, portrait), or null. */
    public String cardId;
    /** If &gt; 0, fog within this many cells is cleared whenever the token is placed or moved. */
    public int revealRadius;
    /** When true the players don't see this token (e.g. an ambusher), even in revealed areas. */
    public boolean hidden;
    /** How far it sees in the dark, in feet (darkvision); 0 = it needs light. Only matters on a dark map. */
    public int visionFeet;

    public MapToken() { }

    public MapToken copy() {
        MapToken t = new MapToken();
        t.label = label; t.color = color; t.col = col; t.row = row; t.size = size;
        t.cardId = cardId; t.revealRadius = revealRadius; t.hidden = hidden; t.visionFeet = visionFeet;
        return t;
    }
}
