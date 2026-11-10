package dnd.ui;

import dnd.battle.BattleEngine;
import dnd.battle.BattleEngine.Slot;
import dnd.model.Battle;
import dnd.model.Combatant;
import dnd.model.Combatant.Side;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.*;

/**
 * Before the fight: who is in it and what each one's initiative is. Initiative is entered (or rolled) here once,
 * and Start battle only unlocks when everyone has one; it is never rolled again after the battle begins.
 */
final class BattleSetupView extends VBox {
    private final BattleContext ctx;
    private final Runnable onAdd;
    private final Runnable onLoadEncounter;
    private final Runnable onStart;
    private final Random random = new Random();
    private final Set<String> ticked = new LinkedHashSet<>();

    BattleSetupView(BattleContext ctx, Runnable onAdd, Runnable onLoadEncounter, Runnable onStart) {
        this.ctx = ctx;
        this.onAdd = onAdd;
        this.onLoadEncounter = onLoadEncounter;
        this.onStart = onStart;
        setSpacing(8);
        setPadding(new Insets(12));
        setStyle("-fx-background-color: -t-slate;");
        refresh();
    }

    void refresh() {
        getChildren().clear();
        Battle b = ctx.battle();

        Label title = new Label("Set up the battle");
        title.setFont(Font.font("System", FontWeight.BOLD, UiScale.px(20)));
        Label steps = new Label("1. Add the players and monsters.\n2. Enter each initiative (or roll it).\n3. Press Start battle.");
        steps.setStyle("-fx-text-fill: -t-text2;");
        getChildren().addAll(title, steps);

        Button add = new Button("+ Add creatures...");
        add.setStyle("-fx-font-weight: bold;");
        add.setOnAction(e -> onAdd.run());
        Button load = new Button("Load encounter...");
        load.setTooltip(new Tooltip("Add every creature of a saved encounter at once"));
        load.setDisable(ctx.store().allEncounterIds().isEmpty());
        load.setOnAction(e -> onLoadEncounter.run());
        List<Slot> missing = BattleEngine.missingInitiative(b);
        boolean canRollSome = missing.stream().anyMatch(s -> s.members.stream().anyMatch(c -> c.side != Side.PLAYER));
        Button rollAll = new Button("Roll enemies and allies");
        rollAll.setTooltip(new Tooltip("Rolls d20 + initiative bonus for every enemy and ally that has no initiative yet (one roll per group).\n"
                + "Players roll their own: type their results in, or use the d20 button on their row. Entered values are never re-rolled."));
        rollAll.setDisable(!canRollSome);
        rollAll.setOnAction(e -> {
            BattleEngine.rollMissing(ctx.battle(), false, random);
            ctx.changed();
        });
        HBox actions = new HBox(8, add, load, rollAll);
        actions.setAlignment(Pos.CENTER_LEFT);
        getChildren().add(actions);

        List<Slot> slots = BattleEngine.slots(b);
        if (b.combatants.isEmpty()) {
            Label none = new Label("Nobody yet. Press + Add creatures... to bring in cards from your project.");
            none.setStyle("-fx-text-fill: -t-text3;");
            none.setWrapText(true);
            getChildren().add(none);
        } else {
            VBox rows = new VBox(6);
            for (Slot s : slots) rows.getChildren().add(row(b, s));
            getChildren().add(rows);
            if (ticked.size() >= 2) {
                Button group = new Button("Group the " + ticked.size() + " ticked creatures");
                group.setTooltip(new Tooltip("They will share one initiative and take their turn together"));
                group.setOnAction(e -> {
                    List<Combatant> picked = new ArrayList<>();
                    for (Combatant c : ctx.battle().combatants) if (ticked.contains(c.id)) picked.add(c);
                    BattleEngine.group(ctx.battle(), picked);
                    ticked.clear();
                    ctx.changed();
                });
                getChildren().add(group);
            }
        }

        // ---- start
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Button start = new Button("Start battle");
        start.setStyle("-fx-font-size: 1.500em; -fx-font-weight: bold; -fx-base: #fbbf24; -fx-padding: 8 24 8 24;");
        start.setDisable(!BattleEngine.canStart(b));
        start.setOnAction(e -> onStart.run());
        Label why = new Label();
        why.setWrapText(true);
        if (b.combatants.isEmpty()) {
            why.setText("Add at least one creature to start.");
        } else if (!missing.isEmpty()) {
            why.setText(missing.size() == 1 ? "1 creature still needs an initiative: " + missing.get(0).name()
                    : missing.size() + " creatures still need an initiative.");
            why.setStyle("-fx-text-fill: -t-danger; -fx-font-weight: bold;");
        } else if (!BattleEngine.canStart(b)) {
            why.setText("Everyone is marked down, so there is nobody to take a turn.");
        } else {
            why.setText("Everyone is ready.");
            why.setStyle("-fx-text-fill: -t-ok; -fx-font-weight: bold;");
        }
        getChildren().addAll(spacer, why, start);
    }

