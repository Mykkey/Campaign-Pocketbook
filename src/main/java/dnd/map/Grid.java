package dnd.map;

import dnd.model.BattleMap;

/** Geometry of a battlemap grid: where cells are, which cell a point is in, and how far apart cells are. */
public interface Grid {
    int cols();

    int rows();

    /** Total size of the grid area in map units. */
    double width();

    double height();

    /** Centre of a cell as {x, y}. */
    double[] center(int col, int row);

    /** Corner points of a cell as x0,y0,x1,y1,... */
    double[] polygon(int col, int row);

    /** The {col, row} containing the point, or null if it is outside the grid. */
    int[] cellAt(double x, double y);

    /** Distance in cells between two cells (a hex step or a square step counts as 1). */
    int distance(int col1, int row1, int col2, int row2);

    static Grid of(BattleMap m) {
        return m.gridType == BattleMap.GridType.HEX
                ? new HexGrid(m.cols, m.rows, m.cellSize)
                : new SquareGrid(m.cols, m.rows, m.cellSize);
    }

    default boolean inside(int col, int row) {
        return col >= 0 && row >= 0 && col < cols() && row < rows();
    }
}
