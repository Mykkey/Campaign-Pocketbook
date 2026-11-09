package dnd.battle;

import dnd.model.Battle;
import dnd.model.Card;
import dnd.model.Combatant;
import dnd.model.Combatant.Side;
import dnd.rules.Dice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The rules of the initiative tracker, with no UI: turn order, groups of creatures that act together, rounds,
 * and hit points. Everything works on a {@link Battle}, so it can be saved with the map and unit tested.
 *
 * <p>Combat is tracked manually: the DM rolls dice and types the results; this class just keeps the state.
 */
public final class BattleEngine {
    private BattleEngine() { }

    /** One entry in the turn order: a single creature, or a group of creatures that act together. */
    public static final class Slot {
        public final String key;
        public final List<Combatant> members;

        Slot(String key, List<Combatant> members) {
            this.key = key;
            this.members = members;
        }

        public boolean isGroup() { return members.size() > 1 || members.get(0).group != null; }

        public int initiative() { return members.get(0).initiative; }

        /** True when every member is defeated (the slot is skipped). */
        public boolean defeated() {
            for (Combatant c : members) if (!c.defeated) return false;
            return true;
        }

        public int aliveCount() {
            int n = 0;
            for (Combatant c : members) if (!c.defeated) n++;
            return n;
        }

        public String name() {
            if (members.size() == 1) return members.get(0).name;
            String base = members.get(0).name.replaceAll("\\s+\\d+$", "");
            return base + " x" + members.size();
        }
    }

    // ================================================================ order

    /** The key shared by every member of a group, or the creature's own id. */
    public static String slotKey(Combatant c) {
        return c.group != null ? "g:" + c.group : c.id;
    }

    /** All turn slots, highest initiative first. Ties go to the higher initiative bonus, then manual order. */
    public static List<Slot> slots(Battle b) {
        Map<String, List<Combatant>> by = new LinkedHashMap<>();
        for (Combatant c : b.combatants) by.computeIfAbsent(slotKey(c), k -> new ArrayList<>()).add(c);
        List<Slot> out = new ArrayList<>();
        for (Map.Entry<String, List<Combatant>> e : by.entrySet()) out.add(new Slot(e.getKey(), e.getValue()));
        out.sort(Comparator
                .comparingInt((Slot s) -> -s.initiative())
                .thenComparingInt(s -> -s.members.stream().mapToInt(c -> c.initiativeBonus).max().orElse(0))
                .thenComparingInt(s -> s.members.stream().mapToInt(c -> c.order).min().orElse(0)));
        return out;
    }

    public static Slot currentSlot(Battle b) {
        if (b.turnId == null) return null;
        for (Slot s : slots(b)) if (s.key.equals(b.turnId)) return s;
        return null;
    }

    public static boolean isCurrent(Battle b, Combatant c) {
        return b.active && b.turnId != null && b.turnId.equals(slotKey(c));
    }

    // ================================================================ running the fight

    // ================================================================ setup: initiative is entered once, before starting

    /** Whether this creature has an initiative (entered, rolled, or from a battle that was already running). */
    public static boolean hasInitiative(Battle b, Combatant c) {
        return c.initiativeSet || b.active;
    }

    /** The turn slots that don't have an initiative yet, in the order they were added. */
    public static List<Slot> missingInitiative(Battle b) {
        List<Slot> out = new ArrayList<>();
        for (Slot s : slots(b)) if (!hasInitiative(b, s.members.get(0))) out.add(s);
        out.sort(Comparator.comparingInt(s -> s.members.stream().mapToInt(c -> c.order).min().orElse(0)));
        return out;
    }

    public static boolean canStart(Battle b) {
        if (b.combatants.isEmpty()) return false;
        if (!missingInitiative(b).isEmpty()) return false;
        for (Slot s : slots(b)) if (!s.defeated()) return true;
        return false;
    }

