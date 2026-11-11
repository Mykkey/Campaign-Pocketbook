package dnd.relations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lays a graph out top-down, like a family tree or an organisation chart: whoever is above someone (a parent, a
 * mentor, an employer, a boss, an organisation over its members) is in an earlier row, partners and siblings share a
 * row, and everything else sits near the people it is linked to. Rows are ordered to cut down crossing lines.
 * Pure maths on ids (no UI), so it can be tested; the same input always gives the same picture.
 */
public final class LayeredLayout {
    private LayeredLayout() { }

    /** The row (0 is the top) and the horizontal place (in slots: one slot is the width of one box, plus a gap) of a node. */
    public record Spot(int rank, double x) { }

    /**
     * @param ids   the nodes (one connected group, or several)
     * @param down  {above, below} pairs: the first is placed in an earlier row than the second
     * @param level {a, b} pairs that share a row (partners, siblings) and are kept side by side
     * @param loose {a, b} pairs with no row rule (allies, rivals): they are only kept near each other
     */
    public static Map<String, Spot> layout(List<String> ids, List<String[]> down, List<String[]> level, List<String[]> loose) {
        int n = ids.size();
        Map<String, Spot> out = new LinkedHashMap<>();
        if (n == 0) return out;
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < n; i++) index.put(ids.get(i), i);
        List<int[]> d = pairs(down, index), lv = pairs(level, index), lo = pairs(loose, index);

        // ---- rows
        int[] rank = new int[n];
        int guard = n * 4 + 8;
        boolean changed = true;
        while (changed && guard-- > 0) {
            changed = false;
            for (int[] e : d) {
                if (rank[e[1]] < rank[e[0]] + 1 && rank[e[0]] + 1 <= n) {
                    rank[e[1]] = rank[e[0]] + 1;
                    changed = true;
                }
            }
            for (int[] e : lv) {
                int g = Math.max(rank[e[0]], rank[e[1]]);
                if (rank[e[0]] != g || rank[e[1]] != g) {
                    rank[e[0]] = g;
                    rank[e[1]] = g;
                    changed = true;
                }
            }
        }
        // someone with people below them but nobody above sits just above them (not at the very top of the page)
        boolean[] hasAbove = new boolean[n], inLevel = new boolean[n], hasDown = new boolean[n];
        for (int[] e : d) {
            hasAbove[e[1]] = true;
            hasDown[e[0]] = true;
        }
        for (int[] e : lv) {
            inLevel[e[0]] = true;
            inLevel[e[1]] = true;
        }
        for (int i = 0; i < n; i++) {
            if (hasAbove[i] || inLevel[i] || !hasDown[i]) continue;
            int lowest = Integer.MAX_VALUE;
            for (int[] e : d) if (e[0] == i) lowest = Math.min(lowest, rank[e[1]]);
            if (lowest != Integer.MAX_VALUE && lowest - 1 > rank[i]) rank[i] = lowest - 1;
        }
        // someone with no rule of their own (only allies, rivals...) joins the row of the first person they are linked to
        for (int pass = 0; pass < 3; pass++) {
            for (int[] e : lo) {
                for (int side = 0; side < 2; side++) {
                    int a = e[side], b = e[1 - side];
                    if (!hasAbove[a] && !hasDown[a] && !inLevel[a] && (hasAbove[b] || hasDown[b] || inLevel[b] || pass > 0)) rank[a] = rank[b];
                }
            }
        }
        // use consecutive rows
        int[] used = Arrays.stream(rank).distinct().sorted().toArray();
        Map<Integer, Integer> compact = new HashMap<>();
        for (int i = 0; i < used.length; i++) compact.put(used[i], i);
        for (int i = 0; i < n; i++) rank[i] = compact.get(rank[i]);
        int rows = used.length;

