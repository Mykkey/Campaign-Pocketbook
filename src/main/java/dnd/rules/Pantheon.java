package dnd.rules;

import dnd.model.Card;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Looks at a set of deities as a pantheon: where each sits on the alignment grid, which domains are covered (and which are
 * left unclaimed), and how they rank. Pure logic on the cards, so it can be tested.
 */
public final class Pantheon {
    private Pantheon() { }

    public static final String[] LAW = {"Lawful", "Neutral", "Chaotic"};
    public static final String[] MORAL = {"Good", "Neutral", "Evil"};

    /** The domains of the 5th edition rules, so the overview can show which are not claimed by any god. */
    public static final List<String> STANDARD_DOMAINS = List.of("Arcana", "Death", "Forge", "Grave", "Knowledge", "Life", "Light", "Nature",
            "Order", "Peace", "Tempest", "Trickery", "Twilight", "War");

    // ---------------------------------------------------------------- alignment

    /**
     * Where an alignment written as text sits on the nine-square grid: {row, column} with row 0 lawful, 1 neutral, 2 chaotic and
     * column 0 good, 1 neutral, 2 evil. Understands "lawful good", "chaotic evil", "neutral", "true neutral", "LG", "CN", "N"
     * and the like; returns null for "unaligned", "any" or anything it does not recognise.
     */
    public static int[] alignmentCell(String text) {
        if (text == null) return null;
        String s = text.trim().toLowerCase(Locale.ROOT).replace("-", " ");
        if (s.isEmpty()) return null;
        if (s.matches("[lnc][gne]")) return new int[]{"lnc".indexOf(s.charAt(0)), "gne".indexOf(s.charAt(1))};
        if (s.equals("n") || s.equals("true neutral") || s.equals("neutral") || s.equals("neutral neutral")) return new int[]{1, 1};
        int row = -1, col = -1;
        for (String w : s.split("[\\s/,]+")) {
            switch (w) {
                case "lawful" -> row = 0;
                case "chaotic" -> row = 2;
                case "good" -> col = 0;
                case "evil" -> col = 2;
                case "neutral", "true" -> {
                    // "neutral" alone decides whichever direction has not been said yet
                    if (w.equals("neutral")) {
                        if (row < 0) row = 1;
                        else if (col < 0) col = 1;
                    }
                }
                default -> { }
            }
        }
        if (row < 0 && col < 0) return null;
        return new int[]{row < 0 ? 1 : row, col < 0 ? 1 : col};
    }

    /** The deities on the nine-square grid: {@code grid.get(row).get(column)}, and the ones with no alignment the grid can show. */
    public record AlignmentGrid(List<List<List<Card>>> cells, List<Card> elsewhere) { }

    public static AlignmentGrid alignmentGrid(List<Card> gods) {
        List<List<List<Card>>> cells = new ArrayList<>();
        for (int r = 0; r < 3; r++) {
            List<List<Card>> row = new ArrayList<>();
            for (int c = 0; c < 3; c++) row.add(new ArrayList<>());
            cells.add(row);
        }
        List<Card> elsewhere = new ArrayList<>();
        for (Card g : sorted(gods)) {
            int[] at = alignmentCell(g.alignment);
            if (at == null) elsewhere.add(g);
            else cells.get(at[0]).get(at[1]).add(g);
        }
        return new AlignmentGrid(cells, elsewhere);
    }

    // ---------------------------------------------------------------- domains

    /** The domains written on a card ("Life, Light" or "Life and Light"), each starting with a capital, without repeats. */
    public static List<String> domainsOf(Card c) {
        List<String> out = new ArrayList<>();
        if (c.domains == null) return out;
        for (String part : c.domains.split("[,;/&]|\\band\\b")) {
            String d = part.trim();
            if (d.isEmpty()) continue;
            d = Character.toUpperCase(d.charAt(0)) + d.substring(1);
            boolean seen = false;
            for (String o : out) if (o.equalsIgnoreCase(d)) seen = true;
            if (!seen) out.add(d);
        }
        return out;
    }

