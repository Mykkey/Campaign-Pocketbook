package dnd.ui;

import dnd.model.BoardObject;
import dnd.text.RichText;
import dnd.text.RichText.Run;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays styled text out in lines so it can be measured and drawn on the canvas, with bold and italic applied to
 * individual words. Lines break at newlines and, when a width is given, at word boundaries.
 */
final class RichLayout {
    private RichLayout() { }

    /** A piece of one line drawn in one style, {@code x} pixels from the line's left edge. */
    record Seg(String text, boolean bold, boolean italic, double x, double w) { }

    record Line(List<Seg> segs, double width) { }

    /** One styled stretch inside a word, or a run of spaces. */
    private record Piece(String text, boolean bold, boolean italic, double w, boolean space) { }

    /** The font for a stretch of text: the object's own bold/italic (whole block) plus the word's. */
    static Font font(BoardObject o, boolean bold, boolean italic) {
        return Font.font("System", (o.bold || bold) ? FontWeight.BOLD : FontWeight.NORMAL,
                (o.italic || italic) ? FontPosture.ITALIC : FontPosture.REGULAR, o.fontSize);
    }

    /** Height of one line of this object's text. */
    static double lineHeight(BoardObject o) {
        Text t = new Text("Ag");
        t.setFont(font(o, false, false));
        return Math.max(o.fontSize * 1.3, t.getLayoutBounds().getHeight());
    }

    private static double width(BoardObject o, String s, boolean bold, boolean italic) {
        if (s.isEmpty()) return 0;
        Text t = new Text(s);
        t.setFont(font(o, bold, italic));
        return t.getLayoutBounds().getWidth();
    }

    /**
     * @param maxWidth wrap at this width in pixels; zero or less means do not wrap (only newlines break lines)
     */
    static List<Line> layout(String markup, BoardObject o, double maxWidth) {
        Builder b = new Builder(maxWidth);
        for (Run r : RichText.parse(markup)) {
            String text = r.text();
            int i = 0;
            while (i < text.length()) {
                char c = text.charAt(i);
                if (c == '\n') {
                    b.newline();
                    i++;
                    continue;
                }
                boolean space = c == ' ';
                int j = i;
                while (j < text.length() && text.charAt(j) != '\n' && (text.charAt(j) == ' ') == space) j++;
                String piece = text.substring(i, j);
                b.add(new Piece(piece, r.bold(), r.italic(), width(o, piece, r.bold(), r.italic()), space));
                i = j;
            }
        }
        return b.finish();
    }

    /** Width of the widest line. */
    static double maxWidth(List<Line> lines) {
        double m = 0;
        for (Line l : lines) m = Math.max(m, l.width());
        return m;
    }

    /** Collects pieces into words (a word ends at a space) and words into lines. */
    private static final class Builder {
        private final double max;
        private final List<Line> lines = new ArrayList<>();
        private List<Seg> cur = new ArrayList<>();
        private final List<Piece> word = new ArrayList<>();
        private double x;

        Builder(double max) {
            this.max = max;
        }

        void add(Piece p) {
            word.add(p);
            if (p.space) flush(); // the space stays with the word before it
        }

        void newline() {
            flush();
            breakLine();
        }

        private void breakLine() {
            lines.add(new Line(cur, x));
            cur = new ArrayList<>();
            x = 0;
        }

        private void flush() {
            if (word.isEmpty()) return;
            double wordWidth = 0;
            for (Piece p : word) if (!p.space) wordWidth += p.w;
            // start a new line if this word would stick out (but never leave a line empty)
            if (max > 0 && !cur.isEmpty() && x + wordWidth > max) breakLine();
            for (Piece p : word) {
                cur.add(new Seg(p.text, p.bold, p.italic, x, p.w));
                x += p.w;
            }
            word.clear();
        }

        List<Line> finish() {
            flush();
            lines.add(new Line(cur, x));
            return lines;
        }
    }
}
