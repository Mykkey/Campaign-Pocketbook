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
        o.image = image; o.link = link; o.note = note;
        for (java.util.Map.Entry<String, SpanRef> e : refs.entrySet()) o.refs.put(e.getKey(), e.getValue().copy());
        o.dismissedNames.addAll(dismissedNames);
        o.points = new ArrayList<>(points);
        return o;
    }
}
