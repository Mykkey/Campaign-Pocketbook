package dnd;

import dnd.model.Card;
import dnd.model.LinkType;
import dnd.model.Organisation;
import dnd.model.Relation;
import dnd.model.RelationType;
import dnd.relations.OverviewGraph;
import dnd.relations.RelationGraph;
import dnd.relations.RelationGraph.Edge;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RelationTypesTest {
    private static Card card(Map<String, Card> m, String id) {
        Card c = new Card();
        c.id = id;
        c.name = id.toUpperCase();
        m.put(id, c);
        return c;
    }

    private static Relation rel(Card from, RelationType t, String to) {
        Relation r = new Relation(to, t, "");
        from.relations.add(r);
        return r;
    }

    private static Relation custom(Card from, LinkType lt, String to) {
        Relation r = new Relation(to, RelationType.CUSTOM, "");
        r.linkTypeId = lt.id;
        from.relations.add(r);
        return r;
    }

    @Test
    void thereAreManyMoreKindsOfRelationshipAndEachIsWellFormed() {
        assertTrue(RelationType.values().length >= 30, "more than just ally and rival: " + RelationType.values().length);
        for (RelationType t : RelationType.values()) {
            assertFalse(t.label.isBlank(), t.name());
            assertFalse(t.shortLabel.isBlank(), t.name());
            assertTrue(t.colour.matches("#[0-9a-fA-F]{6}"), t.name() + " colour " + t.colour);
            assertEquals(t, t.inverse().inverse(), t.name());
            if (t.symmetric()) assertFalse(t.reverse, t.name() + " reads the same both ways, so it has no 'other side'");
            else assertNotEquals(t.reverse, t.inverse().reverse, t.name() + " and its partner: exactly one is the forward side");
            if (t.reverse) assertEquals(t.colour, t.inverse().colour, "a pair shares its colour");
        }
        for (RelationType t : List.of(RelationType.WORSHIPPER, RelationType.SUBJECT, RelationType.CREATION, RelationType.PROTEGE, RelationType.HUNTED)) {
            assertTrue(t.reverse, t.name());
        }
        for (RelationType t : List.of(RelationType.FRIEND, RelationType.LOVER, RelationType.ALLY, RelationType.RIVAL, RelationType.OTHER, RelationType.CUSTOM)) {
            assertTrue(t.symmetric(), t.name());
        }
        assertEquals(RelationType.DEITY, RelationType.WORSHIPPER.inverse());
        assertEquals("Worships", RelationType.WORSHIPPER.label);
        assertEquals("Is worshipped by", RelationType.DEITY.label);
        assertTrue(RelationType.PARENT.family && RelationType.SIBLING.family && !RelationType.RULER.family && !RelationType.FRIEND.family);
    }

    @Test
    void theOtherSideOfAPairIsTurnedRoundSoBothCardsAgree() {
        Map<String, Card> m = new LinkedHashMap<>();
        Card dan = card(m, "dan"), maris = card(m, "maris");
        rel(dan, RelationType.WORSHIPPER, "maris"); // Dan worships Maris
        RelationGraph g = new RelationGraph(m);
        Edge e = g.edges().get(0);
        assertEquals("maris", e.from);
        assertEquals("dan", e.to);
        assertEquals(RelationType.DEITY, e.type);
        assertTrue(e.directed);
        assertEquals("worshipped by", e.name);
        assertEquals(RelationType.DEITY.colour, e.colour);
        assertEquals("Worships", g.linksOf("dan").get(0).label());
        assertEquals("Is worshipped by", g.linksOf("maris").get(0).label());

        rel(maris, RelationType.DEITY, "dan"); // the same thing written on the other card counts once
        assertEquals(1, new RelationGraph(m).edges().size());
    }

    @Test
    void aFriendshipIsTheSameFromBothSidesAndHasNoArrow() {
        Map<String, Card> m = new LinkedHashMap<>();
        Card a = card(m, "a"), b = card(m, "b");
        rel(a, RelationType.FRIEND, "b");
        rel(b, RelationType.FRIEND, "a");
        List<Edge> edges = new RelationGraph(m).edges();
        assertEquals(1, edges.size());
        assertFalse(edges.get(0).directed);
        assertEquals("friend", edges.get(0).name);
    }

    @Test
    void aCustomLinkThatReadsTheSameBothWaysIsMergedAndKeepsItsColour() {
        Map<String, Card> m = new LinkedHashMap<>();
        Card a = card(m, "a"), b = card(m, "b");
        LinkType oath = new LinkType("Blood oath with", "", "#112233");
        custom(a, oath, "b");
        custom(b, oath, "a");
        RelationGraph g = new RelationGraph(m, List.of(oath));
        assertEquals(1, g.edges().size());
        Edge e = g.edges().get(0);
        assertEquals(RelationType.CUSTOM, e.type);
        assertEquals(oath.id, e.linkTypeId);
        assertEquals("Blood oath with", e.name);
        assertEquals("#112233", e.colour);
        assertFalse(e.directed);
        assertEquals("Blood oath with", g.linksOf("a").get(0).label());
        assertEquals("Blood oath with", g.linksOf("b").get(0).label());
    }

    @Test
    void aCustomLinkThatReadsDifferentlyKeepsItsDirectionAndTheOtherSidesWords() {
        Map<String, Card> m = new LinkedHashMap<>();
        Card a = card(m, "a"), b = card(m, "b");
        LinkType owes = new LinkType("Owes a favour to", "Is owed a favour by", "#d9731a");
        assertTrue(owes.directed());
        custom(b, owes, "a"); // B owes A a favour (written on B's card, though B sorts after A)
        RelationGraph g = new RelationGraph(m, List.of(owes));
        Edge e = g.edges().get(0);
        assertEquals("b", e.from, "the arrow runs from the card it was written on");
        assertEquals("a", e.to);
        assertTrue(e.directed);
        assertEquals("Owes a favour to", g.linksOf("b").get(0).label());
        assertEquals("Is owed a favour by", g.linksOf("a").get(0).label());
        // the same link the other way round is a different line
        custom(a, owes, "b");
        assertEquals(2, new RelationGraph(m, List.of(owes)).edges().size());
    }

    @Test
    void aDeletedCustomLinkBecomesAPlainConnection() {
        Map<String, Card> m = new LinkedHashMap<>();
        Card a = card(m, "a");
        card(m, "b");
        LinkType gone = new LinkType("Forgotten", "", "#ff0000");
        custom(a, gone, "b");
        RelationGraph g = new RelationGraph(m, List.of()); // not in the project any more
        assertEquals(1, g.edges().size());
        assertEquals(RelationType.OTHER, g.edges().get(0).type);
        assertEquals("Connected to", g.linksOf("a").get(0).label());
    }

    @Test
    void customLinksAreSavedWithTheProjectAndTheRelationsThatUseThem(@TempDir Path tmp) throws Exception {
        ProjectStore s = ProjectStore.create(tmp, "Camp");
        LinkType t = new LinkType("Sworn to", "Is sworn to by", "#ABCDEF");
        s.project.linkTypes.add(t);
        Card a = new Card();
        a.name = "A";
        s.createCard(s.project.root, a);
        Card b = new Card();
        b.name = "B";
        s.createCard(s.project.root, b);
        Relation r = new Relation(b.id, RelationType.CUSTOM, "since the war");
        r.linkTypeId = t.id;
        a.relations.add(r);
        s.markCardDirty(a);
        s.markProjectDirty();
        s.flush();

        ProjectStore back = ProjectStore.open(tmp, "Camp");
        assertEquals(1, back.project.linkTypes.size());
        LinkType loaded = back.project.linkTypes.get(0);
        assertEquals("Sworn to", loaded.name);
        assertEquals("Is sworn to by", loaded.reverseName);
        assertEquals("#ABCDEF", loaded.colour);
        assertEquals(t.id, loaded.id);
        Relation lr = back.card(a.id).relations.get(0);
        assertEquals(RelationType.CUSTOM, lr.type);
        assertEquals(t.id, lr.linkTypeId);
        assertEquals(t.id, lr.copy().linkTypeId, "a copy keeps the link");
        assertEquals("#8a94a3", new LinkType("x", "", "not a colour").colour, "a bad colour falls back to grey");
    }

    @Test
    void theEverythingMapDrawsTheNewKindsAndCustomLinksWithTheirOwnColourAndArrow() {
        Map<String, Card> m = new LinkedHashMap<>();
        Card god = card(m, "god"), mortal = card(m, "mortal"), pal = card(m, "pal");
        god.kind = Card.Kind.DEITY;
        rel(mortal, RelationType.WORSHIPPER, "god");
        LinkType owes = new LinkType("Owes a favour to", "Is owed a favour by", "#00AA55");
        custom(mortal, owes, "pal");
        rel(god, RelationType.FRIEND, "pal");
        Map<String, Organisation> orgs = new LinkedHashMap<>();

        OverviewGraph g = OverviewGraph.build(m, orgs, List.of(owes), OverviewGraph.Options.ALL);
        assertEquals(OverviewGraph.NodeKind.DEITY, g.nodes().get("god").kind(), "a deity is its own kind of box");
        OverviewGraph.Edge worship = g.edges().stream().filter(e -> e.name().equals("worshipped by")).findFirst().orElseThrow();
        assertEquals(OverviewGraph.EdgeKind.LINK, worship.kind());
        assertTrue(worship.directed());
        assertEquals("god", worship.from());
        assertEquals(RelationType.DEITY.colour, worship.colour());
        OverviewGraph.Edge favour = g.edges().stream().filter(e -> e.name().equals("Owes a favour to")).findFirst().orElseThrow();
        assertEquals("#00AA55", favour.colour());
        assertEquals("mortal", favour.from());
        assertTrue(favour.directed());
        OverviewGraph.Edge friend = g.edges().stream().filter(e -> e.name().equals("friend")).findFirst().orElseThrow();
        assertFalse(friend.directed());
        assertEquals(OverviewGraph.Group.RELATIONSHIPS, friend.kind().group);

        OverviewGraph none = OverviewGraph.build(m, orgs, List.of(owes), new OverviewGraph.Options(true, false, false));
        assertTrue(none.edges().isEmpty(), "with Relationships unticked the new kinds are left out");
    }
}
