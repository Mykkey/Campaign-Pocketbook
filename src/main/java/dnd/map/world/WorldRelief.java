package dnd.map.world;

/**
 * Relief shading: works out how light or dark each bit of ground should be from its height, as if the sun shone on it from
 * the top left. Pure maths, so it can be unit tested without any graphics.
 */
public final class WorldRelief {
    private WorldRelief() { }

    /** How tall the highest ground (elevation 255) is, measured in map cells, so slopes have a sensible steepness. */
    public static final double MAX_HEIGHT_CELLS = 30;

    /** A painted elevation value (0 to 255) as a height in cells. */
    public static float heightOf(int elevation) {
        return (float) ((elevation & 0xFF) / 255.0 * MAX_HEIGHT_CELLS);
    }

    // the sun: from the top left (north west) and fairly high
    private static final double LX = -0.5, LY = -0.5, LZ = 0.7;
    private static final double LEN = Math.sqrt(LX * LX + LY * LY + LZ * LZ);

    /**
     * The shade at one point from the heights around it: 0 on flat ground, positive on slopes that face the sun and negative
     * on slopes that face away. Runs roughly from -1 to +1.
     *
     * @param strength how steep to treat the slopes (1 is normal)
     */
    public static double shadeAt(float[] h, int w, int ht, int x, int y, double strength) {
        float left = at(h, w, ht, x - 1, y), right = at(h, w, ht, x + 1, y);
        float up = at(h, w, ht, x, y - 1), down = at(h, w, ht, x, y + 1);
        double dzdx = (right - left) / 2.0 * strength, dzdy = (down - up) / 2.0 * strength;
        // surface normal (-dz/dx, -dz/dy, 1)
        double nl = Math.sqrt(dzdx * dzdx + dzdy * dzdy + 1);
        double dot = (-dzdx * LX - dzdy * LY + LZ) / (nl * LEN);
        double flat = LZ / LEN;
        return Math.max(-1, Math.min(1, (dot - flat) * 2.2));
    }

    /** The shade of every cell of a height grid. */
    public static float[] shade(float[] h, int w, int ht, double strength) {
        float[] out = new float[w * ht];
        for (int y = 0; y < ht; y++) for (int x = 0; x < w; x++) out[y * w + x] = (float) shadeAt(h, w, ht, x, y, strength);
        return out;
    }

    /** Pixel art: the shade in three flat bands (-1 shadow, 0 flat, +1 lit). */
    public static int band(double shade) {
        return shade <= -0.12 ? -1 : shade >= 0.12 ? 1 : 0;
    }

    private static float at(float[] h, int w, int ht, int x, int y) {
        return h[Math.max(0, Math.min(ht - 1, y)) * w + Math.max(0, Math.min(w - 1, x))];
    }
}