    /**
     * Rolls d20 + bonus for every creature (one roll per group) that has no initiative yet. Creatures that
     * already have one are left alone, so this can never re-roll anyone. Players are skipped unless
     * {@code includePlayers}, because they roll their own. Returns how many rolls were made.
     */
    public static int rollMissing(Battle b, boolean includePlayers, Random random) {
        Dice d20 = Dice.parse("1d20").orElseThrow();
        int rolls = 0;
        for (Slot s : missingInitiative(b)) {
            Combatant first = s.members.get(0);
            if (!includePlayers && s.members.stream().allMatch(c -> c.side == Side.PLAYER)) continue;
            int bonus = s.members.stream().mapToInt(c -> c.initiativeBonus).max().orElse(0);
            setInitiative(b, first, d20.roll(random) + bonus);
            rolls++;
        }
        return rolls;
    }

    /** Rolls one creature or group (even if it already has a value; used by a row's own dice button). */
    public static int rollOne(Battle b, Combatant c, Random random) {
        int v = Dice.parse("1d20").orElseThrow().roll(random) + c.initiativeBonus;
        setInitiative(b, c, v);
        return v;
    }

    /**
     * Adds creatures to a fight that is already running. They need an initiative right away (one value, shared
     * by a group). The current turn is left exactly where it is.
     */
    public static List<Combatant> addMidBattle(Battle b, Card card, int count, boolean group, Side side, int initiative) {
        List<Combatant> added = addFromCard(b, card, count, group, side);
        if (!added.isEmpty()) setInitiative(b, added.get(0), initiative);
        for (Combatant c : added) {
            c.initiative = initiative;
            c.initiativeSet = true;
        }
        return added;
    }

    /**
     * Starts the fight at round 1 with the first creature that isn't defeated. Does nothing (and returns false)
     * unless everyone has an initiative.
     */
    public static boolean start(Battle b) {
        if (!canStart(b)) return false;
        b.active = true;
        b.round = 1;
        b.turnId = null;
        for (Slot s : slots(b)) {
            if (!s.defeated()) {
                b.turnId = s.key;
                break;
            }
        }
        log(b, "Battle begins. Round 1" + (b.turnId == null ? "" : ": " + currentSlot(b).name() + " goes first."));
        return true;
    }

    public static void end(Battle b) {
        b.active = false;
        b.turnId = null;
        log(b, "Battle ends after round " + b.round + ".");
    }

    /** Moves to the next creature (or group) that isn't defeated; passing the last one starts a new round. */
    public static boolean next(Battle b) {
        return step(b, +1);
    }

    public static boolean previous(Battle b) {
        return step(b, -1);
    }

    private static boolean step(Battle b, int dir) {
        List<Slot> slots = slots(b);
        if (slots.isEmpty() || slots.stream().allMatch(Slot::defeated)) return false;
        int cur = -1;
        for (int i = 0; i < slots.size(); i++) if (slots.get(i).key.equals(b.turnId)) cur = i;
        int n = slots.size();
        int i = cur;
        int guard = 0;
        do {
            i += dir;
            if (i >= n) {
                i = 0;
                b.round++;
                log(b, "Round " + b.round);
            } else if (i < 0) {
                i = n - 1;
                if (b.round > 1) b.round--;
            }
            guard++;
        } while (slots.get(i).defeated() && guard <= n + 1);
        if (slots.get(i).defeated()) return false;
        b.turnId = slots.get(i).key;
        return true;
    }

    // ================================================================ adding / removing

    /**
     * Adds {@code count} creatures made from a card. Names are numbered ("Goblin 1", "Goblin 2") when there
     * are several of the same card. With {@code group}, they share one initiative and act together.
     */
    public static List<Combatant> addFromCard(Battle b, Card card, int count, boolean group, Side side) {
        int existing = 0;
        for (Combatant c : b.combatants) if (card.id != null && card.id.equals(c.cardId)) existing++;
        boolean numbered = count > 1 || existing > 0;
        String groupId = group && count > 1 ? UUID.randomUUID().toString() : null;
        List<Combatant> added = new ArrayList<>();
        int baseOrder = b.combatants.size();
        for (int i = 0; i < count; i++) {
            Combatant c = new Combatant();
            c.cardId = card.id;
            c.name = numbered ? card.name + " " + (existing + i + 1) : card.name;
            c.side = side;
            c.hpMax = Math.max(1, card.hpMax);
            c.hpCurrent = c.hpMax;
            c.ac = card.ac;
            c.initiativeBonus = card.initiative();
            c.group = groupId;
            c.order = baseOrder + i;
            added.add(c);
            b.combatants.add(c);
        }
        log(b, "Added " + (count > 1 ? count + " x " : "") + card.name + ".");
        return added;
    }

