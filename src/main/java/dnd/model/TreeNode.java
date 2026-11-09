package dnd.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A folder or whiteboard entry in the project tree. For boards, {@link #id} equals the Board id. */
public class TreeNode {
    public String id = UUID.randomUUID().toString();
    public String name = "";
    public boolean folder;
    public List<TreeNode> children = new ArrayList<>();

    public TreeNode() { }

    public TreeNode(String name, boolean folder) {
        this.name = name;
        this.folder = folder;
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

    /** Appends this node (if a board) and all descendant boards, in tree order. */
    public void collectBoards(List<TreeNode> out) {
        if (!folder) out.add(this);
        for (TreeNode c : children) c.collectBoards(out);
    }
}
