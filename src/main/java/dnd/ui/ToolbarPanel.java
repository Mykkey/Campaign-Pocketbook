package dnd.ui;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** The tool palette plus colour / size / style controls: icon buttons, with the names and shortcuts in tooltips. */
public class ToolbarPanel extends FlowPane {

    public ToolbarPanel(ToolState ts, Supplier<BoardView> current) {
        setHgap(4);
        setVgap(4);
        setPadding(new Insets(6));

        // --- tools
        ToggleGroup group = new ToggleGroup();
        Map<Tool, ToggleButton> buttons = new EnumMap<>(Tool.class);
        for (Tool t : Tool.values()) {
            ToggleButton b = IconButtons.decorate(new ToggleButton(), t.name().toLowerCase(),
                    t.label + ": " + t.tip + " [" + (t.ordinal() + 1) % 10 + "]");
            b.setToggleGroup(group);
            b.setOnAction(e -> {
                ts.tool.set(t);
                b.setSelected(true); // clicking the active tool must not deselect it
            });
            buttons.put(t, b);
            getChildren().add(b);
        }
        buttons.get(ts.tool.get()).setSelected(true);
        ts.tool.addListener((o, a, n) -> buttons.get(n).setSelected(true));
        getChildren().add(new Separator(Orientation.VERTICAL));

        getChildren().add(action("image", "Image: add a picture (you can also drag files in, or paste with Ctrl+V)",
                v -> v.addImageFromChooser(), current));
        getChildren().add(action("map", "Battlemap: insert a live preview of a battlemap; double-click it to open the map",
                v -> v.insertMapPreview(), current));
        getChildren().add(new Separator(Orientation.VERTICAL));

        // --- style
        ColorPicker color = new ColorPicker(ts.color.get());
        color.setTooltip(IconButtons.tip("Text / outline colour (applies to the selection too)"));
        color.setOnAction(e -> ts.color.set(color.getValue()));
        color.setStyle("-fx-color-label-visible: false;"); // just the swatch, not the hex code
        ColorPicker fill = new ColorPicker(ts.fill.get());
        fill.setTooltip(IconButtons.tip("Sticky note colour / shape fill"));
        fill.setOnAction(e -> ts.fill.set(fill.getValue()));
        fill.setStyle("-fx-color-label-visible: false;");
        ToggleButton useFill = IconButtons.switchToggle("fill-shapes", "Fill shapes: draw rectangles and ellipses filled with the fill colour", ts.useFill);

        Spinner<Integer> width = new Spinner<>(1, 60, ts.width.get());
        width.setPrefWidth(70);
        width.setTooltip(IconButtons.tip("Line / pen width"));
        width.valueProperty().addListener((o, a, n) -> {
            if (n != null) ts.width.set(n);
        });
        Spinner<Integer> font = new Spinner<>(8, 200, ts.fontSize.get());
        font.setPrefWidth(75);
        font.setTooltip(IconButtons.tip("Font size"));
        font.valueProperty().addListener((o, a, n) -> {
            if (n != null) ts.fontSize.set(n);
        });
        // While a text is being typed, B and I style the selected words; otherwise they style the whole block.
        ToggleButton bold = new ToggleButton("B");
        bold.getStyleClass().add("icon-button");
        bold.setMinWidth(30);
        bold.setStyle("-fx-font-weight: bold;");
        bold.setFocusTraversable(false);
        bold.setTooltip(IconButtons.tip("Bold. While typing: the selected words (Ctrl+B). Otherwise: the whole text or sticky note"));
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
        italic.getStyleClass().add("icon-button");
        italic.setMinWidth(30);
        italic.setStyle("-fx-font-style: italic;");
        italic.setFocusTraversable(false);
        italic.setTooltip(IconButtons.tip("Italic. While typing: the selected words (Ctrl+I). Otherwise: the whole text or sticky note"));
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

        getChildren().addAll(color, fill, useFill, width, font, bold, italic, new Separator(Orientation.VERTICAL));

        // --- links and notes
        getChildren().addAll(
                action("link", "Link: link the selected words (while typing) or object(s) to another item (Ctrl+L). Blue = link: hover it to preview the item, double-click to open it", v -> v.linkSelected(), current),
                action("unlink", "Unlink: remove the link from the selected words (while typing) or object(s)", v -> v.unlinkSelected(), current),
                action("note", "Note: write your own note that shows when you hover over the selected words (while typing) or object(s). Green = note. Double-click a note to edit it",
                        v -> v.noteSelected(), current),
                action("no-note", "No note: remove the note from the selected words (while typing) or object(s)", v -> v.removeNoteSelected(), current),
                action("link-all", "Link all names: link every suggested name (dotted underline) in the selected text, or on the whole whiteboard if nothing is selected", v -> v.linkAllNames(), current),
                IconButtons.switchToggle("name-hints", "Name hints: underline the names of your cards and spells in text; hover one to link it", ts.nameHints),
                IconButtons.switchToggle("previews", "Link previews: show a preview of the linked card, spell, map or whiteboard when you hover over a link", ts.linkPreviews),
                new Separator(Orientation.VERTICAL),
                action("undo", "Undo (Ctrl+Z)", v -> v.undo(), current),
                action("redo", "Redo (Ctrl+Y)", v -> v.redo(), current),
                action("delete", "Delete the selection", v -> v.deleteSelected(), current));
    }

    private static Button action(String icon, String tip, Consumer<BoardView> run, Supplier<BoardView> current) {
        Button b = IconButtons.decorate(new Button(), icon, tip);
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
