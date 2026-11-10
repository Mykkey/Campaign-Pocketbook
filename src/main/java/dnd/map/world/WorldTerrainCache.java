package dnd.map.world;

import dnd.model.PaintLayer;
import dnd.model.WorldMap;
import dnd.model.WorldStyle;
import dnd.model.WorldTerrain;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * The painted land and sea of one world map as a picture, built in square tiles as they come into view and rebuilt
 * only where something was painted. Coasts are made organic by bending the sampling position with noise, and each
 * art style adds its own touches (ink lines, washes, foam, pixels, glow).
 */
public final class WorldTerrainCache {
    /** Tile size in map cells. */
    public static final int TILE = 64;
    /** How far, in cells, the "distance to land" blur reaches (the width of the shallows and water lines). */
    private static final int R = 6;
    /** How far painting can change what is drawn around a cell (the blur plus the coast bending). */
    private static final int REACH = R + 3;

    private static final Map<String, WorldTerrainCache> ALL = new HashMap<>();

    /** The cache for a map (a new one if the map object or its size is different from last time). */
    public static synchronized WorldTerrainCache of(WorldMap m) {
        WorldTerrainCache c = ALL.get(m.id);
        if (c == null || c.map != m || c.cols != m.cols || c.rows != m.rows) {
            c = new WorldTerrainCache(m);
            ALL.put(m.id, c);
        }
        return c;
    }

    private final WorldMap map;
    private final int cols, rows, scale, tilesX, tilesY;
    private final WritableImage image;
    private final boolean[] built;
    private WorldStyle builtStyle;

    private WorldTerrainCache(WorldMap map) {
        this.map = map;
        this.cols = map.cols;
        this.rows = map.rows;
        this.scale = (long) cols * rows <= 1024L * 768L ? 2 : 1; // big maps trade sharpness for memory
        this.tilesX = (cols + TILE - 1) / TILE;
        this.tilesY = (rows + TILE - 1) / TILE;
        this.image = new WritableImage(cols * scale, rows * scale);
        this.built = new boolean[tilesX * tilesY];
    }

    /** Picture pixels per map cell. */
    public int scale() {
        return scale;
    }

    /** The picture, with every tile that touches the given area (in cells) up to date. */
    public Image image(double vx0, double vy0, double vx1, double vy1) {
        if (builtStyle != map.style) {
            invalidateAll();
            builtStyle = map.style;
        }
        int tx0 = clamp((int) Math.floor(vx0 / TILE), tilesX), tx1 = clamp((int) Math.floor(vx1 / TILE), tilesX);
        int ty0 = clamp((int) Math.floor(vy0 / TILE), tilesY), ty1 = clamp((int) Math.floor(vy1 / TILE), tilesY);
        for (int ty = ty0; ty <= ty1; ty++) {
            for (int tx = tx0; tx <= tx1; tx++) if (!built[ty * tilesX + tx]) buildTile(tx, ty);
        }
        return image;
    }

    /** The whole picture, fully built (for previews and exports). */
    public Image fullImage() {
        return image(0, 0, cols, rows);
    }

    public void invalidateAll() {
        Arrays.fill(built, false);
    }

    /** Marks the tiles that painting the cells x0..x1, y0..y1 (inclusive) can have changed. */
    public void invalidateCells(int x0, int y0, int x1, int y1) {
        int tx0 = clamp(Math.floorDiv(x0 - REACH, TILE), tilesX), tx1 = clamp(Math.floorDiv(x1 + REACH, TILE), tilesX);
        int ty0 = clamp(Math.floorDiv(y0 - REACH, TILE), tilesY), ty1 = clamp(Math.floorDiv(y1 + REACH, TILE), tilesY);
        for (int ty = ty0; ty <= ty1; ty++) for (int tx = tx0; tx <= tx1; tx++) built[ty * tilesX + tx] = false;
    }

    private static int clamp(int v, int count) {
        return Math.max(0, Math.min(count - 1, v));
    }

