package dnd.map;

public class SquareGrid implements Grid {
    private final int cols, rows;
    private final double s;

    public SquareGrid(int cols, int rows, double cellSize) {
        this.cols = cols;
        this.rows = rows;
        this.s = cellSize;
    }

    @Override public int cols() { return cols; }

    @Override public int rows() { return rows; }

    @Override public double width() { return cols * s; }

    @Override public double height() { return rows * s; }

    @Override
    public double[] center(int col, int row) {
        return new double[]{(col + 0.5) * s, (row + 0.5) * s};
    }

    @Override
    public double[] polygon(int col, int row) {
        double x = col * s, y = row * s;
        return new double[]{x, y, x + s, y, x + s, y + s, x, y + s};
    }

    @Override
    public int[] cellAt(double x, double y) {
        int c = (int) Math.floor(x / s), r = (int) Math.floor(y / s);
        return inside(c, r) ? new int[]{c, r} : null;
    }

    /** Chebyshev distance: diagonal steps count as one, like the standard 5e grid rule. */
    @Override
    public int distance(int c1, int r1, int c2, int r2) {
        return Math.max(Math.abs(c1 - c2), Math.abs(r1 - r2));
    }
}
