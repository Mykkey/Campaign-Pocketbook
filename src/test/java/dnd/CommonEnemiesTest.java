package dnd;

import dnd.library.CommonEnemies;
import dnd.model.Card;
import dnd.model.Combatant.Side;
import dnd.model.Encounter;
import dnd.model.TreeNode;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CommonEnemiesTest {
    @Test
    void firstUseFillsTheLibraryWithTheStarterCreatures(@TempDir Path tmp) {
        CommonEnemies lib = new CommonEnemies(tmp.resolve("lib"));
        assertEquals(CommonEnemies.bundledIds().size(), lib.list().size());
        assertTrue(CommonEnemies.bundledIds().size() >= 10);
        Card goblin = lib.get("goblin");
        assertNotNull(goblin);
        assertEquals("goblin", goblin.id);
        assertEquals("Goblin", goblin.name);
        assertTrue(lib.isBundled("goblin"));
    }

    @Test
    void editsAreSavedAndSurviveARestart(@TempDir Path tmp) {
        CommonEnemies lib = new CommonEnemies(tmp.resolve("lib"));
        Card goblin = lib.get("goblin");
        goblin.hpMax = 99;
        goblin.name = "Goblin Boss";
        lib.save(goblin);

        CommonEnemies again = new CommonEnemies(tmp.resolve("lib"));
        assertEquals(99, again.get("goblin").hpMax);
        assertEquals("Goblin Boss", again.get("goblin").name);
    }

    @Test
    void homebrewCreaturesGetTheirOwnIdAndAreSorted(@TempDir Path tmp) {
        CommonEnemies lib = new CommonEnemies(tmp.resolve("lib"));
        Card mine = new Card();
        mine.name = "Aaa Homebrew Horror";
        mine.hpMax = 120;
        Card saved = lib.add(mine);
        assertNotEquals(mine.id, saved.id);
        assertFalse(lib.isBundled(saved.id));
        assertEquals("Aaa Homebrew Horror", lib.list().get(0).name, "list is A to Z");

        CommonEnemies again = new CommonEnemies(tmp.resolve("lib"));
        assertEquals(120, again.get(saved.id).hpMax);
    }

    @Test
    void deletedCreaturesStayDeletedButCanBeRestored(@TempDir Path tmp) {
        CommonEnemies lib = new CommonEnemies(tmp.resolve("lib"));
        lib.delete("orc");
        assertNull(lib.get("orc"));

        CommonEnemies again = new CommonEnemies(tmp.resolve("lib"));
        assertNull(again.get("orc"), "does not come back on its own");
        again.get("goblin").hpMax = 55;
        again.save(again.get("goblin"));
        Card homebrew = new Card();
        homebrew.name = "Mine";
        Card kept = again.add(homebrew);

        again.restoreDefaults();
        assertNotNull(again.get("orc"));
        assertEquals(7, again.get("goblin").hpMax, "starter stats are put back");
        assertNotNull(again.get(kept.id), "homebrew creatures are not touched");
    }

    @Test
    void usingACommonEnemyMakesOneCardInTheProjectAndRefreshesIt(@TempDir Path tmp) throws Exception {
        CommonEnemies lib = new CommonEnemies(tmp.resolve("lib"));
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        int cardsBefore = store.allCardIds().size();

        Card first = store.useCommonEnemy(lib.get("goblin"));
        assertEquals("goblin", first.libraryId);
        assertEquals(cardsBefore + 1, store.allCardIds().size());
        TreeNode folder = store.project.root.parentOf(first.id);
        assertEquals(ProjectStore.COMMON_FOLDER, folder.name);

        Card goblin = lib.get("goblin");
        goblin.hpMax = 40;
        goblin.name = "Big Goblin";
        lib.save(goblin);
        Card second = store.useCommonEnemy(lib.get("goblin"));
        assertEquals(first.id, second.id, "same project card, not a duplicate");
        assertEquals(cardsBefore + 1, store.allCardIds().size());
        assertEquals(40, second.hpMax);
        assertEquals("Big Goblin", store.project.root.find(first.id).name, "tree name follows");

        store.useCommonEnemy(lib.get("orc"));
        assertEquals(cardsBefore + 2, store.allCardIds().size());
        long folders = store.project.root.children.stream().filter(n -> n.folder && ProjectStore.COMMON_FOLDER.equals(n.name)).count();
        assertEquals(1, folders, "one shared folder");

        Card copy = first.copy();
        assertNull(copy.libraryId, "a duplicate is its own card");
    }

    @Test
    void encounterEntriesRememberCommonEnemies(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        Encounter e = store.createEncounter(store.project.root, "Raid");
        e.entries.add(Encounter.Entry.common("goblin", 3, Side.ENEMY, true));
        store.markEncounterDirty(e);
        store.flush();

        Encounter loaded = ProjectStore.open(tmp, "Camp").encounter(e.id);
        Encounter.Entry en = loaded.entries.get(0);
        assertEquals("goblin", en.libraryId);
        assertNull(en.cardId);
        assertEquals(3, en.count);
        assertEquals("goblin", loaded.copy().entries.get(0).libraryId);
    }
}
