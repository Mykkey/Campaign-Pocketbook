package dnd.ui;

import dnd.map.world.WorldGenerator;
import dnd.map.world.WorldGenerator.Climate;
import dnd.map.world.WorldGenerator.Options;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import java.util.Random;

/** The controls for the random world generator: a seed, how much land, how rugged, the climate, and what to add. */
final class WorldGeneratorPane extends GridPane {
    private final TextField seed = new TextField();
    private final Spinner<Integer> land = new Spinner<>(15, 80, 38, 5);
    private final Slider mountains = new Slider(0, 100, 50);
    private final ComboBox<Climate> climate = new ComboBox<>(FXCollections.observableArrayList(Climate.values()));
    private final CheckBox rivers = new CheckBox("Rivers"), symbols = new CheckBox("Mountains, forests and towns"), names = new CheckBox("Place names");

    WorldGeneratorPane() {
        setHgap(10);
        setVgap(8);
        setPadding(new Insets(4));
        seed.setText(String.valueOf(new Random().nextInt(900000) + 100000));
        seed.setPrefWidth(110);
        seed.setTooltip(new Tooltip("The same seed and settings always make the same world. Press New seed to try another."));
        Button reroll = new Button("New seed");
        reroll.setOnAction(e -> seed.setText(String.valueOf(new Random().nextInt(900000) + 100000)));
        land.setEditable(true);
        land.setPrefWidth(80);
        land.setTooltip(new Tooltip("Roughly how much of the map is land, in percent"));
        mountains.setPrefWidth(170);
        mountains.setTooltip(new Tooltip("From rolling hills (left) to rugged mountain ranges (right)"));
        climate.setValue(Climate.TEMPERATE);
        rivers.setSelected(true);
        symbols.setSelected(true);
        names.setSelected(true);
        addRow(0, new Label("Seed"), new HBox(6, seed, reroll));
        addRow(1, new Label("Land (%)"), land);
        addRow(2, new Label("Mountains"), mountains);
        addRow(3, new Label("Climate"), climate);
        add(rivers, 1, 4);
        add(symbols, 1, 5);
        add(names, 1, 6);
    }

    /** The settings as they are now. */
    Options options() {
        long s;
        String text = seed.getText() == null ? "" : seed.getText().trim();
        try {
            s = Long.parseLong(text);
        } catch (NumberFormatException e) {
            s = text.hashCode(); // any words work as a seed
        }
        int pct = land.getValue() == null ? 38 : land.getValue();
        return new Options(s, pct / 100.0, mountains.getValue() / 100.0, climate.getValue() == null ? Climate.TEMPERATE : climate.getValue(),
                rivers.isSelected(), symbols.isSelected(), names.isSelected());
    }

    static WorldGenerator.Options defaults() {
        return Options.defaults(new Random().nextInt(900000) + 100000);
    }
}