    private void buildTile(int tx, int ty) {
        int cx0 = tx * TILE, cy0 = ty * TILE, cx1 = Math.min(cols, cx0 + TILE), cy1 = Math.min(rows, cy0 + TILE);
        Sampler s = new Sampler(cx0, cy0, cx1, cy1);
        int pw = (cx1 - cx0) * scale, ph = (cy1 - cy0) * scale;
        int[] buf = new int[pw * ph];
        for (int py = 0; py < ph; py++) {
            double v = cy0 + (py + 0.5) / scale;
            for (int px = 0; px < pw; px++) buf[py * pw + px] = s.pixel(cx0 + (px + 0.5) / scale, v);
        }
        image.getPixelWriter().setPixels(cx0 * scale, cy0 * scale, pw, ph, PixelFormat.getIntArgbInstance(), buf, 0, pw);
        built[ty * tilesX + tx] = true;
    }

    /** Works out the colour of single points of one tile. */
    private final class Sampler {
        private final WorldPalette pal = WorldPalette.of(map.style);
        private final WorldStyle style = map.style;
        private final PaintLayer base = map.base(), bio = map.biome();
        private final double[] bw = new double[6], mw = new double[16];
        private final int bx0, by0, bw2, bh2;
        /** How much of the surroundings is solid ground: a wide blur (for shallows) and a tight one (for the coast itself). */
        private final float[] wide, tight;
        private static final int R2 = 2;

        Sampler(int cx0, int cy0, int cx1, int cy1) {
            bx0 = cx0 - R - 1;
            by0 = cy0 - R - 1;
            bw2 = (cx1 - cx0) + 2 * (R + 1);
            bh2 = (cy1 - cy0) + 2 * (R + 1);
            float[] solid = new float[bw2 * bh2];
            for (int y = 0; y < bh2; y++) {
                for (int x = 0; x < bw2; x++) {
                    WorldTerrain t = WorldTerrain.of(baseAt(bx0 + x, by0 + y));
                    solid[y * bw2 + x] = t != null && t.solid() ? 1f : 0f;
                }
            }
            wide = boxBlur(solid, bw2, bh2, R);
            tight = boxBlur(solid, bw2, bh2, R2);
        }

        /** Cells beyond the edge of the map count as the nearest cell, so the sea and land run right up to the border. */
        private byte baseAt(int x, int y) {
            return base.get(Math.max(0, Math.min(cols - 1, x)), Math.max(0, Math.min(rows - 1, y)));
        }

        private byte biomeAt(int x, int y) {
            return bio.get(Math.max(0, Math.min(cols - 1, x)), Math.max(0, Math.min(rows - 1, y)));
        }

