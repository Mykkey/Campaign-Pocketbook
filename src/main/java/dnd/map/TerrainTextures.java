package dnd.map;

import dnd.model.Terrain;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * Procedurally generated, seamlessly tiling ground textures (grass, dirt, sand, stone, wood, water,
 * snow, lava). Each is drawn once, then repeated across the map as an image pattern.
 */
public final class TerrainTextures {
    private TerrainTextures() { }

    public static final int SIZE = 512;

    private static final Map<Terrain, Image> CACHE = new EnumMap<>(Terrain.class);

    /** The texture for a terrain as a JavaFX image (generated on first use, then cached). */
    public static synchronized Image get(Terrain t) {
        return CACHE.computeIfAbsent(t, k -> SwingFXUtils.toFXImage(render(k, SIZE), null));
    }

    // ================================================================ noise (all tileable)

    private static double hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xffff) / 65535.0;
    }

    private static double smooth(double t) { return t * t * (3 - 2 * t); }

    /** Value noise in [0,1] that repeats every {@code px} / {@code py} lattice cells. */
    private static double value(double x, double y, int px, int py, int seed) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        double fx = smooth(x - ix), fy = smooth(y - iy);
        int x0 = Math.floorMod(ix, px), x1 = Math.floorMod(ix + 1, px);
        int y0 = Math.floorMod(iy, py), y1 = Math.floorMod(iy + 1, py);
        double a = hash(x0, y0, seed), b = hash(x1, y0, seed), c = hash(x0, y1, seed), d = hash(x1, y1, seed);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }

    /** Fractal noise over the unit square; u, v in [0,1). Repeats seamlessly. */
    private static double fbm(double u, double v, int px, int py, int octaves, int seed) {
        double sum = 0, amp = 0.5, norm = 0;
        int ppx = px, ppy = py;
        for (int o = 0; o < octaves; o++) {
            sum += amp * value(u * ppx, v * ppy, ppx, ppy, seed + o * 31);
            norm += amp;
            amp *= 0.5;
            ppx *= 2;
            ppy *= 2;
        }
        return sum / norm;
    }

    private static double fbm(double u, double v, int period, int octaves, int seed) {
        return fbm(u, v, period, period, octaves, seed);
    }

    /** Tileable Voronoi: returns {nearest distance, second nearest distance, cell id in [0,1)}. */
    private static double[] voronoi(double u, double v, int n, int seed) {
        double x = u * n, y = v * n;
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        double f1 = 9, f2 = 9, id = 0;
        for (int j = -1; j <= 1; j++) {
            for (int i = -1; i <= 1; i++) {
                int gx = cx + i, gy = cy + j;
                int wx = Math.floorMod(gx, n), wy = Math.floorMod(gy, n);
                double px = gx + 0.15 + 0.7 * hash(wx, wy, seed);
                double py = gy + 0.15 + 0.7 * hash(wx, wy, seed + 7);
                double d = Math.hypot(px - x, py - y);
                if (d < f1) {
                    f2 = f1;
                    f1 = d;
                    id = hash(wx, wy, seed + 13);
                } else if (d < f2) {
                    f2 = d;
                }
            }
        }
        return new double[]{f1, f2, id};
    }

    // ================================================================ colour helpers

    private static int rgb(double r, double g, double b) {
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(double v) { return (int) Math.max(0, Math.min(255, Math.round(v))); }

    private static int lerp(int c0, int c1, double t) {
        t = Math.max(0, Math.min(1, t));
        double r = ((c0 >> 16) & 255) * (1 - t) + ((c1 >> 16) & 255) * t;
        double g = ((c0 >> 8) & 255) * (1 - t) + ((c1 >> 8) & 255) * t;
        double b = (c0 & 255) * (1 - t) + (c1 & 255) * t;
        return rgb(r, g, b);
    }

    private static int shade(int c, double f) {
        return rgb(((c >> 16) & 255) * f, ((c >> 8) & 255) * f, (c & 255) * f);
    }

    // ================================================================ textures

    /** Renders one tile. Safe to call without the JavaFX toolkit (used by tests). */
    public static BufferedImage render(Terrain t, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        switch (t) {
            case GRASS -> grass(img, size);
            case DIRT -> dirt(img, size);
            case SAND -> sand(img, size);
            case STONE -> stone(img, size);
            case WOOD -> wood(img, size);
            case WATER -> water(img, size);
            case SNOW -> snow(img, size);
            case LAVA -> lava(img, size);
        }
        return img;
    }

    private static Graphics2D g2(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        return g;
    }

    /** Draws a shape at its position and at the wrapped positions so tiles stay seamless. */
    private static void wrapLine(Graphics2D g, double x1, double y1, double x2, double y2, int size) {
        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                g.draw(new Line2D.Double(x1 + ox * size, y1 + oy * size, x2 + ox * size, y2 + oy * size));
            }
        }
    }

    private static void wrapDot(Graphics2D g, double x, double y, double rx, double ry, int size, boolean fill) {
        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                Ellipse2D e = new Ellipse2D.Double(x - rx + ox * size, y - ry + oy * size, rx * 2, ry * 2);
                if (fill) g.fill(e); else g.draw(e);
            }
        }
    }

    private static void grass(BufferedImage img, int s) {
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                double u = (double) x / s, v = (double) y / s;
                double n = fbm(u, v, 3, 4, 11);
                double fine = fbm(u, v, 48, 1, 5);
                int c = lerp(0x4f7f3c, 0x86b862, n * 1.2 - 0.1);
                img.setRGB(x, y, shade(c, 0.92 + 0.16 * fine));
            }
        }
        Graphics2D g = g2(img);
        Random r = new Random(101);
        for (int i = 0; i < 2600; i++) {
            double x = r.nextDouble() * s, y = r.nextDouble() * s;
            double a = Math.toRadians(-90 + (r.nextDouble() - 0.5) * 80);
            double len = 5 + r.nextDouble() * 9;
            boolean light = r.nextBoolean();
            g.setColor(light ? new Color(160, 205, 105, 110) : new Color(45, 90, 38, 120));
            g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            wrapLine(g, x, y, x + Math.cos(a) * len, y + Math.sin(a) * len, s);
        }
        for (int i = 0; i < 40; i++) { // a few tiny flowers / clover spots
            double x = r.nextDouble() * s, y = r.nextDouble() * s;
            g.setColor(new Color(255, 255, 255, 120));
            wrapDot(g, x, y, 1.6, 1.6, s, true);
        }
        g.dispose();
    }

    private static void dirt(BufferedImage img, int s) {
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                double u = (double) x / s, v = (double) y / s;
                double n = fbm(u, v, 4, 5, 23);
                double fine = fbm(u, v, 64, 1, 9);
                int c = lerp(0x7d5f3d, 0xb08c5e, n * 1.3 - 0.15);
                img.setRGB(x, y, shade(c, 0.9 + 0.2 * fine));
            }
        }
        Graphics2D g = g2(img);
        Random r = new Random(202);
        for (int i = 0; i < 1400; i++) {
            double x = r.nextDouble() * s, y = r.nextDouble() * s;
            double rad = 0.6 + r.nextDouble() * 1.6;
            g.setColor(r.nextBoolean() ? new Color(70, 48, 28, 130) : new Color(205, 175, 130, 120));
            wrapDot(g, x, y, rad, rad, s, true);
        }
        for (int i = 0; i < 70; i++) { // pebbles
            double x = r.nextDouble() * s, y = r.nextDouble() * s;
            double rx = 2.5 + r.nextDouble() * 4, ry = rx * (0.6 + r.nextDouble() * 0.4);
            g.setColor(new Color(150, 135, 115, 230));
            wrapDot(g, x, y, rx, ry, s, true);
            g.setColor(new Color(60, 48, 36, 200));
            wrapDot(g, x, y, rx, ry, s, false);
            g.setColor(new Color(220, 205, 180, 130));
            wrapDot(g, x - rx * 0.25, y - ry * 0.3, rx * 0.4, ry * 0.3, s, true);
        }
        g.dispose();
    }

    private static void sand(BufferedImage img, int s) {
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                double u = (double) x / s, v = (double) y / s;
                double n = fbm(u, v, 3, 4, 37);
                double fine = fbm(u, v, 128, 1, 3);
                // soft wind ripples: stretched noise
                double rip = fbm(u + 0.15 * fbm(u, v, 2, 2, 8), v, 2, 24, 3, 71);
                int c = lerp(0xd8c283, 0xeedfa6, n);
                double f = 0.93 + 0.14 * fine + 0.1 * (rip - 0.5);
                img.setRGB(x, y, shade(c, f));
            }
        }
        Graphics2D g = g2(img);
        Random r = new Random(303);
        for (int i = 0; i < 2000; i++) {
            double x = r.nextDouble() * s, y = r.nextDouble() * s;
            g.setColor(r.nextBoolean() ? new Color(255, 245, 200, 90) : new Color(150, 120, 60, 80));
            wrapDot(g, x, y, 0.7, 0.7, s, true);
        }
        g.dispose();
    }

    private static void stone(BufferedImage img, int s) {
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                double u = (double) x / s, v = (double) y / s;
                double[] vor = voronoi(u, v, 7, 51);
                double edge = vor[1] - vor[0];
                double tone = 0.86 + 0.28 * vor[2];
                double grain = fbm(u, v, 40, 2, 17);
                int base = lerp(0x8d8f94, 0xb6b4ac, vor[2]);
                double f = tone * (0.9 + 0.2 * grain);
                // bevel: slightly lighter toward the middle of each flagstone
                f *= 0.92 + 0.14 * Math.min(1, edge * 3.2);
                int c = shade(base, f);
                if (edge < 0.06) c = lerp(c, 0x3c3c42, 1 - edge / 0.06);   // mortar
                else if (edge < 0.1) c = shade(c, 0.9 + 0.1 * (edge - 0.06) / 0.04);
                img.setRGB(x, y, c);
            }
        }
        Graphics2D g = g2(img);
        Random r = new Random(404);
        for (int i = 0; i < 160; i++) { // small chips and cracks
            double x = r.nextDouble() * s, y = r.nextDouble() * s;
            double a = r.nextDouble() * Math.PI * 2, len = 4 + r.nextDouble() * 10;
            g.setColor(new Color(40, 40, 46, 90));
            g.setStroke(new BasicStroke(0.9f));
            wrapLine(g, x, y, x + Math.cos(a) * len, y + Math.sin(a) * len, s);
        }
        g.dispose();
    }

    private static void wood(BufferedImage img, int s) {
        int rows = 8, plankH = s / rows;
        for (int y = 0; y < s; y++) {
            int row = y / plankH;
            int shift = (row % 2) * (s / 4);
            for (int x = 0; x < s; x++) {
                double u = (double) x / s, v = (double) y / s;
                int px = Math.floorMod(x + shift, s / 2);          // two planks per row
                int plankId = row * 2 + Math.floorMod(x + shift, s) / (s / 2);
                double tone = 0.85 + 0.3 * hash(plankId, 3, 91);
                // grain runs along the plank: low frequency in x, high frequency in y
                double grain = fbm(u, v, 3, 56, 3, 61 + plankId % 5);
                double rings = 0.5 + 0.5 * Math.sin((grain * 7 + v * 40) * Math.PI);
                int base = lerp(0x9a6e3e, 0xc79a60, grain);
                double f = tone * (0.9 + 0.12 * rings);
                int c = shade(base, f);
                int ry = y % plankH;
                if (ry < 2 || ry > plankH - 3) c = lerp(c, 0x3b2815, ry < 2 ? 0.85 : 0.55); // gaps between rows
                if (px < 2 || px > s / 2 - 3) c = lerp(c, 0x3b2815, px < 2 ? 0.85 : 0.5);   // butt joints
                img.setRGB(x, y, c);
            }
        }
        Graphics2D g = g2(img);
        Random r = new Random(505);
        for (int i = 0; i < 18; i++) { // knots
            double x = r.nextDouble() * s, y = r.nextDouble() * s;
            g.setColor(new Color(80, 52, 28, 150));
            wrapDot(g, x, y, 4.5, 3, s, true);
            g.setColor(new Color(60, 38, 20, 170));
            wrapDot(g, x, y, 6.5, 4.5, s, false);
        }
        for (int row = 0; row < rows; row++) { // nails near plank ends
            int shift = (row % 2) * (s / 4);
            for (int k = 0; k < 2; k++) {
                double nx = ((k * (s / 2) + shift) % s) + 8;
                double ny = row * plankH + plankH / 2.0;
                g.setColor(new Color(40, 30, 22, 200));
                wrapDot(g, nx, ny, 1.6, 1.6, s, true);
            }
        }
        g.dispose();
    }

    private static void water(BufferedImage img, int s) {
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                double u = (double) x / s, v = (double) y / s;
                double n = fbm(u, v, 3, 4, 77);
                int c = lerp(0x2f78bd, 0x66aee6, n * 1.3 - 0.15);
                // caustic-like ripples: bright where a warped noise field crosses 0.5
                double warp = fbm(u, v, 5, 2, 3);
                double w = fbm(u + 0.08 * warp, v + 0.08 * warp, 6, 3, 19);
                double ridge = Math.abs(w - 0.5) * 2;
                if (ridge < 0.07) c = lerp(c, 0xdff1ff, 0.45 * (1 - ridge / 0.07));
                double depth = fbm(u, v, 2, 3, 5);
                c = shade(c, 0.92 + 0.14 * depth);
                img.setRGB(x, y, c);
            }
        }
        Graphics2D g = g2(img);
        Random r = new Random(606);
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 160; i++) { // small foam flecks
            double x = r.nextDouble() * s, y = r.nextDouble() * s, len = 6 + r.nextDouble() * 10;
            g.setColor(new Color(255, 255, 255, 70));
            wrapLine(g, x, y, x + len, y + (r.nextDouble() - 0.5) * 3, s);
        }
        g.dispose();
    }

    private static void snow(BufferedImage img, int s) {
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                double u = (double) x / s, v = (double) y / s;
                double n = fbm(u, v, 3, 5, 83);
                double fine = fbm(u, v, 96, 1, 2);
                int c = lerp(0xcfdeec, 0xfafcff, n * 1.25 - 0.05);
                img.setRGB(x, y, shade(c, 0.97 + 0.06 * fine));
            }
        }
        Graphics2D g = g2(img);
        Random r = new Random(707);
        for (int i = 0; i < 700; i++) { // sparkles and drift flecks
            double x = r.nextDouble() * s, y = r.nextDouble() * s;
            boolean bright = r.nextInt(3) > 0;
            g.setColor(bright ? new Color(255, 255, 255, 190) : new Color(150, 175, 205, 90));
            wrapDot(g, x, y, 0.8 + r.nextDouble(), 0.8 + r.nextDouble(), s, true);
        }
        g.dispose();
    }

    private static void lava(BufferedImage img, int s) {
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                double u = (double) x / s, v = (double) y / s;
                double warp = fbm(u, v, 4, 3, 29);
                double[] vor = voronoi(u + 0.03 * warp, v + 0.03 * warp, 6, 63);
                double edge = vor[1] - vor[0];
                double crust = fbm(u, v, 24, 3, 41);
                int c = lerp(0x241512, 0x4a2a22, crust);
                c = shade(c, 0.85 + 0.3 * vor[2]);
                if (edge < 0.11) {
                    double t = 1 - edge / 0.11;
                    int glow = lerp(0xd9420f, 0xffd45a, t * t);
                    c = lerp(c, glow, Math.min(1, t * 1.25));
                } else if (edge < 0.2) {
                    c = lerp(c, 0x7a2a12, (0.2 - edge) / 0.09 * 0.45); // heat haze near the cracks
                }
                img.setRGB(x, y, c);
            }
        }
        Graphics2D g = g2(img);
        Random r = new Random(808);
        for (int i = 0; i < 220; i++) { // embers
            double x = r.nextDouble() * s, y = r.nextDouble() * s;
            g.setColor(new Color(255, 190 + r.nextInt(50), 80, 160));
            wrapDot(g, x, y, 0.8 + r.nextDouble() * 1.2, 0.8 + r.nextDouble() * 1.2, s, true);
        }
        g.dispose();
    }
}
