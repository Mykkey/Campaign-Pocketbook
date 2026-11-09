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
        getChildren().add(action("Battlemap...", "Insert a live preview of a battlemap; double-click it to open the map",
                v -> v.insertMapPreview(), current));
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
        // While a text is being typed, B and I style the selected words; otherwise they style the whole block.
        ToggleButton bold = new ToggleButton("B");
        bold.setStyle("-fx-font-weight: bold;");
        bold.setFocusTraversable(false);
        bold.setTooltip(new Tooltip("Bold. While typing: the selected words (Ctrl+B). Otherwise: the whole text or sticky note"));
        bold.setSelected(ts.bold.get());
        ts.bold.addListener((o, a, n) -> bold.setSelected(n));
        bold.setOnAction(e -> {
            BoardView v = current.get();
            if (v != null && v.isEditingText()) {
                bold.setSelected(!bold.isSelected()); // this click styled words, not the whole-block setting
                v.styleSelectedWords(true);
            } else {
                ts.bold.set(bold.isSelected());
            }
        });
        ToggleButton italic = new ToggleButton("I");
        italic.setStyle("-fx-font-style: italic;");
        italic.setFocusTraversable(false);
        italic.setTooltip(new Tooltip("Italic. While typing: the selected words (Ctrl+I). Otherwise: the whole text or sticky note"));
        italic.setSelected(ts.italic.get());
        ts.italic.addListener((o, a, n) -> italic.setSelected(n));
        italic.setOnAction(e -> {
            BoardView v = current.get();
            if (v != null && v.isEditingText()) {
                italic.setSelected(!italic.isSelected());
                v.styleSelectedWords(false);
            } else {
                ts.italic.set(italic.isSelected());
            }
        });

        getChildren().addAll(new Label("Colour"), color, new Label("Fill"), fill, useFill,
                new Label("Width"), width, new Label("Font"), font, bold, italic,
                new Separator(Orientation.VERTICAL));

        // --- actions
        getChildren().addAll(
                action("Link...", "Link the selected words (while typing) or object(s) to another item (Ctrl+L). Blue = link: hover it to preview the item, double-click to open it", v -> v.linkSelected(), current),
                action("Unlink", "Remove the link from the selected words (while typing) or object(s)", v -> v.unlinkSelected(), current),
                action("Note...", "Write your own note that shows when you hover over the selected words (while typing) or object(s). Green = note. Double-click a note to edit it",
                        v -> v.noteSelected(), current),
                action("No note", "Remove the note from the selected words (while typing) or object(s)", v -> v.removeNoteSelected(), current),
                nameHintsBox(ts),
                linkPreviewsBox(ts),
                action("Undo", "Ctrl+Z", v -> v.undo(), current),
                action("Redo", "Ctrl+Y", v -> v.redo(), current),
                action("Delete", "Delete the selection", v -> v.deleteSelected(), current));
    }

    private static CheckBox nameHintsBox(ToolState ts) {
        CheckBox cb = new CheckBox("Name hints");
        cb.setFocusTraversable(false);
        cb.setTooltip(new Tooltip("Underline the names of your cards and spells in text; hover one to link it"));
        cb.selectedProperty().bindBidirectional(ts.nameHints);
        return cb;
    }

    private static CheckBox linkPreviewsBox(ToolState ts) {
        CheckBox cb = new CheckBox("Link previews");
        cb.setFocusTraversable(false);
        cb.setTooltip(new Tooltip("Show a preview of the linked card, spell, map or whiteboard when you hover over a link"));
        cb.selectedProperty().bindBidirectional(ts.linkPreviews);
        return cb;
    }

    private static Button action(String text, String tip, Consumer<BoardView> run, Supplier<BoardView> current) {
        Button b = new Button(text);
        b.setFocusTraversable(false);
        b.setTooltip(new Tooltip(tip));
        b.setOnAction(e -> {
            BoardView v = current.get();
            if (v != null) {
                run.accept(v);
                if (!v.isEditingText()) v.requestFocus(); // keep typing in the text box if that is where we were
            }
        });
        return b;
    }
}
