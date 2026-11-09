package dnd.ui;

import dnd.battle.BattleEngine;
import dnd.battle.BattleEngine.Slot;
import dnd.model.Attack;
import dnd.model.Battle;
import dnd.model.BattleMap;
import dnd.model.Card;
import dnd.model.Combatant;
import dnd.model.Combatant.Side;
import dnd.model.Feature;
import dnd.storage.ProjectStore;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.*;

/**
 * The initiative tracker, docked beside a battlemap. The DM adds creatures from cards, types each initiative,
 * and steps through the turns. It tracks HP, conditions and groups, and shows the attacks of whoever's turn it is.
 * Dice are rolled at the table; this only keeps the state.
 */
public class BattlePanel extends VBox {

    /** What the panel needs from the map editor around it. */
    public interface Host {
        /** Battle state changed: save the map and redraw it. */
        void changed();

        /** New creatures were added; put tokens for them on the map. */
        void addTokens(List<Combatant> added);

        void removeToken(String tokenId);

        void centerOn(String tokenId);

        void openCard(String cardId);
    }

    private static final String[] CONDITIONS = {"Blinded", "Charmed", "Concentrating", "Deafened", "Exhausted", "Frightened",
            "Grappled", "Incapacitated", "Invisible", "Paralyzed", "Petrified", "Poisoned", "Prone", "Restrained",
            "Stunned", "Unconscious"};

    private final BattleMap map;
    private final ProjectStore store;
    private final Host host;
    private final Random random = new Random();

    private final VBox header = new VBox(6);
    private final VBox turnCard = new VBox(4);
    private final VBox list = new VBox(6);
    private final TextArea log = new TextArea();
    private final Set<String> expanded = new HashSet<>();
    private final Set<String> ticked = new LinkedHashSet<>();

    public BattlePanel(BattleMap map, ProjectStore store, Host host) {
        this.map = map;
        this.store = store;
        this.host = host;
        setSpacing(8);
        setPadding(new Insets(8));
        setPrefWidth(410);
        setMinWidth(300);
        setStyle("-fx-background-color: #f7f5ef; -fx-border-color: #d9d4c4; -fx-border-width: 0 0 0 1;");

        Label title = new Label("Battle");
        title.setFont(Font.font("System", FontWeight.BOLD, 18));

        ScrollPane listScroll = new ScrollPane(list);
        listScroll.setFitToWidth(true);
        listScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(listScroll, Priority.ALWAYS);
        listScroll.setStyle("-fx-background-color: transparent;");

        log.setEditable(false);
        log.setWrapText(true);
        log.setPrefRowCount(5);
        TitledPane logPane = new TitledPane("Log", log);
        logPane.setExpanded(false);

        getChildren().addAll(title, header, turnCard, listScroll, logPane);
        refresh();
    }

    private Battle battle() {
        if (map.battle == null) map.battle = new Battle();
        return map.battle;
    }

    private void changed() {
        host.changed();
        refresh();
    }

    // ================================================================ rebuild

    public void refresh() {
        buildHeader();
        buildTurnCard();
        buildList();
        Battle b = map.battle;
        log.setText(b == null ? "" : String.join("\n", b.log.subList(Math.max(0, b.log.size() - 60), b.log.size())));
        log.positionCaret(log.getLength());
    }

