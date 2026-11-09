package dnd;

import dnd.loot.LootGenerator;
import dnd.loot.LootGenerator.Kind;
import dnd.loot.LootGenerator.Loot;
import dnd.loot.LootGenerator.Rarity;
import dnd.loot.LootGenerator.Tier;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class LootGeneratorTest {
    @Test
    void sameSeedGivesTheSameLoot() {
        for (Tier t : Tier.values()) {
            for (Kind k : Kind.values()) {
                assertEquals(LootGenerator.generate(t, k, 3, new Random(42)).toText(),
                        LootGenerator.generate(t, k, 3, new Random(42)).toText());
            }
        }
    }

    @Test
    void pocketsHoldOnlyCoinsAndAnOccasionalKeepsake() {
        for (int seed = 0; seed < 200; seed++) {
            for (Tier t : Tier.values()) {
                Loot l = LootGenerator.generate(t, Kind.INDIVIDUAL, 1, new Random(seed));
                assertTrue(l.valuables.isEmpty());
                assertTrue(l.items.isEmpty());
                assertFalse(l.isEmpty(), "never an empty purse");
                assertTrue(l.totalGp() >= 0);
            }
        }
    }

    @Test
    void moreCreaturesMeansMoreCoin() {
        int one = 0, thirty = 0;
        for (int seed = 0; seed < 50; seed++) {
            one += LootGenerator.generate(Tier.TIER2, Kind.INDIVIDUAL, 1, new Random(seed)).totalGp();
            thirty += LootGenerator.generate(Tier.TIER2, Kind.INDIVIDUAL, 30, new Random(seed)).totalGp();
        }
        assertTrue(thirty > one * 10, "30 creatures should carry far more than 1");
    }

    @Test
    void hoardsGetRicherWithTier() {
        long[] total = new long[4];
        for (int seed = 0; seed < 200; seed++) {
            for (Tier t : Tier.values()) total[t.ordinal()] += LootGenerator.generate(t, Kind.HOARD, 1, new Random(seed)).totalGp();
        }
        for (int i = 1; i < 4; i++) assertTrue(total[i] > total[i - 1], "tier " + (i + 1) + " should beat tier " + i);
    }

    @Test
    void magicItemsStayWithinTheTiersRarities() {
        for (Tier t : Tier.values()) {
            for (int seed = 0; seed < 300; seed++) {
                for (var item : LootGenerator.generate(t, Kind.HOARD, 1, new Random(seed)).items) {
                    assertTrue(LootGenerator.raritiesFor(t).contains(item.rarity()), t + " produced " + item.rarity());
                    assertFalse(item.name().isBlank());
                }
            }
        }
        assertFalse(LootGenerator.raritiesFor(Tier.TIER1).contains(Rarity.LEGENDARY));
        assertTrue(LootGenerator.raritiesFor(Tier.TIER4).contains(Rarity.LEGENDARY));
    }

    @Test
    void textListsEachSectionAndTheTotal() {
        // find a high-tier hoard that has everything, then check it reads properly
        Loot full = null;
        for (int seed = 0; seed < 500 && full == null; seed++) {
            Loot l = LootGenerator.generate(Tier.TIER3, Kind.HOARD, 1, new Random(seed));
            boolean gems = l.valuables.stream().anyMatch(v -> !v.art());
            boolean art = l.valuables.stream().anyMatch(v -> v.art());
            if (gems && art && !l.items.isEmpty() && !l.curios.isEmpty()) full = l;
        }
        assertNotNull(full, "some seed should produce every kind of treasure");
        String text = full.toText();
        assertTrue(text.contains("Coins:"));
        assertTrue(text.contains("Gems:"));
        assertTrue(text.contains("Art objects:"));
        assertTrue(text.contains("Magic items:"));
        assertTrue(text.contains("Odds and ends:"));
        assertTrue(text.contains("gp in all"));
    }

    @Test
    void totalValueConvertsCoins() {
        Loot l = LootGenerator.generate(Tier.TIER1, Kind.INDIVIDUAL, 1, new Random(1));
        l.coins.replaceAll((k, v) -> 0);
        l.coins.put("cp", 100);
        l.coins.put("sp", 10);
        l.coins.put("gp", 1);
        l.coins.put("pp", 1);
        l.curios.clear();
        assertEquals(1 + 1 + 1 + 10, l.totalGp());
    }

    @Test
    void identicalGemsAreGroupedInTheText() {
        Loot l = LootGenerator.generate(Tier.TIER1, Kind.INDIVIDUAL, 1, new Random(1));
        l.valuables.clear();
        l.valuables.add(new LootGenerator.Valuable("onyx", 50, false));
        l.valuables.add(new LootGenerator.Valuable("onyx", 50, false));
        l.valuables.add(new LootGenerator.Valuable("jasper", 50, false));
        String text = l.toText();
        assertTrue(text.contains("2 x onyx (50 gp)"), text);
        assertTrue(text.contains("jasper (50 gp)"), text);
    }
}