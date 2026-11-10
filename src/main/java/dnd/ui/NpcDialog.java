package dnd.ui;

import dnd.names.NameData;
import dnd.names.NameData.Race;
import dnd.names.NameGenerator.Gender;
import dnd.npc.Npc;
import dnd.npc.NpcData.AgeGroup;
import dnd.npc.NpcData.Attitude;
import dnd.npc.NpcData.Profession;
import dnd.npc.NpcGenerator;
import dnd.npc.NpcGenerator.Detail;
import dnd.npc.NpcGenerator.Spec;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Non-modal window for rolling a random NPC when the party talks to someone unplanned. Pin what you want (race,
 * profession, age, attitude...) and the rest is rolled; any single detail can be re-rolled. Nothing is saved or put
 * anywhere automatically: the buttons underneath are separate choices, e.g. only Add to board and no card made.
 */
public final class NpcDialog {
    private NpcDialog() { }

    private static final String RANDOM = "Random";
    private static final String ANY_CATEGORY = "Any category";

    // remembered for the rest of the session
    private static Race lastRace;
    private static Gender lastGender;
    private static String lastCategory;
    private static String lastProfession;
    private static AgeGroup lastAge;
    private static Attitude lastAttitude;

    private static Stage open;

    /**
     * @param addToBoard receives the NPC as text (null disables the button)
     * @param saveAsCard makes an NPC card in the project from the NPC (null disables the button)
     */
    public static void show(Window owner, Consumer<String> addToBoard, Consumer<Npc> saveAsCard) {
        if (open != null && open.isShowing()) {
            open.toFront();
            open.requestFocus();
            return;
        }
        NpcGenerator[] gen = {new NpcGenerator()};
        Npc[] current = {null};

        // ---- what to pin
        ComboBox<Object> race = combo(RANDOM, NameData.races().toArray());
        race.setValue(lastRace == null ? RANDOM : lastRace);
        ComboBox<Object> gender = combo(RANDOM, Gender.MASCULINE, Gender.FEMININE);
        gender.setValue(lastGender == null ? RANDOM : lastGender);
        ComboBox<Object> category = new ComboBox<>();
        ComboBox<Object> profession = new ComboBox<>();
        ComboBox<Object> age = combo(RANDOM, (Object[]) AgeGroup.values());
        age.setValue(lastAge == null ? RANDOM : lastAge);
        ComboBox<Object> attitude = combo(RANDOM, (Object[]) Attitude.values());
        attitude.setValue(lastAttitude == null ? RANDOM : lastAttitude);
        for (ComboBox<Object> c : List.of(race, gender, category, profession, age, attitude)) c.setMaxWidth(Double.MAX_VALUE);

        Runnable fillProfessions = () -> {
            Object cat = category.getValue();
            List<Object> names = new ArrayList<>();
            names.add(RANDOM);
            for (Profession p : gen[0].professions()) {
                if (cat == null || ANY_CATEGORY.equals(cat) || p.category().equals(cat)) names.add(p.name());
            }
            Object keep = profession.getValue();
            profession.setItems(FXCollections.observableArrayList(names));
            profession.setValue(keep != null && names.contains(keep) ? keep : RANDOM);
        };
        Runnable fillCategories = () -> {
            List<Object> cats = new ArrayList<>();
            cats.add(ANY_CATEGORY);
            for (Profession p : gen[0].professions()) if (!cats.contains(p.category())) cats.add(p.category());
            Object keep = category.getValue() != null ? category.getValue() : lastCategory;
            category.setItems(FXCollections.observableArrayList(cats));
            category.setValue(keep != null && cats.contains(keep) ? keep : ANY_CATEGORY);
            if (lastProfession != null && profession.getValue() == null) profession.setValue(lastProfession);
            fillProfessions.run();
        };
        profession.setValue(lastProfession);
        fillCategories.run();
        category.setOnAction(e -> fillProfessions.run());

        Function<Object, Object> pinned = v -> RANDOM.equals(v) || ANY_CATEGORY.equals(v) ? null : v;
        java.util.function.Supplier<Spec> spec = () -> new Spec(
                (Race) pinned.apply(race.getValue()), (Gender) pinned.apply(gender.getValue()),
                (String) pinned.apply(profession.getValue()), (String) pinned.apply(category.getValue()),
                (AgeGroup) pinned.apply(age.getValue()), (Attitude) pinned.apply(attitude.getValue()));

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        ColumnConstraints c0 = new ColumnConstraints();
        c0.setMinWidth(Region.USE_PREF_SIZE);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().addAll(c0, c1);
        form.addRow(0, new Label("Race"), race);
        form.addRow(1, new Label("Gender"), gender);
        form.addRow(2, new Label("Category"), category);
        form.addRow(3, new Label("Profession"), profession);
        form.addRow(4, new Label("Age"), age);
        form.addRow(5, new Label("Attitude"), attitude);

        Button roll = new Button("Roll");
        roll.setDefaultButton(true);
        roll.setStyle("-fx-font-weight: bold; -fx-font-size: 1.167em;");
        roll.setMaxWidth(Double.MAX_VALUE);
        Button edit = new Button("Edit professions...");
        edit.setMaxWidth(Double.MAX_VALUE);
        edit.setTooltip(IconButtons.tip("Add your own professions (and what such a person knows) to the list"));
        Label hint = new Label("Anything left on Random is rolled. Pins are kept when you re-roll one detail.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 0.917em;");
        VBox left = new VBox(10, form, roll, edit, hint);
        left.setPadding(new Insets(14));
        left.setPrefWidth(270);

        // ---- the result
        VBox rows = new VBox(8);
        rows.setPadding(new Insets(14));
        Label placeholder = new Label("Press Roll to make someone.");
        placeholder.setStyle("-fx-text-fill: -t-text3;");
        rows.getChildren().add(placeholder);
        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);

