package dnd;

import dnd.text.RichText;
import dnd.text.RichText.Edit;
import dnd.text.RichText.Run;
import dnd.text.RichText.Span;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RichTextSpansTest {
    @Test
    void aSpanIsParsedAndStrippedFromThePlainText() {
        List<Run> runs = RichText.parse("Ask [Mira]{1} about it");
        assertEquals(3, runs.size());
        assertEquals("Ask ", runs.get(0).text());
        assertEquals(0, runs.get(0).ref());
        assertEquals("Mira", runs.get(1).text());
        assertEquals(1, runs.get(1).ref());
        assertEquals(" about it", runs.get(2).text());
        assertEquals("Ask Mira about it", RichText.plain("Ask [Mira]{1} about it"));
    }

    @Test
    void stylesCanSitInsideASpan() {
        List<Run> runs = RichText.parse("[**Mira**]{2}");
        assertEquals(1, runs.size());
        assertTrue(runs.get(0).bold());
        assertEquals(2, runs.get(0).ref());
        assertEquals("Mira", RichText.plain("[**Mira** and *Dan*]{2}").substring(0, 4));
    }

    @Test
    void bracketsThatAreNotCompleteSpansStayLiteral() {
        for (String s : new String[]{"see [1] and [x]", "[a]{x}", "[a]{0}", "[a] {1}", "[a]{1", "a]{1}", "[]{}", "[unclosed"}) {
            assertEquals(s, RichText.plain(s), s);
            assertTrue(RichText.spans(s).isEmpty(), s);
        }
        assertEquals("[a b", RichText.plain("[a [b]{1}"), "the outer bracket is text; the inner one is the span");
        assertEquals(1, RichText.spans("[a [b]{1}").size());
        assertEquals("b", RichText.parse("[a [b]{1}").get(1).text());
    }

    @Test
    void escapingKeepsBracketsLiteral() {
        for (String s : new String[]{"[x]{1}", "a [b] c", "5 * 3 \\ [", "**[a]{1}**"}) {
            assertEquals(s, RichText.plain(RichText.escape(s)), s);
            assertTrue(RichText.spans(RichText.escape(s)).isEmpty(), "escaped text never forms a span: " + s);
        }
    }

    @Test
    void markupSurvivesAParseAndWriteRoundTrip() {
        for (String m : new String[]{"Ask [Mira]{1} about it", "[**Mira**]{2} and [*Dan*]{3}", "x **bold [span]{4} bold** y",
                "[a]{1}[b]{2}[c]{1}", "\\[literal] then [real]{7}"}) {
            List<Run> once = RichText.parse(m);
            List<Run> twice = RichText.parse(RichText.toMarkup(once));
            assertEquals(once, twice, m);
        }
    }

    @Test
    void settingASpanOnAPlainRange() {
        Edit e = RichText.setSpanRef("Ask Mira about it", 4, 8, 3);
        assertEquals("Ask [Mira]{3} about it", e.markup());
        assertEquals("Mira", e.markup().substring(e.selStart(), e.selEnd()));
        assertEquals(List.of(new Span(4, 8, 3)), RichText.spans(e.markup()));
        assertEquals(Set.of(3), RichText.refsUsed(e.markup()));
        assertEquals(3, RichText.refAt(e.markup(), 5));
        assertEquals(0, RichText.refAt(e.markup(), 0));
        assertEquals(0, RichText.refAt(e.markup(), 99));
    }

    @Test
    void aSpanWrapsAroundExistingStyles() {
        assertEquals("Ask [**Mira**]{3} now", RichText.setSpanRef("Ask **Mira** now", 4, 8, 3).markup());
        assertEquals("[*a* b]{1}", RichText.setSpanRef("*a* b", 0, 3, 1).markup());
    }

    @Test
    void replacingPartOfAnExistingSpanSplitsIt() {
        String m = RichText.setSpanRef("[Mira Stonehelm]{1}", 5, 10, 2).markup();
        assertEquals("[Mira ]{1}[Stone]{2}[helm]{1}", m);
        assertEquals(List.of(new Span(0, 5, 1), new Span(5, 10, 2), new Span(10, 14, 1)), RichText.spans(m));
        assertEquals("Mira Stonehelm", RichText.plain(m));
    }

    @Test
    void takingTextOutOfASpan() {
        String m = RichText.setSpanRef("Ask [Mira]{3} about it", 4, 8, 0).markup();
        assertEquals("Ask Mira about it", m);
        assertTrue(RichText.refsUsed(m).isEmpty());
        // part of it only
        assertEquals("[Mi]{3}ra", RichText.setSpanRef("[Mira]{3}", 2, 4, 0).markup());
    }

    @Test
    void anEmptyRangeChangesNothing() {
        assertEquals("Ask Mira", RichText.setSpanRef("Ask Mira", 3, 3, 1).markup());
    }

    @Test
    void aTextBoxSelectionConvertsToPlainPositions() {
        String m = "Ask [**Mira**]{3} now";
        assertArrayEquals(new int[]{4, 8}, RichText.toPlainRange(m, 5, 13));
        assertArrayEquals(new int[]{0, 0}, RichText.toPlainRange(m, 0, 0));
        assertArrayEquals(new int[]{0, 12}, RichText.toPlainRange(m, 0, m.length()));
        assertArrayEquals(new int[]{4, 8}, RichText.toPlainRange(m, 13, 5), "either order");
    }

    @Test
    void boldAndItalicTogglesKeepSpans() {
        Edit e = RichText.toggle("Ask [Mira]{3} now", 5, 9, true);
        assertEquals("Ask [**Mira**]{3} now", e.markup());
        assertEquals(List.of(new Span(4, 8, 3)), RichText.spans(e.markup()));
        Edit back = RichText.toggle(e.markup(), e.selStart(), e.selEnd(), true);
        assertEquals("Ask [Mira]{3} now", back.markup());
    }

    @Test
    void spansAreListedInOrderWithTheirRanges() {
        List<Span> s = RichText.spans("[a]{1} b [cd]{2} [e]{1}");
        assertEquals(List.of(new Span(0, 1, 1), new Span(4, 6, 2), new Span(7, 8, 1)), s);
        assertEquals(Set.of(1, 2), RichText.refsUsed("[a]{1} b [cd]{2} [e]{1}"));
    }

    @Test
    void runsCarryTheReferenceAcrossLineBreaksAndWords() {
        List<Run> runs = RichText.parse("[two words\nand more]{5}!");
        assertEquals("two words\nand more", runs.get(0).text());
        assertEquals(5, runs.get(0).ref());
        assertEquals("!", runs.get(1).text());
        assertEquals("[ab]{1}", RichText.toMarkup(List.of(new Run("ab", false, false, 1))));
    }
}
