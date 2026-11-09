package dnd.text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bold and italic for individual words, and links or tooltips on individual words, stored as light markup inside
 * ordinary text: {@code **bold**}, {@code *italic*}, {@code ***both***}, and {@code [words]{3}} for a span that
 * refers to entry 3 of the text object's reference table (which holds the link and tooltip targets). A literal
 * asterisk, backslash or opening bracket is written {@code \*}, {@code \\} or {@code \[}. Unpaired markers are
 * shown as they are, so nothing a person typed is ever lost; a bracket only starts a span when its closing bracket
 * is immediately followed by {@code {number}}.
 *
 * <p>This class is pure logic (no UI) so it can be unit tested.
 */
public final class RichText {
    private RichText() { }

    /** A stretch of text with one style; {@code ref} is the span's reference number (0 = not in a span). */
    public record Run(String text, boolean bold, boolean italic, int ref) {
        public Run(String text, boolean bold, boolean italic) {
            this(text, bold, italic, 0);
        }
    }

    /** Result of {@link #toggle}: the new markup and where the selection is in it. */
    public record Edit(String markup, int selStart, int selEnd) { }

    /** A span in plain-text positions: characters {@code plainStart} up to (not including) {@code plainEnd}. */
    public record Span(int plainStart, int plainEnd, int ref) { }

    /** The text as characters with a style each, plus how markup positions map to plain positions. */
    private static final class Chars {
        final StringBuilder plain = new StringBuilder();
        final List<Boolean> bold = new ArrayList<>();
        final List<Boolean> italic = new ArrayList<>();
        final List<Integer> ref = new ArrayList<>();
        /** plainAt[i] = number of plain characters before markup index i (length markup+1). */
        int[] plainAt;
    }

    private enum Kind { CHAR, BOLD, ITALIC, SPAN_OPEN, SPAN_CLOSE }

    private record Tok(Kind kind, char ch, int start, int end, int ref) { }

    // ================================================================ parsing

    private static boolean isEscapable(char c) {
        return c == '*' || c == '\\' || c == '[';
    }

    /**
     * If a span opens at {@code open} (a '['), returns {index of its ']', index just past "]{n}", n}; otherwise
     * null. The first ']' after the '[' must be followed by "{digits}", and spans do not nest.
     */
    private static int[] spanAt(String s, int open) {
        int n = s.length();
        for (int j = open + 1; j < n; j++) {
            char c = s.charAt(j);
            if (c == '\\' && j + 1 < n && isEscapable(s.charAt(j + 1))) {
                j++;
            } else if (c == '[') {
                return null;
            } else if (c == ']') {
                if (j + 1 < n && s.charAt(j + 1) == '{') {
                    int k = j + 2, digits = 0, value = 0;
                    while (k < n && Character.isDigit(s.charAt(k)) && digits < 6) {
                        value = value * 10 + (s.charAt(k) - '0');
                        k++;
                        digits++;
                    }
                    if (digits > 0 && k < n && s.charAt(k) == '}' && value > 0) return new int[]{j, k + 1, value};
                }
                return null;
            }
        }
        return null;
    }

    private static Chars parseChars(String s) {
        int n = s.length();
        // spans first: opening index -> reference, closing index -> index after "]{n}"
        Map<Integer, Integer> openRef = new HashMap<>(), closeEnd = new HashMap<>();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < n && isEscapable(s.charAt(i + 1))) {
                i++;
            } else if (c == '[') {
                int[] sp = spanAt(s, i);
                if (sp != null) {
                    openRef.put(i, sp[2]);
                    closeEnd.put(sp[0], sp[1]);
                }
            }
        }

        List<Tok> toks = new ArrayList<>();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < n && isEscapable(s.charAt(i + 1))) {
                toks.add(new Tok(Kind.CHAR, s.charAt(i + 1), i, i + 2, 0));
                i += 2;
            } else if (c == '*' && i + 1 < n && s.charAt(i + 1) == '*') {
                toks.add(new Tok(Kind.BOLD, '*', i, i + 2, 0));
                i += 2;
            } else if (c == '*') {
                toks.add(new Tok(Kind.ITALIC, '*', i, i + 1, 0));
                i += 1;
            } else if (c == '[' && openRef.containsKey(i)) {
                toks.add(new Tok(Kind.SPAN_OPEN, c, i, i + 1, openRef.get(i)));
                i += 1;
            } else if (c == ']' && closeEnd.containsKey(i)) {
                toks.add(new Tok(Kind.SPAN_CLOSE, c, i, closeEnd.get(i), 0));
                i = closeEnd.get(i);
            } else {
                toks.add(new Tok(Kind.CHAR, c, i, i + 1, 0));
                i += 1;
            }
        }
        // an odd number of a marker means the last one has no partner: show it literally
        int lastBold = -1, lastItalic = -1, boldCount = 0, italicCount = 0;
        for (int k = 0; k < toks.size(); k++) {
            if (toks.get(k).kind == Kind.BOLD) { boldCount++; lastBold = k; }
            if (toks.get(k).kind == Kind.ITALIC) { italicCount++; lastItalic = k; }
        }
        boolean boldOn = false, italicOn = false;
        int refOn = 0;
        Chars out = new Chars();
        int[] plainAt = new int[n + 1];
        int pos = 0; // markup index already mapped
        for (int k = 0; k < toks.size(); k++) {
            Tok t = toks.get(k);
            while (pos < t.start) plainAt[pos++] = out.plain.length();
            plainAt[pos] = out.plain.length();
            boolean literalMarker = (t.kind == Kind.BOLD && boldCount % 2 == 1 && k == lastBold)
                    || (t.kind == Kind.ITALIC && italicCount % 2 == 1 && k == lastItalic);
            if (t.kind == Kind.CHAR || literalMarker) {
                String text = t.kind == Kind.BOLD ? "**" : t.kind == Kind.ITALIC ? "*" : String.valueOf(t.ch);
                for (int q = 0; q < text.length(); q++) {
                    out.plain.append(text.charAt(q));
                    out.bold.add(boldOn);
                    out.italic.add(italicOn);
                    out.ref.add(refOn);
                }
            } else if (t.kind == Kind.BOLD) {
                boldOn = !boldOn;
            } else if (t.kind == Kind.ITALIC) {
                italicOn = !italicOn;
            } else if (t.kind == Kind.SPAN_OPEN) {
                refOn = t.ref;
            } else {
                refOn = 0;
            }
            // every markup index inside this token maps to the plain length before it (after it for the end)
            for (int q = t.start + 1; q < t.end; q++) plainAt[q] = plainAt[t.start];
            pos = t.end;
        }
        while (pos <= n) plainAt[pos++] = out.plain.length();
        out.plainAt = plainAt;
        return out;
    }

    /** The styled runs of a markup string. */
    public static List<Run> parse(String markup) {
        return runs(parseChars(markup == null ? "" : markup));
    }

    private static List<Run> runs(Chars c) {
        List<Run> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean b = false, it = false;
        int r = 0;
        for (int i = 0; i < c.plain.length(); i++) {
            boolean cb = c.bold.get(i), ci = c.italic.get(i);
            int cr = c.ref.get(i);
            if (i > 0 && (cb != b || ci != it || cr != r)) {
                out.add(new Run(cur.toString(), b, it, r));
                cur.setLength(0);
            }
            b = cb;
            it = ci;
            r = cr;
            cur.append(c.plain.charAt(i));
        }
        if (cur.length() > 0) out.add(new Run(cur.toString(), b, it, r));
        return out;
    }

    /** The text without any markup, e.g. for searching. */
    public static String plain(String markup) {
        if (markup == null) return "";
        if (markup.indexOf('*') < 0 && markup.indexOf('\\') < 0 && markup.indexOf('[') < 0) return markup;
        return parseChars(markup).plain.toString();
    }

    /** Escapes plain text so it shows exactly as typed (used for text pasted from elsewhere). */
    public static String escape(String plain) {
        if (plain == null) return "";
        return plain.replace("\\", "\\\\").replace("*", "\\*").replace("[", "\\[");
    }

    // ================================================================ spans (links and tooltips on words)

    /** The spans in a markup string, in order, as plain-text ranges. */
    public static List<Span> spans(String markup) {
        Chars c = parseChars(markup == null ? "" : markup);
        List<Span> out = new ArrayList<>();
        int start = -1, ref = 0;
        for (int k = 0; k <= c.plain.length(); k++) {
            int r = k < c.plain.length() ? c.ref.get(k) : 0;
            if (r != ref) {
                if (ref != 0) out.add(new Span(start, k, ref));
                start = k;
                ref = r;
            }
        }
        return out;
    }

    /** The reference numbers used by spans in this markup. */
    public static Set<Integer> refsUsed(String markup) {
        Set<Integer> out = new LinkedHashSet<>();
        for (Span s : spans(markup)) out.add(s.ref());
        return out;
    }

    /** The span reference at a plain-text position, or 0. */
    public static int refAt(String markup, int plainIndex) {
        Chars c = parseChars(markup == null ? "" : markup);
        return plainIndex >= 0 && plainIndex < c.ref.size() ? c.ref.get(plainIndex) : 0;
    }

    /** Converts a selection in markup positions (what a text box reports) to plain-text positions {start, end}. */
    public static int[] toPlainRange(String markup, int selStart, int selEnd) {
        String m = markup == null ? "" : markup;
        Chars c = parseChars(m);
        int s = Math.max(0, Math.min(Math.min(selStart, selEnd), m.length()));
        int e = Math.max(0, Math.min(Math.max(selStart, selEnd), m.length()));
        return new int[]{c.plainAt[s], c.plainAt[e]};
    }

    /**
     * Puts plain-text characters {@code plainStart} up to {@code plainEnd} into the span with reference {@code ref}
     * (0 takes them out of any span). Characters of an older span that are not in the range keep it.
     */
    public static Edit setSpanRef(String markup, int plainStart, int plainEnd, int ref) {
        String m = markup == null ? "" : markup;
        Chars c = parseChars(m);
        int a = Math.max(0, Math.min(plainStart, plainEnd)), b = Math.min(c.plain.length(), Math.max(plainStart, plainEnd));
        if (a >= b) return new Edit(m, 0, 0);
        for (int k = a; k < b; k++) c.ref.set(k, ref);
        Written w = write(c);
        int newStart = w.markupAt[a];
        int newEnd = w.markupAt[b - 1] + (needsEscape(c.plain.charAt(b - 1)) ? 2 : 1);
        return new Edit(w.markup, newStart, newEnd);
    }

    // ================================================================ writing markup

    private static final class Written {
        String markup;
        int[] markupAt; // markupAt[k] = markup index of plain character k (and markup length at k == plain length)
    }

    private static Written write(Chars c) {
        StringBuilder sb = new StringBuilder();
        int n = c.plain.length();
        int[] at = new int[n + 1];
        boolean b = false, it = false;
        int curRef = 0;
        for (int k = 0; k < n; k++) {
            boolean wantB = c.bold.get(k), wantI = c.italic.get(k);
            int wantRef = c.ref.get(k);
            if (wantRef != curRef) { // span markers sit outside the styles: close styles, change span, reopen below
                if (it) sb.append('*');
                if (b) sb.append("**");
                it = false;
                b = false;
                if (curRef != 0) sb.append("]{").append(curRef).append('}');
                if (wantRef != 0) sb.append('[');
                curRef = wantRef;
            }
            if (b && !wantB) sb.append("**");
            if (it && !wantI) sb.append('*');
            if (!b && wantB) sb.append("**");
            if (!it && wantI) sb.append('*');
            b = wantB;
            it = wantI;
            at[k] = sb.length();
            char ch = c.plain.charAt(k);
            if (needsEscape(ch)) sb.append('\\');
            sb.append(ch);
        }
        if (it) sb.append('*');
        if (b) sb.append("**");
        if (curRef != 0) sb.append("]{").append(curRef).append('}');
        at[n] = sb.length();
        Written w = new Written();
        w.markup = sb.toString();
        w.markupAt = at;
        return w;
    }

    /** Builds markup from runs. */
    public static String toMarkup(List<Run> runs) {
        Chars c = new Chars();
        for (Run r : runs) {
            for (int i = 0; i < r.text().length(); i++) {
                c.plain.append(r.text().charAt(i));
                c.bold.add(r.bold());
                c.italic.add(r.italic());
                c.ref.add(r.ref());
            }
        }
        return write(c).markup;
    }

    // ================================================================ toggling a style over a selection

    /**
     * Makes the selected characters bold (or italic), or plain again if they already all are. The selection
     * is given in markup positions (what a text box reports) and returned the same way.
     */
    public static Edit toggle(String markup, int selStart, int selEnd, boolean bold) {
        String m = markup == null ? "" : markup;
        int s = Math.max(0, Math.min(selStart, selEnd)), e = Math.min(m.length(), Math.max(selStart, selEnd));
        Chars c = parseChars(m);
        int a = c.plainAt[Math.min(s, m.length())], b = c.plainAt[Math.min(e, m.length())];
        if (a >= b) return new Edit(m, selStart, selEnd);
        List<Boolean> list = bold ? c.bold : c.italic;
        boolean allOn = true;
        for (int k = a; k < b; k++) if (!list.get(k)) { allOn = false; break; }
        for (int k = a; k < b; k++) list.set(k, !allOn);
        Written w = write(c);
        int newStart = w.markupAt[a];
        int newEnd = w.markupAt[b - 1] + (needsEscape(c.plain.charAt(b - 1)) ? 2 : 1);
        return new Edit(w.markup, newStart, newEnd);
    }

    private static boolean needsEscape(char ch) {
        return ch == '*' || ch == '\\' || ch == '[';
    }
}
