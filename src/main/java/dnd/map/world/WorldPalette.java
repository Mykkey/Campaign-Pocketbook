package dnd.map.world;

import dnd.model.WorldBiome;
import dnd.model.WorldStyle;

import java.util.EnumMap;
import java.util.Map;

/** The colours of one art style (packed ARGB ints). The fantasy palette is the source; the others are derived from it. */
public final class WorldPalette {
    public final int parchment, oceanDeep, oceanShallow, lake, land, lavaHot, lavaCool, ice, ink;
    /** Indexed by biome code (1..15); index 0 is plain land. */
    public final int[] biome = new int[16];

    private WorldPalette(int parchment, int oceanDeep, int oceanShallow, int lake, int land, int lavaHot, int lavaCool, int ice, int ink) {
        this.parchment = parchment;
        this.oceanDeep = oceanDeep;
        this.oceanShallow = oceanShallow;
        this.lake = lake;
        this.land = land;
        this.lavaHot = lavaHot;
        this.lavaCool = lavaCool;
        this.ice = ice;
        this.ink = ink;
    }

    private static final Map<WorldStyle, WorldPalette> CACHE = new EnumMap<>(WorldStyle.class);

    public static synchronized WorldPalette of(WorldStyle style) {
        return CACHE.computeIfAbsent(style, WorldPalette::build);
    }

    public int biomeColor(WorldBiome b) {
        return biome[b == null ? 0 : b.code];
    }

    // ---- building

    private static WorldPalette fantasy() {
        WorldPalette p = new WorldPalette(rgb("efe6cf"), rgb("2e6f9e"), rgb("6fc0d0"), rgb("5aa9d6"), rgb("a8c46c"),
                rgb("ff6a1a"), rgb("7a1d0c"), rgb("e6f4fb"), rgb("2b2118"));
        String[] hex = {"a8c46c", "7fb94f", "b3c964", "cdb858", "3f7d3a", "2a8a4a", "c9b45a", "e6d28f", "dcb872", "c27a4a",
                "8e8b88", "f4f8fb", "aab8a0", "52745a", "8a7a8a", "4a4642"};
        for (int i = 0; i < hex.length; i++) p.biome[i] = rgb(hex[i]);
        return p;
    }

    private static WorldPalette build(WorldStyle style) {
        WorldPalette f = fantasy();
        return switch (style) {
            case FANTASY_COLOUR -> f;
            case CLASSIC_INK -> derive(f, c -> mix(mix(c, f.parchment, 0.62), rgb("c9a46a"), 0.18),
                    mix(f.parchment, rgb("9fb7b5"), 0.42), mix(f.parchment, rgb("b7c9c2"), 0.55), mix(f.parchment, rgb("a9c1bd"), 0.5),
                    mix(f.parchment, rgb("8f6f4a"), 0.14), rgb("3b2a1a"), rgb("e8dcc0"));
            case WATERCOLOUR -> derive(f, c -> mix(desat(c, 0.2), rgb("ffffff"), 0.32),
                    mix(rgb("5d93bd"), rgb("ffffff"), 0.3), mix(rgb("8ec9d8"), rgb("ffffff"), 0.35), mix(rgb("6fb0d4"), rgb("ffffff"), 0.3),
                    mix(f.land, rgb("ffffff"), 0.3), rgb("5b4a52"), rgb("f6efdf"));
            case DARK_FANTASY -> derive(f, c -> scale(desat(c, 0.35), 0.5),
                    rgb("08121c"), rgb("163247"), rgb("1c3f55"), scale(desat(f.land, 0.4), 0.5), rgb("0a0908"), rgb("1b1a1f"));
            case RETRO_PIXEL -> derive(f, c -> posterize(c, 7),
                    posterize(f.oceanDeep, 7), posterize(f.oceanShallow, 7), posterize(f.lake, 7), posterize(f.land, 7), rgb("1a1420"), posterize(f.parchment, 7));
        };
    }

    /** A palette whose land colours are {@code landFn} of the fantasy ones and whose water, ink and paper are given. */
    private static WorldPalette derive(WorldPalette f, java.util.function.IntUnaryOperator landFn, int oceanDeep, int oceanShallow,
                                       int lake, int land, int ink, int parchment) {
        WorldPalette p = new WorldPalette(parchment, oceanDeep, oceanShallow, lake, land,
                landFn.applyAsInt(f.lavaHot), landFn.applyAsInt(f.lavaCool), landFn.applyAsInt(f.ice), ink);
        for (int i = 0; i < f.biome.length; i++) p.biome[i] = landFn.applyAsInt(f.biome[i]);
        return p;
    }

    // ---- colour helpers (ARGB ints)

    static int rgb(String hex) {
        return 0xFF000000 | Integer.parseInt(hex, 16);
    }

    static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    static int scale(int c, double f) {
        int r = clamp(((c >> 16) & 255) * f), g = clamp(((c >> 8) & 255) * f), b = clamp((c & 255) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static int desat(int c, double amount) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        double gray = 0.3 * r + 0.59 * g + 0.11 * b;
        return 0xFF000000 | (clamp(r + (gray - r) * amount) << 16) | (clamp(g + (gray - g) * amount) << 8) | clamp(b + (gray - b) * amount);
    }

    static int posterize(int c, int levels) {
        double step = 255.0 / (levels - 1);
        int r = clamp(Math.round(((c >> 16) & 255) / step) * step);
        int g = clamp(Math.round(((c >> 8) & 255) / step) * step);
        int b = clamp(Math.round((c & 255) / step) * step);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }
}
