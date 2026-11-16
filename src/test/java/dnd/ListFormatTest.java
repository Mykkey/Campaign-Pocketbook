package dnd;

import dnd.text.ListFormat;
import dnd.text.ListFormat.Edit;
import dnd.text.ListFormat.Kind;
import dnd.text.ListFormat.Marker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ListFormatTest {
    private static final ListFormat.Scheme OUTLINE = ListFormat.scheme("outline");

    /** Presses Enter at the end of the text and returns the new text. */
    private static String enter(String text) {
        Edit e = ListFormat.enter(text, text.length(), OUTLINE);
        return e == null ? null : e.text();
    }

    @Test
    void numbersCountUpWhateverTheirStyle() {
        assertEquals("1. one\n2. ", enter("1. one"));
        assertEquals("1) one\n2) ", enter("1) one"));
        assertEquals("(1) one\n(2) ", enter("(1) one"));
        assertEquals("9. nine\n10. ", enter("9. nine"));
        assertEquals("a) alpha\nb) ", enter("a) alpha"));
        assertEquals("A. alpha\nB. ", enter("A. alpha"));
        assertEquals("(a) alpha\n(b) ", enter("(a) alpha"));
        assertEquals("i) first\nii) ", enter("i) first"));
        assertEquals("I. first\nII. ", enter("I. first"));
        assertEquals("iii) third\niv) ", enter("iii) third"));
        assertEquals("ix) nine\nx) ", enter("ix) nine"));
        assertEquals("(i) one\n(ii) ", enter("(i) one"));
    }

    @Test
    void aSingleLetterThatIsAlsoANumeralFollowsTheItemAbove() {
        assertEquals("a) a\nb) b\nc) c\nd) ", enter("a) a\nb) b\nc) c"), "c after b is the letter");
        assertEquals("h) h\ni) i\nj) ", enter("h) h\ni) i"), "i after h is the letter");
        assertEquals("i) i\nii) ii\niii) iii\niv) ", enter("i) i\nii) ii\niii) iii"));
        assertEquals("iv) four\nv) five\nvi) ", enter("iv) four\nv) five"), "v after iv is the numeral");
        assertEquals("u) u\nv) v\nw) ", enter("u) u\nv) v"), "v after u is the letter");
        assertEquals("i) one\nii) ", enter("i) one"), "an i on its own starts numerals");
        assertEquals("w) w\nx) x\ny) ", enter("w) w\nx) x"), "x after w is the letter");
    }

    @Test
    void bulletsOfAnyKindContinueTheSameBullet() {
        for (String b : new String[]{"•", "-", "*", "–", "◦", "▪", "→", "✓", "+"}) {
            assertEquals(b + " item\n" + b + " ", enter(b + " item"), b);
        }
    }

    @Test
    void indentIsKeptAndTheNewItemSplitsTheTextAtTheCaret() {
        assertEquals("    a) deep\n    b) ", enter("    a) deep"));
        Edit mid = ListFormat.enter("1. split here", 8, OUTLINE); // after "1. split"
        assertEquals("1. split\n2.  here", mid.text());
        assertEquals("1. split\n2. ".length(), mid.selStart(), "the caret is after the new marker");
    }

    @Test
    void enterOnAnEmptyItemEndsTheListOrGoesOneLevelOut() {
        assertEquals("1. one\n", enter("1. one\n2. "));
        Edit out = ListFormat.enter("1. one\n    a) two\n    b) ", "1. one\n    a) two\n    b) ".length(), OUTLINE);
        assertEquals("1. one\n    a) two\n2. ", out.text(), "an empty nested item goes out a level and takes the next number");
        assertEquals(out.text().length(), out.selStart());
    }

    @Test
    void enterIsLeftAloneOutsideAListOrInsideTheMarker() {
        assertNull(enter("just text"));
        assertNull(enter("e.g. not a list"));
        assertNull(enter("Smith and Jones"));
        assertNull(enter("12345 no dot"));
        assertNull(ListFormat.enter("1. item", 1, OUTLINE), "the caret is inside the marker");
        assertNull(ListFormat.enter("1. item", 0, OUTLINE));
        assertNull(enter("(1. odd"));
        assertNull(enter("-negative"), "a dash needs a space after it");
    }

    @Test
    void tabIndentsAndChangesTheStyleByLevelThenNumbersCarryOn() {
        String text = "1. one\n2. two\n3. three";
        Edit in = ListFormat.shift(text, text.length(), text.length(), false, OUTLINE);
        assertEquals("1. one\n2. two\n    a) three", in.text(), "level 1 of the outline is a)");
        String more = in.text() + "\n    b) four";
        Edit deeper = ListFormat.shift(more, more.length(), more.length(), false, OUTLINE);
        assertEquals("1. one\n2. two\n    a) three\n        i) four", deeper.text(), "level 2 is i)");
        // a sibling at the same level carries on from the one above
        String sib = "1. one\n    a) x\n2. two\n3. three";
        Edit into = ListFormat.shift(sib, sib.length(), sib.length(), false, OUTLINE);
        assertEquals("1. one\n    a) x\n2. two\n    a) three", into.text(), "restarts under a new parent");
        String sib2 = "1. one\n    a) x\n    b) y\n2. two";
        assertNull(ListFormat.shift(sib2, sib2.length(), sib2.length(), true, OUTLINE), "an item already at the top level has nowhere to go out");
        String lvl = "1. one\n    a) x\n    b) y";
        Edit out = ListFormat.shift(lvl, lvl.length(), lvl.length(), true, OUTLINE);
        assertEquals("1. one\n    a) x\n2. y", out.text(), "outdenting takes the next number of the level above");
    }

    @Test
    void bulletsChangeShapeWhenIndentedAndTheSameStyleSchemeJustMoves() {
        ListFormat.Scheme bullets = ListFormat.scheme("bullets");
        Edit in = ListFormat.shift("• one\n• two", 8, 8, false, bullets);
        assertEquals("• one\n    ◦ two", in.text());
        Edit deeper = ListFormat.shift(in.text(), in.text().length(), in.text().length(), false, bullets);
        assertEquals("• one\n        ▪ two", deeper.text());
        ListFormat.Scheme same = ListFormat.scheme("same");
        assertEquals("1) one\n    2) two", ListFormat.shift("1) one\n2) two", 12, 12, false, same).text(), "keep the style typed");
    }

    @Test
    void tabOnSeveralSelectedItemsMovesThemAll() {
        String text = "1. a\n2. b\n3. c\n4. d";
        Edit e = ListFormat.shift(text, 5, 14, false, OUTLINE); // lines 2 and 3
        assertEquals("1. a\n    a) b\n    b) c\n4. d", e.text());
    }

    @Test
    void tabOutsideAListPutsSpacesInAndShiftTabTakesThemOut() {
        Edit in = ListFormat.shift("hello", 0, 0, false, OUTLINE);
        assertEquals("    hello", in.text());
        assertEquals(4, in.selStart());
        Edit out = ListFormat.shift("    hello", 6, 6, true, OUTLINE);
        assertEquals("hello", out.text());
        assertEquals(2, out.selStart());
        assertNull(ListFormat.shift("hello", 2, 2, true, OUTLINE), "nothing to take out");
    }

    @Test
    void linesCanBeTurnedIntoAListAndBack() {
        ListFormat.Style bullet = ListFormat.BULLETS.get(0);
        String text = "apples\npears\n\nplums";
        Edit made = ListFormat.toggle(text, 0, text.length(), bullet);
        assertEquals("• apples\n• pears\n\n• plums", made.text(), "blank lines are skipped");
        Edit off = ListFormat.toggle(made.text(), 0, made.text().length(), bullet);
        assertEquals(text, off.text(), "a second press takes the bullets off again");

        Edit numbered = ListFormat.toggle("one\ntwo\nthree", 0, 13, ListFormat.NUMBER_DOT);
        assertEquals("1. one\n2. two\n3. three", numbered.text());
        Edit swapped = ListFormat.toggle(numbered.text(), 0, numbered.text().length(), bullet);
        assertEquals("• one\n• two\n• three", swapped.text(), "a different list style replaces the old markers");
        Edit caretLine = ListFormat.toggle("first\nsecond", 8, 8, ListFormat.NUMBER_PAREN);
        assertEquals("first\n1) second", caretLine.text(), "with no selection only the caret's line changes");
        Edit indented = ListFormat.toggle("    deep line", 2, 2, bullet);
        assertEquals("    • deep line", indented.text(), "the indent is kept");
    }

    @Test
    void romanNumeralsAndLettersAreCorrect() {
        assertEquals("iv", ListFormat.toRoman(4));
        assertEquals("xlii", ListFormat.toRoman(42));
        assertEquals("mcmxciv", ListFormat.toRoman(1994));
        assertEquals(4, ListFormat.fromRoman("IV"));
        assertEquals(1994, ListFormat.fromRoman("mcmxciv"));
        assertEquals(-1, ListFormat.fromRoman("iiii"));
        assertEquals(-1, ListFormat.fromRoman("abc"));
        assertEquals("a", ListFormat.NUMBER_DOT.marker(1).isEmpty() ? "" : ListFormat.LOWER_DOT.marker(1).replace(".", ""));
        assertEquals("z.", ListFormat.LOWER_DOT.marker(26));
        assertEquals("aa.", ListFormat.LOWER_DOT.marker(27));
        assertEquals("(iii)", ListFormat.ROMAN_LOWER_BOTH.marker(3));
        assertEquals("XIV)", ListFormat.ROMAN_UPPER_PAREN.marker(14));
    }

    @Test
    void parsingTellsKindValueAndWhereTheTextStarts() {
        Marker m = ListFormat.parse("   (c) the text");
        assertEquals(3, m.indent());
        assertEquals(Kind.ALPHA_LOWER, m.style().kind());
        assertEquals(3, m.value());
        assertEquals("the text", m.body());
        assertEquals("   (c) ".length(), m.prefixLength());
        assertEquals(Kind.BULLET, ListFormat.parse("• point").style().kind());
        assertEquals("• ".length(), ListFormat.prefixLength("• point"));
        assertEquals(0, ListFormat.prefixLength("plain"));
        assertNull(ListFormat.parse("A.B. text"));
        assertNull(ListFormat.parse("no. 5"));
        assertEquals(List.of("1.", "", "(a)"), ListFormat.markers("1. x\nplain\n(a) y"));
        assertTrue(ListFormat.inList("1. x\nplain", 2));
        assertFalse(ListFormat.inList("1. x\nplain", 7));
        assertEquals("outline", ListFormat.scheme("nonsense").id(), "an unknown scheme gives the first");
    }
}
