package dnd;

import dnd.model.Card;
import dnd.model.Combatant.Side;
import dnd.model.Encounter;
import dnd.model.TreeNode;
import dnd.rules.EncounterMath;
import dnd.rules.EncounterMath.Difficulty;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EncounterTest {
    @Test
    void challengeRatingsMapToXp() {
        assertEquals(50, EncounterMath.xpForChallenge("1/4"));
        assertEquals(50, EncounterMath.xpForChallenge("0.25"));
        assertEquals(200, EncounterMath.xpForChallenge(" 1 "));
        assertEquals(1800, EncounterMath.xpForChallenge("5 (1,800 XP)"));
        assertEquals(0, EncounterMath.xpForChallenge("lots"));
        assertEquals(0, EncounterMath.xpForChallenge(null));
        assertEquals(75, EncounterMath.xpOf(75, "5"), "stated XP wins");
        assertEquals(1800, EncounterMath.xpOf(0, "5"));
    }

    @Test
    void multiplierGrowsWithMonsterCount() {
        assertEquals(0, EncounterMath.multiplier(0, 4));
        assertEquals(1.0, EncounterMath.multiplier(1, 4));
        assertEquals(1.5, EncounterMath.multiplier(2, 4));
        assertEquals(2.0, EncounterMath.multiplier(3, 4));
        assertEquals(2.0, EncounterMath.multiplier(6, 4));
        assertEquals(2.5, EncounterMath.multiplier(7, 4));
        assertEquals(3.0, EncounterMath.multiplier(11, 4));
        assertEquals(4.0, EncounterMath.multiplier(15, 4));
    }

    @Test
    void smallAndLargePartiesShiftTheMultiplier() {
        assertEquals(1.5, EncounterMath.multiplier(1, 2), "fewer than 3 characters: one step harder");
        assertEquals(0.5, EncounterMath.multiplier(1, 6), "6 or more: one step easier");
        assertEquals(5.0, EncounterMath.multiplier(15, 2), "capped at the top of the table");
        assertEquals(0.5, EncounterMath.multiplier(1, 8), "capped at the bottom of the table");
    }

    @Test
    void thresholdsScaleWithPartySize() {
        assertArrayEquals(new int[]{300, 600, 900, 1600}, EncounterMath.thresholds(3, 4));
        assertArrayEquals(new int[]{75, 150, 225, 400}, EncounterMath.thresholds(3, 1));
        assertArrayEquals(EncounterMath.thresholds(20, 4), EncounterMath.thresholds(99, 4), "level is clamped");
    }

    @Test
    void fourLevelThreesFacingFourGoblinsAndABoss() {
        // 4 goblins (50 XP) and an orc (100 XP): 300 XP, 5 monsters, x2 = 600 -> medium for four 3rd-level characters
        int adjusted = EncounterMath.adjustedXp(4 * 50 + 100, 5, 4);
        assertEquals(600, adjusted);
        assertEquals(Difficulty.MEDIUM, EncounterMath.difficulty(adjusted, 3, 4));
        assertEquals(Difficulty.TRIVIAL, EncounterMath.difficulty(100, 3, 4));
        assertEquals(Difficulty.EASY, EncounterMath.difficulty(300, 3, 4));
        assertEquals(Difficulty.HARD, EncounterMath.difficulty(900, 3, 4));
        assertEquals(Difficulty.DEADLY, EncounterMath.difficulty(1600, 3, 4));
    }

    @Test
    void encountersSaveReloadDuplicateAndDelete(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        Card goblin = store.createCard(store.project.root, new Card());
        Encounter e = store.createEncounter(store.project.root, "Cave ambush");
        e.entries.add(new Encounter.Entry(goblin.id, 4, Side.ENEMY, true));
        e.notes = "They attack from the ceiling.";
        store.markEncounterDirty(e);
        store.flush();

        assertTrue(store.isEncounter(e.id));
        assertFalse(store.isCard(e.id));
        TreeNode node = store.project.root.find(e.id);
        assertTrue(node.isEncounter());
        assertFalse(node.isBoard(), "an encounter is not a whiteboard");
        assertTrue(store.itemExists(e.id), "whiteboards can link to it");
        assertEquals(java.util.List.of(e.id), store.allEncounterIds());

        ProjectStore again = ProjectStore.open(tmp, "Camp");
        Encounter loaded = again.encounter(e.id);
        assertNotNull(loaded);
        assertEquals("Cave ambush", loaded.name);
        assertEquals("They attack from the ceiling.", loaded.notes);
        assertEquals(1, loaded.entries.size());
        assertEquals(4, loaded.entries.get(0).count);
        assertEquals(Side.ENEMY, loaded.entries.get(0).side);
        assertTrue(loaded.entries.get(0).group);

        again.renameNode(again.project.root.find(e.id), "Ceiling ambush");
        assertEquals("Ceiling ambush", again.encounter(e.id).name);

        Encounter copy = again.duplicateEncounter(again.project.root.find(e.id));
        assertNotEquals(e.id, copy.id);
        assertEquals("Ceiling ambush (copy)", copy.name);
        assertEquals(4, copy.entries.get(0).count);
        copy.entries.get(0).count = 9;
        assertEquals(4, again.encounter(e.id).entries.get(0).count, "the copy is independent");

        again.deleteNode(again.project.root.find(copy.id));
        assertNull(again.encounter(copy.id));
        assertFalse(again.itemExists(copy.id));
        again.flush();
        assertFalse(java.nio.file.Files.exists(tmp.resolve("Camp").resolve("encounters").resolve(copy.id + ".encounter")));
    }

    @Test
    void partyIsRememberedWithTheProject(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Party");
        assertEquals(4, store.project.partySize);
        assertEquals(3, store.project.partyLevel);
        store.project.partySize = 5;
        store.project.partyLevel = 7;
        store.markProjectDirty();
        store.flush();
        ProjectStore again = ProjectStore.open(tmp, "Party");
        assertEquals(5, again.project.partySize);
        assertEquals(7, again.project.partyLevel);
    }
}
