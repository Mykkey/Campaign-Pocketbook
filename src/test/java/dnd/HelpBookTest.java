package dnd;

import dnd.help.HelpBook;
import dnd.help.MarkdownBlocks;
import dnd.help.MarkdownBlocks.Block;
import dnd.help.MarkdownBlocks.Span;
import dnd.help.MarkdownBlocks.Type;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HelpBookTest {
    private static final String SAMPLE = """
            # Guide

            Intro that is not a topic.

            ## First chapter
            Text of the first chapter with a **bold** word.

            ### A topic
            Details about the lighthouse keeper.

            ## Second chapter
            More about fog of war.

            ## For developers
            Build notes nobody needs.
            """;

    @Test
    void theGuideIsSplitIntoChaptersAndTopicsAndTheDeveloperPartIsLeftOut() {
        HelpBook b = HelpBook.parse(SAMPLE);
        assertEquals(List.of("First chapter", "A topic", "Second chapter"), b.topics().stream().map(HelpBook.Topic::title).toList());
        assertEquals(List.of(2, 3, 2), b.topics().stream().map(HelpBook.Topic::level).toList());
        assertEquals("First chapter", b.topics().get(1).chapter());
        assertTrue(b.topics().get(0).body().contains("**bold**"));
        assertFalse(b.topics().stream().anyMatch(t -> t.body().contains("Build notes") || t.body().contains("Intro")));
        assertEquals(List.of("First chapter", "A topic"), b.pageFor(b.topics().get(0)).stream().map(HelpBook.Topic::title).toList(), "a chapter shows its topics");
        assertEquals(1, b.pageFor(b.topics().get(1)).size());
    }

    @Test
    void searchNeedsEveryWordAndPutsTitleMatchesFirst() {
        HelpBook b = HelpBook.parse(SAMPLE);
        assertTrue(b.search("").isEmpty());
        assertTrue(b.search("nothing here").isEmpty());
        List<HelpBook.Hit> lighthouse = b.search("lighthouse");
        assertEquals(1, lighthouse.size());
        assertEquals("A topic", lighthouse.get(0).topic().title());
        assertTrue(lighthouse.get(0).snippet().contains("lighthouse"));
        assertEquals("A topic", b.search("keeper details").get(0).topic().title(), "all the words, in any order");
        assertTrue(b.search("keeper fog").isEmpty(), "the words must be in the same topic");
        assertEquals("Second chapter", b.search("second").get(0).topic().title(), "a title match counts");
        assertEquals(3, b.search("CHAPTER").size(), "case does not matter; the topic inside the first chapter matches by its chapter title");
    }

    @Test
    void theRealGuideCanBeSearchedForTheThingsAPersonWouldAsk() throws Exception {
        HelpBook b = HelpBook.parse(Files.readString(Path.of("docs", "GUIDE.md")));
        assertTrue(b.topics().size() > 30, "many chapters and topics: " + b.topics().size());
        assertEquals("Getting started: a first tour", b.topics().get(0).title(), "the tutorial is first");
        assertTrue(b.topics().stream().noneMatch(t -> t.title().toLowerCase().startsWith("for developers")));
        for (String question : new String[]{"back up", "deleted bin", "challenge rating", "resize battlemap", "link card", "fog", "shortcut", "organisation hierarchy", "player view", "dark mode"}) {
            assertFalse(b.search(question).isEmpty(), "no answer found for: " + question);
        }
    }

    @Test
    void markdownMarksAreStrippedForSnippets() {
        assertEquals("Press Ctrl+K and see Battlemaps", HelpBook.plain("Press **Ctrl+K** and see [Battlemaps](#battlemaps)"));
    }

    // ---------------------------------------------------------------- blocks and inline

    @Test
    void blocksAreParagraphsListsTablesAndCode() {
        List<Block> blocks = MarkdownBlocks.parse("""
                A paragraph
                over two lines.

                - first
                  continued here
                  - nested
                - second
                1. one
                2. two

                | Key | Does |
                |---|---|
                | `A` | alpha |
                | B | beta |

                ```
                code here
                ```
                """);
        assertEquals(List.of(Type.PARAGRAPH, Type.BULLET, Type.BULLET, Type.BULLET, Type.NUMBERED, Type.NUMBERED, Type.TABLE, Type.CODE),
                blocks.stream().map(Block::type).toList());
        assertEquals("A paragraph over two lines.", blocks.get(0).text());
        assertEquals("first continued here", blocks.get(1).text());
        assertEquals(1, blocks.get(2).level(), "an indented item is one level deeper");
        assertEquals(2, blocks.get(5).number());
        assertEquals(List.of(List.of("Key", "Does"), List.of("`A`", "alpha"), List.of("B", "beta")), blocks.get(6).rows());
        assertEquals("code here", blocks.get(7).text());
    }

    @Test
    void aTableCellCanHoldAPipeInsideCode() {
        List<Block> blocks = MarkdownBlocks.parse("| a | b |\n|---|---|\n| `x | y` | z |\n");
        assertEquals(List.of("`x | y`", "z"), blocks.get(0).rows().get(1));
    }

    @Test
    void inlineMarksBecomeStyledRuns() {
        List<Span> s = MarkdownBlocks.inline("Press **Ctrl+K**, then *pick* `Link` or see [the site](https://example.com/x) now");
        assertEquals("Press ", s.get(0).text());
        assertTrue(s.get(1).bold());
        assertEquals("Ctrl+K", s.get(1).text());
        assertTrue(s.get(3).italic());
        assertEquals("pick", s.get(3).text());
        assertTrue(s.get(5).code());
        Span link = s.stream().filter(x -> x.url() != null).findFirst().orElseThrow();
        assertEquals("the site", link.text());
        assertEquals("https://example.com/x", link.url());
        assertEquals(" now", s.get(s.size() - 1).text());
        assertEquals(1, MarkdownBlocks.inline("a * b and c").size(), "a lone star is just text");
        assertEquals("x**", MarkdownBlocks.inline("x**").get(0).text(), "an unclosed mark is just text");
    }
}
