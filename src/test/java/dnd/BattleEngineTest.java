package dnd;

import dnd.battle.BattleEngine;
import dnd.battle.BattleEngine.Slot;
import dnd.model.Battle;
import dnd.model.Card;
import dnd.model.Combatant;
import dnd.model.Combatant.Side;
import dnd.rules.CardTemplates;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BattleEngineTest {

    private static Card card(String template) {
        return CardTemplates.create(template, null);
    }

    private static Combatant add(Battle b, String name, int initiative, Side side) {
        Combatant c = new Combatant();
        c.name = name;
        c.side = side;
        c.initiative = initiative;
        c.hpMax = c.hpCurrent = 10;
        c.order = b.combatants.size();
        b.combatants.add(c);
        return c;
    }

    private static List<String> order(Battle b) {
        List<String> names = new ArrayList<>();
        for (Slot s : BattleEngine.slots(b)) names.add(s.name());
        return names;
    }

    @Test
    void turnOrderIsHighestInitiativeFirst() {
        Battle b = new Battle();
        add(b, "Fighter", 12, Side.PLAYER);
        add(b, "Goblin", 17, Side.ENEMY);
        add(b, "Wizard", 8, Side.PLAYER);
        assertEquals(List.of("Goblin", "Fighter", "Wizard"), order(b));
    }

    @Test
    void ties_goToTheHigherBonusThenManualOrder() {
        Battle b = new Battle();
        Combatant slow = add(b, "Slow", 14, Side.ENEMY);
        Combatant quick = add(b, "Quick", 14, Side.ENEMY);
        slow.initiativeBonus = 0;
        quick.initiativeBonus = 3;
        assertEquals(List.of("Quick", "Slow"), order(b));

        quick.initiativeBonus = 0;           // same bonus: the order they were added in
        assertEquals(List.of("Slow", "Quick"), order(b));
        assertTrue(BattleEngine.moveSlot(b, quick.id, -1), "tied creatures can be swapped by hand");
        assertEquals(List.of("Quick", "Slow"), order(b));
        assertTrue(BattleEngine.moveSlot(b, quick.id, +1));
        assertEquals(List.of("Slow", "Quick"), order(b));
    }

    @Test
    void creaturesWithDifferentInitiativeCannotBeSwappedByHand() {
        Battle b = new Battle();
        Combatant a = add(b, "A", 15, Side.ENEMY);
        add(b, "B", 10, Side.ENEMY);
        assertFalse(BattleEngine.moveSlot(b, a.id, +1));
        assertEquals(List.of("A", "B"), order(b));
    }

    @Test
    void startBeginsAtRoundOneWithTheFirstCreature() {
        Battle b = new Battle();
        add(b, "Fighter", 12, Side.PLAYER);
        Combatant gob = add(b, "Goblin", 17, Side.ENEMY);
        BattleEngine.start(b);
        assertTrue(b.active);
        assertEquals(1, b.round);
        assertEquals(gob.id, b.turnId);
        assertTrue(BattleEngine.isCurrent(b, gob));
    }

    @Test
    void nextWalksTheOrderAndStartsNewRounds() {
        Battle b = new Battle();
        Combatant a = add(b, "A", 20, Side.PLAYER);
        Combatant c = add(b, "B", 10, Side.ENEMY);
        Combatant d = add(b, "C", 5, Side.PLAYER);
        BattleEngine.start(b);
        assertEquals(a.id, b.turnId);
        BattleEngine.next(b);
        assertEquals(c.id, b.turnId);
        BattleEngine.next(b);
        assertEquals(d.id, b.turnId);
        assertEquals(1, b.round);
        BattleEngine.next(b);
        assertEquals(a.id, b.turnId, "wraps to the top");
        assertEquals(2, b.round, "and that is a new round");
        BattleEngine.previous(b);
        assertEquals(d.id, b.turnId);
        assertEquals(1, b.round, "going back across the boundary undoes the round");
        BattleEngine.previous(b);
        assertEquals(c.id, b.turnId);
    }

    @Test
    void defeatedCreaturesAreSkipped() {
        Battle b = new Battle();
        Combatant a = add(b, "A", 20, Side.PLAYER);
        Combatant dead = add(b, "Dead", 15, Side.ENEMY);
        Combatant c = add(b, "C", 10, Side.PLAYER);
        dead.defeated = true;
        BattleEngine.start(b);
        BattleEngine.next(b);
        assertEquals(c.id, b.turnId, "skips the defeated creature");
        BattleEngine.next(b);
        assertEquals(a.id, b.turnId);
        assertEquals(2, b.round);
    }

    @Test
    void ifEveryoneIsDefeatedNextDoesNothing() {
        Battle b = new Battle();
        Combatant a = add(b, "A", 20, Side.PLAYER);
        a.defeated = true;
        BattleEngine.start(b);
        assertNull(b.turnId);
        assertFalse(BattleEngine.next(b));
        assertEquals(1, b.round);
    }

    @Test
    void groupsShareOneInitiativeAndOneTurn() {
        Battle b = new Battle();
        Card goblin = card("goblin");
        add(b, "Fighter", 12, Side.PLAYER);
        List<Combatant> gobs = BattleEngine.addFromCard(b, goblin, 4, true, Side.ENEMY);
        BattleEngine.setInitiative(b, gobs.get(2), 16);
        for (Combatant g : gobs) assertEquals(16, g.initiative, "setting one member sets the whole group");

        List<Slot> slots = BattleEngine.slots(b);
        assertEquals(2, slots.size(), "4 goblins are one slot");
        assertEquals(4, slots.get(0).members.size());
        assertTrue(slots.get(0).isGroup());
        assertEquals("Goblin x4", slots.get(0).name());

        BattleEngine.start(b);
        assertTrue(BattleEngine.isCurrent(b, gobs.get(0)));
        assertTrue(BattleEngine.isCurrent(b, gobs.get(3)), "every member shares the turn");
        BattleEngine.next(b);
        assertFalse(BattleEngine.isCurrent(b, gobs.get(0)));
    }

    @Test
    void aGroupOnlyCountsAsDefeatedWhenEveryMemberIsDown() {
        Battle b = new Battle();
        List<Combatant> gobs = BattleEngine.addFromCard(b, card("goblin"), 3, true, Side.ENEMY);
        Combatant hero = add(b, "Hero", 5, Side.PLAYER);
        BattleEngine.setInitiative(b, gobs.get(0), 18);
        gobs.get(0).defeated = true;
        gobs.get(1).defeated = true;
        Slot s = BattleEngine.slots(b).get(0);
        assertFalse(s.defeated());
        assertEquals(1, s.aliveCount());
        gobs.get(2).defeated = true;
        assertTrue(BattleEngine.slots(b).get(0).defeated());
        BattleEngine.start(b);
        assertEquals(hero.id, b.turnId, "the dead group is skipped");
    }

    @Test
    void groupAndUngroup() {
        Battle b = new Battle();
        Combatant a = add(b, "Orc 1", 9, Side.ENEMY);
        Combatant c = add(b, "Orc 2", 14, Side.ENEMY);
        BattleEngine.group(b, List.of(a, c));
        assertEquals(1, BattleEngine.slots(b).size());
        assertEquals(9, c.initiative, "the first member's initiative wins");
        BattleEngine.ungroup(b, a);
        assertEquals(2, BattleEngine.slots(b).size());
        assertNull(a.group);
        assertNull(c.group);
    }

    @Test
    void addingCreaturesFromCardsCopiesStatsAndNumbersNames() {
        Battle b = new Battle();
        Card wolf = card("wolf");
        List<Combatant> one = BattleEngine.addFromCard(b, wolf, 1, false, Side.ENEMY);
        assertEquals("Wolf", one.get(0).name, "a single creature keeps the plain name");
        assertEquals(11, one.get(0).hpMax);
        assertEquals(13, one.get(0).ac);
        assertEquals(2, one.get(0).initiativeBonus);
        assertEquals(wolf.id, one.get(0).cardId);

        List<Combatant> more = BattleEngine.addFromCard(b, wolf, 2, false, Side.ENEMY);
        assertEquals("Wolf 2", more.get(0).name, "numbering continues after the existing one");
        assertEquals("Wolf 3", more.get(1).name);
        assertNull(more.get(0).group);
        assertNotEquals(more.get(0).id, more.get(1).id);
    }

    @Test
    void damageUsesTempHpFirstAndDownsAtZero() {
        Battle b = new Battle();
        Combatant c = add(b, "Orc", 10, Side.ENEMY);
        c.hpMax = c.hpCurrent = 15;
        BattleEngine.setTempHp(c, 5);
        BattleEngine.damage(b, c, 8);
        assertEquals(0, c.tempHp);
        assertEquals(12, c.hpCurrent);
        assertFalse(c.defeated);
        BattleEngine.damage(b, c, 100);
        assertEquals(0, c.hpCurrent, "never below zero");
        assertTrue(c.defeated);
        assertTrue(b.log.get(b.log.size() - 1).contains("is down"));
    }

    @Test
    void healingCapsAtMaxAndRevives() {
        Battle b = new Battle();
        Combatant c = add(b, "Hero", 10, Side.PLAYER);
        c.hpMax = 20;
        c.hpCurrent = 0;
        c.defeated = true;
        BattleEngine.heal(b, c, 5);
        assertEquals(5, c.hpCurrent);
        assertFalse(c.defeated, "healing from 0 brings them back");
        BattleEngine.heal(b, c, 500);
        assertEquals(20, c.hpCurrent);
    }

    @Test
    void hpBoxUnderstandsDamageHealAndSet() {
        Battle b = new Battle();
        Combatant c = add(b, "Ogre", 10, Side.ENEMY);
        c.hpMax = 59;
        c.hpCurrent = 59;
        assertTrue(BattleEngine.applyHpInput(b, c, "-7"));
        assertEquals(52, c.hpCurrent);
        assertTrue(BattleEngine.applyHpInput(b, c, " + 3 "));
        assertEquals(55, c.hpCurrent);
        assertTrue(BattleEngine.applyHpInput(b, c, "20"));
        assertEquals(20, c.hpCurrent, "a plain number sets the current HP");
        assertTrue(BattleEngine.applyHpInput(b, c, "999"));
        assertEquals(59, c.hpCurrent, "but never above max");
        assertFalse(BattleEngine.applyHpInput(b, c, "lots"));
        assertFalse(BattleEngine.applyHpInput(b, c, ""));
        assertEquals(59, c.hpCurrent);
    }

    @Test
    void severalHitsCanBeEnteredAtOnce() {
        Battle b = new Battle();
        Combatant c = add(b, "Fighter", 10, Side.PLAYER);
        c.hpMax = 40;
        c.hpCurrent = 40;
        assertTrue(BattleEngine.applyHpInput(b, c, "-5 -7 -3"));
        assertEquals(25, c.hpCurrent, "four goblins hit: 5 + 7 + 3 damage");
        assertTrue(BattleEngine.applyHpInput(b, c, "-4, +2"));
        assertEquals(23, c.hpCurrent, "mixed hits and healing");
        assertTrue(BattleEngine.applyHpInput(b, c, "6 4"));
        assertEquals(13, c.hpCurrent, "unsigned numbers among several count as damage");
        assertFalse(BattleEngine.applyHpInput(b, c, "-5 oops"));
        assertEquals(13, c.hpCurrent, "a bad entry changes nothing");
    }

    @Test
    void conditionsToggle() {
        Combatant c = new Combatant();
        assertTrue(BattleEngine.toggleCondition(c, "Prone"));
        assertEquals(List.of("Prone"), c.conditions);
        assertFalse(BattleEngine.toggleCondition(c, "prone"), "case-insensitive toggle removes it");
        assertTrue(c.conditions.isEmpty());
    }

    @Test
    void rollingInitiativeIsPerCreatureAndPerGroup() {
        Battle b = new Battle();
        Card goblin = card("goblin");
        List<Combatant> gobs = BattleEngine.addFromCard(b, goblin, 3, true, Side.ENEMY);
        List<Combatant> orcs = BattleEngine.addFromCard(b, card("orc"), 2, false, Side.ENEMY);
        Combatant pc = add(b, "Hero", 0, Side.PLAYER);
        int rolls = BattleEngine.rollInitiative(b, Side.ENEMY, new Random(4));
        assertEquals(3, rolls, "one for the goblin group and one per orc");
        assertEquals(gobs.get(0).initiative, gobs.get(1).initiative);
        assertEquals(gobs.get(0).initiative, gobs.get(2).initiative);
        assertEquals(0, pc.initiative, "players are not rolled for");
        for (Combatant o : orcs) {
            assertTrue(o.initiative >= 1 + o.initiativeBonus && o.initiative <= 20 + o.initiativeBonus);
        }
    }

    @Test
    void removingTheCurrentCreatureHandsOverTheTurn() {
        Battle b = new Battle();
        Combatant a = add(b, "A", 20, Side.PLAYER);
        Combatant c = add(b, "B", 10, Side.ENEMY);
        BattleEngine.start(b);
        BattleEngine.remove(b, a);
        assertEquals(c.id, b.turnId);
        assertEquals(1, b.combatants.size());
    }

    @Test
    void endingClearsTheTurn() {
        Battle b = new Battle();
        add(b, "A", 20, Side.PLAYER);
        BattleEngine.start(b);
        BattleEngine.end(b);
        assertFalse(b.active);
        assertNull(b.turnId);
        assertNull(BattleEngine.currentSlot(b));
    }

    @Test
    void logStaysShort() {
        Battle b = new Battle();
        for (int i = 0; i < 500; i++) BattleEngine.log(b, "line " + i);
        assertEquals(200, b.log.size());
        assertEquals("line 499", b.log.get(b.log.size() - 1));
    }
}
