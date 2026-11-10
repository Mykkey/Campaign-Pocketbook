package dnd.battle;

import dnd.model.Battle;
import dnd.model.Combatant;
import dnd.model.Combatant.Side;
import dnd.model.TimedEffect;

import java.util.ArrayList;
import java.util.List;

/**
 * The extra things a DM keeps track of in a fight: death saving throws, concentration, effects that last a number of
 * turns, reactions and legendary actions. No UI, so it is unit tested; {@link BattleEngine} calls into it when
 * damage, healing and turns happen.
 */
public final class CombatStatus {
    private CombatStatus() { }

    public static final String CONCENTRATING = "Concentrating";
    public static final String UNCONSCIOUS = "Unconscious";

    // ================================================================ death saves

    /** Whether a creature makes death saving throws at 0 hit points: player characters do, monsters only if the DM turned it on. */
    public static boolean makesDeathSaves(Combatant c) {
        return c.deathSaves != null ? c.deathSaves : c.side == Side.PLAYER;
    }

    /** At 0 hit points, not stable and not dead: rolling death saves each turn. */
    public static boolean isDying(Combatant c) {
        return makesDeathSaves(c) && c.hpCurrent == 0 && !c.defeated;
    }

    public static boolean isStable(Combatant c) {
        return makesDeathSaves(c) && c.hpCurrent == 0 && c.defeated && c.stable && c.deathFailures < 3;
    }

    public static boolean isDead(Combatant c) {
        return makesDeathSaves(c) && c.hpCurrent == 0 && c.defeated && c.deathFailures >= 3;
    }

    /** Forgets all death save progress (the creature was healed). */
    public static void clearDeathState(Combatant c) {
        c.deathSuccesses = 0;
        c.deathFailures = 0;
        c.stable = false;
    }

    /** A creature with death saves has just been reduced to 0 hit points: it falls unconscious and starts dying. */
    static void goDown(Battle b, Combatant c) {
        clearDeathState(c);
        c.defeated = false;
        dropConcentration(b, c);
        addCondition(c, UNCONSCIOUS);
    }

    /** A creature at 0 hit points takes damage. Each hit is one death save failure; a stable creature starts dying again. */
    static String hitWhileDown(Battle b, Combatant c, int failures) {
        if (c.defeated && !c.stable) return ""; // already dead: nothing more happens
        if (c.stable) {
            c.stable = false;
            c.defeated = false;
            c.deathSuccesses = 0;
        }
        return addFailures(b, c, failures);
    }

    /** Adds death save failures; the third one kills. */
    public static String addFailures(Battle b, Combatant c, int n) {
        if (n <= 0 || !isDying(c)) return "";
        c.deathFailures = Math.min(3, c.deathFailures + n);
        if (c.deathFailures >= 3) return die(b, c);
        String msg = c.name + " fails a death save (" + c.deathFailures + " of 3 failures)";
        BattleEngine.log(b, msg + ".");
        return msg;
    }

    /** Adds death save successes; the third one makes the creature stable. */
    public static String addSuccesses(Battle b, Combatant c, int n) {
        if (n <= 0 || !isDying(c)) return "";
        c.deathSuccesses = Math.min(3, c.deathSuccesses + n);
        if (c.deathSuccesses >= 3) return stabilize(b, c);
        String msg = c.name + " succeeds on a death save (" + c.deathSuccesses + " of 3 successes)";
        BattleEngine.log(b, msg + ".");
        return msg;
    }

    /** The creature stops dying (it stays at 0 hit points, unconscious, and takes no turns until it is healed). */
    public static String stabilize(Battle b, Combatant c) {
        c.stable = true;
        c.deathSuccesses = 3;
        c.defeated = true;
        String msg = c.name + " is stable";
        BattleEngine.log(b, msg + ".");
        return msg;
    }

    /** The creature dies (three failures, or killed outright by massive damage). */
    static String die(Battle b, Combatant c) {
        c.deathFailures = 3;
        c.stable = false;
        c.defeated = true;
        c.hpCurrent = 0;
        dropConcentration(b, c);
        String msg = c.name + " dies";
        BattleEngine.log(b, msg + ".");
        return msg;
    }

    /** Applies the result of a death saving throw (the d20, 1 to 20) and returns what happened. Does nothing if the creature is not dying. */
    public static String deathSave(Battle b, Combatant c, int roll) {
        if (!isDying(c)) return "";
        BattleEngine.log(b, c.name + " rolls " + roll + " on a death save.");
        if (roll >= 20) {
            clearDeathState(c);
            c.hpCurrent = 1;
            c.defeated = false;
            removeCondition(c, UNCONSCIOUS);
            String msg = c.name + " rolls a natural 20 on a death save and regains 1 hit point";
            BattleEngine.log(b, msg + ".");
            return msg;
        }
        if (roll <= 1) return addFailures(b, c, 2);
        return roll >= 10 ? addSuccesses(b, c, 1) : addFailures(b, c, 1);
    }

    // ================================================================ concentration

    /** The Constitution save DC to keep concentrating after taking damage: 10, or half the damage if that is more. */
    public static int concentrationDc(int damage) {
        return Math.max(10, damage / 2);
    }

    public static boolean isConcentrating(Combatant c) {
        return hasCondition(c, CONCENTRATING);
    }

    /** Starts concentrating (on a named spell, or on something unnamed if {@code spell} is blank). */
    public static void setConcentration(Combatant c, String spell) {
        addCondition(c, CONCENTRATING);
        c.concentratingOn = spell == null ? "" : spell.trim();
    }

    /** Ends concentration. */
    public static void dropConcentration(Battle b, Combatant c) {
        if (!isConcentrating(c)) return;
        removeCondition(c, CONCENTRATING);
        String what = c.concentratingOn == null || c.concentratingOn.isBlank() ? "" : " on " + c.concentratingOn;
        c.concentratingOn = null;
        BattleEngine.log(b, c.name + " stops concentrating" + what + ".");
    }

    // ================================================================ effects, reactions, legendary actions

    public static void addEffect(Combatant c, String name, int turns) {
        if (name == null || name.isBlank()) return;
        c.effects.add(new TimedEffect(name.trim(), Math.max(1, turns)));
    }

    /** The creature's turn ends: every effect on it has one turn fewer, and those that reach 0 end. Returns the names of the ones that ended. */
    static List<String> tickEffects(Battle b, Combatant c) {
        List<String> ended = new ArrayList<>();
        for (java.util.Iterator<TimedEffect> it = c.effects.iterator(); it.hasNext(); ) {
            TimedEffect e = it.next();
            e.turns--;
            if (e.turns <= 0) {
                it.remove();
                ended.add(e.name);
                BattleEngine.log(b, e.name + " on " + c.name + " ends.");
            }
        }
        return ended;
    }

    /** The creature's turn begins: its reaction and its legendary actions are available again. */
    static void startTurn(Combatant c) {
        c.reactionUsed = false;
        c.legendaryUsed = 0;
    }

    // ================================================================ helpers

    public static boolean hasCondition(Combatant c, String condition) {
        for (String s : c.conditions) if (s.equalsIgnoreCase(condition)) return true;
        return false;
    }

    static void addCondition(Combatant c, String condition) {
        if (!hasCondition(c, condition)) c.conditions.add(condition);
    }

    static void removeCondition(Combatant c, String condition) {
        c.conditions.removeIf(s -> s.equalsIgnoreCase(condition));
    }
}
