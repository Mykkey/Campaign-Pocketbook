package dnd.ui;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** The tool palette plus colour / size / style controls. */
public class ToolbarPanel extends FlowPane {

    public ToolbarPanel(ToolState ts, Supplier<BoardView> current) {
        setHgap(6);
        setVgap(4);
        setPadding(new Insets(6));

        // --- tools
        ToggleGroup group = new ToggleGroup();
        Map<Tool, ToggleButton> buttons = new EnumMap<>(Tool.class);
        for (Tool t : Tool.values()) {
            ToggleButton b = new ToggleButton((t.ordinal() + 1) % 10 + " " + t.label);
            b.setToggleGroup(group);
            b.setFocusTraversable(false);
            b.setTooltip(new Tooltip(t.tip + " [" + (t.ordinal() + 1) % 10 + "]"));
            b.setOnAction(e -> {
                ts.tool.set(t);
                b.setSelected(true); // clicking the active tool must not deselect it
            });
            buttons.put(t, b);
            getChildren().add(b);
        }
        buttons.get(ts.tool.get()).setSelected(true);
        ts.tool.addListener((o, a, n) -> buttons.get(n).setSelected(true));

        getChildren().add(action("Image...", "Add a picture (you can also drag files in, or paste with Ctrl+V)",
                v -> v.addImageFromChooser(), current));
        getChildren().add(new Separator(Orientation.VERTICAL));

        // --- style
        ColorPicker color = new ColorPicker(ts.color.get());
        color.setTooltip(new Tooltip("Text / outline colour (applies to the selection too)"));
        color.setOnAction(e -> ts.color.set(color.getValue()));
        ColorPicker fill = new ColorPicker(ts.fill.get());
        fill.setTooltip(new Tooltip("Sticky note colour / shape fill"));
        fill.setOnAction(e -> ts.fill.set(fill.getValue()));
        CheckBox useFill = new CheckBox("Fill shapes");
        useFill.setFocusTraversable(false);
        useFill.selectedProperty().bindBidirectional(ts.useFill);

        Spinner<Integer> width = new Spinner<>(1, 60, ts.width.get());
        width.setPrefWidth(70);
        width.setTooltip(new Tooltip("Line / pen width"));
        width.valueProperty().addListener((o, a, n) -> {
            if (n != null) ts.width.set(n);
        });
        Spinner<Integer> font = new Spinner<>(8, 200, ts.fontSize.get());
        font.setPrefWidth(75);
        font.setTooltip(new Tooltip("Font size"));
        font.valueProperty().addListener((o, a, n) -> {
            if (n != null) ts.fontSize.set(n);
        });
        ToggleButton bold = new ToggleButton("B");
        bold.setStyle("-fx-font-weight: bold;");
        bold.setFocusTraversable(false);
        bold.selectedProperty().bindBidirectional(ts.bold);
        ToggleButton italic = new ToggleButton("I");
        italic.setStyle("-fx-font-style: italic;");
        italic.setFocusTraversable(false);
        italic.selectedProperty().bindBidirectional(ts.italic);

        getChildren().addAll(new Label("Colour"), color, new Label("Fill"), fill, useFill,
                new Label("Width"), width, new Label("Font"), font, bold, italic,
                new Separator(Orientation.VERTICAL));

        // --- actions
        getChildren().addAll(
                action("Link...", "Link the selected object(s) to another whiteboard (Ctrl+L)", v -> v.linkSelected(), current),
                action("Unlink", "Remove the link from the selected object(s)", v -> v.unlinkSelected(), current),
                action("Undo", "Ctrl+Z", v -> v.undo(), current),
                action("Redo", "Ctrl+Y", v -> v.redo(), current),
                action("Delete", "Delete the selection", v -> v.deleteSelected(), current));
    }

    private static Button action(String text, String tip, Consumer<BoardView> run, Supplier<BoardView> current) {
        Button b = new Button(text);
        b.setFocusTraversable(false);
        b.setTooltip(new Tooltip(tip));
        b.setOnAction(e -> {
            BoardView v = current.get();
            if (v != null) {
                run.accept(v);
                v.requestFocus();
            }
        });
        return b;
    }
}
