package dnd.loot;

import dnd.rules.Dice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Random treasure for a DM. Pick a tier (by the challenge rating of what the party beat) and either "a creature's
 * pockets" (a bit of coin) or a "hoard" (coin, gems, art, magic items and curios). The amounts are rough guidance
 * in the spirit of the usual treasure tiers, not a copy of any published table. Pure logic with an injectable
 * {@link Random}, so it can be tested.
 */
public final class LootGenerator {
    private LootGenerator() { }

    public enum Tier {
        TIER1("Tier 1 (CR 0-4)"), TIER2("Tier 2 (CR 5-10)"), TIER3("Tier 3 (CR 11-16)"), TIER4("Tier 4 (CR 17+)");

        public final String label;

        Tier(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Kind {
        INDIVIDUAL("A creature's pockets"), HOARD("A treasure hoard");

        public final String label;

        Kind(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Rarity {
        COMMON("common"), UNCOMMON("uncommon"), RARE("rare"), VERY_RARE("very rare"), LEGENDARY("legendary");

        public final String label;

        Rarity(String label) {
            this.label = label;
        }
    }

    /** A gem or art object. */
    public record Valuable(String name, int gp, boolean art) { }

    /** A magic item. */
    public record MagicItem(String name, Rarity rarity) { }

    /** The result of a roll. Coins are keyed cp, sp, gp, pp. */
    public static final class Loot {
        public final Map<String, Integer> coins = new LinkedHashMap<>();
        public final List<Valuable> valuables = new ArrayList<>();
        public final List<MagicItem> items = new ArrayList<>();
        public final List<String> curios = new ArrayList<>();

        Loot() {
            for (String c : COINS) coins.put(c, 0);
        }

        public boolean isEmpty() {
            return coins.values().stream().allMatch(v -> v == 0) && valuables.isEmpty() && items.isEmpty() && curios.isEmpty();
        }

        /** Rough worth in gold pieces: coins, gems and art (magic items are not priced). */
        public int totalGp() {
            double gp = coins.get("cp") / 100.0 + coins.get("sp") / 10.0 + coins.get("gp") + coins.get("pp") * 10.0;
            for (Valuable v : valuables) gp += v.gp();
            return (int) Math.round(gp);
        }

        /** Plain text for the dialog, the clipboard and a whiteboard note. */
        public String toText() {
            StringBuilder sb = new StringBuilder();
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, Integer> e : coins.entrySet()) {
                if (e.getValue() > 0) parts.add(String.format("%,d %s", e.getValue(), e.getKey()));
            }
            if (!parts.isEmpty()) sb.append("Coins: ").append(String.join(", ", parts)).append('\n');
            appendValuables(sb, "Gems", false);
            appendValuables(sb, "Art objects", true);
            if (!items.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (MagicItem m : items) names.add(m.name() + " (" + m.rarity().label + ")");
                sb.append("Magic items: ").append(String.join("; ", names)).append('\n');
            }
            if (!curios.isEmpty()) sb.append("Odds and ends: ").append(String.join("; ", curios)).append('\n');
            if (isEmpty()) sb.append("Nothing of value.\n");
            else if (totalGp() > 0) sb.append(String.format("Roughly %,d gp in all, not counting magic items.%n", totalGp()));
            return sb.toString().trim();
        }

        private void appendValuables(StringBuilder sb, String heading, boolean art) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (Valuable v : valuables) {
                if (v.art() != art) continue;
                counts.merge(v.name() + " (" + v.gp() + " gp" + ")", 1, Integer::sum);
            }
            if (counts.isEmpty()) return;
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, Integer> e : counts.entrySet()) parts.add(e.getValue() > 1 ? e.getValue() + " x " + e.getKey() : e.getKey());
            sb.append(heading).append(": ").append(String.join("; ", parts)).append('\n');
        }
    }

    private static final String[] COINS = {"cp", "sp", "gp", "pp"};

    private record CoinRoll(String coin, String dice, int multiplier, int chance) { }

    private static final CoinRoll[][] INDIVIDUAL = {
            {new CoinRoll("cp", "4d6", 1, 90), new CoinRoll("sp", "2d6", 1, 60), new CoinRoll("gp", "1d6", 1, 30)},
            {new CoinRoll("sp", "4d6", 10, 70), new CoinRoll("gp", "2d6", 10, 60), new CoinRoll("pp", "1d6", 1, 15)},
            {new CoinRoll("gp", "4d6", 10, 80), new CoinRoll("gp", "2d6", 100, 40), new CoinRoll("pp", "2d6", 1, 50)},
            {new CoinRoll("gp", "2d6", 100, 80), new CoinRoll("pp", "3d6", 10, 60)}};

    private static final CoinRoll[][] HOARD = {
            {new CoinRoll("cp", "3d6", 100, 100), new CoinRoll("sp", "3d6", 100, 100), new CoinRoll("gp", "2d6", 10, 100)},
            {new CoinRoll("sp", "2d6", 100, 100), new CoinRoll("gp", "3d6", 100, 100), new CoinRoll("pp", "2d6", 10, 100)},
            {new CoinRoll("gp", "2d6", 1000, 100), new CoinRoll("pp", "1d6", 100, 100)},
            {new CoinRoll("gp", "6d6", 1000, 100), new CoinRoll("pp", "4d6", 1000, 100)}};

    private record ValuableSpec(int chance, String dice, int[] values) { }

    private static final ValuableSpec[] GEM_SPEC = {
            new ValuableSpec(60, "2d4", new int[]{10, 50}), new ValuableSpec(70, "2d6", new int[]{50, 100}),
            new ValuableSpec(80, "2d6", new int[]{100, 500, 1000}), new ValuableSpec(90, "2d6", new int[]{500, 1000, 5000})};
    private static final ValuableSpec[] ART_SPEC = {
            new ValuableSpec(40, "1d4", new int[]{25}), new ValuableSpec(60, "1d6", new int[]{25, 250}),
            new ValuableSpec(70, "2d4", new int[]{250, 750}), new ValuableSpec(80, "2d4", new int[]{750, 2500, 7500})};

