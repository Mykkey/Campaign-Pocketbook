package dnd.map;

import dnd.model.BattleMap;
import dnd.model.MapLight;
import dnd.model.MapObject;
import dnd.model.MapPin;
import dnd.model.MapToken;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Changes the size of a battlemap after it was made: adding or cropping rows and columns on any side (everything keeps its
 * place on the map), or changing the size of the cells (pieces grow or shrink with them). Nothing here touches the screen;
 * the editor makes a resize one undoable step.
 */
public final class BattleResize {
    private BattleResize() { }

    public static final int MIN_CELLS = 3, MAX_CELLS = 200;
    public static final double MIN_CELL_SIZE = 20, MAX_CELL_SIZE = 200;

    /** The rows or columns to add on each side; a negative number crops that many instead. */
    public record Margins(int left, int right, int top, int bottom) {
        public static final Margins NONE = new Margins(0, 0, 0, 0);

        public boolean isNone() {
            return left == 0 && right == 0 && top == 0 && bottom == 0;
        }
    }

    public static int newCols(BattleMap m, Margins g) {
        return m.cols + g.left() + g.right();
    }

    public static int newRows(BattleMap m, Margins g) {
        return m.rows + g.top() + g.bottom();
    }

    /** True if the new size is allowed: within the limits, and (on a hex grid) with an even number of rows added at the top. */
    public static boolean allowed(BattleMap m, Margins g) {
        int c = newCols(m, g), r = newRows(m, g);
        if (c < MIN_CELLS || c > MAX_CELLS || r < MIN_CELLS || r > MAX_CELLS) return false;
        return m.gridType != BattleMap.GridType.HEX || g.top() % 2 == 0;
    }

    /** Why the size is not allowed, in words for the person, or null if it is. */
    public static String problem(BattleMap m, Margins g) {
        int c = newCols(m, g), r = newRows(m, g);
        if (c < MIN_CELLS || r < MIN_CELLS) return "A map needs at least " + MIN_CELLS + " columns and " + MIN_CELLS + " rows.";
        if (c > MAX_CELLS || r > MAX_CELLS) return "A map can be at most " + MAX_CELLS + " columns and " + MAX_CELLS + " rows.";
        if (m.gridType == BattleMap.GridType.HEX && g.top() % 2 != 0) return "On a hex map, rows are added to or cut from the top two at a time (so the hexes line up).";
        int out = tokensOutside(m, g);
        if (out > 0) return out + (out == 1 ? " token would be cut off. Move it first, or crop less." : " tokens would be cut off. Move them first, or crop less.");
        return null;
    }

    /** How many tokens would no longer fit on the map after the change (they are never removed, so a change that cuts one off is refused). */
    public static int tokensOutside(BattleMap m, Margins g) {
        int c = newCols(m, g), r = newRows(m, g);
        int n = 0;
        for (MapToken t : m.tokens) {
            int col = t.col + g.left(), row = t.row + g.top();
            int size = Math.max(1, t.size);
            if (col < 0 || row < 0 || col + size > c || row + size > r) n++;
        }
        return n;
    }

    /**
     * Adds or crops rows and columns. Terrain, revealed cells, tokens, pieces, pins and lights keep their place relative to the
     * rest of the map; those that end up outside are removed (tokens are never removed: see {@link #problem}).
     *
     * @return false (and changes nothing) if {@link #problem} says the change cannot be made
     */
    public static boolean resizeCanvas(BattleMap m, Margins g) {
        if (g.isNone()) return true;
        if (problem(m, g) != null) return false;
        Grid before = Grid.of(m);
        double[] a = before.center(0, 0), b = before.center(g.left(), g.top());
        double dx = b[0] - a[0], dy = b[1] - a[1];
        int newCols = newCols(m, g), newRows = newRows(m, g);

        m.terrain = shifted(m.terrain, g, newCols, newRows);
        Set<String> rev = new LinkedHashSet<>();
        for (String k : m.revealed) {
            String moved = shiftKey(k, g, newCols, newRows);
            if (moved != null) rev.add(moved);
        }
        m.revealed = rev;
        for (MapToken t : m.tokens) {
            t.col += g.left();
            t.row += g.top();
        }
        m.cols = newCols;
        m.rows = newRows;
        Grid after = Grid.of(m);
        double w = after.width(), h = after.height();
        for (MapObject o : m.objects) o.move(dx, dy);
        m.objects.removeIf(o -> o.x < 0 || o.y < 0 || o.x > w || o.y > h);
        for (MapPin p : m.pins) {
            p.x += dx;
            p.y += dy;
        }
        m.pins.removeIf(p -> p.x < 0 || p.y < 0 || p.x > w || p.y > h);
        for (MapLight l : m.lights) {
            l.x += dx;
            l.y += dy;
        }
        m.lights.removeIf(l -> l.tokenId == null && (l.x < 0 || l.y < 0 || l.x > w || l.y > h));
        return true;
    }

    private static Map<String, String> shifted(Map<String, String> in, Margins g, int newCols, int newRows) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : in.entrySet()) {
            String moved = shiftKey(e.getKey(), g, newCols, newRows);
            if (moved != null) out.put(moved, e.getValue());
        }
        return out;
    }

    /** A "col,row" key moved to its place after the change, or null if that cell is gone (or the key is damaged). */
    static String shiftKey(String key, Margins g, int newCols, int newRows) {
        int comma = key.indexOf(',');
        if (comma < 0) return null;
        try {
            int col = Integer.parseInt(key.substring(0, comma)) + g.left();
            int row = Integer.parseInt(key.substring(comma + 1)) + g.top();
            return col < 0 || row < 0 || col >= newCols || row >= newRows ? null : BattleMap.key(col, row);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Changes the size of the cells; pieces, pins and lights grow or shrink with them so the map looks the same, only bigger or smaller. */
    public static void setCellSize(BattleMap m, double newSize) {
        double size = Math.max(MIN_CELL_SIZE, Math.min(MAX_CELL_SIZE, newSize));
        double k = size / m.cellSize;
        for (MapObject o : m.objects) {
            o.x *= k;
            o.y *= k;
            o.w *= k;
            o.h *= k;
        }
        for (MapPin p : m.pins) {
            p.x *= k;
            p.y *= k;
        }
        for (MapLight l : m.lights) {
            l.x *= k;
            l.y *= k;
        }
        m.cellSize = size;
    }
}
