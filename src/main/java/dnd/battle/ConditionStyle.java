package dnd.battle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One colour per condition, used for the ring drawn around a token, the chips on a card and the legend. */
public final class ConditionStyle {
    private ConditionStyle() { }

    /** Colour for a condition name that is not in {@link #COLORS} (custom conditions). */
    public static final String UNKNOWN = "#9ca3af";

    /** The standard conditions, in menu order, with their ring colour. */
    public static final Map<String, String> COLORS = new LinkedHashMap<>();

    static {
        COLORS.put("Blinded", "#4b5563");
        COLORS.put("Charmed", "#ec4899");
        COLORS.put("Concentrating", "#3b82f6");
        COLORS.put("Deafened", "#a8a29e");
        COLORS.put("Exhausted", "#7c2d12");
        COLORS.put("Frightened", "#8b5cf6");
        COLORS.put("Grappled", "#f97316");
        COLORS.put("Incapacitated", "#dc2626");
        COLORS.put("Invisible", "#67e8f9");
        COLORS.put("Paralyzed", "#ca8a04");
        COLORS.put("Petrified", "#78716c");
        COLORS.put("Poisoned", "#22c55e");
        COLORS.put("Prone", "#b45309");
        COLORS.put("Restrained", "#0d9488");
        COLORS.put("Stunned", "#facc15");
        COLORS.put("Unconscious", "#1e3a8a");
    }

    /** The standard condition names, in menu order. */
    public static List<String> names() {
        return new ArrayList<>(COLORS.keySet());
    }

    /** Hex colour (e.g. {@code #22c55e}) for a condition, ignoring case; grey if it is not a standard one. */
    public static String colorOf(String condition) {
        if (condition == null) return UNKNOWN;
        for (Map.Entry<String, String> e : COLORS.entrySet()) {
            if (e.getKey().equalsIgnoreCase(condition.trim())) return e.getValue();
        }
        return UNKNOWN;
    }

    /** Ring colours for a list of conditions, one per condition, in order. */
    public static List<String> colorsOf(List<String> conditions) {
        List<String> out = new ArrayList<>();
        if (conditions != null) for (String c : conditions) out.add(colorOf(c));
        return out;
    }

    /**
     * Start angles (degrees, counter-clockwise from 3 o'clock as JavaFX arcs use) of the {@code n} equal slices of a
     * ring, beginning at the top. Every slice spans {@code 360 / n} degrees.
     */
    public static double[] sliceStarts(int n) {
        double[] s = new double[Math.max(0, n)];
        double extent = n <= 0 ? 0 : 360.0 / n;
        for (int i = 0; i < s.length; i++) s[i] = 90 + i * extent;
        return s;
    }
}
