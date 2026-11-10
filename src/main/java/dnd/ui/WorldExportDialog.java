package dnd.ui;

import dnd.map.world.WorldComposer;
import dnd.map.world.WorldExport;
import dnd.model.WorldMap;
import dnd.storage.ProjectStore;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.prefs.Preferences;

/** Asks how to save a world map as a picture, then saves it band by band while showing the progress. */
final class WorldExportDialog {
    private static final Preferences PREFS = Preferences.userNodeForPackage(WorldExportDialog.class).node("worldExport");
    private static final String DM = "The DM's version (everything, hidden things faded)";
    private static final String PLAYERS = "The players' version (nothing hidden, unexplored areas blank)";

    private WorldExportDialog() { }

    static void show(Window owner, WorldMap map, ProjectStore store) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Export image");
        d.setHeaderText("Save \"" + map.name + "\" as a PNG picture");
        if (owner != null) d.initOwner(owner);

        ComboBox<String> who = new ComboBox<>();
        who.getItems().addAll(DM, PLAYERS);
        who.setValue(PREFS.getBoolean("players", false) ? PLAYERS : DM);
        List<Integer> widths = List.of(1024, 2048, 4096, 8192);
        ComboBox<Integer> size = new ComboBox<>();
        size.getItems().addAll(widths);
        size.setValue(widths.contains(PREFS.getInt("width", 2048)) ? PREFS.getInt("width", 2048) : 2048);
        size.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Integer w) {
                return w == null ? "" : w + " pixels wide (" + WorldExport.heightFor(map, w) + " high)";
            }

            @Override
            public Integer fromString(String s) {
                return null;
            }
        });
        CheckBox grid = new CheckBox("Grid");
        grid.setSelected(map.gridKind != dnd.model.WorldGridKind.NONE && PREFS.getBoolean("grid", true));
        grid.setDisable(map.gridKind == dnd.model.WorldGridKind.NONE);
        CheckBox legend = new CheckBox("Scale bar and legend");
        legend.setSelected((map.showScale || map.showLegend) && PREFS.getBoolean("legend", true));
        legend.setDisable(!map.showScale && !map.showLegend);
        Label note = new Label();
        note.setWrapText(true);
        note.setMaxWidth(380);
        Runnable explain = () -> note.setText(who.getValue() == PLAYERS || PLAYERS.equals(who.getValue())
                ? "Nothing marked \"hide from players\" is drawn, and neither are notes or links."
                        + (map.hideUnexplored ? " The parts you have not revealed are blank parchment." : "")
                : "Notes, links and the editing marks are never drawn. Things hidden from the players show faded.");
        who.valueProperty().addListener((o, a, n) -> explain.run());
        explain.run();

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.setPadding(new Insets(6));
        form.addRow(0, new Label("Version"), who);
        form.addRow(1, new Label("Size"), size);
        form.addRow(2, new Label("Include"), new VBox(4, grid, legend));
        form.add(note, 1, 3);
        d.getDialogPane().setContent(form);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ((Button) d.getDialogPane().lookupButton(ButtonType.OK)).setText("Choose file...");
        if (d.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        boolean forPlayers = PLAYERS.equals(who.getValue());
        int width = size.getValue();
        try {
            PREFS.putBoolean("players", forPlayers);
            PREFS.putInt("width", width);
            PREFS.putBoolean("grid", grid.isSelected() || grid.isDisabled() && PREFS.getBoolean("grid", true));
            PREFS.putBoolean("legend", legend.isSelected() || legend.isDisabled() && PREFS.getBoolean("legend", true));
        } catch (RuntimeException ignored) {
            // remembering the choices is a convenience only
        }

        FileChooser fc = new FileChooser();
        fc.setTitle("Save the map picture");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG picture", "*.png"));
        String safe = (map.name == null || map.name.isBlank() ? "World map" : map.name).replaceAll("[\\\\/:*?\"<>|]", "-");
        fc.setInitialFileName(safe + (forPlayers ? " (players)" : "") + ".png");
        String lastDir = PREFS.get("dir", null);
        if (lastDir != null && new File(lastDir).isDirectory()) fc.setInitialDirectory(new File(lastDir));
        File file = fc.showSaveDialog(owner);
        if (file == null) return;
        if (file.getParentFile() != null) {
            try {
                PREFS.put("dir", file.getParentFile().getAbsolutePath());
            } catch (RuntimeException ignored) {
                // remembering the folder is a convenience only
            }
        }
        if (!file.getName().toLowerCase().endsWith(".png")) file = new File(file.getParentFile(), file.getName() + ".png");
        run(owner, map, store, new WorldExport.Options(forPlayers ? WorldComposer.Audience.PLAYERS : WorldComposer.Audience.DM,
                width, grid.isSelected(), legend.isSelected()), file);
    }

    /** Draws and writes the picture a band at a time, with a small window showing how far it has got. */
    private static void run(Window owner, WorldMap map, ProjectStore store, WorldExport.Options options, File file) {
        OutputStream out;
        WorldExport.Job job;
        try {
            out = new java.io.BufferedOutputStream(Files.newOutputStream(file.toPath()), 64 * 1024);
            job = new WorldExport.Job(map, store.assets(), options, out);
        } catch (IOException | RuntimeException ex) {
            new Alert(Alert.AlertType.ERROR, "Could not save the picture: " + ex.getMessage()).show();
            return;
        }
        ExportProgress.run(owner, job, out, file);
    }
}
