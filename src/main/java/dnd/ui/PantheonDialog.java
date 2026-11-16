package dnd.ui;

import dnd.model.Card;
import dnd.model.TreeNode;
import dnd.rules.Pantheon;
import dnd.storage.ProjectStore;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The pantheon at a glance: all your deities on the alignment grid, by domain (with the domains nobody holds), by rank and by the
 * organisation they belong to. Click a god to open its card.
 */
public final class PantheonDialog {
    private PantheonDialog() { }

    private static Stage open;

    public static void show(Window owner, ProjectStore store, Consumer<String> openItem) {
        if (open != null && open.isShowing()) {
            open.toFront();
            open.requestFocus();
            return;
        }
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Label summary = new Label();
        summary.setStyle("-fx-text-fill: -t-text3;");
        Button refresh = new Button("Refresh");
        refresh.setTooltip(IconButtons.tip("Pick up gods you have just added or changed"));
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox top = new HBox(8, summary, gap, refresh);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(8, 10, 4, 10));

        Runnable[] build = new Runnable[1];
        build[0] = () -> {
            int selected = Math.max(0, tabs.getSelectionModel().getSelectedIndex());
            List<Card> cards = new ArrayList<>();
            for (String id : store.allCardIds()) {
                Card c = store.card(id);
                if (c != null) cards.add(c);
            }
            List<Card> gods = Pantheon.deitiesOf(cards);
            summary.setText(gods.isEmpty() ? "No deities yet" : gods.size() + (gods.size() == 1 ? " deity" : " deities"));
            tabs.getTabs().clear();
            if (gods.isEmpty()) {
                Label none = new Label("You have no deities yet. Press the halo card button above the project tree (+ Deity) to make one, "
                        + "or change a card's Kind to Deity. They will appear here by alignment, domain and rank.");
                none.setWrapText(true);
                none.setStyle("-fx-text-fill: -t-text3;");
                none.setPadding(new Insets(20));
                tabs.getTabs().add(tab("Overview", none));
                return;
            }
            tabs.getTabs().add(tab("Alignment", alignment(store, gods, openItem)));
            tabs.getTabs().add(tab("Domains", domains(store, gods, openItem)));
            tabs.getTabs().add(tab("Ranks", grouped(store, Pantheon.byRank(gods), openItem)));
            tabs.getTabs().add(tab("Organisations", grouped(store, Pantheon.byGroup(gods, g -> {
                List<String> names = new ArrayList<>();
                for (dnd.model.Organisation o : store.organisationsOf(g.id)) names.add(o.name);
                return names;
            }), openItem)));
            tabs.getSelectionModel().select(Math.min(selected, tabs.getTabs().size() - 1));
        };
        refresh.setOnAction(e -> build[0].run());