    private void buildHeader() {
        header.getChildren().clear();
        Battle b = map.battle;
        boolean active = b != null && b.active;
        boolean hasAny = b != null && !b.combatants.isEmpty();

        Button add = new Button("+ Add...");
        add.setTooltip(new Tooltip("Bring players and monsters from your cards into the fight"));
        add.setOnAction(e -> addCombatants());
        Button roll = new Button("Roll enemy initiative");
        roll.setTooltip(new Tooltip("Optional: rolls d20 + bonus for enemies and allies (one roll per group). Players are typed in."));
        roll.setDisable(!hasAny);
        roll.setOnAction(e -> {
            BattleEngine.rollInitiative(battle(), Side.ENEMY, random);
            BattleEngine.rollInitiative(battle(), Side.ALLY, random);
            changed();
        });
        HBox top = new HBox(6, add, roll);
        top.setAlignment(Pos.CENTER_LEFT);

        HBox flow = new HBox(6);
        flow.setAlignment(Pos.CENTER_LEFT);
        if (!active) {
            Button start = new Button("Start battle");
            start.setStyle("-fx-font-weight: bold;");
            start.setDisable(!hasAny);
            start.setOnAction(e -> {
                BattleEngine.start(battle());
                changed();
            });
            flow.getChildren().add(start);
            if (hasAny) {
                Button clear = new Button("Clear all");
                clear.setTooltip(new Tooltip("Remove every creature (and its token) from the battle"));
                clear.setOnAction(e -> clearAll());
                flow.getChildren().add(clear);
            }
        } else {
            Label round = new Label("Round " + b.round);
            round.setFont(Font.font("System", FontWeight.BOLD, 15));
            Button back = new Button("< Back");
            back.setOnAction(e -> {
                BattleEngine.previous(battle());
                changed();
            });
            Button next = new Button("Next >");
            next.setStyle("-fx-font-weight: bold; -fx-base: #fbbf24;");
            next.setTooltip(new Tooltip("End this turn and go to the next creature (or group)"));
            next.setDefaultButton(false);
            next.setOnAction(e -> {
                BattleEngine.next(battle());
                changed();
            });
            Button end = new Button("End battle");
            end.setOnAction(e -> {
                BattleEngine.end(battle());
                changed();
            });
            flow.getChildren().addAll(round, back, next, end);
        }
        header.getChildren().addAll(top, flow);

        if (ticked.size() >= 2) {
            Button group = new Button("Group " + ticked.size() + " ticked creatures");
            group.setTooltip(new Tooltip("They will share one initiative and take their turn together"));
            group.setOnAction(e -> {
                List<Combatant> picked = new ArrayList<>();
                for (Combatant c : battle().combatants) if (ticked.contains(c.id)) picked.add(c);
                BattleEngine.group(battle(), picked);
                ticked.clear();
                changed();
            });
            header.getChildren().add(group);
        }
    }

    // ---------------------------------------------------------------- current turn

    private void buildTurnCard() {
        turnCard.getChildren().clear();
        Battle b = map.battle;
        if (b == null || !b.active) {
            turnCard.setStyle("");
            return;
        }
        Slot slot = BattleEngine.currentSlot(b);
        turnCard.setPadding(new Insets(8));
        turnCard.setStyle("-fx-background-color: #fff7d6; -fx-border-color: #e0b84a; -fx-background-radius: 6; -fx-border-radius: 6;");
        if (slot == null) {
            turnCard.getChildren().add(new Label("Nobody is able to act. Everyone is down."));
            return;
        }
        Label who = new Label("Turn: " + slot.name());
        who.setFont(Font.font("System", FontWeight.BOLD, 15));
        turnCard.getChildren().add(who);

        // one block per distinct card among the creatures that can still act
        Map<String, List<Combatant>> byCard = new LinkedHashMap<>();
        for (Combatant c : slot.members) {
            if (c.defeated) continue;
            byCard.computeIfAbsent(c.cardId == null ? "" : c.cardId, k -> new ArrayList<>()).add(c);
        }
        if (byCard.isEmpty()) byCard.put("", new ArrayList<>());
        for (Map.Entry<String, List<Combatant>> en : byCard.entrySet()) {
            Card card = en.getKey().isEmpty() ? null : store.card(en.getKey());
            List<Combatant> who2 = en.getValue();
            String name = card != null ? card.name : (who2.isEmpty() ? "?" : who2.get(0).name);
            Label head = new Label(name + (who2.size() > 1 ? "  x" + who2.size() : ""));
            head.setStyle("-fx-font-weight: bold;");
            turnCard.getChildren().add(head);
            if (card == null) {
                Label none = new Label("No card is linked to this creature, so there is no attack list.");
                none.setStyle("-fx-text-fill: #777; -fx-font-size: 11px;");
                none.setWrapText(true);
                turnCard.getChildren().add(none);
                continue;
            }
            if (card.attacks.isEmpty()) {
                Label none = new Label("This card has no attacks listed.");
                none.setStyle("-fx-text-fill: #777; -fx-font-size: 11px;");
                turnCard.getChildren().add(none);
            }
            for (Attack a : card.attacks) {
                Label l = new Label(a.name + ": " + a.summary());
                l.setWrapText(true);
                l.setMaxWidth(340);
                turnCard.getChildren().add(l);
            }
            List<String> traits = new ArrayList<>();
            for (Feature f : card.traits) if (!f.name.isBlank()) traits.add(f.name);
            for (Feature f : card.actions) if (!f.name.isBlank()) traits.add(f.name);
            if (!traits.isEmpty()) {
                Label t = new Label("Also: " + String.join(", ", traits));
                t.setStyle("-fx-text-fill: #555; -fx-font-style: italic; -fx-font-size: 11px;");
                t.setWrapText(true);
                t.setMaxWidth(340);
                turnCard.getChildren().add(t);
            }
            Hyperlink open = new Hyperlink("Open " + card.name + " card");
            open.setOnAction(e -> host.openCard(card.id));
            turnCard.getChildren().add(open);
        }
    }

