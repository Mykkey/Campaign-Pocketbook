package dnd.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One creature in a battle: an instance of a card with its own HP, initiative and token. */
public class Combatant {
    public enum Side { PLAYER, ALLY, ENEMY }

    public String id = UUID.randomUUID().toString();
    /** The card this was made from (stat block, attacks). May be null if the card was deleted. */
    public String cardId;
    public String name = "Creature";
    public Side side = Side.ENEMY;
    public int initiative;
    /**
     * True once an initiative has been entered or rolled. A battle can't start until everyone has one. (Battles
     * saved before this existed are treated as set while they are running.)
     */
    public boolean initiativeSet;
    /** Tie-breaker bonus copied from the card (used when two creatures roll the same initiative). */
    public int initiativeBonus;
    /** Manual order for ties: lower acts first. */
    public int order;
    public int hpMax = 1;
    public int hpCurrent = 1;
    public int tempHp;
    public int ac = 10;
    public List<String> conditions = new ArrayList<>();
    public boolean defeated;
    /** The map token for this creature, or null. */
    public String tokenId;
    /** Combatants with the same non-null group id share an initiative and take their turn together. */
    public String group;
    public String notes = "";

    public Combatant() { }
}
