package dnd.map.world;

import dnd.model.PaintLayer;
import dnd.model.WorldMap;
import dnd.model.WorldObjects;
import dnd.model.WorldPath;
import dnd.model.WorldRegion;
import dnd.model.WorldStamp;

import java.util.Iterator;

/**
 * Changes the size of a world map after it was made: scaling the whole map (everything shrinks or grows with it) or changing
 * the canvas (adding or cropping margins while everything keeps its size). Painting is resampled; symbols, lines, regions
 * and the legend move and scale with it. A resize can be undone by putting back a {@link State} taken beforehand.
 */
public final class WorldResize {
    private WorldResize() { }

    public static final int MIN_COLS = 64, MIN_ROWS = 48, MAX_COLS = 4096, MAX_ROWS = 3072;

    /** Everything a resize changes, so it can be put back. */
    public record State(int cols, int rows, double milesPerCell, Double legendX, Double legendY,
                        PaintLayer base, PaintLayer biome, PaintLayer reveal, String objects) { }

    public static State capture(WorldMap m) {
        return new State(m.cols, m.rows, m.milesPerCell, m.legendX, m.legendY, m.base().copy(), m.biome().copy(), m.reveal().copy(),
                WorldObjects.capture(m));
    }

    public static void restore(WorldMap m, State s) {
        m.cols = s.cols();
        m.rows = s.rows();
        m.milesPerCell = s.milesPerCell();
        m.legendX = s.legendX();
        m.legendY = s.legendY();
        m.replaceLayers(s.base().copy(), s.biome().copy(), s.reveal().copy());
        WorldObjects.restore(m, s.objects());
        finish(m);
    }

    /** The height that keeps the map's proportions when it is scaled to a new width. */
    public static int scaledRows(WorldMap m, int newCols) {
        return Math.max(1, (int) Math.round(m.rows * (double) newCols / m.cols));
    }

    /** Scales the whole map to a new width (the height follows). Everything on it scales too. */
    public static void scale(WorldMap m, int newCols, double newMilesPerCell) {
        int newRows = scaledRows(m, newCols);
        double k = (double) newCols / m.cols;
        apply(m, newCols, newRows, k, 0, 0, newMilesPerCell, false);
    }

    /**
     * Changes the canvas: it becomes newCols x newRows cells, and what was at (0, 0) moves to (left, top) (negative numbers crop).
     * Nothing changes size. Symbols left outside are removed; lines and regions are held at the edge.
     */
    public static void canvas(WorldMap m, int left, int top, int newCols, int newRows, double newMilesPerCell) {
        apply(m, newCols, newRows, 1, left, top, newMilesPerCell, true);
    }

    /** How many symbols a canvas change would leave outside the map (and so remove). */
    public static int symbolsLost(WorldMap m, int left, int top, int newCols, int newRows) {
        int n = 0;
        for (WorldStamp s : m.stamps) if (!inside(s.x + left, s.y + top, newCols, newRows)) n++;
        return n;
    }

    private static boolean inside(double x, double y, int cols, int rows) {
        return x >= 0 && y >= 0 && x < cols && y < rows;
    }

    private static void apply(WorldMap m, int newCols, int newRows, double k, double dx, double dy, double newMilesPerCell, boolean dropOutside) {
        newCols = Math.max(MIN_COLS, Math.min(MAX_COLS, newCols));
        newRows = Math.max(MIN_ROWS, Math.min(MAX_ROWS, newRows));
        int step = WorldMap.REVEAL_STEP;
        // paint: each new cell takes the old cell at the same place on the map
        PaintLayer base = resample(m.base(), newCols, newRows, 1, k, dx, dy);
        PaintLayer biome = resample(m.biome(), newCols, newRows, 1, k, dx, dy);
        PaintLayer reveal = resample(m.reveal(), (newCols + step - 1) / step, (newRows + step - 1) / step, step, k, dx, dy);

        // symbols, lines, regions and the legend
        for (Iterator<WorldStamp> it = m.stamps.iterator(); it.hasNext(); ) {
            WorldStamp s = it.next();
            if (k != 1) {
                // a symbol with no size of its own follows its default size, so write that down before scaling it
                s.size = (WorldLabels.isLabel(s) ? WorldLabels.sizeOf(s) : WorldStampRenderer.widthOf(s, m.style)) * k;
            }
            s.x = s.x * k + dx;
            s.y = s.y * k + dy;
            if (dropOutside && !inside(s.x, s.y, newCols, newRows)) it.remove();
        }
        for (WorldPath p : m.paths) {
            if (k != 1) p.width = p.drawnWidth() * k;
            movePoints(p.points, k, dx, dy, dropOutside, newCols, newRows);
        }
        for (WorldRegion r : m.regions) {
            movePoints(r.points, k, dx, dy, dropOutside, newCols, newRows);
            if (r.labelX != null && r.labelY != null) {
                r.labelX = r.labelX * k + dx;
                r.labelY = r.labelY * k + dy;
            }
        }
        if (m.legendX != null) m.legendX = m.legendX * k + dx;
        if (m.legendY != null) m.legendY = m.legendY * k + dy;

        m.cols = newCols;
        m.rows = newRows;
        m.milesPerCell = Math.max(0.01, newMilesPerCell);
        m.replaceLayers(base, biome, reveal);
        finish(m);
    }

    private static void movePoints(java.util.List<Double> pts, double k, double dx, double dy, boolean clamp, int cols, int rows) {
        for (int i = 0; i + 1 < pts.size(); i += 2) {
            double x = pts.get(i) * k + dx, y = pts.get(i + 1) * k + dy;
            if (clamp) {
                x = Math.max(0, Math.min(cols, x));
                y = Math.max(0, Math.min(rows, y));
            }
            pts.set(i, x);
            pts.set(i + 1, y);
        }
    }

    /**
     * A new layer of the given size in which each cell (cell size {@code step} map cells) takes the value of the old
     * cell found at the same place on the map. A new map position p came from old position (p - d) / k.
     */
    static PaintLayer resample(PaintLayer old, int w, int h, int step, double k, double dx, double dy) {
        PaintLayer out = new PaintLayer(w, h);
        for (int y = 0; y < h; y++) {
            int oy = (int) Math.floor((((y + 0.5) * step - dy) / k) / step);
            if (oy < 0 || oy >= old.h) continue;
            for (int x = 0; x < w; x++) {
                int ox = (int) Math.floor((((x + 0.5) * step - dx) / k) / step);
                if (ox < 0 || ox >= old.w) continue;
                out.cells[y * w + x] = old.cells[oy * old.w + ox];
            }
        }
        return out;
    }

    /** The picture of the land and sea must be rebuilt by whoever shows the map ({@code WorldTerrainCache.of(m).invalidateAll()}). */
    private static void finish(WorldMap m) {
        m.revision++;
        m.revealVersion++;
    }
}
