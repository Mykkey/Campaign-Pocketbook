package dnd;

import dnd.storage.ProjectStore;
import dnd.ui.ProjectLauncher;
import dnd.ui.UpdateBanner;
import javafx.scene.layout.BorderPane;
import dnd.ui.WorkspaceView;
import dnd.update.UpdateChecker;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class App extends Application {
    private static final String TITLE = "Campaign Pocketbook (" + UpdateChecker.currentVersion() + ")";

    private Stage stage;
    private WorkspaceView workspace;
    private ProjectLauncher launcher;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        dnd.ui.SettingsMigration.run(dnd.ui.SettingsMigration.oldRoot());
        dnd.ui.Theme.install();
        dnd.ui.Theme.onLookChanged = this::rebuildScreen;
        dnd.ui.WindowFit.install();
        stage.setTitle(TITLE);
        try (java.io.InputStream icon = App.class.getResourceAsStream("/icon.png")) {
            if (icon != null) stage.getIcons().add(new javafx.scene.image.Image(icon));
        } catch (java.io.IOException ignored) {
            // the app works fine without an icon
        }
        double[] box = dnd.ui.WindowFit.fit(1400, 850, javafx.stage.Screen.getPrimary().getVisualBounds());
        stage.setX(box[0]);
        stage.setY(box[1]);
        stage.setWidth(box[2]);
        stage.setHeight(box[3]);
        stage.setMinWidth(Math.min(700, box[2]));
        stage.setMinHeight(Math.min(480, box[3]));
        stage.setOnCloseRequest(e -> {
            if (workspace != null) workspace.shutdown();
        });
        if (dnd.ui.GeneralSettings.updateCheck()) UpdateBanner.startCheck();
        showLauncher();
        stage.show();
        // the campaign that was open last time, if the settings say so
        String last = dnd.ui.AppSettings.getString(dnd.ui.GeneralSettings.LAST_PROJECT, "");
        if (dnd.ui.GeneralSettings.reopenLast() && !last.isBlank() && launcher != null
                && java.nio.file.Files.isDirectory(ProjectStore.baseDir().resolve(last))) {
            javafx.application.Platform.runLater(() -> launcher.openProject(last));
        }
    }

    /** Shows the current screen again, built afresh (after the text size changed); the open campaign and its tabs come back as they were. */
    private void rebuildScreen() {
        if (workspace != null) {
            ProjectStore store = workspace.store();
            workspace.shutdown();
            showWorkspace(store);
        } else {
            showLauncher();
        }
    }

    /** Leaves the campaign for the project list (so the next start does not reopen it). */
    private void backToProjects() {
        dnd.ui.AppSettings.remove(dnd.ui.GeneralSettings.LAST_PROJECT);
        showLauncher();
    }

    private void showLauncher() {
        workspace = null;
        BorderPane root = new BorderPane();
        root.setTop(new UpdateBanner(getHostServices()));
        ProjectLauncher launcher = new ProjectLauncher(this::showWorkspace);
        this.launcher = launcher;
        root.setCenter(new StackPane(launcher, launcher.loadingOverlay()));
        Scene scene = new Scene(root, stage.getWidth(), stage.getHeight());
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, ev -> {
            if (dnd.keys.Shortcuts.matches(ev, "global.settings")) {
                dnd.ui.SettingsDialog.show(stage);
                ev.consume();
            }
        });
        stage.setScene(scene);
        stage.setTitle(TITLE);
    }

    private void showWorkspace(ProjectStore store) {
        dnd.ui.AppSettings.setString(dnd.ui.GeneralSettings.LAST_PROJECT, store.dir.getFileName().toString());
        dnd.ui.RecentProjects.opened(store.dir.getFileName().toString());
        workspace = new WorkspaceView(store, this::backToProjects, getHostServices());
        stage.setScene(new Scene(workspace, stage.getWidth(), stage.getHeight()));
        stage.setTitle(TITLE + " - " + store.project.name);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
