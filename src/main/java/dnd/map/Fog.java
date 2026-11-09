package dnd.map;

import dnd.model.BattleMap;

/**
 * Fog of war rules: which cells are revealed. Pure logic on {@link BattleMap#revealed}, so it can be tested
 * without any UI. Every method returns how many cells changed state.
 */
public final class Fog {
    private Fog() { }

    public static boolean isRevealed(BattleMap m, int col, int row) {
        return m.revealed.contains(BattleMap.key(col, row));
    }

    private static boolean set(BattleMap m, int col, int row, boolean reveal) {
        String key = BattleMap.key(col, row);
        return reveal ? m.revealed.add(key) : m.revealed.remove(key);
    }

    /** Reveals every cell within {@code radius} steps of (col,row), including that cell. */
    public static int revealRadius(BattleMap m, Grid g, int col, int row, int radius) {
        int changed = 0;
        int r = Math.max(0, radius);
        for (int rr = Math.max(0, row - r - 1); rr <= Math.min(g.rows() - 1, row + r + 1); rr++) {
            for (int cc = Math.max(0, col - r - 1); cc <= Math.min(g.cols() - 1, col + r + 1); cc++) {
                if (g.distance(col, row, cc, rr) <= r && set(m, cc, rr, true)) changed++;
            }
        }
        return changed;
    }

    /** A brush: every cell fewer than {@code brush} steps from (col,row) (brush 1 = just that cell). */
    public static int paint(BattleMap m, Grid g, int col, int row, int brush, boolean reveal) {
        int changed = 0;
        int b = Math.max(1, brush);
        for (int rr = Math.max(0, row - b); rr <= Math.min(g.rows() - 1, row + b); rr++) {
            for (int cc = Math.max(0, col - b); cc <= Math.min(g.cols() - 1, col + b); cc++) {
                if (g.distance(col, row, cc, rr) < b && set(m, cc, rr, reveal)) changed++;
            }
        }
        return changed;
    }

    /** Reveals or hides every cell whose centre lies inside the rectangle (map units, any corner order). */
    public static int setRect(BattleMap m, Grid g, double x0, double y0, double x1, double y1, boolean reveal) {
        double l = Math.min(x0, x1), r = Math.max(x0, x1), t = Math.min(y0, y1), b = Math.max(y0, y1);
        int changed = 0;
        for (int row = 0; row < g.rows(); row++) {
            for (int col = 0; col < g.cols(); col++) {
                double[] c = g.center(col, row);
                if (c[0] >= l && c[0] <= r && c[1] >= t && c[1] <= b && set(m, col, row, reveal)) changed++;
            }
        }
        return changed;
    }

    public static int revealAll(BattleMap m, Grid g) {
        int changed = 0;
        for (int row = 0; row < g.rows(); row++) {
            for (int col = 0; col < g.cols(); col++) if (set(m, col, row, true)) changed++;
        }
        return changed;
    }

    public static int hideAll(BattleMap m) {
        int n = m.revealed.size();
        m.revealed.clear();
        return n;
    }
}
