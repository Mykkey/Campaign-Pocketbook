package dnd.ui;

import dnd.model.Attack;
import dnd.model.Card;
import dnd.model.Feature;
import dnd.rules.Dice;
import dnd.storage.ProjectStore;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** A tab for editing one card: a form on the left and a live stat-block preview on the right. */
public class CardEditor extends BorderPane implements ItemView {
    private final Card card;
    private final ProjectStore store;
    private final Consumer<String> onRename;
    private final StatBlockView preview;
    private final ImageView portraitView = new ImageView();

    private final Runnable saver;
    private final boolean portraits;

    public CardEditor(Card card, ProjectStore store, Consumer<String> onRename) {
        this(card, store, onRename, () -> store.markCardDirty(card), true);
    }

    /**
     * @param saver     what to do after every edit (save the card somewhere)
     * @param portraits whether a portrait can be chosen (not for shared library creatures, whose images would not
     *                  exist in other projects)
     */
    public CardEditor(Card card, ProjectStore store, Consumer<String> onRename, Runnable saver, boolean portraits) {
        this.card = card;
        this.store = store;
        this.onRename = onRename;
        this.saver = saver;
        this.portraits = portraits;
        this.preview = new StatBlockView(store);

        VBox form = new VBox(14);
        form.setPadding(new Insets(14));
        form.getChildren().addAll(identitySection(), defenseSection(), abilitySection(), detailsSection(),
                new FeatureList("Traits", card.traits), attackSection(),
                new FeatureList("Other actions", card.actions), new FeatureList("Reactions", card.reactions),
                new FeatureList("Legendary actions", card.legendary), notesSection());
        if (portraits) form.getChildren().add(relationsSection()); // shared library creatures have no relationships
        ScrollPane formScroll = new ScrollPane(form);
        formScroll.setFitToWidth(true);

        ScrollPane previewScroll = new ScrollPane(preview);
        previewScroll.setPadding(new Insets(10));
        previewScroll.setFitToWidth(true);
        previewScroll.setStyle("-fx-background: -t-paperbg;");

        SplitPane split = new SplitPane(formScroll, previewScroll);
        Panels.remember(split, "editor.card", 0.6);
        setCenter(split);
        changedQuietly();
    }

    // ================================================================ helpers

    private void changed() {
        saver.run();
        preview.refresh(card);
    }

    private void changedQuietly() {
        preview.refresh(card);
    }

    private static Label heading(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 1.167em; -fx-font-weight: bold; -fx-text-fill: -t-heading;");
        return l;
    }

    private TextField field(String value, Consumer<String> set, String prompt) {
        TextField t = new TextField(value == null ? "" : value);
        if (prompt != null) t.setPromptText(prompt);
        t.textProperty().addListener((o, a, n) -> {
            set.accept(n);
            changed();
        });
        return t;
    }

    private Spinner<Integer> spinner(int min, int max, int value, IntConsumer set) {
        Spinner<Integer> s = new Spinner<>(min, max, Math.max(min, Math.min(max, value)));
        s.setEditable(true);
        s.setPrefWidth(84);
        s.valueProperty().addListener((o, a, n) -> {
            if (n != null) {
                set.accept(n);
                changed();
            }
        });
        // typed values are committed when the box loses focus
        s.getEditor().focusedProperty().addListener((o, was, is) -> {
            if (!is) commit(s, min, max);
        });
        s.getEditor().setOnAction(e -> commit(s, min, max));
        return s;
    }

    private static void commit(Spinner<Integer> s, int min, int max) {
        try {
            int v = Integer.parseInt(s.getEditor().getText().trim());
            s.getValueFactory().setValue(Math.max(min, Math.min(max, v)));
        } catch (NumberFormatException e) {
            s.getEditor().setText(String.valueOf(s.getValue()));
        }
    }

