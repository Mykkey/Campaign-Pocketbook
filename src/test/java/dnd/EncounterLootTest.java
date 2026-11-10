package dnd;

import dnd.loot.EncounterLoot;
import dnd.loot.EncounterLoot.Creature;
import dnd.loot.LootGenerator.Kind;
import dnd.loot.LootGenerator.Tier;
import dnd.model.Encounter;
import dnd.model.WorldMap;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class EncounterLootTest {
    @Test
    void challengeRatingsMapToTheTreasureTiers() {
        assertEquals(Tier.TIER1, EncounterLoot.tierFor("0"));
        assertEquals(Tier.TIER1, EncounterLoot.tierFor("1/4"));
        assertEquals(Tier.TIER1, EncounterLoot.tierFor("0.5"));
        assertEquals(Tier.TIER1, EncounterLoot.tierFor("4"));
        assertEquals(Tier.TIER2, EncounterLoot.tierFor("5"));
        assertEquals(Tier.TIER2, EncounterLoot.tierFor("10"));
        assertEquals(Tier.TIER3, EncounterLoot.tierFor("11"));
        assertEquals(Tier.TIER3, EncounterLoot.tierFor("16"));
        assertEquals(Tier.TIER4, EncounterLoot.tierFor("17"));
        assertEquals(Tier.TIER4, EncounterLoot.tierFor("30"));
        assertEquals(Tier.TIER2, EncounterLoot.tierFor("5 (1,800 XP)"), "stated XP after the rating is ignored");
        assertEquals(Tier.TIER1, EncounterLoot.tierFor(null));
        assertEquals(Tier.TIER1, EncounterLoot.tierFor("lots"));
    }

    @Test
    void individualTreasureIsRolledPerTierAndAHoardAtTheHighestTier() {
        List<Creature> fight = List.of(new Creature("Goblin", "1/4", 6), new Creature("Ogre", "2", 2), new Creature("Troll", "5", 1));
        EncounterLoot.Result ind = EncounterLoot.roll(fight, Kind.INDIVIDUAL, new Random(1));
        assertTrue(ind.text().contains("Pockets of 8 creatures, " + Tier.TIER1.label), ind.text());
        assertTrue(ind.text().contains("Pockets of 1 creature, " + Tier.TIER2.label), ind.text());
        assertFalse(ind.text().contains("hoard"));

        EncounterLoot.Result hoard = EncounterLoot.roll(fight, Kind.HOARD, new Random(1));
        assertTrue(hoard.text().startsWith("Treasure hoard, " + Tier.TIER2.label), hoard.text());
        assertTrue(hoard.totalGp() >= 0);
    }

    @Test
    void theSameSeedGivesTheSameTreasureAndNoEnemiesGiveNone() {
        List<Creature> fight = List.of(new Creature("Wyrm", "17", 1));
        assertEquals(EncounterLoot.roll(fight, Kind.HOARD, new Random(5)), EncounterLoot.roll(fight, Kind.HOARD, new Random(5)));
        EncounterLoot.Result none = EncounterLoot.roll(List.of(new Creature("Ghost", "1", 0)), Kind.HOARD, new Random(5));
        assertTrue(none.text().contains("nothing to roll"));
        assertEquals(0, none.totalGp());
        assertTrue(EncounterLoot.roll(List.of(), Kind.INDIVIDUAL, new Random(5)).text().contains("nothing to roll"));
    }

    @Test
    void theTreasureIsSavedWithTheEncounterAndCopied(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        Encounter e = store.createEncounter(store.project.root, "Ambush");
        e.loot = "Coins: 40 gp";
        store.markEncounterDirty(e);
        store.flush();
        Encounter back = ProjectStore.open(tmp, "Camp").encounter(e.id);
        assertEquals("Coins: 40 gp", back.loot);
        assertEquals("Coins: 40 gp", back.copy().loot);
        assertNull(new Encounter().loot, "old encounters have no treasure");
    }
}
