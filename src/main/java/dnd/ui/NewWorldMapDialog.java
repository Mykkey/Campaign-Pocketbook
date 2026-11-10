package dnd.ui;

import dnd.model.WorldMap;
import dnd.model.WorldStyle;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

import java.util.Optional;

/** Asks for a new world map's name, size, scale and art style. */
final class NewWorldMapDialog {
    private NewWorldMapDialog() { }

    private static final String[] SIZES = {"Small (512 x 384 cells)", "Medium (1024 x 768 cells)", "Large (2048 x 1536 cells)"};

    /** The new map, and the settings for generating a random world on it (null to start with a blank map). */
    record Result(WorldMap map, dnd.map.world.WorldGenerator.Options generate) { }

    static Optional<Result> show(Window owner) {
        TextField name = new TextField("New World");
        ComboBox<String> size = new ComboBox<>(FXCollections.observableArrayList(SIZES));
        size.setValue(SIZES[1]);
        Spinner<Integer> miles = new Spinner<>(50, 100000, 2000, 100);
        miles.setEditable(true);
        miles.setPrefWidth(120);
        ComboBox<WorldStyle> style = new ComboBox<>(FXCollections.observableArrayList(WorldStyle.values()));
        style.setValue(WorldStyle.FANTASY_COLOUR);
        Label styleNote = new Label(WorldStyle.FANTASY_COLOUR.description);
        styleNote.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 11px;");
        styleNote.setWrapText(true);
        style.valueProperty().addListener((o, a, n) -> styleNote.setText(n == null ? "" : n.description));
        Label hint = new Label("The scale sets how far the map is across, for the scale bar and travel times. You can change the art style later.");
        hint.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 11px;");
        hint.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(8));
        grid.addRow(0, new Label("Name"), name);
        grid.addRow(1, new Label("Size"), size);
        grid.addRow(2, new Label("Miles across"), miles);
        grid.addRow(3, new Label("Art style"), style);
        grid.add(styleNote, 1, 4);
        grid.add(hint, 0, 5, 2, 1);
        CheckBox generate = new CheckBox("Generate a random world");
        generate.setTooltip(new Tooltip("Start with continents, mountains, rivers, towns and names already made, then change anything you like"));
        WorldGeneratorPane gen = new WorldGeneratorPane();
        gen.visibleProperty().bind(generate.selectedProperty());
        gen.managedProperty().bind(generate.selectedProperty());
        generate.selectedProperty().addListener((o, a, n) -> {
            if (grid.getScene() != null && grid.getScene().getWindow() != null) grid.getScene().getWindow().sizeToScene();
        });
        grid.add(generate, 0, 6, 2, 1);
        grid.add(gen, 0, 7, 2, 1);
        grid.setPrefWidth(430);

        Dialog<Result> d = new Dialog<>();
        d.setTitle("New world map");
        d.setHeaderText("Create a world map");
        if (owner != null) d.initOwner(owner);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.getDialogPane().setContent(grid);
        d.setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            String n = name.getText() == null || name.getText().isBlank() ? "New World" : name.getText().trim();
            int s = size.getItems().indexOf(size.getValue());
            int cols = s == 0 ? WorldMap.SMALL_COLS : s == 2 ? WorldMap.LARGE_COLS : WorldMap.MEDIUM_COLS;
            int rows = s == 0 ? WorldMap.SMALL_ROWS : s == 2 ? WorldMap.LARGE_ROWS : WorldMap.MEDIUM_ROWS;
            double across = miles.getValue() == null ? 2000 : miles.getValue();
            WorldMap map = new WorldMap(n, cols, rows, across, style.getValue() == null ? WorldStyle.FANTASY_COLOUR : style.getValue());
            return new Result(map, generate.isSelected() ? gen.options() : null);
        });
        return d.showAndWait();
    }
}
