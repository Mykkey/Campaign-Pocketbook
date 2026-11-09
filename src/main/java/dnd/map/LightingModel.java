package dnd.map;

import dnd.model.BattleMap;
import dnd.model.MapLight;
import dnd.model.MapToken;

/**
 * How well lit each cell of a dark map is, from the lights on it and from what the creatures can see in the dark.
 * Pure logic with no UI, so it can be tested. There are no walls to block light: it is a radius.
 */
public final class LightingModel {
    private LightingModel() { }

    public enum Level { DARK, DIM, BRIGHT }

    /** Where a light is, in map units: at its token if it is carried, otherwise where it was placed. Null if its token is gone. */
    public static double[] position(BattleMap m, Grid g, MapLight l) {
        if (l.tokenId == null) return new double[]{l.x, l.y};
        MapToken t = m.findToken(l.tokenId);
        if (t == null || !g.inside(t.col, t.row)) return null;
        return g.center(t.col, t.row);
    }

    private static double feetBetween(double[] a, double x, double y, double cellSize) {
        return Math.hypot(a[0] - x, a[1] - y) / cellSize * 5.0;
    }

    /**
     * The light level at a cell's centre. Lights give bright light out to their bright radius and dim light out to
     * their dim radius. A creature with vision (darkvision) sees darkness as dim light and dim light as bright light
     * within its range.
     */
    public static Level levelAt(BattleMap m, Grid g, int col, int row) {
        double[] c = g.center(col, row);
        Level level = Level.DARK;
        for (MapLight l : m.lights) {
            double[] p = position(m, g, l);
            if (p == null) continue;
            double d = feetBetween(p, c[0], c[1], m.cellSize);
            if (d <= l.brightFeet + 1e-6) return Level.BRIGHT;
            if (d <= l.dimFeet + 1e-6) level = Level.DIM;
        }
        for (MapToken t : m.tokens) {
            if (t.visionFeet <= 0 || !g.inside(t.col, t.row)) continue;
            double d = feetBetween(g.center(t.col, t.row), c[0], c[1], m.cellSize);
            if (d <= t.visionFeet + 1e-6) {
                level = level == Level.DARK ? Level.DIM : Level.BRIGHT;
                if (level == Level.BRIGHT) break;
            }
        }
        return level;
    }

    /**
     * How many cells out a token sees, for clearing fog: its own reveal radius, its vision range, and (on a dark map)
     * the dim radius of any light it carries.
     */
    public static int sightCells(BattleMap m, MapToken t) {
        int cells = t.revealRadius;
        if (m.darkMap) {
            cells = Math.max(cells, (int) Math.ceil(t.visionFeet / 5.0));
            for (MapLight l : m.lights) {
                if (t.id.equals(l.tokenId)) cells = Math.max(cells, (int) Math.ceil(Math.max(l.dimFeet, l.brightFeet) / 5.0));
            }
        }
        return cells;
    }
}