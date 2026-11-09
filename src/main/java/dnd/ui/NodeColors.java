package dnd.ui;

import dnd.model.TreeNode;

import java.util.List;

/** The colours a folder or item can be given, and the default colour of each kind. */
public final class NodeColors {
    private NodeColors() { }

    public record Swatch(String name, String hex) { }

    public static final List<Swatch> PALETTE = List.of(
            new Swatch("Red", "#e5484d"), new Swatch("Orange", "#f08c32"), new Swatch("Yellow", "#e6b400"),
            new Swatch("Green", "#3cb371"), new Swatch("Teal", "#20a6a6"), new Swatch("Blue", "#3b82f6"),
            new Swatch("Purple", "#8b5cf6"), new Swatch("Pink", "#ec4899"), new Swatch("Brown", "#a16a45"),
            new Swatch("Grey", "#8a94a3"));

    public static final String FOLDER = "#d9a441";
    public static final String BOARD = "#5b7fb5";
    public static final String BATTLEMAP = "#4e9e6a";
    public static final String CARD = "#e0a030";
    public static final String ENCOUNTER = "#cf4b4b";
    public static final String SPELL = "#8b5fd6";

    /** The colour a node has unless the DM picked one. */
    public static String defaultFor(TreeNode n) {
        if (n.folder) return FOLDER;
        if (n.isBattleMap()) return BATTLEMAP;
        if (n.isCard()) return CARD;
        if (n.isEncounter()) return ENCOUNTER;
        if (n.isSpell()) return SPELL;
        return BOARD;
    }

    /** The node's own colour if it has a valid one, otherwise its default. */
    public static String colorFor(TreeNode n) {
        return isValid(n.color) ? n.color : defaultFor(n);
    }

    public static boolean isValid(String hex) {
        return hex != null && hex.matches("#[0-9a-fA-F]{6}");
    }

    /** The palette name for a colour, or null if it is not one of the presets. */
    public static String nameOf(String hex) {
        for (Swatch s : PALETTE) if (s.hex().equalsIgnoreCase(hex)) return s.name();
        return null;
    }
}