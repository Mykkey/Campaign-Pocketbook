package dnd.map.world;

/** Smooth, repeatable noise for organic coastlines and textures. Same input always gives the same output. */
public final class WorldNoise {
    private WorldNoise() { }

    private static int hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    private static double rnd(int x, int y, int seed) {
        return (hash(x, y, seed) & 0xFFFFFF) / (double) 0x1000000;
    }

    /** Smooth value noise in 0..1. */
    public static double value(double x, double y, int seed) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        double fx = x - ix, fy = y - iy;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = rnd(ix, iy, seed), b = rnd(ix + 1, iy, seed), c = rnd(ix, iy + 1, seed), d = rnd(ix + 1, iy + 1, seed);
        return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy;
    }

    /** Several layers of noise at growing detail, in 0..1. */
    public static double fbm(double x, double y, int seed, int octaves) {
        double sum = 0, amp = 0.5, norm = 0, f = 1;
        for (int i = 0; i < octaves; i++) {
            sum += amp * value(x * f, y * f, seed + i * 101);
            norm += amp;
            amp *= 0.5;
            f *= 2.03;
        }
        return sum / norm;
    }
}
