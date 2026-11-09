package dnd.model;

import java.util.ArrayList;
import java.util.List;

/** The combat state saved with a battlemap: who is fighting, the round and whose turn it is. */
public class Battle {
    public boolean active;
    public int round = 1;
    /** Id of the combatant (or the first member of the group) whose turn it is; null before the first turn. */
    public String turnId;
    public List<Combatant> combatants = new ArrayList<>();
    /** Short history of HP changes and turns, newest last. */
    public List<String> log = new ArrayList<>();

    public Battle() { }

    public Combatant find(String id) {
        for (Combatant c : combatants) if (c.id.equals(id)) return c;
        return null;
    }
}
