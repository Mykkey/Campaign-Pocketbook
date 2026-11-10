package dnd.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;

/** Write or edit a note for words or an object on a whiteboard. */
final class NoteDialog {
    private NoteDialog() { }

    /**
     * @return the new note text; an empty string means "remove the note"; empty Optional means cancelled
     */
    static Optional<String> show(String title, String current) {
        TextArea text = new TextArea(current == null ? "" : current);
        text.setPromptText("Your own note: a stat line, a rumour, what the NPC wants...");
        text.setWrapText(true);
        text.setPrefRowCount(6);
        Label hint = new Label("Supports **bold** and *italic*. It shows when you hover over the words.");
        hint.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 0.917em;");
        VBox box = new VBox(8, text, hint);
        box.setPadding(new Insets(6));
        box.setPrefWidth(400);

        Dialog<String> d = new Dialog<>();
        d.setTitle("Note");
        d.setHeaderText(title);
        Window owner = Window.getWindows().stream().filter(Window::isFocused).findFirst().orElse(null);
        if (owner != null) d.initOwner(owner);
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType remove = new ButtonType("Remove note", ButtonBar.ButtonData.LEFT);
        d.getDialogPane().getButtonTypes().addAll(save, remove, ButtonType.CANCEL);
        d.getDialogPane().setContent(box);
        javafx.application.Platform.runLater(text::requestFocus);
        d.setResultConverter(bt -> {
            if (bt == save) return text.getText().strip();
            if (bt == remove) return "";
            return null;
        });
        return d.showAndWait();
    }
}
