package dnd.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A planned fight: which creature cards, how many of each, which side they are on and whether they act as a
 * group. Stored as encounters/&lt;id&gt;.encounter (JSON). Loading one in the battle setup adds all its creatures.
 */
public class Encounter {
    public String id = UUID.randomUUID().toString();
    public String name = "New Encounter";
    public String notes = "";
    public List<Entry> entries = new ArrayList<>();

    /** One line of the encounter: {@code count} creatures made from a card. */
    public static class Entry {
        /** A card in the project, or null if this is a common enemy. */
        public String cardId;
        /** A creature in the shared Common Enemies library, or null if this is a project card. */
        public String libraryId;
        public int count = 1;
        public Combatant.Side side = Combatant.Side.ENEMY;
        public boolean group;

        public Entry() { }

        public Entry(String cardId, int count, Combatant.Side side, boolean group) {
            this.cardId = cardId;
            this.count = count;
            this.side = side;
            this.group = group;
        }

        /** An entry for a creature in the Common Enemies library. */
        public static Entry common(String libraryId, int count, Combatant.Side side, boolean group) {
            Entry e = new Entry(null, count, side, group);
            e.libraryId = libraryId;
            return e;
        }

        public Entry copy() {
            Entry e = new Entry(cardId, count, side, group);
            e.libraryId = libraryId;
            return e;
        }
    }

    public Encounter() { }

    public Encounter copy() {
        Encounter e = new Encounter();
        e.name = name;
        e.notes = notes;
        for (Entry x : entries) e.entries.add(x.copy());
        return e;
    }
}
