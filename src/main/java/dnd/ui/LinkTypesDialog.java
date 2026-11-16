package dnd.ui;

import dnd.model.Card;
import dnd.model.LinkType;
import dnd.model.Relation;
import dnd.model.RelationType;
import dnd.storage.ProjectStore;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.Window;

/** The custom links of the campaign: make a new one, change its name or colour, or delete it. */
public final class LinkTypesDialog {
    private LinkTypesDialog() { }

    private static Stage open;

    /** How many relationships on cards use this custom link. */
    static int uses(ProjectStore store, LinkType t) {
        int n = 0;
        for (String id : store.allCardIds()) {
            Card c = store.card(id);
            if (c == null) continue;
            for (Relation r : c.relations) if (r != null && r.type == RelationType.CUSTOM && t.id.equals(r.linkTypeId)) n++;
        }
        return n;
    }

    /** @param changed called after a link was made, changed or deleted (so the screens that show them can refresh) */
    public static void show(Window owner, ProjectStore store, Runnable changed) {
        if (open != null && open.isShowing()) {
            open.toFront();
            return;
        }
        ListView<LinkType> list = new ListView<>();
        Label none = new Label("No custom links yet. Press New... to make one: choose its name, how it reads from the other side, and its colour.");
        none.setWrapText(true);
        none.setStyle("-fx-text-fill: -t-text3;");
        list.setPlaceholder(none);
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(LinkType t, boolean empty) {
                super.updateItem(t, empty);
                if (empty || t == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Rectangle swatch = new Rectangle(26, 8);
                swatch.setArcWidth(4);
                swatch.setArcHeight(4);
                try {
                    swatch.setFill(Color.web(t.colour));
                } catch (IllegalArgumentException e) {
                    swatch.setFill(Color.GRAY);
                }
                int n = uses(store, t);
                setGraphic(swatch);
                setText(t.name + (t.directed() ? "   /   " + t.reverseName : "") + "     (used " + n + (n == 1 ? " time)" : " times)"));
            }
        });
        Button add = new Button("New...");
        Button edit = new Button("Change...");
        Button delete = new Button("Delete");
        Button close = new Button("Close");
        Runnable refresh = () -> list.setItems(FXCollections.observableArrayList(store.project.linkTypes));
        add.setOnAction(e -> LinkTypeDialog.show(open, null).ifPresent(t -> {
            store.project.linkTypes.add(t);
            store.markProjectDirty();
            refresh.run();
            changed.run();
        }));
        Runnable doEdit = () -> {
            LinkType sel = list.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            LinkTypeDialog.show(open, sel).ifPresent(t -> {
                int i = store.project.linkTypes.indexOf(sel);
                if (i >= 0) store.project.linkTypes.set(i, t);
                store.markProjectDirty();
                refresh.run();
                changed.run();
            });
        };
        edit.setOnAction(e -> doEdit.run());
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) doEdit.run();
        });
        delete.setOnAction(e -> {
            LinkType sel = list.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            int n = uses(store, sel);
            Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Delete the link \"" + sel.name + "\"?" + (n > 0
                    ? " It is used " + n + (n == 1 ? " time" : " times") + "; those connections stay but are shown as plain \"Connected to\"." : ""),
                    ButtonType.OK, ButtonType.CANCEL);
            a.setHeaderText("Delete custom link");
            a.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
                store.project.linkTypes.remove(sel);
                store.markProjectDirty();
                refresh.run();
                changed.run();
            });
        });
        edit.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        delete.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());

        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox buttons = new HBox(8, add, edit, delete, gap, close);
        buttons.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(list, Priority.ALWAYS);
        VBox box = new VBox(8, new Label("Your own kinds of link. Pick one in the type box of a relationship on any card (or choose \"New custom link...\" there)."), list, buttons);
        box.setPadding(new Insets(12));
        Stage stage = new Stage();
        stage.setTitle("Custom links");
        if (owner != null) stage.initOwner(owner);
        close.setOnAction(e -> stage.close());
        stage.setScene(new Scene(box, UiScale.px(560), UiScale.px(380)));
        stage.setOnHidden(e -> open = null);
        open = stage;
        refresh.run();
        stage.show();
    }
}
