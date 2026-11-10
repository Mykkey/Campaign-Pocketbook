package dnd.ui;

import dnd.keys.Shortcuts;
import dnd.keys.Shortcuts.Action;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Shortcuts tab of the settings: every action with its keys. Click a key and press the keys you want instead; click + to
 * give an action a second key; x takes a key away. If the key is already used for something that can happen at the same time,
 * you are asked whether to take it (the other action is then left without a key until you give it one).
 */
final class ShortcutsPane extends VBox {
    private final VBox rows = new VBox(2);
    private final TextField search = new TextField();
    private final Label message = new Label();

    /** The key button being changed right now, or null; the next key press goes to it. */
    private Button capturing;
    private Runnable finishCapture;

    ShortcutsPane() {
        super(8);
        search.setPromptText("Search the shortcuts (undo, rotate, tool...)");
        search.textProperty().addListener((o, a, n) -> rebuild());
        Label intro = new Label("Click a key, then press the keys you want instead (Esc cancels). Changes work at once; the hints on buttons show "
                + "the new keys the next time you open a tab. The same key can be used for different things in different editors.");
        intro.setWrapText(true);
        intro.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 11px;");
        message.setWrapText(true);
        Button resetAll = new Button("Reset all shortcuts to the defaults");
        resetAll.setOnAction(e -> {
            Shortcuts.resetAll();
            rebuild();
        });
        HBox top = new HBox(10, search, resetAll);
        HBox.setHgrow(search, Priority.ALWAYS);
        getChildren().addAll(intro, top, message, rows);
        rebuild();
        sceneProperty().addListener((o, was, scene) -> {
            if (scene != null) scene.addEventFilter(KeyEvent.KEY_PRESSED, this::captureKey);
        });
    }

    /** What Reset on this tab does. */
    static void resetAll() {
        Shortcuts.resetAll();
    }

    private void rebuild() {
        rows.getChildren().clear();
        String q = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        List<Action> unassigned = Shortcuts.unassigned();
        if (unassigned.isEmpty()) {
            message.setText("");
        } else {
            StringBuilder sb = new StringBuilder("Without a key now: ");
            for (int i = 0; i < unassigned.size(); i++) sb.append(i > 0 ? ", " : "").append(unassigned.get(i).label());
            message.setText(sb.toString());
            message.setStyle("-fx-text-fill: -t-danger;");
        }
        for (String category : Shortcuts.categories()) {
            List<Action> actions = new ArrayList<>();
            for (Action a : Shortcuts.all()) if (a.category().equals(category) && matches(a, q)) actions.add(a);
            if (actions.isEmpty()) continue;
            Label h = new Label(category.toUpperCase());
            h.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: -t-heading; -fx-padding: 10 0 2 0;");
            rows.getChildren().add(h);
            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(3);
            javafx.scene.layout.ColumnConstraints c0 = new javafx.scene.layout.ColumnConstraints(190), c1 = new javafx.scene.layout.ColumnConstraints(95),
                    c2 = new javafx.scene.layout.ColumnConstraints(), c3 = new javafx.scene.layout.ColumnConstraints();
            c2.setMinWidth(270);
            c3.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            grid.getColumnConstraints().addAll(c0, c1, c2, c3);
            int r = 0;
            for (Action a : actions) {
                Label name = new Label(a.label());
                name.setMinWidth(190);
                name.setMaxWidth(190);
                Label where = new Label(a.context().label);
                where.setMinWidth(95);
                where.setMaxWidth(95);
                where.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 11px;");
                grid.addRow(r++, name, where, keysOf(a), resetButton(a));
            }
            rows.getChildren().add(grid);
        }
        if (rows.getChildren().isEmpty()) rows.getChildren().add(new Label("No shortcut matches that."));
    }

    private static boolean matches(Action a, String q) {
        if (q.isEmpty()) return true;
        return a.label().toLowerCase(Locale.ROOT).contains(q) || a.category().toLowerCase(Locale.ROOT).contains(q)
                || Shortcuts.label(a.id()).toLowerCase(Locale.ROOT).contains(q) || a.context().label.toLowerCase(Locale.ROOT).contains(q);
    }

