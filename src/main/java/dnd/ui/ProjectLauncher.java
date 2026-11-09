package dnd.ui;

import dnd.storage.ProjectStore;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** First screen: choose, create or delete a project. */
public class ProjectLauncher extends VBox {
    private final Path base;
    private final ListView<String> list = new ListView<>();
    private final Consumer<ProjectStore> onOpen;
    private final StackPane overlay = new StackPane();
    private final Label overlayText = new Label();
    private boolean busy;

    public ProjectLauncher(Consumer<ProjectStore> onOpen) {
        this(ProjectStore.baseDir(), onOpen);
    }

    public ProjectLauncher(Path base, Consumer<ProjectStore> onOpen) {
        this.base = base;
        this.onOpen = onOpen;
        buildOverlay();
        setSpacing(12);
        setPadding(new Insets(30));
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(520);

        Label title = new Label("D&D Campaign Notes");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: bold;");
        Label sub = new Label("Choose a campaign to open, or create a new one.");
        Label where = new Label("Saved in: " + base);
        where.setStyle("-fx-text-fill: #888; -fx-font-size: 11px;");

        Button open = new Button("Open");
        open.setDefaultButton(true);
        open.setOnAction(e -> openSelected());
        Button create = new Button("New project...");
        create.setOnAction(e -> createProject());
        Button delete = new Button("Delete");
        delete.setOnAction(e -> deleteSelected());
        open.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        delete.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        HBox buttons = new HBox(8, open, create, delete);
        buttons.setAlignment(Pos.CENTER);

        list.setPlaceholder(new Label("No projects yet - create one!"));
        list.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) openSelected();
        });
        VBox.setVgrow(list, Priority.ALWAYS);

        getChildren().addAll(title, sub, list, buttons, where);
        refresh();
    }

    private void refresh() {
        list.setItems(FXCollections.observableArrayList(ProjectStore.listProjects(base)));
    }

    /**
     * A full-window "loading" layer (spinner and message). Put it on top of the launcher, e.g.
     * {@code new StackPane(launcher, launcher.loadingOverlay())}; it only shows while a project opens.
     */
    public StackPane loadingOverlay() {
        return overlay;
    }

    private void buildOverlay() {
        ProgressIndicator spinner = new ProgressIndicator(ProgressIndicator.INDETERMINATE_PROGRESS);
        spinner.setPrefSize(64, 64);
        overlayText.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #333;");
        VBox box = new VBox(14, spinner, overlayText);
        box.setAlignment(Pos.CENTER);
        box.setMaxSize(VBox.USE_PREF_SIZE, VBox.USE_PREF_SIZE);
        box.setPadding(new Insets(26, 40, 26, 40));
        box.setStyle("-fx-background-color: white; -fx-background-radius: 12; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 18, 0.2, 0, 4);");
        overlay.getChildren().add(box);
        overlay.setStyle("-fx-background-color: rgba(255,255,255,0.65);");
        overlay.setCursor(Cursor.WAIT);
        overlay.setVisible(false); // while hidden it neither shows nor catches clicks
    }

    private void showLoading(String message) {
        busy = true;
        overlayText.setText(message);
        overlay.setVisible(true);
        overlay.toFront();
    }

    private void hideLoading() {
        busy = false;
        overlay.setVisible(false);
    }

    /**
     * Loads a project on a background thread (so the spinner keeps turning) and then builds the workspace.
     */
    private void launch(String message, Callable<ProjectStore> loader, String failure) {
        if (busy) return; // ignore a second click / double-click while one is in progress
        showLoading(message);
        Task<ProjectStore> task = new Task<>() {
            @Override
            protected ProjectStore call() throws Exception {
                ProjectStore store = loader.call();
                store.preload();
                return store;
            }
        };
        task.setOnSucceeded(e -> {
            // building the workspace happens on the UI thread; the overlay stays up until it replaces this screen
            Platform.runLater(() -> {
                try {
                    onOpen.accept(task.getValue());
                } catch (RuntimeException ex) {
                    ex.printStackTrace();
                    hideLoading();
                    new Alert(Alert.AlertType.ERROR, failure + ex.getMessage()).show();
                }
            });
        });
        task.setOnFailed(e -> {
            hideLoading();
            Throwable ex = task.getException();
            new Alert(Alert.AlertType.ERROR, failure + (ex == null ? "" : ex.getMessage())).show();
        });
        Thread t = new Thread(task, "open-project");
        t.setDaemon(true);
        t.start();
    }

    private void openSelected() {
        String name = list.getSelectionModel().getSelectedItem();
        if (name == null) return;
        openProject(name);
    }

    /** Opens the named project with the loading spinner showing. */
    public void openProject(String name) {
        launch("Opening " + name + "...", () -> ProjectStore.open(base, name), "Could not open project: ");
    }

    private void createProject() {
        TextInputDialog d = new TextInputDialog("My Campaign");
        d.setTitle("New project");
        d.setHeaderText("Campaign name:");
        Optional<String> name = d.showAndWait().map(String::trim).filter(s -> !s.isEmpty());
        if (name.isEmpty()) return;
        String projectName = name.get();
        launch("Creating " + projectName + "...", () -> {
            Files.createDirectories(base);
            return ProjectStore.create(base, projectName);
        }, "Could not create project: ");
    }

    private void deleteSelected() {
        String name = list.getSelectionModel().getSelectedItem();
        if (name == null) return;
        Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                "Permanently delete project \"" + name + "\" and all its whiteboards and images?",
                ButtonType.OK, ButtonType.CANCEL);
        a.setHeaderText("Delete project");
        Optional<ButtonType> r = a.showAndWait();
        if (r.isPresent() && r.get() == ButtonType.OK) {
            try {
                ProjectStore.deleteProject(base, name);
            } catch (IOException e) {
                new Alert(Alert.AlertType.ERROR, "Could not delete: " + e.getMessage()).show();
            }
            refresh();
        }
    }
}
