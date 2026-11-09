package dnd;

import dnd.model.Attack;
import dnd.model.Card;
import dnd.model.Encounter;
import dnd.model.Feature;
import dnd.search.SearchService;
import dnd.search.SearchService.Result;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchServiceTest {
    private static Result only(List<Result> r) {
        assertEquals(1, r.size(), r.toString());
        return r.get(0);
    }

    @Test
    void findsTextInsideACardsTraitsActionsAttacksAndNotes(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        Card c = new Card();
        c.name = "Mossback";
        c.traits.add(new Feature("Pack Tactics", "Advantage when an ally is next to the target."));
        c.actions.add(new Feature("Multiattack", "Makes two attacks."));
        c.reactions.add(new Feature("Parry", "Adds 2 to AC against one melee attack."));
        c.attacks.add(new Attack("Gnarled Club", 4, "1d8+2", "bludgeoning", "5 ft.", "Knocks the target prone."));
        c.notes = "Haunts the old mill road at night.";
        c.languages = "Sylvan";
        store.createCard(store.project.root, c);

        Result trait = only(SearchService.search(store, "pack tactics", 20));
        assertEquals("Mossback", trait.boardName);
        assertNull(trait.objectId);
        assertTrue(trait.snippet.startsWith("Card - trait Pack Tactics"), trait.snippet);

        assertTrue(only(SearchService.search(store, "next to the target", 20)).snippet.contains("trait Pack Tactics"));
        assertTrue(only(SearchService.search(store, "multiattack", 20)).snippet.contains("action Multiattack"));
        assertTrue(only(SearchService.search(store, "parry", 20)).snippet.contains("reaction Parry"));
        assertTrue(only(SearchService.search(store, "gnarled", 20)).snippet.contains("attack Gnarled Club"));
        assertTrue(only(SearchService.search(store, "prone", 20)).snippet.contains("attack Gnarled Club"));
        Result notes = only(SearchService.search(store, "mill road", 20));
        assertTrue(notes.snippet.contains("notes:") && notes.snippet.contains("mill road"), notes.snippet);
        assertTrue(only(SearchService.search(store, "sylvan", 20)).snippet.contains("languages"));
    }

    @Test
    void aCardWhoseNameMatchesIsListedOnceAsACard(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        Card c = new Card();
        c.name = "Goblin Boss";
        c.notes = "Not a goblin at all.";
        store.createCard(store.project.root, c);
        Result r = only(SearchService.search(store, "goblin", 20));
        assertEquals("Card", r.snippet);
    }

    @Test
    void findsEncountersByNameAndNotes(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        Encounter e = store.createEncounter(store.project.root, "Cave ambush");
        e.notes = "They attack from the ceiling, led by a one-eyed hobgoblin.";
        store.markEncounterDirty(e);

        Result byName = only(SearchService.search(store, "ambush", 20));
        assertEquals("Encounter", byName.snippet);
        Result byNotes = only(SearchService.search(store, "one-eyed", 20));
        assertEquals("Cave ambush", byNotes.boardName);
        assertTrue(byNotes.snippet.startsWith("Encounter - notes:") && byNotes.snippet.contains("one-eyed"), byNotes.snippet);
    }

    @Test
    void findsTextOnMapPinsAndRelationshipNotes(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        dnd.model.BattleMap m = store.createBattleMap(store.project.root, "Crypt", dnd.model.BattleMap.GridType.SQUARE, 10, 10, 50);
        m.pins.add(new dnd.model.MapPin(10, 10, "Entrance"));
        m.pins.add(new dnd.model.MapPin(60, 60, "Pressure plate here fires poison darts"));
        Result pin = only(SearchService.search(store, "poison darts", 20));
        assertEquals("Crypt", pin.boardName);
        assertTrue(pin.snippet.startsWith("Pin 2:"), pin.snippet);

        Card a = new Card();
        a.name = "Dan";
        Card b = new Card();
        b.name = "Mira";
        store.createCard(store.project.root, b);
        a.relations.add(new dnd.model.Relation(b.id, dnd.model.RelationType.RIVAL, "feud over the old mill"));
        store.createCard(store.project.root, a);
        Result rel = only(SearchService.search(store, "old mill", 20));
        assertEquals("Dan", rel.boardName);
        assertTrue(rel.snippet.contains("relationship:"), rel.snippet);
    }

    @Test
    void backlinksIncludeLinksOnWordsAndWholeObjectsButNotNotes(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        Card target = store.createCard(store.project.root, new Card());
        dnd.model.Board home = store.board(store.allBoardIds().get(0));
        dnd.model.Board other = store.createBoard(store.project.root, "Other");

        dnd.model.BoardObject spanLink = new dnd.model.BoardObject(dnd.model.BoardObject.Type.TEXT);
        spanLink.text = "Ask [Mira]{1}";
        spanLink.refs.put("1", new dnd.model.SpanRef(target.id, null));
        home.objects.add(spanLink);
        dnd.model.BoardObject spanTip = new dnd.model.BoardObject(dnd.model.BoardObject.Type.TEXT);
        spanTip.text = "See [Dan]{1}";
        spanTip.refs.put("1", new dnd.model.SpanRef(target.id, "Owes the DM a favour"));
        other.objects.add(spanTip);
        dnd.model.BoardObject whole = new dnd.model.BoardObject(dnd.model.BoardObject.Type.TEXT);
        whole.text = "Whole block";
        whole.link = target.id;
        other.objects.add(whole);
        dnd.model.BoardObject noteOnly = new dnd.model.BoardObject(dnd.model.BoardObject.Type.TEXT);
        noteOnly.text = "Mentions " + target.name + " in a note";
        noteOnly.note = "Not a reference to " + target.id;
        other.objects.add(noteOnly);
        dnd.model.BoardObject unrelated = new dnd.model.BoardObject(dnd.model.BoardObject.Type.TEXT);
        unrelated.text = "Nothing here";
        unrelated.refs.put("1", new dnd.model.SpanRef("somewhere-else", null));
        other.objects.add(unrelated);

        List<Result> back = SearchService.backlinks(store, target.id);
        assertEquals(3, back.size());
        assertTrue(back.stream().anyMatch(r -> r.objectId.equals(spanLink.id)));
        assertTrue(back.stream().anyMatch(r -> r.objectId.equals(spanTip.id)));
        assertTrue(back.stream().anyMatch(r -> r.objectId.equals(whole.id)));
        assertFalse(back.stream().anyMatch(r -> r.objectId.equals(unrelated.id)));
        assertFalse(back.stream().anyMatch(r -> r.objectId.equals(noteOnly.id)));
    }

    @Test
    void searchFindsTextInNotesOnWordsAndWholeObjects(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        dnd.model.Board home = store.board(store.allBoardIds().get(0));
        dnd.model.BoardObject words = new dnd.model.BoardObject(dnd.model.BoardObject.Type.TEXT);
        words.text = "The [innkeeper]{1} waits";
        words.refs.put("1", new dnd.model.SpanRef(null, "Secretly a **vampire** named Orlo"));
        home.objects.add(words);
        dnd.model.BoardObject whole = new dnd.model.BoardObject(dnd.model.BoardObject.Type.TEXT);
        whole.text = "Cellar door";
        whole.note = "Locked, the key is under the *third* barrel";
        home.objects.add(whole);

        Result a = only(SearchService.search(store, "vampire named", 10));
        assertEquals(words.id, a.objectId);
        assertTrue(a.snippet.startsWith("note: ") && a.snippet.contains("vampire"), a.snippet);
        assertFalse(a.snippet.contains("**"), "markup should be hidden: " + a.snippet);
        Result b = only(SearchService.search(store, "third barrel", 10));
        assertEquals(whole.id, b.objectId);
    }

    @Test
    void oldTooltipsLoadAsLinksAndNeverComeBack() {
        String json = "{\"id\":\"b1\",\"name\":\"Old\",\"objects\":[{\"id\":\"o1\",\"type\":\"TEXT\",\"text\":\"See [Dan]{1} and more\","
                + "\"tip\":\"card-9\",\"refs\":{\"1\":{\"tip\":\"card-7\"},\"2\":{\"link\":\"card-1\",\"tip\":\"card-2\"}}}]}";
        dnd.model.Board b = dnd.storage.Json.read(json, dnd.model.Board.class);
        dnd.model.BoardObject o = b.objects.get(0);
        assertEquals("card-9", o.link);
        assertEquals("card-7", o.refs.get("1").link);
        assertEquals("card-1", o.refs.get("2").link, "an existing link wins over the old tooltip");
        assertNull(o.note);
        String out = dnd.storage.Json.write(b);
        assertFalse(out.contains("\"tip\""), out);
    }

    @Test
    void notesRoundTripAndEmptySpansAreDetected() {
        dnd.model.SpanRef r = new dnd.model.SpanRef(null, "  ");
        assertFalse(r.hasNote());
        assertTrue(r.isEmpty());
        r.note = "Hi";
        assertTrue(r.hasNote());
        assertFalse(r.isEmpty());
        dnd.model.BoardObject o = new dnd.model.BoardObject(dnd.model.BoardObject.Type.STICKY);
        o.note = "Remember **this**";
        o.refs.put("1", r);
        dnd.model.BoardObject back = dnd.storage.Json.read(dnd.storage.Json.write(o), dnd.model.BoardObject.class);
        assertEquals("Remember **this**", back.note);
        assertEquals("Hi", back.refs.get("1").note);
        assertEquals("Remember **this**", o.copy().note);
        assertEquals("Hi", o.copy().refs.get("1").note);
    }

    @Test
    void searchIgnoresSpanMarkers(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        dnd.model.Board home = store.board(store.allBoardIds().get(0));
        dnd.model.BoardObject o = new dnd.model.BoardObject(dnd.model.BoardObject.Type.TEXT);
        o.text = "Talk to [**Mira** Stonehelm]{3} tonight";
        home.objects.add(o);
        Result r = only(SearchService.search(store, "mira stonehelm", 10));
        assertEquals(o.id, r.objectId);
        assertEquals("Talk to Mira Stonehelm tonight", r.snippet);
        assertTrue(SearchService.search(store, "{3}", 10).isEmpty());
    }

    @Test
    void respectsTheLimitAndIgnoresBlankQueries(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        for (int i = 0; i < 10; i++) {
            Card c = new Card();
            c.name = "Card " + i;
            c.notes = "needle";
            store.createCard(store.project.root, c);
        }
        assertEquals(4, SearchService.search(store, "needle", 4).size());
        assertTrue(SearchService.search(store, "   ", 10).isEmpty());
        assertTrue(SearchService.search(store, null, 10).isEmpty());
    }

    @Test
    void excerptsShowTheTextAroundTheMatch() {
        String s = "A very long sentence about a lantern that is hung from the old oak at the end of the lane.";
        String e = SearchService.excerpt(s, "lantern");
        assertTrue(e.contains("lantern"));
        assertTrue(e.length() < s.length());
        assertTrue(e.startsWith("..."));
    }
}