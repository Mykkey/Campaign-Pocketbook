package dnd;

import dnd.model.Card;
import dnd.names.NameData.Race;
import dnd.names.NameGenerator.Gender;
import dnd.npc.*;
import dnd.npc.NpcData.AgeGroup;
import dnd.npc.NpcData.Attitude;
import dnd.npc.NpcData.Profession;
import dnd.npc.NpcGenerator.Detail;
import dnd.npc.NpcGenerator.Spec;
import dnd.rules.CardTemplates;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NpcGeneratorTest {
    private static NpcGenerator gen(long seed) {
        return new NpcGenerator(new Random(seed), NpcData.PROFESSIONS);
    }

    @Test
    void sameSeedGivesTheSameNpc() {
        assertEquals(gen(7).generate(Spec.any()).toText(), gen(7).generate(Spec.any()).toText());
        assertNotEquals(gen(7).generate(Spec.any()).toText(), gen(8).generate(Spec.any()).toText());
    }

    @Test
    void everythingIsFilledIn() {
        for (int seed = 0; seed < 300; seed++) {
            Npc n = gen(seed).generate(Spec.any());
            assertFalse(n.name.isBlank());
            assertNotNull(n.profession);
            assertEquals(2, n.appearance.size());
            assertEquals(2, n.personality.size());
            assertEquals(2, new HashSet<>(n.personality).size(), "two different traits");
            assertEquals(2, n.knows.size());
            for (String s : List.of(n.opening, n.mannerism, n.voice, n.wants, n.secret)) assertFalse(s.isBlank());
        }
    }

    @Test
    void pinnedThingsAreHonoured() {
        Spec spec = new Spec(Race.DWARF, Gender.FEMININE, "Innkeeper", null, AgeGroup.ELDERLY, Attitude.WARY);
        for (int seed = 0; seed < 100; seed++) {
            Npc n = gen(seed).generate(spec);
            assertEquals(Race.DWARF, n.race);
            assertEquals(Gender.FEMININE, n.gender);
            assertEquals("Innkeeper", n.profession.name());
            assertEquals(AgeGroup.ELDERLY, n.ageGroup);
            assertEquals(Attitude.WARY, n.attitude);
            int[] range = NpcData.ageRange(Race.DWARF, AgeGroup.ELDERLY);
            assertTrue(n.age >= range[0] && n.age <= range[1], n.age + " outside " + range[0] + "-" + range[1]);
            assertTrue(n.toText().contains("Female dwarf innkeeper"), n.toText());
        }
    }

    @Test
    void aCategoryLimitsTheProfession() {
        Spec spec = new Spec(null, null, null, NpcData.SHADY, null, null);
        Set<String> seen = new HashSet<>();
        for (int seed = 0; seed < 200; seed++) {
            Npc n = gen(seed).generate(spec);
            assertEquals(NpcData.SHADY, n.profession.category());
            seen.add(n.profession.name());
        }
        assertTrue(seen.size() > 3, "a category still gives variety: " + seen);
    }

    @Test
    void unknownProfessionFallsBackToRandom() {
        Npc n = gen(1).generate(new Spec(null, null, "Space pirate", null, null, null));
        assertNotNull(n.profession);
    }

    @Test
    void agesFitTheRaceAndGroup() {
        for (Race race : Race.values()) {
            int prevHi = 0;
            for (AgeGroup g : AgeGroup.values()) {
                int[] r = NpcData.ageRange(race, g);
                assertTrue(r[0] >= 1 && r[1] > r[0], race + " " + g);
                assertTrue(r[0] >= prevHi - 1 || g == AgeGroup.YOUNG, race + " groups should not overlap much");
                prevHi = r[1];
            }
        }
        assertTrue(NpcData.ageRange(Race.ELF, AgeGroup.ADULT)[0] > NpcData.ageRange(Race.HUMAN, AgeGroup.ADULT)[0]);
        assertTrue(NpcData.ageRange(Race.DWARF, AgeGroup.ELDERLY)[1] > NpcData.ageRange(Race.HUMAN, AgeGroup.ELDERLY)[1]);
    }

    @Test
    void attitudeLeansTheTraitsWarmOrCold() {
        Set<String> warm = Set.of(NpcData.PERSONALITY_WARM);
        Set<String> cold = Set.of(NpcData.PERSONALITY_COLD);
        for (int seed = 0; seed < 100; seed++) {
            Npc friendly = gen(seed).generate(new Spec(null, null, null, null, null, Attitude.FRIENDLY));
            assertTrue(friendly.personality.stream().allMatch(warm::contains), friendly.personality.toString());
            Npc hostile = gen(seed).generate(new Spec(null, null, null, null, null, Attitude.HOSTILE));
            assertTrue(hostile.personality.stream().allMatch(cold::contains), hostile.personality.toString());
        }
    }

    @Test
    void randomRacesAreMostlyHumanButNotAlways() {
        int humans = 0;
        Set<Race> races = new HashSet<>();
        for (int seed = 0; seed < 600; seed++) {
            Race r = gen(seed).generate(Spec.any()).race;
            races.add(r);
            if (r == Race.HUMAN) humans++;
        }
        assertTrue(humans > 150 && humans < 450, "humans: " + humans);
        assertTrue(races.size() >= 8, "variety: " + races);
    }

    @Test
    void unisexRacesHaveNoGender() {
        Race unisex = null;
        for (Race r : Race.values()) if (r.unisex()) unisex = r;
        assertNotNull(unisex, "the name data has at least one unisex race");
        Npc n = gen(3).generate(new Spec(unisex, Gender.FEMININE, null, null, null, null));
        assertEquals(Gender.ANY, n.gender);
    }

    @Test
    void rerollChangesOnlyTheChosenDetail() {
        for (Detail d : Detail.values()) {
            NpcGenerator g = gen(42);
            Npc base = g.generate(Spec.any());
            Npc n = base.copy();
            boolean changed = false;
            for (int i = 0; i < 40 && !changed; i++) {
                g.reroll(n, d, Spec.any());
                changed = !n.toText().equals(base.toText());
            }
            assertTrue(changed, d + " never changed anything");
        }
        // one detail only: wants
        NpcGenerator g = gen(5);
        Npc base = g.generate(Spec.any());
        Npc n = base.copy();
        g.reroll(n, Detail.WANTS, Spec.any());
        assertEquals(base.name, n.name);
        assertEquals(base.appearance, n.appearance);
        assertEquals(base.personality, n.personality);
        assertEquals(base.secret, n.secret);
        assertEquals(base.profession, n.profession);
        assertEquals(base.age, n.age);
    }

    @Test
    void rerollRespectsPins() {
        Spec spec = new Spec(null, null, "Blacksmith", null, AgeGroup.YOUNG, Attitude.FRIENDLY);
        NpcGenerator g = gen(9);
        Npc n = g.generate(spec);
        for (int i = 0; i < 30; i++) {
            g.reroll(n, Detail.PROFESSION, spec);
            g.reroll(n, Detail.AGE, spec);
            g.reroll(n, Detail.ATTITUDE, spec);
        }
        assertEquals("Blacksmith", n.profession.name());
        assertEquals(AgeGroup.YOUNG, n.ageGroup);
        assertEquals(Attitude.FRIENDLY, n.attitude);
    }

    @Test
    void rerollingTheNameKeepsRaceAndGender() {
        NpcGenerator g = gen(11);
        Npc n = g.generate(new Spec(Race.ELF, Gender.MASCULINE, null, null, null, null));
        for (int i = 0; i < 20; i++) g.reroll(n, Detail.NAME, Spec.any());
        assertEquals(Race.ELF, n.race);
        assertEquals(Gender.MASCULINE, n.gender);
        assertTrue(n.name.contains(" "), "first and last name");
    }

    @Test
    void rerollingTheProfessionPicksNewHints() {
        NpcGenerator g = gen(13);
        Npc n = g.generate(new Spec(null, null, "Guide", null, null, null));
        assertTrue(n.profession.knows().containsAll(n.knows));
        g.reroll(n, Detail.PROFESSION, new Spec(null, null, "Thief", null, null, null));
        assertEquals("Thief", n.profession.name());
        assertTrue(n.profession.knows().containsAll(n.knows), "hints now come from the new profession");
    }

    // ================================================================ the data

    @Test
    void everyProfessionIsUsable() {
        Set<String> templates = new HashSet<>();
        for (CardTemplates.Template t : CardTemplates.list()) templates.add(t.id);
        Set<String> names = new HashSet<>();
        for (Profession p : NpcData.PROFESSIONS) {
            assertTrue(names.add(p.name().toLowerCase()), "duplicate profession " + p.name());
            assertTrue(templates.contains(p.template()), p.name() + " uses unknown template " + p.template());
            assertTrue(p.knows().size() >= 3, p.name() + " needs at least 3 hints");
            assertFalse(p.category().isBlank());
        }
        assertTrue(NpcData.PROFESSIONS.size() >= 60);
    }

    @Test
    void everyCategoryHasProfessionsAndIsListed() {
        for (String c : NpcData.categories()) {
            assertTrue(NpcData.PROFESSIONS.stream().anyMatch(p -> p.category().equals(c)));
        }
        assertEquals(8, NpcData.categories().size());
    }

    @Test
    void tablesHaveNoDuplicatesAndEnoughChoice() {
        String[][] tables = {NpcData.APPEARANCE, NpcData.PERSONALITY_WARM, NpcData.PERSONALITY_NEUTRAL, NpcData.PERSONALITY_COLD,
                NpcData.MANNERISMS, NpcData.VOICES, NpcData.WANTS, NpcData.SECRETS};
        for (String[] t : tables) {
            assertTrue(t.length >= 18, "table too small: " + t.length);
            assertEquals(t.length, new HashSet<>(List.of(t)).size(), "duplicate entry in a table");
            for (String s : t) assertFalse(s.isBlank());
        }
        assertEquals(Attitude.values().length, NpcData.OPENINGS.length);
        for (String[] o : NpcData.OPENINGS) assertTrue(o.length >= 3);
    }

    // ================================================================ cards

    @Test
    void anNpcBecomesAnNpcCard() {
        Npc n = gen(21).generate(new Spec(Race.HALFLING, Gender.FEMININE, "Town guard", null, null, null));
        Card c = NpcCards.toCard(n);
        assertEquals(Card.Kind.NPC, c.kind);
        assertEquals(n.name, c.name);
        assertEquals("Small", c.size);
        assertEquals("humanoid (halfling)", c.creatureType);
        assertEquals(n.toText(), c.notes);
        assertEquals(CardTemplates.create("guard", null).hpMax, c.hpMax, "stats come from the profession's template");
        assertNotEquals(CardTemplates.create("guard", null).id, c.id);

        Card human = NpcCards.toCard(gen(22).generate(new Spec(Race.HUMAN, null, null, null, null, null)));
        assertEquals("Medium", human.size);
    }
}