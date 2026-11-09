package dnd.ui;

import dnd.model.Spell;
import dnd.storage.ProjectStore;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A tab for editing one spell card: a form on the left and a live spell card on the right. */
public class SpellEditor extends BorderPane implements ItemView {
    static final String[] SCHOOLS = {"Abjuration", "Conjuration", "Divination", "Enchantment", "Evocation", "Illusion", "Necromancy", "Transmutation"};
    private static final String[] LEVELS = {"Cantrip", "1st level", "2nd level", "3rd level", "4th level", "5th level", "6th level", "7th level", "8th level", "9th level"};

    private final Spell spell;
    private final ProjectStore store;
    private final SpellCardView preview = new SpellCardView();

    public SpellEditor(Spell spell, ProjectStore store, Consumer<String> onRename) {
        this.spell = spell;
        this.store = store;

        TextField name = new TextField(spell.name);
        name.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        name.textProperty().addListener((o, a, n) -> {
            if (n.isBlank()) return;
            spell.name = n.trim();
            changed();
            onRename.accept(spell.name);
        });

        ComboBox<String> level = new ComboBox<>(FXCollections.observableArrayList(LEVELS));
        level.getSelectionModel().select(Math.max(0, Math.min(9, spell.level)));
        level.setOnAction(e -> {
            spell.level = Math.max(0, level.getSelectionModel().getSelectedIndex());
            changed();
        });
        ComboBox<String> school = new ComboBox<>(FXCollections.observableArrayList(SCHOOLS));
        school.setEditable(true);
        school.setValue(spell.school);
        school.valueProperty().addListener((o, a, n) -> {
            spell.school = n == null ? "" : n;
            changed();
        });

        ComboBox<String> casting = options(spell.castingTime, v -> spell.castingTime = v, CASTING_TIMES, "1 action");
        ComboBox<String> range = options(spell.range, v -> spell.range = v, RANGES, "Self, Touch, 60 feet...");
        ComboBox<String> duration = options(spell.duration, v -> spell.duration = v, DURATIONS, "Instantaneous");

        CheckBox v = new CheckBox("V");
        CheckBox s = new CheckBox("S");
        CheckBox m = new CheckBox("M");
        List<String> have = new ArrayList<>();
        for (String c : (spell.components == null ? "" : spell.components).split("[,\\s]+")) have.add(c.trim().toUpperCase());
        v.setSelected(have.contains("V"));
        s.setSelected(have.contains("S"));
        m.setSelected(have.contains("M"));
        TextField material = field(spell.material, x -> spell.material = x, "what the M component is, e.g. a pinch of sulfur");
        Runnable comps = () -> {
            List<String> parts = new ArrayList<>();
            if (v.isSelected()) parts.add("V");
            if (s.isSelected()) parts.add("S");
            if (m.isSelected()) parts.add("M");
            spell.components = String.join(", ", parts);
            material.setDisable(!m.isSelected());
            changed();
        };
        v.setOnAction(e -> comps.run());
        s.setOnAction(e -> comps.run());
        m.setOnAction(e -> comps.run());
        material.setDisable(!m.isSelected());
        HBox compRow = new HBox(10, v, s, m, material);
        compRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(material, Priority.ALWAYS);

        CheckBox conc = new CheckBox("Concentration");
        conc.setSelected(spell.concentration);
        conc.setOnAction(e -> {
            spell.concentration = conc.isSelected();
            changed();
        });
        CheckBox ritual = new CheckBox("Ritual");
        ritual.setSelected(spell.ritual);
        ritual.setOnAction(e -> {
            spell.ritual = ritual.isSelected();
            changed();
        });
        TextField classes = field(String.join(", ", spell.classes), x -> {
            spell.classes = new ArrayList<>();
            for (String c : x.split(",")) if (!c.isBlank()) spell.classes.add(c.trim());
        }, "Sorcerer, Wizard");

        TextArea description = area(spell.description, x -> spell.description = x, "What the spell does. A blank line starts a new paragraph.", 9);
        TextArea higher = area(spell.higherLevel, x -> spell.higherLevel = x, "What changes when cast with a higher-level slot", 3);
        TextArea notes = area(spell.notes, x -> spell.notes = x, "Your own notes: how you rule it, who has it, a house rule...", 3);

        VBox form = new VBox(12,
                labelled("Name", name),
                new HBox(12, labelled("Level", level), labelled("School", school), labelled("Casting time", casting)),
                new HBox(12, labelled("Range / target", range), labelled("Duration", duration)),
                labelled("Components", compRow),
                new HBox(16, conc, ritual),
                labelled("Classes", classes),
                labelled("Description", description),
                labelled("At higher levels", higher),
                labelled("Notes", notes));
        form.setPadding(new Insets(14));
        ScrollPane formScroll = new ScrollPane(form);
        formScroll.setFitToWidth(true);

        ScrollPane previewScroll = new ScrollPane(preview);
        previewScroll.setPadding(new Insets(10));
        previewScroll.setFitToWidth(true);
        previewScroll.setStyle("-fx-background: -t-paperbg;");

        SplitPane split = new SplitPane(formScroll, previewScroll);
        split.setDividerPositions(0.6);
        setCenter(split);
        preview.refresh(spell);
    }

    private void changed() {
        store.markSpellDirty(spell);
        preview.refresh(spell);
    }

    static final List<String> RANGES = List.of("Self", "Self (5-foot radius)", "Self (10-foot radius)", "Self (15-foot cone)",
            "Self (30-foot cone)", "Self (30-foot line)", "Self (60-foot line)", "Touch", "Sight", "5 feet", "10 feet", "15 feet", "30 feet",
            "60 feet", "90 feet", "100 feet", "120 feet", "150 feet", "300 feet", "500 feet", "1 mile", "Unlimited", "Special");
    static final List<String> CASTING_TIMES = List.of("1 action", "1 bonus action", "1 reaction", "1 minute", "10 minutes", "1 hour",
            "8 hours", "12 hours", "24 hours");
    static final List<String> DURATIONS = List.of("Instantaneous", "1 round", "1 minute", "10 minutes", "1 hour", "8 hours", "24 hours",
            "7 days", "10 days", "Until dispelled", "Until dispelled or triggered", "Special");

    /** A dropdown of common choices that also accepts anything typed in (a custom value). */
    private ComboBox<String> options(String value, Consumer<String> set, List<String> common, String prompt) {
        ComboBox<String> box = new ComboBox<>(FXCollections.observableArrayList(common));
        box.setEditable(true);
        box.setPromptText(prompt);
        box.setMaxWidth(Double.MAX_VALUE);
        box.setVisibleRowCount(12);
        box.getEditor().setText(value == null ? "" : value);
        // the editor text is the value, whether it was picked from the list or typed
        box.getEditor().textProperty().addListener((o, a, n) -> {
            set.accept(n == null ? "" : n);
            changed();
        });
        return box;
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

    @Override public String itemId() { return spell.id; }

    @Override public void undo() { }

    @Override public void redo() { }

    @Override public void activated() { }

    @Override public boolean selectTool(int index) { return false; }
}