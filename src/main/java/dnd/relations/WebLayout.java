package dnd.relations;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Spreads the people of a web evenly round a circle, starting at the top. Pure maths, so it can be tested. */
public final class WebLayout {
    private WebLayout() { }

    /** The radius, in the same units as the positions, that keeps people about {@code spacing} apart. */
    public static double radius(int count, double spacing) {
        if (count <= 1) return 0;
        if (count == 2) return spacing / 2;
        return Math.max(spacing / 2, spacing / (2 * Math.sin(Math.PI / count)));
    }

    /** Positions (x, y) relative to the centre, y pointing down. */
    public static Map<String, double[]> circle(List<String> ids, double spacing) {
        Map<String, double[]> out = new LinkedHashMap<>();
        int n = ids.size();
        double r = radius(n, spacing);
        for (int i = 0; i < n; i++) {
            double angle = -Math.PI / 2 + 2 * Math.PI * i / Math.max(1, n);
            out.put(ids.get(i), new double[]{r * Math.cos(angle), r * Math.sin(angle)});
        }
        return out;
    }
}