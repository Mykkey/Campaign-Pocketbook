package dnd.ui;

import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/** The small form controls the card, spell, item, quest and session editors all use. */
final class FormKit {
    private FormKit() { }

    /** A text field that writes every edit to {@code set} and then runs {@code changed}. */
    static TextField field(String value, Consumer<String> set, String prompt, Runnable changed) {
        TextField t = new TextField(value == null ? "" : value);
        t.setPromptText(prompt);
        t.textProperty().addListener((o, a, n) -> {
            set.accept(n);
            changed.run();
        });
        return t;
    }

    /** A wrapping text area that writes every edit to {@code set} and then runs {@code changed}. */
    static TextArea area(String value, Consumer<String> set, String prompt, int rows, Runnable changed) {
        TextArea t = new TextArea(value == null ? "" : value);
        t.setPromptText(prompt);
        t.setWrapText(true);
        t.setPrefRowCount(rows);
        t.textProperty().addListener((o, a, n) -> {
            set.accept(n);
            changed.run();
        });
        return t;
    }

    /** A dropdown of common choices that also accepts anything typed in (the typed text is the value). */
    static ComboBox<String> options(String value, Consumer<String> set, List<String> common, String prompt, Runnable changed) {
        ComboBox<String> box = new ComboBox<>(FXCollections.observableArrayList(common));
        box.setEditable(true);
        box.setPromptText(prompt);
        box.setMaxWidth(Double.MAX_VALUE);
        box.setVisibleRowCount(12);
        box.getEditor().setText(value == null ? "" : value);
        box.getEditor().textProperty().addListener((o, a, n) -> {
            set.accept(n == null ? "" : n);
            changed.run();
        });
        return box;
    }

    /** A small grey caption above a control. */
    static VBox labelled(String label, Node n) {
        Label l = new Label(label);
        l.setStyle("-fx-font-size: 0.917em; -fx-text-fill: -t-text3;");
        VBox box = new VBox(3, l, n);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }
}
