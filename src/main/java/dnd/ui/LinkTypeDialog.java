package dnd.ui;

import dnd.model.LinkType;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Window;

import java.util.Optional;

/** Make up your own kind of link between two cards: what it is called, how it reads from the other side, and the colour of its line. */
public final class LinkTypeDialog {
    private LinkTypeDialog() { }

    /** A "#RRGGBB" string for a colour. */
    static String hex(Color c) {
        return String.format("#%02X%02X%02X", (int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
    }

    /**
     * @param existing the link to change, or null to make a new one
     * @return the link as it was made or changed (a new object for a new one; the same id for a changed one), or empty if cancelled
     */
    public static Optional<LinkType> show(Window owner, LinkType existing) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle(existing == null ? "New custom link" : "Change custom link");
        d.setHeaderText(existing == null ? "Make up a kind of link between two cards" : "Change \"" + existing.name + "\"");
        if (owner != null) d.initOwner(owner);

        TextField name = new TextField(existing == null ? "" : existing.name);
        name.setPromptText("e.g. Owes a favour to");
        name.setPrefWidth(260);
        CheckBox directed = new CheckBox("It reads differently from the other card's side");
        directed.setSelected(existing != null && existing.directed());
        TextField reverse = new TextField(existing == null ? "" : existing.reverseName);
        reverse.setPromptText("e.g. Is owed a favour by");
        reverse.setPrefWidth(260);
        reverse.disableProperty().bind(directed.selectedProperty().not());
        Color start;
        try {
            start = Color.web(existing == null ? "#d9731a" : existing.colour);
        } catch (IllegalArgumentException e) {
            start = Color.web("#d9731a");
        }
        ColorPicker colour = new ColorPicker(start);
        Label preview = new Label();
        preview.setWrapText(true);
        Label note = new Label("A link written on one card shows on the other card too. If it reads differently from the other side it is drawn with an arrow "
                + "(from the card it is written on to the other card).");
        note.setWrapText(true);
        note.setMaxWidth(420);
        note.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 0.917em;");

        Runnable refresh = () -> {
            String n = name.getText().trim();
            String r = reverse.getText().trim();
            String shown = n.isEmpty() ? "(name)" : n;
            preview.setText("Alice  ---  " + shown + (directed.isSelected() ? "  --->  Bob     (from Bob: " + (r.isEmpty() ? "..." : r) + ")" : "  ---  Bob"));
            preview.setStyle("-fx-font-weight: bold; -fx-text-fill: " + hex(colour.getValue()) + ";");
            d.getDialogPane().lookupButton(ButtonType.OK).setDisable(n.isEmpty() || (directed.isSelected() && r.isEmpty()));
        };

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label("Name"), name);
        grid.add(directed, 1, 1);
        grid.addRow(2, new Label("Other side reads"), reverse);
        grid.addRow(3, new Label("Colour"), colour);
        VBox box = new VBox(10, grid, preview, note);
        box.setPadding(new Insets(10));
        d.getDialogPane().setContent(box);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        name.textProperty().addListener((o, a, b) -> refresh.run());
        reverse.textProperty().addListener((o, a, b) -> refresh.run());
        directed.selectedProperty().addListener((o, a, b) -> refresh.run());
        colour.valueProperty().addListener((o, a, b) -> refresh.run());
        refresh.run();
        javafx.application.Platform.runLater(name::requestFocus);

        if (d.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return Optional.empty();
        LinkType made = new LinkType(name.getText(), directed.isSelected() ? reverse.getText() : "", hex(colour.getValue()));
        if (existing != null) made.id = existing.id;
        return Optional.of(made);
    }
}
