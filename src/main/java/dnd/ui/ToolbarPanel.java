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
                    dnd.keys.Shortcuts.tip(t.label + ": " + t.tip, "tool.board." + (t.ordinal() + 1)));
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

        MenuButton insert = IconMenu.create("image", "Insert: a picture or a live battlemap preview");
        insert.getItems().addAll(
                row("image", "Picture (or drag files in, or paste)", null, v -> v.addImageFromChooser(), current),
                row("map", "Battlemap preview (double-click it to open the map)", null, v -> v.insertMapPreview(), current));
        getChildren().addAll(insert, new Separator(Orientation.VERTICAL));

        // --- style
        ColorPicker color = new ColorPicker(ts.color.get());
        color.setTooltip(IconButtons.tip("Text / outline colour (applies to the selection too)"));
        color.setOnAction(e -> ts.color.set(color.getValue()));
        color.setStyle("-fx-color-label-visible: false;"); // just the swatch, not the hex code
        ColorPicker fill = new ColorPicker(ts.fill.get());
        fill.setTooltip(IconButtons.tip("Sticky note colour / shape fill"));
        fill.setOnAction(e -> ts.fill.set(fill.getValue()));
        fill.setStyle("-fx-color-label-visible: false;");

        Spinner<Integer> width = new Spinner<>(1, 60, ts.width.get());
        width.setPrefWidth(70);
        width.setEditable(true);
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

        MenuButton lines = IconMenu.create("sliders", "Line and text size: pen width, font size and filled shapes");
        lines.getItems().addAll(
                IconMenu.control("pen", "Line / pen width", width),
                IconMenu.control("text", "Font size", font),
                IconMenu.toggle("fill-shapes", "Fill rectangles and ellipses with the fill colour", null, ts.useFill));
        getChildren().addAll(color, fill, bold, italic, lines, new Separator(Orientation.VERTICAL));

        // --- links and notes
        MenuButton links = IconMenu.create("link", "Links and notes: link words or objects to other items, add hover notes");
        links.getItems().addAll(
                row("link", "Link to an item (blue; hover to preview, double-click to open)", "item.link", v -> v.linkSelected(), current),
                row("unlink", "Remove the link", null, v -> v.unlinkSelected(), current),
                row("note", "Write a note that shows on hover (green)", null, v -> v.noteSelected(), current),
                row("no-note", "Remove the note", null, v -> v.removeNoteSelected(), current),
                IconMenu.separator(),
                row("link-all", "Link all suggested names in the selection (or whole board)", null, v -> v.linkAllNames(), current),
                IconMenu.toggle("name-hints", "Underline names of your cards and spells so you can link them", null, ts.nameHints),
                IconMenu.toggle("previews", "Preview linked items when hovering over a link", null, ts.linkPreviews));
        getChildren().addAll(links, new Separator(Orientation.VERTICAL));
        getChildren().addAll(
                action("undo", dnd.keys.Shortcuts.tip("Undo", "item.undo"), v -> v.undo(), current),
                action("redo", dnd.keys.Shortcuts.tip("Redo", "item.redo"), v -> v.redo(), current),
                action("delete", "Delete the selection", v -> v.deleteSelected(), current));
    }

    private static MenuItem row(String icon, String label, String actionId, Consumer<BoardView> run, Supplier<BoardView> current) {
        return IconMenu.action(icon, label, actionId, () -> {
            BoardView v = current.get();
            if (v != null) {
                run.accept(v);
                if (!v.isEditingText()) v.requestFocus();
            }
        });
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
