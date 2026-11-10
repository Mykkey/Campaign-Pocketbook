package dnd.ui;

import dnd.library.CommonEnemies;
import dnd.model.BattleMap;
import dnd.model.BoardObject;
import dnd.model.Card;
import dnd.model.Encounter;
import dnd.model.TreeNode;
import dnd.rules.EncounterSummary;
import dnd.storage.ProjectStore;
import dnd.text.RichText;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The small picture of a project item shown in a hover tooltip: a card or spell card as it prints, a battlemap
 * thumbnail, an encounter's creatures and difficulty, or the title and first lines of a whiteboard.
 */
final class ItemPreview {
    private ItemPreview() { }

    private static final double MAX_HEIGHT = 560;

    static Node build(ProjectStore store, String itemId) {
        Node content = content(store, itemId);
        StackPane box = new StackPane(content);
        box.setMaxHeight(MAX_HEIGHT);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(box.widthProperty());
        clip.heightProperty().bind(box.heightProperty());
        box.setClip(clip);
        return box;
    }

    /** A green-edged box with the DM's own note ({@code **bold**} and {@code *italic*} are shown as such). */
    static Node note(String markup) {
        javafx.scene.text.TextFlow flow = new javafx.scene.text.TextFlow();
        for (RichText.Run r : RichText.parse(markup == null ? "" : markup)) {
            javafx.scene.text.Text t = new javafx.scene.text.Text(r.text());
            t.setFont(javafx.scene.text.Font.font("System", r.bold() ? javafx.scene.text.FontWeight.BOLD : javafx.scene.text.FontWeight.NORMAL,
                    r.italic() ? javafx.scene.text.FontPosture.ITALIC : javafx.scene.text.FontPosture.REGULAR, 14));
            t.setStyle("-fx-fill: -t-text;");
            flow.getChildren().add(t);
        }
        flow.setMaxWidth(320);
        flow.setPrefWidth(320);
        VBox box = new VBox(flow);
        box.setId("note-popup");
        box.setPadding(new Insets(10, 12, 10, 10));
        box.setMaxWidth(344);
        box.setStyle("-fx-background-color: -t-surface; -fx-border-color: #16a34a; -fx-border-width: 1 1 1 4;");
        return box;
    }

    /** What a hover shows for words or an object: its note above the preview of what it links to (either may be null). */
    static Node hover(ProjectStore store, String note, String linkedItemId) {
        Node n = note == null ? null : note(note);
        Node p = linkedItemId == null ? null : build(store, linkedItemId);
        if (n != null && p != null) {
            VBox both = new VBox(6, n, p);
            both.setAlignment(javafx.geometry.Pos.TOP_LEFT);
            return both;
        }
        return n != null ? n : p;
    }

    static Node content(ProjectStore store, String id) {
        TreeNode node = id == null ? null : store.project.root.find(id);
        if (node == null || node.folder) return plain("(deleted item)", List.of());
        if (node.isCard()) {
            Card c = store.card(id);
            if (c != null) {
                StatBlockView sv = new StatBlockView(store);
                sv.refresh(c);
                return sv;
            }
        } else if (node.isSpell()) {
            dnd.model.Spell s = store.spell(id);
            if (s != null) return new SpellCardView(s);
        } else if (node.isBattleMap()) {
            BattleMap m = store.battleMap(id);
            if (m != null) {
                ImageView iv = new ImageView(dnd.map.MapRenderer.thumbnail(m, store.assets(), 360));
                iv.setPreserveRatio(true);
                iv.setFitWidth(360);
                return plainWithGraphic(m.name, "Battlemap  -  " + m.cols + " x " + m.rows + " cells", iv);
            }
        } else if (node.isMagicItem()) {
            dnd.model.MagicItem mi = store.magicItem(id);
            if (mi != null) return new MagicItemCardView(mi);
        } else if (node.isWorldMap()) {
            dnd.model.WorldMap wm = store.worldMap(id);
            if (wm != null) {
                ImageView iv = new ImageView(dnd.map.world.WorldThumbnail.of(wm, store.assets(), 360));
                iv.setPreserveRatio(true);
                iv.setFitWidth(360);
                iv.setSmooth(true);
                return plainWithGraphic(wm.name, "World map  -  about " + Math.round(wm.milesAcross()) + " miles across", iv);
            }
        } else if (node.isSession()) {
            dnd.model.Session se = store.session(id);
            if (se != null) return new SessionCardView(store, se);
        } else if (node.isQuest()) {
            dnd.model.Quest qu = store.quest(id);
            if (qu != null) return new QuestCardView(store, qu);
        } else if (node.isEncounter()) {
            Encounter e = store.encounter(id);
            if (e != null) return encounter(store, e);
        } else if (node.isBoard()) {
            dnd.model.Board b = store.board(id);
            if (b != null) return whiteboard(b);
        }
        return plain(node.name, List.of("(could not be loaded)"));
    }

