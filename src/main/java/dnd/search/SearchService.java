package dnd.search;

import dnd.model.Board;
import dnd.model.BoardObject;
import dnd.model.TreeNode;
import dnd.storage.ProjectStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Project-wide text search and backlink lookup. */
public final class SearchService {
    private SearchService() { }

    public static final class Result {
        public final String boardId;
        public final String boardName;
        /** Null when the hit is the board's name itself. */
        public final String objectId;
        public final String snippet;

        public Result(String boardId, String boardName, String objectId, String snippet) {
            this.boardId = boardId;
            this.boardName = boardName;
            this.objectId = objectId;
            this.snippet = snippet;
        }
    }

    public static List<Result> search(ProjectStore store, String query, int limit) {
        List<Result> out = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return out;

        List<TreeNode> nodes = new ArrayList<>();
        store.project.root.collectBoards(nodes);
        for (TreeNode n : nodes) {
            if (n.name.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(new Result(n.id, n.name, null, "Whiteboard"));
            }
            Board b = store.board(n.id);
            if (b == null) continue;
            for (BoardObject o : b.objects) {
                if (o.text != null && o.text.toLowerCase(Locale.ROOT).contains(q)) {
                    out.add(new Result(n.id, n.name, o.id, snippet(o)));
                    if (out.size() >= limit) return out;
                }
            }
        }
        return out;
    }

    /** Every object on any other board that links to {@code targetId}. */
    public static List<Result> backlinks(ProjectStore store, String targetId) {
        List<Result> out = new ArrayList<>();
        for (String id : store.allBoardIds()) {
            if (id.equals(targetId)) continue;
            Board b = store.board(id);
            if (b == null) continue;
            for (BoardObject o : b.objects) {
                if (targetId.equals(o.link)) {
                    out.add(new Result(id, store.boardName(id), o.id, snippet(o)));
                }
            }
        }
        return out;
    }

    static String snippet(BoardObject o) {
        if (o.text == null || o.text.isBlank()) return o.type.name().toLowerCase(Locale.ROOT);
        String s = o.text.replace('\n', ' ').trim();
        return s.length() > 60 ? s.substring(0, 57) + "..." : s;
    }
}
