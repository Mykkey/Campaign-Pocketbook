package dnd;

import dnd.model.Session;
import dnd.model.TreeNode;
import dnd.search.SearchService;
import dnd.storage.ProjectStore;
import dnd.ui.SessionCardView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SessionTest {
    private static Session session(int number, String name, String date, int day) {
        Session s = new Session();
        s.number = number;
        s.name = name;
        s.realDate = date;
        s.campaignDay = day;
        return s;
    }

    @Test
    void sessionsAreOrderedByNumberThenDate() {
        Session c = session(3, "C", "2026-03-01", 0);
        Session a = session(1, "A", "2026-01-01", 0);
        Session b2 = session(2, "B2", "2026-02-10", 0);
        Session b1 = session(2, "B1", "2026-02-01", 0);
        List<Session> ordered = Session.inOrder(List.of(c, b2, a, b1));
        assertEquals(List.of(a, b1, b2, c), ordered);
        assertEquals(4, Session.nextNumber(ordered));
        assertEquals(1, Session.nextNumber(List.of()));
    }

    @Test
    void thePreviousSessionIsTheNextLowerNumber() {
        Session s1 = session(1, "One", "", 0);
        Session s2 = session(2, "Two", "", 0);
        Session s5 = session(5, "Five", "", 0);
        s1.id = "a"; s2.id = "b"; s5.id = "c";
        assertNull(Session.previous(List.of(s1, s2, s5), s1));
        assertSame(s1, Session.previous(List.of(s1, s2, s5), s2));
        assertSame(s2, Session.previous(List.of(s1, s2, s5), s5), "a gap in the numbers is fine");
    }

    @Test
    void labelsAndTimeGaps() {
        assertEquals("Session 4", session(4, "Session 4", "", 0).label());
        assertEquals("Session 4", session(4, "", "", 0).label());
        assertEquals("Session 4: The Lighthouse", session(4, "The Lighthouse", "", 0).label());
        assertEquals("3 days later", Session.gapText(session(1, "a", "", 10), session(2, "b", "", 13)));
        assertEquals("1 day later", Session.gapText(session(1, "a", "", 10), session(2, "b", "", 11)));
        assertEquals("the same day", Session.gapText(session(1, "a", "", 10), session(2, "b", "", 10)));
        assertNull(Session.gapText(session(1, "a", "", 0), session(2, "b", "", 13)));
        assertNull(Session.gapText(session(1, "a", "", 20), session(2, "b", "", 13)), "time does not run backwards");
        assertNull(Session.gapText(null, session(2, "b", "", 13)));
    }

    @Test
    void datesTextJoinsWhatIsFilledIn() {
        Session s = session(1, "x", "2026-03-12", 31);
        assertEquals("2026-03-12", SessionCardView.datesText(session(1, "x", "2026-03-12", 0)));
        s.inWorldDate = "14th of Mirtul";
        assertEquals("2026-03-12  -  14th of Mirtul (day 31)", SessionCardView.datesText(s));
        assertEquals("(day 5)", SessionCardView.datesText(session(1, "x", "", 5)));
        assertEquals("", SessionCardView.datesText(session(1, "x", "", 0)));
    }

    @Test
    void sessionsAreSavedFoundInOrderRenamedAndDeleted(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        Session two = session(2, "The Cellar", "2026-02-01", 5);
        two.recap = "They found a trapdoor under the lamp.";
        two.summary = "A long fight with rats.";
        Session one = session(1, "Arrival", "2026-01-01", 1);
        store.createSession(store.project.root, two);
        store.createSession(store.project.root, one);
        store.flush();
        assertTrue(Files.isRegularFile(store.dir.resolve("sessions").resolve(two.id + ".session")));

        ProjectStore reopened = ProjectStore.open(tmp, "Camp");
        List<Session> all = reopened.allSessions();
        assertEquals(List.of("Arrival", "The Cellar"), List.of(all.get(0).name, all.get(1).name));
        assertEquals("They found a trapdoor under the lamp.", all.get(1).recap);
        assertEquals(5, all.get(1).campaignDay);

        TreeNode node = reopened.project.root.find(two.id);
        assertTrue(node.isSession());
        assertFalse(node.isBoard());
        reopened.renameNode(node, "The Wet Cellar");
        assertEquals("The Wet Cellar", reopened.session(two.id).name);
        reopened.flush();
        reopened.deleteNode(node);
        assertNull(reopened.session(two.id));
        assertFalse(Files.exists(reopened.dir.resolve("sessions").resolve(two.id + ".session")));
        assertEquals(1, reopened.allSessions().size());
    }

    @Test
    void searchFindsSessionsByNameRecapAndNotes(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        Session s = session(1, "Arrival", "2026-01-01", 1);
        s.recap = "The party reached Redbrook at dusk.";
        s.summary = "They argued with the gatekeeper about the toll.";
        s.nextSteps = "Find out who forged the ledger.";
        store.createSession(store.project.root, s);
        assertEquals("Session", SearchService.search(store, "arrival", 10).get(0).snippet);
        assertTrue(SearchService.search(store, "redbrook", 10).get(0).snippet.startsWith("Session - recap:"));
        assertTrue(SearchService.search(store, "gatekeeper", 10).get(0).snippet.contains("what happened:"));
        assertTrue(SearchService.search(store, "forged", 10).get(0).snippet.contains("next:"));
    }
}
