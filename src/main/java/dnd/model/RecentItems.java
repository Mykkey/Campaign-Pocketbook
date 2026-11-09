package dnd.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** The short "recently opened" list kept in the project: newest first, no repeats, capped. */
public final class RecentItems {
    private RecentItems() { }

    public static final int MAX = 10;

    /** Puts {@code id} at the front, removing any earlier copy, and trims the list to {@link #MAX}. */
    public static void add(List<String> recent, String id) {
        if (id == null) return;
        recent.remove(id);
        recent.add(0, id);
        while (recent.size() > MAX) recent.remove(recent.size() - 1);
    }

    /** The entries that still exist, in order (the stored list itself is left alone). */
    public static List<String> existing(List<String> recent, Predicate<String> exists) {
        List<String> out = new ArrayList<>();
        for (String id : recent) if (exists.test(id)) out.add(id);
        return out;
    }
}