    private static HBox row(Node... nodes) {
        HBox h = new HBox(8, nodes);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private static VBox labelled(String label, Node n) {
        Label l = new Label(label);
        l.setStyle("-fx-font-size: 0.917em; -fx-text-fill: -t-text3;");
        return new VBox(2, l, n);
    }

    // ================================================================ sections

    private Node identitySection() {
        TextField name = new TextField(card.name);
        name.setStyle("-fx-font-size: 1.500em; -fx-font-weight: bold;");
        Runnable applyName = () -> {
            String n = name.getText().trim();
            if (n.isEmpty() || n.equals(card.name)) return;
            card.name = n;
            onRename.accept(n);
            changed();
        };
        name.setOnAction(e -> applyName.run());
        name.focusedProperty().addListener((o, was, is) -> {
            if (!is) applyName.run();
        });

        ComboBox<Card.Kind> kind = new ComboBox<>();
        kind.getItems().setAll(Card.Kind.values());
        kind.setValue(card.kind);
        kind.setOnAction(e -> {
            card.kind = kind.getValue();
            changed();
        });
        ComboBox<String> size = new ComboBox<>();
        size.getItems().setAll("Tiny", "Small", "Medium", "Large", "Huge", "Gargantuan");
        size.setValue(card.size);
        size.setOnAction(e -> {
            card.size = size.getValue();
            changed();
        });

        portraitView.setFitWidth(72);
        portraitView.setFitHeight(72);
        portraitView.setPreserveRatio(true);
        updatePortrait();
        Button choose = new Button("Portrait...");
        choose.setOnAction(e -> choosePortrait());
        Button clear = new Button("Clear");
        clear.setOnAction(e -> {
            card.portrait = null;
            updatePortrait();
            changed();
        });
        VBox portraitBox = new VBox(4, portraitView, row(choose, clear));
        portraitBox.setVisible(portraits);
        portraitBox.setManaged(portraits);

        TextField aliases = field(String.join(", ", card.aliases), v -> {
            card.aliases = new java.util.ArrayList<>();
            for (String part : v.split(",")) if (!part.isBlank()) card.aliases.add(part.trim());
        }, "e.g. Jasper, Jas, Captain Egan");
        aliases.setTooltip(IconButtons.tip("Other names this card goes by, separated by commas. On whiteboards, typing one of them "
                + "(at least 2 letters) offers to link or preview this card, just like its full name."));
        VBox left = new VBox(8, labelled("Name (press Enter to apply)", name),
                labelled("Also known as (comma separated, for name hints on whiteboards)", aliases),
                row(labelled("Kind", kind), labelled("Size", size),
                        labelled("Creature type", field(card.creatureType, v -> card.creatureType = v, "humanoid")),
                        labelled("Alignment", field(card.alignment, v -> card.alignment = v, "neutral evil"))));
        HBox.setHgrow(left, Priority.ALWAYS);
        return section("Identity", row(left, portraitBox));
    }

    private void updatePortrait() {
        if (card.portrait != null && Files.isRegularFile(store.imagePath(card.portrait))) {
            portraitView.setImage(new Image(store.imagePath(card.portrait).toUri().toString(), 72, 72, true, true));
        } else {
            portraitView.setImage(null);
        }
    }

    private void choosePortrait() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Choose a portrait");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        File f = fc.showOpenDialog(getScene().getWindow());
        if (f == null) return;
        try {
            card.portrait = store.importImage(f.toPath());
            updatePortrait();
            changed();
        } catch (IOException ex) {
            new Alert(Alert.AlertType.ERROR, "Could not import the image: " + ex.getMessage()).show();
        }
    }

    private Node defenseSection() {
        Spinner<Integer> ac = spinner(0, 40, card.ac, v -> card.ac = v);
        Spinner<Integer> hp = spinner(0, 9999, card.hpMax, v -> card.hpMax = v);
        Spinner<Integer> prof = spinner(0, 12, card.proficiency, v -> card.proficiency = v);
        Spinner<Integer> xp = spinner(0, 1_000_000, card.xp, v -> card.xp = v);

        TextField hitDice = field(card.hitDice, v -> card.hitDice = v, "2d6+2");
        hitDice.setPrefWidth(110);
        hitDice.textProperty().addListener((o, a, n) ->
                hitDice.setStyle(n == null || n.isBlank() || Dice.isValid(n) ? "" : "-fx-border-color: #d33; -fx-border-width: 1;"));

        Spinner<Integer> init = spinner(-10, 30, card.initiative(), v -> card.initiativeBonus = v);
        CheckBox custom = new CheckBox("Custom");
        custom.setSelected(card.initiativeBonus != null);
        init.setDisable(card.initiativeBonus == null);
        custom.setOnAction(e -> {
            init.setDisable(!custom.isSelected());
            if (custom.isSelected()) card.initiativeBonus = init.getValue();
            else {
                card.initiativeBonus = null;
                init.getValueFactory().setValue(Card.mod(card.dex));
            }
            changed();
        });

        return section("Defense and movement",
                row(labelled("Armor class", ac), labelled("AC note", field(card.acNote, v -> card.acNote = v, "leather armor")),
                        labelled("Hit points", hp), labelled("Hit dice", hitDice)),
                row(labelled("Speed", field(card.speed, v -> card.speed = v, "30 ft.")), labelled("Proficiency", prof),
                        labelled("Initiative bonus", row(init, custom)),
                        labelled("Challenge", field(card.challenge, v -> card.challenge = v, "1/4")), labelled("XP", xp)));
    }

