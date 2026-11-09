package dnd.rules;

import java.util.Map;

/**
 * Encounter difficulty from the 5e Dungeon Master's Guide: add up the monsters' XP, multiply by a factor that
 * grows with the number of monsters, and compare against the party's XP thresholds.
 */
public final class EncounterMath {
    private EncounterMath() { }

    public enum Difficulty {
        TRIVIAL("Trivial"), EASY("Easy"), MEDIUM("Medium"), HARD("Hard"), DEADLY("Deadly");

        public final String label;

        Difficulty(String label) {
            this.label = label;
        }
    }

    /** XP thresholds per character, indexed by level 1-20: {easy, medium, hard, deadly}. */
    private static final int[][] THRESHOLDS = {
            {25, 50, 75, 100}, {50, 100, 150, 200}, {75, 150, 225, 400}, {125, 250, 375, 500},
            {250, 500, 750, 1100}, {300, 600, 900, 1400}, {350, 750, 1100, 1700}, {450, 900, 1400, 2100},
            {550, 1100, 1600, 2400}, {600, 1200, 1900, 2800}, {800, 1600, 2400, 3600}, {1000, 2000, 3000, 4500},
            {1100, 2200, 3400, 5100}, {1250, 2500, 3800, 5700}, {1400, 2800, 4300, 6400}, {1600, 3200, 4800, 7200},
            {2000, 3900, 5900, 8800}, {2100, 4200, 6300, 9500}, {2400, 4900, 7300, 10900}, {2800, 5700, 8500, 12700}};

    private static final double[] MULTIPLIERS = {0.5, 1, 1.5, 2, 2.5, 3, 4, 5};

    private static final Map<String, Integer> CR_XP = Map.ofEntries(
            Map.entry("0", 10), Map.entry("1/8", 25), Map.entry("1/4", 50), Map.entry("1/2", 100),
            Map.entry("1", 200), Map.entry("2", 450), Map.entry("3", 700), Map.entry("4", 1100), Map.entry("5", 1800),
            Map.entry("6", 2300), Map.entry("7", 2900), Map.entry("8", 3900), Map.entry("9", 5000),
            Map.entry("10", 5900), Map.entry("11", 7200), Map.entry("12", 8400), Map.entry("13", 10000),
            Map.entry("14", 11500), Map.entry("15", 13000), Map.entry("16", 15000), Map.entry("17", 18000),
            Map.entry("18", 20000), Map.entry("19", 22000), Map.entry("20", 25000), Map.entry("21", 33000),
            Map.entry("22", 41000), Map.entry("23", 50000), Map.entry("24", 62000), Map.entry("25", 75000),
            Map.entry("26", 90000), Map.entry("27", 105000), Map.entry("28", 120000), Map.entry("29", 135000),
            Map.entry("30", 155000));

    /** XP for a challenge rating such as "1/4" or "5"; 0 if it can't be read. */
    public static int xpForChallenge(String cr) {
        if (cr == null) return 0;
        String s = cr.trim();
        int sp = s.indexOf(' ');
        if (sp > 0) s = s.substring(0, sp); // "1/4 (50 XP)"
        if (s.equals("0.5")) s = "1/2";
        if (s.equals("0.25")) s = "1/4";
        if (s.equals("0.125")) s = "1/8";
        return CR_XP.getOrDefault(s, 0);
    }

    /** The XP for one creature: its stated XP, or the value for its challenge rating if that is not filled in. */
    public static int xpOf(int statedXp, String challenge) {
        return statedXp > 0 ? statedXp : xpForChallenge(challenge);
    }

    /**
     * The encounter multiplier for this many monsters. Parties smaller than 3 count the fight one step harder,
     * parties of 6 or more one step easier.
     */
    public static double multiplier(int monsters, int partySize) {
        if (monsters <= 0) return 0;
        int idx = monsters == 1 ? 1 : monsters == 2 ? 2 : monsters <= 6 ? 3 : monsters <= 10 ? 4 : monsters <= 14 ? 5 : 6;
        if (partySize < 3) idx++;
        else if (partySize >= 6) idx--;
        idx = Math.max(0, Math.min(MULTIPLIERS.length - 1, idx));
        return MULTIPLIERS[idx];
    }

    /** Total XP of the monsters times the multiplier for how many there are. */
    public static int adjustedXp(int totalXp, int monsters, int partySize) {
        return (int) Math.round(totalXp * multiplier(monsters, partySize));
    }

    /** The party's {easy, medium, hard, deadly} budget in XP. */
    public static int[] thresholds(int level, int partySize) {
        int[] t = THRESHOLDS[Math.max(1, Math.min(20, level)) - 1];
        int n = Math.max(1, partySize);
        return new int[]{t[0] * n, t[1] * n, t[2] * n, t[3] * n};
    }

    public static Difficulty difficulty(int adjustedXp, int level, int partySize) {
        int[] t = thresholds(level, partySize);
        if (adjustedXp >= t[3]) return Difficulty.DEADLY;
        if (adjustedXp >= t[2]) return Difficulty.HARD;
        if (adjustedXp >= t[1]) return Difficulty.MEDIUM;
        if (adjustedXp >= t[0]) return Difficulty.EASY;
        return Difficulty.TRIVIAL;
    }
}
