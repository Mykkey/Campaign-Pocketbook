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
    public static final String KIND_CARD = "card";
    public static final String KIND_ENCOUNTER = "encounter";
    public static final String KIND_SPELL = "spell";
    public static final String KIND_ITEM = "item";
    public static final String KIND_QUEST = "quest";
    public static final String KIND_SESSION = "session";

    public String id = UUID.randomUUID().toString();
    public String name = "";
    public boolean folder;
    /** null (older projects) or "board" = whiteboard, "battlemap" = battlemap. Ignored for folders. */
    public String kind;
    public List<TreeNode> children = new ArrayList<>();
    /** Chosen colour as "#RRGGBB", or null for the default colour of its kind. */
    public String color;

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
    public boolean isCard() {
        return !folder && KIND_CARD.equals(kind);
    }

    @JsonIgnore
    public boolean isEncounter() {
        return !folder && KIND_ENCOUNTER.equals(kind);
    }

    @JsonIgnore
    public boolean isSpell() {
        return !folder && KIND_SPELL.equals(kind);
    }

    @JsonIgnore
    public boolean isMagicItem() {
        return !folder && KIND_ITEM.equals(kind);
    }

    @JsonIgnore
    public boolean isQuest() {
        return !folder && KIND_QUEST.equals(kind);
    }

    @JsonIgnore
    public boolean isSession() {
        return !folder && KIND_SESSION.equals(kind);
    }

    @JsonIgnore
    public boolean isBoard() {
        return !folder && !KIND_BATTLEMAP.equals(kind) && !KIND_CARD.equals(kind) && !KIND_ENCOUNTER.equals(kind)
                && !KIND_SPELL.equals(kind) && !KIND_ITEM.equals(kind) && !KIND_QUEST.equals(kind) && !KIND_SESSION.equals(kind);
    }

    /** Appends every item of one kind (KIND_ITEM, KIND_QUEST...) at or below this node. */
    public void collectKind(String wanted, List<TreeNode> out) {
        if (!folder && wanted.equals(kind)) out.add(this);
        for (TreeNode c : children) c.collectKind(wanted, out);
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

    /** Appends every card at or below this node. */
    public void collectCards(List<TreeNode> out) {
        if (isCard()) out.add(this);
        for (TreeNode c : children) c.collectCards(out);
    }

    /** How many items (not folders) are inside this node, at any depth. */
    @JsonIgnore
    public int countItems() {
        int n = 0;
        for (TreeNode c : children) n += c.folder ? c.countItems() : 1;
        return n;
    }

    /** Appends every spell card at or below this node. */
    public void collectSpells(List<TreeNode> out) {
        if (isSpell()) out.add(this);
        for (TreeNode c : children) c.collectSpells(out);
    }

    /** Appends every encounter at or below this node. */
    public void collectEncounters(List<TreeNode> out) {
        if (isEncounter()) out.add(this);
        for (TreeNode c : children) c.collectEncounters(out);
    }

    /** Appends every whiteboard, battlemap, card and encounter at or below this node. */
    public void collectItems(List<TreeNode> out) {
        if (!folder) out.add(this);
        for (TreeNode c : children) c.collectItems(out);
    }
}
