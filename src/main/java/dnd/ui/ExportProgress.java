package dnd.ui;

import dnd.map.BandJob;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

/** Runs a picture export a strip at a time while a small window shows the progress and lets the user cancel. */
final class ExportProgress {
    private ExportProgress() { }

    /** Draws and writes the picture; the stream is closed (and a cancelled or failed file deleted) when it ends. */
    static void run(Window owner, BandJob job, OutputStream out, File file) {
        ProgressBar bar = new ProgressBar(0);
        bar.setPrefWidth(320);
        Button cancel = new Button("Cancel");
        Label what = new Label("Drawing " + job.width() + " x " + job.height() + " pixels...");
        VBox box = new VBox(10, what, bar, cancel);
        box.setPadding(new Insets(16));
        box.setAlignment(Pos.CENTER);
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("Exporting");
        stage.setScene(new Scene(box));
        stage.setResizable(false);

        final boolean[] cancelled = {false};
        final BandJob running = job;
        final OutputStream stream = out;
        final File target = file;
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                try {
                    if (cancelled[0]) {
                        finish(false, null);
                        return;
                    }
                    boolean done = running.step();
                    bar.setProgress(running.progress());
                    if (done) finish(true, null);
                } catch (IOException | RuntimeException ex) {
                    finish(false, ex.getMessage());
                }
            }

            private void finish(boolean ok, String error) {
                stop();
                try {
                    if (ok) running.close();
                    stream.close();
                } catch (IOException ex) {
                    ok = false;
                    error = ex.getMessage();
                }
                stage.close();
                if (!ok) {
                    try {
                        Files.deleteIfExists(target.toPath());
                    } catch (IOException ignored) {
                        // a half-written picture may stay behind; nothing more can be done
                    }
                }
                if (ok) {
                    Alert a = new Alert(Alert.AlertType.INFORMATION, "Saved " + target.getAbsolutePath(), ButtonType.OK);
                    a.setHeaderText("Picture saved");
                    if (owner != null) a.initOwner(owner);
                    a.show();
                } else if (error != null) {
                    new Alert(Alert.AlertType.ERROR, "Could not save the picture: " + error).show();
                }
            }
        };
        cancel.setOnAction(e -> cancelled[0] = true);
        stage.setOnCloseRequest(e -> {
            cancelled[0] = true;
            e.consume();
        });
        stage.show();
        timer.start();
    }
}