        Button saveCard = new Button("Save as NPC card");
        saveCard.setTooltip(IconButtons.tip("Makes a card in the project (in an \"NPCs\" folder) with stats to suit the job and this description in its notes"));
        Button addBoard = new Button("Add to board");
        addBoard.setTooltip(IconButtons.tip("Puts the description on the open whiteboard as a text note"));
        Button copy = new Button("Copy");
        saveCard.setDisable(true);
        addBoard.setDisable(true);
        copy.setDisable(true);
        HBox buttons = new HBox(8, addBoard, saveCard, copy);
        buttons.setAlignment(Pos.CENTER_LEFT);
        buttons.setPadding(new Insets(10, 14, 12, 14));
        Label choose = new Label("Choose what to do with them:");
        choose.setStyle("-fx-text-fill: -t-text3;");
        VBox bottom = new VBox(0, new Separator(), new VBox(4, choose, buttons));
        ((VBox) bottom.getChildren().get(1)).setPadding(new Insets(8, 14, 0, 14));

        Runnable show = () -> {
            rows.getChildren().clear();
            Npc n = current[0];
            if (n == null) {
                rows.getChildren().add(placeholder);
                return;
            }
            Label name = new Label(n.name);
            name.setStyle("-fx-font-size: 1.833em; -fx-font-weight: bold;");
            name.setWrapText(true);
            HBox nameRow = new HBox(8, name, reroll("Re-roll the name", () -> gen[0].reroll(n, Detail.NAME, spec.get()), rows));
            nameRow.setAlignment(Pos.CENTER_LEFT);
            Label summary = new Label(n.summary());
            summary.setStyle("-fx-text-fill: -t-text2; -fx-font-size: 1.083em;");
            rows.getChildren().addAll(nameRow, summary);
            addRow(rows, "Job", n.profession.name() + "  (" + n.profession.category() + ")", Detail.PROFESSION, gen, spec, current);
            addRow(rows, "Age", n.ageGroup.label + ", " + n.age, Detail.AGE, gen, spec, current);
            addRow(rows, "Attitude", n.attitude.label + ". " + n.opening, Detail.ATTITUDE, gen, spec, current);
            addRow(rows, "Appearance", String.join("; ", n.appearance), Detail.APPEARANCE, gen, spec, current);
            addRow(rows, "Personality", String.join("; ", n.personality), Detail.PERSONALITY, gen, spec, current);
            addRow(rows, "Mannerism", n.mannerism, Detail.MANNERISM, gen, spec, current);
            addRow(rows, "Voice", n.voice, Detail.VOICE, gen, spec, current);
            addRow(rows, "Wants", n.wants, Detail.WANTS, gen, spec, current);
            addRow(rows, "Secret", n.secret, Detail.SECRET, gen, spec, current);
            addRow(rows, "Might know", String.join("; ", n.knows), Detail.KNOWS, gen, spec, current);
            saveCard.setDisable(saveAsCard == null);
            addBoard.setDisable(addToBoard == null);
            copy.setDisable(false);
        };
        // addRow/reroll buttons need to redraw after a change; they call back through this holder
        rows.getProperties().put("redraw", show);

