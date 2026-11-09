package dnd.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** One game session: when it was played, what happened, a recap for next time and the items it involved. */
public class Session extends ProjectEntry {
    /** 1, 2, 3... in the order the sessions were played. */
    public int number = 1;
    /** The real-world date played, as yyyy-MM-dd, or blank. */
    public String realDate = "";
    /** The date in the game world, as the DM writes it ("14th of Mirtul, 1492 DR"), or blank. */
    public String inWorldDate = "";
    /** The day of the campaign (day 1 is the first day), or 0 when not tracked. Used to show time passing. */
    public int campaignDay;
    /** What happened. */
    public String summary = "";
    /** "Last time on..." : the few lines to read out at the start of the next session. */
    public String recap = "";
    /** Ids of the cards, maps, whiteboards, quests and items that came up. */
    public List<String> related = new ArrayList<>();
    /** Plans and loose ends for the next session. */
    public String nextSteps = "";

    public Session() { }

    public Session copy() {
        Session s = new Session();
        s.id = id; s.name = name; s.number = number; s.realDate = realDate; s.inWorldDate = inWorldDate;
        s.campaignDay = campaignDay; s.summary = summary; s.recap = recap; s.related = new ArrayList<>(related);
        s.nextSteps = nextSteps;
        return s;
    }

    /** Sessions in the order they were played (by number, then by real date, then by name). */
    public static List<Session> inOrder(List<Session> sessions) {
        List<Session> out = new ArrayList<>(sessions);
        out.sort(Comparator.comparingInt((Session s) -> s.number)
                .thenComparing(s -> s.realDate == null ? "" : s.realDate)
                .thenComparing(s -> s.name == null ? "" : s.name));
        return out;
    }

    /** The number a new session should get: one more than the highest so far. */
    public static int nextNumber(List<Session> sessions) {
        int max = 0;
        for (Session s : sessions) max = Math.max(max, s.number);
        return max + 1;
    }

    /** The session played just before {@code current} (the one with the next lower number), or null. */
    public static Session previous(List<Session> sessions, Session current) {
        Session best = null;
        for (Session s : sessions) {
            if (s == current || s.id.equals(current.id)) continue;
            if (s.number < current.number && (best == null || s.number > best.number)) best = s;
        }
        return best;
    }

    /** "Session 4: The Lighthouse", or just "Session 4" when the title is the default. */
    public String label() {
        String n = name == null ? "" : name.trim();
        String base = "Session " + number;
        return n.isEmpty() || n.equalsIgnoreCase(base) ? base : base + ": " + n;
    }

    /** Days between two sessions in the game world ("3 days later"), or null if either has no day number. */
    public static String gapText(Session earlier, Session later) {
        if (earlier == null || later == null || earlier.campaignDay <= 0 || later.campaignDay <= 0) return null;
        int d = later.campaignDay - earlier.campaignDay;
        if (d == 0) return "the same day";
        if (d < 0) return null;
        return d + (d == 1 ? " day later" : " days later");
    }
}
