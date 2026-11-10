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

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        dnd.ui.SettingsMigration.run(dnd.ui.SettingsMigration.oldRoot());
        dnd.ui.Theme.install();
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
        UpdateBanner.startCheck();
        showLauncher();
        stage.show();
    }

    private void showLauncher() {
        workspace = null;
        BorderPane root = new BorderPane();
        root.setTop(new UpdateBanner(getHostServices()));
        ProjectLauncher launcher = new ProjectLauncher(this::showWorkspace);
        root.setCenter(new StackPane(launcher, launcher.loadingOverlay()));
        Scene scene = new Scene(root, stage.getWidth(), stage.getHeight());
        scene.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.COMMA, javafx.scene.input.KeyCombination.SHORTCUT_DOWN),
                () -> dnd.ui.SettingsDialog.show(stage));
        stage.setScene(scene);
        stage.setTitle(TITLE);
    }

    private void showWorkspace(ProjectStore store) {
        workspace = new WorkspaceView(store, this::showLauncher, getHostServices());
        stage.setScene(new Scene(workspace, stage.getWidth(), stage.getHeight()));
        stage.setTitle(TITLE + " - " + store.project.name);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
