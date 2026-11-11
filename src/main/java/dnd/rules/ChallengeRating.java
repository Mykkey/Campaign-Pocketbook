package dnd.rules;

import dnd.model.Attack;
import dnd.model.Card;
import dnd.model.Feature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out a creature's challenge rating the way the 5th edition Dungeon Master's Guide suggests ("Creating a Monster
 * Quickly"): the hit points and armor class give a <em>defensive</em> CR, the damage each round and the attack bonus (or
 * save DC) give an <em>offensive</em> CR, and the two are averaged. It is a guide to compare with, not a replacement for
 * judgement: special abilities, spells and unusual features are not counted.
 */
public final class ChallengeRating {
    private ChallengeRating() { }

    /** One row of the DMG table: the CR, and what a creature of that rating typically has. */
    private record Row(int proficiency, int ac, int hpMax, int attackBonus, int damageMax, int saveDc) { }

    /** The challenge ratings in order: index 0 is CR 0, 1 is 1/8, 2 is 1/4, 3 is 1/2, 4 is CR 1 ... 33 is CR 30. */
    static final String[] LABELS = new String[34];
    private static final double[] VALUES = new double[34];

    static {
        LABELS[0] = "0";
        LABELS[1] = "1/8";
        LABELS[2] = "1/4";
        LABELS[3] = "1/2";
        VALUES[1] = 0.125;
        VALUES[2] = 0.25;
        VALUES[3] = 0.5;
        for (int cr = 1; cr <= 30; cr++) {
            LABELS[cr + 3] = Integer.toString(cr);
            VALUES[cr + 3] = cr;
        }
    }

    // the table: proficiency, armor class, highest hit points, attack bonus, highest damage per round, save DC
    private static final Row[] TABLE = {
            new Row(2, 13, 6, 3, 1, 13), new Row(2, 13, 35, 3, 3, 13), new Row(2, 13, 49, 3, 5, 13), new Row(2, 13, 70, 3, 8, 13),
            new Row(2, 13, 85, 3, 14, 13), new Row(2, 13, 100, 3, 20, 13), new Row(2, 13, 115, 4, 26, 13), new Row(2, 14, 130, 5, 32, 14),
            new Row(3, 15, 145, 6, 38, 15), new Row(3, 15, 160, 6, 44, 15), new Row(3, 15, 175, 6, 50, 15), new Row(3, 16, 190, 7, 56, 16),
            new Row(4, 16, 205, 7, 62, 16), new Row(4, 17, 220, 7, 68, 16), new Row(4, 17, 235, 8, 74, 17), new Row(4, 17, 250, 8, 80, 17),
            new Row(5, 18, 265, 8, 86, 18), new Row(5, 18, 280, 8, 92, 18), new Row(5, 18, 295, 8, 98, 18), new Row(5, 18, 310, 9, 104, 18),
            new Row(6, 19, 325, 10, 110, 19), new Row(6, 19, 340, 10, 116, 19), new Row(6, 19, 355, 10, 122, 19), new Row(6, 19, 400, 10, 140, 19),
            new Row(7, 19, 445, 11, 158, 20), new Row(7, 19, 490, 11, 176, 20), new Row(7, 19, 535, 11, 194, 20), new Row(7, 19, 580, 12, 212, 21),
            new Row(8, 19, 625, 12, 230, 21), new Row(8, 19, 670, 12, 248, 21), new Row(8, 19, 715, 13, 266, 22), new Row(8, 19, 760, 13, 284, 22),
            new Row(9, 19, 805, 13, 302, 22), new Row(9, 19, 850, 14, 320, 23)};

    /**
     * The result of working out a challenge rating.
     *
     * @param defensive the rating from hit points and armor class
     * @param offensive the rating from damage and attack bonus (or save DC)
     * @param suggested the two averaged, as written on a card ("1/4", "5")
     * @param xp        what that rating is worth
     * @param notes     how each part was worked out, one line each, for showing to the person
     */
    public record Result(String defensive, String offensive, String suggested, int xp, List<String> notes) { }

    /** The label for a place in the sequence of ratings (clamped to 0 ... 30). */
    static String label(int index) {
        return LABELS[Math.max(0, Math.min(LABELS.length - 1, index))];
    }

