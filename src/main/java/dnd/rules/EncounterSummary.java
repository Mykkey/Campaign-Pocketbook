package dnd.rules;

import dnd.model.Card;
import dnd.model.Combatant.Side;
import dnd.model.Encounter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** A short description of an encounter and how hard it is, for hover previews. Pure logic. */
public final class EncounterSummary {
    private EncounterSummary() { }

    /**
     * @param creatures   one line per entry, e.g. "4 x Goblin (enemy)"; deleted creatures are marked
     * @param enemies     how many enemies count towards the difficulty
     * @param difficulty  null when there are no enemies
     */
    public record Summary(List<String> creatures, int enemies, int totalXp, int adjustedXp,
                          EncounterMath.Difficulty difficulty, int partySize, int partyLevel) { }

    public static Summary of(Encounter e, Function<Encounter.Entry, Card> cardOf, int partySize, int partyLevel) {
        List<String> lines = new ArrayList<>();
        int enemies = 0, xp = 0;
        for (Encounter.Entry en : e.entries) {
            Card c = cardOf.apply(en);
            int count = Math.max(1, en.count);
            if (c == null) {
                lines.add(count + " x (deleted creature)");
                continue;
            }
            lines.add(count + " x " + c.name + " (" + en.side.name().toLowerCase() + ")");
            if (en.side == Side.ENEMY) {
                enemies += count;
                xp += count * EncounterMath.xpOf(c.xp, c.challenge);
            }
        }
        int adjusted = EncounterMath.adjustedXp(xp, enemies, partySize);
        EncounterMath.Difficulty d = enemies == 0 ? null : EncounterMath.difficulty(adjusted, partyLevel, partySize);
        return new Summary(lines, enemies, xp, adjusted, d, partySize, partyLevel);
    }
}