    // ---------------------------------------------------------------- the list

    private void buildList() {
        list.getChildren().clear();
        Battle b = map.battle;
        if (b == null || b.combatants.isEmpty()) {
            Label hint = new Label("No one is in the battle yet.\nPress + Add... to bring in players and monsters from your cards.");
            hint.setStyle("-fx-text-fill: #888;");
            hint.setWrapText(true);
            list.getChildren().add(hint);
            return;
        }
        for (Slot s : BattleEngine.slots(b)) list.getChildren().add(slotRow(b, s));
    }

    private Node slotRow(Battle b, Slot slot) {
        boolean current = b.active && slot.key.equals(b.turnId);
        VBox box = new VBox(4);
        box.setPadding(new Insets(6));
        box.setStyle("-fx-background-color: " + (current ? "#fff3bf" : "white") + "; -fx-border-color: "
                + (current ? "#f59e0b" : "#d9d4c4") + "; -fx-border-width: " + (current ? 2 : 1) + "; -fx-background-radius: 5; -fx-border-radius: 5;"
                + (slot.defeated() ? " -fx-opacity: 0.55;" : ""));

        if (!slot.isGroup()) {
            box.getChildren().addAll(combatantRow(b, slot.members.get(0), true));
            return box;
        }

        // group header
        boolean open = expanded.contains(slot.key);
        Button toggle = new Button(open ? "v" : ">");
        toggle.setOnAction(e -> {
            if (open) expanded.remove(slot.key);
            else expanded.add(slot.key);
            refresh();
        });
        TextField init = initField(b, slot.members.get(0));
        Label name = new Label(slot.name() + "  -  " + slot.aliveCount() + " of " + slot.members.size() + " up");
        name.setStyle("-fx-font-weight: bold;");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button up = moveButton(b, slot.key, -1, "^"), down = moveButton(b, slot.key, +1, "v");
        Button ungroup = new Button("Ungroup");
        ungroup.setOnAction(e -> {
            BattleEngine.ungroup(b, slot.members.get(0));
            changed();
        });
        HBox head = new HBox(6, toggle, init, name, sp, up, down, ungroup);
        head.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().add(head);
        if (open) {
            for (Combatant c : slot.members) {
                VBox member = new VBox(3, combatantRow(b, c, false).toArray(new Node[0]));
                member.setPadding(new Insets(4, 4, 4, 22));
                box.getChildren().add(member);
            }
        } else {
            // collapsed: a one-line HP summary so the DM can still see how the group is doing
            FlowPane chips = new FlowPane(4, 4);
            for (Combatant c : slot.members) {
                Label chip = new Label(c.name.replaceAll("^.* ", "#") + ": " + (c.defeated ? "down" : c.hpCurrent + "/" + c.hpMax));
                chip.setStyle("-fx-background-color: " + (c.defeated ? "#e5e5e5" : "#eef6ee") + "; -fx-padding: 1 6 1 6; -fx-background-radius: 8; -fx-font-size: 11px;");
                chips.getChildren().add(chip);
            }
            chips.setPadding(new Insets(0, 0, 0, 26));
            box.getChildren().add(chips);
        }
        return box;
    }

    private Button moveButton(Battle b, String key, int delta, String text) {
        Button bt = new Button(text);
        bt.setTooltip(new Tooltip(delta < 0 ? "Earlier in the order (only among equal initiatives)" : "Later in the order (only among equal initiatives)"));
        bt.setOnAction(e -> {
            if (BattleEngine.moveSlot(b, key, delta)) changed();
        });
        return bt;
    }

    private TextField initField(Battle b, Combatant c) {
        TextField init = new TextField(String.valueOf(c.initiative));
        init.setPrefWidth(46);
        init.setTooltip(new Tooltip("Initiative"));
        Runnable apply = () -> {
            try {
                int v = Integer.parseInt(init.getText().trim());
                if (v != c.initiative) {
                    BattleEngine.setInitiative(b, c, v);
                    changed();
                }
            } catch (NumberFormatException ex) {
                init.setText(String.valueOf(c.initiative));
            }
        };
        init.setOnAction(e -> apply.run());
        init.focusedProperty().addListener((o, was, is) -> {
            if (!is) apply.run();
        });
        return init;
    }

