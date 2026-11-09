package dnd.relations;

import dnd.model.Card;
import dnd.model.Relation;
import dnd.model.RelationType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every relationship between cards, put together from what is written on each card. A relationship only needs to
 * be entered on one card; this works out the other side and merges duplicates, so "Dan is a parent of Mira" on Dan's
 * card and "Mira is a child of Dan" on Mira's card count once. Relations pointing at a card that does not exist
 * are ignored.
 */
public final class RelationGraph {
    /**
     * One relationship in its standard direction: PARENT (from is a parent of to), MENTOR (from mentors to), EMPLOYER
     * (from employs to), or one of the symmetric types (then {@code from} sorts before {@code to}).
     */
    public static final class Edge {
        public final String from, to;
        public final RelationType type;
        public String note = "";
        /** Cards that have this relationship written on them. */
        public final Set<String> listedOn = new LinkedHashSet<>();

        Edge(String from, String to, RelationType type) {
            this.from = from;
            this.to = to;
            this.type = type;
        }
    }

    /**
     * How the relationship looks from one person's side.
     *
     * @param type          what this person is to {@code otherId} ("Parent of", "Child of", ...)
     * @param listedOnThis  true if it is written on this person's own card (so it can be edited there)
     */
    public record Link(String otherId, RelationType type, String note, boolean listedOnThis) { }

    private final Map<String, Card> cards;
    private final Map<String, Edge> edges = new LinkedHashMap<>();

    public RelationGraph(Map<String, Card> cards) {
        this.cards = cards;
        for (Card c : cards.values()) {
            for (Relation r : c.relations) {
                if (r == null || r.targetCardId == null || r.type == null) continue;
                if (r.targetCardId.equals(c.id) || !cards.containsKey(r.targetCardId)) continue;
                add(c.id, r);
            }
        }
    }

    private void add(String owner, Relation r) {
        String from = owner, to = r.targetCardId;
        RelationType t = r.type;
        // standard direction: parents, mentors and employers are always "from"
        if (t == RelationType.CHILD || t == RelationType.STUDENT || t == RelationType.EMPLOYEE) {
            from = r.targetCardId;
            to = owner;
            t = t.inverse();
        } else if (t.symmetric() && from.compareTo(to) > 0) {
            String tmp = from;
            from = to;
            to = tmp;
        }
        String key = from + "|" + to + "|" + t;
        Edge e = edges.get(key);
        if (e == null) {
            e = new Edge(from, to, t);
            edges.put(key, e);
        }
        e.listedOn.add(owner);
        if (r.note != null && !r.note.isBlank() && e.note.isBlank()) e.note = r.note.trim();
    }

    public Map<String, Card> cards() {
        return cards;
    }

    public List<Edge> edges() {
        return new ArrayList<>(edges.values());
    }

    public List<Edge> familyEdges() {
        List<Edge> out = new ArrayList<>();
        for (Edge e : edges.values()) if (e.type.family) out.add(e);
        return out;
    }

    public List<Edge> webEdges() {
        List<Edge> out = new ArrayList<>();
        for (Edge e : edges.values()) if (!e.type.family) out.add(e);
        return out;
    }

    /** Everything this person is connected to, from their side, sorted by the other person's name. */
    public List<Link> linksOf(String personId) {
        List<Link> out = new ArrayList<>();
        for (Edge e : edges.values()) {
            if (e.from.equals(personId)) out.add(new Link(e.to, e.type, e.note, e.listedOn.contains(personId)));
            else if (e.to.equals(personId)) out.add(new Link(e.from, e.type.inverse(), e.note, e.listedOn.contains(personId)));
        }
        out.sort(Comparator.comparing((Link l) -> nameOf(l.otherId()).toLowerCase()).thenComparing(l -> l.type().ordinal()));
        return out;
    }

    public String nameOf(String id) {
        Card c = cards.get(id);
        return c == null ? "(deleted)" : c.name;
    }

    /** Groups of people joined (directly or through others) by family or by web relationships, biggest first. */
    public List<List<String>> components(boolean family) {
        Map<String, Set<String>> adj = new LinkedHashMap<>();
        for (Edge e : edges.values()) {
            if (e.type.family != family) continue;
            adj.computeIfAbsent(e.from, k -> new LinkedHashSet<>()).add(e.to);
            adj.computeIfAbsent(e.to, k -> new LinkedHashSet<>()).add(e.from);
        }
        List<List<String>> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String start : adj.keySet()) {
            if (seen.contains(start)) continue;
            List<String> comp = new ArrayList<>();
            java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
            queue.add(start);
            seen.add(start);
            while (!queue.isEmpty()) {
                String id = queue.poll();
                comp.add(id);
                for (String n : adj.get(id)) if (seen.add(n)) queue.add(n);
            }
            out.add(comp);
        }
        out.sort((x, y) -> y.size() - x.size());
        return out;
    }

    /** The component (family or web) containing this person, or just the person alone if they have none. */
    public List<String> componentOf(String personId, boolean family) {
        for (List<String> comp : components(family)) if (comp.contains(personId)) return comp;
        return List.of(personId);
    }
}