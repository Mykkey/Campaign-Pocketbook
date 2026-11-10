package dnd.ui;

import dnd.model.MagicItem;
import dnd.storage.ProjectStore;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import static dnd.ui.FormKit.labelled;

import java.util.List;
import java.util.function.Consumer;

/** A tab for editing one magic item: a form on the left and a live item card on the right. */
public class MagicItemEditor extends BorderPane implements ItemView {
    private final MagicItem item;
    private final ProjectStore store;
    private final MagicItemCardView preview = new MagicItemCardView();

    public MagicItemEditor(MagicItem item, ProjectStore store, Consumer<String> onRename) {
        this.item = item;
        this.store = store;

        TextField name = new TextField(item.name);
        name.setStyle("-fx-font-size: 1.500em; -fx-font-weight: bold;");
        name.textProperty().addListener((o, a, n) -> {
            if (n.isBlank()) return;
            item.name = n.trim();
            changed();
            onRename.accept(item.name);
        });

        ComboBox<String> type = options(item.type, v -> item.type = v, MagicItem.TYPES, "Wondrous item, Weapon...");
        ComboBox<String> rarity = new ComboBox<>(FXCollections.observableArrayList(MagicItem.RARITIES));
        rarity.setEditable(true);
        rarity.setValue(item.rarity);
        rarity.valueProperty().addListener((o, a, n) -> {
            item.rarity = n == null ? "" : n;
            changed();
        });

        CheckBox attune = new CheckBox("Requires attunement");
        attune.setSelected(item.attunement);
        TextField attuneNote = field(item.attunementNote, x -> item.attunementNote = x, "by a spellcaster, by a cleric or paladin...");
        attuneNote.setDisable(!item.attunement);
        attune.setOnAction(e -> {
            item.attunement = attune.isSelected();
            attuneNote.setDisable(!item.attunement);
            changed();
        });
        HBox attuneRow = new HBox(10, attune, attuneNote);
        attuneRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(attuneNote, Priority.ALWAYS);

        Spinner<Integer> charges = new Spinner<>(0, 99, Math.max(0, item.charges));
        charges.setEditable(true);
        charges.setPrefWidth(90);
        charges.valueProperty().addListener((o, a, n) -> {
            item.charges = n == null ? 0 : n;
            changed();
        });
        ComboBox<String> recharge = options(item.recharge, v -> item.recharge = v, MagicItem.RECHARGES, "Dawn, Long rest...");

        TextArea description = area(item.description, x -> item.description = x, "What the item does. A blank line starts a new paragraph.", 9);
        TextField curse = field(item.curse, x -> item.curse = x, "A drawback or curse, if it has one");
        TextField value = field(item.value, x -> item.value = x, "e.g. 500 or 2,500-5,000");
        TextArea notes = area(item.notes, x -> item.notes = x, "Your own notes: who carries it, how you rule it...", 3);

        VBox form = new VBox(12,
                labelled("Name", name),
                new HBox(12, labelled("Type", type), labelled("Rarity", rarity)),
                attuneRow,
                labelled("Description", description),
                new HBox(12, labelled("Charges (0 = none)", charges), labelled("Regained", recharge)),
                labelled("Curse", curse),
                labelled("Value (gp)", value),
                labelled("Notes", notes));
        form.setPadding(new Insets(14));
        ScrollPane formScroll = new ScrollPane(form);
        formScroll.setFitToWidth(true);

        ScrollPane previewScroll = new ScrollPane(preview);
        previewScroll.setPadding(new Insets(10));
        previewScroll.setFitToWidth(true);
        previewScroll.setStyle("-fx-background: -t-paperbg;");

        SplitPane split = new SplitPane(formScroll, previewScroll);
        Panels.remember(split, "editor.magicitem", 0.6);
        setCenter(split);
        preview.refresh(item);
    }

    private void changed() {
        store.markMagicItemDirty(item);
        preview.refresh(item);
    }

    private TextField field(String value, java.util.function.Consumer<String> set, String prompt) {
        return FormKit.field(value, set, prompt, this::changed);
    }

    private TextArea area(String value, java.util.function.Consumer<String> set, String prompt, int rows) {
        return FormKit.area(value, set, prompt, rows, this::changed);
    }

    private ComboBox<String> options(String value, java.util.function.Consumer<String> set, java.util.List<String> common, String prompt) {
        return FormKit.options(value, set, common, prompt, this::changed);
    }

    // ================================================================ ItemView

    @Override public String itemId() { return item.id; }

    @Override public void undo() { }

    @Override public void redo() { }

    @Override public void activated() { }

    @Override public boolean selectTool(int index) { return false; }
}
