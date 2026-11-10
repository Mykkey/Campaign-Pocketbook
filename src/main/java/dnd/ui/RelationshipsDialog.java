package dnd.ui;

import dnd.model.Card;
import dnd.model.RelationType;
import dnd.relations.FamilyTreeLayout;
import dnd.relations.RelationGraph;
import dnd.relations.RelationGraph.Edge;
import dnd.relations.WebLayout;
import dnd.storage.ProjectStore;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A picture of how the cards are connected: the family tree (parents, partners, siblings, children laid out in
 * generations) and the web of everything else (allies, rivals, mentors, employers). Relationships are entered on the
 * cards themselves (Relationships section); click a person here to open their card.
 */
public final class RelationshipsDialog {
    private RelationshipsDialog() { }

    private static Stage open;

    private static final double NODE_W = 150, NODE_H = 46, H_GAP = 26, V_GAP = 78, MARGIN = 40;

    private record Hit(double x, double y, double w, double h, String id) { }

    public static void show(Window owner, ProjectStore store, Consumer<String> openItem) {
        if (open != null && open.isShowing()) {
            open.toFront();
            open.requestFocus();
            return;
        }
        Canvas canvas = new Canvas(900, 600);
        List<Hit> hits = new ArrayList<>();
        ScrollPane scroll = new ScrollPane(canvas);
        scroll.setPannable(true);

        ToggleGroup mode = new ToggleGroup();
        ToggleButton family = new ToggleButton("Family tree");
        ToggleButton web = new ToggleButton("Web of relationships");
        family.setToggleGroup(mode);
        web.setToggleGroup(mode);
        family.setSelected(true);
        mode.selectedToggleProperty().addListener((o, a, n) -> {
            if (n == null) (a == web ? web : family).setSelected(true); // one must stay selected
        });

        ComboBox<Object> focus = new ComboBox<>();
        focus.setPrefWidth(240);
        focus.setTooltip(new Tooltip("Show everyone, or only the people connected to one card"));
        Label message = new Label();
        message.setStyle("-fx-text-fill: -t-text3;");
        message.setWrapText(true);

        Runnable[] redraw = new Runnable[1];
        Runnable refreshChoices = () -> {
            RelationGraph g = graph(store);
            boolean fam = family.isSelected();
            List<Object> items = new ArrayList<>();
            items.add("Everyone");
            List<Card> people = new ArrayList<>();
            for (List<String> comp : g.components(fam)) for (String id : comp) people.add(g.cards().get(id));
            people.sort(Comparator.comparing(c -> c.name.toLowerCase()));
            items.addAll(people);
            Object keep = focus.getValue();
            focus.setItems(FXCollections.observableArrayList(items));
            focus.setValue(keep instanceof Card c && people.stream().anyMatch(p -> p.id.equals(c.id)) ? keep : "Everyone");
        };
        redraw[0] = () -> draw(canvas, hits, graph(store), family.isSelected(), focus.getValue() instanceof Card c ? c.id : null, message);
        focus.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Object o) { return o instanceof Card c ? c.name : String.valueOf(o); }

