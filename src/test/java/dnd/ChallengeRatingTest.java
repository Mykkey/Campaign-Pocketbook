package dnd;

import dnd.model.Attack;
import dnd.model.Card;
import dnd.model.Feature;
import dnd.rules.ChallengeRating;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChallengeRatingTest {
    private static Card creature(int hp, int ac) {
        Card c = new Card();
        c.hpMax = hp;
        c.ac = ac;
        return c;
    }

    private static Attack attack(int toHit, String damage) {
        return new Attack("Hit", toHit, damage, "slashing", "5 ft.", "");
    }

    @Test
    void aCreatureRightOnTheTableGetsThatRating() {
        Card c = creature(138, 15);
        c.attacks.add(attack(6, "10d6")); // 35 a round
        ChallengeRating.Result r = ChallengeRating.of(c);
        assertEquals("5", r.defensive());
        assertEquals("5", r.offensive());
        assertEquals("5", r.suggested());
        assertEquals(1800, r.xp());
    }

    @Test
    void aGoblinIsAQuarter() {
        Card c = creature(7, 15);
        c.attacks.add(attack(4, "1d6+2"));
        c.attacks.add(attack(4, "1d6+2"));
        ChallengeRating.Result r = ChallengeRating.of(c);
        assertEquals("1/4", r.defensive(), "7 hp is 1/8, armor 15 is two higher than 13");
        assertEquals("1/4", r.offensive());
        assertEquals("1/4", r.suggested());
        assertEquals(50, r.xp());
    }

    @Test
    void armorAndAttackBonusMoveTheRatingOneStepForEveryTwoPoints() {
        Card base = creature(138, 15);
        base.attacks.add(attack(6, "10d6"));
        Card tough = creature(138, 19); // 4 higher: two steps up
        tough.attacks.add(attack(6, "10d6"));
        assertEquals("7", ChallengeRating.of(tough).defensive());
        Card soft = creature(138, 13);
        soft.attacks.add(attack(6, "10d6"));
        assertEquals("4", ChallengeRating.of(soft).defensive());
        Card accurate = creature(138, 15);
        accurate.attacks.add(attack(10, "10d6")); // 4 higher: two steps up
        assertEquals("7", ChallengeRating.of(accurate).offensive());
        assertEquals("5", ChallengeRating.of(base).suggested());
        assertEquals("6", ChallengeRating.of(accurate).suggested(), "5 and 7 average to 6");
        Card oneOff = creature(138, 16); // only one higher: no change
        oneOff.attacks.add(attack(6, "10d6"));
        assertEquals("5", ChallengeRating.of(oneOff).defensive());
    }

    @Test
    void aMultiattackAddsUpTheBestAttacks() {
        Card ogre = creature(59, 13);
        ogre.attacks.add(attack(6, "2d8+4")); // 13
        ogre.actions.add(new Feature("Multiattack", "The ogre makes two greatclub attacks."));
        ChallengeRating.Result r = ChallengeRating.of(ogre);
        // 26 damage a round is the row for CR 3, attack +6 is 3 higher than +4: one step up
        assertEquals("4", r.offensive());
        Card single = creature(59, 13);
        single.attacks.add(attack(6, "2d8+4"));
        assertEquals("2", ChallengeRating.of(single).offensive(), "13 damage is CR 1, +6 is 3 above +3: one step up");
    }

    @Test
    void aBreathWeaponCountsByItsSaveDc() {
        Card c = creature(138, 15);
        c.actions.add(new Feature("Fire Breath", "Each creature in a 30-foot cone must make a DC 15 Dexterity saving throw, taking 35 (10d6) fire damage on a failed save."));
        ChallengeRating.Result r = ChallengeRating.of(c);
        assertEquals("5", r.offensive(), "35 damage a round is CR 5 and DC 15 is what CR 5 expects");
        c.actions.get(0).text = "Each creature in a 30-foot cone must make a DC 19 Dexterity saving throw, taking 35 (10d6) fire damage.";
        assertEquals("7", ChallengeRating.of(c).offensive(), "DC 19 is 4 above 15: two steps up");
    }

    @Test
    void resistanceToOrdinaryWeaponsCountsForExtraHitPoints() {
        Card plain = creature(100, 13);
        Card resistant = creature(100, 13);
        resistant.resistances = "bludgeoning, piercing, and slashing from nonmagical attacks";
        assertEquals("2", ChallengeRating.of(plain).defensive());
        assertEquals("8", ChallengeRating.of(resistant).defensive(), "100 hp counted as 200 is CR 9, and armor 13 is three below the 16 expected: one step down");
        assertTrue(ChallengeRating.indexOf(ChallengeRating.of(resistant).defensive()) > ChallengeRating.indexOf(ChallengeRating.of(plain).defensive()));
        Card fire = creature(100, 13);
        fire.resistances = "fire";
        assertEquals("2", ChallengeRating.of(fire).defensive(), "resistance to one kind of damage does not count");
    }

    @Test
    void emptyAndExtremeCardsStayInRange() {
        Card nothing = creature(0, 10);
        ChallengeRating.Result r = ChallengeRating.of(nothing);
        assertEquals("0", r.offensive());
        assertNotNull(r.suggested());
        Card huge = creature(9999, 30);
        huge.attacks.add(attack(30, "40d12"));
        assertEquals("30", ChallengeRating.of(huge).suggested());
        Card noDice = creature(50, 12);
        noDice.attacks.add(attack(5, "lots"));
        assertEquals("0", ChallengeRating.of(noDice).offensive(), "damage that cannot be read is not counted");
    }

    @Test
    void ratingsCanBeReadBack() {
        assertEquals(0, ChallengeRating.indexOf("0"));
        assertEquals(2, ChallengeRating.indexOf("1/4"));
        assertEquals(2, ChallengeRating.indexOf("0.25"));
        assertEquals(4, ChallengeRating.indexOf("1"));
        assertEquals(7, ChallengeRating.indexOf("4 (1,100 XP)"));
        assertEquals(33, ChallengeRating.indexOf("30"));
        assertEquals(-1, ChallengeRating.indexOf("31"));
        assertEquals(-1, ChallengeRating.indexOf(null));
    }
}
