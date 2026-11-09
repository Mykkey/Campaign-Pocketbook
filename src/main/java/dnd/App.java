package dnd;

import dnd.storage.ProjectStore;
import dnd.ui.ProjectLauncher;
import dnd.ui.UpdateBanner;
import javafx.scene.layout.BorderPane;
import dnd.ui.WorkspaceView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class App extends Application {
    private Stage stage;
    private WorkspaceView workspace;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("D&D Campaign Notes");
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
        root.setCenter(new StackPane(new ProjectLauncher(this::showWorkspace)));
        stage.setScene(new Scene(root, stage.getWidth(), stage.getHeight()));
        stage.setTitle("D&D Campaign Notes");
    }

    private void showWorkspace(ProjectStore store) {
        workspace = new WorkspaceView(store, this::showLauncher, getHostServices());
        stage.setScene(new Scene(workspace, stage.getWidth(), stage.getHeight()));
        stage.setTitle("D&D Campaign Notes - " + store.project.name);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
