package dnd.ui;

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

/** Edit the note and the link of something on a world map: a symbol, a line or a region. */
final class LinkNoteDialog {
    private LinkNoteDialog() { }

    /** What the user chose; either part may be null (no note, no link). */
    record Result(String note, String link) { }

    private record Item(String id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * @param what     what is being edited, for the title ("Capital", "Silverflow river")
     * @param openLink opens the linked item (the dialog stays open)
     * @return the new note and link, or empty if the user cancelled
     */
    static Optional<Result> show(Window owner, ProjectStore store, String what, String note, String link, Consumer<String> openLink) {
        TextArea text = new TextArea(note == null ? "" : note);
        text.setPromptText("A note for yourself: what is here, what the players know, a reminder...");
        text.setWrapText(true);
        text.setPrefRowCount(5);

        List<Item> items = new ArrayList<>();
        items.add(new Item(null, "(no link)"));
        List<TreeNode> nodes = new ArrayList<>();
        store.project.root.collectItems(nodes);
        for (TreeNode n : nodes) {
            String kind = n.isBattleMap() ? "battlemap" : n.isCard() ? "card" : n.isEncounter() ? "encounter" : n.isSpell() ? "spell" : n.isMagicItem() ? "magic item"
                    : n.isQuest() ? "quest" : n.isSession() ? "session" : n.isWorldMap() ? "world map" : "whiteboard";
            items.add(new Item(n.id, n.name + "  (" + kind + ")"));
        }
        ComboBox<Item> combo = new ComboBox<>(FXCollections.observableArrayList(items));
        combo.setMaxWidth(Double.MAX_VALUE);
        Item current = items.get(0);
        for (Item i : items) if (i.id() != null && i.id().equals(link)) current = i;
        combo.setValue(current);
        Button open = new Button("Open linked item");
        open.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> combo.getValue() == null || combo.getValue().id() == null, combo.valueProperty()));
        open.setOnAction(e -> {
            if (combo.getValue() != null && combo.getValue().id() != null) openLink.accept(combo.getValue().id());
        });

        Label hint = new Label("Hover the item on the map to see the note and a preview of what it links to. Double-click it to open the link.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 0.917em;");
        VBox box = new VBox(8, text, new Label("Link to"), combo, open, hint);
        box.setPadding(new Insets(6));
        box.setPrefWidth(420);

        Dialog<Result> d = new Dialog<>();
        d.setTitle("Link and note");
        d.setHeaderText(what);
        if (owner != null) d.initOwner(owner);
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        d.getDialogPane().setContent(box);
        d.setResultConverter(bt -> {
            if (bt != save) return null;
            String n = text.getText().trim();
            return new Result(n.isEmpty() ? null : n, combo.getValue() == null ? null : combo.getValue().id());
        });
        return d.showAndWait();
    }
}
