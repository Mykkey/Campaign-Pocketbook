package dnd.ui;

import dnd.library.CommonEnemies;
import dnd.model.Card;
import dnd.model.Combatant.Side;
import dnd.model.Encounter;
import dnd.rules.EncounterMath;
import dnd.rules.EncounterMath.Difficulty;
import dnd.storage.ProjectStore;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A tab for planning a fight: the creatures (from your cards), how many of each, and how hard that is for your
 * party. The party (size and level) is remembered with the project. Load the encounter from the battle setup.
 */
public class EncounterEditor extends BorderPane implements ItemView {
    private final Encounter enc;
    private final ProjectStore store;
    private final Consumer<String> openItem;

    private final VBox rows = new VBox(6);
    private final VBox summary = new VBox(6);

    public EncounterEditor(Encounter enc, ProjectStore store, Consumer<String> onRename, Consumer<String> openItem) {
        this.enc = enc;
        this.store = store;
        this.openItem = openItem;

        TextField name = new TextField(enc.name);
        name.setFont(Font.font("System", FontWeight.BOLD, UiScale.px(20)));
        name.textProperty().addListener((o, a, n) -> {
            if (n.isBlank()) return;
            enc.name = n.trim();
            store.markEncounterDirty(enc);
            onRename.accept(enc.name);
        });

        // common enemies: pick straight from a dropdown, no card needed in the project
        ComboBox<Card> common = new ComboBox<>();
        common.setPromptText("Add a common enemy...");
        common.setPrefWidth(220);
        common.setVisibleRowCount(14);
        common.setTooltip(new Tooltip("Goblins, orcs, wolves and your own homebrew creatures. They work in any project without making a card first."));
        common.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Card c) { return c == null ? "" : c.name; }

