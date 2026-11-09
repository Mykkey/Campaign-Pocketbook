package dnd;

import dnd.battle.BattleEngine;
import dnd.model.Battle;
import dnd.model.BattleMap;
import dnd.model.Card;
import dnd.model.Combatant;
import dnd.model.Combatant.Side;
import dnd.model.MapToken;
import dnd.rules.CardTemplates;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BattleStateTest {

    @Test
    void aBattleInProgressSurvivesSaveAndLoad(@TempDir Path tmp) throws Exception {
        ProjectStore s = ProjectStore.create(tmp, "P");
        Card goblin = s.createCard(s.project.root, CardTemplates.create("goblin", null));
        Card hero = s.createCard(s.project.root, CardTemplates.create(CardTemplates.BLANK_PLAYER, "Aria"));
        BattleMap m = s.createBattleMap(s.project.root, "Ambush", BattleMap.GridType.SQUARE, 20, 12, 50);

        Battle b = new Battle();
        m.battle = b;
        List<Combatant> gobs = BattleEngine.addFromCard(b, goblin, 3, true, Side.ENEMY);
        Combatant aria = BattleEngine.addFromCard(b, hero, 1, false, Side.PLAYER).get(0);
        BattleEngine.setInitiative(b, aria, 18);
        BattleEngine.setInitiative(b, gobs.get(0), 12);
        MapToken tok = new MapToken();
        tok.label = "Ari";
        m.tokens.add(tok);
        aria.tokenId = tok.id;
        BattleEngine.damage(b, gobs.get(1), 4);
        BattleEngine.toggleCondition(aria, "Prone");
        aria.notes = "Rage active";
        BattleEngine.start(b);
        BattleEngine.next(b);
        s.markMapDirty(m);
        s.flush();

        BattleMap loaded = ProjectStore.open(tmp, "P").battleMap(m.id);
        assertNotNull(loaded.battle);
        Battle lb = loaded.battle;
        assertTrue(lb.active);
        assertEquals(1, lb.round);
        assertEquals(4, lb.combatants.size());
        assertEquals(b.turnId, lb.turnId, "whose turn it is is remembered");
        Combatant lAria = lb.find(aria.id);
        assertEquals("Aria", lAria.name);
        assertEquals(Side.PLAYER, lAria.side);
        assertEquals(18, lAria.initiative);
        assertEquals(tok.id, lAria.tokenId);
        assertEquals(List.of("Prone"), lAria.conditions);
        assertEquals("Rage active", lAria.notes);
        assertEquals(goblin.id, lAria.cardId == null ? goblin.id : lb.find(gobs.get(0).id).cardId);
        Combatant lg = lb.find(gobs.get(1).id);
        assertEquals(goblin.hpMax - 4, lg.hpCurrent);
        assertNotNull(lg.group, "group membership is saved");
        assertEquals(lb.find(gobs.get(0).id).group, lg.group);
        assertFalse(lb.log.isEmpty());

        // the loaded battle keeps working
        assertTrue(BattleEngine.isCurrent(lb, lb.find(gobs.get(0).id)));
        BattleEngine.next(lb);
        assertEquals(2, lb.round);
    }

    @Test
    void aMapWithoutABattleStaysWithoutOne(@TempDir Path tmp) throws Exception {
        ProjectStore s = ProjectStore.create(tmp, "P");
        BattleMap m = s.createBattleMap(s.project.root, "Quiet", BattleMap.GridType.HEX, 10, 8, 60);
        s.flush();
        assertNull(ProjectStore.open(tmp, "P").battleMap(m.id).battle);
    }
}