    public static void remove(Battle b, Combatant c) {
        b.combatants.remove(c);
        if (b.turnId != null && b.turnId.equals(slotKey(c)) && currentSlot(b) == null) {
            // the slot disappeared: hand the turn to whoever is next
            b.turnId = null;
            for (Slot s : slots(b)) {
                if (!s.defeated()) {
                    b.turnId = s.key;
                    break;
                }
            }
        }
    }

    // ================================================================ initiative

    /** Sets initiative; every member of a group gets the same value. */
    public static void setInitiative(Battle b, Combatant c, int value) {
        if (c.group == null) {
            c.initiative = value;
            c.initiativeSet = true;
            return;
        }
        for (Combatant m : b.combatants) {
            if (c.group.equals(m.group)) {
                m.initiative = value;
                m.initiativeSet = true;
            }
        }
    }

    // ================================================================ groups and order

    /** Makes these creatures act together: one initiative (the first one's) and one turn. */
    public static void group(Battle b, List<Combatant> members) {
        if (members.size() < 2) return;
        String id = UUID.randomUUID().toString();
        int init = members.get(0).initiative;
        boolean set = members.get(0).initiativeSet;
        for (Combatant c : members) {
            c.group = id;
            c.initiative = init;
            c.initiativeSet = set;
        }
    }

    public static void ungroup(Battle b, Combatant any) {
        if (any.group == null) return;
        String g = any.group;
        for (Combatant c : b.combatants) if (g.equals(c.group)) c.group = null;
    }

    /**
     * Swaps a slot with its neighbour in the turn order, but only when they share the same initiative (that is
     * the only case where the order is up to the DM). Returns true if something changed.
     */
    public static boolean moveSlot(Battle b, String slotKey, int delta) {
        List<Slot> slots = slots(b);
        int i = -1;
        for (int k = 0; k < slots.size(); k++) if (slots.get(k).key.equals(slotKey)) i = k;
        int j = i + delta;
        if (i < 0 || j < 0 || j >= slots.size()) return false;
        Slot a = slots.get(i), c = slots.get(j);
        if (a.initiative() != c.initiative()) return false;
        // give both an explicit order and swap, ignoring the initiative bonus tie-break
        int bonusA = a.members.stream().mapToInt(m -> m.initiativeBonus).max().orElse(0);
        int bonusC = c.members.stream().mapToInt(m -> m.initiativeBonus).max().orElse(0);
        if (bonusA != bonusC) {
            // the bonus decides before the order does, so equalise it to make the manual order stick
            int shared = Math.max(bonusA, bonusC);
            for (Combatant m : a.members) m.initiativeBonus = shared;
            for (Combatant m : c.members) m.initiativeBonus = shared;
        }
        int oa = a.members.stream().mapToInt(m -> m.order).min().orElse(0);
        int oc = c.members.stream().mapToInt(m -> m.order).min().orElse(0);
        if (oa == oc) oc += delta > 0 ? 1 : -1;
        for (Combatant m : a.members) m.order = oc;
        for (Combatant m : c.members) m.order = oa;
        return true;
    }

    // ================================================================ hit points

    /**
     * Applies damage: temporary hit points soak it up first. A creature reduced to 0 is marked defeated.
     * Returns a short description for the log.
     */
    public static String damage(Battle b, Combatant c, int amount) {
        if (amount <= 0) return "";
        int rest = amount;
        if (c.tempHp > 0) {
            int soak = Math.min(c.tempHp, rest);
            c.tempHp -= soak;
            rest -= soak;
        }
        c.hpCurrent = Math.max(0, c.hpCurrent - rest);
        String msg = c.name + " takes " + amount + " damage (" + c.hpCurrent + "/" + c.hpMax + ")";
        if (c.hpCurrent == 0 && !c.defeated) {
            c.defeated = true;
            msg += " and is down";
        }
        log(b, msg + ".");
        return msg;
    }

