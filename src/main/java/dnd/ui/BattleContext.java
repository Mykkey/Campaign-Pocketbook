package dnd.ui;

import dnd.model.Battle;
import dnd.model.BattleMap;
import dnd.model.Combatant;
import dnd.storage.ProjectStore;

/** What the pieces of the battle screen (strip, cards, setup list) need from the screen that owns them. */
interface BattleContext {
    BattleMap map();

    ProjectStore store();

    /** The battle state; created on first use. */
    Battle battle();

    /** Something about the battle changed: save the map, redraw it and refresh every part of the screen. */
    void changed();

    /** Show this creature (or group) in the second, "viewing" card. Choosing the same one again closes it. */
    void inspect(String slotKey);

    void centerOn(String tokenId);

    /** Puts a (new) token on the map for this creature, e.g. after its token was deleted. */
    void placeToken(Combatant c);

    void openCard(String cardId);

    void remove(Combatant c);
}
