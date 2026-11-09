package dnd.model;

import java.util.UUID;

/** A numbered note on a battlemap, for the DM only (never shown to the players): a trap, a secret, a reminder. */
public class MapPin {
    public String id = UUID.randomUUID().toString();
    /** Centre of the pin, in map units. */
    public double x, y;
    public String text = "";
    /** Id of a project item (whiteboard, card, encounter...) this note points at, or null. */
    public String link;

    public MapPin() { }

    public MapPin(double x, double y, String text) {
        this.x = x;
        this.y = y;
        this.text = text;
    }

    public MapPin copy() {
        MapPin p = new MapPin(x, y, text);
        p.link = link;
        return p;
    }
}