        private float[] boxBlur(float[] src, int w, int h, int r) {
            float[] tmp = new float[w * h], out = new float[w * h];
            float norm = 2 * r + 1;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    float sum = 0;
                    for (int k = -r; k <= r; k++) sum += src[y * w + Math.max(0, Math.min(w - 1, x + k))];
                    tmp[y * w + x] = sum / norm;
                }
            }
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    float sum = 0;
                    for (int k = -r; k <= r; k++) sum += tmp[Math.max(0, Math.min(h - 1, y + k)) * w + x];
                    out[y * w + x] = sum / norm;
                }
            }
            return out;
        }

        private double fieldAt(float[] f, double u, double v) {
            double fx = u - 0.5 - bx0, fy = v - 0.5 - by0;
            int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
            double tx = fx - x0, ty = fy - y0;
            return lerp(lerp(at(f, x0, y0), at(f, x0 + 1, y0), tx), lerp(at(f, x0, y0 + 1), at(f, x0 + 1, y0 + 1), tx), ty);
        }

        private double at(float[] f, int x, int y) {
            return f[Math.max(0, Math.min(bh2 - 1, y)) * bw2 + Math.max(0, Math.min(bw2 - 1, x))];
        }

        private double lerp(double a, double c, double t) {
            return a + (c - a) * t;
        }

        private double smooth(double a, double b, double x) {
            double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
            return t * t * (3 - 2 * t);
        }

        int pixel(double u, double v) {
            if (style == WorldStyle.RETRO_PIXEL) { // chunky pixels: one colour per block of cells
                u = (Math.floor(u / 2.0) + 0.5) * 2.0;
                v = (Math.floor(v / 2.0) + 0.5) * 2.0;
            }
            // bend the position, slowly for wandering coasts and quickly for ragged detail
            double wu = u + (WorldNoise.fbm(u * 0.11 + 3.1, v * 0.11, 5, 3) - 0.5) * 3.6 + (WorldNoise.fbm(u * 0.45 + 9.3, v * 0.45, 8, 2) - 0.5) * 1.5;
            double wv = v + (WorldNoise.fbm(u * 0.11, v * 0.11 + 7.7, 6, 3) - 0.5) * 3.6 + (WorldNoise.fbm(u * 0.45, v * 0.45 + 4.1, 9, 2) - 0.5) * 1.5;

            // how far from the coast, in cells: positive on solid ground, negative at sea
            double dist = (fieldAt(tight, wu, wv) - 0.5) * (2 * R2 + 1);
            boolean solidHere = dist > 0;

            double fx = wu - 0.5, fy = wv - 0.5;
            int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
            double tx = fx - x0, ty = fy - y0;
            Arrays.fill(bw, 0);
            addBase(x0, y0, (1 - tx) * (1 - ty));
            addBase(x0 + 1, y0, tx * (1 - ty));
            addBase(x0, y0 + 1, (1 - tx) * ty);
            addBase(x0 + 1, y0 + 1, tx * ty);

            // among solid kinds (land, lava, ice) or among the rest (ocean, lake, bare parchment) take the strongest
            int best = -1, second = -1;
            double bs = -9, ss = -9;
            for (int c = 0; c < 6; c++) {
                boolean solid = c >= 3;
                if (solid != solidHere || bw[c] <= 0.0001) continue;
                double s = bw[c] + 0.2 * (WorldNoise.fbm(u * 0.4, v * 0.4, 11 + c * 7, 2) * 2 - 1);
                if (s > bs) {
                    second = best;
                    ss = bs;
                    best = c;
                    bs = s;
                } else if (s > ss) {
                    second = c;
                    ss = s;
                }
            }
            if (best < 0) best = solidHere ? 3 : (bw[1] >= bw[2] && bw[1] >= bw[0] ? 1 : bw[2] >= bw[0] ? 2 : 0);
            double margin = second < 0 ? 1 : bs - ss;

            int col = switch (best) {
                case 1, 2 -> water(u, v, best == 2, dist);
                case 3 -> land(u, v, wu, wv);
                case 4 -> lava(u, v, margin);
                case 5 -> ice(u, v);
                default -> grain(pal.parchment, u, v, 0.07);
            };
            return coast(col, dist);
        }

        private void addBase(int x, int y, double w) {
            int c = baseAt(x, y);
            bw[c < 0 || c > 5 ? 0 : c] += w;
        }

        private int grain(int c, double u, double v, double amount) {
            if (style == WorldStyle.RETRO_PIXEL) return c; // pixel art uses flat colours
            return WorldPalette.scale(c, 1 + (WorldNoise.fbm(u * 0.8, v * 0.8, 21, 3) - 0.5) * 2 * amount);
        }

        private int water(double u, double v, boolean lake, double dist) {
            double near = fieldAt(wide, u, v);
            double depth = Math.min(1, near * 2.4);
            int deep = lake ? pal.lake : pal.oceanDeep;
            int shallow = lake ? WorldPalette.mix(pal.lake, pal.oceanShallow, 0.5) : pal.oceanShallow;
            if (style == WorldStyle.RETRO_PIXEL) depth = Math.floor(depth * 3) / 3; // three flat bands of depth
            int col = WorldPalette.mix(deep, shallow, depth);
            col = grain(col, u, v, 0.05);
            switch (style) {
                case CLASSIC_INK -> {
                    if (near > 0.05 && near < 0.5) { // water lines running parallel to the coast, fading out to sea
                        double band = near * 9 + (WorldNoise.fbm(u * 0.5, v * 0.5, 3, 2) - 0.5) * 0.8;
                        if (band - Math.floor(band) < 0.2) col = WorldPalette.mix(col, pal.ink, 0.75 * (1 - near));
                    }
                }
                case FANTASY_COLOUR -> {
                    double away = -dist; // cells out to sea
                    if (away < 1.4) col = WorldPalette.mix(col, 0xFFFFFFFF, (1.4 - away) / 1.4 * 0.55); // foam
                }
                case WATERCOLOUR -> col = WorldPalette.mix(col, WorldPalette.scale(deep, 0.8), smooth(0.3, 0.9, near) * 0.25);
                case DARK_FANTASY -> {
                    if (WorldNoise.fbm(u * 0.6, v * 0.6, 41, 3) > 0.7) col = WorldPalette.scale(col, 1.25); // faint shimmer
                }
                default -> { }
            }
            return col;
        }

        private int land(double u, double v, double wu, double wv) {
            double fx = wu - 0.5, fy = wv - 0.5;
            int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
            double tx = fx - x0, ty = fy - y0;
            Arrays.fill(mw, 0);
            addBiome(x0, y0, (1 - tx) * (1 - ty));
            addBiome(x0 + 1, y0, tx * (1 - ty));
            addBiome(x0, y0 + 1, (1 - tx) * ty);
            addBiome(x0 + 1, y0 + 1, tx * ty);
            int b1 = 0, b2 = -1;
            double s1 = -9, s2 = -9;
            for (int c = 0; c < 16; c++) {
                if (mw[c] <= 0.0001) continue;
                double s = mw[c] + 0.25 * (WorldNoise.fbm(u * 0.45, v * 0.45, 61 + c * 5, 2) * 2 - 1);
                if (s > s1) {
                    b2 = b1;
                    s2 = s1;
                    b1 = c;
                    s1 = s;
                } else if (s > s2) {
                    b2 = c;
                    s2 = s;
                }
            }
            int col = biomeColor(b1);
            if (style == WorldStyle.RETRO_PIXEL) return col; // flat colour, no blending or texture
            if (b2 >= 0 && s2 > -8) col = WorldPalette.mix(biomeColor(b2), col, 0.5 + 0.5 * Math.min(1, (s1 - s2) / 0.3));
            return WorldPalette.scale(col, texture(b1, u, v));
        }

        private void addBiome(int x, int y, double w) {
            int c = biomeAt(x, y);
            mw[c < 0 || c > 15 ? 0 : c] += w;
        }

        private int biomeColor(int code) {
            return code == 0 ? pal.land : pal.biome[code];
        }

        /** A brightness factor that gives each kind of ground its own texture. */
        private double texture(int biome, double u, double v) {
            double n = WorldNoise.fbm(u * 0.9, v * 0.9, 71, 3);
            return switch (biome) {
                case 3 -> 0.93 + 0.07 * Math.sin((u * 0.8 + v * 0.35) * 3.0) + (n - 0.5) * 0.08; // farmland furrows
                case 8 -> 0.92 + 0.1 * Math.sin((u + n * 3.0) * 2.4);                             // dunes
                case 13 -> n > 0.6 ? 0.84 : 1.0;                                                   // swamp pools
                case 11 -> 0.97 + (n - 0.5) * 0.06;                                                // snow
                default -> 0.9 + 0.2 * n;
            };
        }

        private int lava(double u, double v, double margin) {
            double n = WorldNoise.fbm(u * 0.5, v * 0.5, 31, 3);
            int col = style == WorldStyle.RETRO_PIXEL ? (n > 0.55 ? pal.lavaHot : pal.lavaCool)
                    : WorldPalette.mix(pal.lavaCool, pal.lavaHot, smooth(0.35, 0.75, n));
            if (style != WorldStyle.RETRO_PIXEL && margin < 0.3) col = WorldPalette.mix(col, pal.lavaCool, 0.5 * (1 - margin / 0.3)); // a darker crust at the edge
            return col;
        }

        private int ice(double u, double v) {
            int col = grain(pal.ice, u, v, 0.05);
            double r = Math.abs(WorldNoise.fbm(u * 0.35, v * 0.35, 51, 3) * 2 - 1);
            if (r < 0.04) col = WorldPalette.mix(col, pal.oceanShallow, 0.5); // cracks
            return col;
        }

        /** The coastline itself, drawn in the way of each style; {@code dist} is the distance to it in cells. */
        private int coast(int col, double dist) {
            double d = Math.abs(dist);
            return switch (style) {
                case CLASSIC_INK -> d < 0.55 ? WorldPalette.mix(col, pal.ink, 1 - d / 0.55 * 0.25) : col;
                case FANTASY_COLOUR -> d < 0.35 ? WorldPalette.mix(col, pal.ink, 0.5 * (1 - d / 0.35)) : col;
                case WATERCOLOUR -> d < 0.9 ? WorldPalette.mix(col, pal.ink, 0.22 * (1 - d / 0.9)) : col;
                case RETRO_PIXEL -> d < 0.6 ? pal.ink : col;
                case DARK_FANTASY -> d < 0.5 ? WorldPalette.mix(col, 0xFF000000, 0.65 * (1 - d / 0.5)) : col;
            };
        }
    }
}
