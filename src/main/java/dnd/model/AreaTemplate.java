package dnd.model;

import dnd.map.Grid;

import java.util.ArrayList;
import java.util.List;

/**
 * A spell or breath area laid over the map: a circle (burst), cone, line or cube, in feet (5 ft per cell). It is a
 * temporary overlay, not saved with the map. A cell is covered when its centre is inside the shape.
 */
public final class AreaTemplate {
    public enum Shape {
        CIRCLE("Circle (burst)"), CONE("Cone"), LINE("Line"), CUBE("Cube");

        public final String label;

        Shape(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Half the opening angle of a cone: its width at any distance equals that distance. */
    public static final double CONE_HALF_ANGLE = Math.atan(0.5);

    public Shape shape;
    /** Where it starts (circle: its centre), in map units. */
    public double ox, oy;
    /** Direction in radians (0 = right, y points down). Not used by circles. */
    public double angle;
    /** Radius (circle), length (cone, line) or side (cube), in feet. */
    public int feet;
    /** Width of a line, in feet. */
    public int widthFeet = 5;
    /** When true the players see it in their window too. */
    public boolean shared;

    public AreaTemplate(Shape shape, double ox, double oy, int feet) {
        this.shape = shape;
        this.ox = ox;
        this.oy = oy;
        this.feet = feet;
    }

    private static double unit(double cellSize) {
        return cellSize / 5.0; // map units per foot
    }

    public boolean contains(double px, double py, double cellSize) {
        double u = unit(cellSize), eps = 1e-6 * cellSize;
        double dx = px - ox, dy = py - oy;
        double len = feet * u;
        switch (shape) {
            case CIRCLE:
                return Math.hypot(dx, dy) <= len + eps;
            case CONE: {
                double dist = Math.hypot(dx, dy);
                if (dist <= eps) return true;
                if (dist > len + eps) return false;
                double diff = Math.abs(normalize(Math.atan2(dy, dx) - angle));
                return diff <= CONE_HALF_ANGLE + 1e-9;
            }
            default: { // LINE, CUBE: a rectangle along the direction
                double cos = Math.cos(angle), sin = Math.sin(angle);
                double along = dx * cos + dy * sin;
                double across = -dx * sin + dy * cos;
                double half = (shape == Shape.LINE ? widthFeet * u : len) / 2;
                return along >= -eps && along <= len + eps && Math.abs(across) <= half + eps;
            }
        }
    }

    private static double normalize(double a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }

    /** The {col, row} of every cell the shape covers. */
    public List<int[]> coveredCells(Grid g, double cellSize) {
        List<int[]> out = new ArrayList<>();
        double reach = feet * unit(cellSize) + cellSize;
        for (int row = 0; row < g.rows(); row++) {
            for (int col = 0; col < g.cols(); col++) {
                double[] c = g.center(col, row);
                if (Math.abs(c[0] - ox) > reach || Math.abs(c[1] - oy) > reach) continue;
                if (contains(c[0], c[1], cellSize)) out.add(new int[]{col, row});
            }
        }
        return out;
    }

    /** Corner points {x0,y0,x1,y1,...} of the outline for cones, lines and cubes; null for a circle. */
    public double[] outline(double cellSize) {
        double u = unit(cellSize), len = feet * u;
        double cos = Math.cos(angle), sin = Math.sin(angle);
        switch (shape) {
            case CIRCLE:
                return null;
            case CONE: { // a sector: the origin, then points along the far arc
                int steps = 12;
                double[] pts = new double[2 + 2 * (steps + 1)];
                pts[0] = ox;
                pts[1] = oy;
                for (int i = 0; i <= steps; i++) {
                    double a = angle - CONE_HALF_ANGLE + 2 * CONE_HALF_ANGLE * i / steps;
                    pts[2 + 2 * i] = ox + Math.cos(a) * len;
                    pts[3 + 2 * i] = oy + Math.sin(a) * len;
                }
                return pts;
            }
            default: {
                double half = (shape == Shape.LINE ? widthFeet * u : len) / 2;
                double px = -sin * half, py = cos * half;
                return new double[]{ox + px, oy + py, ox + px + cos * len, oy + py + sin * len,
                        ox - px + cos * len, oy - py + sin * len, ox - px, oy - py};
            }
        }
    }

    /** Where the label goes: the middle of the shape. */
    public double[] labelPoint(double cellSize) {
        double len = feet * unit(cellSize);
        if (shape == Shape.CIRCLE) return new double[]{ox, oy};
        return new double[]{ox + Math.cos(angle) * len * 0.5, oy + Math.sin(angle) * len * 0.5};
    }

    public String describe(int cells) {
        String name = switch (shape) {
            case CIRCLE -> feet + " ft radius";
            case CONE -> feet + " ft cone";
            case LINE -> feet + " ft line (" + widthFeet + " ft wide)";
            case CUBE -> feet + " ft cube";
        };
        return name + ": " + cells + (cells == 1 ? " cell" : " cells");
    }

    public AreaTemplate copy() {
        AreaTemplate t = new AreaTemplate(shape, ox, oy, feet);
        t.angle = angle;
        t.widthFeet = widthFeet;
        t.shared = shared;
        return t;
    }
}