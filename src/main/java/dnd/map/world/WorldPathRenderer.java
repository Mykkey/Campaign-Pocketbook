package dnd.map.world;

import dnd.model.WorldMap;
import dnd.model.WorldPath;
import dnd.model.WorldRegion;
import dnd.model.WorldStyle;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Draws and hit-tests the rivers, roads, borders and regions of a world map. All coordinates are map cells. */
public final class WorldPathRenderer {
    private WorldPathRenderer() { }

    private static Color color(int argb, double alpha) {
        return Color.rgb((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, alpha);
    }

    /** Regions first (so lines run over them), then lines with rivers at the bottom and roads on top. */
    public static void draw(GraphicsContext g, WorldMap map, double zoom, double x0, double y0, double x1, double y1, boolean showHidden) {
        for (WorldRegion r : map.regions) {
            if (r.hidden && !showHidden) continue;
            drawRegion(g, map, r, zoom, r.hidden);
        }
        List<WorldPath> list = new ArrayList<>(map.paths);
        list.sort(Comparator.comparingInt(p -> order(p.type)));
        for (WorldPath p : list) {
            if (p.hidden && !showHidden) continue;
            if (p.count() < 2) continue;
            drawPath(g, map, p, zoom, p.hidden);
        }
        g.setLineDashes((double[]) null);
        g.setGlobalAlpha(1);
    }

    private static int order(WorldPath.Type t) {
        return switch (t) {
            case RIVER -> 0;
            case SEA_ROUTE -> 1;
            case BORDER -> 2;
            case RAILWAY -> 3;
            case TRAIL -> 4;
            case ROAD -> 5;
        };
    }

    // ---------------------------------------------------------------- regions

    private static void drawRegion(GraphicsContext g, WorldMap map, WorldRegion r, double zoom, boolean faded) {
        int n = r.count();
        if (n < 2) return;
        WorldPalette pal = WorldPalette.of(map.style);
        Color tint = r.tint != null ? Color.web(r.tint) : color(pal.border, 1);
        double[] xs = new double[n], ys = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = r.x(i);
            ys[i] = r.y(i);
        }
        if (faded) g.setGlobalAlpha(0.5);
        if (n >= 3) {
            g.setFill(Color.color(tint.getRed(), tint.getGreen(), tint.getBlue(), map.style == WorldStyle.DARK_FANTASY ? 0.22 : 0.16));
            g.fillPolygon(xs, ys, n);
        }
        double w = Math.max(0.6, 1.5 / zoom);
        g.setStroke(color(pal.ink, map.style == WorldStyle.WATERCOLOUR ? 0.5 : 0.85));
        g.setLineWidth(w);
        g.setLineDashes(w * 3.2, w * 1.4);
        g.setLineJoin(StrokeLineJoin.ROUND);
        if (n >= 3) g.strokePolygon(xs, ys, n);
        else g.strokePolyline(xs, ys, n);
        g.setLineDashes((double[]) null);
        if (r.name != null && !r.name.isBlank() && n >= 3) {
            double[] c = r.centre();
            double size = Math.max(2.5, Math.min(18, Math.sqrt(r.area()) * 0.09));
            g.setFont(Font.font("Serif", FontWeight.BOLD, size));
            g.setTextAlign(TextAlignment.CENTER);
            g.setFill(color(pal.ink, map.style == WorldStyle.DARK_FANTASY ? 0.0 : 0.0));
            g.setFill(map.style == WorldStyle.DARK_FANTASY ? Color.web("#d8d2c4", 0.85) : color(pal.ink, 0.75));
            g.fillText(r.name.toUpperCase(), c[0], c[1]);
        }
        g.setGlobalAlpha(1);
    }

    // ---------------------------------------------------------------- lines

    /** Draws one line (used while it is being drawn). */
    public static void drawOne(GraphicsContext g, WorldMap map, WorldPath p, double zoom) {
        drawPath(g, map, p, zoom, false);
        g.setLineDashes((double[]) null);
    }

    private static void drawPath(GraphicsContext g, WorldMap map, WorldPath p, double zoom, boolean faded) {
        WorldStyle style = p.style == null ? map.style : p.style;
        WorldPalette pal = WorldPalette.of(style);
        List<double[]> pts = WorldPathGeometry.smooth(p, 10);
        double w = Math.max(p.drawnWidth(), 1.3 / zoom);
        if (faded) g.setGlobalAlpha(0.5);
        g.setLineJoin(StrokeLineJoin.ROUND);
        g.setLineCap(StrokeLineCap.ROUND);
        switch (p.type) {
            case RIVER -> river(g, pts, w, pal, style, zoom);
            case ROAD -> {
                stroke(g, pts, w + 1.8 / zoom, color(pal.ink, style == WorldStyle.WATERCOLOUR ? 0.4 : 0.7), null);
                stroke(g, pts, w, color(pal.road, 1), null);
            }
            case TRAIL -> {
                g.setLineCap(StrokeLineCap.BUTT);
                stroke(g, pts, w, color(pal.road, 1), new double[]{w * 2.6, w * 2.0});
            }
            case SEA_ROUTE -> {
                g.setLineCap(StrokeLineCap.BUTT);
                stroke(g, pts, w, style == WorldStyle.DARK_FANTASY ? color(pal.oceanShallow, 0.9) : color(pal.ink, 0.8), new double[]{w * 1.4, w * 2.2});
            }
            case BORDER -> {
                g.setLineCap(StrokeLineCap.BUTT);
                stroke(g, pts, w, color(pal.border, 1), new double[]{w * 3.2, w * 1.1, w * 0.9, w * 1.1});
            }
            case RAILWAY -> railway(g, pts, w, pal);
        }
        g.setLineDashes((double[]) null);
        g.setLineCap(StrokeLineCap.ROUND);
        g.setGlobalAlpha(1);
    }

