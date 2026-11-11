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

        Label title = new Label("Campaign Pocketbook");
        title.setStyle("-fx-font-size: 2.333em; -fx-font-weight: bold;");
        Label sub = new Label("Choose a campaign to open, or create a new one.");
        Label where = new Label("Saved in: " + base);
        where.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 0.917em;");

        Button open = new Button("Open");
        open.setDefaultButton(true);
        open.setOnAction(e -> openSelected());
        Button create = new Button("New project...");
        create.setOnAction(e -> createProject());
        Button delete = new Button("Delete");
        delete.setOnAction(e -> deleteSelected());
        open.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        delete.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        Button settings = IconButtons.decorate(new Button(), "gear", "Settings for the whole app: text size, colours, accessibility and keyboard shortcuts (Ctrl+,)");
        settings.setOnAction(e -> SettingsDialog.show(getScene() == null ? null : getScene().getWindow()));
        HBox buttons = new HBox(8, open, create, delete, Theme.toggleButton(), settings);
        buttons.setAlignment(Pos.CENTER);

        list.setPlaceholder(new Label("No projects yet - create one!"));
        list.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) openSelected();
        });
        VBox.setVgrow(list, Priority.ALWAYS);

        Button export = new Button("Export backup...");
        export.setTooltip(IconButtons.tip("Save the selected campaign, with its pictures and maps, as one .zip file to keep or share"));
        export.setOnAction(e -> exportSelected());
        export.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        Button importZip = new Button("Import backup...");
        importZip.setTooltip(IconButtons.tip("Bring in a campaign from a .zip made with Export backup. It becomes a new project; nothing is overwritten"));
        importZip.setOnAction(e -> importBackup());
        HBox backup = new HBox(8, export, importZip);
        backup.setAlignment(Pos.CENTER);

        getChildren().addAll(title, sub, list, buttons, backup, where);
        refresh();
    }

    private void refresh() {
        list.setItems(FXCollections.observableArrayList(RecentProjects.sorted(ProjectStore.listProjects(base))));
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
        overlayText.setStyle("-fx-font-size: 1.333em; -fx-font-weight: bold; -fx-text-fill: -t-text;");
        VBox box = new VBox(14, spinner, overlayText);
        box.setAlignment(Pos.CENTER);
        box.setMaxSize(VBox.USE_PREF_SIZE, VBox.USE_PREF_SIZE);
        box.setPadding(new Insets(26, 40, 26, 40));
        box.setStyle("-fx-background-color: -t-surface; -fx-background-radius: 12; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 18, 0.2, 0, 4);");
        overlay.getChildren().add(box);
        overlay.styleProperty().bind(javafx.beans.binding.Bindings.when(Theme.darkProperty())
                .then("-fx-background-color: rgba(20,22,26,0.72);").otherwise("-fx-background-color: rgba(255,255,255,0.65);"));
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
                    dnd.util.Log.error("Problem in ProjectLauncher", ex);
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
            ProjectStore made = ProjectStore.create(base, projectName);
            made.project.partySize = Math.max(1, Math.min(10, AppSettings.getInt("new.partySize", 4)));
            made.project.partyLevel = Math.max(1, Math.min(20, AppSettings.getInt("new.partyLevel", 3)));
            made.markProjectDirty();
            return made;
        }, "Could not create project: ");
    }

    /** Runs a slow job with the spinner showing, then reports on the UI thread. */
    private <T> void background(String message, Callable<T> job, Consumer<T> done, String failure) {
        if (busy) return;
        showLoading(message);
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return job.call();
            }
        };
        task.setOnSucceeded(e -> {
            hideLoading();
            done.accept(task.getValue());
        });
        task.setOnFailed(e -> {
            hideLoading();
            Throwable ex = task.getException();
            new Alert(Alert.AlertType.ERROR, failure + (ex == null ? "" : ex.getMessage())).show();
        });
        Thread t = new Thread(task, "backup");
        t.setDaemon(true);
        t.start();
    }

    private void exportSelected() {
        String name = list.getSelectionModel().getSelectedItem();
        if (name == null) return;
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Save a backup of " + name);
        fc.setInitialFileName(name + ".zip");
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Campaign backup (*.zip)", "*.zip"));
        java.io.File target = fc.showSaveDialog(getScene().getWindow());
        if (target == null) return;
        Path zip = target.toPath();
        if (!zip.getFileName().toString().toLowerCase().endsWith(".zip")) zip = zip.resolveSibling(zip.getFileName() + ".zip");
        final Path out = zip;
        background("Saving " + name + "...", () -> dnd.storage.ProjectArchive.export(base.resolve(name), out),
                files -> {
                    Alert a = new Alert(Alert.AlertType.INFORMATION, files + " files saved to:\n" + out);
                    a.setHeaderText("Backup saved");
                    a.show();
                }, "Could not save the backup: ");
    }

    private void importBackup() {
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Choose a campaign backup");
        Path autoBackups = dnd.storage.ProjectBackups.root(base);
        if (Files.isDirectory(autoBackups)) fc.setInitialDirectory(autoBackups.toFile()); // the automatic backups are kept there
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Campaign backup (*.zip)", "*.zip"));
        java.io.File source = fc.showOpenDialog(getScene().getWindow());
        if (source == null) return;
        background("Importing " + source.getName() + "...", () -> dnd.storage.ProjectArchive.importZip(source.toPath(), base),
                result -> {
                    refresh();
                    list.getSelectionModel().select(result.folderName());
                    Alert a = new Alert(Alert.AlertType.INFORMATION,
                            "\"" + result.folderName() + "\" was added to your projects (" + result.files() + " files). Your other campaigns were not touched.");
                    a.setHeaderText("Backup imported");
                    a.show();
                }, "Could not import the backup: ");
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
                RecentProjects.forget(name);
            } catch (IOException e) {
                new Alert(Alert.AlertType.ERROR, "Could not delete: " + e.getMessage()).show();
            }
            refresh();
        }
    }
}
