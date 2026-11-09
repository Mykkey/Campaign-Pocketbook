package dnd.model;

import java.util.UUID;

/** An asset (house, wall, tree, ...) placed on a battlemap. Position/size are in map units. */
public class MapObject {
    public String id = UUID.randomUUID().toString();
    /** Library id, e.g. "buildings/cottage" for built-ins or "imported/name.svg". */
    public String assetId;
    /** Centre of the object. */
    public double x, y;
    public double w = 50, h = 50;
    /** Degrees, clockwise. */
    public double rotation;
    public boolean flipX;

    public MapObject() { }

    public MapObject copy() {
        MapObject o = new MapObject();
        o.assetId = assetId;
        o.x = x; o.y = y; o.w = w; o.h = h;
        o.rotation = rotation; o.flipX = flipX;
        return o;
    }

    public void move(double dx, double dy) {
        x += dx;
        y += dy;
    }

    /** True if the point (in map units) lies inside this (possibly rotated) object. */
    public boolean contains(double px, double py) {
        double a = Math.toRadians(-rotation);
        double dx = px - x, dy = py - y;
        double lx = dx * Math.cos(a) - dy * Math.sin(a);
        double ly = dx * Math.sin(a) + dy * Math.cos(a);
        return Math.abs(lx) <= w / 2 && Math.abs(ly) <= h / 2;
    }
}
