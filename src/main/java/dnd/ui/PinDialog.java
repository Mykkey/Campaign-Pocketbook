package dnd.ui;

import dnd.model.MapPin;
import dnd.model.TreeNode;
import dnd.storage.ProjectStore;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Edit a map pin: its note, and an optional link to a whiteboard, card or encounter. */
final class PinDialog {
    private PinDialog() { }

    enum Action { SAVE, DELETE, CANCEL }

    record Result(Action action, String text, String link) { }

    private record Item(String id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * @param number    the pin's number on the map, shown in the title
     * @param openLink  opens the linked item (the dialog stays open)
     */
    static Result show(Window owner, ProjectStore store, MapPin pin, int number, boolean isNew, Consumer<String> openLink) {
        TextArea text = new TextArea(pin.text == null ? "" : pin.text);
        text.setPromptText("What is here? A trap, a secret door, what the monster does, a reminder...");
        text.setWrapText(true);
        text.setPrefRowCount(5);

        List<Item> items = new ArrayList<>();
        items.add(new Item(null, "(no link)"));
        List<TreeNode> nodes = new ArrayList<>();
        store.project.root.collectItems(nodes);
        for (TreeNode n : nodes) {
            String kind = n.isBattleMap() ? "map" : n.isCard() ? "card" : n.isEncounter() ? "encounter" : n.isSpell() ? "spell" : n.isMagicItem() ? "magic item" : n.isQuest() ? "quest" : n.isSession() ? "session" : "whiteboard";
            items.add(new Item(n.id, n.name + "  (" + kind + ")"));
        }
        ComboBox<Item> link = new ComboBox<>(FXCollections.observableArrayList(items));
        link.setMaxWidth(Double.MAX_VALUE);
        Item current = items.get(0);
        for (Item i : items) if (i.id() != null && i.id().equals(pin.link)) current = i;
        link.setValue(current);
        Button open = new Button("Open linked item");
        open.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> link.getValue() == null || link.getValue().id() == null, link.valueProperty()));
        open.setOnAction(e -> {
            if (link.getValue() != null && link.getValue().id() != null) openLink.accept(link.getValue().id());
        });

        Label hint = new Label("Pins are for you only: the players never see them.");
        hint.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 11px;");
        VBox box = new VBox(8, text, new Label("Link to"), link, open, hint);
        box.setPadding(new Insets(6));
        box.setPrefWidth(420);

        Dialog<Result> d = new Dialog<>();
        d.setTitle("Pin " + number);
        d.setHeaderText(isNew ? "New pin " + number : "Pin " + number);
        if (owner != null) d.initOwner(owner);
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType delete = new ButtonType("Delete pin", ButtonBar.ButtonData.LEFT);
        d.getDialogPane().getButtonTypes().addAll(save, delete, ButtonType.CANCEL);
        d.getDialogPane().setContent(box);
        d.setResultConverter(bt -> {
            if (bt == save) return new Result(Action.SAVE, text.getText().trim(), link.getValue() == null ? null : link.getValue().id());
            if (bt == delete) return new Result(Action.DELETE, null, null);
            return new Result(Action.CANCEL, null, null);
        });
        Optional<Result> r = d.showAndWait();
        return r.orElse(new Result(Action.CANCEL, null, null));
    }
}