    private static void stroke(GraphicsContext g, List<double[]> pts, double width, Color c, double[] dashes) {
        g.setStroke(c);
        g.setLineWidth(width);
        g.setLineDashes(dashes);
        g.beginPath();
        g.moveTo(pts.get(0)[0], pts.get(0)[1]);
        for (int i = 1; i < pts.size(); i++) g.lineTo(pts.get(i)[0], pts.get(i)[1]);
        g.stroke();
    }

    private static void river(GraphicsContext g, List<double[]> pts, double w, WorldPalette pal, WorldStyle style, double zoom) {
        List<List<double[]>> sides = WorldPathGeometry.ribbon(pts, w, 0.3);
        List<double[]> left = sides.get(0), right = sides.get(1);
        int n = left.size();
        double[] xs = new double[2 * n], ys = new double[2 * n];
        for (int i = 0; i < n; i++) {
            xs[i] = left.get(i)[0];
            ys[i] = left.get(i)[1];
            xs[2 * n - 1 - i] = right.get(i)[0];
            ys[2 * n - 1 - i] = right.get(i)[1];
        }
        g.setFill(color(pal.lake, style == WorldStyle.WATERCOLOUR ? 0.9 : 1));
        g.fillPolygon(xs, ys, 2 * n);
        double lw = Math.max(0.12, 0.9 / zoom);
        Color edge = style == WorldStyle.CLASSIC_INK ? color(pal.ink, 0.9)
                : style == WorldStyle.DARK_FANTASY ? Color.web("#000000", 0.7)
                : style == WorldStyle.WATERCOLOUR ? color(pal.oceanDeep, 0.35) : color(pal.oceanDeep, 0.9);
        stroke(g, left, lw, edge, null);
        stroke(g, right, lw, edge, null);
    }

    private static void railway(GraphicsContext g, List<double[]> pts, double w, WorldPalette pal) {
        stroke(g, pts, w * 0.5, color(pal.ink, 0.9), null);
        double spacing = Math.max(0.6, w * 1.8), next = spacing, walked = 0;
        g.setStroke(color(pal.ink, 0.9));
        g.setLineWidth(w * 0.3);
        g.setLineCap(StrokeLineCap.BUTT);
        for (int i = 0; i + 1 < pts.size(); i++) {
            double[] a = pts.get(i), b = pts.get(i + 1);
            double seg = Math.hypot(b[0] - a[0], b[1] - a[1]);
            if (seg == 0) continue;
            double nx = -(b[1] - a[1]) / seg, ny = (b[0] - a[0]) / seg;
            while (next <= walked + seg) {
                double t = (next - walked) / seg;
                double cx = a[0] + (b[0] - a[0]) * t, cy = a[1] + (b[1] - a[1]) * t;
                g.strokeLine(cx - nx * w * 0.8, cy - ny * w * 0.8, cx + nx * w * 0.8, cy + ny * w * 0.8);
                next += spacing;
            }
            walked += seg;
        }
    }

    // ---------------------------------------------------------------- hit testing

    /** The topmost line within {@code tol} cells of a point, or null. */
    public static WorldPath hitPath(WorldMap map, double px, double py, double tol, boolean includeHidden) {
        List<WorldPath> list = new ArrayList<>(map.paths);
        list.sort(Comparator.comparingInt(p -> order(p.type)));
        for (int i = list.size() - 1; i >= 0; i--) {
            WorldPath p = list.get(i);
            if (p.count() < 2 || (p.hidden && !includeHidden)) continue;
            if (WorldPathGeometry.distance(WorldPathGeometry.smooth(p, 8), px, py) <= tol + p.drawnWidth() / 2) return p;
        }
        return null;
    }

    /** The smallest region that contains a point, or null. */
    public static WorldRegion hitRegion(WorldMap map, double px, double py, boolean includeHidden) {
        WorldRegion best = null;
        for (WorldRegion r : map.regions) {
            if (r.count() < 3 || (r.hidden && !includeHidden) || !r.contains(px, py)) continue;
            if (best == null || r.area() < best.area()) best = r;
        }
        return best;
    }
}