    private List<Node> combatantRow(Battle b, Combatant c, boolean showInit) {
        List<Node> out = new ArrayList<>();

        // line 1: tick, initiative, side colour, name, menu
        CheckBox tick = new CheckBox();
        tick.setSelected(ticked.contains(c.id));
        tick.setTooltip(new Tooltip("Tick two or more creatures, then use the Group button"));
        tick.setOnAction(e -> {
            if (tick.isSelected()) ticked.add(c.id);
            else ticked.remove(c.id);
            buildHeader();
        });
        Label dot = new Label("\u25CF");
        dot.setStyle("-fx-text-fill: " + sideColor(c.side) + "; -fx-font-size: 16px;");
        Label name = new Label(c.name);
        name.setStyle("-fx-font-weight: bold;" + (c.defeated ? " -fx-strikethrough: true; -fx-text-fill: #777;" : ""));
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        ToggleButton down = new ToggleButton("Down");
        down.setSelected(c.defeated);
        down.setTooltip(new Tooltip("Mark as defeated (skipped in the turn order). Healing brings a creature back up."));
        down.setOnAction(e -> {
            c.defeated = down.isSelected();
            changed();
        });
        HBox line1 = new HBox(6);
        line1.setAlignment(Pos.CENTER_LEFT);
        if (showInit) {
            line1.getChildren().addAll(tick, initField(b, c));
            if (!BattleEngine.slots(b).isEmpty()) {
                Slot s = BattleEngine.slots(b).stream().filter(x -> x.key.equals(BattleEngine.slotKey(c))).findFirst().orElse(null);
                if (s != null) {
                    line1.getChildren().add(moveButton(b, s.key, -1, "^"));
                    line1.getChildren().add(moveButton(b, s.key, +1, "v"));
                }
            }
        }
        line1.getChildren().addAll(dot, name, sp, down, menuFor(b, c));
        out.add(line1);

        // line 2: HP bar, HP entry, AC
        double ratio = c.hpMax <= 0 ? 0 : Math.max(0, Math.min(1, (double) c.hpCurrent / c.hpMax));
        ProgressBar bar = new ProgressBar(ratio);
        bar.setPrefWidth(84);
        bar.setStyle("-fx-accent: " + (ratio > 0.5 ? "#16a34a" : ratio > 0.25 ? "#f59e0b" : "#dc2626") + ";");
        Label hp = new Label(c.hpCurrent + "/" + c.hpMax + (c.tempHp > 0 ? " (+" + c.tempHp + ")" : ""));
        hp.setStyle("-fx-font-weight: bold;");
        TextField hpBox = new TextField();
        hpBox.setPromptText("-7 / +5 / -5 -7");
        hpBox.setPrefWidth(112);
        hpBox.setTooltip(new Tooltip("Type damage as -7, healing as +5, or a plain number to set HP.\nSeveral hits at once: -5 -7 -3"));
        hpBox.setOnAction(e -> {
            if (BattleEngine.applyHpInput(b, c, hpBox.getText())) {
                changed();
            } else {
                hpBox.setStyle("-fx-border-color: #dc2626;");
            }
        });
        hpBox.textProperty().addListener((o, a, n) -> hpBox.setStyle(""));
        Label ac = new Label("AC " + c.ac);
        ac.setStyle("-fx-text-fill: #555;");
        hp.setMinWidth(64);
        ac.setMinWidth(40);
        HBox line2 = new HBox(6, bar, hp, hpBox, ac);
        line2.setAlignment(Pos.CENTER_LEFT);
        out.add(line2);

        if (!c.conditions.isEmpty()) {
            FlowPane chips = new FlowPane(4, 4);
            for (String cond : c.conditions) {
                Label chip = new Label(cond);
                chip.setStyle("-fx-background-color: #f3e0e0; -fx-padding: 1 6 1 6; -fx-background-radius: 8; -fx-font-size: 11px;");
                chips.getChildren().add(chip);
            }
            out.add(chips);
        }
        if (c.notes != null && !c.notes.isBlank()) {
            Label n = new Label(c.notes);
            n.setWrapText(true);
            n.setStyle("-fx-text-fill: #666; -fx-font-size: 11px; -fx-font-style: italic;");
            out.add(n);
        }
        return out;
    }

