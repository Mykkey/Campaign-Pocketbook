package dnd.loot;

import dnd.loot.LootGenerator.Kind;
import dnd.loot.LootGenerator.Loot;
import dnd.loot.LootGenerator.Tier;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** Treasure for a planned fight: works out the tier from the creatures' challenge ratings and rolls with {@link LootGenerator}. */
public final class EncounterLoot {
    private EncounterLoot() { }

    /** One kind of creature in the fight. */
    public record Creature(String name, String challenge, int count) { }

    /** The text to show, and roughly what the coin, gems and art are worth in gold pieces. */
    public record Result(String text, int totalGp) { }

    /** A challenge rating such as "1/4", "0.5" or "5 (1,800 XP)" as a number, or -1 if it cannot be read. */
    static double crValue(String cr) {
        if (cr == null) return -1;
        String s = cr.trim().toLowerCase(Locale.ROOT);
        int sp = s.indexOf(' ');
        if (sp > 0) s = s.substring(0, sp);
        try {
            int slash = s.indexOf('/');
            if (slash > 0) return Double.parseDouble(s.substring(0, slash)) / Double.parseDouble(s.substring(slash + 1));
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** The treasure tier for a challenge rating: 0-4, 5-10, 11-16 and 17 or more. Unreadable ratings count as tier 1. */
    public static Tier tierFor(String challenge) {
        double cr = crValue(challenge);
        if (cr >= 17) return Tier.TIER4;
        if (cr >= 11) return Tier.TIER3;
        if (cr >= 5) return Tier.TIER2;
        return Tier.TIER1;
    }

    /**
     * Rolls treasure for the creatures. For {@link Kind#INDIVIDUAL} every creature carries its own pocket money at its own
     * tier; for {@link Kind#HOARD} one hoard is rolled at the highest tier among them.
     */
    public static Result roll(List<Creature> creatures, Kind kind, Random random) {
        Map<Tier, Integer> byTier = new EnumMap<>(Tier.class);
        Tier top = null;
        int total = 0;
        for (Creature c : creatures) {
            if (c.count() <= 0) continue;
            Tier t = tierFor(c.challenge());
            byTier.merge(t, c.count(), Integer::sum);
            total += c.count();
            if (top == null || t.ordinal() > top.ordinal()) top = t;
        }
        if (total == 0) return new Result("No enemies in this encounter, so there is nothing to roll treasure for.", 0);
        StringBuilder sb = new StringBuilder();
        int gp = 0;
        if (kind == Kind.HOARD) {
            Loot loot = LootGenerator.generate(top, Kind.HOARD, 1, random);
            sb.append("Treasure hoard, ").append(top.label).append("\n\n").append(loot.toText());
            gp = loot.totalGp();
        } else {
            for (Map.Entry<Tier, Integer> e : byTier.entrySet()) {
                Loot loot = LootGenerator.generate(e.getKey(), Kind.INDIVIDUAL, e.getValue(), random);
                if (sb.length() > 0) sb.append("\n\n");
                int n = e.getValue();
                sb.append("Pockets of ").append(n).append(n == 1 ? " creature, " : " creatures, ").append(e.getKey().label).append("\n\n").append(loot.toText());
                gp += loot.totalGp();
            }
        }
        return new Result(sb.toString(), gp);
    }
}
