package dnd;

import dnd.model.Card;
import dnd.model.Relation;
import dnd.model.RelationType;
import dnd.relations.FamilyTreeLayout;
import dnd.relations.FamilyTreeLayout.Layout;
import dnd.relations.FamilyTreeLayout.Spot;
import dnd.relations.RelationGraph;
import dnd.relations.RelationGraph.Edge;
import dnd.relations.RelationGraph.Link;
import dnd.relations.WebLayout;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RelationshipsTest {
    private static Card card(Map<String, Card> m, String id) {
        Card c = new Card();
        c.id = id;
        c.name = id.toUpperCase();
        m.put(id, c);
        return c;
    }

    private static void rel(Card from, RelationType t, String to) {
        from.relations.add(new Relation(to, t, ""));
    }

    // ---------------------------------------------------------------- types

    @Test
    void inversesPairUp() {
        assertEquals(RelationType.CHILD, RelationType.PARENT.inverse());
        assertEquals(RelationType.PARENT, RelationType.CHILD.inverse());
        assertEquals(RelationType.STUDENT, RelationType.MENTOR.inverse());
        assertEquals(RelationType.EMPLOYER, RelationType.EMPLOYEE.inverse());
        for (RelationType t : RelationType.values()) assertEquals(t, t.inverse().inverse());
        for (RelationType t : List.of(RelationType.SPOUSE, RelationType.SIBLING, RelationType.ALLY, RelationType.RIVAL, RelationType.OTHER)) {
            assertTrue(t.symmetric(), t.name());
        }
        assertTrue(RelationType.PARENT.family && RelationType.SPOUSE.family && !RelationType.ALLY.family);
    }

    // ---------------------------------------------------------------- graph

    @Test
    void oneSidedRelationshipsWorkBothWays() {
        Map<String, Card> m = new LinkedHashMap<>();
        Card dan = card(m, "dan");
        card(m, "mira");
        rel(dan, RelationType.PARENT, "mira");
        RelationGraph g = new RelationGraph(m);

        assertEquals(1, g.edges().size());
        Edge e = g.edges().get(0);
        assertEquals("dan", e.from);
        assertEquals("mira", e.to);
        assertEquals(RelationType.PARENT, e.type);

        Link fromMira = g.linksOf("mira").get(0);
        assertEquals("dan", fromMira.otherId());
        assertEquals(RelationType.CHILD, fromMira.type(), "Mira sees herself as Dan's child");
        assertFalse(fromMira.listedOnThis(), "it is written on Dan's card, not hers");
        assertTrue(g.linksOf("dan").get(0).listedOnThis());
    }

    @Test
    void theSameRelationshipOnBothCardsCountsOnce() {
        Map<String, Card> m = new LinkedHashMap<>();
        Card dan = card(m, "dan");
        Card mira = card(m, "mira");
        rel(dan, RelationType.PARENT, "mira");
        rel(mira, RelationType.CHILD, "dan");
        RelationGraph g = new RelationGraph(m);
        assertEquals(1, g.edges().size());
        assertTrue(g.linksOf("dan").get(0).listedOnThis());
        assertTrue(g.linksOf("mira").get(0).listedOnThis());
    }

    @Test
    void symmetricRelationshipsMergeWhicheverWayRound() {
        Map<String, Card> m = new LinkedHashMap<>();
        Card a = card(m, "a"), b = card(m, "b");
        rel(a, RelationType.ALLY, "b");
        rel(b, RelationType.ALLY, "a");
        a.relations.get(0).note = "since the siege";
        assertEquals(1, new RelationGraph(m).edges().size());
        assertEquals("since the siege", new RelationGraph(m).edges().get(0).note);
    }

    @Test
    void studentsAndEmployeesAreStoredFromTheMentorAndEmployer() {
        Map<String, Card> m = new LinkedHashMap<>();
        Card pupil = card(m, "pupil"), master = card(m, "master"), boss = card(m, "boss");
        rel(pupil, RelationType.STUDENT, "master");
        rel(pupil, RelationType.EMPLOYEE, "boss");
        RelationGraph g = new RelationGraph(m);
        Edge mentor = g.edges().stream().filter(e -> e.type == RelationType.MENTOR).findFirst().orElseThrow();
        assertEquals("master", mentor.from);
        assertEquals("pupil", mentor.to);
        Edge employer = g.edges().stream().filter(e -> e.type == RelationType.EMPLOYER).findFirst().orElseThrow();
        assertEquals("boss", employer.from);
        assertEquals(1, g.webEdges().size() - 1);
        assertEquals(0, g.familyEdges().size());
    }

    @Test
    void relationsToMissingOrSelfAreIgnored() {
        Map<String, Card> m = new LinkedHashMap<>();
        Card a = card(m, "a");
        rel(a, RelationType.ALLY, "ghost");
        rel(a, RelationType.ALLY, "a");
        a.relations.add(new Relation(null, RelationType.ALLY, ""));
        assertTrue(new RelationGraph(m).edges().isEmpty());
    }

    @Test
    void componentsGroupConnectedPeopleSeparatelyForFamilyAndWeb() {
        Map<String, Card> m = new LinkedHashMap<>();
        Card a = card(m, "a"), b = card(m, "b"), c = card(m, "c"), d = card(m, "d"), e = card(m, "e");
        rel(a, RelationType.PARENT, "b");
        rel(b, RelationType.SPOUSE, "c");
        rel(d, RelationType.SIBLING, "e");
        rel(a, RelationType.ALLY, "d");
        RelationGraph g = new RelationGraph(m);
        List<List<String>> fam = g.components(true);
        assertEquals(2, fam.size());
        assertEquals(3, fam.get(0).size(), "biggest first");
        assertEquals(Set.of("a", "b", "c"), new HashSet<>(fam.get(0)));
        assertEquals(1, g.components(false).size());
        assertEquals(Set.of("d", "e"), new HashSet<>(g.componentOf("e", true)));
        assertEquals(List.of("zzz"), g.componentOf("zzz", true));
    }

    @Test
    void linksAreSortedByTheOtherPersonsName() {
        Map<String, Card> m = new LinkedHashMap<>();
        Card a = card(m, "a");
        card(m, "z");
        card(m, "m");
        rel(a, RelationType.ALLY, "z");
        rel(a, RelationType.RIVAL, "m");
        List<Link> links = new RelationGraph(m).linksOf("a");
        assertEquals("m", links.get(0).otherId());
        assertEquals("z", links.get(1).otherId());
    }

    // ---------------------------------------------------------------- family tree

    private static List<String[]> pairs(String... flat) {
        List<String[]> out = new ArrayList<>();
        for (int i = 0; i < flat.length; i += 2) out.add(new String[]{flat[i], flat[i + 1]});
        return out;
    }

    @Test
    void threeGenerationsLineUp() {
        List<String> people = List.of("g1", "g2", "c", "s", "k1", "k2");
        Layout l = FamilyTreeLayout.layout(people,
                pairs("g1", "c", "g2", "c", "c", "k1", "c", "k2", "s", "k1", "s", "k2"),
                pairs("g1", "g2", "c", "s"), pairs());
        Map<String, Spot> s = l.spots();
        assertEquals(6, s.size());
        assertEquals(3, l.generations());
        assertEquals(0, s.get("g1").generation());
        assertEquals(0, s.get("g2").generation());
        assertEquals(1, s.get("c").generation());
        assertEquals(1, s.get("s").generation(), "a partner shares the row");
        assertEquals(2, s.get("k1").generation());
        assertEquals(2, s.get("k2").generation());
        assertEquals(1.0, Math.abs(s.get("g1").x() - s.get("g2").x()), 1e-9, "partners sit side by side");
        assertEquals(1.0, Math.abs(s.get("c").x() - s.get("s").x()), 1e-9);
        assertEquals(1.0, Math.abs(s.get("k1").x() - s.get("k2").x()), 1e-9, "siblings sit together");
        double kidsMid = (s.get("k1").x() + s.get("k2").x()) / 2;
        assertEquals(kidsMid, (s.get("c").x() + s.get("s").x()) / 2, 1e-9, "parents are centred over their children");
        assertEquals(((s.get("c").x() + s.get("s").x()) / 2), (s.get("g1").x() + s.get("g2").x()) / 2, 1e-9);
    }

    @Test
    void nobodyOverlapsInTheirRow() {
        // a bigger, messier family
        List<String> people = List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j");
        Layout l = FamilyTreeLayout.layout(people,
                pairs("a", "c", "a", "d", "a", "e", "c", "f", "c", "g", "e", "h", "i", "j"),
                pairs("a", "b", "c", "i"), pairs("c", "d", "d", "e"));
        Map<Integer, List<Double>> rows = new java.util.TreeMap<>();
        for (Spot s : l.spots().values()) rows.computeIfAbsent(s.generation(), k -> new ArrayList<>()).add(s.x());
        for (List<Double> xs : rows.values()) {
            java.util.Collections.sort(xs);
            for (int i = 1; i < xs.size(); i++) assertTrue(xs.get(i) - xs.get(i - 1) >= 1.0 - 1e-9, "overlap in a row: " + xs);
        }
        for (Spot s : l.spots().values()) assertTrue(s.x() > 0 && s.x() < l.width(), "inside the drawing");
        assertEquals(10, l.spots().size());
    }

    @Test
    void separateFamiliesSitSideBySide() {
        Layout l = FamilyTreeLayout.layout(List.of("a", "b", "x", "y"), pairs("a", "b", "x", "y"), pairs(), pairs());
        double aMax = Math.max(l.spots().get("a").x(), l.spots().get("b").x());
        double xMin = Math.min(l.spots().get("x").x(), l.spots().get("y").x());
        assertTrue(xMin > aMax);
        assertEquals(2, l.generations());
    }

    @Test
    void parentlessSiblingsShareARowAndSitTogether() {
        Layout l = FamilyTreeLayout.layout(List.of("p", "q", "r"), pairs(), pairs(), pairs("p", "q", "q", "r"));
        assertEquals(1, l.generations());
        List<Double> xs = new ArrayList<>();
        for (Spot s : l.spots().values()) xs.add(s.x());
        java.util.Collections.sort(xs);
        assertEquals(1.0, xs.get(1) - xs.get(0), 1e-9);
        assertEquals(1.0, xs.get(2) - xs.get(1), 1e-9);
    }

    @Test
    void aParentCycleDoesNotHang() {
        Layout l = FamilyTreeLayout.layout(List.of("a", "b", "c"), pairs("a", "b", "b", "c", "c", "a"), pairs(), pairs());
        assertEquals(3, l.spots().size());
    }

    @Test
    void emptyAndSinglePersonLayouts() {
        assertTrue(FamilyTreeLayout.layout(List.of(), pairs(), pairs(), pairs()).spots().isEmpty());
        Layout one = FamilyTreeLayout.layout(List.of("solo"), pairs(), pairs(), pairs());
        assertEquals(0, one.spots().get("solo").generation());
        assertEquals(0.5, one.spots().get("solo").x(), 1e-9);
        assertEquals(1.0, one.width(), 1e-9);
    }

    // ---------------------------------------------------------------- web

    @Test
    void webSpreadsPeopleAroundACircle() {
        Map<String, double[]> p = WebLayout.circle(List.of("a", "b", "c", "d"), 100);
        double r = WebLayout.radius(4, 100);
        for (double[] xy : p.values()) assertEquals(r, Math.hypot(xy[0], xy[1]), 1e-9);
        assertEquals(0, p.get("a")[0], 1e-9);
        assertTrue(p.get("a")[1] < 0, "starts at the top");
        double d = Math.hypot(p.get("a")[0] - p.get("b")[0], p.get("a")[1] - p.get("b")[1]);
        assertTrue(d >= 99.9, "people are at least the spacing apart: " + d);
        assertEquals(0, WebLayout.radius(1, 100));
        assertEquals(1, WebLayout.circle(List.of("only"), 100).size());
    }

    // ---------------------------------------------------------------- saved on the card

    @Test
    void relationsAreSavedWithTheCard(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        Card dan = store.createCard(store.project.root, new Card());
        Card mira = store.createCard(store.project.root, new Card());
        dan.relations.add(new Relation(mira.id, RelationType.PARENT, "adopted"));
        store.markCardDirty(dan);
        store.flush();

        Card loaded = ProjectStore.open(tmp, "Camp").card(dan.id);
        assertEquals(1, loaded.relations.size());
        assertEquals(mira.id, loaded.relations.get(0).targetCardId);
        assertEquals(RelationType.PARENT, loaded.relations.get(0).type);
        assertEquals("adopted", loaded.relations.get(0).note);
        assertTrue(dan.copy().relations.isEmpty(), "a copy is a different person");
    }
}