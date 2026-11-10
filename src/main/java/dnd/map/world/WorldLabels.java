package dnd.map.world;

import dnd.model.WorldMap;
import dnd.model.WorldStamp;
import dnd.model.WorldStyle;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Text on a world map: free labels (a title, the name of a sea, a town) that can follow a curve, and the text
 * written on banners. A label is a {@link WorldStamp} whose feature is {@link #FEATURE}, so it moves, turns, resizes,
 * copies and undoes like any other symbol. Its point is the middle of the baseline of the text.
 */
public final class WorldLabels {
    private WorldLabels() { }

    /** The feature id that marks a stamp as a text label. */
    public static final String FEATURE = "label";

    /** The kinds of label, each with its own look. */
    public enum Kind {
        TITLE("Map title", 16, true, 0.12, true, false),
        REGION("Region name", 9, true, 0.32, false, false),
        SEA("Sea or lake name", 6, false, 0.22, false, true),
        TOWN("Town or place", 3.2, false, 0.0, true, false),
        RIVER("River or mountain", 2.6, false, 0.06, false, true),
        NOTE("Small note", 2.2, false, 0.0, false, false);

        public final String label;
        /** Default height of the letters, in map cells. */
        public final double size;
        public final boolean caps;
        /** Extra space between letters, in letter heights. */
        public final double spacing;
        public final boolean bold, water;

        Kind(String label, double size, boolean caps, double spacing, boolean bold, boolean water) {
            this.label = label;
            this.size = size;
            this.caps = caps;
            this.spacing = spacing;
            this.bold = bold;
            this.water = water;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static boolean isLabel(WorldStamp s) {
        return FEATURE.equals(s.feature);
    }

    public static boolean isBanner(WorldStamp s) {
        return s.feature != null && s.feature.startsWith("banner-");
    }

    /** True for symbols that carry text the user can edit. */
    public static boolean hasText(WorldStamp s) {
        return isLabel(s) || isBanner(s);
    }

    public static Kind kindOf(WorldStamp s) {
        if (s.labelKind != null) {
            try {
                return Kind.valueOf(s.labelKind);
            } catch (IllegalArgumentException ignored) {
                // an unknown kind from a newer version: fall through
            }
        }
        return Kind.TOWN;
    }

    // ---------------------------------------------------------------- fonts

    private static final Map<String, String> FAMILY = new HashMap<>();
    private static final Map<String, Double> ADVANCE = new HashMap<>();

    private static String family(WorldStyle style) {
        String[] prefer = switch (style) {
            case CLASSIC_INK, WATERCOLOUR -> new String[]{"Georgia", "Palatino Linotype", "Times New Roman", "Serif"};
            case FANTASY_COLOUR -> new String[]{"Palatino Linotype", "Book Antiqua", "Georgia", "Serif"};
            case RETRO_PIXEL -> new String[]{"Consolas", "Courier New", "Monospaced"};
            case DARK_FANTASY -> new String[]{"Palatino Linotype", "Georgia", "Times New Roman", "Serif"};
        };
        return FAMILY.computeIfAbsent(style.name(), k -> {
            List<String> have = Font.getFamilies();
            for (String f : prefer) if (have.contains(f)) return f;
            return prefer[prefer.length - 1];
        });
    }

    private static Font font(WorldStyle style, Kind kind, boolean italic, double size) {
        boolean bold = kind.bold || style == WorldStyle.RETRO_PIXEL;
        return Font.font(family(style), bold ? FontWeight.BOLD : FontWeight.NORMAL, italic ? FontPosture.ITALIC : FontPosture.REGULAR, size);
    }

    private static boolean italic(Kind kind) {
        return kind.water || kind == Kind.REGION || kind == Kind.NOTE;
    }

    private static String shown(String text, Kind kind) {
        String t = text == null ? "" : text;
        return kind.caps ? t.toUpperCase() : t;
    }

    /** The width of one letter at height 1. */
    private static double advance(WorldStyle style, Kind kind, char c) {
        String key = style.name() + (kind.bold ? "b" : "n") + (italic(kind) ? "i" : "r") + c;
        Double known = ADVANCE.get(key);
        if (known != null) return known;
        Text t = new Text(String.valueOf(c == ' ' ? ' ' : c));
        t.setFont(font(style, kind, italic(kind), 10));
        double w = t.getLayoutBounds().getWidth() / 10.0;
        ADVANCE.put(key, w);
        return w;
    }

    /** The width of a whole text at height 1, letter spacing included. */
    private static double natural(WorldStyle style, Kind kind, String text) {
        double w = 0;
        for (int i = 0; i < text.length(); i++) w += advance(style, kind, text.charAt(i)) + (i < text.length() - 1 ? kind.spacing : 0);
        return w;
    }

    // ---------------------------------------------------------------- geometry (in the label's own space)

    /** How tall the letters are, in map cells. */
    public static double sizeOf(WorldStamp s) {
        return s.size > 0 ? s.size : kindOf(s).size;
    }

    /** The label's width per unit of letter height (so dragging the handle can turn a width into a size). */
    public static double ratio(WorldStamp s, WorldStyle mapStyle) {
        WorldStyle st = s.style == null ? mapStyle : s.style;
        Kind k = kindOf(s);
        String t = shown(s.text, k);
        return Math.max(0.5, natural(st, k, t.isEmpty() ? "Label" : t));
    }

    private static WorldStyle styleOf(WorldStamp s, WorldMap map) {
        return s.style == null ? map.style : s.style;
    }

    public static double width(WorldStamp s, WorldMap map) {
        return ratio(s, map.style) * sizeOf(s);
    }

    /** How far the middle of the baseline is bent away from the straight line (negative is up). */
    private static double apex(WorldStamp s, double width) {
        return s.bend * width;
    }

    /** The top of the label's box above its baseline (a negative number). */
    public static double top(WorldStamp s, WorldMap map) {
        double size = sizeOf(s);
        return -size * 0.95 + Math.min(0, apex(s, width(s, map)));
    }

    public static double height(WorldStamp s, WorldMap map) {
        double size = sizeOf(s);
        return size * 1.3 + Math.abs(apex(s, width(s, map)));
    }

    /** Points along the baseline from its left end to its right end. */
    private static double[][] curve(double width, double apex) {
        int n = 48;
        double[][] pts = new double[n + 1][2];
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n, u = 1 - t;
            pts[i][0] = u * u * (-width / 2) + t * t * (width / 2);
            pts[i][1] = 2 * u * t * (2 * apex);
        }
        return pts;
    }

    // ---------------------------------------------------------------- drawing

    private static Color ink(WorldPalette p) {
        return argb(p.ink, 1.0);
    }

    private static Color argb(int rgb, double alpha) {
        return Color.rgb((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, alpha);
    }

    private static Color mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return Color.rgb(r, g, bl);
    }

    /** Draws a free label; the current transform is map cells. */
    public static void draw(GraphicsContext g, WorldMap map, WorldStamp s) {
        String text = shown(s.text, kindOf(s));
        if (text.isEmpty()) return;
        WorldStyle style = styleOf(s, map);
        Kind kind = kindOf(s);
        WorldPalette pal = WorldPalette.of(style);
        double size = sizeOf(s);
        double w = natural(style, kind, text) * size;
        double[][] pts = curve(w, apex(s, w));
        double[] cum = new double[pts.length];
        for (int i = 1; i < pts.length; i++) cum[i] = cum[i - 1] + Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]);
        double total = cum[cum.length - 1], start = (total - w) / 2;

        boolean dark = style == WorldStyle.DARK_FANTASY;
        Color fill = dark ? (kind.water ? Color.web("#9cc4e0") : Color.web("#e8ddc4")) : kind.water ? mix(pal.ink, pal.lake, 0.5) : ink(pal);
        Color halo = argb(dark ? 0x000000 : pal.parchment, dark ? 0.7 : 0.75);
        Font f = font(style, kind, italic(kind), size);
        g.save();
        g.translate(s.x, s.y);
        g.rotate(s.rotation);
        if (s.hidden) g.setGlobalAlpha(0.45);
        g.setFont(f);
        g.setTextAlign(TextAlignment.CENTER);
        g.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        double pos = start;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            double adv = advance(style, kind, c) * size;
            if (c != ' ') {
                double[] p = at(pts, cum, pos + adv / 2);
                g.save();
                g.translate(p[0], p[1]);
                g.rotate(Math.toDegrees(p[2]));
                String ch = String.valueOf(c);
                g.setStroke(halo);
                g.setLineWidth(size * 0.2);
                g.strokeText(ch, 0, 0);
                g.setFill(fill);
                g.fillText(ch, 0, 0);
                g.restore();
            }
            pos += adv + kind.spacing * size;
        }
        g.restore();
    }

    /** The point {x, y, angle} at a distance along the polyline. */
    private static double[] at(double[][] pts, double[] cum, double d) {
        d = Math.max(0, Math.min(cum[cum.length - 1], d));
        int i = 1;
        while (i < pts.length - 1 && cum[i] < d) i++;
        double seg = cum[i] - cum[i - 1], t = seg == 0 ? 0 : (d - cum[i - 1]) / seg;
        double x = pts[i - 1][0] + (pts[i][0] - pts[i - 1][0]) * t, y = pts[i - 1][1] + (pts[i][1] - pts[i - 1][1]) * t;
        return new double[]{x, y, Math.atan2(pts[i][1] - pts[i - 1][1], pts[i][0] - pts[i - 1][0])};
    }

    /**
     * Writes a banner's text on the banner, shrunk to fit. The transform must be the banner's own (turned but not
     * flipped), with its point at the origin.
     *
     * @param w   the banner's width; h its height; top the distance from its point up to its top edge
     */
    public static void drawBannerText(GraphicsContext g, WorldMap map, WorldStamp s, double w, double h, double top) {
        String text = s.text == null ? "" : s.text.trim();
        if (text.isEmpty()) return;
        WorldStyle style = styleOf(s, map);
        double natural = Math.max(0.5, natural(style, Kind.TITLE, text));
        double size = Math.min(h * 0.4, w * 0.8 / natural);
        double yFraction = s.feature.equals("banner-heraldic") ? 0.8 : s.feature.equals("banner-pennant") ? 0.45 : 0.5;
        WorldPalette pal = WorldPalette.of(style);
        g.save();
        g.setFont(font(style, Kind.TITLE, false, size));
        g.setTextAlign(TextAlignment.CENTER);
        if (s.hidden) g.setGlobalAlpha(0.45);
        g.setFill(argb(pal.ink, 1.0));
        g.fillText(text, 0, top + h * yFraction + size * 0.33);
        g.restore();
    }
}
