package dnd.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lists in the text of whiteboards: bullet points and numbered lines ("1.", "1)", "(1)", "a)", "A.", "i)", "I."). Understands
 * a list item as a line that starts with a marker and a space, continues the list when Enter is pressed, indents and outdents
 * items (changing the style of the marker with the level, as the chosen {@link Scheme} says), and turns lines into a list or
 * back into plain lines. Pure text handling, so it can be tested; the editor passes in its text and caret and gets the new text back.
 */
public final class ListFormat {
    private ListFormat() { }

    /** How far one level of indent moves a line, in spaces. */
    public static final int INDENT = 4;

    public enum Kind { BULLET, NUMBER, ALPHA_LOWER, ALPHA_UPPER, ROMAN_LOWER, ROMAN_UPPER }

    /**
     * The look of a marker: its kind, the bullet glyph (for bullets), and what goes before and after the number or letter
     * ({@code open} is '(' or 0, {@code close} is '.' or ')' or 0).
     */
    public record Style(String name, Kind kind, String glyph, char open, char close) {
        /** The marker for the item with this value (1 is the first), without the space after it. */
        public String marker(int value) {
            return format(kind, glyph, value, open, close);
        }

        boolean sameFamily(Style o) {
            return kind == o.kind && open == o.open && close == o.close && glyph.equals(o.glyph);
        }
    }

    /** A line read as a list item. */
    public record Marker(int indent, Style style, int value, int prefixLength, String body) { }

    /** The result of an edit: the new text and where the selection should be. */
    public record Edit(String text, int selStart, int selEnd) { }

    // ---------------------------------------------------------------- styles and schemes

    private static Style bullet(String name, String glyph) {
        return new Style(name, Kind.BULLET, glyph, (char) 0, (char) 0);
    }

    public static final List<Style> BULLETS = List.of(bullet("Bullet", "•"), bullet("Hollow bullet", "◦"), bullet("Square", "▪"),
            bullet("Dash", "-"), bullet("En dash", "–"), bullet("Arrow", "→"), bullet("Triangle", "▸"), bullet("Tick", "✓"),
            bullet("Star", "★"));

    public static final Style NUMBER_DOT = new Style("1.  2.  3.", Kind.NUMBER, "", (char) 0, '.');
    public static final Style NUMBER_PAREN = new Style("1)  2)  3)", Kind.NUMBER, "", (char) 0, ')');
    public static final Style NUMBER_BOTH = new Style("(1)  (2)  (3)", Kind.NUMBER, "", '(', ')');
    public static final Style LOWER_DOT = new Style("a.  b.  c.", Kind.ALPHA_LOWER, "", (char) 0, '.');
    public static final Style LOWER_PAREN = new Style("a)  b)  c)", Kind.ALPHA_LOWER, "", (char) 0, ')');
    public static final Style LOWER_BOTH = new Style("(a)  (b)  (c)", Kind.ALPHA_LOWER, "", '(', ')');
    public static final Style UPPER_DOT = new Style("A.  B.  C.", Kind.ALPHA_UPPER, "", (char) 0, '.');
    public static final Style UPPER_PAREN = new Style("A)  B)  C)", Kind.ALPHA_UPPER, "", (char) 0, ')');
    public static final Style ROMAN_LOWER_DOT = new Style("i.  ii.  iii.", Kind.ROMAN_LOWER, "", (char) 0, '.');
    public static final Style ROMAN_LOWER_PAREN = new Style("i)  ii)  iii)", Kind.ROMAN_LOWER, "", (char) 0, ')');
    public static final Style ROMAN_LOWER_BOTH = new Style("(i)  (ii)  (iii)", Kind.ROMAN_LOWER, "", '(', ')');
    public static final Style ROMAN_UPPER_DOT = new Style("I.  II.  III.", Kind.ROMAN_UPPER, "", (char) 0, '.');
    public static final Style ROMAN_UPPER_PAREN = new Style("I)  II)  III)", Kind.ROMAN_UPPER, "", (char) 0, ')');

