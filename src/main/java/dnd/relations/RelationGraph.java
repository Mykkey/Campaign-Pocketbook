package dnd.relations;

import dnd.model.Card;
import dnd.model.LinkType;
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
        /** For a custom link: its id (otherwise null). */
        public final String linkTypeId;
        /** What to write on the line: the short name of the type, or the custom link's name. */
        public final String name;
        /** The colour of the line ("#RRGGBB"). */
        public final String colour;
        /** True if the two sides read differently (so the line gets an arrow from {@code from} to {@code to}). */
        public final boolean directed;
        public String note = "";
        /** Cards that have this relationship written on them. */
        public final Set<String> listedOn = new LinkedHashSet<>();

        Edge(String from, String to, RelationType type, String linkTypeId, String name, String colour, boolean directed) {
            this.from = from;
            this.to = to;
            this.type = type;
            this.linkTypeId = linkTypeId;
            this.name = name;
            this.colour = colour;
            this.directed = directed;
        }
    }

    /**
     * How the relationship looks from one person's side.
     *
     * @param type          what this person is to {@code otherId} ("Parent of", "Child of", ...)
     * @param listedOnThis  true if it is written on this person's own card (so it can be edited there)
     * @param label         how it reads from this person's side ("Child of", or a custom link's name or reverse name)
     * @param colour        the colour of its line
     */
    public record Link(String otherId, RelationType type, String note, boolean listedOnThis, String label, String colour) { }

    private final Map<String, Card> cards;
    private final Map<String, LinkType> linkTypes = new LinkedHashMap<>();
    private final Map<String, Edge> edges = new LinkedHashMap<>();

    public RelationGraph(Map<String, Card> cards) {
        this(cards, List.of());
    }

    /** @param customLinks the kinds of link made up for this campaign, so custom relationships can be read */
    public RelationGraph(Map<String, Card> cards, List<LinkType> customLinks) {
        this.cards = cards;
        if (customLinks != null) for (LinkType t : customLinks) if (t != null && t.id != null) linkTypes.put(t.id, t);
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
        String customId = null, name = t.shortLabel, colour = t.colour;
        boolean directed = !t.symmetric();
        if (t == RelationType.CUSTOM) {
            LinkType lt = r.linkTypeId == null ? null : linkTypes.get(r.linkTypeId);
            if (lt == null) { // the custom link was deleted: it is still a connection
                t = RelationType.OTHER;
                name = t.shortLabel;
                colour = t.colour;
                directed = false;
            } else {
                customId = lt.id;
                name = lt.name;
                colour = lt.colour;
                directed = lt.directed();
            }
        }
        // standard direction: the senior one, the actor or the giver is always "from" (parent, mentor, employer, ruler, creator...)
        if (t != RelationType.CUSTOM && t.reverse) {
            from = r.targetCardId;
            to = owner;
            t = t.inverse();
            name = t.shortLabel;
            colour = t.colour;
        } else if (!directed && from.compareTo(to) > 0) {
            String tmp = from;
            from = to;
            to = tmp;
        }
        String key = from + "|" + to + "|" + t + "|" + (customId == null ? "" : customId);
        Edge e = edges.get(key);
        if (e == null) {
            e = new Edge(from, to, t, customId, name, colour, directed);
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
            if (e.from.equals(personId)) out.add(new Link(e.to, e.type, e.note, e.listedOn.contains(personId), labelFrom(e, true), e.colour));
            else if (e.to.equals(personId)) out.add(new Link(e.from, e.type.inverse(), e.note, e.listedOn.contains(personId), labelFrom(e, false), e.colour));
        }
        out.sort(Comparator.comparing((Link l) -> nameOf(l.otherId()).toLowerCase()).thenComparing(l -> l.type().ordinal()));
        return out;
    }

    /** How an edge reads from one side: from the {@code from} card's side, or from the {@code to} card's side. */
    private String labelFrom(Edge e, boolean fromSide) {
        if (e.type == RelationType.CUSTOM) {
            LinkType lt = linkTypes.get(e.linkTypeId);
            if (lt == null) return RelationType.OTHER.label;
            return fromSide || !lt.directed() ? lt.name : lt.reverseName;
        }
        return fromSide ? e.type.label : e.type.inverse().label;
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