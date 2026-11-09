package dnd.map;

/**
 * Pointy-top hexagons in "odd-r" offset layout (odd rows are shifted half a cell to the right).
 * {@code cellSize} is the width of a hex, flat side to flat side.
 */
public class HexGrid implements Grid {
    private static final double SQRT3 = Math.sqrt(3);

    private final int cols, rows;
    private final double s;   // hex width (flat to flat)
    private final double r;   // hex radius (centre to corner)

    public HexGrid(int cols, int rows, double cellSize) {
        this.cols = cols;
        this.rows = rows;
        this.s = cellSize;
        this.r = cellSize / SQRT3;
    }

    @Override public int cols() { return cols; }

    @Override public int rows() { return rows; }

    @Override public double width() { return s * cols + (rows > 1 ? s / 2 : 0); }

    @Override public double height() { return rows == 0 ? 0 : 1.5 * r * (rows - 1) + 2 * r; }

    @Override
    public double[] center(int col, int row) {
        return new double[]{s * (col + 0.5 * (row & 1)) + s / 2, r + 1.5 * r * row};
    }

    @Override
    public double[] polygon(int col, int row) {
        double[] c = center(col, row);
        double[] p = new double[12];
        for (int k = 0; k < 6; k++) {
            double a = Math.toRadians(-90 + 60 * k);
            p[2 * k] = c[0] + r * Math.cos(a);
            p[2 * k + 1] = c[1] + r * Math.sin(a);
        }
        return p;
    }

    @Override
    public int[] cellAt(double x, double y) {
        double x0 = x - s / 2, y0 = y - r;
        double qf = (SQRT3 / 3 * x0 - y0 / 3) / r;
        double rf = (2.0 / 3 * y0) / r;
        // cube rounding
        double sf = -qf - rf;
        long rq = Math.round(qf), rr = Math.round(rf), rs = Math.round(sf);
        double dq = Math.abs(rq - qf), dr = Math.abs(rr - rf), ds = Math.abs(rs - sf);
        if (dq > dr && dq > ds) rq = -rr - rs;
        else if (dr > ds) rr = -rq - rs;
        int row = (int) rr;
        int col = (int) (rq + (rr - (rr & 1)) / 2);
        return inside(col, row) ? new int[]{col, row} : null;
    }

    private static int[] axial(int col, int row) {
        return new int[]{col - (row - (row & 1)) / 2, row};
    }

    @Override
    public int distance(int c1, int r1, int c2, int r2) {
        int[] a = axial(c1, r1), b = axial(c2, r2);
        int dq = a[0] - b[0], dr = a[1] - b[1], ds = -dq - dr;
        return Math.max(Math.abs(dq), Math.max(Math.abs(dr), Math.abs(ds)));
    }
}
