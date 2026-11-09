package dnd.map;

import dnd.model.BattleMap;
import dnd.model.MapObject;
import dnd.model.MapToken;
import dnd.model.Terrain;
import javafx.geometry.VPos;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a battlemap. The caller sets up the transform (map units -> pixels); this class draws in map
 * units. Used by the map editor and to build the thumbnails shown on whiteboards.
 */
public final class MapRenderer {
    private MapRenderer() { }

    public static final Color PAPER = Color.web("#efe9d8");

    private static final Map<String, Color> COLORS = new HashMap<>();

    public static Color color(String css) {
        return COLORS.computeIfAbsent(css == null ? "#888888" : css, s -> {
            try {
                return Color.web(s);
            } catch (IllegalArgumentException e) {
                return Color.GRAY;
            }
        });
    }

    /**
     * Draws everything on the map.
     *
     * @param scale pixels per map unit (picks the sharpness of asset images and the grid line width)
     */
    public static void draw(GraphicsContext g, BattleMap m, Grid grid, AssetLibrary lib, double scale) {
        draw(g, m, grid, lib, scale, m.showGrid);
    }

    /** Same as {@link #draw(GraphicsContext, BattleMap, Grid, AssetLibrary, double)} with the grid forced on or off. */
    public static void draw(GraphicsContext g, BattleMap m, Grid grid, AssetLibrary lib, double scale, boolean showGrid) {
        drawGround(g, m, grid, scale, showGrid);
        drawObjects(g, m, lib, scale);
        drawTokens(g, m, grid);
    }

    public static void drawGround(GraphicsContext g, BattleMap m, Grid grid, double scale, boolean showGrid) {
        g.setFill(PAPER);
        g.fillRect(0, 0, grid.width(), grid.height());

        drawTerrain(g, m, grid);

        // grid lines
        if (showGrid) {
            g.setStroke(Color.web("#00000040"));
            g.setLineWidth(Math.max(0.6 / scale, 0.8 / Math.max(scale, 0.01)));
            g.setLineDashes((double[]) null);
            for (int r = 0; r < grid.rows(); r++) {
                for (int c = 0; c < grid.cols(); c++) {
                    strokeCell(g, grid, c, r);
                }
            }
        }
        g.setStroke(Color.web("#3a2e28"));
        g.setLineWidth(2 / Math.max(scale, 0.01));
        g.strokeRect(0, 0, grid.width(), grid.height());
    }

    /**
     * Fills painted cells with textured ground. The texture is anchored to the map, not to each cell, so
     * it flows continuously across neighbouring cells. Where two terrains (or a terrain and bare paper)
     * meet, a slightly irregular darker edge is drawn so shorelines and paths don't look like grid lines.
     */
    private static void drawTerrain(GraphicsContext g, BattleMap m, Grid grid) {
        if (m.terrain.isEmpty()) return;
        double tile = m.cellSize * 5;
        Map<Terrain, ImagePattern> patterns = new EnumMap<>(Terrain.class);
        List<int[]> cells = new ArrayList<>(m.terrain.size());
        List<Terrain> kinds = new ArrayList<>(m.terrain.size());
        for (Map.Entry<String, String> e : m.terrain.entrySet()) {
            Terrain t = Terrain.byId(e.getValue());
            if (t == null) continue;
            String k = e.getKey();
            int comma = k.indexOf(',');
            if (comma < 0) continue;
            int col, row;
            try {
                col = Integer.parseInt(k.substring(0, comma));
                row = Integer.parseInt(k.substring(comma + 1));
            } catch (NumberFormatException ex) {
                continue;
            }
            if (!grid.inside(col, row)) continue;
            cells.add(new int[]{col, row});
            kinds.add(t);
        }

        // 1. textured fill
        for (int i = 0; i < cells.size(); i++) {
            Terrain t = kinds.get(i);
            ImagePattern p = patterns.computeIfAbsent(t, k -> new ImagePattern(TerrainTextures.get(k), 0, 0, tile, tile, false));
            double[] poly = grid.polygon(cells.get(i)[0], cells.get(i)[1]);
            int n = poly.length / 2;
            double[] xs = new double[n], ys = new double[n];
            for (int j = 0; j < n; j++) {
                xs[j] = poly[2 * j];
                ys[j] = poly[2 * j + 1];
            }
            g.setFill(p);
            g.fillPolygon(xs, ys, n);
        }

        // 2. edges between different terrains
        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineJoin(StrokeLineJoin.ROUND);
        g.setLineDashes((double[]) null);
        double cs = m.cellSize;
        for (int i = 0; i < cells.size(); i++) {
            int col = cells.get(i)[0], row = cells.get(i)[1];
            Terrain t = kinds.get(i);
            double[] poly = grid.polygon(col, row);
            double[] ctr = grid.center(col, row);
            int n = poly.length / 2;
            for (int j = 0; j < n; j++) {
                int k = (j + 1) % n;
                double ax = poly[2 * j], ay = poly[2 * j + 1], bx = poly[2 * k], by = poly[2 * k + 1];
                double mx = (ax + bx) / 2, my = (ay + by) / 2;
                double dx = mx - ctr[0], dy = my - ctr[1], len = Math.hypot(dx, dy);
                if (len == 0) continue;
                int[] nb = grid.cellAt(mx + dx / len * cs * 0.2, my + dy / len * cs * 0.2);
                Terrain nt = nb == null ? null : Terrain.byId(m.terrain.get(BattleMap.key(nb[0], nb[1])));
                if (nt == t) continue;
                if (nt != null && nt.ordinal() >= t.ordinal()) continue; // the other cell draws this edge
                strokeNaturalEdge(g, ax, ay, bx, by, cs, color(t.color).darker().darker());
            }
        }
    }

