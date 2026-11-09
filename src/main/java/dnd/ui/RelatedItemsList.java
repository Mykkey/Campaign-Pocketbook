package dnd.ui;

import dnd.storage.ProjectStore;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A list of project items (characters, maps, quests...) with Add, Open and Remove, used by quests and sessions. */
final class RelatedItemsList extends VBox {
    private final ListView<String> list = new ListView<>();
    private final List<String> ids;
    private final ProjectStore store;
    private final Consumer<String> openItem;
    private final Runnable changed;

    /**
     * @param ids     the live list of item ids this edits
     * @param picker  lets the user pick a project item to add
     * @param changed called after the list is edited
     */
    RelatedItemsList(ProjectStore store, List<String> ids, Supplier<Optional<String>> picker, Consumer<String> openItem,
                     Runnable changed, String addTooltip, double height) {
        super(6);
        this.store = store;
        this.ids = ids;
        this.openItem = openItem;
        this.changed = changed;

        list.setPrefHeight(height);
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(String id, boolean empty) {
                super.updateItem(id, empty);
                if (empty || id == null) {
                    setText(null);
                    return;
                }
                String n = store.itemNameOrNull(id);
                setText(n == null ? "(deleted item)" : n);
            }
        });
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) openSelected();
        });
        rebuild();

        Button add = new Button("Add...");
        add.setTooltip(IconButtons.tip(addTooltip));
        add.setOnAction(e -> picker.get().ifPresent(id -> {
            if (!ids.contains(id)) {
                ids.add(id);
                rebuild();
                changed.run();
            }
        }));
        Button open = new Button("Open");
        open.setOnAction(e -> openSelected());
        Button remove = new Button("Remove");
        remove.setOnAction(e -> {
            String sel = list.getSelectionModel().getSelectedItem();
            if (sel != null) {
                ids.remove(sel);
                rebuild();
                changed.run();
            }
        });
        getChildren().addAll(list, new HBox(6, add, open, remove));
    }

    private void openSelected() {
        String sel = list.getSelectionModel().getSelectedItem();
        if (sel != null && store.itemExists(sel)) openItem.accept(sel);
    }

    private void rebuild() {
        list.setItems(FXCollections.observableArrayList(ids));
    }
}
