package dnd.ui;

import dnd.rules.RulesReference;
import dnd.rules.RulesReference.Entry;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

/** A small non-modal window with the rules a DM looks up most: conditions, combat, cover, travel, resting and hazards. */
public final class RulesReferenceDialog {
    private static Stage open;
    private static TextField searchField;
    private static ListView<Entry> list;

    private RulesReferenceDialog() { }

    public static void show(Window owner) {
        show(owner, null);
    }

    /** Shows the window (or brings it to the front) and jumps to the entry with this title, if there is one. */
    public static void show(Window owner, String title) {
        if (open == null) build(owner);
        else {
            open.toFront();
            open.requestFocus();
        }
        if (title != null) select(title);
    }

    private static void select(String title) {
        searchField.clear();
        list.setItems(FXCollections.observableArrayList(RulesReference.search("")));
        for (Entry e : list.getItems()) {
            if (e.title().equalsIgnoreCase(title)) {
                list.getSelectionModel().select(e);
                list.scrollTo(e);
                return;
            }
        }
    }

    private static void build(Window owner) {
        searchField = new TextField();
        searchField.setPromptText("Search the rules (grapple, cover, exhaustion...)");
        list = new ListView<>(FXCollections.observableArrayList(RulesReference.search("")));
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Entry e, boolean empty) {
                super.updateItem(e, empty);
                if (empty || e == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label t = new Label(e.title());
                Label c = new Label(e.category());
                c.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 10px;");
                setGraphic(new VBox(0, t, c));
                setText(null);
            }
        });
        Label heading = new Label();
        heading.setStyle("-fx-font-size: 17px; -fx-font-weight: bold;");
        Label category = new Label();
        category.setStyle("-fx-text-fill: -t-text3;");
        Label body = new Label();
        body.setWrapText(true);
        body.setStyle("-fx-font-size: 13.5px;");
        VBox detail = new VBox(8, heading, category, body);
        detail.setPadding(new Insets(14));
        ScrollPane scroll = new ScrollPane(detail);
        scroll.setFitToWidth(true);

        list.getSelectionModel().selectedItemProperty().addListener((o, a, e) -> {
            heading.setText(e == null ? "" : e.title());
            category.setText(e == null ? "" : e.category());
            body.setText(e == null ? "Pick something on the left, or search above." : e.body());
        });
        searchField.textProperty().addListener((o, a, q) -> {
            list.setItems(FXCollections.observableArrayList(RulesReference.search(q)));
            if (!list.getItems().isEmpty()) list.getSelectionModel().selectFirst();
        });
        list.getSelectionModel().selectFirst();

        Label note = new Label("A memory aid in plain words, not a rulebook: when in doubt, your book and your judgement win.");
        note.setWrapText(true);
        note.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 11px;");
        VBox left = new VBox(6, searchField, list, note);
        left.setPadding(new Insets(8));
        VBox.setVgrow(list, Priority.ALWAYS);
        SplitPane split = new SplitPane(left, scroll);
        split.setDividerPositions(0.36);

        Stage stage = new Stage();
        stage.setTitle("Quick reference");
        if (owner != null) stage.initOwner(owner);
        Scene scene = new Scene(split, 760, 540);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.close();
        });
        stage.setScene(scene);
        stage.setOnHidden(e -> {
            open = null;
            list = null;
            searchField = null;
        });
        open = stage;
        stage.show();
    }
}
