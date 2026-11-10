package dnd.map.world;

import dnd.model.WorldGridKind;
import dnd.model.WorldMap;
import dnd.model.WorldStyle;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

/**
 * The square or hex grid over a world map. A square cell is {@code gridMiles} across; a hex is {@code gridMiles}
 * across its flat sides (pointy side up). Cells are named like a spreadsheet: column letters, then row number (A1, B1...).
 */
public final class WorldGrid {
    private WorldGrid() { }

    private static final double SQRT3 = Math.sqrt(3);

    /** The width of a square cell, or of a hex across its flats, in map cells. */
    public static double cellSize(WorldMap map) {
        return Math.max(0.5, map.gridMiles / map.milesPerCell);
    }

    /** The column and row of the grid cell holding the point (px, py), or null if there is no grid. */
    public static int[] cellAt(WorldMap map, double px, double py) {
        if (map.gridKind == WorldGridKind.NONE) return null;
        double size = cellSize(map);
        if (map.gridKind == WorldGridKind.SQUARE) return new int[]{(int) Math.floor(px / size), (int) Math.floor(py / size)};
        double radius = size / SQRT3;
        double q = (SQRT3 / 3 * px - py / 3) / radius, r = 2.0 / 3 * py / radius;
        // round the cube coordinates to the nearest hex
        double x = q, z = r, y = -x - z;
        double rx = Math.round(x), ry = Math.round(y), rz = Math.round(z);
        double dx = Math.abs(rx - x), dy = Math.abs(ry - y), dz = Math.abs(rz - z);
        if (dx > dy && dx > dz) rx = -ry - rz;
        else if (dy > dz) ry = -rx - rz;
        else rz = -rx - ry;
        int row = (int) rz, col = (int) (rx + (rz - (((int) rz) & 1)) / 2);
        return new int[]{col, row};
    }

    /** The middle of a cell in map cells. */
    public static double[] centreOf(WorldMap map, int col, int row) {
        double size = cellSize(map);
        if (map.gridKind == WorldGridKind.SQUARE) return new double[]{(col + 0.5) * size, (row + 0.5) * size};
        double radius = size / SQRT3;
        return new double[]{size * (col + 0.5 * (row & 1)), radius * 1.5 * row};
    }

    /** A cell's name: column letters then the row number, both counted from the top left ("A1", "Z3", "AB12"). */
    public static String name(int col, int row) {
        if (col < 0 || row < 0) return "";
        StringBuilder sb = new StringBuilder();
        int c = col;
        do {
            sb.insert(0, (char) ('A' + c % 26));
            c = c / 26 - 1;
        } while (c >= 0);
        return sb.append(row + 1).toString();
    }

    /** Draws the grid lines (and the cell names, when there is room) over the part of the map in view. */
    public static void draw(GraphicsContext g, WorldMap map, double zoom, double x0, double y0, double x1, double y1) {
        if (map.gridKind == WorldGridKind.NONE) return;
        double size = cellSize(map);
        if (size * zoom < 7) return; // too fine to be useful
        WorldPalette pal = WorldPalette.of(map.style);
        boolean dark = map.style == WorldStyle.DARK_FANTASY;
        Color line = dark ? Color.web("#ffffff", 0.28) : Color.web(String.format("#%06x", pal.ink & 0xffffff), 0.4);
        g.save();
        g.beginPath();
        g.rect(0, 0, map.cols, map.rows);
        g.clip();
        g.setStroke(line);
        g.setLineWidth(Math.max(0.8 / zoom, size * 0.015));
        double left = Math.max(0, x0), top = Math.max(0, y0), right = Math.min(map.cols, x1), bottom = Math.min(map.rows, y1);
        if (map.gridKind == WorldGridKind.SQUARE) {
            for (double x = Math.ceil(left / size) * size; x <= right; x += size) g.strokeLine(x, top, x, bottom);
            for (double y = Math.ceil(top / size) * size; y <= bottom; y += size) g.strokeLine(left, y, right, y);
        } else {
            double radius = size / SQRT3;
            int r0 = (int) Math.floor(top / (radius * 1.5)) - 1, r1 = (int) Math.ceil(bottom / (radius * 1.5)) + 1;
            int c0 = (int) Math.floor(left / size) - 1, c1 = (int) Math.ceil(right / size) + 1;
            double[] xs = new double[6], ys = new double[6];
            for (int r = r0; r <= r1; r++) {
                for (int c = c0; c <= c1; c++) {
                    double cx = size * (c + 0.5 * (r & 1)), cy = radius * 1.5 * r;
                    for (int k = 0; k < 6; k++) {
                        double a = Math.toRadians(60 * k - 30);
                        xs[k] = cx + radius * Math.cos(a);
                        ys[k] = cy + radius * Math.sin(a);
                    }
                    g.strokePolygon(xs, ys, 6);
                }
            }
        }
        if (map.gridCoords && size * zoom >= 26) {
            g.setFont(Font.font("SansSerif", Math.min(size * 0.22, 4)));
            g.setTextAlign(TextAlignment.CENTER);
            g.setFill(dark ? Color.web("#ffffff", 0.55) : Color.web(String.format("#%06x", pal.ink & 0xffffff), 0.6));
            int cMax = map.gridKind == WorldGridKind.SQUARE ? (int) Math.ceil(map.cols / size) : (int) Math.ceil(map.cols / size) + 1;
            int rMax = map.gridKind == WorldGridKind.SQUARE ? (int) Math.ceil(map.rows / size) : (int) Math.ceil(map.rows / (size / SQRT3 * 1.5)) + 1;
            for (int r = 0; r < rMax; r++) {
                for (int c = 0; c < cMax; c++) {
                    double[] m = centreOf(map, c, r);
                    if (m[0] < left - size || m[0] > right + size || m[1] < top - size || m[1] > bottom + size) continue;
                    if (m[0] >= map.cols || m[1] >= map.rows) continue;
                    g.fillText(name(c, r), m[0], m[1] - size * 0.28);
                }
            }
        }
        g.restore();
    }
}
