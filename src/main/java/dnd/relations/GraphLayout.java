package dnd.relations;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Places the nodes of a connected graph so that linked ones sit near each other and unlinked ones are pushed apart
 * (a force-directed layout), and then moves boxes that still overlap. The same input always gives the same picture.
 * Pure maths on ids (no UI), so it can be tested.
 */
public final class GraphLayout {
    private GraphLayout() { }

    /**
     * @param ids   the nodes
     * @param edges {a, b} pairs (the direction does not matter); pairs naming an unknown id are ignored
     * @param ideal the distance linked nodes try to keep (centre to centre)
     * @param boxW  width of a node's box, used to stop boxes overlapping
     * @param boxH  height of a node's box
     * @return the centre of each node, with the smallest x and y at 0
     */
    public static Map<String, double[]> layout(List<String> ids, List<String[]> edges, double ideal, double boxW, double boxH) {
        int n = ids.size();
        Map<String, double[]> out = new LinkedHashMap<>();
        if (n == 0) return out;
        if (n == 1) {
            out.put(ids.get(0), new double[]{0, 0});
            return out;
        }
        if (n == 2) {
            out.put(ids.get(0), new double[]{0, 0});
            out.put(ids.get(1), new double[]{ideal, 0});
            return out;
        }
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < n; i++) index.put(ids.get(i), i);
        List<int[]> links = new java.util.ArrayList<>();
        for (String[] e : edges) {
            Integer a = index.get(e[0]), b = index.get(e[1]);
            if (a != null && b != null && !a.equals(b)) links.add(new int[]{a, b});
        }

        double[] x = new double[n], y = new double[n];
        double r = ideal * Math.sqrt(n) / 2 + ideal / 2;
        for (int i = 0; i < n; i++) {
            double ang = -Math.PI / 2 + 2 * Math.PI * i / n;
            x[i] = r * Math.cos(ang);
            y[i] = r * Math.sin(ang);
        }
        double k = ideal;
        int iterations = 300;
        double[] dx = new double[n], dy = new double[n];
        for (int it = 0; it < iterations; it++) {
            double temp = k * (1.0 - (double) it / iterations) + 1;
            java.util.Arrays.fill(dx, 0);
            java.util.Arrays.fill(dy, 0);
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double ddx = x[i] - x[j], ddy = y[i] - y[j];
                    double d = Math.sqrt(ddx * ddx + ddy * ddy);
                    if (d < 0.01) { // exactly on top of each other: nudge apart in a fixed direction
                        ddx = 0.01 * (1 + (i + j) % 3);
                        ddy = 0.01;
                        d = Math.sqrt(ddx * ddx + ddy * ddy);
                    }
                    double f = k * k / d;
                    double fx = ddx / d * f, fy = ddy / d * f;
                    dx[i] += fx;
                    dy[i] += fy;
                    dx[j] -= fx;
                    dy[j] -= fy;
                }
            }
            for (int[] l : links) {
                double ddx = x[l[0]] - x[l[1]], ddy = y[l[0]] - y[l[1]];
                double d = Math.max(0.01, Math.sqrt(ddx * ddx + ddy * ddy));
                double f = d * d / k;
                double fx = ddx / d * f, fy = ddy / d * f;
                dx[l[0]] -= fx;
                dy[l[0]] -= fy;
                dx[l[1]] += fx;
                dy[l[1]] += fy;
            }
            for (int i = 0; i < n; i++) {
                double d = Math.max(0.01, Math.sqrt(dx[i] * dx[i] + dy[i] * dy[i]));
                double step = Math.min(d, temp);
                x[i] += dx[i] / d * step;
                y[i] += dy[i] / d * step;
            }
        }
        // the forces can leave links much longer or shorter than asked for: scale the picture so the average link is about right
        if (!links.isEmpty()) {
            double total = 0;
            for (int[] l : links) total += Math.hypot(x[l[0]] - x[l[1]], y[l[0]] - y[l[1]]);
            double mean = total / links.size();
            if (mean > 1) {
                double s = Math.max(0.4, Math.min(1.5, ideal / mean));
                for (int i = 0; i < n; i++) {
                    x[i] *= s;
                    y[i] *= s;
                }
            }
        }
        separate(x, y, boxW + 24, boxH + 24);

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            minX = Math.min(minX, x[i]);
            minY = Math.min(minY, y[i]);
        }
        for (int i = 0; i < n; i++) out.put(ids.get(i), new double[]{x[i] - minX, y[i] - minY});
        return out;
    }

    /** Pushes apart any two boxes that overlap (along whichever way needs the smaller move), until none do or it gives up. */
    static void separate(double[] x, double[] y, double w, double h) {
        int n = x.length;
        for (int pass = 0; pass < 200; pass++) {
            boolean moved = false;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double ox = w - Math.abs(x[i] - x[j]), oy = h - Math.abs(y[i] - y[j]);
                    if (ox <= 0 || oy <= 0) continue;
                    moved = true;
                    if (ox < oy) {
                        double s = (x[i] <= x[j] ? -1 : 1) * (ox / 2 + 0.5);
                        x[i] += s;
                        x[j] -= s;
                    } else {
                        double s = (y[i] <= y[j] ? -1 : 1) * (oy / 2 + 0.5);
                        y[i] += s;
                        y[j] -= s;
                    }
                }
            }
            if (!moved) return;
        }
    }
}
