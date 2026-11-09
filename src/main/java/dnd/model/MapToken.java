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

    public MapToken() { }

    public MapToken copy() {
        MapToken t = new MapToken();
        t.label = label; t.color = color; t.col = col; t.row = row; t.size = size;
        return t;
    }
}
