package dnd.relations;

import dnd.model.Card;
import dnd.model.Organisation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything in one picture: the family ties between cards, the other relationships (allies, rivals, mentors,
 * employers), and the organisations with their members and chains of command. Built from what is written on the cards
 * and organisations. Only things that are connected to something appear.
 */
public final class OverviewGraph {
    public enum NodeKind { PLAYER, NPC, MONSTER, ORGANISATION }

    public enum EdgeKind {
        PARENT(Group.FAMILY), SPOUSE(Group.FAMILY), SIBLING(Group.FAMILY),
        ALLY(Group.RELATIONSHIPS), RIVAL(Group.RELATIONSHIPS), MENTOR(Group.RELATIONSHIPS), EMPLOYER(Group.RELATIONSHIPS), OTHER(Group.RELATIONSHIPS),
        MEMBER(Group.ORGANISATIONS), REPORTS(Group.ORGANISATIONS);

        public final Group group;

        EdgeKind(Group group) {
            this.group = group;
        }
    }

    public enum Group { FAMILY, RELATIONSHIPS, ORGANISATIONS }

    public record Node(String id, String name, String subtitle, NodeKind kind) { }

    /** A link in its standard direction: PARENT, MENTOR, EMPLOYER and REPORTS point from the senior one; MEMBER from the organisation to its member. */
    public record Edge(String from, String to, EdgeKind kind, String label) { }

    /** Which kinds of link to include. */
    public record Options(boolean family, boolean relationships, boolean organisations) {
        public static final Options ALL = new Options(true, true, true);

        boolean includes(Group g) {
            return switch (g) {
                case FAMILY -> family;
                case RELATIONSHIPS -> relationships;
                case ORGANISATIONS -> organisations;
            };
        }
    }

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final List<Edge> edges = new ArrayList<>();

    private OverviewGraph() { }

    public static OverviewGraph build(Map<String, Card> cards, Map<String, Organisation> orgs, Options options) {
        Map<String, Node> all = new LinkedHashMap<>();
        for (Card c : cards.values()) {
            NodeKind k = switch (c.kind) {
                case PLAYER -> NodeKind.PLAYER;
                case NPC -> NodeKind.NPC;
                case MONSTER -> NodeKind.MONSTER;
            };
            String sub = c.creatureType == null || c.creatureType.isBlank() ? c.kind.name().toLowerCase() : c.creatureType;
            all.put(c.id, new Node(c.id, c.name, sub, k));
        }
        for (Organisation o : orgs.values()) {
            all.put(o.id, new Node(o.id, o.name, o.type == null || o.type.isBlank() ? "organisation" : o.type, NodeKind.ORGANISATION));
        }

        List<Edge> found = new ArrayList<>();
        for (RelationGraph.Edge e : new RelationGraph(cards).edges()) {
            EdgeKind kind = switch (e.type) {
                case PARENT, CHILD -> EdgeKind.PARENT;
                case SPOUSE -> EdgeKind.SPOUSE;
                case SIBLING -> EdgeKind.SIBLING;
                case MENTOR, STUDENT -> EdgeKind.MENTOR;
                case EMPLOYER, EMPLOYEE -> EdgeKind.EMPLOYER;
                case ALLY -> EdgeKind.ALLY;
                case RIVAL -> EdgeKind.RIVAL;
                case OTHER -> EdgeKind.OTHER;
            };
            found.add(new Edge(e.from, e.to, kind, e.note));
        }
        for (Organisation o : orgs.values()) {
            for (Organisation.Member m : o.members) {
                if (!all.containsKey(m.id) || m.id.equals(o.id)) continue;
                String boss = o.bossOf(m.id);
                String role = m.role == null ? "" : m.role.trim();
                if (boss != null && all.containsKey(boss)) found.add(new Edge(boss, m.id, EdgeKind.REPORTS, role));
                else found.add(new Edge(o.id, m.id, EdgeKind.MEMBER, role));
            }
        }

        OverviewGraph g = new OverviewGraph();
        Set<String> used = new LinkedHashSet<>();
        for (Edge e : found) {
            if (!options.includes(e.kind().group) || !all.containsKey(e.from()) || !all.containsKey(e.to())) continue;
            g.edges.add(e);
            used.add(e.from());
            used.add(e.to());
        }
        for (Map.Entry<String, Node> en : all.entrySet()) if (used.contains(en.getKey())) g.nodes.put(en.getKey(), en.getValue());
        return g;
    }

    /** Everything that has at least one link. */
    public Map<String, Node> nodes() {
        return nodes;
    }

    public List<Edge> edges() {
        return edges;
    }

    /** Groups of nodes joined (directly or through others) by any link, biggest first. */
    public List<List<String>> components() {
        Map<String, Set<String>> adj = new LinkedHashMap<>();
        for (String id : nodes.keySet()) adj.put(id, new LinkedHashSet<>());
        for (Edge e : edges) {
            adj.get(e.from()).add(e.to());
            adj.get(e.to()).add(e.from());
        }
        List<List<String>> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String start : adj.keySet()) {
            if (seen.contains(start)) continue;
            List<String> comp = new ArrayList<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(start);
            seen.add(start);
            while (!queue.isEmpty()) {
                String id = queue.poll();
                comp.add(id);
                for (String nb : adj.get(id)) if (seen.add(nb)) queue.add(nb);
            }
            out.add(comp);
        }
        out.sort((a, b) -> b.size() - a.size());
        return out;
    }

    /** The group containing this node, or an empty list if it has no links. */
    public List<String> componentOf(String id) {
        for (List<String> c : components()) if (c.contains(id)) return c;
        return List.of();
    }

    /** The links between the nodes of one group. */
    public List<Edge> edgesWithin(List<String> ids) {
        Set<String> set = new LinkedHashSet<>(ids);
        List<Edge> out = new ArrayList<>();
        for (Edge e : edges) if (set.contains(e.from()) && set.contains(e.to())) out.add(e);
        return out;
    }
}
