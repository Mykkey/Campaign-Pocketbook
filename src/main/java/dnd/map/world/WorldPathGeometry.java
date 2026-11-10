package dnd.map.world;

import dnd.model.WorldPath;

import java.util.ArrayList;
import java.util.List;

/** The maths of the lines on a world map: smooth curves through their points, distances, and the outline of a tapering river. */
public final class WorldPathGeometry {
    private WorldPathGeometry() { }

    /**
     * A smooth curve through the line's points (a Catmull-Rom spline), as many short straight pieces.
     *
     * @param perSpan how many pieces between each pair of points
     * @return the points of the curve as {x, y}, starting at the first point and ending at the last
     */
    public static List<double[]> smooth(WorldPath p, int perSpan) {
        int n = p.count();
        List<double[]> out = new ArrayList<>();
        if (n == 0) return out;
        if (n == 1) {
            out.add(new double[]{p.x(0), p.y(0)});
            return out;
        }
        for (int i = 0; i < n - 1; i++) {
            double[] p0 = at(p, i - 1), p1 = at(p, i), p2 = at(p, i + 1), p3 = at(p, i + 2);
            for (int k = 0; k < perSpan; k++) {
                double t = (double) k / perSpan, t2 = t * t, t3 = t2 * t;
                out.add(new double[]{
                        0.5 * ((2 * p1[0]) + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2 + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3),
                        0.5 * ((2 * p1[1]) + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2 + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3)});
            }
        }
        out.add(new double[]{p.x(n - 1), p.y(n - 1)});
        return out;
    }

    /** A point of the line, with the ends repeated so the curve starts and ends exactly on them. */
    private static double[] at(WorldPath p, int i) {
        int k = Math.max(0, Math.min(p.count() - 1, i));
        return new double[]{p.x(k), p.y(k)};
    }

    /**
     * Thins out a freehand line: keeps only the points needed to stay within {@code tolerance} of the original
     * (the Ramer-Douglas-Peucker method). The first and last points are always kept.
     */
    public static List<Double> simplify(List<Double> flat, double tolerance) {
        int n = flat.size() / 2;
        if (n <= 2) return new ArrayList<>(flat);
        boolean[] keep = new boolean[n];
        keep[0] = true;
        keep[n - 1] = true;
        reduce(flat, 0, n - 1, tolerance, keep);
        List<Double> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (keep[i]) {
                out.add(flat.get(2 * i));
                out.add(flat.get(2 * i + 1));
            }
        }
        return out;
    }

    private static void reduce(List<Double> f, int a, int b, double tol, boolean[] keep) {
        double best = 0;
        int at = -1;
        double[] pa = {f.get(2 * a), f.get(2 * a + 1)}, pb = {f.get(2 * b), f.get(2 * b + 1)};
        for (int i = a + 1; i < b; i++) {
            double d = segmentDistance(f.get(2 * i), f.get(2 * i + 1), pa, pb);
            if (d > best) {
                best = d;
                at = i;
            }
        }
        if (at >= 0 && best > tol) {
            keep[at] = true;
            reduce(f, a, at, tol, keep);
            reduce(f, at, b, tol, keep);
        }
    }

    /** The total length of a polyline. */
    public static double length(List<double[]> pts) {
        double len = 0;
        for (int i = 0; i + 1 < pts.size(); i++) len += Math.hypot(pts.get(i + 1)[0] - pts.get(i)[0], pts.get(i + 1)[1] - pts.get(i)[1]);
        return len;
    }

    /** The shortest distance from a point to a polyline. */
    public static double distance(List<double[]> pts, double px, double py) {
        double best = Double.MAX_VALUE;
        if (pts.size() == 1) return Math.hypot(px - pts.get(0)[0], py - pts.get(0)[1]);
        for (int i = 0; i + 1 < pts.size(); i++) best = Math.min(best, segmentDistance(px, py, pts.get(i), pts.get(i + 1)));
        return best;
    }

    private static double segmentDistance(double px, double py, double[] a, double[] b) {
        double dx = b[0] - a[0], dy = b[1] - a[1], len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - a[0]) * dx + (py - a[1]) * dy) / len2));
        return Math.hypot(px - (a[0] + t * dx), py - (a[1] + t * dy));
    }

    /** The index (0-based) of the original point after which a new point at (px, py) belongs, so it lands on the nearest span. */
    public static int insertIndex(WorldPath p, double px, double py) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i + 1 < p.count(); i++) {
            double d = segmentDistance(px, py, new double[]{p.x(i), p.y(i)}, new double[]{p.x(i + 1), p.y(i + 1)});
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best + 1;
    }

    /**
     * The two sides of a ribbon that follows a polyline and grows from {@code startFraction} of {@code width} at the
     * start to the full width at the end (a river getting wider towards its mouth).
     *
     * @return {left, right}, each a list of {x, y}
     */
    public static List<List<double[]>> ribbon(List<double[]> pts, double width, double startFraction) {
        List<double[]> left = new ArrayList<>(), right = new ArrayList<>();
        int n = pts.size();
        for (int i = 0; i < n; i++) {
            double[] a = pts.get(Math.max(0, i - 1)), b = pts.get(Math.min(n - 1, i + 1));
            double dx = b[0] - a[0], dy = b[1] - a[1], len = Math.hypot(dx, dy);
            double nx = len == 0 ? 0 : -dy / len, ny = len == 0 ? 0 : dx / len;
            double t = n <= 1 ? 1 : (double) i / (n - 1);
            double half = width * (startFraction + (1 - startFraction) * t) / 2;
            left.add(new double[]{pts.get(i)[0] + nx * half, pts.get(i)[1] + ny * half});
            right.add(new double[]{pts.get(i)[0] - nx * half, pts.get(i)[1] - ny * half});
        }
        return List.of(left, right);
    }
}
