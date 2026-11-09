package dnd.ui;

import dnd.model.Quest;
import dnd.storage.ProjectStore;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A tab for editing one quest: a form on the left (with a checklist of steps) and a live summary on the right. */
public class QuestEditor extends BorderPane implements ItemView {
    private final Quest quest;
    private final ProjectStore store;
    private final Consumer<String> openItem;
    private final Supplier<Optional<String>> picker;
    private final QuestCardView preview;
    private final VBox stepRows = new VBox(6);
    private final ListView<String> related = new ListView<>();

    /**
     * @param openItem opens a project item in a tab
     * @param picker   lets the user pick any project item (for "involves")
     */
    public QuestEditor(Quest quest, ProjectStore store, Consumer<String> onRename, Consumer<String> openItem,
                       Supplier<Optional<String>> picker) {
        this.quest = quest;
        this.store = store;
        this.openItem = openItem;
        this.picker = picker;
        this.preview = new QuestCardView(store);

        TextField name = new TextField(quest.name);
        name.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        name.textProperty().addListener((o, a, n) -> {
            if (n.isBlank()) return;
            quest.name = n.trim();
            changed();
            onRename.accept(quest.name);
        });

        ComboBox<Quest.Status> status = new ComboBox<>(FXCollections.observableArrayList(Quest.Status.values()));
        status.setValue(quest.status);
        status.setOnAction(e -> {
            if (status.getValue() != null) quest.status = status.getValue();
            changed();
        });

        TextField giver = field(quest.giver, x -> quest.giver = x, "Who asked the party? e.g. Mira the innkeeper");
        TextField reward = field(quest.reward, x -> quest.reward = x, "e.g. 200 gp and a favour");
        TextArea summary = area(quest.summary, x -> quest.summary = x, "What is going on, in a few lines", 4);
        TextArea notes = area(quest.notes, x -> quest.notes = x, "Your own notes: secrets, what happens if they ignore it...", 4);

        Button addStep = new Button("+ Add step");
        addStep.setOnAction(e -> {
            quest.steps.add(new Quest.Step("", false));
            rebuildSteps();
            changed();
            if (!stepRows.getChildren().isEmpty()) {
                Node last = stepRows.getChildren().get(stepRows.getChildren().size() - 1);
                for (Node n : ((HBox) last).getChildren()) if (n instanceof TextField tf) tf.requestFocus();
            }
        });
        Label stepHint = new Label("Steps and clues: tick them off as the party does or finds them.");
        stepHint.setStyle("-fx-font-size: 11px; -fx-text-fill: -t-text3;");
        rebuildSteps();

        related.setPrefHeight(120);
        related.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(String id, boolean empty) {
                super.updateItem(id, empty);
                if (empty || id == null) {
                    setText(null);
                    return;
                }
                String n = store.itemName(id);
                setText(n == null ? "(deleted item)" : n);
            }
        });
        related.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) openSelected();
        });
        rebuildRelated();
        Button addRel = new Button("Add...");
        addRel.setTooltip(new Tooltip("Add a character, map, whiteboard or item this quest involves"));
        addRel.setOnAction(e -> picker.get().ifPresent(id -> {
            if (!quest.related.contains(id)) {
                quest.related.add(id);
                rebuildRelated();
                changed();
            }
        }));
        Button openRel = new Button("Open");
        openRel.setOnAction(e -> openSelected());
        Button removeRel = new Button("Remove");
        removeRel.setOnAction(e -> {
            String sel = related.getSelectionModel().getSelectedItem();
            if (sel != null) {
                quest.related.remove(sel);
                rebuildRelated();
                changed();
            }
        });
        HBox relButtons = new HBox(6, addRel, openRel, removeRel);

        VBox form = new VBox(12,
                labelled("Name", name),
                new HBox(12, labelled("Status", status), labelled("Given by", giver), labelled("Reward", reward)),
                labelled("Summary", summary),
                labelled("Steps and clues", new VBox(6, stepHint, stepRows, addStep)),
                labelled("Involves", new VBox(6, related, relButtons)),
                labelled("Notes", notes));
        form.setPadding(new Insets(14));
        ScrollPane formScroll = new ScrollPane(form);
        formScroll.setFitToWidth(true);

        ScrollPane previewScroll = new ScrollPane(preview);
        previewScroll.setPadding(new Insets(10));
        previewScroll.setFitToWidth(true);
        previewScroll.setStyle("-fx-background: -t-paperbg;");

        SplitPane split = new SplitPane(formScroll, previewScroll);
        split.setDividerPositions(0.62);
        setCenter(split);
        preview.refresh(quest);
    }

    private void openSelected() {
        String sel = related.getSelectionModel().getSelectedItem();
        if (sel != null && store.itemExists(sel)) openItem.accept(sel);
    }

    private void rebuildRelated() {
        related.setItems(FXCollections.observableArrayList(quest.related));
    }

    private void rebuildSteps() {
        stepRows.getChildren().clear();
        for (Quest.Step step : quest.steps) {
            CheckBox done = new CheckBox();
            done.setSelected(step.done);
            done.setOnAction(e -> {
                step.done = done.isSelected();
                changed();
            });
            TextField text = new TextField(step.text);
            text.setPromptText("What needs doing, or what the party can find out");
            text.textProperty().addListener((o, a, n) -> {
                step.text = n;
                changed();
            });
            HBox.setHgrow(text, Priority.ALWAYS);
            Button remove = new Button("✕");
            remove.setTooltip(new Tooltip("Remove this step"));
            remove.setOnAction(e -> {
                quest.steps.remove(step);
                rebuildSteps();
                changed();
            });
            HBox row = new HBox(8, done, text, remove);
            row.setAlignment(Pos.CENTER_LEFT);
            stepRows.getChildren().add(row);
        }
    }

    private void changed() {
        store.markQuestDirty(quest);
        preview.refresh(quest);
    }

    private TextField field(String value, Consumer<String> set, String prompt) {
        TextField t = new TextField(value == null ? "" : value);
        t.setPromptText(prompt);
        t.textProperty().addListener((o, a, n) -> {
            set.accept(n);
            changed();
        });
        return t;
    }

    private TextArea area(String value, Consumer<String> set, String prompt, int rows) {
        TextArea t = new TextArea(value == null ? "" : value);
        t.setPromptText(prompt);
        t.setWrapText(true);
        t.setPrefRowCount(rows);
        t.textProperty().addListener((o, a, n) -> {
            set.accept(n);
            changed();
        });
        return t;
    }

    private static VBox labelled(String label, Node n) {
        Label l = new Label(label);
        l.setStyle("-fx-font-size: 11px; -fx-text-fill: -t-text3;");
        VBox box = new VBox(3, l, n);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    // ================================================================ ItemView

    @Override public String itemId() { return quest.id; }

    @Override public void undo() { }

    @Override public void redo() { }

    @Override public void activated() { }

    @Override public boolean selectTool(int index) { return false; }
}
