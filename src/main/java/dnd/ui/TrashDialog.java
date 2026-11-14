package dnd.ui;

import dnd.model.TreeNode;
import dnd.storage.ProjectStore;
import dnd.storage.TrashBin;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.function.Consumer;

/** The bin of deleted items: bring one back, erase one for good, or empty the bin. */
public final class TrashDialog {
    private TrashDialog() { }

    private static Stage open;

    /** "just now", "5 minutes ago", "3 hours ago", "2 days ago". */
    public static String ago(long deletedAt, long now) {
        long minutes = Math.max(0, (now - deletedAt) / 60_000);
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        long hours = minutes / 60;
        if (hours < 24) return hours + (hours == 1 ? " hour ago" : " hours ago");
        long days = hours / 24;
        return days + (days == 1 ? " day ago" : " days ago");
    }

    /** How many days are left before an item deleted at {@code deletedAt} is erased for good (never less than 0). */
    public static long daysLeft(long deletedAt, long now) {
        long used = Math.max(0, (now - deletedAt) / (24L * 60 * 60 * 1000));
        return Math.max(0, TrashBin.KEEP_DAYS - used);
    }

    /** What an entry is, for the list: "card", "folder with 3 items"... */
    static String describe(TrashBin.Entry e) {
        TreeNode n = e.node;
        if (n.folder) {
            int count = e.itemCount();
            return "folder with " + count + (count == 1 ? " item" : " items");
        }
        return n.isBattleMap() ? "battlemap" : n.isCard() ? "card" : n.isEncounter() ? "encounter" : n.isSpell() ? "spell" : n.isMagicItem() ? "magic item"
                : n.isQuest() ? "quest" : n.isSession() ? "session" : n.isWorldMap() ? "world map" : n.isOrganisation() ? "organisation" : "whiteboard";
    }

    /**
     * @param restored called with the id of an item that was brought back (so the tree can show it)
     */
    public static void show(Window owner, ProjectStore store, Consumer<String> restored) {
        if (open != null && open.isShowing()) {
            open.toFront();
            return;
        }
        ListView<TrashBin.Entry> list = new ListView<>();
        Label empty = new Label("Nothing has been deleted. When you delete something from the project tree it waits here for "
                + TrashBin.KEEP_DAYS + " days, and you can bring it back.");
        empty.setWrapText(true);
        empty.setStyle("-fx-text-fill: -t-text3;");
        list.setPlaceholder(empty);
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(TrashBin.Entry e, boolean isEmpty) {
                super.updateItem(e, isEmpty);
                if (isEmpty || e == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                long now = System.currentTimeMillis();
                setGraphic(NodeIcons.icon(e.node, false));
                setText(e.node.name + "   (" + describe(e) + ", deleted " + ago(e.deletedAt, now) + ", "
                        + daysLeft(e.deletedAt, now) + " days left)");
            }
        });

        Button restore = new Button("Restore");
        restore.setDefaultButton(true);
        restore.setTooltip(IconButtons.tip("Put the selected item back where it was (or at the top of the project if its folder is gone)"));
        Button forever = new Button("Delete forever");
        forever.setTooltip(IconButtons.tip("Erase the selected item for good"));
        Button emptyBin = new Button("Empty the bin");
        emptyBin.setTooltip(IconButtons.tip("Erase everything in the bin for good"));
        Button close = new Button("Close");

        Runnable refresh = () -> {
            list.setItems(FXCollections.observableArrayList(store.trash().entries()));
            emptyBin.setDisable(store.trash().isEmpty());
        };
        Runnable doRestore = () -> {
            TrashBin.Entry e = list.getSelectionModel().getSelectedItem();
            if (e == null) return;
            TreeNode back = store.restoreFromTrash(e.id);
            if (back == null) {
                new Alert(Alert.AlertType.INFORMATION, "\"" + e.node.name + "\" could not be restored: something with the same identity is already in the project.").show();
                return;
            }
            refresh.run();
            restored.accept(back.id);
        };
        restore.setOnAction(e -> doRestore.run());
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) doRestore.run();
        });
        forever.setOnAction(e -> {
            TrashBin.Entry sel = list.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Erase \"" + sel.node.name + "\" for good? This cannot be undone.", ButtonType.OK, ButtonType.CANCEL);
            a.setHeaderText("Delete forever");
            a.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
                store.trash().deleteForever(sel);
                refresh.run();
            });
        });
        emptyBin.setOnAction(e -> {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Erase everything in the bin for good? This cannot be undone.", ButtonType.OK, ButtonType.CANCEL);
            a.setHeaderText("Empty the bin");
            a.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
                store.trash().empty();
                refresh.run();
            });
        });
        restore.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        forever.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());

        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox buttons = new HBox(8, restore, forever, gap, emptyBin, close);
        buttons.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(list, Priority.ALWAYS);
        VBox box = new VBox(8, new Label("Deleted items (kept for " + TrashBin.KEEP_DAYS + " days). Double-click one to bring it back."), list, buttons);
        box.setPadding(new Insets(12));
        BorderPane root = new BorderPane(box);
        Stage stage = new Stage();
        stage.setTitle("Deleted items");
        if (owner != null) stage.initOwner(owner);
        close.setOnAction(e -> stage.close());
        Scene scene = new Scene(root, UiScale.px(640), UiScale.px(440));
        scene.setOnKeyPressed(e -> {
            if (dnd.keys.Shortcuts.matches(e, "dialog.close")) stage.close();
        });
        stage.setScene(scene);
        stage.setOnHidden(e -> open = null);
        open = stage;
        refresh.run();
        stage.show();
    }
}