        roll.setOnAction(e -> {
            remember(race, gender, category, profession, age, attitude);
            current[0] = gen[0].generate(spec.get());
            show.run();
        });
        saveCard.setOnAction(e -> {
            if (current[0] != null && saveAsCard != null) saveAsCard.accept(current[0].copy());
        });
        addBoard.setOnAction(e -> {
            if (current[0] != null && addToBoard != null) addToBoard.accept(current[0].toText());
        });
        copy.setOnAction(e -> {
            if (current[0] == null) return;
            ClipboardContent c = new ClipboardContent();
            c.putString(current[0].toText());
            Clipboard.getSystemClipboard().setContent(c);
        });
        edit.setOnAction(e -> ProfessionsDialog.show(open, () -> {
            gen[0] = new NpcGenerator();
            fillCategories.run();
        }));

        BorderPane root = new BorderPane(scroll);
        root.setLeft(left);
        root.setBottom(bottom);

        Stage stage = new Stage();
        stage.setTitle("NPC Generator");
        if (owner != null) stage.initOwner(owner);
        Scene scene = new Scene(root, UiScale.px(860), UiScale.px(640));
        scene.setOnKeyPressed(e -> {
            if (dnd.keys.Shortcuts.matches(e, "dialog.close")) stage.close();
        });
        stage.setScene(scene);
        stage.setOnHidden(e -> open = null);
        open = stage;
        stage.show();
        roll.fire();
    }

    @SafeVarargs
    private static <T> ComboBox<Object> combo(String first, T... items) {
        List<Object> all = new ArrayList<>();
        all.add(first);
        all.addAll(List.of(items));
        ComboBox<Object> c = new ComboBox<>(FXCollections.observableArrayList(all));
        c.setValue(first);
        return c;
    }

    private static void remember(ComboBox<Object> race, ComboBox<Object> gender, ComboBox<Object> category,
                                 ComboBox<Object> profession, ComboBox<Object> age, ComboBox<Object> attitude) {
        lastRace = race.getValue() instanceof Race r ? r : null;
        lastGender = gender.getValue() instanceof Gender g ? g : null;
        lastCategory = category.getValue() instanceof String s && !ANY_CATEGORY.equals(s) ? s : null;
        lastProfession = profession.getValue() instanceof String s && !RANDOM.equals(s) ? s : null;
        lastAge = age.getValue() instanceof AgeGroup a ? a : null;
        lastAttitude = attitude.getValue() instanceof Attitude a ? a : null;
    }

    private static void redraw(VBox rows) {
        ((Runnable) rows.getProperties().get("redraw")).run();
    }

    private static Button reroll(String tip, Runnable action, VBox rows) {
        Button b = new Button("↻");
        b.setTooltip(IconButtons.tip(tip));
        b.setFocusTraversable(false);
        b.setOnAction(e -> {
            action.run();
            redraw(rows);
        });
        return b;
    }

    private static void addRow(VBox rows, String label, String value, Detail detail, NpcGenerator[] gen,
                               java.util.function.Supplier<Spec> spec, Npc[] current) {
        Label l = new Label(label);
        l.setMinWidth(92);
        l.setStyle("-fx-font-weight: bold; -fx-text-fill: -t-heading;");
        Label v = new Label(value);
        v.setWrapText(true);
        HBox.setHgrow(v, Priority.ALWAYS);
        v.setMaxWidth(Double.MAX_VALUE);
        Button b = reroll("Re-roll just this (" + label.toLowerCase() + ")", () -> gen[0].reroll(current[0], detail, spec.get()), rows);
        HBox row = new HBox(8, l, v, b);
        row.setAlignment(Pos.TOP_LEFT);
        row.setPadding(new Insets(6, 8, 6, 8));
        row.setStyle("-fx-background-color: -t-cream; -fx-background-radius: 4;");
        rows.getChildren().add(row);
    }

}
