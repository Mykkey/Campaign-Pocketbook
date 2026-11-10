package dnd.map.world;

import dnd.map.AssetLibrary;
import dnd.model.WorldMap;
import dnd.model.WorldStamp;
import dnd.model.WorldStyle;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Draws and hit-tests the symbols placed on a world map. All coordinates are map cells. */
public final class WorldStampRenderer {
    private WorldStampRenderer() { }

    /** The symbol's width in map cells. */
    public static double widthOf(WorldStamp s) {
        WorldAssets.Feature f = WorldAssets.get(s.feature);
        if (s.size > 0) return s.size;
        return f == null ? 4 : f.w();
    }

    public static double heightOf(WorldStamp s) {
        WorldAssets.Feature f = WorldAssets.get(s.feature);
        double w = widthOf(s);
        return f == null ? w : w * f.h() / f.w();
    }

    /** Distance from the stamp's point up to the top of its picture (negative y), in cells. */
    private static double topOf(WorldStamp s) {
        WorldAssets.Feature f = WorldAssets.get(s.feature);
        double h = heightOf(s);
        return f != null && f.centered() ? -h / 2 : -h;
    }

    /** Symbols in drawing order: those further up the map first, so nearer ones overlap them. */
    public static List<WorldStamp> inOrder(WorldMap map) {
        List<WorldStamp> list = new ArrayList<>(map.stamps);
        list.sort(Comparator.comparingDouble((WorldStamp s) -> s.y).thenComparing(s -> s.id));
        return list;
    }

    /**
     * Draws the symbols that touch the area x0..x1, y0..y1.
     *
     * @param pxPerCell how many screen pixels a cell is (picks how sharp the pictures are)
     * @param showHidden draw the symbols hidden from the players (faded), as the DM sees them
     */
    public static void draw(GraphicsContext g, WorldMap map, AssetLibrary lib, double pxPerCell, double x0, double y0, double x1, double y1,
                            boolean showHidden) {
        for (WorldStamp s : inOrder(map)) {
            if (s.hidden && !showHidden) continue;
            WorldAssets.Feature f = WorldAssets.get(s.feature);
            if (f == null) continue;
            double w = widthOf(s), h = heightOf(s);
            double reach = Math.max(w, h);
            if (s.x + reach < x0 || s.x - reach > x1 || s.y + reach < y0 || s.y - reach > y1) continue;
            WorldStyle style = s.style == null ? map.style : s.style;
            Image img = WorldAssets.image(lib, style, f.id(), w * pxPerCell);
            if (img == null) continue;
            g.save();
            g.translate(s.x, s.y);
            g.rotate(s.rotation);
            if (s.flipX) g.scale(-1, 1);
            g.setImageSmoothing(style != WorldStyle.RETRO_PIXEL);
            if (s.hidden) g.setGlobalAlpha(0.45);
            g.drawImage(img, -w / 2, topOf(s), w, h);
            g.restore();
        }
        g.setImageSmoothing(true);
    }

    /** True if the point (px, py) is on the symbol's picture (its box, allowing for rotation and flip). */
    public static boolean contains(WorldStamp s, double px, double py) {
        double[] l = toLocal(s, px, py);
        double w = widthOf(s), top = topOf(s);
        return l[0] >= -w / 2 && l[0] <= w / 2 && l[1] >= top && l[1] <= top + heightOf(s);
    }

    /** The map position of the symbol's resize handle: the bottom right corner of its box. */
    public static double[] handleOf(WorldStamp s) {
        double w = widthOf(s);
        double lx = s.flipX ? -w / 2 : w / 2, ly = topOf(s) + heightOf(s);
        double a = Math.toRadians(s.rotation), cos = Math.cos(a), sin = Math.sin(a);
        return new double[]{s.x + lx * cos - ly * sin, s.y + lx * sin + ly * cos};
    }

    /** A map point in the symbol's own space (origin at its point, axes turned with it). */
    public static double[] toLocal(WorldStamp s, double px, double py) {
        double dx = px - s.x, dy = py - s.y;
        double a = Math.toRadians(-s.rotation), cos = Math.cos(a), sin = Math.sin(a);
        double lx = dx * cos - dy * sin, ly = dx * sin + dy * cos;
        return new double[]{s.flipX ? -lx : lx, ly};
    }

    /** The corners of the symbol's box in map space (for drawing a selection outline). */
    public static double[][] corners(WorldStamp s) {
        double w = widthOf(s), top = topOf(s), h = heightOf(s);
        double[][] local = {{-w / 2, top}, {w / 2, top}, {w / 2, top + h}, {-w / 2, top + h}};
        double a = Math.toRadians(s.rotation), cos = Math.cos(a), sin = Math.sin(a);
        double[][] out = new double[4][2];
        for (int i = 0; i < 4; i++) {
            double lx = s.flipX ? -local[i][0] : local[i][0], ly = local[i][1];
            out[i][0] = s.x + lx * cos - ly * sin;
            out[i][1] = s.y + lx * sin + ly * cos;
        }
        return out;
    }

    /** The topmost symbol at a point, or null. */
    public static WorldStamp hit(WorldMap map, double px, double py, boolean includeHidden) {
        List<WorldStamp> list = inOrder(map);
        for (int i = list.size() - 1; i >= 0; i--) {
            WorldStamp s = list.get(i);
            if (s.hidden && !includeHidden) continue;
            if (contains(s, px, py)) return s;
        }
        return null;
    }
}
