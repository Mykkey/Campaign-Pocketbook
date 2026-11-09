package dnd.ui;

import dnd.model.BattleMap;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Window;

import java.util.Optional;

/** Asks for the name, grid type and size of a new battlemap. */
public final class NewBattleMapDialog {
    private NewBattleMapDialog() { }

    public record Spec(String name, BattleMap.GridType type, int cols, int rows, double cellSize) { }

    public static Optional<Spec> show(Window owner) {
        Dialog<Spec> d = new Dialog<>();
        d.setTitle("New battlemap");
        d.setHeaderText("Create a battlemap");
        if (owner != null) d.initOwner(owner);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField name = new TextField("New Battlemap");
        ToggleGroup tg = new ToggleGroup();
        RadioButton squares = new RadioButton("Squares");
        RadioButton hexes = new RadioButton("Hexagons");
        squares.setToggleGroup(tg);
        hexes.setToggleGroup(tg);
        squares.setSelected(true);
        Spinner<Integer> cols = new Spinner<>(3, 200, 30);
        Spinner<Integer> rows = new Spinner<>(3, 200, 20);
        Spinner<Integer> cell = new Spinner<>(20, 200, 50);
        for (Spinner<Integer> s : java.util.List.of(cols, rows, cell)) {
            s.setEditable(true);
            s.setPrefWidth(100);
        }
        Label size = new Label();
        Runnable update = () -> size.setText(cols.getValue() + " x " + rows.getValue() + " cells  =  "
                + cols.getValue() * 5 + " x " + rows.getValue() * 5 + " ft");
        cols.valueProperty().addListener((o, a, n) -> update.run());
        rows.valueProperty().addListener((o, a, n) -> update.run());
        update.run();

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.setPadding(new Insets(10));
        ColumnConstraints c0 = new ColumnConstraints();
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().addAll(c0, c1);
        form.addRow(0, new Label("Name"), name);
        form.addRow(1, new Label("Grid"), new HBox(14, squares, hexes));
        form.addRow(2, new Label("Columns"), cols);
        form.addRow(3, new Label("Rows"), rows);
        form.addRow(4, new Label("Cell size (px)"), cell);
        form.add(size, 1, 5);
        d.getDialogPane().setContent(form);

        Node ok = d.getDialogPane().lookupButton(ButtonType.OK);
        name.textProperty().addListener((o, a, n) -> ok.setDisable(n == null || n.isBlank()));

        d.setResultConverter(bt -> {
            if (bt != ButtonType.OK || name.getText().isBlank()) return null;
            cols.increment(0); // commit any text typed into the spinners
            rows.increment(0);
            cell.increment(0);
            return new Spec(name.getText().trim(),
                    hexes.isSelected() ? BattleMap.GridType.HEX : BattleMap.GridType.SQUARE,
                    cols.getValue(), rows.getValue(), cell.getValue());
        });
        javafx.application.Platform.runLater(name::requestFocus);
        return d.showAndWait();
    }
}
