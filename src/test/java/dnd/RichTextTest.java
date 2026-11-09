package dnd;

import dnd.text.RichText;
import dnd.text.RichText.Edit;
import dnd.text.RichText.Run;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RichTextTest {

    private static Run r(String t, boolean b, boolean i) { return new Run(t, b, i); }

    @Test
    void plainTextIsOneUnstyledRun() {
        assertEquals(List.of(r("Hello world", false, false)), RichText.parse("Hello world"));
        assertEquals(List.of(), RichText.parse(""));
        assertEquals(List.of(), RichText.parse(null));
    }

    @Test
    void boldItalicAndBothAreParsed() {
        assertEquals(List.of(r("a ", false, false), r("bold", true, false), r(" b", false, false)), RichText.parse("a **bold** b"));
        assertEquals(List.of(r("a ", false, false), r("slanted", false, true), r(" b", false, false)), RichText.parse("a *slanted* b"));
        assertEquals(List.of(r("both", true, true)), RichText.parse("***both***"));
        assertEquals(List.of(r("x", true, false), r("y", true, true), r("z", false, true)), RichText.parse("**x*y**z*"));
    }

    @Test
    void stylesCanSitInsideAWord() {
        assertEquals(List.of(r("un", false, false), r("believ", true, false), r("able", false, false)), RichText.parse("un**believ**able"));
    }

    @Test
    void multilineStylesAreKept() {
        List<Run> runs = RichText.parse("**line one\nline two** done");
        assertEquals(List.of(r("line one\nline two", true, false), r(" done", false, false)), runs);
    }

    @Test
    void anUnpairedMarkerIsShownAsTyped() {
        assertEquals("2 * 3 = 6", RichText.plain("2 * 3 = 6"));
        assertEquals(List.of(r("2 * 3 = 6", false, false)), RichText.parse("2 * 3 = 6"));
        assertEquals("**oops", RichText.plain("**oops"));
        // next to real italics, a literal asterisk is written escaped
        assertEquals("5*4 and this works", RichText.plain("5\\*4 and *this* works"));
        // a pair elsewhere still works while the odd one out is literal
        assertEquals(List.of(r("a*b ", false, false), r("c", false, true)), RichText.parse("a\\*b *c*"));
    }

    @Test
    void escapedMarkersAreLiteral() {
        assertEquals("a*b", RichText.plain("a\\*b"));
        assertEquals("a\\b", RichText.plain("a\\\\b"));
        assertEquals("keep \\n as is", RichText.plain("keep \\n as is"), "a backslash before other letters is untouched");
    }

    @Test
    void escapeRoundTripsAnyText() {
        for (String s : new String[]{"plain", "2*3", "a\\b", "**", "*\\*", "x * y ** z", ""}) {
            assertEquals(s, RichText.plain(RichText.escape(s)), s);
        }
    }

    @Test
    void toMarkupRebuildsWhatParseFound() {
        for (String s : new String[]{"plain", "a **bold** b", "a *it* b", "***both*** and", "un**believ**able", "x\\*y **z**"}) {
            assertEquals(RichText.parse(s), RichText.parse(RichText.toMarkup(RichText.parse(s))), s);
        }
        assertEquals("a **bold** b", RichText.toMarkup(List.of(r("a ", false, false), r("bold", true, false), r(" b", false, false))));
    }

    @Test
    void makingSelectedWordsBold() {
        String m = "the quick brown fox";
        Edit e = RichText.toggle(m, 4, 9, true);              // "quick"
        assertEquals("the **quick** brown fox", e.markup());
        assertEquals("quick", e.markup().substring(e.selStart(), e.selEnd()));
        assertEquals(List.of(r("the ", false, false), r("quick", true, false), r(" brown fox", false, false)), RichText.parse(e.markup()));
    }

    @Test
    void makingSelectedWordsItalic() {
        Edit e = RichText.toggle("the quick brown fox", 10, 15, false);
        assertEquals("the quick *brown* fox", e.markup());
    }

    @Test
    void togglingAgainRemovesTheStyle() {
        Edit on = RichText.toggle("a quick fox", 2, 7, true);
        assertEquals("a **quick** fox", on.markup());
        Edit off = RichText.toggle(on.markup(), on.selStart(), on.selEnd(), true);
        assertEquals("a quick fox", off.markup());
        assertEquals("quick", off.markup().substring(off.selStart(), off.selEnd()));
    }

    @Test
    void selectingTheMarkersAsWellStillTogglesOff() {
        // the user selects "**quick**" including the stars
        Edit off = RichText.toggle("a **quick** fox", 2, 11, true);
        assertEquals("a quick fox", off.markup());
    }

    @Test
    void boldAndItalicCombineAndAreIndependent() {
        Edit b = RichText.toggle("hello world", 0, 5, true);
        Edit both = RichText.toggle(b.markup(), b.selStart(), b.selEnd(), false);
        assertEquals(List.of(r("hello", true, true), r(" world", false, false)), RichText.parse(both.markup()));
        Edit noBold = RichText.toggle(both.markup(), both.selStart(), both.selEnd(), true);
        assertEquals(List.of(r("hello", false, true), r(" world", false, false)), RichText.parse(noBold.markup()));
    }

    @Test
    void aSelectionPartlyStyledBecomesFullyStyled() {
        Edit first = RichText.toggle("one two three", 0, 3, true);          // **one** two three
        // now select "one two" (part bold, part not) and apply bold
        String m = first.markup();
        int start = 0, end = m.indexOf("two") + 3;
        Edit all = RichText.toggle(m, start, end, true);
        assertEquals(List.of(r("one two", true, false), r(" three", false, false)), RichText.parse(all.markup()));
    }

    @Test
    void toggleAroundExistingStylesKeepsThem() {
        Edit e = RichText.toggle("a **bold** and plain", 11, 20, false);    // italicise " and plain" region
        List<Run> runs = RichText.parse(e.markup());
        assertEquals("a bold and plain", RichText.plain(e.markup()));
        assertTrue(runs.get(1).bold() && !runs.get(1).italic(), "bold word is untouched");
        assertTrue(runs.get(runs.size() - 1).italic());
    }

    @Test
    void emptySelectionDoesNothing() {
        Edit e = RichText.toggle("abc", 2, 2, true);
        assertEquals("abc", e.markup());
        assertEquals(2, e.selStart());
        assertEquals("abc", RichText.toggle("abc", 3, 3, false).markup());
        assertEquals("abc", RichText.toggle("", 0, 0, true).markup().isEmpty() ? "abc" : "x");
    }

    @Test
    void literalAsterisksSurviveATogglePass() {
        Edit e = RichText.toggle("2*3 is six", 0, 3, true);
        assertEquals("2*3 is six", RichText.plain(e.markup()));
        assertEquals(List.of(r("2*3", true, false), r(" is six", false, false)), RichText.parse(e.markup()));
    }

    @Test
    void searchSeesThePlainText() {
        assertEquals("the dragon sleeps", RichText.plain("the **dragon** *sleeps*"));
    }
}