        // ---- order within each row
        List<List<Integer>> row = new ArrayList<>();
        for (int r = 0; r < rows; r++) row.add(new ArrayList<>());
        for (int i = 0; i < n; i++) row.get(rank[i]).add(i);
        List<Set<Integer>> nb = new ArrayList<>();
        for (int i = 0; i < n; i++) nb.add(new HashSet<>());
        for (List<int[]> list : List.of(d, lv, lo)) {
            for (int[] e : list) {
                nb.get(e[0]).add(e[1]);
                nb.get(e[1]).add(e[0]);
            }
        }
        for (int sweep = 0; sweep < 6; sweep++) {
            for (int r = 1; r < rows; r++) reorder(row.get(r), row.get(r - 1), nb, rank, r - 1);
            for (int r = rows - 2; r >= 0; r--) reorder(row.get(r), row.get(r + 1), nb, rank, r + 1);
        }
        for (List<Integer> r : row) keepTogether(r, lv);

        // ---- places across: a slot each, then pulled towards the people they are linked to without changing the order
        double[] x = new double[n];
        double widest = 0;
        for (List<Integer> r : row) widest = Math.max(widest, r.size());
        for (List<Integer> r : row) for (int i = 0; i < r.size(); i++) x[r.get(i)] = i + (widest - r.size()) / 2.0;
        for (int round = 0; round < 12; round++) {
            for (int r = 1; r < rows; r++) pull(row.get(r), nb, x, rank, r - 1);
            for (int r = rows - 2; r >= 0; r--) pull(row.get(r), nb, x, rank, r + 1);
        }
        double min = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) min = Math.min(min, x[i]);
        for (int i = 0; i < n; i++) out.put(ids.get(i), new Spot(rank[i], x[i] - min));
        return out;
    }

    private static List<int[]> pairs(List<String[]> in, Map<String, Integer> index) {
        List<int[]> out = new ArrayList<>();
        for (String[] e : in) {
            Integer a = index.get(e[0]), b = index.get(e[1]);
            if (a != null && b != null && !a.equals(b)) out.add(new int[]{a, b});
        }
        return out;
    }

    /** Sorts a row by the average place of each node's neighbours in the neighbouring row (nodes with none stay where they are). */
    private static void reorder(List<Integer> row, List<Integer> other, List<Set<Integer>> nb, int[] rank, int otherRank) {
        Map<Integer, Integer> at = new HashMap<>();
        for (int i = 0; i < other.size(); i++) at.put(other.get(i), i);
        Map<Integer, Double> key = new HashMap<>();
        for (int i = 0; i < row.size(); i++) {
            double sum = 0;
            int count = 0;
            for (int m : nb.get(row.get(i))) {
                if (rank[m] == otherRank && at.containsKey(m)) {
                    sum += at.get(m);
                    count++;
                }
            }
            key.put(row.get(i), count == 0 ? (double) i * other.size() / Math.max(1, row.size()) : sum / count);
        }
        row.sort((a, b) -> Double.compare(key.get(a), key.get(b))); // stable: ties keep their order
    }

    /** Moves a node next to its partner or sibling when the sort has put others between them. */
    private static void keepTogether(List<Integer> row, List<int[]> level) {
        for (int[] e : level) {
            int ia = row.indexOf(e[0]), ib = row.indexOf(e[1]);
            if (ia < 0 || ib < 0 || Math.abs(ia - ib) == 1) continue;
            row.remove(Integer.valueOf(e[1]));
            row.add(row.indexOf(e[0]) + 1, e[1]);
        }
    }

    /** Moves each node of the row towards the average place of its neighbours in the other row, keeping the order and one slot apart. */
    private static void pull(List<Integer> row, List<Set<Integer>> nb, double[] x, int[] rank, int otherRank) {
        int m = row.size();
        double[] want = new double[m];
        for (int i = 0; i < m; i++) {
            double sum = 0;
            int count = 0;
            for (int o : nb.get(row.get(i))) {
                if (rank[o] == otherRank) {
                    sum += x[o];
                    count++;
                }
            }
            want[i] = count == 0 ? x[row.get(i)] : sum / count;
        }
        double[] at = want.clone();
        for (int i = 1; i < m; i++) at[i] = Math.max(at[i], at[i - 1] + 1);
        // shift each run back so the row as a whole stays as near as it can to what it wanted
        for (int i = m - 2; i >= 0; i--) at[i] = Math.min(at[i], at[i + 1] - 1);
        // the two sweeps can disagree: keep the second and make sure the gaps still hold
        for (int i = 1; i < m; i++) at[i] = Math.max(at[i], at[i - 1] + 1);
        for (int i = 0; i < m; i++) x[row.get(i)] = at[i];
    }
}