    public static final List<Style> NUMBERED = List.of(NUMBER_DOT, NUMBER_PAREN, NUMBER_BOTH, LOWER_DOT, LOWER_PAREN, LOWER_BOTH, UPPER_DOT, UPPER_PAREN,
            ROMAN_LOWER_DOT, ROMAN_LOWER_PAREN, ROMAN_LOWER_BOTH, ROMAN_UPPER_DOT, ROMAN_UPPER_PAREN);

    /** What an indented list item looks like at each level: level 0 is the first style, level 1 the second, and the pattern repeats. */
    public record Scheme(String id, String name, List<Style> levels) {
        public Style level(int n) {
            return levels.get(Math.floorMod(n, levels.size()));
        }
    }

    public static final List<Scheme> SCHEMES = List.of(
            new Scheme("outline", "Outline:  1.  a)  i)  A.", List.of(NUMBER_DOT, LOWER_PAREN, ROMAN_LOWER_PAREN, UPPER_DOT)),
            new Scheme("numbers", "Numbers:  1.  a.  i.", List.of(NUMBER_DOT, LOWER_DOT, ROMAN_LOWER_DOT)),
            new Scheme("brackets", "Brackets:  1)  a)  i)", List.of(NUMBER_PAREN, LOWER_PAREN, ROMAN_LOWER_PAREN)),
            new Scheme("parens", "Parentheses:  (1)  (a)  (i)", List.of(NUMBER_BOTH, LOWER_BOTH, ROMAN_LOWER_BOTH)),
            new Scheme("bullets", "Bullets:  •  ◦  ▪", List.of(BULLETS.get(0), BULLETS.get(1), BULLETS.get(2))),
            new Scheme("dashes", "Dashes:  -  –  ·", List.of(BULLETS.get(3), BULLETS.get(4), bullet("Dot", "·"))),
            new Scheme("same", "Keep the style I typed", List.of()));

    public static Scheme scheme(String id) {
        for (Scheme s : SCHEMES) if (s.id().equals(id)) return s;
        return SCHEMES.get(0);
    }

    // ---------------------------------------------------------------- numbers, letters, roman numerals