    private Node abilitySection() {
        HBox boxes = new HBox(10);
        Spinner<?>[] spinners = new Spinner<?>[6];
        Label[] mods = new Label[6];
        for (int i = 0; i < 6; i++) {
            final int idx = i;
            mods[i] = new Label(Card.signed(Card.mod(card.score(i))));
            mods[i].setStyle("-fx-font-weight: bold;");
            Spinner<Integer> sp = spinner(1, 30, card.score(i), v -> {
                switch (idx) {
                    case 0 -> card.str = v;
                    case 1 -> card.dex = v;
                    case 2 -> card.con = v;
                    case 3 -> card.intel = v;
                    case 4 -> card.wis = v;
                    default -> card.cha = v;
                }
                mods[idx].setText(Card.signed(Card.mod(v)));
            });
            spinners[i] = sp;
            VBox col = new VBox(3, new Label(Card.ABILITIES[i]), sp, mods[i]);
            col.setAlignment(Pos.CENTER);
            boxes.getChildren().add(col);
        }
        return section("Ability scores", boxes);
    }

    private Node detailsSection() {
        return section("Proficiencies and traits of the creature",
                row(labelled("Saving throws", field(card.saves, v -> card.saves = v, "Dex +5, Wis +2")),
                        labelled("Skills", field(card.skills, v -> card.skills = v, "Stealth +6"))),
                row(labelled("Damage vulnerabilities", field(card.vulnerabilities, v -> card.vulnerabilities = v, null)),
                        labelled("Resistances", field(card.resistances, v -> card.resistances = v, null)),
                        labelled("Immunities", field(card.immunities, v -> card.immunities = v, null))),
                row(labelled("Condition immunities", field(card.conditionImmunities, v -> card.conditionImmunities = v, null)),
                        labelled("Senses", field(card.senses, v -> card.senses = v, "darkvision 60 ft., passive Perception 9"))),
                row(labelled("Languages", field(card.languages, v -> card.languages = v, "Common, Goblin"))));
    }

    private Node attackSection() {
        return new AttackList();
    }

    private Node notesSection() {
        TextArea notes = new TextArea(card.notes);
        notes.setWrapText(true);
        notes.setPrefRowCount(4);
        notes.textProperty().addListener((o, a, n) -> {
            card.notes = n;
            changed();
        });
        return section("Notes", notes);
    }

    // ================================================================ relationships