    /**
     * Each domain and the gods that hold it: the standard domains first (empty if no god has them), then any others the gods
     * have, in alphabetical order. A god with several domains appears under each.
     */
    public static Map<String, List<Card>> byDomain(List<Card> gods) {
        Map<String, List<Card>> out = new LinkedHashMap<>();
        for (String d : STANDARD_DOMAINS) out.put(d, new ArrayList<>());
        Map<String, List<Card>> extra = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Card g : sorted(gods)) {
            for (String d : domainsOf(g)) {
                String standard = null;
                for (String s : STANDARD_DOMAINS) if (s.equalsIgnoreCase(d)) standard = s;
                if (standard != null) out.get(standard).add(g);
                else extra.computeIfAbsent(d, k -> new ArrayList<>()).add(g);
            }
        }
        out.putAll(extra);
        return out;
    }

    /** The standard domains that no god holds. */
    public static List<String> unclaimed(List<Card> gods) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, List<Card>> e : byDomain(gods).entrySet()) {
            if (e.getValue().isEmpty() && STANDARD_DOMAINS.contains(e.getKey())) out.add(e.getKey());
        }
        return out;
    }

    // ---------------------------------------------------------------- rank and groups

    /** How high a rank written as text is: greater gods first, then intermediate, lesser, demigods, and the rest; blank is last. */
    static int rankOrder(String rank) {
        if (rank == null || rank.isBlank()) return 99;
        String r = rank.toLowerCase(Locale.ROOT);
        if (r.contains("primordial") || r.contains("overgod") || r.contains("supreme")) return 0;
        if (r.contains("greater")) return 1;
        if (r.contains("intermediate")) return 2;
        if (r.contains("lesser")) return 3;
        if (r.contains("demi")) return 4;
        return 10;
    }

    /** The gods grouped by the rank they wrote (blank ranks under "No rank"), the highest rank first. */
    public static Map<String, List<Card>> byRank(List<Card> gods) {
        List<Card> list = sorted(gods);
        list.sort(Comparator.comparingInt((Card c) -> rankOrder(c.divineRank)).thenComparing(c -> rankName(c).toLowerCase(Locale.ROOT)));
        Map<String, List<Card>> out = new LinkedHashMap<>();
        for (Card g : list) out.computeIfAbsent(rankName(g), k -> new ArrayList<>()).add(g);
        return out;
    }

    private static String rankName(Card c) {
        return c.divineRank == null || c.divineRank.isBlank() ? "No rank" : c.divineRank.trim();
    }

    /**
     * The gods grouped by the organisations they belong to (a pantheon, a court). A god in several appears in each; those in none are
     * under "Not in an organisation", listed last.
     */
    public static Map<String, List<Card>> byGroup(List<Card> gods, Function<Card, List<String>> groupsOf) {
        Map<String, List<Card>> groups = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<Card> loose = new ArrayList<>();
        for (Card g : sorted(gods)) {
            List<String> names = groupsOf.apply(g);
            if (names == null || names.isEmpty()) loose.add(g);
            else for (String n : names) groups.computeIfAbsent(n, k -> new ArrayList<>()).add(g);
        }
        Map<String, List<Card>> out = new LinkedHashMap<>(groups);
        if (!loose.isEmpty()) out.put("Not in an organisation", loose);
        return out;
    }

    /** Just the deities, by name. */
    public static List<Card> deitiesOf(List<Card> cards) {
        List<Card> out = new ArrayList<>();
        for (Card c : cards) if (c.kind == Card.Kind.DEITY) out.add(c);
        return sorted(out);
    }

    private static List<Card> sorted(List<Card> gods) {
        List<Card> out = new ArrayList<>(gods);
        out.sort(Comparator.comparing((Card c) -> c.name == null ? "" : c.name.toLowerCase(Locale.ROOT)));
        return out;
    }
}
