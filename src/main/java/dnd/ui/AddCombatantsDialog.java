package dnd.ui;

import dnd.model.Card;
import dnd.model.Combatant.Side;
import dnd.storage.ProjectStore;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Pick cards from the project to bring into the battle: how many of each, which side, and whether they act as a group. */
public final class AddCombatantsDialog {
    private AddCombatantsDialog() { }

    public record Pick(Card card, int count, boolean group, Side side) { }

    private static final class Row {
        final Card card;
        final CheckBox use = new CheckBox();
        final Spinner<Integer> count = new Spinner<>(1, 50, 1);
        final CheckBox group = new CheckBox("Act as a group");
        final ComboBox<Side> side = new ComboBox<>();

        Row(Card c) {
            card = c;
            count.setEditable(true);
            count.setPrefWidth(70);
            side.getItems().setAll(Side.values());
            side.setValue(c.kind == Card.Kind.PLAYER ? Side.PLAYER : c.kind == Card.Kind.NPC ? Side.ALLY : Side.ENEMY);
            group.setSelected(c.kind == Card.Kind.MONSTER);
            group.setTooltip(new Tooltip("Share one initiative and take the turn together (good for a pack of goblins)"));
            count.valueProperty().addListener((o, a, n) -> {
                if (n != null && n > 1 && !use.isSelected()) use.setSelected(true);
            });
        }
    }

    public static Optional<List<Pick>> show(Window owner, ProjectStore store) {
        List<Card> cards = new ArrayList<>();
        for (String id : store.allCardIds()) {
            Card c = store.card(id);
            if (c != null) cards.add(c);
        }
        cards.sort(Comparator.comparing((Card c) -> c.kind).thenComparing(c -> c.name.toLowerCase()));

        Dialog<List<Pick>> d = new Dialog<>();
        d.setTitle("Add to battle");
        d.setHeaderText("Choose who is fighting");
        if (owner != null) d.initOwner(owner);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox rows = new VBox(6);
        List<Row> all = new ArrayList<>();
        for (Card c : cards) {
            Row r = new Row(c);
            all.add(r);
            r.use.setText(c.name);
            r.use.setPrefWidth(190);
            Label info = new Label("AC " + c.ac + " | HP " + c.hpMax + " | CR " + c.challenge);
            info.setStyle("-fx-text-fill: #777; -fx-font-size: 11px;");
            info.setPrefWidth(150);
            HBox h = new HBox(8, r.use, info, new Label("x"), r.count, r.side, r.group);
            h.setAlignment(Pos.CENTER_LEFT);
            rows.getChildren().add(h);
        }
        if (cards.isEmpty()) {
            Label none = new Label("There are no cards in this project yet.\nCreate some with + Card in the project tree first.");
            none.setStyle("-fx-text-fill: #888;");
            rows.getChildren().add(none);
        }
        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setPrefSize(820, 380);
        VBox box = new VBox(8, new Label("Tick each creature to add, set how many, and pick its side."), scroll);
        box.setPadding(new Insets(10));
        VBox.setVgrow(scroll, Priority.ALWAYS);
        Region spacer = new Region();
        box.getChildren().add(spacer);
        d.getDialogPane().setContent(box);

        d.setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            List<Pick> picks = new ArrayList<>();
            for (Row r : all) {
                if (!r.use.isSelected()) continue;
                int n;
                try {
                    n = Integer.parseInt(r.count.getEditor().getText().trim());
                } catch (NumberFormatException e) {
                    n = r.count.getValue() == null ? 1 : r.count.getValue();
                }
                picks.add(new Pick(r.card, Math.max(1, Math.min(50, n)), r.group.isSelected(), r.side.getValue()));
            }
            return picks;
        });
        return d.showAndWait();
    }
}
