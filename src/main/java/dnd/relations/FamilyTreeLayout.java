package dnd.relations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lays people out as a family tree: each generation is a row, partners sit side by side, and children are centred
 * under their parents. Pure maths on ids (no UI), so it can be tested. Positions are in "slots": one slot is the
 * width of one person.
 */
public final class FamilyTreeLayout {
    private FamilyTreeLayout() { }

    /** Where a person goes: {@code generation} 0 is the oldest row; {@code x} is the centre of their slot. */
    public record Spot(int generation, double x) { }

    public record Layout(Map<String, Spot> spots, double width, int generations) { }

    private static final class Unit {
        final List<String> members = new ArrayList<>();
        final List<Unit> children = new ArrayList<>();
        double width;
    }

    /**
     * @param persons      everyone to place
     * @param parentEdges  {parent, child} pairs
     * @param spouseEdges  {a, b} pairs: partners share a row and sit together
     * @param siblingEdges {a, b} pairs: siblings share a row (and sit together when no parent is known)
     */
    public static Layout layout(List<String> persons, List<String[]> parentEdges, List<String[]> spouseEdges,
                                List<String[]> siblingEdges) {
        Set<String> known = new LinkedHashSet<>(persons);
        Map<String, Set<String>> parents = new HashMap<>(), kids = new HashMap<>();
        for (String id : known) {
            parents.put(id, new LinkedHashSet<>());
            kids.put(id, new LinkedHashSet<>());
        }
        for (String[] e : parentEdges) {
            if (!known.contains(e[0]) || !known.contains(e[1]) || e[0].equals(e[1])) continue;
            parents.get(e[1]).add(e[0]);
            kids.get(e[0]).add(e[1]);
        }

        // ---- generations: a child is one below its lowest parent; partners and siblings share a row
        Map<String, Integer> gen = new HashMap<>();
        for (String id : known) gen.put(id, 0);
        int guard = known.size() * 4 + 4;
        boolean changed = true;
        while (changed && guard-- > 0) {
            changed = false;
            for (String[] e : parentEdges) {
                if (!known.contains(e[0]) || !known.contains(e[1]) || e[0].equals(e[1])) continue;
                int want = gen.get(e[0]) + 1;
                if (gen.get(e[1]) < want) {
                    gen.put(e[1], want);
                    changed = true;
                }
            }
            for (List<String[]> pairs : List.of(spouseEdges, siblingEdges)) {
                for (String[] e : pairs) {
                    if (!known.contains(e[0]) || !known.contains(e[1])) continue;
                    int g = Math.max(gen.get(e[0]), gen.get(e[1]));
                    if (gen.get(e[0]) != g || gen.get(e[1]) != g) {
                        gen.put(e[0], g);
                        gen.put(e[1], g);
                        changed = true;
                    }
                }
            }
        }

        // ---- units: partners (and parentless siblings) are placed as one block
        Map<String, String> uf = new HashMap<>();
        for (String id : known) uf.put(id, id);
        for (String[] e : spouseEdges) union(uf, known, e[0], e[1]);
        for (String[] e : siblingEdges) {
            if (known.contains(e[0]) && known.contains(e[1]) && parents.get(e[0]).isEmpty() && parents.get(e[1]).isEmpty()) {
                union(uf, known, e[0], e[1]);
            }
        }
        Map<String, Unit> unitOf = new LinkedHashMap<>();
        List<Unit> units = new ArrayList<>();
        for (String id : known) {
            String root = find(uf, id);
            Unit u = unitOf.get(root);
            if (u == null) {
                u = new Unit();
                unitOf.put(root, u);
                units.add(u);
            }
            u.members.add(id);
        }
        Map<String, Unit> byPerson = new HashMap<>();
        for (Unit u : units) for (String m : u.members) byPerson.put(m, u);

        // ---- build the tree of units: each unit claims the child units not yet claimed
        Set<Unit> claimed = new LinkedHashSet<>();
        List<Unit> roots = new ArrayList<>();
        for (Unit u : units) {
            boolean hasParent = false;
            for (String m : u.members) if (!parents.get(m).isEmpty()) hasParent = true;
            if (!hasParent) roots.add(u);
        }
        for (Unit r : roots) claim(r, claimed, kids, byPerson);
        for (Unit u : units) { // anything left over (a parent cycle): treat as a root of its own
            if (!claimed.contains(u)) {
                roots.add(u);
                claim(u, claimed, kids, byPerson);
            }
        }

        // ---- widths bottom-up, then x positions top-down
        Map<String, Spot> spots = new LinkedHashMap<>();
        double left = 0;
        for (Unit r : roots) {
            measure(r);
            place(r, left, gen, spots);
            left += r.width;
        }
        int gens = 0;
        for (Spot s : spots.values()) gens = Math.max(gens, s.generation() + 1);
        return new Layout(spots, left, gens);
    }

    private static void claim(Unit u, Set<Unit> claimed, Map<String, Set<String>> kids, Map<String, Unit> byPerson) {
        if (!claimed.add(u)) return;
        for (String m : u.members) {
            for (String child : kids.get(m)) {
                Unit cu = byPerson.get(child);
                if (cu != null && cu != u && !claimed.contains(cu)) {
                    u.children.add(cu);
                    claim(cu, claimed, kids, byPerson);
                }
            }
        }
    }

    private static double measure(Unit u) {
        double kidsWidth = 0;
        for (Unit c : u.children) kidsWidth += measure(c);
        u.width = Math.max(u.members.size(), kidsWidth);
        return u.width;
    }

    private static void place(Unit u, double left, Map<String, Integer> gen, Map<String, Spot> spots) {
        double kidsWidth = 0;
        for (Unit c : u.children) kidsWidth += c.width;
        double memberStart = left + (u.width - u.members.size()) / 2.0;
        for (int i = 0; i < u.members.size(); i++) {
            String id = u.members.get(i);
            spots.put(id, new Spot(gen.get(id), memberStart + i + 0.5));
        }
        double x = left + (u.width - kidsWidth) / 2.0;
        for (Unit c : u.children) {
            place(c, x, gen, spots);
            x += c.width;
        }
    }

    private static String find(Map<String, String> uf, String id) {
        while (!uf.get(id).equals(id)) {
            uf.put(id, uf.get(uf.get(id)));
            id = uf.get(id);
        }
        return id;
    }

    private static void union(Map<String, String> uf, Set<String> known, String a, String b) {
        if (!known.contains(a) || !known.contains(b)) return;
        String ra = find(uf, a), rb = find(uf, b);
        if (!ra.equals(rb)) uf.put(rb, ra);
    }
}