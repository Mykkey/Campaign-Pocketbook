package dnd.ui;

import dnd.npc.CustomProfessions;
import dnd.npc.NpcData;
import dnd.npc.NpcData.Profession;
import dnd.rules.CardTemplates;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;

/**
 * The DM's own professions for the NPC generator: a name, a category, which card's stats suit them, and what such
 * a person is likely to know. They show up next to the built-in professions in every project.
 */
final class ProfessionsDialog {
    private ProfessionsDialog() { }

    /** @param onChanged called after anything is added, changed or removed */
    static void show(Window owner, Runnable onChanged) {
        CustomProfessions store = CustomProfessions.shared();

        ListView<Profession> list = new ListView<>();
        list.setPrefWidth(220);
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(Profession p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : p.name());
            }
        });
        Runnable reload = () -> list.setItems(FXCollections.observableArrayList(store.custom()));
        reload.run();

        TextField name = new TextField();
        name.setPromptText("e.g. Rat catcher");
        ComboBox<String> category = new ComboBox<>(FXCollections.observableArrayList(NpcData.categories()));
        category.setEditable(true);
        category.setValue("Custom");
        category.setMaxWidth(Double.MAX_VALUE);
        ComboBox<CardTemplates.Template> template = new ComboBox<>();
        List<CardTemplates.Template> templates = new ArrayList<>();
        for (CardTemplates.Template t : CardTemplates.list()) {
            if (!CardTemplates.BLANK_PLAYER.equals(t.id) && !CardTemplates.BLANK_MONSTER.equals(t.id)) templates.add(t);
        }
        template.setItems(FXCollections.observableArrayList(templates));
        template.setMaxWidth(Double.MAX_VALUE);
        template.setValue(templates.stream().filter(t -> "commoner".equals(t.id)).findFirst().orElse(templates.get(0)));
        TextArea knows = new TextArea();
        knows.setPromptText("What this kind of person might know or offer, one per line (at least a few)");
        knows.setWrapText(true);
        knows.setPrefRowCount(6);

        String[] editing = {null};
        Label message = new Label();
        message.setStyle("-fx-text-fill: -t-danger;");
        message.setWrapText(true);

        Runnable clear = () -> {
            editing[0] = null;
            list.getSelectionModel().clearSelection();
            name.clear();
            category.setValue("Custom");
            knows.clear();
            message.setText("");
        };
        list.getSelectionModel().selectedItemProperty().addListener((o, a, p) -> {
            if (p == null) return;
            editing[0] = p.name();
            name.setText(p.name());
            category.setValue(p.category());
            knows.setText(String.join("\n", p.knows()));
            for (CardTemplates.Template t : templates) if (t.id.equals(p.template())) template.setValue(t);
            message.setText("");
        });

        Button save = new Button("Save");
        save.setStyle("-fx-font-weight: bold;");
        save.setOnAction(e -> {
            List<String> hints = new ArrayList<>();
            for (String line : knows.getText().split("\\R")) if (!line.isBlank()) hints.add(line.trim());
            if (hints.size() < 1) {
                message.setText("Add at least one thing this person might know.");
                return;
            }
            try {
                String cat = category.getValue() == null || category.getValue().isBlank() ? "Custom" : category.getValue();
                store.put(editing[0], new Profession(name.getText(), cat, template.getValue().id, hints));
            } catch (IllegalArgumentException ex) {
                message.setText(ex.getMessage());
                return;
            }
            String saved = name.getText().trim();
            reload.run();
            for (Profession p : list.getItems()) if (p.name().equalsIgnoreCase(saved)) list.getSelectionModel().select(p);
            message.setText("");
            onChanged.run();
        });
        Button add = new Button("New");
        add.setOnAction(e -> clear.run());
        Button remove = new Button("Delete");
        remove.setOnAction(e -> {
            Profession p = list.getSelectionModel().getSelectedItem();
            if (p == null) return;
            store.remove(p.name());
            reload.run();
            clear.run();
            onChanged.run();
        });

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        ColumnConstraints c0 = new ColumnConstraints();
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().addAll(c0, c1);
        form.addRow(0, new Label("Name"), name);
        form.addRow(1, new Label("Category"), category);
        form.addRow(2, new Label("Stats like"), template);
        Label hint = new Label("Saved NPC cards of this job use that creature's stats. Built-in professions can't be edited, "
                + "but your own can reuse their categories.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 11px;");
        VBox right = new VBox(10, form, new Label("What they know"), knows, new HBox(8, save, add, remove), message, hint);
        right.setPadding(new Insets(0, 0, 0, 12));
        VBox.setVgrow(knows, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);

        Label listTitle = new Label("Your professions");
        listTitle.setStyle("-fx-font-weight: bold;");
        VBox left = new VBox(6, listTitle, list);
        VBox.setVgrow(list, Priority.ALWAYS);

        Dialog<Void> d = new Dialog<>();
        d.setTitle("Your professions");
        d.setHeaderText("Professions you add show up in the NPC generator in every project.");
        if (owner != null) d.initOwner(owner);
        d.getDialogPane().setContent(new HBox(left, right));
        d.getDialogPane().setPrefSize(720, 430);
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        d.showAndWait();
    }
}
