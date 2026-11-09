package dnd.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A folder, whiteboard or battlemap entry in the project tree. For boards and maps, {@link #id}
 * equals the id of the Board / BattleMap.
 */
public class TreeNode {
    public static final String KIND_BATTLEMAP = "battlemap";

    public String id = UUID.randomUUID().toString();
    public String name = "";
    public boolean folder;
    /** null (older projects) or "board" = whiteboard, "battlemap" = battlemap. Ignored for folders. */
    public String kind;
    public List<TreeNode> children = new ArrayList<>();

    public TreeNode() { }

    public TreeNode(String name, boolean folder) {
        this.name = name;
        this.folder = folder;
    }

    @JsonIgnore
    public boolean isBattleMap() {
        return !folder && KIND_BATTLEMAP.equals(kind);
    }

    @JsonIgnore
    public boolean isBoard() {
        return !folder && !KIND_BATTLEMAP.equals(kind);
    }

    public TreeNode find(String nodeId) {
        if (id.equals(nodeId)) return this;
        for (TreeNode c : children) {
            TreeNode f = c.find(nodeId);
            if (f != null) return f;
        }
        return null;
    }

    /** The folder directly containing the node with this id, or null. */
    public TreeNode parentOf(String nodeId) {
        for (TreeNode c : children) {
            if (c.id.equals(nodeId)) return this;
            TreeNode p = c.parentOf(nodeId);
            if (p != null) return p;
        }
        return null;
    }

    /** Appends this node (if a whiteboard) and all descendant whiteboards, in tree order. */
    public void collectBoards(List<TreeNode> out) {
        if (isBoard()) out.add(this);
        for (TreeNode c : children) c.collectBoards(out);
    }

    /** Appends every battlemap at or below this node. */
    public void collectMaps(List<TreeNode> out) {
        if (isBattleMap()) out.add(this);
        for (TreeNode c : children) c.collectMaps(out);
    }

    /** Appends every whiteboard and battlemap at or below this node. */
    public void collectItems(List<TreeNode> out) {
        if (!folder) out.add(this);
        for (TreeNode c : children) c.collectItems(out);
    }
}
