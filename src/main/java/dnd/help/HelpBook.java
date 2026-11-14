package dnd.help;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The help book: the user guide split into topics (a chapter for each "##" heading, and a topic for each "###" heading under
 * it) that can be listed, read and searched. Pure text handling, no screen code, so it can be tested.
 */
public final class HelpBook {
    /** The part of the guide for people building the app, which is not shown in the help. */
    private static final String STOP_AT = "for developers";

    /**
     * One topic.
     *
     * @param id      a position-based number, stable for one copy of the book
     * @param level   2 for a chapter, 3 for a topic inside a chapter
     * @param title   the heading text
     * @param chapter the title of the chapter it is in (its own title for a chapter)
     * @param body    its text (Markdown), up to the next heading
     */
    public record Topic(int id, int level, String title, String chapter, String body) { }

    /** A search result: the topic and a short piece of its text around what was found. */
    public record Hit(Topic topic, String snippet, int score) { }

    private final List<Topic> topics;

    private HelpBook(List<Topic> topics) {
        this.topics = topics;
    }

    /** Splits the guide into topics. Text before the first heading, and everything from "For developers" on, is left out. */
    public static HelpBook parse(String markdown) {
        List<Topic> out = new ArrayList<>();
        String[] lines = (markdown == null ? "" : markdown).replace("\r\n", "\n").split("\n", -1);
        String chapter = "";
        String title = null;
        int level = 0;
        StringBuilder body = new StringBuilder();
        boolean inCode = false;
        for (String line : lines) {
            if (line.startsWith("```")) inCode = !inCode;
            int hashes = inCode ? 0 : headingLevel(line);
            if (hashes == 2 || hashes == 3) {
                if (title != null) out.add(new Topic(out.size(), level, title, chapter, body.toString().strip()));
                title = line.substring(hashes).strip();
                level = hashes;
                if (hashes == 2) {
                    if (title.toLowerCase(Locale.ROOT).startsWith(STOP_AT)) {
                        title = null;
                        break;
                    }
                    chapter = title;
                }
                body.setLength(0);
            } else if (hashes == 1) {
                continue; // the title of the whole guide
            } else if (title != null) {
                body.append(line).append('\n');
            }
        }
        if (title != null) out.add(new Topic(out.size(), level, title, chapter, body.toString().strip()));
        return new HelpBook(out);
    }

    private static int headingLevel(String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == '#') n++;
        return n > 0 && n <= 6 && line.length() > n && line.charAt(n) == ' ' ? n : 0;
    }

    public List<Topic> topics() {
        return topics;
    }

    public Topic topic(int id) {
        return id >= 0 && id < topics.size() ? topics.get(id) : null;
    }

    /** What to show for a topic: a chapter shows its own text and then the topics inside it; a topic shows just its own. */
    public List<Topic> pageFor(Topic t) {
        List<Topic> page = new ArrayList<>();
        page.add(t);
        if (t.level() == 2) {
            for (int i = t.id() + 1; i < topics.size() && topics.get(i).level() == 3; i++) page.add(topics.get(i));
        }
        return page;
    }

    // ---------------------------------------------------------------- search

    /** The words of a query: lower case, split on spaces, empty bits dropped. */
    public static List<String> words(String query) {
        List<String> out = new ArrayList<>();
        if (query == null) return out;
        for (String w : query.toLowerCase(Locale.ROOT).split("\\s+")) if (!w.isBlank()) out.add(w);
        // little words ("of", "to", "a") are in nearly every topic and only add noise: ignore them unless nothing else was typed
        if (out.stream().anyMatch(w -> w.length() >= 3)) out.removeIf(w -> w.length() < 3);
        return out;
    }

    /** The text without Markdown marks: bold, italic, code and link marks removed (a link keeps its words). */
    public static String plain(String markdown) {
        String s = markdown.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1");
        s = s.replace("**", "").replace("`", "");
        s = s.replaceAll("(?m)^\\s*[-*]\\s+", "").replaceAll("(?m)^\\s*\\|", "").replace("|", "  ");
        return s.replaceAll("\\s+", " ").strip();
    }

    /**
     * The topics that contain every word of the query (in the title, the chapter title or the text), best first: a word in a
     * title counts for much more than one in the text. An empty query finds nothing.
     */
    public List<Hit> search(String query) {
        List<String> words = words(query);
        List<Hit> hits = new ArrayList<>();
        if (words.isEmpty()) return hits;
        for (Topic t : topics) {
            String title = t.title().toLowerCase(Locale.ROOT), chapter = t.chapter().toLowerCase(Locale.ROOT);
            String text = plain(t.body());
            String lower = text.toLowerCase(Locale.ROOT);
            int score = 0;
            boolean all = true;
            for (String w : words) {
                int inTitle = title.contains(w) ? 1 : 0, inChapter = t.level() == 3 && chapter.contains(w) ? 1 : 0;
                int inText = count(lower, w);
                if (inTitle + inChapter + inText == 0) {
                    all = false;
                    break;
                }
                score += inTitle * 20 + inChapter * 4 + Math.min(inText, 10);
            }
            if (!all) continue;
            hits.add(new Hit(t, snippet(text, lower, words), score));
        }
        hits.sort(Comparator.comparingInt(Hit::score).reversed().thenComparingInt(h -> h.topic().id()));
        return hits;
    }

    private static int count(String text, String word) {
        int n = 0;
        for (int i = text.indexOf(word); i >= 0; i = text.indexOf(word, i + word.length())) n++;
        return n;
    }

    /** About a line of text around the first place a query word appears (the start of the text if only the title matched). */
    static String snippet(String text, String lower, List<String> words) {
        int at = -1;
        for (String w : words) {
            int i = lower.indexOf(w);
            if (i >= 0 && (at < 0 || i < at)) at = i;
        }
        if (at < 0) at = 0;
        int from = Math.max(0, at - 50), to = Math.min(text.length(), at + 110);
        if (from > 0) { // start at a word, not in the middle of one
            int sp = text.indexOf(' ', from);
            if (sp >= 0 && sp < at) from = sp + 1;
        }
        String s = text.substring(from, to).strip();
        return (from > 0 ? "..." : "") + s + (to < text.length() ? "..." : "");
    }
}
