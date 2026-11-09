package dnd;

import dnd.model.Card;
import dnd.search.SearchService;
import dnd.storage.ProjectStore;
import dnd.text.NameHints;
import dnd.text.NameHints.Entry;
import dnd.text.NameHints.Hint;
import dnd.text.NameHints.ItemNames;
import dnd.text.RichText;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AliasesTest {
    private static List<Hint> find(String text, List<ItemNames> items, String... dismissed) {
        return NameHints.find(RichText.plain(text), RichText.spans(text), NameHints.index(NameHints.entriesFor(items)), Set.of(dismissed));
    }

    @Test
    void anAliasIsOfferedAndPointsAtTheSameCard() {
        List<ItemNames> items = List.of(new ItemNames("jasper-card", "Jasper Egan", List.of("Jasper", "Jas", "Captain Egan")));
        List<Hint> h = find("Jasper arrived, and Captain Egan nodded. Ask Jas. Then Jasper Egan spoke.", items);
        assertEquals(4, h.size());
        for (Hint x : h) assertEquals("jasper-card", x.itemId());
        assertEquals("Jasper", h.get(0).name());
        assertEquals("Captain Egan", h.get(1).name());
        assertEquals("Jas", h.get(2).name());
        assertEquals("Jasper Egan", h.get(3).name(), "the full name still beats the shorter alias");
    }

    @Test
    void withoutAnAliasTheFirstNameAloneIsNotOffered() {
        assertTrue(find("Jasper arrived", List.of(new ItemNames("c", "Jasper Egan", List.of()))).isEmpty());
        assertTrue(find("Jasper arrived", List.of(new ItemNames("c", "Jasper Egan", null))).isEmpty());
    }

    @Test
    void aRealNameBeatsAnAliasThatClashesWithIt() {
        List<ItemNames> items = List.of(
                new ItemNames("a", "Dan Ironbraid", List.of("Dan")),
                new ItemNames("b", "Dan", List.of()));
        List<Hint> h = find("Dan came", items);
        assertEquals(1, h.size());
        assertEquals("b", h.get(0).itemId(), "the card actually called Dan keeps the name");
    }

    @Test
    void shortOrBlankAliasesAreIgnoredAndAliasesCanBeDismissed() {
        List<ItemNames> items = List.of(new ItemNames("c", "Jasper Egan", List.of("J", " ", "Jasper")));
        assertEquals(1, find("J and Jasper", items).size());
        assertTrue(find("Jasper", items, "jasper").isEmpty());
    }

    @Test
    void twoLetterAliasesLikeInitialsWorkButTwoLetterRealNamesStillDoNot() {
        List<ItemNames> items = List.of(new ItemNames("c", "Jasper Egan", List.of("JE")), new ItemNames("d", "Al", List.of()));
        List<Hint> h = find("JE nodded at je and Al", items);
        assertEquals(2, h.size());
        assertEquals("c", h.get(0).itemId());
        assertEquals("c", h.get(1).itemId());
    }

    @Test
    void aliasesAreSavedWithTheCardButNotCopied(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        Card c = new Card();
        c.name = "Jasper Egan";
        c.aliases.add("Jasper");
        c.aliases.add("Captain Egan");
        store.createCard(store.project.root, c);
        store.flush();

        Card loaded = ProjectStore.open(tmp, "Camp").card(c.id);
        assertEquals(List.of("Jasper", "Captain Egan"), loaded.aliases);
        assertTrue(c.copy().aliases.isEmpty(), "a duplicate is a different person");
        Card sync = new Card();
        sync.name = "Other";
        c.copyFrom(sync);
        assertEquals(List.of("Jasper", "Captain Egan"), c.aliases, "syncing stats from the library keeps the project''s aliases");
    }

    @Test
    void searchFindsACardByItsAlias(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        Card c = new Card();
        c.name = "Jasper Egan";
        c.aliases.add("Captain Egan");
        store.createCard(store.project.root, c);
        var r = SearchService.search(store, "captain", 10);
        assertEquals(1, r.size());
        assertEquals("Jasper Egan", r.get(0).boardName);
        assertTrue(r.get(0).snippet.contains("also known as: Captain Egan"), r.get(0).snippet);
    }

    @Test
    void namesVersionMovesWhenAnAliasChangesSoHintsRefresh(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        Card c = store.createCard(store.project.root, new Card());
        int before = store.namesVersion();
        c.aliases.add("Nick");
        store.markCardDirty(c);
        assertTrue(store.namesVersion() > before);
    }
}