    private MenuButton menuFor(Battle b, Combatant c) {
        MenuButton mb = new MenuButton("...");
        MenuItem rename = new MenuItem("Rename...");
        rename.setOnAction(e -> ask("Rename", "Name:", c.name).ifPresent(v -> {
            c.name = v;
            changed();
        }));
        MenuItem maxHp = new MenuItem("Set max HP...");
        maxHp.setOnAction(e -> askInt("Max HP", "Maximum hit points:", c.hpMax).ifPresent(v -> {
            c.hpMax = Math.max(1, v);
            c.hpCurrent = Math.min(c.hpCurrent, c.hpMax);
            changed();
        }));
        MenuItem temp = new MenuItem("Set temporary HP...");
        temp.setOnAction(e -> askInt("Temporary HP", "Temporary hit points:", c.tempHp).ifPresent(v -> {
            BattleEngine.setTempHp(c, v);
            changed();
        }));
        MenuItem acItem = new MenuItem("Set AC...");
        acItem.setOnAction(e -> askInt("Armor class", "AC:", c.ac).ifPresent(v -> {
            c.ac = Math.max(0, v);
            changed();
        }));
        Menu conds = new Menu("Conditions");
        for (String name : CONDITIONS) {
            CheckMenuItem ci = new CheckMenuItem(name);
            ci.setSelected(c.conditions.stream().anyMatch(x -> x.equalsIgnoreCase(name)));
            ci.setOnAction(e -> {
                BattleEngine.toggleCondition(c, name);
                changed();
            });
            conds.getItems().add(ci);
        }
        MenuItem notes = new MenuItem("Notes...");
        notes.setOnAction(e -> ask("Notes", "Notes for " + c.name + ":", c.notes == null ? "" : c.notes).ifPresent(v -> {
            c.notes = v;
            changed();
        }));
        boolean hasToken = c.tokenId != null && map.findToken(c.tokenId) != null;
        MenuItem find = new MenuItem("Find token on the map");
        find.setDisable(!hasToken);
        find.setOnAction(e -> host.centerOn(c.tokenId));
        MenuItem place = new MenuItem(hasToken ? "Place another token on the map" : "Place token on the map");
        place.setOnAction(e -> {
            if (hasToken) c.tokenId = null; // the new token replaces the link; the old one stays as a plain token
            host.addTokens(List.of(c));
            changed();
        });
        MenuItem open = new MenuItem("Open card");
        open.setDisable(c.cardId == null || !store.isCard(c.cardId));
        open.setOnAction(e -> host.openCard(c.cardId));
        MenuItem remove = new MenuItem("Remove from battle");
        remove.setOnAction(e -> {
            BattleEngine.remove(b, c);
            ticked.remove(c.id);
            if (c.tokenId != null && map.findToken(c.tokenId) != null) host.removeToken(c.tokenId);
            changed();
        });
        mb.getItems().addAll(rename, maxHp, temp, acItem, conds, notes, new SeparatorMenuItem(), find, place, open,
                new SeparatorMenuItem(), remove);
        return mb;
    }

    private static String sideColor(Side s) {
        return switch (s) {
            case PLAYER -> "#2b6cb0";
            case ALLY -> "#2f855a";
            case ENEMY -> "#c0392b";
        };
    }

    // ================================================================ actions

    private void addCombatants() {
        Optional<List<AddCombatantsDialog.Pick>> res = AddCombatantsDialog.show(getScene() == null ? null : getScene().getWindow(), store);
        if (res.isEmpty() || res.get().isEmpty()) return;
        Battle b = battle();
        List<Combatant> added = new ArrayList<>();
        for (AddCombatantsDialog.Pick p : res.get()) {
            added.addAll(BattleEngine.addFromCard(b, p.card(), p.count(), p.group(), p.side()));
        }
        host.addTokens(added);
        changed();
        Platform.runLater(this::refresh);
    }

    private void clearAll() {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Remove every creature and its token from this battle?", ButtonType.OK, ButtonType.CANCEL);
        a.setHeaderText("Clear battle");
        Optional<ButtonType> r = a.showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.OK) return;
        Battle b = battle();
        for (Combatant c : new ArrayList<>(b.combatants)) if (c.tokenId != null) host.removeToken(c.tokenId);
        b.combatants.clear();
        b.log.clear();
        b.turnId = null;
        b.active = false;
        b.round = 1;
        ticked.clear();
        changed();
    }

    private Optional<String> ask(String title, String header, String initial) {
        TextInputDialog d = new TextInputDialog(initial);
        d.setTitle(title);
        d.setHeaderText(header);
        if (getScene() != null) d.initOwner(getScene().getWindow());
        return d.showAndWait().map(String::trim).filter(s -> !s.isEmpty());
    }

    private Optional<Integer> askInt(String title, String header, int initial) {
        return ask(title, header, String.valueOf(initial)).map(s -> {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return null;
            }
        });
    }
}
