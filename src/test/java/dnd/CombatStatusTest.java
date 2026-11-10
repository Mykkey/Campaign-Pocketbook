package dnd;

import dnd.battle.BattleEngine;
import dnd.battle.CombatStatus;
import dnd.model.Battle;
import dnd.model.Card;
import dnd.model.Combatant;
import dnd.model.Combatant.Side;
import dnd.model.Feature;
import dnd.storage.Json;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CombatStatusTest {
    private static Combatant add(Battle b, String name, int initiative, Side side) {
        Combatant c = new Combatant();
        c.name = name;
        c.side = side;
        c.initiative = initiative;
        c.initiativeSet = true;
        c.hpMax = c.hpCurrent = 10;
        c.order = b.combatants.size();
        b.combatants.add(c);
        return c;
    }

    @Test
    void aPlayerCharacterAtZeroIsDyingNotDefeatedButAMonsterIsDefeated() {
        Battle b = new Battle();
        Combatant pc = add(b, "Mira", 15, Side.PLAYER);
        Combatant orc = add(b, "Orc", 10, Side.ENEMY);
        CombatStatus.setConcentration(pc, "Bless");
        BattleEngine.damage(b, pc, 12);
        BattleEngine.damage(b, orc, 12);
        assertEquals(0, pc.hpCurrent);
        assertTrue(CombatStatus.isDying(pc));
        assertFalse(pc.defeated, "a dying player stays in the turn order to roll its saves");
        assertTrue(CombatStatus.hasCondition(pc, "Unconscious"));
        assertFalse(CombatStatus.isConcentrating(pc), "concentration ends when it falls unconscious");
        assertTrue(orc.defeated);
        assertFalse(CombatStatus.isDying(orc));
        assertTrue(BattleEngine.slots(b).stream().anyMatch(s -> s.name().equals("Mira") && !s.defeated()));
    }

    @Test
    void theDmCanGiveAMonsterDeathSavesAndTakeThemFromAPlayer() {
        Battle b = new Battle();
        Combatant boss = add(b, "Lich", 10, Side.ENEMY);
        boss.deathSaves = true;
        Combatant pc = add(b, "Hero", 10, Side.PLAYER);
        pc.deathSaves = false;
        BattleEngine.damage(b, boss, 10);
        BattleEngine.damage(b, pc, 99);
        assertTrue(CombatStatus.isDying(boss));
        assertTrue(pc.defeated);
        assertFalse(CombatStatus.isDying(pc));
    }

    @Test
    void damageThatLeavesAtLeastTheMaximumKillsOutright() {
        Battle b = new Battle();
        Combatant pc = add(b, "Mira", 15, Side.PLAYER);
        BattleEngine.damage(b, pc, 20); // 10 to reach 0, 10 left over = max
        assertTrue(CombatStatus.isDead(pc));
        assertTrue(pc.defeated);
        Combatant pc2 = add(b, "Joss", 14, Side.PLAYER);
        BattleEngine.damage(b, pc2, 19);
        assertTrue(CombatStatus.isDying(pc2), "9 left over is not enough");
    }

    @Test
    void hitsAtZeroAreFailuresAndTheThirdKills() {
        Battle b = new Battle();
        Combatant pc = add(b, "Mira", 15, Side.PLAYER);
        BattleEngine.damage(b, pc, 10);
        BattleEngine.damage(b, pc, 3);
        assertEquals(1, pc.deathFailures);
        BattleEngine.damage(b, pc, 3);
        assertEquals(2, pc.deathFailures);
        BattleEngine.damage(b, pc, 3);
        assertTrue(CombatStatus.isDead(pc));
        BattleEngine.damage(b, pc, 3);
        assertEquals(3, pc.deathFailures, "a dead creature collects no more failures");
    }

    @Test
    void temporaryHitPointsAtZeroSoakUpTheHitWithoutAFailure() {
        Battle b = new Battle();
        Combatant pc = add(b, "Mira", 15, Side.PLAYER);
        BattleEngine.damage(b, pc, 10);
        pc.tempHp = 5;
        BattleEngine.damage(b, pc, 4);
        assertEquals(0, pc.deathFailures);
        assertEquals(1, pc.tempHp);
    }

    @Test
    void deathSavingThrowsFollowTheRules() {
        Battle b = new Battle();
        Combatant pc = add(b, "Mira", 15, Side.PLAYER);
        BattleEngine.damage(b, pc, 10);
        CombatStatus.deathSave(b, pc, 10);
        assertEquals(1, pc.deathSuccesses);
        CombatStatus.deathSave(b, pc, 9);
        assertEquals(1, pc.deathFailures);
        CombatStatus.deathSave(b, pc, 1);
        assertTrue(CombatStatus.isDead(pc), "a natural 1 is two failures: 1 + 2 = 3");

        Combatant pc2 = add(b, "Joss", 14, Side.PLAYER);
        BattleEngine.damage(b, pc2, 10);
        CombatStatus.deathSave(b, pc2, 20);
        assertEquals(1, pc2.hpCurrent);
        assertFalse(CombatStatus.isDying(pc2));
        assertFalse(CombatStatus.hasCondition(pc2, "Unconscious"));
        assertEquals("", CombatStatus.deathSave(b, pc2, 5), "saves only happen while dying");
    }

    @Test
    void threeSuccessesMakeItStableAndAHitMakesItDyingAgain() {
        Battle b = new Battle();
        Combatant pc = add(b, "Mira", 15, Side.PLAYER);
        add(b, "Goblin", 5, Side.ENEMY);
        BattleEngine.damage(b, pc, 10);
        for (int i = 0; i < 3; i++) CombatStatus.deathSave(b, pc, 12);
        assertTrue(CombatStatus.isStable(pc));
        assertTrue(pc.defeated, "a stable creature takes no turns");
        BattleEngine.damage(b, pc, 2);
        assertTrue(CombatStatus.isDying(pc));
        assertEquals(1, pc.deathFailures);
        assertEquals(0, pc.deathSuccesses);
        assertFalse(pc.stable);
    }

    @Test
    void healingBringsADyingCreatureBackAndClearsTheSaves() {
        Battle b = new Battle();
        Combatant pc = add(b, "Mira", 15, Side.PLAYER);
        BattleEngine.damage(b, pc, 10);
        CombatStatus.deathSave(b, pc, 4);
        BattleEngine.heal(b, pc, 3);
        assertEquals(3, pc.hpCurrent);
        assertFalse(pc.defeated);
        assertEquals(0, pc.deathFailures);
        assertFalse(CombatStatus.hasCondition(pc, "Unconscious"));
        assertFalse(CombatStatus.isDying(pc));
    }

    @Test
    void concentrationChecksUseTheHigherOfTenAndHalfTheDamage() {
        assertEquals(10, CombatStatus.concentrationDc(5));
        assertEquals(10, CombatStatus.concentrationDc(21));
        assertEquals(15, CombatStatus.concentrationDc(30));
        Battle b = new Battle();
        Combatant wiz = add(b, "Wizard", 12, Side.PLAYER);
        wiz.hpMax = wiz.hpCurrent = 50;
        CombatStatus.setConcentration(wiz, "Hold Person");
        assertTrue(CombatStatus.isConcentrating(wiz));
        String msg = BattleEngine.damage(b, wiz, 30);
        assertTrue(msg.contains("DC 15"), msg);
        CombatStatus.dropConcentration(b, wiz);
        assertFalse(CombatStatus.isConcentrating(wiz));
        assertNull(wiz.concentratingOn);
        assertTrue(b.log.stream().anyMatch(l -> l.contains("stops concentrating on Hold Person")));
    }

    @Test
    void effectsLastTheirTurnsAndEndAfterTheCreaturesOwnTurn() {
        Battle b = new Battle();
        Combatant a = add(b, "Aelin", 15, Side.PLAYER);
        Combatant g = add(b, "Goblin", 10, Side.ENEMY);
        assertTrue(BattleEngine.start(b));
        CombatStatus.addEffect(g, "Hunter's Mark", 2);
        BattleEngine.next(b); // Aelin's turn ends, Goblin's begins
        assertEquals(2, g.effects.get(0).turns);
        BattleEngine.next(b); // Goblin's turn ends
        assertEquals(1, g.effects.get(0).turns);
        BattleEngine.next(b);
        BattleEngine.next(b);
        assertTrue(g.effects.isEmpty());
        assertTrue(b.log.stream().anyMatch(l -> l.contains("Hunter's Mark on Goblin ends")));
        assertTrue(a.effects.isEmpty());
    }

    @Test
    void goingBackDoesNotCountDownEffects() {
        Battle b = new Battle();
        add(b, "Aelin", 15, Side.PLAYER);
        Combatant g = add(b, "Goblin", 10, Side.ENEMY);
        BattleEngine.start(b);
        BattleEngine.next(b);
        CombatStatus.addEffect(g, "Slow", 3);
        BattleEngine.previous(b);
        BattleEngine.next(b);
        assertEquals(3, g.effects.get(0).turns);
    }

    @Test
    void reactionAndLegendaryActionsComeBackAtTheStartOfTheTurn() {
        Battle b = new Battle();
        add(b, "Aelin", 15, Side.PLAYER);
        Combatant dragon = add(b, "Dragon", 10, Side.ENEMY);
        dragon.legendaryMax = 3;
        BattleEngine.start(b);
        dragon.reactionUsed = true;
        dragon.legendaryUsed = 2;
        BattleEngine.next(b);
        assertFalse(dragon.reactionUsed);
        assertEquals(0, dragon.legendaryUsed);
    }

    @Test
    void creaturesWithLegendaryActionsStartWithThree() {
        Battle b = new Battle();
        Card dragon = new Card();
        dragon.name = "Dragon";
        dragon.hpMax = 200;
        dragon.legendary.add(new Feature());
        Card goblin = new Card();
        goblin.name = "Goblin";
        BattleEngine.addFromCard(b, dragon, 1, false, Side.ENEMY);
        BattleEngine.addFromCard(b, goblin, 1, false, Side.ENEMY);
        assertEquals(3, b.combatants.get(0).legendaryMax);
        assertEquals(0, b.combatants.get(1).legendaryMax);
    }

    @Test
    void theNewStateIsSavedAndOldSavesStillLoad() {
        Battle b = new Battle();
        Combatant pc = add(b, "Mira", 15, Side.PLAYER);
        BattleEngine.damage(b, pc, 10);
        CombatStatus.deathSave(b, pc, 12);
        CombatStatus.addEffect(pc, "Bless", 4);
        pc.reactionUsed = true;
        pc.legendaryMax = 2;
        Battle back = Json.read(Json.write(b), Battle.class);
        Combatant c = back.combatants.get(0);
        assertEquals(1, c.deathSuccesses);
        assertEquals("Bless", c.effects.get(0).name);
        assertEquals(4, c.effects.get(0).turns);
        assertTrue(c.reactionUsed);
        assertEquals(2, c.legendaryMax);
        assertTrue(CombatStatus.isDying(c));

        Combatant old = Json.read("{\"name\":\"Old\",\"side\":\"PLAYER\",\"hpMax\":8,\"hpCurrent\":8}", Combatant.class);
        assertNull(old.deathSaves);
        assertTrue(CombatStatus.makesDeathSaves(old));
        assertTrue(old.effects.isEmpty());
        assertEquals(0, old.deathFailures);
    }
}
