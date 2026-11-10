package dnd.map.world;

import dnd.map.AssetLibrary;
import dnd.model.WorldMap;
import dnd.model.WorldPath;
import dnd.model.WorldStamp;
import dnd.model.WorldStyle;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** The scale bar and the legend (a key to the symbols and lines used), drawn on the map itself. */
public final class WorldLegend {
    private WorldLegend() { }

    /** One row of the legend: a symbol or a kind of line. */
    public record Entry(String feature, WorldPath.Type line, String name) { }

    /** A unit that keeps the key in proportion to the map: about 1/110 of its width. */
    public static double unit(WorldMap map) {
        return Math.max(2, map.cols / 110.0);
    }

    /** The symbols and lines the map uses, in the library's order. */
    public static List<Entry> entries(WorldMap map) {
        TreeSet<String> used = new TreeSet<>();
        for (WorldStamp s : map.stamps) {
            if (WorldLabels.isLabel(s) || WorldLabels.isBanner(s) || s.feature == null) continue;
            WorldAssets.Feature f = WorldAssets.get(s.feature);
            if (f != null && !"Decorations".equals(f.category())) used.add(f.id());
        }
        List<Entry> out = new ArrayList<>();
        List<String> cats = WorldAssets.categories();
        Map<Integer, List<Entry>> byCat = new LinkedHashMap<>();
        for (String id : used) {
            WorldAssets.Feature f = WorldAssets.get(id);
            byCat.computeIfAbsent(cats.indexOf(f.category()), k -> new ArrayList<>()).add(new Entry(id, null, f.name()));
        }
        byCat.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> out.addAll(e.getValue()));
        TreeSet<WorldPath.Type> lines = new TreeSet<>();
        for (WorldPath p : map.paths) lines.add(p.type);
        for (WorldPath.Type t : lines) out.add(new Entry(null, t, t.label));
        return out;
    }

    private record Layout(double x, double y, double w, double h, int perColumn, int columns, double rowH, double colW) { }

    private static Layout layout(WorldMap map, List<Entry> entries) {
        double u = unit(map), rowH = u * 2.2, iconW = u * 3.4;
        double textW = 0;
        for (Entry e : entries) textW = Math.max(textW, WorldLabels.measure(map.style, e.name()) * u * 1.15);
        double colW = iconW + textW + u * 1.2;
        int perColumn = Math.max(1, Math.min(entries.size(), 12));
        int columns = Math.max(1, (entries.size() + perColumn - 1) / perColumn);
        double title = u * 2.6;
        double w = u + columns * colW, h = u * 0.6 + title + perColumn * rowH + u * 0.4;
        double x = map.legendX != null ? map.legendX : u;
        double y = map.legendY != null ? map.legendY : map.rows - u - h;
        return new Layout(x, y, w, h, perColumn, columns, rowH, colW);
    }

    /** The legend's box {x, y, width, height} in map cells, or null if it is not shown or there is nothing to show. */
    public static double[] bounds(WorldMap map) {
        if (!map.showLegend) return null;
        List<Entry> entries = entries(map);
        if (entries.isEmpty()) return null;
        Layout l = layout(map, entries);
        return new double[]{l.x, l.y, l.w, l.h};
    }

    private static Color ink(WorldMap map) {
        int c = WorldPalette.of(map.style).ink;
        return Color.rgb((c >> 16) & 255, (c >> 8) & 255, c & 255);
    }

    private static Color paper(WorldMap map, double alpha) {
        if (map.style == WorldStyle.DARK_FANTASY) return Color.web("#14161a", alpha);
        int c = WorldPalette.of(map.style).parchment;
        return Color.rgb((c >> 16) & 255, (c >> 8) & 255, c & 255, alpha);
    }

    private static Color text(WorldMap map) {
        return map.style == WorldStyle.DARK_FANTASY ? Color.web("#e8ddc4") : ink(map);
    }

    /** Draws the scale bar and, if turned on, the legend. The transform must be map cells. */
    public static void draw(GraphicsContext g, WorldMap map, AssetLibrary lib, double zoom) {
        if (map.showScale) drawScale(g, map);
        if (map.showLegend) drawLegend(g, map, lib, zoom);
    }

    private static void drawScale(GraphicsContext g, WorldMap map) {
        double u = unit(map);
        double miles = WorldTravel.niceLength(map.milesAcross() * 0.2);
        double len = miles / map.milesPerCell;
        double x = map.cols - u * 4.6 - len, y = map.rows - u * 2.2, h = u * 0.7;
        Color txt = text(map);
        g.save();
        g.setFill(paper(map, 0.7));
        g.fillRoundRect(x - u * 1.6, y - u * 2.4, len + u * 5.2, u * 4.6, u, u);
        int segments = 4;
        for (int i = 0; i < segments; i++) {
            g.setFill(i % 2 == 0 ? txt : paper(map, 1));
            g.fillRect(x + len / segments * i, y, len / segments, h);
        }
        g.setStroke(txt);
        g.setLineWidth(u * 0.12);
        g.strokeRect(x, y, len, h);
        g.setFill(txt);
        g.setFont(Font.font("SansSerif", u * 1.1));
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText("0", x, y - u * 0.5);
        g.fillText(WorldTravel.distance(miles), x + len, y - u * 0.5);
        g.restore();
    }

    private static void drawLegend(GraphicsContext g, WorldMap map, AssetLibrary lib, double zoom) {
        List<Entry> entries = entries(map);
        if (entries.isEmpty()) return;
        Layout l = layout(map, entries);
        double u = unit(map);
        Color txt = text(map);
        g.save();
        g.setFill(paper(map, 0.88));
        g.fillRoundRect(l.x, l.y, l.w, l.h, u, u);
        g.setStroke(txt);
        g.setLineWidth(u * 0.1);
        g.strokeRoundRect(l.x, l.y, l.w, l.h, u, u);
        g.setFill(txt);
        g.setTextAlign(TextAlignment.LEFT);
        g.setFont(Font.font("SansSerif", javafx.scene.text.FontWeight.BOLD, u * 1.4));
        g.fillText("Legend", l.x + u * 0.7, l.y + u * 2.0);
        g.setFont(Font.font("SansSerif", u * 1.15));
        WorldStyle style = map.style;
        WorldPalette pal = WorldPalette.of(style);
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            int col = i / l.perColumn, row = i % l.perColumn;
            double ex = l.x + u * 0.5 + col * l.colW, ey = l.y + u * 0.6 + u * 2.6 + row * l.rowH;
            double iconW = u * 3.0, iconH = l.rowH * 0.9;
            if (e.feature() != null) {
                WorldAssets.Feature f = WorldAssets.get(e.feature());
                Image img = WorldAssets.image(lib, style, f.id(), iconH * zoom * f.w() / f.h());
                if (img != null) {
                    double ar = f.w() / f.h();
                    double w = Math.min(iconW, iconH * ar), h = w / ar;
                    g.setImageSmoothing(style != WorldStyle.RETRO_PIXEL);
                    g.drawImage(img, ex + (iconW - w) / 2, ey + (iconH - h) / 2, w, h);
                }
            } else {
                int c = switch (e.line()) {
                    case RIVER -> pal.lake;
                    case ROAD, TRAIL -> pal.road;
                    case BORDER -> pal.border;
                    default -> pal.ink;
                };
                g.setStroke(Color.rgb((c >> 16) & 255, (c >> 8) & 255, c & 255));
                g.setLineWidth(u * (e.line() == WorldPath.Type.RIVER ? 0.45 : 0.3));
                if (e.line() == WorldPath.Type.TRAIL || e.line() == WorldPath.Type.SEA_ROUTE) g.setLineDashes(u * 0.6, u * 0.5);
                g.strokeLine(ex, ey + iconH / 2, ex + iconW, ey + iconH / 2);
                g.setLineDashes((double[]) null);
            }
            g.setFill(txt);
            g.fillText(e.name(), ex + iconW + u * 0.6, ey + iconH * 0.68);
        }
        g.restore();
    }
}
