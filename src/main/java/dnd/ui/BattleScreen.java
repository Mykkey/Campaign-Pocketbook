package dnd.ui;

import dnd.battle.BattleEngine;
import dnd.battle.BattleEngine.Slot;
import dnd.model.Battle;
import dnd.model.BattleMap;
import dnd.model.Card;
import dnd.model.Combatant;
import dnd.storage.ProjectStore;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Battle mode: the screen the DM runs a fight from. It has two clearly different phases:
 * <ul>
 *   <li><b>Setup</b> (slate banner): add creatures and enter each initiative once, then Start battle.</li>
 *   <li><b>Running</b> (amber banner): the initiative strip along the bottom highlights whose turn it is, that
 *       creature's card is shown large, and clicking anyone else shows their card too.</li>
 * </ul>
 * The map stays in the middle so tokens can be moved and fog revealed during the fight.
 */
final class BattleScreen implements BattleContext {

    /** What the screen needs from the map editor around it. */
    interface Host {
        /** Leave the battle screen and go back to the normal map editor (the battle is kept). */
        void exit();

        /** Show or hide the normal map toolbar (fog, terrain, ...) under the banner. */
        void showMapTools(boolean show);

        void popOutPlayers();

        /** The project gained items (a card made from a common enemy): refresh the project tree. */
        void treeChanged();

        void openItem(String itemId);
    }

    private final BattleMap map;
    private final ProjectStore store;
    private final MapView view;
    private final Host host;
    private final Random random = new Random();
    private final MapToolState ts;
    private final HBox quickTools = new HBox(8);

    private final HBox banner = new HBox(10);
    private final BorderPane right = new BorderPane();
    private final InitiativeStrip strip;
    private final BattleSetupView setup;
    private final ScrollPane setupScroll = new ScrollPane();
    private final SplitPane cards = new SplitPane();
    private final ScrollPane activeScroll = new ScrollPane();
    private final ScrollPane inspectScroll = new ScrollPane();
    private String inspectedKey;
    private boolean toolsShown;

    BattleScreen(BattleMap map, ProjectStore store, MapView view, MapToolState ts, Host host) {
        this.ts = ts;
        this.map = map;
        this.store = store;
        this.view = view;
        this.host = host;
        this.strip = new InitiativeStrip(this);
        this.setup = new BattleSetupView(this, this::addCreatures, this::loadEncounter, this::startBattle);

        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setPadding(new Insets(8, 14, 8, 14));
        buildQuickTools();

        setupScroll.setContent(setup);
        setupScroll.setFitToWidth(true);
        activeScroll.setFitToWidth(true);
        inspectScroll.setFitToWidth(true);
        cards.setOrientation(Orientation.VERTICAL);
        right.setPrefWidth(500);
        right.setMinWidth(380);
        right.setStyle("-fx-border-color: -t-border; -fx-border-width: 0 0 0 1;");
        refresh();
    }

    Node banner() { return banner; }

    /** The strip of map tools you need mid-fight: move, ruler, area, pin, and whether the players see the ruler and area. */
    Node quickTools() { return quickTools; }

