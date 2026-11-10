package dnd.model;

import java.util.UUID;

/** One symbol placed on a world map: a mountain, a tree, a town... drawn from the symbol library in an art style. */
public class WorldStamp {
    public String id = UUID.randomUUID().toString();
    /** The symbol's id in the library ("peak-snowy", "tree-oak"...). */
    public String feature;
    /** The art style of this symbol, or null to follow the map's style. */
    public WorldStyle style;
    /** Where it stands, in map cells (the foot of a mountain or tree; the middle of a flat decoration). */
    public double x, y;
    /** How wide it is, in map cells; 0 means the symbol's own default width. */
    public double size;
    /** Degrees, clockwise. */
    public double rotation;
    public boolean flipX;
    /** Hidden from the players. */
    public boolean hidden;
    /** The years this exists (either may be null: no limit). Only has an effect while the map shows a chosen year. */
    public Integer fromYear, toYear;
    /** Id of a project item this symbol links to, or null. */
    public String link;
    /** The DM's own note shown on hover, or null. */
    public String note;
    /** Text on banners and labels, or null. */
    public String text;
    /** For a text label: its kind (title, region, sea...), or null. */
    public String labelKind;
    /** For a text label: how far its middle bends away from straight, as a fraction of its width (negative bends up). */
    public double bend;

    public WorldStamp() { }

    public WorldStamp(String feature, double x, double y) {
        this.feature = feature;
        this.x = x;
        this.y = y;
    }

    public WorldStamp copy() {
        WorldStamp s = new WorldStamp(feature, x, y);
        s.style = style;
        s.size = size;
        s.rotation = rotation;
        s.flipX = flipX;
        s.hidden = hidden;
        s.fromYear = fromYear;
        s.toYear = toYear;
        s.link = link;
        s.note = note;
        s.text = text;
        s.labelKind = labelKind;
        s.bend = bend;
        return s;
    }
}
