package dnd.text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Finds the names of cards and spell cards inside a piece of text, so the whiteboard can offer to link them. Pure
 * logic (no UI). A name matches as whole words, ignoring case, with the longest names taking priority; names inside
 * existing link/tooltip spans, names the DM dismissed and names shorter than {@link #MIN_LENGTH} are skipped.
 */
public final class NameHints {
    private NameHints() { }

    public static final int MIN_LENGTH = 3;

    /** A name that can be offered, and the item it belongs to. */
    public record Entry(String name, String itemId) { }

    /** A name found in the text: characters {@code start} up to {@code end} (plain-text positions). */
    public record Hint(int start, int end, String itemId, String name) { }

    /** One item with the other names it goes by. */
    public record ItemNames(String itemId, String name, List<String> aliases) { }

    /**
     * The entries to match for a set of items: every item's own name first, then the aliases, so a real name always
     * wins when an alias clashes with it. Run the result through {@link #index}.
     */
    public static List<Entry> entriesFor(List<ItemNames> items) {
        List<Entry> out = new ArrayList<>();
        for (ItemNames i : items) out.add(new Entry(i.name(), i.itemId()));
        for (ItemNames i : items) {
            if (i.aliases() == null) continue;
            for (String alias : i.aliases()) out.add(new Entry(alias, i.itemId()));
        }
        return out;
    }

    /** The entries worth matching (long enough, one per name ignoring case), longest first. */
    public static List<Entry> index(List<Entry> entries) {
        List<Entry> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Entry e : entries) {
            if (e.name() == null) continue;
            String n = e.name().trim();
            if (n.length() < MIN_LENGTH || !seen.add(n.toLowerCase(Locale.ROOT))) continue;
            out.add(new Entry(n, e.itemId()));
        }
        out.sort(Comparator.comparingInt((Entry e) -> e.name().length()).reversed().thenComparing(e -> e.name().toLowerCase(Locale.ROOT)));
        return out;
    }

    private static boolean wordChar(char c) {
        return Character.isLetterOrDigit(c);
    }

    /**
     * @param plain     the text without markup
     * @param spans     spans already in the text (their ranges are left alone)
     * @param index     entries from {@link #index}, longest first
     * @param dismissed lower-case names to skip
     */
    public static List<Hint> find(String plain, List<RichText.Span> spans, List<Entry> index, Set<String> dismissed) {
        List<Hint> out = new ArrayList<>();
        if (plain == null || plain.isEmpty() || index.isEmpty()) return out;
        String lower = plain.toLowerCase(Locale.ROOT);
        for (Entry e : index) {
            String name = e.name().toLowerCase(Locale.ROOT);
            if (dismissed != null && dismissed.contains(name)) continue;
            int from = 0;
            while (from <= lower.length() - name.length()) {
                int at = lower.indexOf(name, from);
                if (at < 0) break;
                int end = at + name.length();
                from = at + 1;
                if (at > 0 && wordChar(plain.charAt(at - 1))) continue;
                if (end < plain.length() && wordChar(plain.charAt(end))) continue;
                if (overlapsSpan(spans, at, end) || overlapsHint(out, at, end)) continue;
                out.add(new Hint(at, end, e.itemId(), plain.substring(at, end)));
                from = end;
            }
        }
        out.sort(Comparator.comparingInt(Hint::start));
        return out;
    }

    private static boolean overlapsSpan(List<RichText.Span> spans, int a, int b) {
        if (spans != null) for (RichText.Span s : spans) if (a < s.plainEnd() && b > s.plainStart()) return true;
        return false;
    }

    private static boolean overlapsHint(List<Hint> hints, int a, int b) {
        for (Hint h : hints) if (a < h.end() && b > h.start()) return true;
        return false;
    }
}