    /** A short wobbly line (soft shadow underneath, crisp line on top). */
    private static void strokeNaturalEdge(GraphicsContext g, double ax, double ay, double bx, double by,
                                          double cellSize, Color base) {
        int pts = 6;
        double[] xs = new double[pts], ys = new double[pts];
        double dx = bx - ax, dy = by - ay, len = Math.hypot(dx, dy);
        double px = -dy / len, py = dx / len; // perpendicular
        for (int i = 0; i < pts; i++) {
            double t = (double) i / (pts - 1);
            double jitter = 0;
            if (i > 0 && i < pts - 1) {
                int h = (int) Math.round(ax * 7) * 73856093 ^ (int) Math.round(ay * 7) * 19349663 ^ i * 83492791
                        ^ (int) Math.round(bx * 7) * 2654435;
                h ^= h >>> 15;
                h *= 0x2c1b3c6d;
                h ^= h >>> 12;
                jitter = (((h & 0xffff) / 65535.0) - 0.5) * cellSize * 0.07;
            }
            xs[i] = ax + dx * t + px * jitter;
            ys[i] = ay + dy * t + py * jitter;
        }
        g.setStroke(Color.color(base.getRed(), base.getGreen(), base.getBlue(), 0.14));
        g.setLineWidth(cellSize * 0.13);
        g.strokePolyline(xs, ys, pts);
        g.setStroke(Color.color(base.getRed(), base.getGreen(), base.getBlue(), 0.5));
        g.setLineWidth(cellSize * 0.04);
        g.strokePolyline(xs, ys, pts);
    }

