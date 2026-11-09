package dnd.ui;

import dnd.model.TreeNode;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/**
 * Small drawn icons for the project tree and the tabs: a folder, a whiteboard, a map, a card and a pair of swords,
 * each tinted with the item's colour. They are plain shapes (no fonts or emoji), so they look the same on every
 * machine and in both light and dark mode.
 */
public final class NodeIcons {
    private NodeIcons() { }

    private static final String FOLDER_BACK = "M1 3.5 A1.5 1.5 0 0 1 2.5 2 H6 L7.5 3.5 H13.5 A1.5 1.5 0 0 1 15 5 V12 A1.5 1.5 0 0 1 13.5 13.5 H2.5 A1.5 1.5 0 0 1 1 12 Z";
    private static final String FOLDER_FRONT = "M2.4 6.4 H15.4 L13.6 13.5 H1.6 Z";

    /** The icon for a tree node, in its colour; {@code expanded} opens the folder. */
    public static Node icon(TreeNode n, boolean expanded) {
        Color c = Color.web(NodeColors.colorFor(n));
        Pane p = new Pane();
        p.setMinSize(16, 16);
        p.setPrefSize(16, 16);
        p.setMaxSize(16, 16);
        p.setMouseTransparent(true);
        if (n.folder) {
            if (expanded) {
                p.getChildren().addAll(fill(FOLDER_BACK, c.darker()), fill(FOLDER_FRONT, c));
            } else {
                p.getChildren().addAll(fill(FOLDER_BACK, c), line("M1.5 6 H14.5", Color.web("#ffffff", 0.35), 1));
            }
        } else if (n.isBattleMap()) {
            p.getChildren().addAll(fill("M1 3.5 L5.5 2 L10.5 3.5 L15 2 V12.5 L10.5 14 L5.5 12.5 L1 14 Z", c),
                    line("M5.5 2.5 V12.5 M10.5 3.5 V13.5", Color.web("#ffffff", 0.8), 1));
        } else if (n.isCard()) {
            p.getChildren().addAll(fill("M2.5 1.5 H13.5 A1 1 0 0 1 14.5 2.5 V13.5 A1 1 0 0 1 13.5 14.5 H2.5 A1 1 0 0 1 1.5 13.5 V2.5 A1 1 0 0 1 2.5 1.5 Z", c),
                    fill("M3.5 3.5 H12.5 V5.2 H3.5 Z", Color.web("#ffffff", 0.9)),
                    fill("M3.5 7 H7 V11.5 H3.5 Z", Color.web("#ffffff", 0.55)),
                    line("M8.6 7.6 H12.5 M8.6 9.5 H12.5 M8.6 11.4 H12.5", Color.web("#ffffff", 0.8), 1));
        } else if (n.isSpell()) { // a spell book with a sparkle
            p.getChildren().addAll(fill("M3.5 1.5 H12.5 A1 1 0 0 1 13.5 2.5 V13 A1.5 1.5 0 0 1 12 14.5 H3.5 A2 2 0 0 1 1.5 12.5 V3.5 A2 2 0 0 1 3.5 1.5 Z", c),
                    line("M4.2 3 V14", Color.web("#ffffff", 0.45), 1),
                    fill("M8.6 4.2 L9.5 6.9 L12.2 7.8 L9.5 8.7 L8.6 11.4 L7.7 8.7 L5 7.8 L7.7 6.9 Z", Color.web("#ffffff", 0.95)));
        } else if (n.isEncounter()) {
            p.getChildren().addAll(line("M3.2 3.2 L12.8 12.8 M12.8 3.2 L3.2 12.8", c, 2),
                    line("M9.2 13 L13 9.2 M2.9 9.2 L6.8 13", c, 1.6));
        } else {
            p.getChildren().addAll(fill("M2.5 1.5 H13.5 A1 1 0 0 1 14.5 2.5 V13.5 A1 1 0 0 1 13.5 14.5 H2.5 A1 1 0 0 1 1.5 13.5 V2.5 A1 1 0 0 1 2.5 1.5 Z", c),
                    line("M4.3 5.3 H11.7 M4.3 8 H11.7 M4.3 10.7 H8.6", Color.web("#ffffff", 0.85), 1.3));
        }
        return p;
    }

    private static SVGPath fill(String path, Color c) {
        SVGPath s = new SVGPath();
        s.setContent(path);
        s.setFill(c);
        return s;
    }

    private static SVGPath line(String path, Color c, double width) {
        SVGPath s = new SVGPath();
        s.setContent(path);
        s.setFill(null);
        s.setStroke(c);
        s.setStrokeWidth(width);
        s.setStrokeLineCap(StrokeLineCap.ROUND);
        s.setStrokeLineJoin(StrokeLineJoin.ROUND);
        return s;
    }
}