package dnd.ui;

import dnd.model.Session;
import dnd.storage.ProjectStore;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;
import java.util.function.Consumer;

/** The campaign so far: every session in order with its dates, the time that passed between them and a recap. */
public final class TimelineDialog {
    private TimelineDialog() { }

    private static Stage open;

    public static void show(Window owner, ProjectStore store, Consumer<String> openItem) {
        if (open != null) {
            open.toFront();
            return;
        }
        VBox list = new VBox(0);
        list.setPadding(new Insets(14));
        List<Session> sessions = store.allSessions();
        if (sessions.isEmpty()) {
            Label none = new Label("No sessions yet. Add one with + Session in the project tree.");
            none.setStyle("-fx-text-fill: -t-text3;");
            list.getChildren().add(none);
        }
        Session before = null;
        for (Session s : sessions) {
            String gap = Session.gapText(before, s);
            if (gap != null) {
                Label g = new Label("      " + gap);
                g.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 0.917em; -fx-padding: 2 0 2 0;");
                list.getChildren().add(g);
            }
            list.getChildren().add(entry(s, openItem));
            before = s;
        }
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        Stage stage = new Stage();
        stage.setTitle("Timeline");
        if (owner != null) stage.initOwner(owner);
        Scene scene = new Scene(scroll, UiScale.px(640), UiScale.px(640));
        scene.setOnKeyPressed(e -> {
            if (dnd.keys.Shortcuts.matches(e, "dialog.close")) stage.close();
        });
        stage.setScene(scene);
        stage.setOnHidden(e -> open = null);
        open = stage;
        stage.show();
    }

    private static HBox entry(Session s, Consumer<String> openItem) {
        Region dot = new Region();
        dot.setMinSize(12, 12);
        dot.setMaxSize(12, 12);
        dot.setStyle("-fx-background-color: " + NodeColors.SESSION + "; -fx-background-radius: 6;");
        VBox dotCol = new VBox(dot);
        dotCol.setPadding(new Insets(4, 10, 0, 0));
        Label title = new Label(s.label());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 1.167em; -fx-text-fill: -t-text;");
        VBox text = new VBox(2, title);
        String dates = SessionCardView.datesText(s);
        if (!dates.isEmpty()) {
            Label d = new Label(dates);
            d.setStyle("-fx-font-size: 0.917em; -fx-text-fill: -t-text3;");
            text.getChildren().add(d);
        }
        String body = s.recap != null && !s.recap.isBlank() ? s.recap : s.summary;
        if (body != null && !body.isBlank()) {
            String shown = body.trim().replaceAll("\\s+", " ");
            Label b = new Label(shown.length() > 220 ? shown.substring(0, 217) + "..." : shown);
            b.setWrapText(true);
            b.setStyle("-fx-text-fill: -t-text2;");
            text.getChildren().add(b);
        }
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(dotCol, text);
        row.setPadding(new Insets(6, 8, 6, 8));
        row.setStyle("-fx-cursor: hand;");
        row.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) openItem.accept(s.id);
        });
        return row;
    }
}
