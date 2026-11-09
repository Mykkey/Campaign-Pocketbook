package dnd.ui;

import dnd.library.CommonEnemies;
import dnd.model.Card;
import dnd.storage.ProjectStore;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The window for the shared Common Enemies library: the creatures offered in the encounter builder's dropdown and
 * in the battle's Add dialog. Edit a creature's stats, add homebrew ones (new, copied from a project card, or
 * duplicated from another), remove ones you never use, or put the starter creatures back.
 */
public final class CommonEnemiesDialog {
    private CommonEnemiesDialog() { }

    private static Stage open;

    /** Opens (or raises) the library window, selecting {@code selectId} if given. */
    public static void show(Window owner, ProjectStore store, String selectId) {
        if (open != null && open.isShowing()) {
            open.toFront();
            open.requestFocus();
            return;
        }
        CommonEnemies lib = CommonEnemies.shared();
        Stage stage = new Stage();
        open = stage;
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("Common enemies");

        ListView<Card> list = new ListView<>();
        list.setPrefWidth(250);
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(Card c, boolean empty) {
                super.updateItem(c, empty);
                setText(empty || c == null ? null : c.name + "   (CR " + (c.challenge == null || c.challenge.isBlank() ? "?" : c.challenge) + ")");
            }
        });
        BorderPane editorHolder = new BorderPane();
        Label placeholder = new Label("Pick a creature on the left to edit its stats.");
        placeholder.setStyle("-fx-text-fill: #888;");
        placeholder.setPadding(new Insets(20));
        editorHolder.setCenter(placeholder);

        Runnable reload = () -> {
            Card keep = list.getSelectionModel().getSelectedItem();
            list.setItems(FXCollections.observableArrayList(lib.list()));
            if (keep != null) selectById(list, keep.id);
        };
        reload.run();

        list.getSelectionModel().selectedItemProperty().addListener((o, a, c) -> {
            if (c == null) {
                editorHolder.setCenter(placeholder);
                return;
            }
            editorHolder.setCenter(new CardEditor(c, store, newName -> {
                lib.save(c);
                list.refresh();
            }, () -> lib.save(c), false));
        });

        Button add = new Button("+ New creature");
        add.setMaxWidth(Double.MAX_VALUE);
        add.setTooltip(new Tooltip("Start a blank creature (for example a homebrew monster)"));
        add.setOnAction(e -> {
            Card c = new Card();
            c.name = "New creature";
            Card saved = lib.add(c);
            reload.run();
            selectById(list, saved.id);
        });
        Button fromCard = new Button("+ From a project card...");
        fromCard.setMaxWidth(Double.MAX_VALUE);
        fromCard.setTooltip(new Tooltip("Copy a card from the open project into the library so every project can use it"));
        fromCard.setOnAction(e -> pickProjectCard(stage, store).ifPresent(card -> {
            Card saved = lib.add(card);
            reload.run();
            selectById(list, saved.id);
        }));
        Button dup = new Button("Duplicate");
        dup.setMaxWidth(Double.MAX_VALUE);
        dup.setOnAction(e -> {
            Card c = list.getSelectionModel().getSelectedItem();
            if (c == null) return;
            Card copy = c.copy();
            copy.name = c.name + " (copy)";
            Card saved = lib.add(copy);
            reload.run();
            selectById(list, saved.id);
        });
        Button del = new Button("Remove");
        del.setMaxWidth(Double.MAX_VALUE);
        del.setTooltip(new Tooltip("Take it off the dropdown. Cards already made from it in a project are kept."));
        del.setOnAction(e -> {
            Card c = list.getSelectionModel().getSelectedItem();
            if (c == null) return;
            Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Remove " + c.name + " from the common enemies? "
                    + "Cards already made from it in your projects are kept, but encounters that use it will not find it.",
                    ButtonType.OK, ButtonType.CANCEL);
            a.initOwner(stage);
            if (a.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            lib.delete(c.id);
            list.getSelectionModel().clearSelection();
            reload.run();
        });
        Button restore = new Button("Restore starter creatures");
        restore.setMaxWidth(Double.MAX_VALUE);
        restore.setTooltip(new Tooltip("Puts the bundled creatures (goblin, orc, wolf...) back to their original stats, "
                + "re-adding any you removed. Your homebrew creatures are not touched."));
        restore.setOnAction(e -> {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Reset the bundled creatures to their original stats "
                    + "and bring back any you removed? Your changes to them will be lost. Homebrew creatures are not touched.",
                    ButtonType.OK, ButtonType.CANCEL);
            a.initOwner(stage);
            if (a.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            lib.restoreDefaults();
            list.getSelectionModel().clearSelection();
            reload.run();
        });

        Label hint = new Label("These creatures are available in every project's encounters and battles without "
                + "making a card first. Edit the stats here; the first time one is used in a project a card for it is "
                + "made in a \"" + ProjectStore.COMMON_FOLDER + "\" folder and refreshed from here each time.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");

        VBox left = new VBox(6, list, add, fromCard, dup, del, restore, hint);
        left.setPadding(new Insets(8));
        VBox.setVgrow(list, Priority.ALWAYS);
        left.setPrefWidth(270);

        BorderPane root = new BorderPane();
        root.setLeft(left);
        root.setCenter(editorHolder);
        stage.setScene(new Scene(root, 1250, 760));
        stage.show();
        if (selectId != null) selectById(list, selectId);
    }

    private static void selectById(ListView<Card> list, String id) {
        for (Card c : list.getItems()) {
            if (c.id.equals(id)) {
                list.getSelectionModel().select(c);
                list.scrollTo(c);
                return;
            }
        }
    }

    private static Optional<Card> pickProjectCard(Window owner, ProjectStore store) {
        List<Card> cards = new ArrayList<>();
        for (String id : store.allCardIds()) {
            Card c = store.card(id);
            if (c != null) cards.add(c);
        }
        cards.sort(java.util.Comparator.comparing(c -> c.name.toLowerCase()));
        if (cards.isEmpty()) {
            Alert a = new Alert(Alert.AlertType.INFORMATION, "The open project has no cards to copy.");
            a.initOwner(owner);
            a.show();
            return Optional.empty();
        }
        ComboBox<Card> choice = new ComboBox<>(FXCollections.observableArrayList(cards));
        choice.setValue(cards.get(0));
        choice.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Card c) { return c == null ? "" : c.name; }

            @Override public Card fromString(String s) { return null; }
        });
        Dialog<Card> d = new Dialog<>();
        d.setTitle("Copy a card");
        d.setHeaderText("Which card should be added to the common enemies?");
        d.getDialogPane().setContent(new VBox(8, choice));
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.setResultConverter(bt -> bt == ButtonType.OK ? choice.getValue() : null);
        d.initOwner(owner);
        return d.showAndWait();
    }
}