    private record CardRef(String id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    private List<CardRef> otherCards() {
        List<CardRef> out = new java.util.ArrayList<>();
        for (String id : store.allCardIds()) {
            dnd.model.Card c = store.card(id);
            if (c != null && !id.equals(card.id)) out.add(new CardRef(id, c.name));
        }
        out.sort(java.util.Comparator.comparing(r -> r.name().toLowerCase()));
        return out;
    }

    /** Who this card is related to. Only one of the two cards needs the relationship; the other side is worked out. */
    private Node relationsSection() {
        VBox rows = new VBox(6);
        VBox incoming = new VBox(3);
        Label hint = new Label("Enter a relationship on either card; the other card shows it too. Parents, partners and siblings "
                + "build the family tree (Relations... in the top bar).");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 0.917em;");
        Button add = new Button("+ Add relationship");

        Runnable[] refreshIncoming = new Runnable[1];
        Runnable[] rebuild = new Runnable[1];
        refreshIncoming[0] = () -> {
            incoming.getChildren().clear();
            java.util.Map<String, dnd.model.Card> all = new java.util.LinkedHashMap<>();
            for (String id : store.allCardIds()) {
                dnd.model.Card c = store.card(id);
                if (c != null) all.put(id, c);
            }
            dnd.relations.RelationGraph g = new dnd.relations.RelationGraph(all);
            for (dnd.relations.RelationGraph.Link l : g.linksOf(card.id)) {
                if (l.listedOnThis()) continue;
                Label lab = new Label(l.type().label + " " + g.nameOf(l.otherId()) + (l.note().isBlank() ? "" : "  (" + l.note() + ")")
                        + "   - set on their card");
                lab.setStyle("-fx-text-fill: -t-text2;");
                incoming.getChildren().add(lab);
            }
            incoming.setVisible(!incoming.getChildren().isEmpty());
            incoming.setManaged(!incoming.getChildren().isEmpty());
        };
        rebuild[0] = () -> {
            rows.getChildren().clear();
            List<CardRef> others = otherCards();
            add.setDisable(others.isEmpty());
            for (dnd.model.Relation r : new java.util.ArrayList<>(card.relations)) rows.getChildren().add(relationRow(r, others, rebuild[0], refreshIncoming[0]));
            if (rows.getChildren().isEmpty()) {
                Label none = new Label(others.isEmpty() ? "Create more cards to connect them." : "No relationships yet.");
                none.setStyle("-fx-text-fill: -t-text3;");
                rows.getChildren().add(none);
            }
            refreshIncoming[0].run();
        };
        add.setOnAction(e -> {
            List<CardRef> others = otherCards();
            if (others.isEmpty()) return;
            card.relations.add(new dnd.model.Relation(others.get(0).id(), dnd.model.RelationType.ALLY, ""));
            changed();
            rebuild[0].run();
        });
        rebuild[0].run();
        return section("Relationships", rows, add, incoming, hint);
    }

    private Node relationRow(dnd.model.Relation r, List<CardRef> others, Runnable rebuild, Runnable refreshIncoming) {
        ComboBox<dnd.model.RelationType> type = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(dnd.model.RelationType.values()));
        type.setValue(r.type);
        type.setPrefWidth(190);
        List<CardRef> choices = new java.util.ArrayList<>(others);
        CardRef current = choices.stream().filter(c -> c.id().equals(r.targetCardId)).findFirst().orElse(null);
        if (current == null) { // the other card was deleted
            current = new CardRef(r.targetCardId, "(deleted card)");
            choices.add(0, current);
        }
        ComboBox<CardRef> target = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(choices));
        target.setValue(current);
        target.setPrefWidth(190);
        TextField note = new TextField(r.note == null ? "" : r.note);
        note.setPromptText("note (optional)");
        HBox.setHgrow(note, Priority.ALWAYS);
        Button remove = new Button("x");
        remove.setTooltip(IconButtons.tip("Remove this relationship from this card"));
        type.valueProperty().addListener((o, a, n) -> {
            if (n == null) return;
            r.type = n;
            changed();
            refreshIncoming.run();
        });
        target.valueProperty().addListener((o, a, n) -> {
            if (n == null) return;
            r.targetCardId = n.id();
            changed();
            refreshIncoming.run();
        });
        note.textProperty().addListener((o, a, n) -> {
            r.note = n;
            changed();
        });
        remove.setOnAction(e -> {
            card.relations.remove(r);
            changed();
            rebuild.run();
        });
        HBox row = new HBox(8, type, target, note, remove);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static VBox section(String title, Node... content) {
        VBox box = new VBox(8);
        box.getChildren().add(heading(title));
        box.getChildren().addAll(content);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: -t-cream; -fx-border-color: -t-border; -fx-background-radius: 4; -fx-border-radius: 4;");
        for (Node n : content) {
            if (n instanceof HBox h) for (Node c : h.getChildren()) HBox.setHgrow(c, Priority.SOMETIMES);
        }
        return box;
    }

    // ================================================================ repeating rows

    /** A list of name + description entries (traits, reactions, ...) with add / remove. */
    private class FeatureList extends VBox {
        private final List<Feature> list;
        private final VBox rows = new VBox(8);

