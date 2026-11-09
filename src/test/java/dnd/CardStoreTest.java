package dnd;

import dnd.model.Attack;
import dnd.model.Card;
import dnd.model.Feature;
import dnd.model.TreeNode;
import dnd.rules.CardTemplates;
import dnd.rules.Dice;
import dnd.search.SearchService;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CardStoreTest {

    @Test
    void abilityModifiersFollowThe5eFormula() {
        assertEquals(-5, Card.mod(1));
        assertEquals(-1, Card.mod(8));
        assertEquals(0, Card.mod(10));
        assertEquals(0, Card.mod(11));
        assertEquals(2, Card.mod(14));
        assertEquals(3, Card.mod(16));
        assertEquals(5, Card.mod(20));
        Card c = new Card();
        c.dex = 14;
        assertEquals(2, c.initiative());
        c.initiativeBonus = 5;
        assertEquals(5, c.initiative());
    }

    @Test
    void cardRoundTripsThroughDisk(@TempDir Path tmp) throws Exception {
        ProjectStore s = ProjectStore.create(tmp, "P");
        Card c = CardTemplates.create("goblin", null);
        c.traits.add(new Feature("Extra", "Does a thing"));
        c.attacks.add(new Attack("Net", 4, "1d4", "bludgeoning", "5/15 ft.", ""));
        Card stored = s.createCard(s.project.root, c);
        stored.notes = "Leader of the Redfang gang";
        s.markCardDirty(stored);
        s.flush();

        assertTrue(Files.isRegularFile(tmp.resolve("P").resolve("cards").resolve(stored.id + ".card")));
        ProjectStore again = ProjectStore.open(tmp, "P");
        assertTrue(again.isCard(stored.id));
        assertTrue(again.itemExists(stored.id));
        assertFalse(again.boardExists(stored.id), "a card is not a whiteboard");
        assertFalse(again.isBattleMap(stored.id));
        Card loaded = again.card(stored.id);
        assertEquals("Goblin", loaded.name);
        assertEquals(15, loaded.ac);
        assertEquals(7, loaded.hpMax);
        assertEquals("Leader of the Redfang gang", loaded.notes);
        assertEquals(3, loaded.attacks.size());
        assertEquals("Net", loaded.attacks.get(2).name);
        assertEquals("Extra", loaded.traits.get(1).name);
        assertEquals(Card.Kind.MONSTER, loaded.kind);
    }

    @Test
    void duplicateRenameAndDelete(@TempDir Path tmp) throws Exception {
        ProjectStore s = ProjectStore.create(tmp, "P");
        TreeNode folder = s.createFolder(s.project.root, "Monsters");
        Card orc = s.createCard(folder, CardTemplates.create("orc", null));
        TreeNode node = s.project.root.find(orc.id);
        assertTrue(node.isCard());

        Card copy = s.duplicateCard(node);
        assertNotEquals(orc.id, copy.id);
        assertEquals("Orc (copy)", copy.name);
        assertSame(folder, s.project.root.parentOf(copy.id), "the copy sits next to the original");
        assertEquals(orc.hpMax, copy.hpMax);
        copy.hpMax = 99;
        assertEquals(15, orc.hpMax, "changing the copy must not change the original");

        s.renameNode(s.project.root.find(copy.id), "Orc Warchief");
        assertEquals("Orc Warchief", s.card(copy.id).name);
        assertEquals("Orc Warchief", s.itemName(copy.id));

        s.flush();
        Path copyFile = tmp.resolve("P").resolve("cards").resolve(copy.id + ".card");
        assertTrue(Files.exists(copyFile));
        s.deleteNode(folder);
        s.flush();
        assertFalse(Files.exists(copyFile));
        assertNull(s.card(orc.id));
        assertFalse(s.itemExists(orc.id));
    }

    @Test
    void everyStarterCardLoadsAndHasValidDice() {
        List<CardTemplates.Template> all = CardTemplates.list();
        assertTrue(all.size() >= 12, "2 blanks + 10 creatures, got " + all.size());
        int creatures = 0;
        for (CardTemplates.Template t : all) {
            Card c = CardTemplates.create(t.id, null);
            assertNotNull(c, t.id);
            assertFalse(c.name.isBlank(), t.id);
            assertTrue(c.ac > 0 && c.hpMax > 0, t.id + " needs AC and HP");
            if (t.id.startsWith("blank")) continue;
            creatures++;
            assertFalse(c.attacks.isEmpty(), t.id + " should list at least one attack");
            for (Attack a : c.attacks) {
                assertTrue(Dice.isValid(a.damage), t.id + " bad damage dice: " + a.damage);
                assertFalse(a.name.isBlank());
            }
            if (!c.hitDice.isBlank()) assertTrue(Dice.isValid(c.hitDice), t.id + " bad hit dice " + c.hitDice);
        }
        assertEquals(10, creatures);
    }

    @Test
    void starterCardsMatchKnownStatBlocks() {
        Card goblin = CardTemplates.create("goblin", null);
        assertEquals(15, goblin.ac);
        assertEquals(7, goblin.hpMax);
        assertEquals(2, goblin.initiative());
        assertEquals("+4 to hit, reach 5 ft., 5 (1d6+2) slashing", goblin.attacks.get(0).summary());
        assertEquals("+4 to hit, range 80/320 ft., 5 (1d6+2) piercing", goblin.attacks.get(1).summary());
        Card wolf = CardTemplates.create("wolf", "Dire Wolf");
        assertEquals("Dire Wolf", wolf.name);
        assertEquals(7, wolf.attacks.get(0).averageDamage());
        assertTrue(wolf.attacks.get(0).summary().contains("knocked prone"));
        assertEquals(Card.Kind.PLAYER, CardTemplates.create(CardTemplates.BLANK_PLAYER, "Thorin").kind);
    }

    @Test
    void eachCreatedCardGetsItsOwnId() {
        assertNotEquals(CardTemplates.create("goblin", null).id, CardTemplates.create("goblin", null).id);
    }

    @Test
    void searchFindsCardsByName(@TempDir Path tmp) throws Exception {
        ProjectStore s = ProjectStore.create(tmp, "P");
        Card c = s.createCard(s.project.root, CardTemplates.create("skeleton", "Bone Knight"));
        List<SearchService.Result> r = SearchService.search(s, "bone", 10);
        assertEquals(1, r.size());
        assertEquals(c.id, r.get(0).boardId);
    }
}
