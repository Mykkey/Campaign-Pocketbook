package dnd.map.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Picks where to put symbols when scattering a forest or a mountain range: spread out, not on top of each other. */
public final class ScatterPlanner {
    private ScatterPlanner() { }

    /**
     * Chooses new points inside a circle, none closer than {@code spacing} to each other or to an existing point.
     *
     * @param existing points already there, as {x, y}
     * @param attempts how many random points to try (more means a denser result)
     * @return the accepted points, as {x, y}
     */
    public static List<double[]> plan(double cx, double cy, double radius, double spacing, List<double[]> existing, int attempts, Random rnd) {
        List<double[]> out = new ArrayList<>();
        double min2 = spacing * spacing;
        for (int i = 0; i < attempts; i++) {
            double a = rnd.nextDouble() * 2 * Math.PI, d = Math.sqrt(rnd.nextDouble()) * radius;
            double x = cx + Math.cos(a) * d, y = cy + Math.sin(a) * d;
            if (tooClose(x, y, existing, min2) || tooClose(x, y, out, min2)) continue;
            out.add(new double[]{x, y});
        }
        return out;
    }

    private static boolean tooClose(double x, double y, List<double[]> points, double min2) {
        for (double[] p : points) {
            double dx = p[0] - x, dy = p[1] - y;
            if (dx * dx + dy * dy < min2) return true;
        }
        return false;
    }
}
