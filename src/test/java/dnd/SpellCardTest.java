package dnd;

import dnd.model.Spell;
import dnd.model.TreeNode;
import dnd.search.SearchService;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpellCardTest {
    private static Spell fireball() {
        Spell s = new Spell();
        s.name = "Fireball";
        s.level = 3;
        s.school = "Evocation";
        s.castingTime = "1 action";
        s.range = "150 feet";
        s.components = "V, S, M";
        s.material = "A tiny ball of bat guano and sulfur.";
        s.duration = "Instantaneous";
        s.description = "A bright streak flashes to a point you choose.\n\nEach creature in the area takes fire damage.";
        s.higherLevel = "The damage increases by 1d6 for each slot level above 3rd.";
        s.classes.add("Sorcerer");
        s.classes.add("Wizard");
        s.notes = "House rule: no friendly fire at my table.";
        return s;
    }

    @Test
    void aSpellCardIsItsOwnItemTypeAndSurvivesReopening(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        Spell s = store.createSpell(store.project.root, fireball());
        store.flush();

        assertTrue(store.isSpell(s.id));
        assertFalse(store.isCard(s.id));
        assertFalse(store.isEncounter(s.id));
        TreeNode node = store.project.root.find(s.id);
        assertTrue(node.isSpell());
        assertFalse(node.isBoard(), "a spell card is not a whiteboard");
        assertTrue(store.itemExists(s.id), "it can be linked to");
        assertEquals(List.of(s.id), store.allSpellIds());
        assertTrue(Files.exists(tmp.resolve("Camp").resolve("spells").resolve(s.id + ".spell")));

        ProjectStore again = ProjectStore.open(tmp, "Camp");
        Spell loaded = again.spell(s.id);
        assertEquals("Fireball", loaded.name);
        assertEquals(3, loaded.level);
        assertEquals("V, S, M", loaded.components);
        assertEquals(List.of("Sorcerer", "Wizard"), loaded.classes);
        assertEquals("House rule: no friendly fire at my table.", loaded.notes);
        assertEquals(s.id, loaded.id);
    }

    @Test
    void renamingDuplicatingAndDeleting(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        Spell s = store.createSpell(store.project.root, fireball());
        store.renameNode(store.project.root.find(s.id), "Big Fireball");
        assertEquals("Big Fireball", store.spell(s.id).name);

        Spell copy = store.duplicateSpell(store.project.root.find(s.id));
        assertNotEquals(s.id, copy.id);
        assertEquals("Big Fireball (copy)", copy.name);
        assertEquals("Evocation", copy.school);
        copy.level = 5;
        assertEquals(3, store.spell(s.id).level, "the copy is independent");

        store.flush();
        store.deleteNode(store.project.root.find(copy.id));
        assertNull(store.spell(copy.id));
        assertFalse(store.itemExists(copy.id));
        store.flush();
        assertFalse(Files.exists(tmp.resolve("Camp").resolve("spells").resolve(copy.id + ".spell")));
        assertEquals(1, store.allSpellIds().size());
    }

    @Test
    void aSpellMadeFromTheLibraryIsRememberedSoItIsNotMadeTwice(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        assertNull(store.spellFromLibrary("srd-fireball"));
        assertNull(store.spellFromLibrary(null));
        Spell srd = fireball();
        srd.id = "srd-fireball";
        Spell card = srd.copy();
        card.libraryId = srd.id;
        store.createSpell(store.project.root, card);
        assertNotEquals("srd-fireball", card.id, "the project card gets its own id");
        assertEquals(card.id, store.spellFromLibrary("srd-fireball").id);

        Spell dup = store.duplicateSpell(store.project.root.find(card.id));
        assertNull(dup.libraryId, "a duplicate is a homebrew spell of its own");
        assertEquals(card.id, store.spellFromLibrary("srd-fireball").id);
    }

    @Test
    void theNamesVersionMovesWhenCardsOrSpellsChange(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        int v0 = store.namesVersion();
        Spell s = store.createSpell(store.project.root, fireball());
        int v1 = store.namesVersion();
        assertTrue(v1 > v0);
        store.renameNode(store.project.root.find(s.id), "Fire");
        assertTrue(store.namesVersion() > v1);
        int v2 = store.namesVersion();
        store.createCard(store.project.root, new dnd.model.Card());
        assertTrue(store.namesVersion() > v2);
    }

    @Test
    void spellCardsAreSearchable(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        store.createSpell(store.project.root, fireball());

        assertEquals("Spell", SearchService.search(store, "fireball", 10).get(0).snippet);
        var text = SearchService.search(store, "bright streak", 10);
        assertEquals(1, text.size());
        assertTrue(text.get(0).snippet.startsWith("Spell - text:"), text.get(0).snippet);
        assertTrue(SearchService.search(store, "sorcerer", 10).get(0).snippet.contains("class: Sorcerer"));
        assertTrue(SearchService.search(store, "bat guano", 10).get(0).snippet.contains("material"));
        assertTrue(SearchService.search(store, "friendly fire", 10).get(0).snippet.contains("notes"));
    }
}