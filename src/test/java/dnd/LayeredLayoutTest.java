package dnd;

import dnd.relations.LayeredLayout;
import dnd.relations.LayeredLayout.Spot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LayeredLayoutTest {
    private static List<String[]> L(String[]... e) {
        return java.util.Arrays.asList(e);
    }

    private static String[] p(String a, String b) {
        return new String[]{a, b};
    }

    /** No two boxes in a row closer than one slot. */
    private static void assertRowsHaveRoom(Map<String, Spot> m) {
        List<String> ids = new ArrayList<>(m.keySet());
        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                Spot a = m.get(ids.get(i)), b = m.get(ids.get(j));
                if (a.rank() == b.rank()) assertTrue(Math.abs(a.x() - b.x()) >= 1 - 1e-9, ids.get(i) + " crowds " + ids.get(j));
            }
        }
    }

    @Test
    void whoeverIsAboveIsInAnEarlierRow() {
        // org -> boss -> lieutenant -> thug, and the boss's parent above the boss
        Map<String, Spot> m = LayeredLayout.layout(List.of("org", "boss", "lt", "thug", "mum"),
                L(p("org", "boss"), p("boss", "lt"), p("lt", "thug"), p("mum", "boss")), List.of(), List.of());
        assertTrue(m.get("org").rank() < m.get("boss").rank());
        assertTrue(m.get("mum").rank() < m.get("boss").rank());
        assertTrue(m.get("boss").rank() < m.get("lt").rank());
        assertTrue(m.get("lt").rank() < m.get("thug").rank());
        assertRowsHaveRoom(m);
    }

    @Test
    void partnersAndSiblingsShareARowAndSitTogether() {
        Map<String, Spot> m = LayeredLayout.layout(List.of("dad", "mum", "a", "b", "c"),
                L(p("dad", "a"), p("dad", "b"), p("mum", "a"), p("mum", "b"), p("dad", "c")),
                L(p("dad", "mum"), p("a", "b")), List.of());
        assertEquals(m.get("dad").rank(), m.get("mum").rank());
        assertEquals(m.get("a").rank(), m.get("b").rank());
        assertEquals(1.0, Math.abs(m.get("dad").x() - m.get("mum").x()), 1e-9, "partners side by side");
        assertEquals(1.0, Math.abs(m.get("a").x() - m.get("b").x()), 1e-9, "siblings side by side");
        assertTrue(m.get("dad").rank() < m.get("a").rank());
        assertRowsHaveRoom(m);
    }

    @Test
    void someoneWithOnlyLooseLinksJoinsTheRowOfTheirFriend() {
        Map<String, Spot> m = LayeredLayout.layout(List.of("boss", "lt", "friend"),
                L(p("boss", "lt")), List.of(), L(p("friend", "lt")));
        assertEquals(m.get("lt").rank(), m.get("friend").rank());
        assertRowsHaveRoom(m);
    }

    @Test
    void aLeaderWithNobodyAboveThemSitsJustAboveTheirPeople() {
        // "ceo" is at the top of a chain; "founder" only has a link down to a person in row 2
        Map<String, Spot> m = LayeredLayout.layout(List.of("ceo", "vp", "dev", "founder"),
                L(p("ceo", "vp"), p("vp", "dev"), p("founder", "dev")), List.of(), List.of());
        assertEquals(m.get("vp").rank(), m.get("founder").rank(), "the founder is next to the vp, directly above the dev");
    }

    @Test
    void aCircleOfCommandStillFinishesAndKeepsRoom() {
        Map<String, Spot> m = LayeredLayout.layout(List.of("a", "b", "c"), L(p("a", "b"), p("b", "c"), p("c", "a")), List.of(), List.of());
        assertEquals(3, m.size());
        assertRowsHaveRoom(m);
    }

    @Test
    void theSameInputGivesTheSamePicture() {
        List<String> ids = new ArrayList<>();
        List<String[]> down = new ArrayList<>(), loose = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            ids.add("n" + i);
            if (i > 0) down.add(p("n" + (i - 1) / 2, "n" + i));
            if (i > 3 && i % 5 == 0) loose.add(p("n" + i, "n" + (i - 3)));
        }
        Map<String, Spot> a = LayeredLayout.layout(ids, down, List.of(), loose);
        Map<String, Spot> b = LayeredLayout.layout(ids, down, List.of(), loose);
        assertEquals(a, b);
        assertRowsHaveRoom(a);
        for (Spot s : a.values()) assertTrue(s.x() >= 0 && !Double.isNaN(s.x()));
    }

    @Test
    void tinyInputsAreHandled() {
        assertTrue(LayeredLayout.layout(List.of(), List.of(), List.of(), List.of()).isEmpty());
        Map<String, Spot> one = LayeredLayout.layout(List.of("a"), List.of(), List.of(), List.of());
        assertEquals(0, one.get("a").rank());
        Map<String, Spot> ignored = LayeredLayout.layout(List.of("a", "b"), L(p("a", "zzz"), p("a", "a")), List.of(), List.of());
        assertEquals(2, ignored.size());
    }
}
