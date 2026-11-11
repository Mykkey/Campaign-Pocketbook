package dnd;

import dnd.model.Card;
import dnd.model.Organisation;
import dnd.model.RelationType;
import dnd.relations.FamilyTreeLayout;
import dnd.relations.GraphLayout;
import dnd.relations.OrgHierarchy;
import dnd.relations.OverviewGraph;
import dnd.relations.OverviewGraph.EdgeKind;
import dnd.relations.OverviewGraph.Options;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrgHierarchyTest {
    private static Organisation guild() {
        Organisation o = new Organisation();
        o.id = "guild";
        o.name = "Silver Hand";
        for (String m : new String[]{"boss", "lt1", "lt2", "thug1", "thug2", "thug3"}) o.addMember(m, "");
        o.setBoss("lt1", "boss");
        o.setBoss("lt2", "boss");
        o.setBoss("thug1", "lt1");
        o.setBoss("thug2", "lt1");
        o.setBoss("thug3", "lt2");
        return o;
    }

    @Test
    void reportingLinesNeverGoRoundInACircle() {
        Organisation o = guild();
        assertNull(o.bossOf("boss"));
        assertEquals("boss", o.bossOf("lt1"));
        assertTrue(o.isUnder("thug1", "boss"));
        assertFalse(o.isUnder("boss", "thug1"));
        assertFalse(o.setBoss("boss", "thug1"), "the boss cannot report to someone beneath them");
        assertFalse(o.setBoss("lt1", "lt1"), "nobody reports to themselves");
        assertFalse(o.setBoss("lt1", "stranger"), "the boss must be a member");
        assertNull(o.bossOf("boss"));
        assertTrue(o.possibleBosses("lt1").contains("boss"));
        assertFalse(o.possibleBosses("lt1").contains("thug1"));
        assertTrue(o.setBoss("lt1", null));
        assertNull(o.bossOf("lt1"));
        assertEquals(List.of("lt2"), o.directReports("boss"));
    }

    @Test
    void removingAMemberMovesTheirReportsUpOne() {
        Organisation o = guild();
        assertTrue(o.removeMember("lt1"));
        assertEquals("boss", o.bossOf("thug1"));
        assertEquals("boss", o.bossOf("thug2"));
        assertTrue(o.removeMember("boss"));
        assertNull(o.bossOf("thug1"));
        assertNull(o.bossOf("lt2"), "they are now at the top");
    }

    @Test
    void aBossWhoLeftTheGroupLeavesPeopleAtTheTop() {
        Organisation o = guild();
        o.members.removeIf(m -> m.id.equals("boss")); // removed without the clean-up, as an old file might be
        assertNull(o.bossOf("lt1"));
        assertTrue(o.hasHierarchy(), "the others still report to each other");
    }

    @Test
    void theChartPutsBossesAboveTheirPeopleAndCentresThem() {
        Organisation o = guild();
        FamilyTreeLayout.Layout l = OrgHierarchy.layout(o);
        assertEquals(0, l.spots().get("boss").generation());
        assertEquals(1, l.spots().get("lt1").generation());
        assertEquals(2, l.spots().get("thug1").generation());
        assertEquals(1.5, l.spots().get("boss").x(), 1e-9, "the boss is centred over the whole block of people beneath");
        assertTrue(l.spots().get("lt1").x() < l.spots().get("lt2").x());
        assertEquals(3, l.generations());
        assertEquals("boss", OrgHierarchy.reportLines(o).get(0)[0]);
        assertEquals("lt1", OrgHierarchy.reportLines(o).get(0)[1]);
    }

    @Test
    void aCopyKeepsWhoReportsToWhom() {
        Organisation c = guild().copy();
        assertEquals("lt1", c.bossOf("thug1"));
        c.setBoss("thug1", "lt2");
        assertEquals("lt1", guild().bossOf("thug1"));
    }

    // ---------------------------------------------------------------- the big picture

    private static Card card(String id, String name, Card.Kind kind) {
        Card c = new Card();
        c.id = id;
        c.name = name;
        c.kind = kind;
        return c;
    }

    @Test
    void theBigPictureJoinsFamilyRelationshipsAndOrganisations() {
        Map<String, Card> cards = new LinkedHashMap<>();
        Card dad = card("dad", "Dad", Card.Kind.NPC), kid = card("kid", "Kid", Card.Kind.PLAYER), rival = card("rival", "Rival", Card.Kind.NPC);
        Card loner = card("loner", "Loner", Card.Kind.NPC);
        for (Card c : List.of(dad, kid, rival, loner)) cards.put(c.id, c);
        dad.relations.add(new dnd.model.Relation("kid", RelationType.PARENT, ""));
        kid.relations.add(new dnd.model.Relation("rival", RelationType.RIVAL, "since school"));
        Organisation guild = guild2();
        Map<String, Organisation> orgs = new LinkedHashMap<>();
        orgs.put(guild.id, guild);

        OverviewGraph g = OverviewGraph.build(cards, orgs, Options.ALL);
        assertFalse(g.nodes().containsKey("loner"), "someone with no links is not drawn");
        assertEquals(OverviewGraph.NodeKind.ORGANISATION, g.nodes().get("guild").kind());
        assertTrue(g.edges().stream().anyMatch(e -> e.kind() == EdgeKind.PARENT && e.from().equals("dad") && e.to().equals("kid")));
        assertTrue(g.edges().stream().anyMatch(e -> e.kind() == EdgeKind.RIVAL && e.label().equals("since school")));
        assertTrue(g.edges().stream().anyMatch(e -> e.kind() == EdgeKind.MEMBER && e.from().equals("guild") && e.to().equals("dad") && e.label().equals("Leader")));
        assertTrue(g.edges().stream().anyMatch(e -> e.kind() == EdgeKind.REPORTS && e.from().equals("dad") && e.to().equals("kid")));
        assertFalse(g.edges().stream().anyMatch(e -> e.kind() == EdgeKind.MEMBER && e.to().equals("kid")), "a member with a boss hangs from the boss, not the group");
        assertEquals(1, g.components().size(), "all joined into one picture");
        assertEquals(4, g.componentOf("guild").size());
        assertTrue(g.componentOf("loner").isEmpty());

        OverviewGraph family = OverviewGraph.build(cards, orgs, new Options(true, false, false));
        assertFalse(family.nodes().containsKey("guild"));
        assertEquals(2, family.nodes().size());
        OverviewGraph onlyOrgs = OverviewGraph.build(cards, orgs, new Options(false, false, true));
        assertEquals(3, onlyOrgs.nodes().size());
    }

    private static Organisation guild2() {
        Organisation o = new Organisation();
        o.id = "guild";
        o.name = "Guild";
        o.addMember("dad", "Leader");
        o.addMember("kid", "Runner");
        o.addMember("ghost", "Deleted");
        o.setBoss("kid", "dad");
        return o;
    }

    @Test
    void theForceLayoutKeepsEveryBoxApartAndIsRepeatable() {
        List<String> ids = new ArrayList<>();
        List<String[]> edges = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            ids.add("n" + i);
            if (i > 0) edges.add(new String[]{"n" + (i - 1) / 2, "n" + i});
        }
        Map<String, double[]> a = GraphLayout.layout(ids, edges, 230, 150, 46);
        Map<String, double[]> b = GraphLayout.layout(ids, edges, 230, 150, 46);
        assertEquals(25, a.size());
        for (String id : ids) {
            assertArrayEquals(a.get(id), b.get(id), 1e-9, "same input, same picture");
            assertTrue(a.get(id)[0] >= 0 && a.get(id)[1] >= 0);
            assertFalse(Double.isNaN(a.get(id)[0]) || Double.isNaN(a.get(id)[1]));
        }
        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                double[] p = a.get(ids.get(i)), q = a.get(ids.get(j));
                boolean overlap = Math.abs(p[0] - q[0]) < 150 && Math.abs(p[1] - q[1]) < 46;
                assertFalse(overlap, ids.get(i) + " overlaps " + ids.get(j));
            }
        }
    }

    @Test
    void tinyGraphsAreHandled() {
        assertTrue(GraphLayout.layout(List.of(), List.of(), 200, 150, 46).isEmpty());
        assertEquals(1, GraphLayout.layout(List.of("a"), List.of(), 200, 150, 46).size());
        Map<String, double[]> two = GraphLayout.layout(List.of("a", "b"), java.util.Collections.singletonList(new String[]{"a", "b"}), 200, 150, 46);
        assertEquals(200, two.get("b")[0] - two.get("a")[0], 1e-9);
    }
}
