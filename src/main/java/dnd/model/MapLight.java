package dnd.model;

import java.util.UUID;

/**
 * A light source on a battlemap: bright light out to {@link #brightFeet}, dim light out to {@link #dimFeet}. It sits at
 * a spot on the map, or is carried by a token and moves with it.
 */
public class MapLight {
    public String id = UUID.randomUUID().toString();
    /** Where it is, in map units (ignored while {@link #tokenId} is set). */
    public double x, y;
    public String name = "Torch";
    public int brightFeet = 20, dimFeet = 40;
    /** The token carrying this light, or null if it is fixed on the map. */
    public String tokenId;

    public MapLight() { }

    public MapLight(String name, int brightFeet, int dimFeet) {
        this.name = name;
        this.brightFeet = brightFeet;
        this.dimFeet = dimFeet;
    }

    public MapLight copy() {
        MapLight l = new MapLight(name, brightFeet, dimFeet);
        l.x = x;
        l.y = y;
        l.tokenId = tokenId;
        return l;
    }
}