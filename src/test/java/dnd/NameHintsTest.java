package dnd;

import dnd.text.NameHints;
import dnd.text.NameHints.Entry;
import dnd.text.NameHints.Hint;
import dnd.text.RichText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NameHintsTest {
    private static List<Entry> idx(String... namesAndIds) {
        java.util.ArrayList<Entry> all = new java.util.ArrayList<>();
        for (int i = 0; i < namesAndIds.length; i += 2) all.add(new Entry(namesAndIds[i], namesAndIds[i + 1]));
        return NameHints.index(all);
    }

    private static List<Hint> find(String text, List<Entry> index, String... dismissed) {
        return NameHints.find(RichText.plain(text), RichText.spans(text), index, Set.of(dismissed));
    }

    @Test
    void findsWholeWordNamesIgnoringCase() {
        List<Hint> h = find("Ask mira about Dan's debt. MIRA agrees.", idx("Mira", "m", "Dan", "d"));
        assertEquals(3, h.size());
        assertEquals("mira", h.get(0).name());
        assertEquals(4, h.get(0).start());
        assertEquals(8, h.get(0).end());
        assertEquals("m", h.get(0).itemId());
        assertEquals("Dan", h.get(1).name());
        assertEquals("d", h.get(1).itemId());
        assertEquals("MIRA", h.get(2).name());
    }

    @Test
    void namesInsideBiggerWordsDoNotMatch() {
        assertTrue(find("Miranda and Amira and Mirabel", idx("Mira", "m")).isEmpty());
        assertEquals(1, find("Mira, Mira2 and (Mira)", idx("Mira", "m")).size() - 1, "Mira2 is a different word");
        assertEquals(2, find("Mira, Mira2 and (Mira)", idx("Mira", "m")).size());
    }

    @Test
    void theLongestNameWins() {
        List<Hint> h = find("Mira Stonehelm waved. Mira waved back.", idx("Mira", "short", "Mira Stonehelm", "long"));
        assertEquals(2, h.size());
        assertEquals("long", h.get(0).itemId());
        assertEquals("Mira Stonehelm", h.get(0).name());
        assertEquals("short", h.get(1).itemId());
    }

    @Test
    void tooShortNamesAndDuplicatesAreLeftOut() {
        List<Entry> index = idx("Al", "a", "Bo", "b", "Dan", "d", "dan", "d2", "  ", "x");
        assertEquals(1, index.size());
        assertEquals("d", index.get(0).itemId(), "the first of two names that differ only by case");
        assertTrue(find("Al and Bo", index).isEmpty());
    }

    @Test
    void namesAlreadyInASpanAreLeftAlone() {
        String text = "Ask [Mira]{1} about Mira";
        List<Hint> h = find(text, idx("Mira", "m"));
        assertEquals(1, h.size());
        assertEquals(RichText.plain(text).lastIndexOf("Mira"), h.get(0).start());
        // part of a bigger span
        assertTrue(find("[Mira Stonehelm]{1}", idx("Mira", "m")).isEmpty());
    }

    @Test
    void dismissedNamesAreSkippedAnywhereInThatText() {
        List<Entry> index = idx("Mira", "m", "Dan", "d");
        assertEquals(1, find("Mira met Dan and Mira", index, "mira").size());
        assertEquals("Dan", find("Mira met Dan and Mira", index, "mira").get(0).name());
        assertTrue(find("Mira", index, "mira").isEmpty());
    }

    @Test
    void positionsAreInPlainTextSoMarkupDoesNotShiftThem() {
        String text = "**Big** news: Mira is *here*";
        List<Hint> h = find(text, idx("Mira", "m"));
        assertEquals(1, h.size());
        String plain = RichText.plain(text);
        assertEquals("Mira", plain.substring(h.get(0).start(), h.get(0).end()));
        // and the hint can become a span
        String linked = RichText.setSpanRef(text, h.get(0).start(), h.get(0).end(), 1).markup();
        assertEquals("**Big** news: [Mira]{1} is *here*", linked);
        assertTrue(find(linked, idx("Mira", "m")).isEmpty(), "once linked it is no longer offered");
    }

    @Test
    void namesWithSpacesAndPunctuationWork() {
        List<Hint> h = find("Ask Mira Stonehelm, then Sir Bram-the-Bold!", idx("Mira Stonehelm", "a", "Bram-the-Bold", "b"));
        assertEquals(2, h.size());
        assertEquals("Mira Stonehelm", h.get(0).name());
        assertEquals("Bram-the-Bold", h.get(1).name());
    }

    @Test
    void emptyInputsGiveNoHints() {
        assertTrue(find("", idx("Mira", "m")).isEmpty());
        assertTrue(find("Mira", List.of()).isEmpty());
        assertTrue(NameHints.find(null, List.of(), idx("Mira", "m"), null).isEmpty());
    }

    @Test
    void manyOccurrencesAreAllFound() {
        List<Hint> h = find("Dan Dan Dan", idx("Dan", "d"));
        assertEquals(3, h.size());
        assertEquals(List.of(0, 4, 8), h.stream().map(Hint::start).toList());
    }
}