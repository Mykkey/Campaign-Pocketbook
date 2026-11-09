package dnd.ui;

import dnd.rules.CardTemplates;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;

/** Asks for a card's name and what to start it from (blank player, blank creature or an example monster). */
public final class NewCardDialog {
    private NewCardDialog() { }

    public record Result(String name, String templateId) { }

    public static Optional<Result> show(Window owner) {
        Dialog<Result> d = new Dialog<>();
        d.setTitle("New card");
        d.setHeaderText("Create a character or creature card");
        if (owner != null) d.initOwner(owner);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField name = new TextField();
        name.setPromptText("Name (leave empty to use the template's name)");
        ListView<CardTemplates.Template> templates = new ListView<>();
        templates.getItems().setAll(CardTemplates.list());
        templates.getSelectionModel().select(1); // blank creature
        templates.setPrefSize(420, 280);
        VBox.setVgrow(templates, Priority.ALWAYS);

        VBox box = new VBox(8, new Label("Name"), name, new Label("Start from"), templates);
        box.setPadding(new Insets(10));
        d.getDialogPane().setContent(box);

        Node ok = d.getDialogPane().lookupButton(ButtonType.OK);
        templates.getSelectionModel().selectedItemProperty().addListener((o, a, n) -> ok.setDisable(n == null));
        templates.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && templates.getSelectionModel().getSelectedItem() != null) ((Button) ok).fire();
        });
        d.setResultConverter(bt -> {
            CardTemplates.Template t = templates.getSelectionModel().getSelectedItem();
            return bt == ButtonType.OK && t != null ? new Result(name.getText().trim(), t.id) : null;
        });
        javafx.application.Platform.runLater(name::requestFocus);
        return d.showAndWait();
    }
}