    private void buildQuickTools() {
        quickTools.setAlignment(Pos.CENTER_LEFT);
        quickTools.setPadding(new Insets(4, 14, 4, 14));
        quickTools.setStyle("-fx-background-color: -t-slate; -fx-border-color: -t-border; -fx-border-width: 0 0 1 0;");
        Label label = new Label("Map:");
        label.setStyle("-fx-font-weight: bold; -fx-text-fill: -t-text2;");
        quickTools.getChildren().add(label);

        ToggleGroup tools = new ToggleGroup();
        Object[][] defs = {
                {MapTool.SELECT, "Move", "Select and move tokens and pieces (key 1)"},
                {MapTool.RULER, "Ruler", "Measure a distance: drag on the map (key 6)"},
                {MapTool.TEMPLATE, "Area", "Lay a cone, circle, line or cube over the map (key 8)"},
                {MapTool.PIN, "Pin", "Drop a numbered note only you can see (key 9)"}};
        java.util.Map<MapTool, ToggleButton> buttons = new java.util.EnumMap<>(MapTool.class);
        for (Object[] d : defs) {
            MapTool tool = (MapTool) d[0];
            ToggleButton b = IconButtons.decorate(new ToggleButton(), tool.name().toLowerCase(), d[1] + ": " + d[2]);
            b.setToggleGroup(tools);
            b.setOnAction(e -> {
                ts.tool.set(b.isSelected() ? tool : MapTool.SELECT);
                view.requestFocus();
            });
            buttons.put(tool, b);
            quickTools.getChildren().add(b);
        }
        // follow the tool chosen anywhere else (number keys, the map toolbar)
        ts.tool.addListener((o, a, n) -> {
            ToggleButton b = buttons.get(n);
            if (b != null) b.setSelected(true);
            else tools.selectToggle(null);
        });
        ToggleButton current = buttons.get(ts.tool.get());
        if (current != null) current.setSelected(true);

        // the area's shape and size, only while the Area tool is in use
        ComboBox<dnd.model.AreaTemplate.Shape> shape = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(dnd.model.AreaTemplate.Shape.values()));
        shape.setValue(ts.templateShape.get());
        shape.setFocusTraversable(false);
        shape.valueProperty().addListener((o, a, n) -> {
            if (n != null) ts.templateShape.set(n);
        });
        ts.templateShape.addListener((o, a, n) -> shape.setValue(n));
        Spinner<Integer> feet = new Spinner<>(5, 300, ts.templateFeet.get(), 5);
        feet.setPrefWidth(78);
        feet.setTooltip(IconButtons.tip("Size in feet: radius of a circle, length of a cone or line, side of a cube"));
        feet.valueProperty().addListener((o, a, n) -> {
            if (n != null) ts.templateFeet.set(n);
        });
        ts.templateFeet.addListener((o, a, n) -> {
            if (feet.getValue() == null || feet.getValue().intValue() != n.intValue()) feet.getValueFactory().setValue(n.intValue());
        });
        HBox areaControls = new HBox(6, shape, feet, new Label("ft"));
        areaControls.setAlignment(Pos.CENTER_LEFT);
        areaControls.visibleProperty().bind(ts.tool.isEqualTo(MapTool.TEMPLATE));
        areaControls.managedProperty().bind(ts.tool.isEqualTo(MapTool.TEMPLATE));
        quickTools.getChildren().add(areaControls);

        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        // one switch for what the players see: the ruler and the area
        ToggleButton share = IconButtons.toggle("monitor", "Show to players: when on, the ruler and the area you lay on the map are also drawn in the player window");
        share.setSelected(ts.rulerShared.get() || ts.templateShared.get());
        share.setOnAction(e -> {
            boolean on = share.isSelected(); // read once: setting the first property updates the box through its listener
            ts.rulerShared.set(on);
            ts.templateShared.set(on);
        });
        ts.rulerShared.addListener((o, a, n) -> share.setSelected(n || ts.templateShared.get()));
        ts.templateShared.addListener((o, a, n) -> share.setSelected(n || ts.rulerShared.get()));
        Button clear = IconButtons.decorate(new Button(), "clear", "Clear: take the ruler and the area off the map (Esc)");
        clear.setOnAction(e -> view.clearMeasures());
        quickTools.getChildren().addAll(gap, share, clear);
    }

    Node rightPane() { return right; }

    Node strip() { return strip; }

    // ================================================================ BattleContext

    @Override public BattleMap map() { return map; }

    @Override public ProjectStore store() { return store; }

    @Override
    public Battle battle() {
        if (map.battle == null) map.battle = new Battle();
        return map.battle;
    }

    @Override
    public void changed() {
        view.battleChanged();
        refresh();
    }

    @Override
    public void inspect(String slotKey) {
        Battle b = battle();
        if (!b.active) return;
        if (slotKey == null || slotKey.equals(b.turnId)) {
            inspectedKey = null; // the current creature is already shown large
        } else {
            inspectedKey = slotKey.equals(inspectedKey) ? null : slotKey;
        }
        refresh();
    }

    @Override
    public void centerOn(String tokenId) {
        view.centerOnToken(tokenId);
    }

    @Override
    public void placeToken(Combatant c) {
        c.tokenId = null;
        view.addCombatantTokens(List.of(c));
        refresh();
    }

    @Override
    public void openCard(String cardId) {
        host.openItem(cardId);
    }

    @Override
    public void remove(Combatant c) {
        Battle b = battle();
        String tokenId = c.tokenId;
        BattleEngine.remove(b, c);
        if (tokenId != null && map.findToken(tokenId) != null) view.removeTokenById(tokenId);
        changed();
    }

    // ================================================================ rebuilding

    void refresh() {
        Battle b = battle();
        if (BattleEngine.relinkTokens(b, map) > 0) view.battleChanged(); // repairs saves where a creature lost its token
        boolean active = b.active;
        if (!active) inspectedKey = null;
        if (inspectedKey != null && BattleEngine.slots(b).stream().noneMatch(s -> s.key.equals(inspectedKey))) inspectedKey = null;

        buildBanner(b, active);
        if (active) buildCards(b);
        else {
            setup.refresh();
            right.setCenter(setupScroll);
        }
        strip.refresh(inspectedKey);
    }

    private void buildBanner(Battle b, boolean active) {
        banner.getChildren().clear();
        banner.setStyle(active
                ? "-fx-background-color: #f59e0b; -fx-border-color: #b45309; -fx-border-width: 0 0 3 0;"
                : "-fx-background-color: #334155; -fx-border-color: #0f172a; -fx-border-width: 0 0 3 0;");

        Label title = new Label(active ? "BATTLE  -  Round " + b.round : "BATTLE SETUP");
        title.setFont(Font.font("System", FontWeight.BOLD, 22));
        title.setStyle("-fx-text-fill: " + (active ? "#1f1300" : "white") + ";");
        banner.getChildren().add(title);

        if (active) {
            Slot now = BattleEngine.currentSlot(b);
            Label who = new Label(now == null ? "nobody can act" : "Now: " + now.name());
            who.setFont(Font.font("System", FontWeight.BOLD, 16));
            who.setStyle("-fx-text-fill: #1f1300; -fx-padding: 2 12 2 12; -fx-background-color: #fde68a; -fx-background-radius: 12;");
            banner.getChildren().add(who);
        }
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        banner.getChildren().add(sp);

        if (active) {
            Button back = IconButtons.decorate(new Button(), "back", "Back: go back one turn (P)");
            back.setOnAction(e -> previous());
            Button next = IconButtons.decorate(new Button(), "forward", "Next: end this turn and go to the next creature (N)");
            next.setStyle("-fx-background-color: -t-blue; -fx-border-color: -t-blue-border; -fx-border-radius: 6; -fx-padding: 8 22 8 22;");
            next.setOnAction(e -> next());
            Button add = IconButtons.decorate(new Button(), "add", "Add creature: bring someone into the fight (you will be asked for their initiative)");
            add.setOnAction(e -> addCreatures());
            Button end = IconButtons.decorate(new Button(), "stop", "End battle");
            end.setOnAction(e -> endBattle());
            banner.getChildren().addAll(back, next, add, end);
        }
        ToggleButton tools = IconButtons.toggle("sliders", "Map tools: show the map toolbar (fog, terrain, tokens, ...)");
        tools.setSelected(toolsShown);
        tools.setOnAction(e -> {
            toolsShown = tools.isSelected();
            host.showMapTools(toolsShown);
        });
        Button players = IconButtons.decorate(new Button(), "monitor", "Player view: open the player window");
        players.setOnAction(e -> host.popOutPlayers());
        Button exit = IconButtons.decorate(new Button(), "exit", active ? "Exit (keeps the battle)" : "Close the battle screen");
        exit.setOnAction(e -> host.exit());
        Button rules = IconButtons.decorate(new Button(), "rules", dnd.keys.Shortcuts.tip("Quick reference: conditions, cover, grappling and other rules", "global.rules"));
        rules.setOnAction(e -> RulesReferenceDialog.show(view.getScene() == null ? null : view.getScene().getWindow()));
        banner.getChildren().addAll(tools, players, rules, exit);
    }

    private void buildCards(Battle b) {
        double activeV = activeScroll.getVvalue(), inspectV = inspectScroll.getVvalue();
        Slot now = BattleEngine.currentSlot(b);
        if (now == null) {
            Label none = new Label("Everyone is down. Heal someone, or end the battle.");
            none.setPadding(new Insets(16));
            none.setWrapText(true);
            activeScroll.setContent(none);
        } else {
            activeScroll.setContent(new CombatantCard(this, new ArrayList<>(now.members), true));
        }

        Slot seen = null;
        if (inspectedKey != null) {
            for (Slot s : BattleEngine.slots(b)) if (s.key.equals(inspectedKey)) seen = s;
        }
        cards.getItems().clear();
        cards.getItems().add(activeScroll);
        if (seen != null) {
            Button close = IconButtons.decorate(new Button(), "target", "Back to the current turn");
            close.setOnAction(e -> inspect(null));
            Label hint = new Label("Viewing " + seen.name() + " (their turn is not up)");
            hint.setStyle("-fx-text-fill: -t-blue-text; -fx-font-weight: bold;");
            HBox top = new HBox(10, hint, close);
            top.setAlignment(Pos.CENTER_LEFT);
            top.setPadding(new Insets(6, 10, 6, 10));
            top.setStyle("-fx-background-color: -t-blue;");
            VBox box = new VBox(top, new CombatantCard(this, new ArrayList<>(seen.members), false));
            inspectScroll.setContent(box);
            cards.getItems().add(inspectScroll);
            cards.setDividerPositions(0.55);
        }
        right.setCenter(cards);
        Platform.runLater(() -> {
            activeScroll.setVvalue(activeV);
            inspectScroll.setVvalue(inspectV);
        });
    }

    // ================================================================ actions

    private void startBattle() {
        Battle b = battle();
        if (!BattleEngine.start(b)) return;
        inspectedKey = null;
        changed();
    }

    void next() {
        Battle b = battle();
        if (!b.active) return;
        BattleEngine.next(b);
        if (b.turnId != null && b.turnId.equals(inspectedKey)) inspectedKey = null;
        changed();
    }

    void previous() {
        Battle b = battle();
        if (!b.active) return;
        BattleEngine.previous(b);
        if (b.turnId != null && b.turnId.equals(inspectedKey)) inspectedKey = null;
        changed();
    }

    private void endBattle() {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                "End the battle? Everyone's HP and initiative are kept, so you can start it again from the setup screen.",
                ButtonType.OK, ButtonType.CANCEL);
        a.setHeaderText("End battle");
        if (view.getScene() != null) a.initOwner(view.getScene().getWindow());
        Optional<ButtonType> r = a.showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.OK) return;
        BattleEngine.end(battle());
        changed();
    }

    /** Adds creatures from cards. Before the fight they get no initiative yet; during it each needs one right away. */
    private void addCreatures() {
        Optional<List<AddCombatantsDialog.Pick>> res = AddCombatantsDialog.show(view.getScene() == null ? null : view.getScene().getWindow(), store);
        host.treeChanged(); // adding a common enemy may have created a card (and its folder) in the project
        if (res.isEmpty() || res.get().isEmpty()) return;
        Battle b = battle();
        List<Combatant> added = new ArrayList<>();
        for (AddCombatantsDialog.Pick p : res.get()) {
            if (!b.active) {
                added.addAll(BattleEngine.addFromCard(b, p.card(), p.count(), p.group(), p.side()));
            } else {
                int suggested = dnd.rules.Dice.parse("1d20").orElseThrow().roll(random) + p.card().initiative();
                Optional<Integer> init = askInitiative(p.card(), p.count(), suggested);
                if (init.isEmpty()) continue; // cancelled: leave them out
                added.addAll(BattleEngine.addMidBattle(b, p.card(), p.count(), p.group(), p.side(), init.get()));
            }
        }
        if (added.isEmpty()) return;
        view.addCombatantTokens(added);
        changed();
    }

    /** The project card for a common enemy (made on first use), or null if it was deleted from the library. */
    private Card commonCard(String libraryId) {
        Card lib = dnd.library.CommonEnemies.shared().get(libraryId);
        return lib == null ? null : store.useCommonEnemy(lib);
    }

    /** Adds every creature of a saved encounter (setup phase). */
    private void loadEncounter() {
        List<dnd.model.TreeNode> nodes = new ArrayList<>();
        store.project.root.collectEncounters(nodes);
        if (nodes.isEmpty()) return;
        ComboBox<dnd.model.TreeNode> choice = new ComboBox<>();
        choice.getItems().setAll(nodes);
        choice.setValue(nodes.get(0));
        choice.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(dnd.model.TreeNode n) { return n == null ? "" : n.name; }

            @Override public dnd.model.TreeNode fromString(String s) { return null; }
        });
        Dialog<dnd.model.TreeNode> d = new Dialog<>();
        d.setTitle("Load encounter");
        d.setHeaderText("Which encounter do you want to run?");
        d.getDialogPane().setContent(choice);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.setResultConverter(bt -> bt == ButtonType.OK ? choice.getValue() : null);
        if (view.getScene() != null) d.initOwner(view.getScene().getWindow());
        Optional<dnd.model.TreeNode> picked = d.showAndWait();
        if (picked.isEmpty()) return;
        dnd.model.Encounter enc = store.encounter(picked.get().id);
        if (enc == null) return;
        Battle b = battle();
        List<Combatant> added = new ArrayList<>();
        int skipped = 0;
        for (dnd.model.Encounter.Entry en : enc.entries) {
            Card card = en.libraryId != null ? commonCard(en.libraryId) : (en.cardId == null ? null : store.card(en.cardId));
            if (card == null || en.count < 1) {
                skipped++;
                continue;
            }
            added.addAll(BattleEngine.addFromCard(b, card, en.count, en.group, en.side));
        }
        host.treeChanged();
        if (!added.isEmpty()) {
            view.addCombatantTokens(added);
            changed();
        }
        if (skipped > 0) {
            Alert a = new Alert(Alert.AlertType.INFORMATION, skipped + " creature"
                    + (skipped == 1 ? " was" : "s were") + " left out because its card has been deleted.");
            if (view.getScene() != null) a.initOwner(view.getScene().getWindow());
            a.show();
        }
    }

    private Optional<Integer> askInitiative(Card card, int count, int suggested) {
        TextInputDialog d = new TextInputDialog(String.valueOf(suggested));
        d.setTitle("Initiative");
        d.setHeaderText("Initiative for " + card.name + (count > 1 ? " x" + count : ""));
        d.setContentText("A d20 + " + card.initiative() + " roll gave " + suggested + ". Change it if you rolled differently:");
        if (view.getScene() != null) d.initOwner(view.getScene().getWindow());
        return d.showAndWait().map(s -> {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        });
    }

    /** A token on the map was clicked: show that creature's card (during a running battle). */
    void inspectToken(String tokenId) {
        Battle b = battle();
        if (!b.active || tokenId == null) return;
        for (Combatant c : b.combatants) {
            if (tokenId.equals(c.tokenId)) {
                String key = BattleEngine.slotKey(c);
                if (key.equals(b.turnId)) inspectedKey = null;
                else inspectedKey = key;
                refresh();
                return;
            }
        }
    }
}
