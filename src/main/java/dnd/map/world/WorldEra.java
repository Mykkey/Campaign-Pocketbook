package dnd.map.world;

import dnd.model.WorldMap;
import dnd.model.WorldPath;
import dnd.model.WorldRegion;
import dnd.model.WorldStamp;

/**
 * Eras: a map can be shown as it was in a chosen year. Symbols, lines and regions may be given the years they exist (from
 * a year, until a year, or both); in a chosen year only the ones that exist then are shown. With no year chosen (or no
 * years given) everything is shown.
 */
public final class WorldEra {
    private WorldEra() { }

    /** True if something that exists from {@code from} until {@code to} (either may be null: no limit) exists in {@code year} (null: no year chosen). */
    public static boolean visible(Integer from, Integer to, Integer year) {
        if (year == null) return true;
        return (from == null || year >= from) && (to == null || year <= to);
    }

    public static boolean visible(WorldStamp s, WorldMap map) {
        return visible(s.fromYear, s.toYear, map.year);
    }

    public static boolean visible(WorldPath p, WorldMap map) {
        return visible(p.fromYear, p.toYear, map.year);
    }

    public static boolean visible(WorldRegion r, WorldMap map) {
        return visible(r.fromYear, r.toYear, map.year);
    }

    /** "from year 120 to year 340", "from year 120", "until year 340", or "" if there is no limit. */
    public static String describe(Integer from, Integer to) {
        if (from != null && to != null) return "years " + from + " to " + to;
        if (from != null) return "from year " + from;
        if (to != null) return "until year " + to;
        return "";
    }
}
