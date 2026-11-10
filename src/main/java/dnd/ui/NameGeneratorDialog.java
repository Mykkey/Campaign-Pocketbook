package dnd.ui;

import dnd.names.NameData;
import dnd.names.NameData.Race;
import dnd.names.NameGenerator;
import dnd.names.NameGenerator.Gender;
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

import java.util.List;
import java.util.function.Consumer;

/** Small non-modal window for rolling random character names. */
public class NameGeneratorDialog {
    // remembered for the rest of the session
    private static Race lastRace = Race.HUMAN;
    private static Gender lastGender = Gender.ANY;
    private static int lastCount = 5;

    private static Stage open;

    private NameGeneratorDialog() { }

    /** Shows the generator (or brings it to the front). {@code addToBoard} receives a chosen name. */
    public static void show(Window owner, Consumer<String> addToBoard) {
        if (open != null) {
            open.toFront();
            open.requestFocus();
            return;
        }
        NameGenerator gen = new NameGenerator();

        ComboBox<Race> race = new ComboBox<>(FXCollections.observableArrayList(NameData.races()));
        race.setValue(lastRace);
        race.setMaxWidth(Double.MAX_VALUE);
        ComboBox<Gender> gender = new ComboBox<>(FXCollections.observableArrayList(Gender.values()));
        gender.setValue(lastGender);
        gender.setMaxWidth(Double.MAX_VALUE);
        Spinner<Integer> count = new Spinner<>(1, 10, lastCount);
        count.setPrefWidth(80);
        count.setEditable(true);

        ListView<String> results = new ListView<>();
        results.setPlaceholder(new Label("Press Generate"));
        results.setPrefHeight(260);

        Button generate = new Button("Generate");
        generate.setDefaultButton(true);
        Button copy = new Button("Copy");
        Button add = new Button("Add to board");
        copy.setDisable(true);
        add.setDisable(true);
        results.getSelectionModel().selectedItemProperty().addListener((o, a, n) -> {
            copy.setDisable(n == null);
            add.setDisable(n == null || addToBoard == null);
        });

        Runnable roll = () -> {
            lastRace = race.getValue();
            lastGender = gender.getValue();
            lastCount = count.getValue() == null ? 5 : count.getValue();
            List<String> names = gen.generate(lastRace, lastGender, lastCount);
            results.setItems(FXCollections.observableArrayList(names));
            if (!names.isEmpty()) results.getSelectionModel().select(0);
        };
        generate.setOnAction(e -> roll.run());
        race.setOnAction(e -> {
            gender.setDisable(race.getValue().unisex());
            roll.run();
        });
        gender.setDisable(lastRace.unisex());
        gender.setOnAction(e -> roll.run());

        copy.setOnAction(e -> {
            String s = results.getSelectionModel().getSelectedItem();
            if (s == null) return;
            ClipboardContent c = new ClipboardContent();
            c.putString(s);
            Clipboard.getSystemClipboard().setContent(c);
        });
        add.setOnAction(e -> {
            String s = results.getSelectionModel().getSelectedItem();
            if (s != null && addToBoard != null) addToBoard.accept(s);
        });
        results.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) add.fire();
        });

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        ColumnConstraints c0 = new ColumnConstraints();
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().addAll(c0, c1);
        form.addRow(0, new Label("Race"), race);
        form.addRow(1, new Label("Gender"), gender);
        form.addRow(2, new Label("How many"), count);

        HBox buttons = new HBox(8, generate, copy, add);
        buttons.setAlignment(Pos.CENTER_LEFT);
        Label hint = new Label("Double-click a name to put it on the current board.");
        hint.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 11px;");

        VBox root = new VBox(10, form, buttons, results, hint);
        root.setPadding(new Insets(14));
        VBox.setVgrow(results, Priority.ALWAYS);

        Stage stage = new Stage();
        stage.setTitle("Name Generator");
        if (owner != null) stage.initOwner(owner);
        Scene scene = new Scene(root, 360, 460);
        scene.setOnKeyPressed(e -> {
            if (dnd.keys.Shortcuts.matches(e, "dialog.close")) stage.close();
        });
        stage.setScene(scene);
        stage.setOnHidden(e -> open = null);
        open = stage;
        stage.show();
        roll.run();
    }
}