    private Node row(Battle b, Slot s) {
        Combatant first = s.members.get(0);
        boolean has = BattleEngine.hasInitiative(b, first);
        boolean group = s.isGroup();

        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6));
        row.setStyle("-fx-background-color: " + (has ? "-t-surface" : "-t-red") + "; -fx-border-color: " + (has ? "-t-border" : "#fca5a5")
                + "; -fx-background-radius: 5; -fx-border-radius: 5;");

        if (!group) {
            CheckBox tick = new CheckBox();
            tick.setSelected(ticked.contains(first.id));
            tick.setTooltip(new Tooltip("Tick two or more creatures, then use the Group button below"));
            tick.setOnAction(e -> {
                if (tick.isSelected()) ticked.add(first.id);
                else ticked.remove(first.id);
                refresh();
            });
            row.getChildren().add(tick);
        }
        Label dot = new Label("●");
        dot.setStyle("-fx-text-fill: " + sideColor(first.side) + "; -fx-font-size: 1.333em;");
        Label name = new Label(s.name());
        name.setStyle("-fx-font-weight: bold;");
        Label info = new Label(group ? s.members.size() + " together | AC " + first.ac + " | HP " + first.hpMax + " each"
                : "AC " + first.ac + " | HP " + first.hpMax);
        info.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 0.917em;");
        VBox who = new VBox(1, name, info);
        HBox.setHgrow(who, Priority.ALWAYS);

        TextField init = new TextField(has ? String.valueOf(s.initiative()) : "");
        init.setPromptText("init");
        init.setPrefWidth(58);
        init.setTooltip(new Tooltip("Initiative"));
        Runnable apply = () -> {
            String t = init.getText().trim();
            if (t.isEmpty()) return;
            try {
                int v = Integer.parseInt(t);
                if (!has || v != s.initiative()) {
                    BattleEngine.setInitiative(ctx.battle(), first, v);
                    ctx.changed();
                }
            } catch (NumberFormatException ex) {
                init.setStyle("-fx-border-color: #dc2626;");
            }
        };
        init.setOnAction(e -> apply.run());
        init.focusedProperty().addListener((o, was, is) -> {
            if (!is) apply.run();
        });
        init.textProperty().addListener((o, a, n) -> init.setStyle(""));

        Button dice = new Button("d20");
        int bonus = s.members.stream().mapToInt(c -> c.initiativeBonus).max().orElse(0);
        dice.setTooltip(new Tooltip("Roll d20 " + (bonus >= 0 ? "+ " : "- ") + Math.abs(bonus) + " for " + s.name()));
        dice.setOnAction(e -> {
            BattleEngine.rollOne(ctx.battle(), first, random);
            ctx.changed();
        });

        row.getChildren().addAll(dot, who, init, dice);
        if (group) {
            Button ungroup = new Button("Ungroup");
            ungroup.setOnAction(e -> {
                BattleEngine.ungroup(ctx.battle(), first);
                ctx.changed();
            });
            row.getChildren().add(ungroup);
        }
        Button remove = new Button("x");
        remove.setTooltip(new Tooltip("Remove " + s.name() + " from the battle"));
        remove.setOnAction(e -> {
            for (Combatant c : new ArrayList<>(s.members)) ctx.remove(c);
        });
        row.getChildren().add(remove);
        return row;
    }

    private static String sideColor(Side s) {
        return switch (s) {
            case PLAYER -> "#2b6cb0";
            case ALLY -> "#2f855a";
            case ENEMY -> "#c0392b";
        };
    }
}
