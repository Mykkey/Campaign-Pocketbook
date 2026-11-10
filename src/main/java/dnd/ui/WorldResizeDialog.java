package dnd.ui;

import dnd.map.world.WorldResize;
import dnd.model.WorldMap;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.function.Consumer;

/** Changes the size of a world map after it was made: scale the whole map, or crop and extend its canvas, and set how many miles it is across. */
final class WorldResizeDialog {
    private WorldResizeDialog() { }

    /** @param apply runs the change as one undoable step (the dialog hands it the change to make) */
    static void show(Window owner, WorldMap map, Consumer<Runnable> apply) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Map size");
        d.setHeaderText("\"" + map.name + "\" is now " + map.cols + " x " + map.rows + " cells, " + fmt(map.milesAcross()) + " miles across");
        if (owner != null) d.initOwner(owner);

        ToggleGroup mode = new ToggleGroup();
        RadioButton scaleMode = new RadioButton("Scale the whole map");
        RadioButton canvasMode = new RadioButton("Crop or extend the canvas");
        scaleMode.setToggleGroup(mode);
        canvasMode.setToggleGroup(mode);
        scaleMode.setSelected(true);

        // ---- scale: one width, the height follows
        Spinner<Integer> width = new Spinner<>(WorldResize.MIN_COLS, WorldResize.MAX_COLS, map.cols, 64);
        width.setEditable(true);
        width.setPrefWidth(100);
        Button small = new Button("Small"), medium = new Button("Medium"), large = new Button("Large");
        small.setOnAction(e -> width.getValueFactory().setValue(WorldMap.SMALL_COLS));
        medium.setOnAction(e -> width.getValueFactory().setValue(WorldMap.MEDIUM_COLS));
        large.setOnAction(e -> width.getValueFactory().setValue(WorldMap.LARGE_COLS));
        Label scaleNote = new Label("Everything on the map (painting, symbols, lines, regions, the legend) shrinks or grows with it. Scaling down is the way to fix a map that is too big.");
        scaleNote.setWrapText(true);
        scaleNote.setMaxWidth(420);
        VBox scaleBox = new VBox(6, new HBox(6, new Label("New width in cells"), width, small, medium, large), scaleNote);

        // ---- canvas: margins to add (or, if negative, to cut off)
        Spinner<Integer> left = margin(), right = margin(), top = margin(), bottom = margin();
        GridPane margins = new GridPane();
        margins.setHgap(8);
        margins.setVgap(6);
        margins.addRow(0, new Label("Left"), left, new Label("Right"), right);
        margins.addRow(1, new Label("Top"), top, new Label("Bottom"), bottom);
        Label canvasNote = new Label("Cells to add on each side (a negative number cuts that much off). Nothing changes size; symbols that end up outside the map are removed.");
        canvasNote.setWrapText(true);
        canvasNote.setMaxWidth(420);
        VBox canvasBox = new VBox(6, margins, canvasNote);

        scaleBox.visibleProperty().bind(scaleMode.selectedProperty());
        scaleBox.managedProperty().bind(scaleBox.visibleProperty());
        canvasBox.visibleProperty().bind(canvasMode.selectedProperty());
        canvasBox.managedProperty().bind(canvasBox.visibleProperty());

        // ---- the scale in miles, and a summary of the result
        Spinner<Double> miles = new Spinner<>(1.0, 10_000_000.0, map.milesAcross(), 100.0);
        miles.setEditable(true);
        miles.setPrefWidth(120);
        Label result = new Label();
        result.setWrapText(true);
        result.setMaxWidth(420);
        boolean[] touched = {false}, setting = {false};
        Runnable[] refresh = new Runnable[1];

        refresh[0] = () -> {
            try {
                int cols, rows;
                String extra = "";
                if (scaleMode.isSelected()) {
                    cols = width.getValue();
                    rows = WorldResize.scaledRows(map, cols);
                } else {
                    cols = map.cols + left.getValue() + right.getValue();
                    rows = map.rows + top.getValue() + bottom.getValue();
                    int lost = WorldResize.symbolsLost(map, left.getValue(), top.getValue(), cols, rows);
                    if (lost > 0) extra = "  " + lost + " symbol" + (lost == 1 ? "" : "s") + " will be removed.";
                }
                boolean ok = cols >= WorldResize.MIN_COLS && cols <= WorldResize.MAX_COLS && rows >= WorldResize.MIN_ROWS && rows <= WorldResize.MAX_ROWS;
                if (!touched[0]) {
                    setting[0] = true;
                    miles.getValueFactory().setValue(scaleMode.isSelected() ? map.milesAcross() : Math.max(1, cols * map.milesPerCell));
                    setting[0] = false;
                }
                double perCell = miles.getValue() / Math.max(1, cols);
                result.setText(ok ? "New size: " + cols + " x " + rows + " cells, " + fmt(miles.getValue()) + " miles across (" + fmt(perCell) + " miles a cell)." + extra
                        : "The map must be between " + WorldResize.MIN_COLS + " x " + WorldResize.MIN_ROWS + " and " + WorldResize.MAX_COLS + " x " + WorldResize.MAX_ROWS + " cells (now " + cols + " x " + rows + ").");
                d.getDialogPane().lookupButton(ButtonType.OK).setDisable(!ok);
            } catch (RuntimeException ignored) {
                d.getDialogPane().lookupButton(ButtonType.OK).setDisable(true); // half-typed number
            }
        };
        for (Spinner<Integer> sp : java.util.List.of(width, left, right, top, bottom)) sp.valueProperty().addListener((o, a, n) -> refresh[0].run());
        mode.selectedToggleProperty().addListener((o, a, n) -> {
            touched[0] = false;
            refresh[0].run();
        });
        miles.valueProperty().addListener((o, a, n) -> {
            if (!setting[0]) touched[0] = true;
            refresh[0].run();
        });

        VBox box = new VBox(10, new HBox(16, scaleMode, canvasMode), scaleBox, canvasBox,
                new HBox(6, new Label("Miles across the whole map"), miles), result,
                note("You can undo this with Ctrl+Z. The scale only changes the distances (the ruler, the scale bar, travel times); the picture stays as it is."));
        box.setPadding(new Insets(6));
        box.setPrefWidth(440);
        d.getDialogPane().setContent(box);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        refresh[0].run();
        if (d.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        double milesAcross = miles.getValue();
        if (scaleMode.isSelected()) {
            int cols = width.getValue();
            if (cols == map.cols && Math.abs(milesAcross - map.milesAcross()) < 1e-9) return;
            apply.accept(() -> WorldResize.scale(map, cols, milesAcross / cols));
        } else {
            int l = left.getValue(), t = top.getValue(), cols = map.cols + l + right.getValue(), rows = map.rows + t + bottom.getValue();
            apply.accept(() -> WorldResize.canvas(map, l, t, cols, rows, milesAcross / cols));
        }
    }

    private static Spinner<Integer> margin() {
        Spinner<Integer> s = new Spinner<>(-WorldResize.MAX_COLS, WorldResize.MAX_COLS, 0, 16);
        s.setEditable(true);
        s.setPrefWidth(90);
        return s;
    }

    private static Label note(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setMaxWidth(420);
        l.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 0.917em;");
        return l;
    }

    private static String fmt(double v) {
        return v >= 100 ? String.format("%,.0f", v) : String.format("%,.2f", v).replaceAll("\\.?0+$", "");
    }
}
