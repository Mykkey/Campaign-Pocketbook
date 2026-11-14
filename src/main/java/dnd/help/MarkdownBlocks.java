package dnd.help;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the Markdown of the guide into blocks (paragraphs, list items, tables, code, headings) and their inline parts (bold,
 * italic, code, links), so the help window can draw them. Only what the guide uses is understood. Pure text handling.
 */
public final class MarkdownBlocks {
    private MarkdownBlocks() { }

    public enum Type { HEADING, PARAGRAPH, BULLET, NUMBERED, TABLE, CODE, RULE }

    /**
     * One block.
     *
     * @param level  the heading level, or how deep a list item is (0 for the outer list)
     * @param number the number of a numbered item
     * @param text   the Markdown text of a heading, paragraph, list item or code block
     * @param rows   the rows of a table (the first is the header), each a list of cells
     */
    public record Block(Type type, int level, int number, String text, List<List<String>> rows) {
        static Block of(Type type, int level, String text) {
            return new Block(type, level, 0, text, List.of());
        }
    }

    /** A run of text with one style. {@code url} is set for a link. */
    public record Span(String text, boolean bold, boolean italic, boolean code, String url) { }

    // ---------------------------------------------------------------- blocks

    public static List<Block> parse(String markdown) {
        List<Block> out = new ArrayList<>();
        String[] lines = (markdown == null ? "" : markdown).replace("\r\n", "\n").split("\n", -1);
        StringBuilder paragraph = new StringBuilder();
        List<List<String>> table = new ArrayList<>();
        Block lastItem = null;
        int lastItemIndex = -1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.strip();
            if (trimmed.startsWith("```")) {
                flush(out, paragraph, table);
                StringBuilder code = new StringBuilder();
                for (i++; i < lines.length && !lines[i].strip().startsWith("```"); i++) code.append(lines[i]).append('\n');
                out.add(Block.of(Type.CODE, 0, code.toString().stripTrailing()));
                lastItem = null;
                continue;
            }
            if (trimmed.isEmpty()) {
                flush(out, paragraph, table);
                lastItem = null;
                continue;
            }
            if (trimmed.startsWith("|")) {
                flushParagraph(out, paragraph);
                List<String> cells = cells(trimmed);
                if (!isSeparator(cells)) table.add(cells);
                lastItem = null;
                continue;
            }
            flushTable(out, table);
            if (trimmed.equals("---") || trimmed.equals("***")) {
                flushParagraph(out, paragraph);
                out.add(Block.of(Type.RULE, 0, ""));
                continue;
            }
            int hashes = 0;
            while (hashes < trimmed.length() && trimmed.charAt(hashes) == '#') hashes++;
            if (hashes > 0 && hashes < trimmed.length() && trimmed.charAt(hashes) == ' ') {
                flushParagraph(out, paragraph);
                out.add(Block.of(Type.HEADING, hashes, trimmed.substring(hashes + 1).strip()));
                continue;
            }
            int indent = line.length() - line.stripLeading().length();
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                flushParagraph(out, paragraph);
                lastItem = Block.of(Type.BULLET, indent / 2, trimmed.substring(2).strip());
                out.add(lastItem);
                lastItemIndex = out.size() - 1;
                continue;
            }
            int dot = trimmed.indexOf(". ");
            if (dot > 0 && dot <= 3 && trimmed.substring(0, dot).chars().allMatch(Character::isDigit)) {
                flushParagraph(out, paragraph);
                lastItem = new Block(Type.NUMBERED, indent / 2, Integer.parseInt(trimmed.substring(0, dot)), trimmed.substring(dot + 2).strip(), List.of());
                out.add(lastItem);
                lastItemIndex = out.size() - 1;
                continue;
            }
            if (lastItem != null && indent > 0) { // a continuation line of the list item above
                Block b = out.get(lastItemIndex);
                lastItem = new Block(b.type(), b.level(), b.number(), b.text() + " " + trimmed, List.of());
                out.set(lastItemIndex, lastItem);
                continue;
            }
            if (paragraph.length() > 0) paragraph.append(' ');
            paragraph.append(trimmed);
            lastItem = null;
        }
        flush(out, paragraph, table);
        return out;
    }

    private static void flush(List<Block> out, StringBuilder paragraph, List<List<String>> table) {
        flushParagraph(out, paragraph);
        flushTable(out, table);
    }

    private static void flushParagraph(List<Block> out, StringBuilder paragraph) {
        if (paragraph.length() > 0) out.add(Block.of(Type.PARAGRAPH, 0, paragraph.toString()));
        paragraph.setLength(0);
    }

    private static void flushTable(List<Block> out, List<List<String>> table) {
        if (!table.isEmpty()) out.add(new Block(Type.TABLE, 0, 0, "", new ArrayList<>(table)));
        table.clear();
    }

    private static List<String> cells(String row) {
        String s = row;
        if (s.startsWith("|")) s = s.substring(1);
        if (s.endsWith("|")) s = s.substring(0, s.length() - 1);
        List<String> out = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean tick = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '`') tick = !tick;
            if (c == '|' && !tick) {
                out.add(cell.toString().strip());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        out.add(cell.toString().strip());
        return out;
    }

    private static boolean isSeparator(List<String> cells) {
        for (String c : cells) if (!c.matches(":?-{2,}:?")) return false;
        return !cells.isEmpty();
    }

    // ---------------------------------------------------------------- inline

    /** Splits a line of Markdown into styled runs: **bold**, *italic*, `code` and [text](url). */
    public static List<Span> inline(String text) {
        List<Span> out = new ArrayList<>();
        StringBuilder plain = new StringBuilder();
        int i = 0, n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '`') {
                int end = text.indexOf('`', i + 1);
                if (end > i) {
                    emit(out, plain);
                    out.add(new Span(text.substring(i + 1, end), false, false, true, null));
                    i = end + 1;
                    continue;
                }
            } else if (c == '*' && text.startsWith("**", i)) {
                int end = text.indexOf("**", i + 2);
                if (end > i + 2) {
                    emit(out, plain);
                    for (Span s : inline(text.substring(i + 2, end))) out.add(new Span(s.text(), true, s.italic(), s.code(), s.url()));
                    i = end + 2;
                    continue;
                }
            } else if (c == '*' && i + 1 < n && text.charAt(i + 1) != ' ' && text.charAt(i + 1) != '*') {
                int end = text.indexOf('*', i + 1);
                if (end > i + 1 && text.charAt(end - 1) != ' ') {
                    emit(out, plain);
                    for (Span s : inline(text.substring(i + 1, end))) out.add(new Span(s.text(), s.bold(), true, s.code(), s.url()));
                    i = end + 1;
                    continue;
                }
            } else if (c == '[') {
                int close = text.indexOf(']', i + 1);
                if (close > i && close + 1 < n && text.charAt(close + 1) == '(') {
                    int end = text.indexOf(')', close + 2);
                    if (end > close) {
                        emit(out, plain);
                        out.add(new Span(text.substring(i + 1, close), false, false, false, text.substring(close + 2, end)));
                        i = end + 1;
                        continue;
                    }
                }
            }
            plain.append(c);
            i++;
        }
        emit(out, plain);
        return out;
    }

    private static void emit(List<Span> out, StringBuilder plain) {
        if (plain.length() > 0) out.add(new Span(plain.toString(), false, false, false, null));
        plain.setLength(0);
    }
}