            @Override public Card fromString(String s) { return null; }
        });
        common.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(Card c, boolean empty) {
                super.updateItem(c, empty);
                setText(empty || c == null ? null : c.name + "   (CR " + (c.challenge == null || c.challenge.isBlank() ? "?" : c.challenge) + ")");
            }
        });
        common.setOnShowing(e -> common.getItems().setAll(CommonEnemies.shared().list())); // picks up edits and new homebrew
        common.getItems().setAll(CommonEnemies.shared().list());
        common.valueProperty().addListener((o, a, c) -> {
            if (c == null) return;
            addCommon(c);
            javafx.application.Platform.runLater(() -> common.setValue(null));
        });
        Button manage = new Button("Edit common enemies...");
        manage.setTooltip(new Tooltip("Change their stats, remove ones you never use, or add your own homebrew creatures"));
        manage.setOnAction(e -> CommonEnemiesDialog.show(getScene() == null ? null : getScene().getWindow(), store, null));

        Button add = new Button("+ From my cards...");
        add.setTooltip(new Tooltip("Pick a card from this project (player characters, NPCs, custom monsters)"));
        add.setOnAction(e -> addCreature());
        HBox addBar = new HBox(8, common, add, manage);
        addBar.setAlignment(Pos.CENTER_LEFT);

        TextArea notes = new TextArea(enc.notes == null ? "" : enc.notes);
        notes.setPromptText("Notes: where it happens, tactics, what the enemies want, how it ends...");
        notes.setWrapText(true);
        notes.setPrefRowCount(5);
        notes.textProperty().addListener((o, a, n) -> {
            enc.notes = n;
            store.markEncounterDirty(enc);
        });

        Label hint = new Label("To run this fight: open a battlemap, press Battle, then Load encounter.");
        hint.setStyle("-fx-text-fill: -t-text3;");
        hint.setWrapText(true);

        // ---- treasure for this fight
        ComboBox<dnd.loot.LootGenerator.Kind> lootKind = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(dnd.loot.LootGenerator.Kind.values()));
        lootKind.setValue(dnd.loot.LootGenerator.Kind.INDIVIDUAL);
        lootKind.setTooltip(new Tooltip("A creature's pockets: every enemy carries some coin at its own level. A hoard: one stash at the level of the strongest enemy."));
        Button rollLoot = new Button("Roll treasure");
        rollLoot.setTooltip(new Tooltip("Rolls treasure from the enemies' challenge ratings. Roll again for something different; you can edit the text."));
        Button copyLoot = new Button("Copy");
        Button toSession = new Button("Add to a session...");
        toSession.setTooltip(new Tooltip("Adds this treasure to the notes of one of your sessions"));
        TextArea lootText = new TextArea(enc.loot == null ? "" : enc.loot);
        lootText.setPromptText("Treasure the enemies carry or guard. Press Roll treasure, or write your own.");
        lootText.setWrapText(true);
        lootText.setPrefRowCount(6);
        lootText.textProperty().addListener((o, a, n) -> {
            enc.loot = n == null || n.isBlank() ? null : n;
            store.markEncounterDirty(enc);
        });
        rollLoot.setOnAction(e -> {
            List<dnd.loot.EncounterLoot.Creature> fight = new ArrayList<>();
            for (Encounter.Entry en : enc.entries) {
                Card c = cardOf(en);
                if (c != null && en.side == Side.ENEMY) fight.add(new dnd.loot.EncounterLoot.Creature(c.name, c.challenge, en.count));
            }
            lootText.setText(dnd.loot.EncounterLoot.roll(fight, lootKind.getValue(), new java.util.Random()).text());
        });
        copyLoot.setOnAction(e -> {
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString(lootText.getText());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
        });
        toSession.setOnAction(e -> addLootToSession(lootText.getText()));
        HBox lootBar = new HBox(8, lootKind, rollLoot, copyLoot, toSession);
        lootBar.setAlignment(Pos.CENTER_LEFT);

        VBox left = new VBox(10, name, hint, sectionLabel("Creatures"), rows, addBar, sectionLabel("Notes"), notes,
                sectionLabel("Treasure"), lootBar, lootText);
        left.setPadding(new Insets(14));
        ScrollPane leftScroll = new ScrollPane(left);
        leftScroll.setFitToWidth(true);

        summary.setPadding(new Insets(14));
        summary.setStyle("-fx-background-color: -t-slate;");
        ScrollPane rightScroll = new ScrollPane(summary);
        rightScroll.setFitToWidth(true);
        rightScroll.setStyle("-fx-background: -t-slate;");

        SplitPane split = new SplitPane(leftScroll, rightScroll);
        split.setDividerPositions(0.62);
        setCenter(split);
        rebuild();
    }

    private record SessionPick(dnd.model.Session session) {
        @Override
        public String toString() {
            return session.label();
        }
    }

    /** Appends the treasure to the notes of a session the user picks. */
    private void addLootToSession(String loot) {
        if (loot == null || loot.isBlank()) return;
        List<dnd.model.Session> sessions = store.allSessions();
        if (sessions.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "There are no sessions yet. Make a session first, then add the treasure to it.").show();
            return;
        }
        List<SessionPick> picks = new ArrayList<>();
        for (dnd.model.Session s : sessions) picks.add(new SessionPick(s));
        ChoiceDialog<SessionPick> d = new ChoiceDialog<>(picks.get(picks.size() - 1), picks);
        d.setTitle("Add treasure to a session");
        d.setHeaderText("Which session found this treasure?");
        if (getScene() != null) d.initOwner(getScene().getWindow());
        d.showAndWait().ifPresent(pick -> {
            dnd.model.Session s = pick.session();
            String block = "Treasure from " + enc.name + ":\n" + loot.trim();
            s.summary = s.summary == null || s.summary.isBlank() ? block : s.summary.stripTrailing() + "\n\n" + block;
            store.markSessionDirty(s);
            new Alert(Alert.AlertType.INFORMATION, "Added to " + s.label() + ". If that session is open in a tab, close and reopen it to see the change.").show();
        });
    }

    private static Label sectionLabel(String text) {
        Label l = new Label(text.toUpperCase());
        l.setStyle("-fx-font-size: 0.917em; -fx-font-weight: bold; -fx-text-fill: -t-heading; -fx-padding: 6 0 0 0;");
        return l;
    }

    // ================================================================ rows

    private void changed() {
        store.markEncounterDirty(enc);
        rebuildSummary();
    }

    private void rebuild() {
        rows.getChildren().clear();
        if (enc.entries.isEmpty()) {
            Label none = new Label("No creatures yet. Pick a common enemy from the dropdown below, or add one of your own cards.");
            none.setStyle("-fx-text-fill: -t-text3;");
            none.setWrapText(true);
            rows.getChildren().add(none);
        }
        for (Encounter.Entry en : new ArrayList<>(enc.entries)) rows.getChildren().add(row(en));
        rebuildSummary();
    }

    private HBox row(Encounter.Entry en) {
        Card card = cardOf(en);
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6));
        row.setStyle("-fx-background-color: -t-surface; -fx-border-color: -t-border; -fx-background-radius: 5; -fx-border-radius: 5;");

        Label name = new Label(card == null ? "(deleted creature)" : card.name + (en.libraryId != null ? "   (common enemy)" : ""));
        name.setStyle("-fx-font-weight: bold;" + (card == null ? " -fx-text-fill: -t-danger;" : ""));
        Label info = new Label(card == null ? "This creature was deleted, so it is left out of the totals."
                : "CR " + (card.challenge == null || card.challenge.isBlank() ? "?" : card.challenge)
                + " | " + EncounterMath.xpOf(card.xp, card.challenge) + " XP each | AC " + card.ac + " | HP " + card.hpMax);
        info.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 0.917em;");
        VBox who = new VBox(1, name, info);
        HBox.setHgrow(who, Priority.ALWAYS);

        Spinner<Integer> count = new Spinner<>(1, 50, Math.max(1, en.count));
        count.setEditable(true);
        count.setPrefWidth(72);
        count.valueProperty().addListener((o, a, n) -> {
            if (n == null) return;
            en.count = n;
            changed();
        });

        ComboBox<Side> side = new ComboBox<>(FXCollections.observableArrayList(Side.values()));
        side.setValue(en.side);
        side.setTooltip(new Tooltip("Enemies count towards the difficulty. Allies and players do not."));
        side.valueProperty().addListener((o, a, n) -> {
            if (n == null) return;
            en.side = n;
            changed();
        });

        CheckBox group = new CheckBox("Group");
        group.setSelected(en.group);
        group.setTooltip(new Tooltip("They share one initiative and take their turn together"));
        group.selectedProperty().addListener((o, a, n) -> {
            en.group = n;
            store.markEncounterDirty(enc);
        });

        Hyperlink open = new Hyperlink(en.libraryId != null ? "Edit stats" : "Card");
        open.setDisable(card == null);
        open.setOnAction(e -> {
            if (en.libraryId != null) CommonEnemiesDialog.show(getScene() == null ? null : getScene().getWindow(), store, en.libraryId);
            else openItem.accept(en.cardId);
        });

        Button remove = new Button("x");
        remove.setTooltip(new Tooltip("Remove from this encounter"));
        remove.setOnAction(e -> {
            enc.entries.remove(en);
            store.markEncounterDirty(enc);
            rebuild();
        });
        row.getChildren().addAll(who, count, side, group, open, remove);
        return row;
    }

    // ================================================================ adding

    /** The creature behind an entry: a project card, or a common enemy from the shared library. */
    private Card cardOf(Encounter.Entry en) {
        if (en.libraryId != null) return CommonEnemies.shared().get(en.libraryId);
        return en.cardId == null ? null : store.card(en.cardId);
    }

    private void addCommon(Card c) {
        for (Encounter.Entry en : enc.entries) {
            if (c.id.equals(en.libraryId)) {
                en.count++;
                store.markEncounterDirty(enc);
                rebuild();
                return;
            }
        }
        Side side = c.kind == Card.Kind.PLAYER ? Side.PLAYER : c.kind == Card.Kind.NPC ? Side.ALLY : Side.ENEMY;
        enc.entries.add(Encounter.Entry.common(c.id, 1, side, c.kind == Card.Kind.MONSTER));
        store.markEncounterDirty(enc);
        rebuild();
    }

    private void addCreature() {
        Optional<Card> picked = pickCard();
        if (picked.isEmpty()) return;
        Card c = picked.get();
        for (Encounter.Entry en : enc.entries) {
            if (c.id.equals(en.cardId)) { // already in the fight: one more of them
                en.count++;
                store.markEncounterDirty(enc);
                rebuild();
                return;
            }
        }
        Side side = c.kind == Card.Kind.PLAYER ? Side.PLAYER : c.kind == Card.Kind.NPC ? Side.ALLY : Side.ENEMY;
        enc.entries.add(new Encounter.Entry(c.id, 1, side, c.kind == Card.Kind.MONSTER));
        store.markEncounterDirty(enc);
        rebuild();
    }

    private Optional<Card> pickCard() {
        List<Card> cards = new ArrayList<>();
        for (String id : store.allCardIds()) {
            Card c = store.card(id);
            if (c != null) cards.add(c);
        }
        cards.sort(Comparator.comparing((Card c) -> c.kind).thenComparing(c -> c.name.toLowerCase()));
        FilteredList<Card> shown = new FilteredList<>(FXCollections.observableArrayList(cards), c -> true);

        Dialog<Card> d = new Dialog<>();
        d.setTitle("Add creature");
        d.setHeaderText("Pick a card to add to " + enc.name);
        if (getScene() != null) d.initOwner(getScene().getWindow());
        TextField filter = new TextField();
        filter.setPromptText("Search by name...");
        ListView<Card> list = new ListView<>(shown);
        list.setPrefSize(420, 340);
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(Card c, boolean empty) {
                super.updateItem(c, empty);
                setText(empty || c == null ? null : c.name + "   (" + c.kind.name().toLowerCase() + ", CR "
                        + (c.challenge == null || c.challenge.isBlank() ? "?" : c.challenge) + ")");
            }
        });
        filter.textProperty().addListener((o, a, n) -> {
            String q = n.trim().toLowerCase();
            shown.setPredicate(c -> q.isEmpty() || c.name.toLowerCase().contains(q));
        });
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && list.getSelectionModel().getSelectedItem() != null) {
                d.setResult(list.getSelectionModel().getSelectedItem());
                d.close();
            }
        });
        Label empty = new Label("There are no cards yet. Create some with + Card first.");
        d.getDialogPane().setContent(cards.isEmpty() ? new VBox(8, empty) : new VBox(8, filter, list));
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.getDialogPane().lookupButton(ButtonType.OK).disableProperty()
                .bind(list.getSelectionModel().selectedItemProperty().isNull());
        d.setResultConverter(b -> b == ButtonType.OK ? list.getSelectionModel().getSelectedItem() : null);
        return d.showAndWait();
    }

    // ================================================================ difficulty

    private void rebuildSummary() {
        summary.getChildren().clear();

        // ---- the party
        Label partyTitle = sectionLabel("Your party");
        Spinner<Integer> size = new Spinner<>(1, 12, store.project.partySize);
        size.setEditable(true);
        size.setPrefWidth(72);
        Spinner<Integer> level = new Spinner<>(1, 20, store.project.partyLevel);
        level.setEditable(true);
        level.setPrefWidth(72);
        size.valueProperty().addListener((o, a, n) -> {
            if (n == null) return;
            store.project.partySize = n;
            store.markProjectDirty();
            javafx.application.Platform.runLater(this::rebuildSummary);
        });
        level.valueProperty().addListener((o, a, n) -> {
            if (n == null) return;
            store.project.partyLevel = n;
            store.markProjectDirty();
            javafx.application.Platform.runLater(this::rebuildSummary);
        });
        HBox party = new HBox(8, new Label("Characters"), size, new Label("Level"), level);
        party.setAlignment(Pos.CENTER_LEFT);

        // ---- the maths: only enemies count
        int monsters = 0, totalXp = 0;
        for (Encounter.Entry en : enc.entries) {
            Card c = cardOf(en);
            if (c == null || en.side != Side.ENEMY) continue;
            monsters += Math.max(0, en.count);
            totalXp += Math.max(0, en.count) * EncounterMath.xpOf(c.xp, c.challenge);
        }
        int partySize = store.project.partySize, partyLevel = store.project.partyLevel;
        double mult = EncounterMath.multiplier(monsters, partySize);
        int adjusted = EncounterMath.adjustedXp(totalXp, monsters, partySize);
        Difficulty diff = EncounterMath.difficulty(adjusted, partyLevel, partySize);
        int[] t = EncounterMath.thresholds(partyLevel, partySize);

        Label verdict = new Label(monsters == 0 ? "No enemies yet" : diff.label);
        verdict.setFont(Font.font("System", FontWeight.BOLD, UiScale.px(28)));
        verdict.setStyle("-fx-text-fill: " + (monsters == 0 ? "-t-text3" : colour(diff)) + ";");
        Label forParty = new Label("for " + partySize + " characters of level " + partyLevel);
        forParty.setStyle("-fx-text-fill: -t-text2;");

        Label xp = new Label(monsters + " enemies, " + totalXp + " XP, x" + trim(mult) + " for the number of enemies = "
                + adjusted + " adjusted XP");
        xp.setWrapText(true);
        Label share = new Label(monsters == 0 || partySize == 0 ? "" : "About " + (totalXp / Math.max(1, partySize))
                + " XP for each character when it is over.");
        share.setStyle("-fx-text-fill: -t-text2;");

        // a bar against the deadly budget with the thresholds marked
        double max = Math.max(t[3] * 1.25, adjusted * 1.05);
        StackPane bar = new StackPane();
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPrefHeight(18);
        bar.setMaxHeight(18);
        bar.setStyle("-fx-background-color: -t-surface2; -fx-background-radius: 9;");
        Region fill = new Region();
        fill.setStyle("-fx-background-color: " + (monsters == 0 ? "-t-border" : colour(diff)) + "; -fx-background-radius: 9;");
        fill.setMaxHeight(18);
        bar.widthProperty().addListener((o, a, w) -> fill.setPrefWidth(w.doubleValue() * Math.min(1, adjusted / max)));
        fill.setMaxWidth(Region.USE_PREF_SIZE);
        fill.setPrefWidth(0);
        bar.getChildren().add(fill);

        GridPane budget = new GridPane();
        budget.setHgap(14);
        budget.setVgap(2);
        String[] names = {"Easy", "Medium", "Hard", "Deadly"};
        for (int i = 0; i < 4; i++) {
            Label n = new Label(names[i]);
            n.setStyle("-fx-text-fill: -t-text2;");
            Label v = new Label(t[i] + " XP");
            v.setStyle("-fx-font-weight: bold;");
            budget.add(n, 0, i);
            budget.add(v, 1, i);
        }
        Label note = new Label("Rated with the 5e Dungeon Master's Guide method. Only enemies count; allies and players "
                + "do not. It is a guide: terrain, tactics and resources change how a fight really goes.");
        note.setWrapText(true);
        note.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 0.917em;");

        summary.getChildren().addAll(partyTitle, party, sectionLabel("Difficulty"), verdict, forParty, bar, xp, share,
                sectionLabel("Your party's budget"), budget, note);
    }

    private static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static String colour(Difficulty d) {
        return switch (d) {
            case TRIVIAL -> "#64748b";
            case EASY -> "#16a34a";
            case MEDIUM -> "#ca8a04";
            case HARD -> "#ea580c";
            case DEADLY -> "#b91c1c";
        };
    }

    // ================================================================ ItemView

    @Override public String itemId() { return enc.id; }

    @Override public void undo() { }

    @Override public void redo() { }

    @Override public void activated() { }

    @Override public boolean selectTool(int index) { return false; }
}
