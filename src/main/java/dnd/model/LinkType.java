package dnd.model;

import java.util.UUID;

/**
 * A kind of link the person made up for their own campaign ("Owes a favour to", "Blood oath with"), with the colour it is drawn in.
 * Kept in the project. A link that reads the same from both sides has no {@link #reverseName}; one that reads differently from the
 * other side ("Owes a favour to" / "Is owed a favour by") has one.
 */
public class LinkType {
    public String id = UUID.randomUUID().toString();
    /** How it reads from the card it is written on: "Owes a favour to". */
    public String name = "";
    /** How it reads from the other card's side, or empty if both sides read the same. */
    public String reverseName = "";
    /** The colour of its line on diagrams: "#RRGGBB". */
    public String colour = "#8a94a3";

    public LinkType() { }

    public LinkType(String name, String reverseName, String colour) {
        this.name = name == null ? "" : name.trim();
        this.reverseName = reverseName == null ? "" : reverseName.trim();
        this.colour = colour == null || !colour.matches("#[0-9a-fA-F]{6}") ? "#8a94a3" : colour;
    }

    /** True if it reads differently from the two sides (so it is drawn with an arrow). */
    public boolean directed() {
        return reverseName != null && !reverseName.isBlank();
    }

    public LinkType copy() {
        LinkType t = new LinkType(name, reverseName, colour);
        t.id = id;
        return t;
    }

    @Override
    public String toString() {
        return name;
    }
}
