package dnd.map;

/**
 * Snapping on a whiteboard while something is moved, resized or drawn: its left, middle and right edge (and top, middle and
 * bottom) snap to those of the other objects, and its left and top edges snap to the dot grid, whenever they come within a
 * small distance. Which of the two is used is chosen by the person. Pure maths, so it can be tested.
 */
public final class BoardSnap {
    private BoardSnap() { }

    /** The result: how far to move it more, and where the guide lines are (NaN if that direction did not snap to an object). */
    public record Result(double dx, double dy, double guideX, double guideY) {
        public static final Result NONE = new Result(0, 0, Double.NaN, Double.NaN);

        public boolean any() {
            return dx != 0 || dy != 0 || !Double.isNaN(guideX) || !Double.isNaN(guideY);
        }
    }

    /**
     * @param min      the item's smallest x (or y)
     * @param max      its largest x (or y)
     * @param targets  the places (x or y) of the other objects' edges and middles to snap to
     * @param gridStep the distance between grid lines, or 0 or less for no grid
     * @param tol      how close counts as snapping
     * @return {shift, guide}: how far to move it, and the line to show (NaN for a grid snap or no snap)
     */
    public static double[] axis(double min, double max, double[] targets, double gridStep, double tol) {
        double best = Double.NaN, guide = Double.NaN;
        double mid = (min + max) / 2;
        if (targets != null) {
            for (double mine : new double[]{min, mid, max}) {
                for (double t : targets) {
                    double d = t - mine;
                    if (Math.abs(d) <= tol && (Double.isNaN(best) || Math.abs(d) < Math.abs(best))) {
                        best = d;
                        guide = t;
                    }
                }
            }
        }
        if (gridStep > 0) {
            for (double mine : new double[]{min, max}) {
                double d = Math.round(mine / gridStep) * gridStep - mine;
                if (Math.abs(d) <= tol && (Double.isNaN(best) || Math.abs(d) < Math.abs(best))) {
                    best = d;
                    guide = Double.NaN;
                }
            }
        }
        return Double.isNaN(best) ? new double[]{0, Double.NaN} : new double[]{best, guide};
    }

    /**
     * Works out the snap for a box minX..maxX, minY..maxY.
     *
     * @param xs        x places of the other objects' edges and middles (null or empty for none)
     * @param ys        y places of the same
     * @param gridStep  the dot grid spacing, or 0 or less to leave the grid out
     * @param useObjects false to ignore {@code xs} and {@code ys}
     */
    public static Result box(double minX, double maxX, double minY, double maxY, double[] xs, double[] ys, double gridStep, boolean useObjects, double tol) {
        double[] x = axis(minX, maxX, useObjects ? xs : null, gridStep, tol);
        double[] y = axis(minY, maxY, useObjects ? ys : null, gridStep, tol);
        return new Result(x[0], y[0], x[1], y[1]);
    }

    /** The snap for a single point (the corner being dragged, or where a shape starts). */
    public static Result point(double x, double y, double[] xs, double[] ys, double gridStep, boolean useObjects, double tol) {
        return box(x, x, y, y, xs, ys, gridStep, useObjects, tol);
    }
}
