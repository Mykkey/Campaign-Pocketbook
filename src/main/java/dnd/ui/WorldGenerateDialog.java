package dnd.ui;

import dnd.map.world.WorldGenerator;
import dnd.model.WorldMap;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.function.Consumer;

/** Asks how to generate a random world on an existing map (this replaces what is painted and placed on it). */
final class WorldGenerateDialog {
    private WorldGenerateDialog() { }

    /** @param apply runs the generation as one undoable step */
    static void show(Window owner, WorldMap map, Consumer<Runnable> apply) {
        WorldGeneratorPane pane = new WorldGeneratorPane();
        boolean hasContent = !map.base().isEmpty() || !map.biome().isEmpty() || !map.stamps.isEmpty() || !map.paths.isEmpty() || !map.regions.isEmpty();
        Label warn = new Label(hasContent
                ? "This replaces everything painted and placed on \"" + map.name + "\" with a new random world. You can undo it with Ctrl+Z."
                : "Makes a random world on this map. You can undo it with Ctrl+Z.");
        warn.setWrapText(true);
        warn.setMaxWidth(380);
        warn.setStyle(hasContent ? "-fx-text-fill: #b45309; -fx-font-weight: bold;" : "-fx-text-fill: -t-text2;");
        Label size = new Label("The map keeps its size (" + map.cols + " x " + map.rows + " cells), its scale and its art style. Big maps take a few seconds.");
        size.setWrapText(true);
        size.setMaxWidth(380);
        size.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 0.917em;");
        VBox box = new VBox(10, pane, warn, size);
        box.setPadding(new Insets(6));

        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Generate a random world");
        d.setHeaderText("Random world");
        if (owner != null) d.initOwner(owner);
        d.getDialogPane().setContent(box);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        if (d.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        WorldGenerator.Options options = pane.options();
        apply.accept(() -> WorldGenerator.generate(map, options));
    }
}