    /** Heals (never above max). A defeated creature that gets HP back is up again. */
    public static String heal(Battle b, Combatant c, int amount) {
        if (amount <= 0) return "";
        c.hpCurrent = Math.min(c.hpMax, c.hpCurrent + amount);
        String msg = c.name + " heals " + amount + " (" + c.hpCurrent + "/" + c.hpMax + ")";
        if (c.defeated && c.hpCurrent > 0) {
            c.defeated = false;
            msg += " and is back up";
        }
        log(b, msg + ".");
        return msg;
    }

    public static void setTempHp(Combatant c, int value) {
        c.tempHp = Math.max(0, value);
    }

    /**
     * Applies what the DM typed in an HP box: {@code -7} damage, {@code +5} heal, {@code 12} set current HP.
     * Returns false if the text wasn't a number.
     */
    public static boolean applyHpInput(Battle b, Combatant c, String input) {
        if (input == null) return false;
        String t = input.trim();
        if (t.isEmpty()) return false;
        t = t.replaceAll("([+-])\\s+(\\d)", "$1$2"); // "- 5" means "-5"
        // several hits at once, e.g. "-5 -7 -3" or "-5, -7, +2": every number is applied in turn
        String[] parts = t.split("[\\s,;]+");
        if (parts.length > 1) {
            int[] values = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                try {
                    int v = Integer.parseInt(parts[i].startsWith("+") ? parts[i].substring(1) : parts[i]);
                    // an unsigned number among several is a hit; "+n" heals; "-n" hurts
                    values[i] = parts[i].startsWith("+") ? v : -Math.abs(v);
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            for (int v : values) {
                if (v < 0) damage(b, c, -v);
                else if (v > 0) heal(b, c, v);
            }
            return true;
        }
        try {
            if (t.startsWith("-")) {
                damage(b, c, Integer.parseInt(t.substring(1).trim()));
            } else if (t.startsWith("+")) {
                heal(b, c, Integer.parseInt(t.substring(1).trim()));
            } else {
                int v = Math.max(0, Math.min(c.hpMax, Integer.parseInt(t)));
                int diff = v - c.hpCurrent;
                if (diff < 0) damageIgnoringTemp(b, c, -diff);
                else if (diff > 0) heal(b, c, diff);
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static void damageIgnoringTemp(Battle b, Combatant c, int amount) {
        int temp = c.tempHp;
        c.tempHp = 0;
        damage(b, c, amount);
        c.tempHp = temp;
    }

    /**
     * Re-links creatures to their map tokens. Undo/redo on the map restores old token lists but not which token a
     * creature points at, so a creature can end up pointing at a token that no longer exists (and its ring, turn
     * highlight and clicks stop working). Each such creature is given an unclaimed token for the same card.
     *
     * @return how many creatures were re-linked
     */
    public static int relinkTokens(Battle b, dnd.model.BattleMap map) {
        if (b == null) return 0;
        java.util.Set<String> claimed = new java.util.HashSet<>();
        for (Combatant c : b.combatants) if (c.tokenId != null && map.findToken(c.tokenId) != null) claimed.add(c.tokenId);
        int fixed = 0;
        for (Combatant c : b.combatants) {
            if (c.tokenId != null && map.findToken(c.tokenId) != null) continue;
            if (c.cardId == null) continue;
            for (dnd.model.MapToken t : map.tokens) {
                if (claimed.contains(t.id) || !c.cardId.equals(t.cardId)) continue;
                c.tokenId = t.id;
                claimed.add(t.id);
                fixed++;
                break;
            }
        }
        return fixed;
    }

    public static boolean toggleCondition(Combatant c, String condition) {
        if (condition == null || condition.isBlank()) return false;
        String key = condition.trim();
        for (int i = 0; i < c.conditions.size(); i++) {
            if (c.conditions.get(i).equalsIgnoreCase(key)) {
                c.conditions.remove(i);
                return false;
            }
        }
        c.conditions.add(key);
        return true;
    }

    public static void log(Battle b, String line) {
        if (line == null || line.isBlank()) return;
        b.log.add(line);
        while (b.log.size() > 200) b.log.remove(0);
    }
}
