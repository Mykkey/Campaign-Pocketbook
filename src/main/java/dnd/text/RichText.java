package dnd.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Bold and italic for individual words, stored as light markup inside ordinary text:
 * {@code **bold**}, {@code *italic*}, {@code ***both***}. A literal asterisk or backslash is written
 * {@code \*} or {@code \\}. Unpaired markers are shown as they are, so nothing a person typed is ever lost.
 *
 * <p>This class is pure logic (no UI) so it can be unit tested.
 */
public final class RichText {
    private RichText() { }

    /** A stretch of text with one style. */
    public record Run(String text, boolean bold, boolean italic) { }

    /** Result of {@link #toggle}: the new markup and where the selection is in it. */
    public record Edit(String markup, int selStart, int selEnd) { }

    /** The text as characters with a style each, plus how markup positions map to plain positions. */
    private static final class Chars {
        final StringBuilder plain = new StringBuilder();
        final List<Boolean> bold = new ArrayList<>();
        final List<Boolean> italic = new ArrayList<>();
        /** plainAt[i] = number of plain characters before markup index i (length markup+1). */
        int[] plainAt;
    }

    private enum Kind { CHAR, BOLD, ITALIC }

    private record Tok(Kind kind, char ch, int start, int end) { }

    // ================================================================ parsing

    private static Chars parseChars(String s) {
        List<Tok> toks = new ArrayList<>();
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < n && (s.charAt(i + 1) == '*' || s.charAt(i + 1) == '\\')) {
                toks.add(new Tok(Kind.CHAR, s.charAt(i + 1), i, i + 2));
                i += 2;
            } else if (c == '*' && i + 1 < n && s.charAt(i + 1) == '*') {
                toks.add(new Tok(Kind.BOLD, '*', i, i + 2));
                i += 2;
            } else if (c == '*') {
                toks.add(new Tok(Kind.ITALIC, '*', i, i + 1));
                i += 1;
            } else {
                toks.add(new Tok(Kind.CHAR, c, i, i + 1));
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
                }
            } else if (t.kind == Kind.BOLD) {
                boldOn = !boldOn;
            } else {
                italicOn = !italicOn;
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
        for (int i = 0; i < c.plain.length(); i++) {
            boolean cb = c.bold.get(i), ci = c.italic.get(i);
            if (i > 0 && (cb != b || ci != it)) {
                out.add(new Run(cur.toString(), b, it));
                cur.setLength(0);
            }
            b = cb;
            it = ci;
            cur.append(c.plain.charAt(i));
        }
        if (cur.length() > 0) out.add(new Run(cur.toString(), b, it));
        return out;
    }

    /** The text without any markup, e.g. for searching. */
    public static String plain(String markup) {
        if (markup == null || markup.indexOf('*') < 0 && markup.indexOf('\\') < 0) return markup == null ? "" : markup;
        return parseChars(markup).plain.toString();
    }

    /** Escapes plain text so it shows exactly as typed (used for text pasted from elsewhere). */
    public static String escape(String plain) {
        if (plain == null) return "";
        return plain.replace("\\", "\\\\").replace("*", "\\*");
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
        for (int k = 0; k < n; k++) {
            boolean wantB = c.bold.get(k), wantI = c.italic.get(k);
            if (b && !wantB) sb.append("**");
            if (it && !wantI) sb.append('*');
            if (!b && wantB) sb.append("**");
            if (!it && wantI) sb.append('*');
            b = wantB;
            it = wantI;
            at[k] = sb.length();
            char ch = c.plain.charAt(k);
            if (ch == '*' || ch == '\\') sb.append('\\');
            sb.append(ch);
        }
        if (it) sb.append('*');
        if (b) sb.append("**");
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
        return ch == '*' || ch == '\\';
    }
}
