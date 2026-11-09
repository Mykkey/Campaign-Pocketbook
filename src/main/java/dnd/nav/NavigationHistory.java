package dnd.nav;

import java.util.ArrayList;
import java.util.List;

/** Browser-style back/forward history of visited board ids. */
public class NavigationHistory {
    private final List<String> entries = new ArrayList<>();
    private int index = -1;

    /** Records a visit. Ignored if it is the current entry; drops any forward history. */
    public void visit(String id) {
        if (id == null || (index >= 0 && entries.get(index).equals(id))) return;
        while (entries.size() > index + 1) entries.remove(entries.size() - 1);
        entries.add(id);
        if (entries.size() > 100) entries.remove(0);
        index = entries.size() - 1;
    }

    public boolean canBack() { return index > 0; }

    public boolean canForward() { return index >= 0 && index < entries.size() - 1; }

    /** Steps back and returns the id now current, or null. */
    public String back() {
        if (!canBack()) return null;
        return entries.get(--index);
    }

    public String forward() {
        if (!canForward()) return null;
        return entries.get(++index);
    }

    /** Removes an id everywhere (e.g. a deleted board). */
    public void forget(String id) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).equals(id)) {
                entries.remove(i);
                if (i <= index) index--;
            }
        }
        if (index < 0 && !entries.isEmpty()) index = 0;
    }

    /** The last {@code max} entries up to and including the current one. */
    public List<String> trail(int max) {
        if (index < 0) return new ArrayList<>();
        int from = Math.max(0, index + 1 - max);
        return new ArrayList<>(entries.subList(from, index + 1));
    }
}
