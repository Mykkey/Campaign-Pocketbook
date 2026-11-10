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
        dnd.ui.Theme.install();
        stage.setTitle(TITLE);
        try (java.io.InputStream icon = App.class.getResourceAsStream("/icon.png")) {
            if (icon != null) stage.getIcons().add(new javafx.scene.image.Image(icon));
        } catch (java.io.IOException ignored) {
            // the app works fine without an icon
        }
        stage.setWidth(1400);
        stage.setHeight(850);
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
        stage.setScene(new Scene(root, stage.getWidth(), stage.getHeight()));
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
