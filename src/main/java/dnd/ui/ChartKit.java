package dnd.ui;

import dnd.relations.OverviewGraph;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

/** Drawing helpers shared by the charts of relationships and organisations: the boxes people and groups are drawn in. */
final class ChartKit {
    private ChartKit() { }

    static final double NODE_W = 150, NODE_H = 46;

    /** The accent colour of a kind of node: blue for players, amber for NPCs, red for monsters, purple for organisations. */
    static Color accent(OverviewGraph.NodeKind kind) {
        return switch (kind) {
            case PLAYER -> Color.web("#3b82f6");
            case NPC -> Color.web("#d99a2b");
            case MONSTER -> Color.web("#cf4b4b");
            case ORGANISATION -> Color.web(NodeColors.ORGANISATION);
        };
    }

    /** A box with a bold title and a smaller line beneath. Organisations get a double border so they stand out. */
    static void box(GraphicsContext gc, double x, double y, String title, String sub, Color accent, boolean dark, boolean organisation) {
        gc.setFill(dark ? accent.deriveColor(0, 0.9, 0.55, 1) : accent.deriveColor(0, 0.35, 1.18, 1));
        gc.fillRoundRect(x, y, NODE_W, NODE_H, 10, 10);
        gc.setStroke(accent);
        gc.setLineWidth(2);
        gc.strokeRoundRect(x, y, NODE_W, NODE_H, 10, 10);
        if (organisation) {
            gc.setLineWidth(1);
            gc.strokeRoundRect(x + 3.5, y + 3.5, NODE_W - 7, NODE_H - 7, 7, 7);
        }
        gc.setFill(dark ? Color.web("#f3f4f6") : Color.web("#1f2430"));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.setFont(Font.font("System", FontWeight.BOLD, 14));
        gc.fillText(fit(title, NODE_W - 16, 14, true), x + NODE_W / 2, y + 17);
        gc.setFont(Font.font("System", 11));
        gc.setFill(dark ? Color.web("#d1d5db") : Color.web("#4b5563"));
        gc.fillText(fit(sub == null ? "" : sub, NODE_W - 16, 11, false), x + NODE_W / 2, y + 34);
    }

    /** The text shortened with "..." until it fits the width. */
    static String fit(String s, double width, double size, boolean bold) {
        Text t = new Text();
        t.setFont(Font.font("System", bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
        String out = s;
        t.setText(out);
        while (t.getLayoutBounds().getWidth() > width && out.length() > 1) {
            out = out.substring(0, out.length() - 1);
            t.setText(out + "...");
        }
        return out.equals(s) ? s : out + "...";
    }

    /** An elbow line from the bottom of one box to the top of another (the way a family tree joins parents and children). */
    static void elbow(GraphicsContext gc, double sx, double sy, double tx, double ty, double gap) {
        double midY = Math.max(sy, ty - gap / 2.0);
        gc.strokeLine(sx, sy, sx, midY);
        gc.strokeLine(sx, midY, tx, midY);
        gc.strokeLine(tx, midY, tx, ty);
    }
}
