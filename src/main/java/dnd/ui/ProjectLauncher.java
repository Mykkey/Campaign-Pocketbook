package dnd.ui;

import dnd.storage.ProjectStore;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

/** First screen: choose, create or delete a project. */
public class ProjectLauncher extends VBox {
    private final Path base = ProjectStore.baseDir();
    private final ListView<String> list = new ListView<>();
    private final Consumer<ProjectStore> onOpen;

    public ProjectLauncher(Consumer<ProjectStore> onOpen) {
        this.onOpen = onOpen;
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

    private void openSelected() {
        String name = list.getSelectionModel().getSelectedItem();
        if (name == null) return;
        try {
            onOpen.accept(ProjectStore.open(base, name));
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Could not open project: " + e.getMessage()).show();
        }
    }

    private void createProject() {
        TextInputDialog d = new TextInputDialog("My Campaign");
        d.setTitle("New project");
        d.setHeaderText("Campaign name:");
        Optional<String> name = d.showAndWait().map(String::trim).filter(s -> !s.isEmpty());
        if (name.isEmpty()) return;
        try {
            Files.createDirectories(base);
            onOpen.accept(ProjectStore.create(base, name.get()));
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Could not create project: " + e.getMessage()).show();
        }
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
