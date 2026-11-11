package dnd.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToLongFunction;

/** Remembers when each campaign was last opened, so the project list can show the latest one first. */
public final class RecentProjects {
    private RecentProjects() { }

    /** The settings key of a campaign's "last opened" time. Kept on this computer only (not saved in exported settings, not reset). */
    static String key(String folderName) {
        return "internal.opened." + folderName;
    }

    /** Notes that the campaign was opened just now. */
    public static void opened(String folderName) {
        opened(folderName, System.currentTimeMillis());
    }

    static void opened(String folderName, long when) {
        AppSettings.setString(key(folderName), Long.toString(when));
    }

    /** Forgets a campaign that was deleted. */
    public static void forget(String folderName) {
        AppSettings.remove(key(folderName));
    }

    /** When the campaign was last opened (milliseconds), or 0 if it never was on this computer. */
    public static long lastOpened(String folderName) {
        try {
            return Long.parseLong(AppSettings.getString(key(folderName), "0").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The names most recently opened first; those never opened follow, in alphabetical order. */
    public static List<String> sorted(List<String> names) {
        return order(names, RecentProjects::lastOpened);
    }

    static List<String> order(List<String> names, ToLongFunction<String> openedAt) {
        List<String> out = new ArrayList<>(names);
        out.sort(Comparator.comparingLong((String n) -> openedAt.applyAsLong(n)).reversed().thenComparing(String.CASE_INSENSITIVE_ORDER));
        return out;
    }
}