    /** The place in the sequence of ratings for a CR written as "1/4", "0.25" or "5"; -1 if it is not one. */
    public static int indexOf(String cr) {
        if (cr == null) return -1;
        String s = cr.trim();
        int sp = s.indexOf(' ');
        if (sp > 0) s = s.substring(0, sp);
        if (s.equals("0.125")) s = "1/8";
        if (s.equals("0.25")) s = "1/4";
        if (s.equals("0.5")) s = "1/2";
        for (int i = 0; i < LABELS.length; i++) if (LABELS[i].equals(s)) return i;
        return -1;
    }

    /** The first rating whose highest hit points reach this many. */
    static int rowForHp(double hp) {
        for (int i = 0; i < TABLE.length; i++) if (hp <= TABLE[i].hpMax()) return i;
        return TABLE.length - 1;
    }

    /** The first rating whose highest damage per round reaches this much. */
    static int rowForDamage(double dpr) {
        for (int i = 0; i < TABLE.length; i++) if (dpr <= TABLE[i].damageMax()) return i;
        return TABLE.length - 1;
    }

    /** One step up or down for every 2 points the creature is above or below the table (whole steps only, towards zero). */
    private static int steps(int diff) {
        return diff / 2;
    }

    private static int clamp(int index) {
        return Math.max(0, Math.min(TABLE.length - 1, index));
    }

    // ---------------------------------------------------------------- the card

