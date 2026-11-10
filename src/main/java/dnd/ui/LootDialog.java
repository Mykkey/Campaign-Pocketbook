package dnd.ui;

import dnd.loot.LootGenerator;
import dnd.loot.LootGenerator.Kind;
import dnd.loot.LootGenerator.Loot;
import dnd.loot.LootGenerator.Tier;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.Random;
import java.util.function.Consumer;

/** Small non-modal window for rolling random treasure. */
public class LootDialog {
    // remembered for the rest of the session
    private static Tier lastTier = Tier.TIER1;
    private static Kind lastKind = Kind.HOARD;
    private static int lastCount = 4;

    private static Stage open;

    private LootDialog() { }

    /** Shows the generator (or brings it to the front). {@code addToBoard} receives the loot text. */
    public static void show(Window owner, Consumer<String> addToBoard) {
        if (open != null) {
            open.toFront();
            open.requestFocus();
            return;
        }
        Random random = new Random();

        ComboBox<Tier> tier = new ComboBox<>(FXCollections.observableArrayList(Tier.values()));
        tier.setValue(lastTier);
        tier.setMaxWidth(Double.MAX_VALUE);
        tier.setTooltip(IconButtons.tip("The challenge rating of what the party defeated, or how rich the place is"));
        ComboBox<Kind> kind = new ComboBox<>(FXCollections.observableArrayList(Kind.values()));
        kind.setValue(lastKind);
        kind.setMaxWidth(Double.MAX_VALUE);
        Spinner<Integer> count = new Spinner<>(1, 50, lastCount);
        count.setPrefWidth(80);
        count.setEditable(true);
        Label countLabel = new Label("Creatures");

        TextArea result = new TextArea();
        result.setEditable(false);
        result.setWrapText(true);
        result.setPromptText("Press Roll");
        result.setPrefRowCount(12);

        Button roll = new Button("Roll");
        roll.setDefaultButton(true);
        Button copy = new Button("Copy");
        Button add = new Button("Add to board");
        add.setDisable(addToBoard == null);

        Runnable go = () -> {
            lastTier = tier.getValue();
            lastKind = kind.getValue();
            int n = 1;
            try {
                n = Integer.parseInt(count.getEditor().getText().trim());
            } catch (NumberFormatException e) {
                n = count.getValue() == null ? 1 : count.getValue();
            }
            lastCount = Math.max(1, Math.min(50, n));
            Loot loot = LootGenerator.generate(lastTier, lastKind, lastCount, random);
            String title = lastKind == Kind.HOARD ? "Treasure hoard, " + lastTier.label
                    : "Pockets of " + lastCount + (lastCount == 1 ? " creature" : " creatures") + ", " + lastTier.label;
            result.setText(title + "\n\n" + loot.toText());
        };
        Runnable syncCount = () -> {
            boolean ind = kind.getValue() == Kind.INDIVIDUAL;
            count.setDisable(!ind);
            countLabel.setDisable(!ind);
        };
        syncCount.run();
        roll.setOnAction(e -> go.run());
        tier.setOnAction(e -> go.run());
        kind.setOnAction(e -> {
            syncCount.run();
            go.run();
        });
        copy.setOnAction(e -> {
            ClipboardContent c = new ClipboardContent();
            c.putString(result.getText());
            Clipboard.getSystemClipboard().setContent(c);
        });
        add.setOnAction(e -> {
            if (addToBoard != null && !result.getText().isBlank()) addToBoard.accept(result.getText());
        });

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        ColumnConstraints c0 = new ColumnConstraints();
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().addAll(c0, c1);
        form.addRow(0, new Label("Tier"), tier);
        form.addRow(1, new Label("Treasure"), kind);
        form.addRow(2, countLabel, count);

        HBox buttons = new HBox(8, roll, copy, add);
        buttons.setAlignment(Pos.CENTER_LEFT);
        Label hint = new Label("Amounts are rough guidance, not a published table. Change anything you like.");
        hint.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 0.917em;");
        hint.setWrapText(true);

        VBox root = new VBox(10, form, buttons, result, hint);
        root.setPadding(new Insets(14));
        VBox.setVgrow(result, Priority.ALWAYS);

        Stage stage = new Stage();
        stage.setTitle("Loot Generator");
        if (owner != null) stage.initOwner(owner);
        Scene scene = new Scene(root, UiScale.px(420), UiScale.px(520));
        scene.setOnKeyPressed(e -> {
            if (dnd.keys.Shortcuts.matches(e, "dialog.close")) stage.close();
        });
        stage.setScene(scene);
        stage.setOnHidden(e -> open = null);
        open = stage;
        stage.show();
        go.run();
    }
}