    private static Node encounter(ProjectStore store, Encounter e) {
        EncounterSummary.Summary s = EncounterSummary.of(e, en -> en.libraryId != null
                ? CommonEnemies.shared().get(en.libraryId) : (en.cardId == null ? null : store.card(en.cardId)),
                store.project.partySize, store.project.partyLevel);
        List<String> lines = new ArrayList<>(s.creatures());
        if (lines.isEmpty()) lines.add("No creatures yet.");
        if (s.difficulty() != null) {
            lines.add("");
            lines.add(s.difficulty().label + " for " + s.partySize() + " characters of level " + s.partyLevel()
                    + "  (" + s.enemies() + " enemies, " + s.adjustedXp() + " adjusted XP)");
        }
        if (e.notes != null && !e.notes.isBlank()) {
            lines.add("");
            lines.add(e.notes.trim());
        }
        return plain(e.name, lines, "Encounter");
    }

    private static Node whiteboard(dnd.model.Board b) {
        List<BoardObject> texts = new ArrayList<>();
        for (BoardObject o : b.objects) {
            if ((o.type == BoardObject.Type.TEXT || o.type == BoardObject.Type.STICKY) && o.text != null && !o.text.isBlank()) texts.add(o);
        }
        texts.sort(Comparator.comparingDouble((BoardObject o) -> o.y).thenComparingDouble(o -> o.x));
        List<String> lines = new ArrayList<>();
        for (BoardObject o : texts) {
            for (String line : RichText.plain(o.text).split("\\n")) {
                if (!line.isBlank()) lines.add(line.trim());
                if (lines.size() >= 8) break;
            }
            if (lines.size() >= 8) break;
        }
        if (lines.isEmpty()) lines.add("(nothing written on it yet)");
        return plain(b.name, lines, "Whiteboard");
    }

    private static Node plain(String title, List<String> lines) {
        return plain(title, lines, null);
    }

    private static Node plain(String title, List<String> lines, String kind) {
        VBox box = base();
        Label t = new Label(title == null || title.isBlank() ? "(unnamed)" : title);
        t.setStyle("-fx-font-size: 1.333em; -fx-font-weight: bold; -fx-text-fill: -t-text;");
        box.getChildren().add(t);
        if (kind != null) {
            Label k = new Label(kind);
            k.setStyle("-fx-font-size: 0.917em; -fx-text-fill: -t-text3;");
            box.getChildren().add(k);
        }
        for (String line : lines) {
            Label l = new Label(line);
            l.setWrapText(true);
            l.setMaxWidth(340);
            l.setStyle("-fx-text-fill: -t-text2;");
            box.getChildren().add(l);
        }
        return box;
    }

    private static Node plainWithGraphic(String title, String sub, Node graphic) {
        VBox box = base();
        Label t = new Label(title);
        t.setStyle("-fx-font-size: 1.333em; -fx-font-weight: bold; -fx-text-fill: -t-text;");
        Label k = new Label(sub);
        k.setStyle("-fx-font-size: 0.917em; -fx-text-fill: -t-text3;");
        box.getChildren().addAll(t, k, graphic);
        return box;
    }

    private static VBox base() {
        VBox box = new VBox(4);
        box.setPadding(new Insets(12));
        box.setMaxWidth(380);
        box.setStyle("-fx-background-color: -t-surface; -fx-border-color: -t-border; -fx-border-width: 1;");
        return box;
    }
}