            @Override public Object fromString(String s) { return s; }
        });
        mode.selectedToggleProperty().addListener((o, a, n) -> {
            refreshChoices.run();
            redraw[0].run();
        });
        focus.setOnAction(e -> redraw[0].run());

        Button refresh = new Button("Refresh");
        refresh.setTooltip(new Tooltip("Pick up relationships you have just changed on cards"));
        refresh.setOnAction(e -> {
            refreshChoices.run();
            redraw[0].run();
        });
        HBox bar = new HBox(8, family, web, new Label("Show"), focus, refresh);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 10, 4, 10));
        message.setPadding(new Insets(0, 10, 6, 10));
        VBox top = new VBox(bar, message);

        canvas.setOnMouseMoved(e -> canvas.setCursor(hit(hits, e.getX(), e.getY()) != null ? Cursor.HAND : Cursor.DEFAULT));
        canvas.setOnMouseClicked(e -> {
            Hit h = hit(hits, e.getX(), e.getY());
            if (h != null && e.isStillSincePress()) openItem.accept(h.id());
        });

        BorderPane root = new BorderPane(scroll);
        root.setTop(top);
        Stage stage = new Stage();
        stage.setTitle("Relationships");
        if (owner != null) stage.initOwner(owner);
        Scene scene = new Scene(root, 1100, 720);
        scene.setOnKeyPressed(e -> {
            if (dnd.keys.Shortcuts.matches(e, "dialog.close")) stage.close();
        });
        stage.setScene(scene);
        ChangeListener<Boolean> themeListener = (o, was, now) -> redraw[0].run();
        Theme.darkProperty().addListener(new WeakChangeListener<>(themeListener));
        stage.getProperties().put("themeListener", themeListener); // keep it alive while the window is
        stage.setOnHidden(e -> open = null);
        open = stage;
        stage.show();
        refreshChoices.run();
        redraw[0].run();
    }

    private static RelationGraph graph(ProjectStore store) {
        Map<String, Card> cards = new LinkedHashMap<>();
        for (String id : store.allCardIds()) {
            Card c = store.card(id);
            if (c != null) cards.put(id, c);
        }
        return new RelationGraph(cards);
    }

    private static Hit hit(List<Hit> hits, double x, double y) {
        for (Hit h : hits) if (x >= h.x() && x <= h.x() + h.w() && y >= h.y() && y <= h.y() + h.h()) return h;
        return null;
    }

    // ================================================================ drawing

    private static void draw(Canvas canvas, List<Hit> hits, RelationGraph g, boolean family, String focusId, Label message) {
        hits.clear();
        boolean dark = Theme.isDark();
        Color bg = dark ? Color.web("#1c1e22") : Color.web("#fbfaf6");
        Color line = dark ? Color.web("#8d95a1") : Color.web("#6b6f78");
        Color text = dark ? Color.web("#e6e8eb") : Color.web("#222222");

        List<List<String>> comps = new ArrayList<>();
        if (focusId != null) comps.add(g.componentOf(focusId, family));
        else comps.addAll(g.components(family));
        List<String> people = new ArrayList<>();
        for (List<String> c : comps) people.addAll(c);

        if (people.isEmpty()) {
            canvas.setWidth(900);
            canvas.setHeight(600);
            GraphicsContext gc = canvas.getGraphicsContext2D();
            gc.setFill(bg);
            gc.fillRect(0, 0, 900, 600);
            message.setText(family
                    ? "No family relationships yet. Open a card, find Relationships, and add a parent, child, partner or sibling."
                    : "No other relationships yet. Open a card, find Relationships, and add an ally, rival, mentor or employer.");
            return;
        }
        message.setText(family ? "Click a person to open their card. Double lines join partners; dashed lines join siblings with no known parent."
                : "Click a person to open their card. Arrows point from mentor to student and from employer to employee.");

        if (family) drawFamily(canvas, hits, g, people, bg, line, text);
        else drawWeb(canvas, hits, g, comps, bg, line, text);
    }

    private static Color kindColor(Card c) {
        return switch (c.kind) {
            case PLAYER -> Color.web("#3b82f6");
            case NPC -> Color.web("#d99a2b");
            case MONSTER -> Color.web("#cf4b4b");
        };
    }

    private static void node(GraphicsContext gc, List<Hit> hits, Card c, double x, double y, Color text, boolean dark) {
        Color k = kindColor(c);
        gc.setFill(dark ? k.deriveColor(0, 0.9, 0.55, 1) : k.deriveColor(0, 0.35, 1.18, 1));
        gc.fillRoundRect(x, y, NODE_W, NODE_H, 10, 10);
        gc.setStroke(k);
        gc.setLineWidth(2);
        gc.strokeRoundRect(x, y, NODE_W, NODE_H, 10, 10);
        gc.setFill(dark ? Color.web("#f3f4f6") : Color.web("#1f2430"));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.setFont(Font.font("System", FontWeight.BOLD, 14));
        gc.fillText(fit(c.name, NODE_W - 14, 14, true), x + NODE_W / 2, y + 17);
        gc.setFont(Font.font("System", 11));
        gc.setFill(dark ? Color.web("#d1d5db") : Color.web("#4b5563"));
        String sub = c.creatureType == null || c.creatureType.isBlank() ? c.kind.name().toLowerCase() : c.creatureType;
        gc.fillText(fit(sub, NODE_W - 14, 11, false), x + NODE_W / 2, y + 34);
        hits.add(new Hit(x, y, NODE_W, NODE_H, c.id));
    }

    private static String fit(String s, double width, double size, boolean bold) {
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

    // ---------------------------------------------------------------- family tree

    private static void drawFamily(Canvas canvas, List<Hit> hits, RelationGraph g, List<String> people,
                                   Color bg, Color line, Color text) {
        List<String[]> parentEdges = new ArrayList<>(), spouseEdges = new ArrayList<>(), siblingEdges = new ArrayList<>();
        for (Edge e : g.familyEdges()) {
            if (!people.contains(e.from) || !people.contains(e.to)) continue;
            String[] pair = {e.from, e.to};
            switch (e.type) {
                case PARENT -> parentEdges.add(pair);
                case SPOUSE -> spouseEdges.add(pair);
                case SIBLING -> siblingEdges.add(pair);
                default -> { }
            }
        }
        FamilyTreeLayout.Layout layout = FamilyTreeLayout.layout(people, parentEdges, spouseEdges, siblingEdges);
        double slot = NODE_W + H_GAP;
        double w = Math.max(900, layout.width() * slot + 2 * MARGIN);
        double h = Math.max(600, layout.generations() * (NODE_H + V_GAP) + 2 * MARGIN);
        canvas.setWidth(w);
        canvas.setHeight(h);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(bg);
        gc.fillRect(0, 0, w, h);
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);

        Map<String, double[]> at = new LinkedHashMap<>(); // person -> {left, top}
        for (Map.Entry<String, FamilyTreeLayout.Spot> en : layout.spots().entrySet()) {
            double cx = MARGIN + en.getValue().x() * slot - H_GAP / 2.0;
            double top = MARGIN + en.getValue().generation() * (NODE_H + V_GAP);
            at.put(en.getKey(), new double[]{cx - NODE_W / 2 + H_GAP / 2.0, top});
        }

        // partners: a double line between neighbours
        gc.setStroke(line);
        for (String[] s : spouseEdges) {
            double[] a = at.get(s[0]), b = at.get(s[1]);
            if (a == null || b == null) continue;
            double[] l = a[0] <= b[0] ? a : b, r = a[0] <= b[0] ? b : a;
            double y1 = l[1] + NODE_H / 2 - 3, y2 = l[1] + NODE_H / 2 + 3;
            gc.setLineWidth(2);
            gc.strokeLine(l[0] + NODE_W, y1, r[0], y1 + (r[1] - l[1]));
            gc.strokeLine(l[0] + NODE_W, y2, r[0], y2 + (r[1] - l[1]));
        }
        // siblings with no parent: dashed arc under the boxes
        gc.setLineDashes(6, 5);
        gc.setLineWidth(1.5);
        for (String[] s : siblingEdges) {
            double[] a = at.get(s[0]), b = at.get(s[1]);
            if (a == null || b == null) continue;
            double y = Math.max(a[1], b[1]) + NODE_H;
            gc.strokeLine(a[0] + NODE_W / 2, y, a[0] + NODE_W / 2, y + 8);
            gc.strokeLine(a[0] + NODE_W / 2, y + 8, b[0] + NODE_W / 2, y + 8);
            gc.strokeLine(b[0] + NODE_W / 2, y + 8, b[0] + NODE_W / 2, y);
        }
        gc.setLineDashes((double[]) null);

        // parents to children: from the couple's midpoint when both parents are partners, else from each parent
        Map<String, List<String>> parentsOf = new LinkedHashMap<>();
        for (String[] e : parentEdges) parentsOf.computeIfAbsent(e[1], k -> new ArrayList<>()).add(e[0]);
        gc.setLineWidth(2);
        for (Map.Entry<String, List<String>> en : parentsOf.entrySet()) {
            double[] c = at.get(en.getKey());
            if (c == null) continue;
            List<double[]> from = new ArrayList<>();
            List<String> ps = en.getValue();
            boolean couple = ps.size() == 2 && spouseEdges.stream().anyMatch(s ->
                    (s[0].equals(ps.get(0)) && s[1].equals(ps.get(1))) || (s[0].equals(ps.get(1)) && s[1].equals(ps.get(0))));
            if (couple && at.get(ps.get(0)) != null && at.get(ps.get(1)) != null) {
                double[] a = at.get(ps.get(0)), b = at.get(ps.get(1));
                from.add(new double[]{(a[0] + b[0]) / 2 + NODE_W / 2, a[1] + NODE_H / 2});
            } else {
                for (String p : ps) {
                    double[] a = at.get(p);
                    if (a != null) from.add(new double[]{a[0] + NODE_W / 2, a[1] + NODE_H});
                }
            }
            for (double[] f : from) {
                double midY = c[1] - V_GAP / 2.0;
                double sx = f[0], sy = f[1], tx = c[0] + NODE_W / 2, ty = c[1];
                gc.strokeLine(sx, sy, sx, Math.max(sy, midY));
                gc.strokeLine(sx, Math.max(sy, midY), tx, Math.max(sy, midY));
                gc.strokeLine(tx, Math.max(sy, midY), tx, ty);
            }
        }
        boolean dark = Theme.isDark();
        for (Map.Entry<String, double[]> en : at.entrySet()) {
            Card c = g.cards().get(en.getKey());
            if (c != null) node(gc, hits, c, en.getValue()[0], en.getValue()[1], text, dark);
        }
    }

    // ---------------------------------------------------------------- web

    private static Color edgeColor(RelationType t) {
        return switch (t) {
            case ALLY -> Color.web("#2f9e5f");
            case RIVAL -> Color.web("#d64545");
            case MENTOR -> Color.web("#8b5cf6");
            case EMPLOYER -> Color.web("#1aa3a3");
            default -> Color.web("#8a94a3");
        };
    }

    private static void drawWeb(Canvas canvas, List<Hit> hits, RelationGraph g, List<List<String>> comps,
                                Color bg, Color line, Color text) {
        double spacing = NODE_W + 130;
        List<Map<String, double[]>> placed = new ArrayList<>();
        double x = MARGIN, maxH = 0, rowY = MARGIN, rowH = 0, totalW = 900;
        double wrapAt = 1100;
        Map<String, double[]> at = new LinkedHashMap<>();
        for (List<String> comp : comps) {
            Map<String, double[]> rel = WebLayout.circle(comp, spacing * 0.9);
            double r = WebLayout.radius(comp.size(), spacing * 0.9);
            double size = 2 * r + NODE_W + 40;
            double hsize = 2 * r + NODE_H + 60;
            if (x + size > wrapAt && x > MARGIN) {
                x = MARGIN;
                rowY += rowH + 20;
                rowH = 0;
            }
            for (Map.Entry<String, double[]> en : rel.entrySet()) {
                at.put(en.getKey(), new double[]{x + size / 2 + en.getValue()[0] - NODE_W / 2, rowY + hsize / 2 + en.getValue()[1] - NODE_H / 2});
            }
            x += size + 20;
            rowH = Math.max(rowH, hsize);
            totalW = Math.max(totalW, x + MARGIN);
        }
        double h = Math.max(600, rowY + rowH + MARGIN + 40);
        canvas.setWidth(totalW);
        canvas.setHeight(h);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(bg);
        gc.fillRect(0, 0, totalW, h);
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);

        gc.setFont(Font.font("System", 11));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        for (Edge e : g.webEdges()) {
            double[] a = at.get(e.from), b = at.get(e.to);
            if (a == null || b == null) continue;
            double ax = a[0] + NODE_W / 2, ay = a[1] + NODE_H / 2, bx = b[0] + NODE_W / 2, by = b[1] + NODE_H / 2;
            Color c = edgeColor(e.type);
            gc.setStroke(c);
            gc.setLineWidth(2.2);
            gc.strokeLine(ax, ay, bx, by);
            if (!e.type.symmetric()) { // arrow head at the student / employee end
                double ang = Math.atan2(by - ay, bx - ax);
                double tipX = bx - Math.cos(ang) * (NODE_H / 2 + 6), tipY = by - Math.sin(ang) * (NODE_H / 2 + 6);
                gc.setFill(c);
                gc.fillPolygon(new double[]{tipX, tipX - 11 * Math.cos(ang - 0.4), tipX - 11 * Math.cos(ang + 0.4)},
                        new double[]{tipY, tipY - 11 * Math.sin(ang - 0.4), tipY - 11 * Math.sin(ang + 0.4)}, 3);
            }
            String label = e.type == RelationType.OTHER && !e.note.isBlank() ? e.note : shortLabel(e.type);
            double mx = (ax + bx) / 2, my = (ay + by) / 2;
            Text t = new Text(label);
            t.setFont(Font.font("System", 11));
            double tw = t.getLayoutBounds().getWidth() + 10;
            gc.setFill(bg);
            gc.fillRoundRect(mx - tw / 2, my - 9, tw, 18, 8, 8);
            gc.setFill(c.darker());
            gc.fillText(label, mx, my);
        }
        boolean dark = Theme.isDark();
        for (Map.Entry<String, double[]> en : at.entrySet()) {
            Card c = g.cards().get(en.getKey());
            if (c != null) node(gc, hits, c, en.getValue()[0], en.getValue()[1], text, dark);
        }
    }

    private static String shortLabel(RelationType t) {
        return switch (t) {
            case ALLY -> "ally";
            case RIVAL -> "rival";
            case MENTOR -> "mentor";
            case EMPLOYER -> "employer";
            default -> "connected";
        };
    }
}
