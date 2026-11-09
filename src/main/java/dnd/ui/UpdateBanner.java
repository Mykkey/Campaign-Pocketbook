package dnd.ui;

import dnd.update.UpdateChecker;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * Blue "new version available" bar. One background check runs per app launch; every banner
 * instance (project screen, workspace) shows the same result.
 */
public class UpdateBanner extends HBox {
    /** The newer version found on GitHub, or null. */
    private static final StringProperty NEWER = new SimpleStringProperty();
    private static final Preferences PREFS = Preferences.userNodeForPackage(UpdateBanner.class);
    private static boolean started;

    private final HostServices hostServices;
    private final Label msg = new Label();
    private final Button update = new Button();
    private final Button later = new Button("Dismiss");
    private final ChangeListener<String> listener = (o, a, n) -> refresh(n);

    public UpdateBanner(HostServices hostServices) {
        super(10);
        this.hostServices = hostServices;
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        msg.setStyle("-fx-text-fill: #1e3a8a;");
        getChildren().addAll(msg, sp, update, later);
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(6, 10, 6, 10));
        setStyle("-fx-background-color: #dbeafe; -fx-border-color: #93c5fd; -fx-border-width: 0 0 1 0;");

        later.setOnAction(e -> {
            String v = NEWER.get();
            if (v != null) PREFS.put("dismissedUpdate", v);
            NEWER.set(null);
        });
        update.setOnAction(e -> onUpdate());

        NEWER.addListener(new WeakChangeListener<>(listener)); // weak: banners from old screens can be collected
        refresh(NEWER.get());
    }

    /** Starts the one-per-launch background check. Safe to call repeatedly. */
    public static void startCheck() {
        if (started) return;
        started = true;
        Thread t = new Thread(() -> {
            Optional<String> newer = UpdateChecker.findNewerVersion();
            newer.ifPresent(v -> {
                if (!v.equals(PREFS.get("dismissedUpdate", ""))) Platform.runLater(() -> NEWER.set(v));
            });
        }, "update-check");
        t.setDaemon(true);
        t.start();
    }

    private void refresh(String version) {
        boolean show = version != null;
        setVisible(show);
        setManaged(show);
        if (!show) return;
        msg.setText("A new version (" + version + ") is available - you have " + UpdateChecker.currentVersion() + ".");
        update.setText(isGitCopy() ? "Update now" : "Open download page");
        update.setVisible(true);
        update.setManaged(true);
        update.setDisable(false);
        later.setVisible(true);
        later.setManaged(true);
    }

    private void onUpdate() {
        if (!isGitCopy()) {
            hostServices.showDocument(UpdateChecker.REPO_URL);
            return;
        }
        update.setDisable(true);
        msg.setText("Updating...");
        Thread t = new Thread(() -> {
            String result;
            try {
                Process p = new ProcessBuilder("git", "pull").directory(appDir().toFile())
                        .redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                result = p.waitFor() == 0
                        ? "Updated! Close and reopen the app to use the new version."
                        : "Update failed. Try running update.bat, or see the README.";
            } catch (Exception ex) {
                result = "Could not run git. Install Git, or download the new version from GitHub.";
            }
            String text = result;
            Platform.runLater(() -> {
                msg.setText(text);
                update.setVisible(false);
                update.setManaged(false);
            });
        }, "git-pull");
        t.setDaemon(true);
        t.start();
    }

    private static Path appDir() {
        return Paths.get("").toAbsolutePath();
    }

    private static boolean isGitCopy() {
        return Files.isDirectory(appDir().resolve(".git"));
    }
}
