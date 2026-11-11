package dnd.ui;

import dnd.model.Organisation;
import dnd.relations.FamilyTreeLayout;
import dnd.relations.OrgHierarchy;
import dnd.relations.OverviewGraph;
import dnd.storage.ProjectStore;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The chain of command of an organisation drawn like a family tree: the group at the top, the people who report to nobody
 * beneath it, and each person's reports beneath them. Click a member to open it.
 */
final class HierarchyChart extends BorderPane {
    private static final double H_GAP = 26, V_GAP = 70, MARGIN = 30;

    private record Hit(double x, double y, String id) { }

    private final ProjectStore store;
    private final Consumer<String> openItem;
    private final Canvas canvas = new Canvas(400, 300);
    private final Label message = new Label();
    private final List<Hit> hits = new ArrayList<>();
    private Organisation shown;
    private final ScrollPane scroll;
    private boolean centred;
    @SuppressWarnings("unused") private final ChangeListener<Boolean> themeListener = (o, was, now) -> {
        if (shown != null) refresh(shown);
    };

    HierarchyChart(ProjectStore store, Consumer<String> openItem) {
        this.store = store;
        this.openItem = openItem;
        scroll = new ScrollPane(canvas);
        scroll.setPannable(true);
        message.setWrapText(true);
        message.setStyle("-fx-text-fill: -t-text3;");
        message.setPadding(new Insets(8, 10, 4, 10));
        setTop(new VBox(message));
        setCenter(scroll);
        Theme.darkProperty().addListener(new WeakChangeListener<>(themeListener));
        canvas.setOnMouseMoved(e -> canvas.setCursor(hitAt(e.getX(), e.getY()) != null ? Cursor.HAND : Cursor.DEFAULT));
        canvas.setOnMouseClicked(e -> {
            Hit h = hitAt(e.getX(), e.getY());
            if (h != null && e.isStillSincePress() && store.itemExists(h.id())) openItem.accept(h.id());
        });
    }

    private Hit hitAt(double x, double y) {
        for (Hit h : hits) {
            if (x >= h.x() && x <= h.x() + ChartKit.NODE_W && y >= h.y() && y <= h.y() + ChartKit.NODE_H) return h;
        }
        return null;
    }

    /** Draws the organisation again (after a member, a role or a boss changed). */
    void refresh(Organisation o) {
        shown = o;
        hits.clear();
        boolean dark = Theme.isDark();
        Color bg = dark ? Color.web("#1c1e22") : Color.web("#fbfaf6");
        Color line = dark ? Color.web("#8d95a1") : Color.web("#6b6f78");
        GraphicsContext gc = canvas.getGraphicsContext2D();
        if (o.members.isEmpty()) {
            canvas.setWidth(400);
            canvas.setHeight(200);
            gc.setFill(bg);
            gc.fillRect(0, 0, 400, 200);
            message.setText("No members yet. Add some, then say who each one reports to.");
            return;
        }
        message.setText(o.hasHierarchy()
                ? "Click a member to open it. Lines run from a boss down to the people who report to them."
                : "Everyone is at the top for now. Choose \"Reports to\" for a member (on the left) to build the chain of command.");

        FamilyTreeLayout.Layout layout = OrgHierarchy.layout(o);
        double slot = ChartKit.NODE_W + H_GAP;
        double rowH = ChartKit.NODE_H + V_GAP;
        double w = Math.max(400, layout.width() * slot + 2 * MARGIN);
        double h = Math.max(300, (layout.generations() + 1) * rowH + 2 * MARGIN);
        canvas.setWidth(w);
        canvas.setHeight(h);
        gc.setFill(bg);
        gc.fillRect(0, 0, w, h);
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        gc.setStroke(line);
        gc.setLineWidth(2);
        if (!centred) { // the first time, show the middle of the chart (where the group and its leaders are)
            centred = true;
            javafx.application.Platform.runLater(() -> scroll.setHvalue(0.5));
        }

        Map<String, double[]> at = new LinkedHashMap<>(); // member -> {left, top}; the group itself is the first row
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        for (Map.Entry<String, FamilyTreeLayout.Spot> en : layout.spots().entrySet()) {
            double left = MARGIN + en.getValue().x() * slot - slot / 2 + H_GAP / 2.0;
            double top = MARGIN + (en.getValue().generation() + 1) * rowH;
            at.put(en.getKey(), new double[]{left, top});
        }
        for (Organisation.Member m : o.members) {
            if (o.bossOf(m.id) != null) continue;
            double[] p = at.get(m.id);
            if (p == null) continue;
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
        }
        double orgLeft = (minX + maxX) / 2, orgTop = MARGIN;
        // lines: the group to the people at the top, then each boss to their reports
        for (Organisation.Member m : o.members) {
            double[] p = at.get(m.id);
            if (p == null) continue;
            String boss = o.bossOf(m.id);
            double[] from = boss == null ? new double[]{orgLeft, orgTop} : at.get(boss);
            if (from == null) continue;
            ChartKit.elbow(gc, from[0] + ChartKit.NODE_W / 2, from[1] + ChartKit.NODE_H, p[0] + ChartKit.NODE_W / 2, p[1], V_GAP);
        }
        ChartKit.box(gc, orgLeft, orgTop, o.name == null || o.name.isBlank() ? "(unnamed)" : o.name,
                o.type == null || o.type.isBlank() ? "organisation" : o.type, ChartKit.accent(OverviewGraph.NodeKind.ORGANISATION), dark, true);
        for (Organisation.Member m : o.members) {
            double[] p = at.get(m.id);
            if (p == null) continue;
            String name = store.itemNameOrNull(m.id);
            String kind = OrganisationCardView.memberKind(store, m.id);
            OverviewGraph.NodeKind nk = switch (kind) {
                case "Player" -> OverviewGraph.NodeKind.PLAYER;
                case "Monster" -> OverviewGraph.NodeKind.MONSTER;
                case "Organisation" -> OverviewGraph.NodeKind.ORGANISATION;
                default -> OverviewGraph.NodeKind.NPC;
            };
            String sub = m.role == null || m.role.isBlank() ? kind : m.role.trim();
            ChartKit.box(gc, p[0], p[1], name == null ? "(deleted item)" : name, sub, ChartKit.accent(nk), dark, nk == OverviewGraph.NodeKind.ORGANISATION);
            if (name != null) hits.add(new Hit(p[0], p[1], m.id));
        }
    }
}
