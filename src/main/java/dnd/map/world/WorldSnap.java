package dnd.map.world;

/**
 * Snapping while something is moved over a world map: its left, middle and right edge snap to the map's left, middle and
 * right, and its top, middle and bottom to the map's top, middle and bottom, whenever they come within a small distance.
 */
public final class WorldSnap {
    private WorldSnap() { }

    /** The result: how far to move it more, and where the guide lines are (NaN if that direction did not snap). */
    public record Result(double dx, double dy, double guideX, double guideY) {
        public boolean any() {
            return !Double.isNaN(guideX) || !Double.isNaN(guideY);
        }
    }

    /**
     * @param min the item's smallest x (or y), @param max its largest x (or y); @param size the map's width (or height)
     * @return {shift, guide} for the closest snap within {@code tol}, or {0, NaN}
     */
    static double[] axis(double min, double max, double size, double tol) {
        double[] mine = {min, (min + max) / 2, max};
        double[] targets = {0, size / 2, size};
        double best = Double.NaN, guide = Double.NaN;
        for (double m : mine) {
            for (double t : targets) {
                double d = t - m;
                if (Math.abs(d) <= tol && (Double.isNaN(best) || Math.abs(d) < Math.abs(best))) {
                    best = d;
                    guide = t;
                }
            }
        }
        return Double.isNaN(best) ? new double[]{0, Double.NaN} : new double[]{best, guide};
    }

    /** Works out the snap for an item whose box is minX..maxX, minY..maxY on a map of the given size, within {@code tol} map cells. */
    public static Result snap(double minX, double maxX, double minY, double maxY, double cols, double rows, double tol) {
        double[] x = axis(minX, maxX, cols, tol), y = axis(minY, maxY, rows, tol);
        return new Result(x[0], y[0], x[1], y[1]);
    }
}
