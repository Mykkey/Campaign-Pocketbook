package dnd.ui;

import dnd.model.Quest;
import dnd.storage.ProjectStore;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import static dnd.ui.FormKit.labelled;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A tab for editing one quest: a form on the left (with a checklist of steps) and a live summary on the right. */
public class QuestEditor extends BorderPane implements ItemView {
    private final Quest quest;
    private final ProjectStore store;
    private final Consumer<String> openItem;
    private final Supplier<Optional<String>> picker;
    private final Supplier<Optional<String>> cardPicker;
    private final QuestCardView preview;
    private final VBox stepRows = new VBox(6);

    /**
     * @param openItem opens a project item in a tab
     * @param picker   lets the user pick any project item (for "involves")
     * @param cardPicker lets the user pick one of their character cards (for "given by")
     */
    public QuestEditor(Quest quest, ProjectStore store, Consumer<String> onRename, Consumer<String> openItem,
                       Supplier<Optional<String>> picker, Supplier<Optional<String>> cardPicker) {
        this.quest = quest;
        this.store = store;
        this.openItem = openItem;
        this.picker = picker;
        this.cardPicker = cardPicker;
        this.preview = new QuestCardView(store);

        TextField name = new TextField(quest.name);
        name.setStyle("-fx-font-size: 1.500em; -fx-font-weight: bold;");
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

        Node giver = giverControl();
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
        stepHint.setStyle("-fx-font-size: 0.917em; -fx-text-fill: -t-text3;");
        rebuildSteps();

        RelatedItemsList related = new RelatedItemsList(store, quest.related, picker, openItem, this::changed,
                "Add a character, map, whiteboard or item this quest involves", 120);

        VBox form = new VBox(12,
                labelled("Name", name),
                new HBox(12, labelled("Status", status), labelled("Given by", giver), labelled("Reward", reward)),
                labelled("Summary", summary),
                labelled("Steps and clues", new VBox(6, stepHint, stepRows, addStep)),
                labelled("Involves", related),
                labelled("Notes", notes));
        form.setPadding(new Insets(14));
        ScrollPane formScroll = new ScrollPane(form);
        formScroll.setFitToWidth(true);

        ScrollPane previewScroll = new ScrollPane(preview);
        previewScroll.setPadding(new Insets(10));
        previewScroll.setFitToWidth(true);
        previewScroll.setStyle("-fx-background: -t-paperbg;");

        SplitPane split = new SplitPane(formScroll, previewScroll);
        Panels.remember(split, "editor.quest", 0.62);
        setCenter(split);
        preview.refresh(quest);
    }

    /**
     * The "Given by" control: a typed name, or a chosen character shown as a link (hover for the card, double-click
     * to open it). Choose... picks a character; Clear goes back to a typed name.
     */
    private Node giverControl() {
        TextField typed = new TextField(quest.giver == null ? "" : quest.giver);
        typed.setPromptText("A name, or choose a character");
        typed.textProperty().addListener((o, a, n) -> {
            if (quest.giverId == null) {
                quest.giver = n;
                changed();
            }
        });
        HBox.setHgrow(typed, Priority.ALWAYS);

        Label linked = new Label();
        linked.setMaxWidth(Double.MAX_VALUE);
        Runnable colour = () -> linked.setStyle("-fx-text-fill: " + (Theme.isDark() ? "#6aa5ff" : "#2563eb")
                + "; -fx-underline: true; -fx-cursor: hand; -fx-padding: 4 6 4 6;");
        colour.run();
        Theme.darkProperty().addListener((o, was, now) -> colour.run());
        HBox.setHgrow(linked, Priority.ALWAYS);
        Tooltip preview = IconButtons.tip("");
        preview.setShowDelay(javafx.util.Duration.millis(350));
        preview.setShowDuration(javafx.util.Duration.minutes(5));
        preview.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        preview.setOnShowing(e -> preview.setGraphic(ItemPreview.build(store, quest.giverId)));
        linked.setTooltip(preview);
        linked.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && quest.giverId != null && store.itemExists(quest.giverId)) openItem.accept(quest.giverId);
        });

        Button choose = new Button("Choose...");
        choose.setTooltip(IconButtons.tip("Pick one of your characters as the quest giver"));
        Button clear = new Button("Clear");
        clear.setTooltip(IconButtons.tip("Go back to typing a name"));
        HBox row = new HBox(6, typed, linked, choose, clear);
        row.setAlignment(Pos.CENTER_LEFT);

        Runnable refresh = () -> {
            boolean card = quest.giverId != null;
            typed.setVisible(!card);
            typed.setManaged(!card);
            linked.setVisible(card);
            linked.setManaged(card);
            clear.setVisible(card);
            clear.setManaged(card);
            if (card) {
                String name = store.itemNameOrNull(quest.giverId);
                linked.setText(name == null ? (quest.giver + " (card deleted)") : name + "   (double-click to open)");
            }
        };
        choose.setOnAction(e -> cardPicker.get().ifPresent(id -> {
            quest.giverId = id;
            String name = store.itemNameOrNull(id);
            if (name != null) quest.giver = name;
            changed();
            refresh.run();
        }));
        clear.setOnAction(e -> {
            quest.giverId = null;
            typed.setText(quest.giver == null ? "" : quest.giver);
            changed();
            refresh.run();
        });
        refresh.run();
        return row;
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
            remove.setTooltip(IconButtons.tip("Remove this step"));
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

    private TextField field(String value, java.util.function.Consumer<String> set, String prompt) {
        return FormKit.field(value, set, prompt, this::changed);
    }

    private TextArea area(String value, java.util.function.Consumer<String> set, String prompt, int rows) {
        return FormKit.area(value, set, prompt, rows, this::changed);
    }

    // ================================================================ ItemView

    @Override public String itemId() { return quest.id; }

    @Override public void undo() { }

    @Override public void redo() { }

    @Override public void activated() { }

    @Override public boolean selectTool(int index) { return false; }
}