    private static final String[] ROMAN_SYMBOLS = {"m", "cm", "d", "cd", "c", "xc", "l", "xl", "x", "ix", "v", "iv", "i"};
    private static final int[] ROMAN_VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};

    /** 4 becomes "iv" (lower case). */
    public static String toRoman(int n) {
        StringBuilder sb = new StringBuilder();
        int left = Math.max(1, Math.min(3999, n));
        for (int i = 0; i < ROMAN_VALUES.length; i++) {
            while (left >= ROMAN_VALUES[i]) {
                sb.append(ROMAN_SYMBOLS[i]);
                left -= ROMAN_VALUES[i];
            }
        }
        return sb.toString();
    }

    /** "iv" becomes 4; -1 if it is not a valid roman numeral. */
    public static int fromRoman(String s) {
        String r = s.toLowerCase(Locale.ROOT);
        if (r.isEmpty() || !r.matches("m{0,3}(cm|cd|d?c{0,3})(xc|xl|l?x{0,3})(ix|iv|v?i{0,3})")) return -1;
        int total = 0, at = 0;
        for (int i = 0; i < ROMAN_SYMBOLS.length; i++) {
            while (r.startsWith(ROMAN_SYMBOLS[i], at)) {
                total += ROMAN_VALUES[i];
                at += ROMAN_SYMBOLS[i].length();
            }
        }
        return at == r.length() ? total : -1;
    }

    /** 1 becomes "a", 26 "z", 27 "aa". */
    static String toLetters(int n) {
        StringBuilder sb = new StringBuilder();
        int v = Math.max(1, n);
        while (v > 0) {
            v--;
            sb.insert(0, (char) ('a' + v % 26));
            v /= 26;
        }
        return sb.toString();
    }

    private static int fromLetters(String s) {
        int v = 0;
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) v = v * 26 + (c - 'a' + 1);
        return v;
    }

    /** The marker text for a value: "3." or "(c)" or "iv)" or the bullet glyph. */
    public static String format(Kind kind, String glyph, int value, char open, char close) {
        if (kind == Kind.BULLET) return glyph;
        String core = switch (kind) {
            case NUMBER -> Integer.toString(value);
            case ALPHA_LOWER -> toLetters(value);
            case ALPHA_UPPER -> toLetters(value).toUpperCase(Locale.ROOT);
            case ROMAN_LOWER -> toRoman(value);
            default -> toRoman(value).toUpperCase(Locale.ROOT);
        };
        return (open == 0 ? "" : String.valueOf(open)) + core + (close == 0 ? "" : String.valueOf(close));
    }

    // ---------------------------------------------------------------- reading a line

    private static final Pattern ITEM = Pattern.compile("^( *)(?:([\u2022\u25E6\u25AA\u25B8\u25BA\u2192\u2713\u2714\u2605\u2013\u2014\u00B7*+\\-])"
            + "|(\\()?(\\d{1,4}|[a-zA-Z]{1,6})([.)]))( +)(.*)$");

    /** Reads a line as a list item, or returns null if it is not one. Single letters like "i" are taken as they stand (see the two-line version). */
    public static Marker parse(String line) {
        return parse(line, null);
    }

    /**
     * Reads a line as a list item. {@code previous} is the item above it (or null): it settles whether "c)" is the third letter or 100,
     * and whether "i)" follows "h)" (letters) or starts a list (roman).
     */
    public static Marker parse(String line, Marker previous) {
        Matcher m = ITEM.matcher(line);
        if (!m.matches()) return null;
        int indent = m.group(1).length();
        int prefix = indent + (m.group(2) != null ? 1 : (m.group(3) == null ? 0 : 1) + m.group(4).length() + 1) + m.group(6).length();
        if (m.group(2) != null) { // a bullet
            return new Marker(indent, new Style("", Kind.BULLET, m.group(2), (char) 0, (char) 0), 1, prefix, m.group(7));
        }
        char open = m.group(3) == null ? 0 : '(';
        char close = m.group(5).charAt(0);
        if (open != 0 && close != ')') return null; // "(1." is not a marker
        String word = m.group(4);
        Kind kind;
        int value;
        if (word.chars().allMatch(Character::isDigit)) {
            kind = Kind.NUMBER;
            value = Integer.parseInt(word);
        } else {
            boolean upper = Character.isUpperCase(word.charAt(0));
            if (!word.chars().allMatch(c -> Character.isUpperCase(c) == upper)) return null;
            int roman = fromRoman(word), letters = word.length() == 1 ? fromLetters(word) : -1;
            Kind romanKind = upper ? Kind.ROMAN_UPPER : Kind.ROMAN_LOWER, letterKind = upper ? Kind.ALPHA_UPPER : Kind.ALPHA_LOWER;
            if (roman < 0 && letters < 0) return null; // not a numeral and more than one letter: ordinary text
            if (roman >= 0 && letters >= 0) { // one letter that is also a numeral: the item above decides
                boolean prevAlpha = previous != null && previous.style().kind() == letterKind && previous.indent() == indent && previous.value() + 1 == letters;
                boolean prevRoman = previous != null && previous.style().kind() == romanKind && previous.indent() == indent && previous.value() + 1 == roman;
                if (prevAlpha) {
                    kind = letterKind;
                    value = letters;
                } else if (prevRoman || word.equalsIgnoreCase("i")) {
                    kind = romanKind;
                    value = roman;
                } else {
                    kind = letterKind;
                    value = letters;
                }
            } else if (roman >= 0) {
                kind = romanKind;
                value = roman;
            } else {
                kind = letterKind;
                value = letters;
            }
        }
        return new Marker(indent, new Style("", kind, "", open, close), value, prefix, m.group(7));
    }

    /** How many characters of the line come before its text (indent, marker and the space after); 0 if it is not a list item. */
    public static int prefixLength(String line) {
        Marker m = parse(line);
        return m == null ? 0 : m.prefixLength();
    }

    // ---------------------------------------------------------------- lines of a text

    private static int lineStart(String text, int pos) {
        return text.lastIndexOf('\n', Math.max(0, Math.min(pos, text.length())) - 1) + 1;
    }

    private static int lineEnd(String text, int pos) {
        int e = text.indexOf('\n', Math.max(0, Math.min(pos, text.length())));
        return e < 0 ? text.length() : e;
    }

    /** The list item on the line above (read with the few lines above that as context), or null. */
    private static Marker markerAbove(String text, int lineStart, int depth) {
        if (lineStart <= 0 || depth <= 0) return null;
        int prevStart = lineStart(text, lineStart - 1);
        String prev = text.substring(prevStart, lineStart - 1);
        return parse(prev, markerAbove(text, prevStart, depth - 1));
    }

    private static Marker markerOf(String text, int start) {
        return parse(text.substring(start, lineEnd(text, start)), markerAbove(text, start, 4));
    }

    private static String spaces(int n) {
        return " ".repeat(Math.max(0, n));
    }

    // ---------------------------------------------------------------- Enter

    /**
     * What pressing Enter does at the caret when it is in a list item: a new item with the next marker (the same indent) for the text
     * after the caret; or, on an empty item, the end of the list (one level out if it was indented). Returns null if the line is not
     * a list item, or the caret is within its marker, so the editor does its normal new line.
     */
    public static Edit enter(String text, int caret, Scheme scheme) {
        int start = lineStart(text, caret), end = lineEnd(text, caret);
        Marker m = markerOf(text, start);
        if (m == null || caret < start + m.prefixLength()) return null;
        if (m.body().isBlank()) return endItem(text, start, end, m, scheme);
        String nextMarker = m.style().marker(m.value() + 1);
        String prefix = spaces(m.indent()) + nextMarker + " ";
        String result = text.substring(0, caret) + "\n" + prefix + text.substring(caret);
        int c = caret + 1 + prefix.length();
        return new Edit(result, c, c);
    }

    /** Enter on an empty item: out one level if indented, otherwise the marker goes and the list is over. */
    private static Edit endItem(String text, int start, int end, Marker m, Scheme scheme) {
        if (m.indent() >= INDENT) {
            Edit out = shiftLines(text, start, start, true, scheme);
            if (out != null) {
                int c = lineEnd(out.text(), start);
                return new Edit(out.text(), c, c);
            }
        }
        String result = text.substring(0, start) + text.substring(end);
        return new Edit(result, start, start);
    }

    // ---------------------------------------------------------------- Tab and Shift+Tab

    /**
     * Indents (or, with {@code outdent}, outdents) the list items in the selection by one level, changing each marker to the style the
     * scheme gives that level (the numbers carry on from the item above at that level, or start again). Lines that are not list items
     * are left alone, except that with no list in the selection Tab puts spaces in and Shift+Tab takes them out. Returns null if nothing changes.
     */
    public static Edit shift(String text, int selStart, int selEnd, boolean outdent, Scheme scheme) {
        int from = lineStart(text, Math.min(selStart, selEnd)), to = lineEnd(text, Math.max(selStart, selEnd));
        Edit e = shiftLines(text, from, to, outdent, scheme);
        if (e != null) {
            if (selStart == selEnd) { // keep the caret in the same place in the text of the item
                int delta = e.text().length() - text.length();
                int c = Math.max(from, selStart + delta);
                return new Edit(e.text(), c, c);
            }
            return e;
        }
        // no list item among the lines: plain indent with spaces
        if (selStart == selEnd) {
            if (!outdent) {
                String r = text.substring(0, selStart) + spaces(INDENT) + text.substring(selStart);
                return new Edit(r, selStart + INDENT, selStart + INDENT);
            }
            int ls = lineStart(text, selStart), le = lineEnd(text, selStart);
            int remove = 0;
            while (remove < INDENT && ls + remove < le && text.charAt(ls + remove) == ' ') remove++;
            if (remove == 0) return null;
            return new Edit(text.substring(0, ls) + text.substring(ls + remove), Math.max(ls, selStart - remove), Math.max(ls, selStart - remove));
        }
        return null;
    }

    private static Edit shiftLines(String text, int from, int to, boolean outdent, Scheme scheme) {
        StringBuilder out = new StringBuilder(text);
        int pos = from, end = to, changed = 0;
        while (pos <= end && pos <= out.length()) {
            String s = out.toString();
            int le = lineEnd(s, pos);
            Marker m = markerOf(s, pos);
            if (m != null) {
                int newIndent = Math.max(0, m.indent() + (outdent ? -INDENT : INDENT));
                if (newIndent != m.indent()) {
                    String marker;
                    if (scheme == null || scheme.levels().isEmpty()) { // keep the style typed: only the indent moves
                        marker = m.style().marker(m.value());
                    } else {
                        Style st = scheme.level(newIndent / INDENT);
                        marker = st.marker(valueAtLevel(s, pos, newIndent, st));
                    }
                    String line = spaces(newIndent) + marker + " " + m.body();
                    out.replace(pos, le, line);
                    end += line.length() - (le - pos);
                    le = pos + line.length();
                    changed++;
                }
            }
            if (le >= out.length()) break;
            pos = le + 1;
        }
        if (changed == 0) return null;
        return new Edit(out.toString(), from, Math.min(out.length(), Math.max(from, end)));
    }

    /** The value a marker of this style should have at this indent: one more than the item above at the same level, or the first value. */
    private static int valueAtLevel(String text, int lineStart, int indent, Style style) {
        int pos = lineStart;
        while (pos > 0) {
            int prevStart = lineStart(text, pos - 1);
            Marker prev = markerOf(text, prevStart);
            if (prev == null || prev.indent() < indent) return 1; // out of the list, or up in the parent item: start again
            if (prev.indent() == indent) return prev.style().sameFamily(style) && style.kind() != Kind.BULLET ? prev.value() + 1 : 1;
            pos = prevStart;
        }
        return 1;
    }

    // ---------------------------------------------------------------- turning lines into a list and back

    /**
     * Makes the lines of the selection (or the caret's line) a list of this style: numbered from 1 for numbered styles. If they are all
     * already a list of exactly this style, the markers are taken off again (the text and the indent stay).
     */
    public static Edit toggle(String text, int selStart, int selEnd, Style style) {
        int from = lineStart(text, Math.min(selStart, selEnd)), to = lineEnd(text, Math.max(selStart, selEnd));
        String block = text.substring(from, to);
        String[] lines = block.split("\n", -1);
        boolean all = true, any = false;
        for (String line : lines) {
            if (line.isBlank()) continue;
            any = true;
            Marker m = parse(line);
            if (m == null || !m.style().sameFamily(style)) all = false;
        }
        StringBuilder out = new StringBuilder();
        int value = 1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (i > 0) out.append('\n');
            if (line.isBlank()) {
                out.append(line);
                continue;
            }
            Marker m = parse(line);
            int indent = m != null ? m.indent() : line.length() - line.stripLeading().length();
            String body = m != null ? m.body() : line.stripLeading();
            if (any && all) out.append(spaces(indent)).append(body);
            else out.append(spaces(indent)).append(style.marker(value++)).append(' ').append(body);
        }
        String result = text.substring(0, from) + out + text.substring(to);
        int end = from + out.length();
        if (selStart == selEnd && lines.length == 1) { // a caret: put it at the end of the line's text
            return new Edit(result, end, end);
        }
        return new Edit(result, from, end);
    }

    /** True if the text has a list item at the caret's line. */
    public static boolean inList(String text, int caret) {
        return markerOf(text, lineStart(text, caret)) != null;
    }

    /** All the markers of a list as strings, for tests and menus. */
    public static List<String> markers(String text) {
        List<String> out = new ArrayList<>();
        int pos = 0;
        while (pos <= text.length()) {
            Marker m = markerOf(text, pos);
            out.add(m == null ? "" : m.style().marker(m.value()));
            int le = lineEnd(text, pos);
            if (le >= text.length()) break;
            pos = le + 1;
        }
        return out;
    }
}
