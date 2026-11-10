package dnd.ui;

import dnd.map.BattleMapExport;
import dnd.map.MapRenderer;
import dnd.export.MarkdownExport;
import dnd.model.BattleMap;
import dnd.storage.ProjectStore;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;

/** Asks how to save a battlemap as a PNG picture, then saves it strip by strip with a progress window. */
final class BattleExportDialog {
    private static final String DM = "The DM's version (everything, hidden things faded)";
    private static final String PLAYERS = "The players' version (what they can see now)";

    private BattleExportDialog() { }

    static void show(Window owner, BattleMap map, ProjectStore store) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Export image");
        d.setHeaderText("Save \"" + map.name + "\" as a PNG picture");
        if (owner != null) d.initOwner(owner);

        ComboBox<String> who = new ComboBox<>();
        who.getItems().addAll(DM, PLAYERS);
        who.setValue(AppSettings.getBoolean("export.battle.players", false) ? PLAYERS : DM);
        List<Integer> widths = List.of(1024, 2048, 4096, 8192);
        ComboBox<Integer> size = new ComboBox<>();
        size.getItems().addAll(widths);
        size.setValue(widths.contains(AppSettings.getInt("export.battle.width", 2048)) ? AppSettings.getInt("export.battle.width", 2048) : 2048);
        size.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Integer w) {
                return w == null ? "" : w + " pixels wide (" + BattleMapExport.heightFor(map, w) + " high)";
            }

            @Override
            public Integer fromString(String s) {
                return null;
            }
        });
        CheckBox grid = new CheckBox("Grid");
        grid.setSelected(map.showGrid);
        Label note = new Label("Tokens, fog, lights and pins are drawn as in the chosen version; the DM's pins are never drawn.");
        note.setWrapText(true);
        note.setMaxWidth(380);
        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.setPadding(new Insets(6));
        form.addRow(0, new Label("Version"), who);
        form.addRow(1, new Label("Size"), size);
        form.addRow(2, new Label("Include"), grid);
        form.add(note, 1, 3);
        d.getDialogPane().setContent(form);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ((Button) d.getDialogPane().lookupButton(ButtonType.OK)).setText("Choose file...");
        if (d.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        boolean forPlayers = PLAYERS.equals(who.getValue());
        int width = size.getValue();
        try {
            AppSettings.setBoolean("export.battle.players", forPlayers);
            AppSettings.setInt("export.battle.width", width);
        } catch (RuntimeException ignored) {
            // remembering the choices is a convenience only
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Save the map picture");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG picture", "*.png"));
        fc.setInitialFileName(MarkdownExport.fileName(map.name + (forPlayers ? " (players)" : ""), ".png"));
        String lastDir = AppSettings.getString("export.battle.dir", null);
        if (lastDir != null && new File(lastDir).isDirectory()) fc.setInitialDirectory(new File(lastDir));
        File file = fc.showSaveDialog(owner);
        if (file == null) return;
        if (file.getParentFile() != null) {
            try {
                AppSettings.setString("export.battle.dir", file.getParentFile().getAbsolutePath());
            } catch (RuntimeException ignored) {
                // remembering the folder is a convenience only
            }
        }
        if (!file.getName().toLowerCase().endsWith(".png")) file = new File(file.getParentFile(), file.getName() + ".png");
        OutputStream out;
        BattleMapExport.Job job;
        try {
            out = new java.io.BufferedOutputStream(Files.newOutputStream(file.toPath()), 64 * 1024);
            job = new BattleMapExport.Job(map, store.assets(),
                    new BattleMapExport.Options(forPlayers ? MapRenderer.Audience.PLAYERS : MapRenderer.Audience.DM, width, grid.isSelected()), out);
        } catch (IOException | RuntimeException ex) {
            new Alert(Alert.AlertType.ERROR, "Could not save the picture: " + ex.getMessage()).show();
            return;
        }
        ExportProgress.run(owner, job, out, file);
    }
}
