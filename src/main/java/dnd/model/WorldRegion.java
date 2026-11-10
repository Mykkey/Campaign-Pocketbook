package dnd.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A named area of a world map (a kingdom, a forest, a sea) outlined by a border and softly tinted. */
public class WorldRegion {
    public String id = UUID.randomUUID().toString();
    public String name = "";
    /** The corners, as x0, y0, x1, y1... in map cells. */
    public List<Double> points = new ArrayList<>();
    /** "#RRGGBB", or null for the style's border colour. */
    public String tint;
    public boolean hidden;
    /** Where the name is written, or null for the middle of the outline. */
    public Double labelX, labelY;
    public String link;
    public String note;

    public WorldRegion() { }

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

    /** True if the point is inside the outline. */
    public boolean contains(double px, double py) {
        boolean in = false;
        int n = count();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = x(i), yi = y(i), xj = x(j), yj = y(j);
            if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) in = !in;
        }
        return in;
    }

    /** The middle of the outline (the average of its corners), where the name is written. */
    public double[] centre() {
        double sx = 0, sy = 0;
        int n = Math.max(1, count());
        for (int i = 0; i < count(); i++) {
            sx += x(i);
            sy += y(i);
        }
        return new double[]{sx / n, sy / n};
    }

    /** Where the name is written: where the user put it, or the middle of the outline. */
    public double[] labelAt() {
        return labelX != null && labelY != null ? new double[]{labelX, labelY} : centre();
    }

    /** The area inside the outline, in square map cells. */
    public double area() {
        double a = 0;
        int n = count();
        for (int i = 0, j = n - 1; i < n; j = i++) a += (x(j) + x(i)) * (y(j) - y(i));
        return Math.abs(a / 2);
    }

    public WorldRegion copy() {
        WorldRegion r = new WorldRegion();
        r.name = name;
        r.points = new ArrayList<>(points);
        r.tint = tint;
        r.hidden = hidden;
        r.labelX = labelX;
        r.labelY = labelY;
        r.link = link;
        r.note = note;
        return r;
    }
}
