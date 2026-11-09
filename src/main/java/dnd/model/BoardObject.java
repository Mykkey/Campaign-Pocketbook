package dnd.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Anything that can sit on a whiteboard. One flexible class (rather than a hierarchy) keeps JSON
 * handling, copy/paste and undo trivial. Which fields matter depends on {@link #type}.
 */
public class BoardObject {
    /** MAP_PREVIEW shows a live thumbnail of the battlemap whose id is in {@link #link}. */
    public enum Type { TEXT, STICKY, IMAGE, RECT, ELLIPSE, LINE, ARROW, STROKE, MAP_PREVIEW }

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
    /**
     * TEXT only: the width the text wraps at, set by dragging out or resizing the box. 0 means the box takes the
     * width of its text (one line per line break), as older boards do.
     */
    public double wrapWidth;
    /**
     * LINE and ARROW only: where along the line (0..1 from the start) the bend point sits; 0 means the middle.
     * Together with {@link #bendOffset} it is the curve's most extreme point (the one farthest from the straight line).
     */
    public double bendT;
    /** LINE and ARROW only: how far the bend point is pulled sideways from the straight line (0 = straight). */
    public double bendOffset;
    public boolean bold, italic;
    /** File name inside the project's images folder (IMAGE). */
    public String image;
    /** Flat list x0,y0,x1,y1,... in board coordinates (STROKE). */
    public List<Double> points = new ArrayList<>();
    /** Id of the whiteboard this object links to, or null. */
    public String link;
    /** The DM's own note shown when hovering this whole object ({@code **bold**} and {@code *italic*} allowed), or null. */
    public String note;
    /**
     * Links and notes on individual words of this text: the text markup has {@code [words]{n}} spans, and entry
     * {@code "n"} here says what they link to and what note they carry.
     */
    public java.util.Map<String, SpanRef> refs = new java.util.LinkedHashMap<>();
    /** Lower-case card or spell names whose "link this name?" hint was dismissed in this text. */
    public java.util.Set<String> dismissedNames = new java.util.LinkedHashSet<>();

    public BoardObject() { }

    public BoardObject(Type type) { this.type = type; }

    /** Older files had a "tooltip" that previewed an item: that is exactly what a link does now. */
    @com.fasterxml.jackson.annotation.JsonSetter("tip")
    private void legacyTip(String itemId) {
        if (itemId != null && link == null) link = itemId;
    }

    public boolean hasNote() {
        return note != null && !note.isBlank();
    }

    /** The next free span reference number. */
    public int nextRef() {
        int max = 0;
        for (String k : refs.keySet()) {
            try {
                max = Math.max(max, Integer.parseInt(k));
            } catch (NumberFormatException ignored) {
                // not one of ours
            }
        }
        return max + 1;
    }

    /** The span entry for a reference number, or null. */
    public SpanRef ref(int n) {
        return n <= 0 ? null : refs.get(String.valueOf(n));
    }

    // ---- curved lines and arrows: a quadratic curve from (x, y) to (x + w, y + h) through the bend point

    /** The position of the bend point along the line, 0.1 to 0.9 (the middle unless it was moved). */
    public double bendPosition() {
        return bendT > 0 ? Math.max(0.1, Math.min(0.9, bendT)) : 0.5;
    }

    /** True for a line or arrow that has been pulled into a curve. */
    public boolean curved() {
        return lineLike() && Math.abs(bendOffset) > 0.5;
    }

    /** The point on the curve that the bend handle sits on: its most extreme point. */
    public double[] bendPoint() {
        double t = bendPosition(), len = Math.hypot(w, h);
        double bx = x + w * t, by = y + h * t;
        if (len < 1e-9) return new double[]{bx, by};
        return new double[]{bx - h / len * bendOffset, by + w / len * bendOffset};
    }

    /**
     * The control point of the quadratic curve (on the straight line when it is not curved). It is chosen so the
     * curve's most extreme point, the one farthest from the straight line, is exactly the bend point: along the
     * line that point is at distance d from the start, so the control point sits at 2d - length/2, and its pull to
     * the side is twice the bend offset.
     */
    public double[] controlPoint() {
        double len = Math.hypot(w, h);
        if (len < 1e-9) return new double[]{x, y};
        double ux = w / len, uy = h / len, d = bendPosition() * len;
        double along = 2 * d - len / 2, side = 2 * bendOffset;
        return new double[]{x + ux * along - uy * side, y + uy * along + ux * side};
    }

    /**
     * Moves the bend point to (px, py): how far along the line sets where it bends, how far to the side sets how
     * much. A sideways pull smaller than {@code snap} counts as straight.
     */
    public void bendTo(double px, double py, double snap) {
        double len = Math.hypot(w, h);
        if (len < 1) return;
        double ux = w / len, uy = h / len, rx = px - x, ry = py - y;
        bendT = Math.max(0.1, Math.min(0.9, (rx * ux + ry * uy) / len));
        double off = -rx * uy + ry * ux;
        bendOffset = Math.abs(off) < snap ? 0 : off;
    }

    /** The line or curve as {@code n + 1} points from start to end. */
    public double[][] curvePoints(int n) {
        double[][] out = new double[n + 1][2];
        double[] c = controlPoint();
        double ex = x + w, ey = y + h;
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n, u = 1 - t;
            out[i][0] = u * u * x + 2 * u * t * c[0] + t * t * ex;
            out[i][1] = u * u * y + 2 * u * t * c[1] + t * t * ey;
        }
        return out;
    }

    /** Bounds of a curved line or arrow, including how far the curve bulges: {minX, minY, maxX, maxY}. */
    private double[] curveBounds() {
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (double[] p : curvePoints(24)) {
            b[0] = Math.min(b[0], p[0]);
            b[1] = Math.min(b[1], p[1]);
            b[2] = Math.max(b[2], p[0]);
            b[3] = Math.max(b[3], p[1]);
        }
        return b;
    }

    public double left() { return curved() ? curveBounds()[0] : Math.min(x, x + w); }
    public double top() { return curved() ? curveBounds()[1] : Math.min(y, y + h); }
    public double right() { return curved() ? curveBounds()[2] : Math.max(x, x + w); }
    public double bottom() { return curved() ? curveBounds()[3] : Math.max(y, y + h); }

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
        o.text = text; o.fontSize = fontSize; o.wrapWidth = wrapWidth; o.bendT = bendT; o.bendOffset = bendOffset; o.bold = bold; o.italic = italic;
        o.image = image; o.link = link; o.note = note;
        for (java.util.Map.Entry<String, SpanRef> e : refs.entrySet()) o.refs.put(e.getKey(), e.getValue().copy());
        o.dismissedNames.addAll(dismissedNames);
        o.points = new ArrayList<>(points);
        return o;
    }
}