    public static void fillCell(GraphicsContext g, Grid grid, int col, int row, Color c) {
        double[] p = grid.polygon(col, row);
        int n = p.length / 2;
        double[] xs = new double[n], ys = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = p[2 * i];
            ys[i] = p[2 * i + 1];
        }
        g.setFill(c);
        g.fillPolygon(xs, ys, n);
    }

    public static void strokeCell(GraphicsContext g, Grid grid, int col, int row) {
        double[] p = grid.polygon(col, row);
        int n = p.length / 2;
        double[] xs = new double[n], ys = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = p[2 * i];
            ys[i] = p[2 * i + 1];
        }
        g.strokePolygon(xs, ys, n);
    }

    public static void drawObjects(GraphicsContext g, BattleMap m, AssetLibrary lib, double scale) {
        for (MapObject o : m.objects) drawObject(g, o, lib, scale, 1.0);
    }

    public static void drawObject(GraphicsContext g, MapObject o, AssetLibrary lib, double scale, double opacity) {
        Image img = lib == null ? null : lib.image(o.assetId, Math.max(o.w, o.h) * scale);
        g.save();
        g.setGlobalAlpha(opacity);
        g.translate(o.x, o.y);
        g.rotate(o.rotation);
        if (o.flipX) g.scale(-1, 1);
        if (img != null) {
            g.drawImage(img, -o.w / 2, -o.h / 2, o.w, o.h);
        } else {
            g.setFill(Color.web("#ffffff99"));
            g.fillRect(-o.w / 2, -o.h / 2, o.w, o.h);
            g.setStroke(Color.RED);
            g.setLineWidth(2 / Math.max(scale, 0.01));
            g.strokeRect(-o.w / 2, -o.h / 2, o.w, o.h);
        }
        g.restore();
    }

    public static void drawTokens(GraphicsContext g, BattleMap m, Grid grid) {
        for (MapToken t : m.tokens) drawToken(g, t, grid, m.cellSize, 1.0);
    }

    public static void drawToken(GraphicsContext g, MapToken t, Grid grid, double cellSize, double opacity) {
        if (!grid.inside(t.col, t.row)) return;
        double[] c = grid.center(t.col, t.row);
        double d = cellSize * (0.78 + 0.95 * (Math.max(1, t.size) - 1));
        g.save();
        g.setGlobalAlpha(opacity);
        g.setFill(Color.web("#00000040"));
        g.fillOval(c[0] - d / 2 + d * 0.05, c[1] - d / 2 + d * 0.07, d, d);
        g.setFill(color(t.color));
        g.fillOval(c[0] - d / 2, c[1] - d / 2, d, d);
        g.setStroke(Color.web("#1d1b1a"));
        g.setLineWidth(Math.max(1.5, d * 0.05));
        g.strokeOval(c[0] - d / 2, c[1] - d / 2, d, d);
        String label = t.label == null ? "" : t.label.trim();
        if (!label.isEmpty()) {
            String shown = label.length() > 3 ? label.substring(0, 3) : label;
            g.setFill(Color.WHITE);
            g.setFont(Font.font("System", FontWeight.BOLD, d * (shown.length() > 2 ? 0.30 : 0.42)));
            g.setTextAlign(TextAlignment.CENTER);
            g.setTextBaseline(VPos.CENTER);
            g.fillText(shown, c[0], c[1]);
        }
        g.restore();
    }

    // ---------------------------------------------------------------- thumbnails for whiteboards

    private static final class Thumb {
        final int revision, pxW;
        final Image image;

        Thumb(int revision, int pxW, Image image) {
            this.revision = revision;
            this.pxW = pxW;
            this.image = image;
        }
    }

    private static final Map<String, Thumb> THUMBS = new HashMap<>();

    /**
     * A picture of the whole map about {@code wantedPxW} pixels wide, cached until the map changes
     * (its revision) or a clearly different size is needed. Must be called on the JavaFX thread.
     */
    public static Image thumbnail(BattleMap m, AssetLibrary lib, double wantedPxW) {
        int bucket = 128;
        while (bucket < wantedPxW && bucket < 2048) bucket *= 2;
        Thumb t = THUMBS.get(m.id);
        if (t != null && t.revision == m.revision && t.pxW == bucket) return t.image;

        Grid grid = Grid.of(m);
        double mw = Math.max(1, grid.width()), mh = Math.max(1, grid.height());
        double scale = bucket / mw;
        Canvas canvas = new Canvas(bucket, Math.max(1, Math.ceil(mh * scale)));
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.scale(scale, scale);
        draw(g, m, grid, lib, scale);
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        Image img = canvas.snapshot(sp, null);
        THUMBS.put(m.id, new Thumb(m.revision, bucket, img));
        return img;
    }

    /** Width : height of the map area (for sizing previews). */
    public static double aspect(BattleMap m) {
        Grid g = Grid.of(m);
        return g.height() <= 0 ? 1 : g.width() / g.height();
    }
}