    private record MagicSpec(int chance, String dice, Rarity[] rarities, int[] weights) { }

    private static final MagicSpec[] MAGIC_SPEC = {
            new MagicSpec(40, "1d2", new Rarity[]{Rarity.COMMON, Rarity.UNCOMMON}, new int[]{60, 40}),
            new MagicSpec(70, "1d3", new Rarity[]{Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE}, new int[]{20, 55, 25}),
            new MagicSpec(80, "1d3", new Rarity[]{Rarity.UNCOMMON, Rarity.RARE, Rarity.VERY_RARE}, new int[]{30, 55, 15}),
            new MagicSpec(90, "1d4", new Rarity[]{Rarity.RARE, Rarity.VERY_RARE, Rarity.LEGENDARY}, new int[]{25, 50, 25})};

    /** Rarities that can turn up in a hoard of this tier. */
    public static List<Rarity> raritiesFor(Tier tier) {
        return List.of(MAGIC_SPEC[tier.ordinal()].rarities());
    }

    /**
     * Rolls treasure.
     *
     * @param count for {@link Kind#INDIVIDUAL}, how many creatures' pockets to add together (1-50); a hoard is
     *              always a single pile
     */
    public static Loot generate(Tier tier, Kind kind, int count, Random r) {
        Loot loot = new Loot();
        int t = tier.ordinal();
        if (kind == Kind.INDIVIDUAL) {
            int n = Math.max(1, Math.min(50, count));
            for (int i = 0; i < n; i++) {
                for (CoinRoll c : INDIVIDUAL[t]) rollCoin(loot, c, r);
                if (r.nextInt(100) < 8 - 2 * t) loot.curios.add(pick(LootData.CURIOS, r)); // an occasional keepsake
            }
            if (loot.coins.values().stream().allMatch(v -> v == 0)) { // never hand back a completely empty purse
                CoinRoll c = INDIVIDUAL[t][0];
                loot.coins.merge(c.coin(), Math.max(1, roll(c.dice(), r)) * c.multiplier(), Integer::sum);
            }
            dedupe(loot.curios);
            return loot;
        }
        for (CoinRoll c : HOARD[t]) rollCoin(loot, c, r);
        addValuables(loot, GEM_SPEC[t], false, t, r);
        addValuables(loot, ART_SPEC[t], true, t, r);
        MagicSpec m = MAGIC_SPEC[t];
        if (r.nextInt(100) < m.chance()) {
            int n = Math.max(1, roll(m.dice(), r));
            for (int i = 0; i < n; i++) {
                Rarity rar = weighted(m.rarities(), m.weights(), r);
                loot.items.add(new MagicItem(pick(magicTable(rar), r), rar));
            }
        }
        int curios = r.nextInt(3); // 0-2
        for (int i = 0; i < curios; i++) loot.curios.add(pick(LootData.CURIOS, r));
        dedupe(loot.curios);
        return loot;
    }

    private static void rollCoin(Loot loot, CoinRoll c, Random r) {
        if (r.nextInt(100) >= c.chance()) return;
        loot.coins.merge(c.coin(), roll(c.dice(), r) * c.multiplier(), Integer::sum);
    }

    private static void addValuables(Loot loot, ValuableSpec spec, boolean art, int tier, Random r) {
        if (r.nextInt(100) >= spec.chance()) return;
        int n = Math.max(1, roll(spec.dice(), r));
        for (int i = 0; i < n; i++) {
            int gp = spec.values()[r.nextInt(spec.values().length)];
            loot.valuables.add(new Valuable(pick(art ? artTable(gp) : gemTable(gp), r), gp, art));
        }
    }

    private static String[] gemTable(int gp) {
        return switch (gp) {
            case 10 -> LootData.GEMS_10;
            case 50 -> LootData.GEMS_50;
            case 100 -> LootData.GEMS_100;
            case 500 -> LootData.GEMS_500;
            case 1000 -> LootData.GEMS_1000;
            default -> LootData.GEMS_5000;
        };
    }

    private static String[] artTable(int gp) {
        return switch (gp) {
            case 25 -> LootData.ART_25;
            case 250 -> LootData.ART_250;
            case 750 -> LootData.ART_750;
            case 2500 -> LootData.ART_2500;
            default -> LootData.ART_7500;
        };
    }

    private static String[] magicTable(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> LootData.MAGIC_COMMON;
            case UNCOMMON -> LootData.MAGIC_UNCOMMON;
            case RARE -> LootData.MAGIC_RARE;
            case VERY_RARE -> LootData.MAGIC_VERY_RARE;
            case LEGENDARY -> LootData.MAGIC_LEGENDARY;
        };
    }

    private static Rarity weighted(Rarity[] rarities, int[] weights, Random r) {
        int total = 0;
        for (int w : weights) total += w;
        int roll = r.nextInt(total);
        for (int i = 0; i < rarities.length; i++) {
            roll -= weights[i];
            if (roll < 0) return rarities[i];
        }
        return rarities[rarities.length - 1];
    }

    private static int roll(String dice, Random r) {
        return Dice.parse(dice).orElseThrow().roll(r);
    }

    private static String pick(String[] table, Random r) {
        return table[r.nextInt(table.length)];
    }

    private static void dedupe(List<String> list) {
        List<String> seen = new ArrayList<>();
        for (String s : list) if (!seen.contains(s)) seen.add(s);
        list.clear();
        list.addAll(seen);
    }
}
