package dnd.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A line drawn on a world map: a river, a road, a trail, a sea route, a border or a railway. */
public class WorldPath {
    /** The kinds of line, with how wide they are drawn unless the width is set (in map cells). */
    public enum Type {
        RIVER("River", 1.8), ROAD("Road", 1.1), TRAIL("Trail", 0.7), SEA_ROUTE("Sea route", 0.8), BORDER("Border", 0.8), RAILWAY("Railway", 1.0);

        public final String label;
        public final double defaultWidth;

        Type(String label, double defaultWidth) {
            this.label = label;
            this.defaultWidth = defaultWidth;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public String id = UUID.randomUUID().toString();
    public Type type = Type.ROAD;
    /** The points the line passes through, as x0, y0, x1, y1... in map cells. It is drawn as a smooth curve through them. */
    public List<Double> points = new ArrayList<>();
    /** Width in map cells, or 0 for the kind's own width. */
    public double width;
    /** The art style of this line, or null to follow the map's. */
    public WorldStyle style;
    public String name;
    public boolean hidden;
    public String link;
    public String note;

    public WorldPath() { }

    public WorldPath(Type type) {
        this.type = type;
    }

    public int count() {
        return points.size() / 2;
    }

    public double x(int i) {
        return points.get(2 * i);
    }

    public double y(int i) {
        return points.get(2 * i + 1);
    }

    public void add(double x, double y) {
        points.add(x);
        points.add(y);
    }

    public void insert(int index, double x, double y) {
        points.add(2 * index, x);
        points.add(2 * index + 1, y);
    }

    public void remove(int index) {
        points.remove(2 * index + 1);
        points.remove(2 * index);
    }

    public double drawnWidth() {
        return width > 0 ? width : type.defaultWidth;
    }

    public WorldPath copy() {
        WorldPath p = new WorldPath(type);
        p.points = new ArrayList<>(points);
        p.width = width;
        p.style = style;
        p.name = name;
        p.hidden = hidden;
        p.link = link;
        p.note = note;
        return p;
    }
}