    private Node keysOf(Action a) {
        HBox box = new HBox(4);
        box.setAlignment(Pos.CENTER_LEFT);
        List<KeyCombination> keys = Shortcuts.bindings(a.id());
        if (keys.isEmpty()) {
            Label none = new Label("no key");
            none.setStyle("-fx-text-fill: -t-danger; -fx-font-style: italic;");
            box.getChildren().add(none);
        }
        for (KeyCombination k : keys) {
            Button key = new Button(k.getDisplayText());
            key.setMinWidth(70);
            key.setTooltip(IconButtons.tip("Click, then press the new keys"));
            key.setOnAction(e -> startCapture(key, a, k));
            Button remove = new Button("x");
            remove.setTooltip(IconButtons.tip("Take this key away from " + a.label()));
            remove.setOnAction(e -> {
                List<KeyCombination> rest = new ArrayList<>(Shortcuts.bindings(a.id()));
                rest.remove(k);
                Shortcuts.set(a.id(), rest);
                rebuild();
            });
            box.getChildren().addAll(key, remove);
        }
        Button add = new Button("+");
        add.setTooltip(IconButtons.tip("Add another key for " + a.label()));
        add.setOnAction(e -> startCapture(add, a, null));
        box.getChildren().add(add);
        return box;
    }

    private Node resetButton(Action a) {
        Button b = new Button("Reset");
        b.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        b.setDisable(Shortcuts.isDefault(a.id()));
        b.setOnAction(e -> {
            Shortcuts.reset(a.id());
            rebuild();
        });
        return b;
    }

    // ---------------------------------------------------------------- capturing a key

    private Action target;
    private KeyCombination replacing;

    private void startCapture(Button button, Action action, KeyCombination old) {
        if (finishCapture != null) finishCapture.run();
        capturing = button;
        target = action;
        replacing = old;
        String before = button.getText();
        button.setText("Press keys...");
        button.setStyle("-fx-border-color: -t-blue-border; -fx-border-width: 2;");
        finishCapture = () -> {
            button.setText(before);
            button.setStyle("");
            capturing = null;
            target = null;
            replacing = null;
            finishCapture = null;
        };
    }

    private void captureKey(KeyEvent e) {
        if (capturing == null) return;
        e.consume();
        if (e.getCode() == KeyCode.ESCAPE) {
            finishCapture.run();
            return;
        }
        KeyCombination key = Shortcuts.fromEvent(e);
        if (key == null) return; // only Ctrl, Shift or Alt so far: wait for the real key
        Action action = target;
        KeyCombination old = replacing;
        finishCapture.run();
        assign(action, old, key);
    }

    /** Gives the action the key (in place of {@code old}, or as an extra one), asking first if another action already has it. */
    private void assign(Action action, KeyCombination old, KeyCombination key) {
        List<Action> clash = Shortcuts.conflicts(action.id(), key);
        if (!clash.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < clash.size(); i++) names.append(i > 0 ? ", " : "").append(clash.get(i).label());
            ButtonType take = new ButtonType("Take it", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
            Alert a = new Alert(Alert.AlertType.CONFIRMATION, key.getDisplayText() + " is already used for: " + names + ".\n\nIf you take it, "
                    + (clash.size() == 1 ? "that action has" : "those actions have") + " no key until you give " + (clash.size() == 1 ? "it" : "them") + " one.",
                    take, ButtonType.CANCEL);
            a.setHeaderText("Use " + key.getDisplayText() + " for " + action.label() + "?");
            if (getScene() != null) a.initOwner(getScene().getWindow());
            if (a.showAndWait().orElse(ButtonType.CANCEL) != take) return;
        }
        List<KeyCombination> mine = new ArrayList<>(Shortcuts.bindings(action.id()));
        if (old != null) mine.remove(old);
        if (!mine.contains(key)) mine.add(key);
        Shortcuts.set(action.id(), mine);
        for (Action loser : clash) {
            List<KeyCombination> rest = new ArrayList<>(Shortcuts.bindings(loser.id()));
            rest.removeIf(k -> k.equals(key));
            Shortcuts.set(loser.id(), rest);
        }
        rebuild();
    }
}
