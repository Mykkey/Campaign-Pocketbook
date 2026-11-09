package dnd.npc;

import dnd.model.Card;
import dnd.names.NameData.Race;
import dnd.rules.CardTemplates;

/** Turns a rolled NPC into a card the DM can keep, edit and put into a battle. */
public final class NpcCards {
    private NpcCards() { }

    /** A new NPC card: stats from the profession's template, the whole description in its notes. */
    public static Card toCard(Npc npc) {
        String template = npc.profession == null ? "commoner" : npc.profession.template();
        Card c = CardTemplates.create(template, npc.name);
        c.kind = Card.Kind.NPC;
        c.name = npc.name;
        c.creatureType = "humanoid (" + npc.race.display.toLowerCase() + ")";
        c.size = npc.race == Race.HALFLING || npc.race == Race.GNOME || npc.race == Race.GOBLIN || npc.race == Race.KOBOLD ? "Small" : "Medium";
        c.alignment = "";
        c.notes = npc.toText();
        return c;
    }
}