        BorderPane root = new BorderPane(tabs);
        root.setTop(top);
        Stage stage = new Stage();
        stage.setTitle("Pantheon");
        if (owner != null) stage.initOwner(owner);
        Scene scene = new Scene(root, UiScale.px(980), UiScale.px(680));
        scene.setOnKeyPressed(e -> {
            if (dnd.keys.Shortcuts.matches(e, "dialog.close")) stage.close();
        });
        stage.setScene(scene);
        stage.setOnHidden(e -> open = null);
        open = stage;
        build[0].run();
        stage.show();
    }

    private static Tab tab(String title, Node content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        Tab t = new Tab(title, sp);
        t.setClosable(false);
        return t;
    }

    /** One god as a small clickable label with its icon; hover for the card. */
    private static Node chip(ProjectStore store, Card god, Consumer<String> openItem) {
        TreeNode node = store.project.root.find(god.id);
        Label l = new Label(god.name);
        if (node != null) l.setGraphic(NodeIcons.icon(node, false));
        l.setGraphicTextGap(5);
        l.setStyle("-fx-background-color: -t-surface2; -fx-background-radius: 10; -fx-padding: 3 9 3 7; -fx-cursor: hand; -fx-text-fill: -t-text;");
        Tooltip tip = IconButtons.tip("");
        tip.setShowDelay(javafx.util.Duration.millis(350));
        tip.setShowDuration(javafx.util.Duration.minutes(5));
        tip.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        tip.setOnShowing(e -> tip.setGraphic(ItemPreview.build(store, god.id)));
        l.setTooltip(tip);
        l.setOnMouseClicked(e -> openItem.accept(god.id));
        return l;
    }

    private static FlowPane chips(ProjectStore store, List<Card> gods, Consumer<String> openItem) {
        FlowPane flow = new FlowPane(6, 6);
        for (Card g : gods) flow.getChildren().add(chip(store, g, openItem));
        return flow;
    }

    // ---------------------------------------------------------------- alignment

    private static Node alignment(ProjectStore store, List<Card> gods, Consumer<String> openItem) {
        Pantheon.AlignmentGrid g = Pantheon.alignmentGrid(gods);
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));
        ColumnConstraints head = new ColumnConstraints();
        head.setPercentWidth(10);
        grid.getColumnConstraints().add(head);
        for (int c = 0; c < 3; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(30);
            cc.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(cc);
        }
        for (int c = 0; c < 3; c++) grid.add(heading(Pantheon.MORAL[c]), c + 1, 0);
        for (int r = 0; r < 3; r++) {
            grid.add(heading(Pantheon.LAW[r]), 0, r + 1);
            for (int c = 0; c < 3; c++) {
                VBox cell = new VBox(4);
                cell.setPadding(new Insets(8));
                cell.setMinHeight(90);
                cell.setStyle("-fx-border-color: -t-border; -fx-border-radius: 6; -fx-background-color: -t-surface; -fx-background-radius: 6;");
                Label name = new Label(Pantheon.LAW[r].equals("Neutral") && Pantheon.MORAL[c].equals("Neutral") ? "True neutral"
                        : Pantheon.LAW[r] + " " + Pantheon.MORAL[c].toLowerCase());
                name.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 0.833em;");
                cell.getChildren().addAll(name, chips(store, g.cells().get(r).get(c), openItem));
                grid.add(cell, c + 1, r + 1);
            }
        }
        VBox box = new VBox(10, grid);
        if (!g.elsewhere().isEmpty()) {
            Label l = new Label("Unaligned, or an alignment the grid cannot show:");
            l.setStyle("-fx-text-fill: -t-text2;");
            VBox other = new VBox(6, l, chips(store, g.elsewhere(), openItem));
            other.setPadding(new Insets(0, 12, 12, 12));
            box.getChildren().add(other);
        }
        return box;
    }

    private static Label heading(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold; -fx-text-fill: -t-heading;");
        l.setAlignment(Pos.CENTER);
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    // ---------------------------------------------------------------- domains

    private static Node domains(ProjectStore store, List<Card> gods, Consumer<String> openItem) {
        VBox box = new VBox(6);
        box.setPadding(new Insets(12));
        List<String> unclaimed = Pantheon.unclaimed(gods);
        Label note = new Label(unclaimed.isEmpty() ? "Every standard domain has a god."
                : unclaimed.size() + " of the standard domains have no god yet: " + String.join(", ", unclaimed) + ".");
        note.setWrapText(true);
        note.setStyle("-fx-text-fill: -t-text2;");
        box.getChildren().add(note);
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(6);
        ColumnConstraints a = new ColumnConstraints();
        a.setMinWidth(110);
        ColumnConstraints b = new ColumnConstraints();
        b.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(a, b);
        int row = 0;
        for (Map.Entry<String, List<Card>> e : Pantheon.byDomain(gods).entrySet()) {
            Label d = new Label(e.getKey());
            d.setStyle("-fx-font-weight: bold;" + (e.getValue().isEmpty() ? " -fx-text-fill: -t-text3;" : ""));
            grid.add(d, 0, row);
            if (e.getValue().isEmpty()) {
                Label none = new Label("nobody yet");
                none.setStyle("-fx-text-fill: -t-text3; -fx-font-style: italic;");
                grid.add(none, 1, row);
            } else {
                grid.add(chips(store, e.getValue(), openItem), 1, row);
            }
            row++;
        }
        box.getChildren().add(grid);
        return box;
    }

    // ---------------------------------------------------------------- ranks and organisations

    private static Node grouped(ProjectStore store, Map<String, List<Card>> groups, Consumer<String> openItem) {
        VBox box = new VBox(12);
        box.setPadding(new Insets(12));
        for (Map.Entry<String, List<Card>> e : groups.entrySet()) {
            Label title = new Label(e.getKey() + "  (" + e.getValue().size() + ")");
            title.setStyle("-fx-font-weight: bold; -fx-text-fill: -t-heading;");
            box.getChildren().add(new VBox(5, title, chips(store, e.getValue(), openItem)));
        }
        return box;
    }
}
