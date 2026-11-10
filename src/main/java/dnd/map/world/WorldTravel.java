package dnd.map.world;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Distances along a route and how long they take at different speeds. */
public final class WorldTravel {
    private WorldTravel() { }

    /** Hours of travel in a day. */
    public static final double HOURS_PER_DAY = 8;

    /** Miles per day for each way of travelling (the 5th edition rules: 18, 24 and 30 miles on foot at a slow, normal and fast pace). */
    public static Map<String, Double> defaultPaces() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("On foot, slow", 18.0);
        m.put("On foot", 24.0);
        m.put("On foot, fast", 30.0);
        m.put("Riding", 48.0);
        m.put("Sailing ship", 48.0);
        m.put("Longship", 72.0);
        return m;
    }

    /** The length of a route in map cells; points are x0, y0, x1, y1... */
    public static double length(List<Double> points) {
        double len = 0;
        for (int i = 2; i + 1 < points.size(); i += 2) len += Math.hypot(points.get(i) - points.get(i - 2), points.get(i + 1) - points.get(i - 1));
        return len;
    }

    /** A time in days as words: "3 days 2 hr", "5 hr", "under an hour". */
    public static String duration(double miles, double milesPerDay) {
        if (milesPerDay <= 0 || miles <= 0) return "-";
        double hours = miles / milesPerDay * HOURS_PER_DAY;
        long totalHours = Math.round(hours);
        if (totalHours < 1) return "under an hour";
        long days = (long) (totalHours / HOURS_PER_DAY), rest = Math.round(totalHours - days * HOURS_PER_DAY);
        if (days == 0) return rest + " hr";
        String d = days + (days == 1 ? " day" : " days");
        return rest == 0 ? d : d + " " + rest + " hr";
    }

    /** A distance in miles as words: "12 mi", "1,240 mi", "0.4 mi". */
    public static String distance(double miles) {
        if (miles < 1) return String.format("%.1f mi", miles);
        return String.format("%,.0f mi", miles);
    }

    /** A round length for a scale bar that is at most {@code atMost} miles: 1, 2, 5, 10, 20, 50... */
    public static double niceLength(double atMost) {
        if (atMost <= 0) return 1;
        double mag = Math.pow(10, Math.floor(Math.log10(atMost)));
        for (double m : new double[]{5, 2, 1}) if (m * mag <= atMost) return m * mag;
        return mag;
    }
}