        FeatureList(String title, List<Feature> list) {
            super(8);
            this.list = list;
            Button add = new Button("+ Add");
            add.setOnAction(e -> {
                list.add(new Feature("", ""));
                rebuild();
                changed();
            });
            getChildren().addAll(row(heading(title), add), rows);
            setPadding(new Insets(10));
            setStyle("-fx-background-color: -t-cream; -fx-border-color: -t-border; -fx-background-radius: 4; -fx-border-radius: 4;");
            rebuild();
        }

        private void rebuild() {
            rows.getChildren().clear();
            for (Feature f : list) {
                TextField name = new TextField(f.name);
                name.setPromptText("Name");
                name.textProperty().addListener((o, a, n) -> {
                    f.name = n;
                    changed();
                });
                Button remove = new Button("Remove");
                remove.setOnAction(e -> {
                    list.remove(f);
                    rebuild();
                    changed();
                });
                HBox top = row(name, remove);
                HBox.setHgrow(name, Priority.ALWAYS);
                TextArea text = new TextArea(f.text);
                text.setPromptText("Description");
                text.setWrapText(true);
                text.setPrefRowCount(3);
                text.textProperty().addListener((o, a, n) -> {
                    f.text = n;
                    changed();
                });
                rows.getChildren().add(new VBox(4, top, text));
            }
        }
    }

    /** The creature's attacks (what the battle tracker shows on its turn). */
    private class AttackList extends VBox {
        private final VBox rows = new VBox(10);

        AttackList() {
            super(8);
            Button add = new Button("+ Add attack");
            add.setOnAction(e -> {
                card.attacks.add(new Attack());
                rebuild();
                changed();
            });
            Label hint = new Label("Shown in the battle tracker on this creature's turn. Damage uses dice like 1d6+2.");
            hint.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 0.917em;");
            getChildren().addAll(row(heading("Attacks"), add), hint, rows);
            setPadding(new Insets(10));
            setStyle("-fx-background-color: -t-cream; -fx-border-color: -t-border; -fx-background-radius: 4; -fx-border-radius: 4;");
            rebuild();
        }

        private void rebuild() {
            rows.getChildren().clear();
            for (Attack a : card.attacks) {
                TextField name = field(a.name, v -> a.name = v, "Scimitar");
                name.setPrefWidth(140);
                Spinner<Integer> hit = spinner(-10, 40, a.toHit, v -> a.toHit = v);
                Label avg = new Label();
                TextField dmg = new TextField(a.damage);
                dmg.setPromptText("1d6+2");
                dmg.setPrefWidth(110);
                Runnable showAvg = () -> {
                    int av = a.averageDamage();
                    avg.setText(av >= 0 ? "avg " + av : "invalid dice");
                    avg.setStyle(av >= 0 ? "-fx-text-fill: -t-text2;" : "-fx-text-fill: -t-danger;");
                };
                dmg.textProperty().addListener((o, x, n) -> {
                    a.damage = n;
                    showAvg.run();
                    changed();
                });
                showAvg.run();
                TextField type = field(a.damageType, v -> a.damageType = v, "slashing");
                type.setPrefWidth(100);
                TextField reach = field(a.reach, v -> a.reach = v, "5 ft. or 80/320 ft.");
                reach.setPrefWidth(120);
                TextField extra = field(a.text, v -> a.text = v, "Extra effect, e.g. DC 11 Strength save or knocked prone");
                HBox.setHgrow(extra, Priority.ALWAYS);
                Button remove = new Button("Remove");
                remove.setOnAction(e -> {
                    card.attacks.remove(a);
                    rebuild();
                    changed();
                });
                rows.getChildren().add(new VBox(4,
                        row(labelled("Name", name), labelled("To hit", hit), labelled("Damage", dmg), labelled("Type", type),
                                labelled("Reach / range", reach), avg, remove),
                        extra));
            }
            if (card.attacks.isEmpty()) {
                Label none = new Label("No attacks yet.");
                none.setStyle("-fx-text-fill: -t-text3;");
                rows.getChildren().add(none);
            }
        }
    }

    // ================================================================ ItemView

    @Override public String itemId() { return card.id; }

    @Override public void undo() { }

    @Override public void redo() { }

    @Override public void activated() { }

    @Override public boolean selectTool(int index) { return false; }
}
