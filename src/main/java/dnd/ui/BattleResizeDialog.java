package dnd.ui;

import dnd.map.BattleResize;
import dnd.model.BattleMap;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.function.Consumer;

/** Changes the size of a battlemap after it was made: add or cut rows and columns on any side, and change the size of the cells. */
final class BattleResizeDialog {
    private BattleResizeDialog() { }

    /** @param apply runs the change as one undoable step (the dialog hands it the change to make) */
    static void show(Window owner, BattleMap map, Consumer<Runnable> apply) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Map size");
        d.setHeaderText("\"" + map.name + "\" is now " + map.cols + " columns x " + map.rows + " rows");
        if (owner != null) d.initOwner(owner);

        boolean hex = map.gridType == BattleMap.GridType.HEX;
        Spinner<Integer> left = margin(1), right = margin(1), top = margin(hex ? 2 : 1), bottom = margin(1);
        GridPane margins = new GridPane();
        margins.setHgap(8);
        margins.setVgap(6);
        margins.addRow(0, new Label("Left"), left, new Label("Right"), right);
        margins.addRow(1, new Label("Top"), top, new Label("Bottom"), bottom);
        Label note = new Label("Columns and rows to add on each side. A negative number cuts that many off. Everything keeps its place on the map;"
                + " pieces, pins and lights that end up outside are removed, and tokens are never removed."
                + (hex ? " On a hex map, rows are added to or cut from the top two at a time." : ""));
        note.setWrapText(true);
        note.setMaxWidth(440);

        Spinner<Integer> cell = new Spinner<>((int) BattleResize.MIN_CELL_SIZE, (int) BattleResize.MAX_CELL_SIZE, (int) Math.round(map.cellSize), 5);
        cell.setEditable(true);
        cell.setPrefWidth(90);
        Label cellNote = new Label("The size of one cell on screen. Pieces, pins and lights grow or shrink with it, so the map looks the same, only bigger or smaller.");
        cellNote.setWrapText(true);
        cellNote.setMaxWidth(440);

        Label result = new Label();
        result.setWrapText(true);
        result.setMaxWidth(440);
        VBox box = new VBox(10, margins, note, new Label("Cell size"), cell, cellNote, result);
        box.setPadding(new Insets(10));
        d.getDialogPane().setContent(box);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        javafx.scene.Node ok = d.getDialogPane().lookupButton(ButtonType.OK);

        Runnable refresh = () -> {
            BattleResize.Margins g = margins(left, right, top, bottom);
            String problem = BattleResize.problem(map, g);
            boolean same = g.isNone() && cellOf(cell) == Math.round(map.cellSize);
            result.setText(problem != null ? problem
                    : "New size: " + BattleResize.newCols(map, g) + " columns x " + BattleResize.newRows(map, g) + " rows, cells " + cellOf(cell) + " px."
                    + (same ? " (no change)" : " This can be undone."));
            result.setStyle(problem != null ? "-fx-text-fill: -t-danger;" : "-fx-text-fill: -t-text2;");
            ok.setDisable(problem != null || same);
        };
        for (Spinner<Integer> s : java.util.List.of(left, right, top, bottom, cell)) {
            s.valueProperty().addListener((o, a, b) -> refresh.run());
            s.getEditor().textProperty().addListener((o, a, b) -> {
                try {
                    s.getValueFactory().setValue(Integer.parseInt(b.trim()));
                } catch (NumberFormatException ignored) {
                    // half-typed: wait for the rest
                }
            });
        }
        refresh.run();

        if (d.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        BattleResize.Margins g = margins(left, right, top, bottom);
        if (BattleResize.problem(map, g) != null) return;
        long size = cellOf(cell);
        apply.accept(() -> {
            BattleResize.resizeCanvas(map, g);
            if (size != Math.round(map.cellSize)) BattleResize.setCellSize(map, size);
        });
    }

    private static Spinner<Integer> margin(int step) {
        Spinner<Integer> s = new Spinner<>(-BattleResize.MAX_CELLS, BattleResize.MAX_CELLS, 0, step);
        s.setEditable(true);
        s.setPrefWidth(90);
        return s;
    }

    private static long cellOf(Spinner<Integer> cell) {
        Integer v = cell.getValue();
        return v == null ? 0 : v;
    }

    private static int val(Spinner<Integer> s) {
        return s.getValue() == null ? 0 : s.getValue();
    }

    private static BattleResize.Margins margins(Spinner<Integer> l, Spinner<Integer> r, Spinner<Integer> t, Spinner<Integer> b) {
        return new BattleResize.Margins(val(l), val(r), val(t), val(b));
    }
}
