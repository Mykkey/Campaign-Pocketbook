package dnd.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Anything that can sit on a whiteboard. One flexible class (rather than a hierarchy) keeps JSON
 * handling, copy/paste and undo trivial. Which fields matter depends on {@link #type}.
 */
public class BoardObject {
    public enum Type { TEXT, STICKY, IMAGE, RECT, ELLIPSE, LINE, ARROW, STROKE }

    public String id = UUID.randomUUID().toString();
    public Type type = Type.TEXT;
    /** Top-left (or line start). For LINE/ARROW, w/h are the (signed) offset to the end point. */
    public double x, y, w, h;
    /** Text colour for TEXT/STICKY, outline colour for shapes/strokes. CSS "#RRGGBB[AA]". */
    public String color = "#222222";
    /** Fill colour (STICKY, RECT, ELLIPSE). Null means no fill. */
    public String fill;
    public double strokeWidth = 2;
    public String text = "";
    public double fontSize = 20;
    public boolean bold, italic;
    /** File name inside the project's images folder (IMAGE). */
    public String image;
    /** Flat list x0,y0,x1,y1,... in board coordinates (STROKE). */
    public List<Double> points = new ArrayList<>();
    /** Id of the whiteboard this object links to, or null. */
    public String link;

    public BoardObject() { }

    public BoardObject(Type type) { this.type = type; }

    public double left() { return Math.min(x, x + w); }
    public double top() { return Math.min(y, y + h); }
    public double right() { return Math.max(x, x + w); }
    public double bottom() { return Math.max(y, y + h); }

    public boolean lineLike() { return type == Type.LINE || type == Type.ARROW; }

    public void move(double dx, double dy) {
        x += dx;
        y += dy;
        for (int i = 0; i < points.size(); i++) {
            points.set(i, points.get(i) + (i % 2 == 0 ? dx : dy));
        }
    }

    /** Recomputes x/y/w/h from the point list (STROKE only). */
    public void refreshStrokeBounds() {
        if (points.size() < 2) return;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int i = 0; i + 1 < points.size(); i += 2) {
            double px = points.get(i), py = points.get(i + 1);
            minX = Math.min(minX, px);
            maxX = Math.max(maxX, px);
            minY = Math.min(minY, py);
            maxY = Math.max(maxY, py);
        }
        x = minX;
        y = minY;
        w = maxX - minX;
        h = maxY - minY;
    }

    /** Deep copy with a fresh id. */
    public BoardObject copy() {
        BoardObject o = new BoardObject(type);
        o.x = x; o.y = y; o.w = w; o.h = h;
        o.color = color; o.fill = fill; o.strokeWidth = strokeWidth;
        o.text = text; o.fontSize = fontSize; o.bold = bold; o.italic = italic;
        o.image = image; o.link = link;
        o.points = new ArrayList<>(points);
        return o;
    }
}