    private static final Pattern DICE_IN_BRACKETS = Pattern.compile("\\((\\d+d\\d+(?:\\s*[+-]\\s*\\d+)?)\\)");
    private static final Pattern SAVE_DC = Pattern.compile("DC\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTACK_COUNT = Pattern.compile("\\b(one|two|three|four|five|six|\\d)\\b[^.]*?\\battacks?\\b", Pattern.CASE_INSENSITIVE);

    /** How many attacks a Multiattack action makes ("makes two attacks"); 2 if it does not say; 1 if there is no Multiattack. */
    static int attacksPerRound(Card c) {
        for (Feature f : c.actions) {
            if (f.name == null || !f.name.toLowerCase(Locale.ROOT).contains("multiattack")) continue;
            Matcher m = ATTACK_COUNT.matcher(f.text == null ? "" : f.text);
            if (m.find()) {
                return switch (m.group(1).toLowerCase(Locale.ROOT)) {
                    case "one" -> 1;
                    case "two" -> 2;
                    case "three" -> 3;
                    case "four" -> 4;
                    case "five" -> 5;
                    case "six" -> 6;
                    default -> Math.max(1, Math.min(9, Integer.parseInt(m.group(1))));
                };
            }
            return 2;
        }
        return 1;
    }

    /** Damage and DC of the best action that makes the target roll a saving throw (a breath weapon), or {0, 0} if none. */
    static double[] bestSaveAction(Card c) {
        double bestDamage = 0;
        int dc = 0;
        for (Feature f : c.actions) {
            String text = f.text == null ? "" : f.text;
            Matcher d = SAVE_DC.matcher(text);
            if (!d.find()) continue;
            int thisDc = Integer.parseInt(d.group(1));
            double damage = 0;
            Matcher dice = DICE_IN_BRACKETS.matcher(text);
            while (dice.find()) {
                Optional<Dice> parsed = Dice.parse(dice.group(1).replace(" ", ""));
                if (parsed.isPresent()) damage += parsed.get().average();
            }
            if (damage > bestDamage) {
                bestDamage = damage;
                dc = thisDc;
            }
        }
        return new double[]{bestDamage, dc};
    }

    /** Hit points count for more when the creature shrugs off ordinary weapons (resistant or immune to bludgeoning, piercing and slashing). */
    static double hitPointMultiplier(Card c, int baseRow) {
        String resist = c.resistances == null ? "" : c.resistances.toLowerCase(Locale.ROOT);
        String immune = c.immunities == null ? "" : c.immunities.toLowerCase(Locale.ROOT);
        boolean resists = physical(resist), immuneTo = physical(immune);
        if (!resists && !immuneTo) return 1;
        int band = baseRow <= 7 ? 0 : baseRow <= 14 ? 1 : baseRow <= 20 ? 2 : 3; // CR 0-4, 5-10, 11-16, 17 and up
        double[] resistance = {2, 1.5, 1.25, 1}, immunity = {2, 2, 1.5, 1.25};
        return immuneTo ? immunity[band] : resistance[band];
    }

    private static boolean physical(String text) {
        return text.contains("nonmagical") || text.contains("non-magical")
                || (text.contains("bludgeoning") && text.contains("piercing") && text.contains("slashing"));
    }

    /** Works out the rating of a card from its hit points, armor class, attacks and breath-weapon style actions. */
    public static Result of(Card c) {
        List<String> notes = new ArrayList<>();

        // ---- defensive
        int hp = Math.max(0, c.hpMax);
        int baseRow = rowForHp(hp);
        double mult = hitPointMultiplier(c, baseRow);
        double effective = hp * mult;
        int hpRow = rowForHp(effective);
        int acDiff = c.ac - TABLE[hpRow].ac();
        int defensive = clamp(hpRow + steps(acDiff));
        notes.add("Defence: " + hp + " hit points" + (mult > 1 ? " (counted as " + Math.round(effective) + ": resistant or immune to ordinary weapons)" : "")
                + " is CR " + label(hpRow) + "; armor class " + c.ac + " is " + describeDifference(acDiff, "than the " + TABLE[hpRow].ac() + " expected")
                + " -> defensive CR " + label(defensive) + ".");

        // ---- offensive
        List<Attack> attacks = new ArrayList<>();
        for (Attack a : c.attacks) if (a.averageDamage() >= 0) attacks.add(a);
        attacks.sort(Comparator.comparingInt(Attack::averageDamage).reversed());
        int n = attacksPerRound(c);
        double attackDamage = 0;
        int attackBonus = Integer.MIN_VALUE;
        for (int i = 0; i < n && !attacks.isEmpty(); i++) {
            Attack a = attacks.get(Math.min(i, attacks.size() - 1)); // fewer listed than made: the best one repeats
            attackDamage += a.averageDamage();
            attackBonus = Math.max(attackBonus, a.toHit);
        }
        double[] save = bestSaveAction(c);
        double dpr;
        int offensive;
        if (attackDamage <= 0 && save[0] <= 0) {
            dpr = 0;
            offensive = 0;
            notes.add("Offence: no attacks with damage dice yet -> offensive CR 0.");
        } else if (save[0] > attackDamage) {
            dpr = save[0];
            int row = rowForDamage(dpr);
            int dcDiff = (int) save[1] - TABLE[row].saveDc();
            offensive = clamp(row + steps(dcDiff));
            notes.add("Offence: about " + Math.round(dpr) + " damage a round from a save-based action is CR " + label(row) + "; save DC " + (int) save[1]
                    + " is " + describeDifference(dcDiff, "than the " + TABLE[row].saveDc() + " expected") + " -> offensive CR " + label(offensive) + ".");
        } else {
            dpr = attackDamage;
            int row = rowForDamage(dpr);
            int hitDiff = attackBonus - TABLE[row].attackBonus();
            offensive = clamp(row + steps(hitDiff));
            notes.add("Offence: about " + Math.round(dpr) + " damage a round" + (n > 1 ? " (" + n + " attacks)" : "") + " is CR " + label(row)
                    + "; attack bonus +" + attackBonus + " is " + describeDifference(hitDiff, "than the +" + TABLE[row].attackBonus() + " expected")
                    + " -> offensive CR " + label(offensive) + ".");
        }

        // ---- together
        double average = (VALUES[defensive] + VALUES[offensive]) / 2;
        int best = 0;
        for (int i = 0; i < TABLE.length; i++) {
            if (Math.abs(VALUES[i] - average) <= Math.abs(VALUES[best] - average)) best = i; // a tie goes to the higher rating
        }
        String suggested = label(best);
        notes.add("The average of the two is CR " + suggested + ".");
        notes.add("Special abilities, spells, legendary actions and unusual features are not counted: adjust by hand if they matter.");
        return new Result(label(defensive), label(offensive), suggested, EncounterMath.xpForChallenge(suggested), notes);
    }

    private static String describeDifference(int diff, String than) {
        if (diff == 0) return "as expected";
        return Math.abs(diff) + (diff > 0 ? " higher " : " lower ") + than;
    }
}
