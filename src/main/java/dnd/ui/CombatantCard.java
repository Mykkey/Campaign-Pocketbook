package dnd.ui;

import dnd.battle.BattleEngine;
import dnd.model.Attack;
import dnd.model.Battle;
import dnd.model.Card;
import dnd.model.Combatant;
import dnd.model.Combatant.Side;
import dnd.model.Feature;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A creature (or a group of identical creatures) as the DM sees it in a fight: live hit points and conditions,
 * its attacks, and the full stat block. The card for whoever's turn it is is shown large; clicking someone else
 * in the initiative strip shows theirs in a second card so the DM can plan ahead.
 */
final class CombatantCard extends VBox {

    private final BattleContext ctx;
    private final List<Combatant> members;
    private final boolean active;

    /**
     * @param members one creature, or every member of a group
     * @param active  true for the creature whose turn it is (bigger type, amber frame), false for a card being viewed
     */
    CombatantCard(BattleContext ctx, List<Combatant> members, boolean active) {
        this.ctx = ctx;
        this.members = members;
        this.active = active;
        setSpacing(8);
        setPadding(new Insets(10));
        setStyle("-fx-background-color: " + (active ? "#fffbea" : "#f1f5fb") + "; -fx-border-color: "
                + (active ? "#f59e0b" : "#2563eb") + "; -fx-border-width: 0 0 0 5;");
        build();
    }

    private void build() {
        Combatant first = members.get(0);
        boolean group = members.size() > 1;
        Card card = first.cardId == null ? null : ctx.store().card(first.cardId);
        Battle b = ctx.battle();

        // ---- header
        Label tag = new Label(active ? "NOW" : "VIEWING");
        tag.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: white; -fx-padding: 1 7 1 7; -fx-background-radius: 8; "
                + "-fx-background-color: " + (active ? "#d97706" : "#2563eb") + ";");
        Label dot = new Label("●");
        dot.setStyle("-fx-text-fill: " + sideColor(first.side) + "; -fx-font-size: 18px;");
        String title = group ? first.name.replaceAll("\\s+\\d+$", "") + " x" + members.size() : first.name;
        Label name = new Label(title);
        name.setFont(Font.font("System", FontWeight.BOLD, active ? 24 : 18));
        name.setWrapText(true);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox head = new HBox(8, tag, dot, name, sp);
        head.setAlignment(Pos.CENTER_LEFT);
        if (!group) {
            head.getChildren().addAll(conditionsButton(List.of(first)), downToggle(first), menuFor(first));
        } else {
            long up = members.stream().filter(c -> !c.defeated).count();
            Label count = new Label(up + " of " + members.size() + " up");
            count.setStyle("-fx-text-fill: #555; -fx-font-weight: bold;");
            head.getChildren().addAll(count, conditionsButton(members));
        }
        getChildren().add(head);

        // ---- hit points
        if (!group) {
            getChildren().add(hpRow(first, true));
            if (!first.conditions.isEmpty()) getChildren().add(conditionChips(first));
            if (first.notes != null && !first.notes.isBlank()) {
                Label n = new Label(first.notes);
                n.setWrapText(true);
                n.setStyle("-fx-text-fill: #555; -fx-font-style: italic;");
                getChildren().add(n);
            }
        } else {
            getChildren().add(sectionLabel("Members"));
            for (Combatant c : members) getChildren().add(memberRow(c));
        }

        // ---- attacks
        if (card != null) {
            getChildren().add(sectionLabel("Attacks"));
            if (card.attacks.isEmpty()) {
                Label none = new Label("This card lists no attacks.");
                none.setStyle("-fx-text-fill: #777;");
                getChildren().add(none);
            }
            for (Attack a : card.attacks) {
                Label l = new Label(a.name + ":  " + a.summary());
                l.setWrapText(true);
                l.setFont(Font.font("System", active ? 15 : 13));
                l.setStyle("-fx-padding: 3 6 3 6; -fx-background-color: white; -fx-border-color: #e5e7eb;");
                l.setMaxWidth(Double.MAX_VALUE);
                getChildren().add(l);
            }
            List<String> extras = new ArrayList<>();
            for (Feature f : card.traits) if (!f.name.isBlank()) extras.add(f.name);
            for (Feature f : card.actions) if (!f.name.isBlank()) extras.add(f.name);
            for (Feature f : card.reactions) if (!f.name.isBlank()) extras.add(f.name + " (reaction)");
            if (!extras.isEmpty()) {
                Label e = new Label("Also: " + String.join(", ", extras));
                e.setWrapText(true);
                e.setStyle("-fx-text-fill: #555; -fx-font-style: italic;");
                getChildren().add(e);
            }
            Hyperlink open = new Hyperlink("Open " + card.name + " in its own tab");
            open.setOnAction(ev -> ctx.openCard(card.id));
            getChildren().add(open);

            // ---- full stat block
            getChildren().add(sectionLabel("Stat block"));
            StatBlockView sb = new StatBlockView(ctx.store());
            sb.refresh(card);
            sb.setMaxWidth(Double.MAX_VALUE);
            getChildren().add(sb);
        } else {
            Label none = new Label("No card is linked to this creature, so there is no stat block or attack list.");
            none.setWrapText(true);
            none.setStyle("-fx-text-fill: #777;");
            getChildren().add(none);
        }
    }

    // ================================================================ pieces

    private static Label sectionLabel(String text) {
        Label l = new Label(text.toUpperCase());
        l.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #7a200d; -fx-padding: 6 0 0 0;");
        return l;
    }

    private HBox hpRow(Combatant c, boolean big) {
        Battle b = ctx.battle();
        double ratio = c.hpMax <= 0 ? 0 : Math.max(0, Math.min(1, (double) c.hpCurrent / c.hpMax));
        ProgressBar bar = new ProgressBar(ratio);
        bar.setPrefWidth(big ? 170 : 90);
        bar.setPrefHeight(big ? 18 : 12);
        bar.setStyle("-fx-accent: " + (ratio > 0.5 ? "#16a34a" : ratio > 0.25 ? "#f59e0b" : "#dc2626") + ";");
        Label hp = new Label(c.hpCurrent + " / " + c.hpMax + (c.tempHp > 0 ? "  (+" + c.tempHp + " temp)" : ""));
        hp.setFont(Font.font("System", FontWeight.BOLD, big ? 16 : 12));
        TextField box = new TextField();
        box.setPromptText(big ? "-7   +5   -5 -7 -3" : "-7 / +5");
        box.setPrefWidth(big ? 150 : 90);
        box.setTooltip(new Tooltip("Type damage as -7, healing as +5, or a plain number to set HP.\nSeveral hits at once: -5 -7 -3"));
        box.setOnAction(e -> {
            if (BattleEngine.applyHpInput(b, c, box.getText())) ctx.changed();
            else box.setStyle("-fx-border-color: #dc2626;");
        });
        box.textProperty().addListener((o, a, n) -> box.setStyle(""));
        Label ac = new Label("AC " + c.ac);
        ac.setFont(Font.font("System", FontWeight.BOLD, big ? 15 : 12));
        ac.setStyle("-fx-text-fill: #475569;");
        HBox row = new HBox(8, bar, hp, box, ac);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node memberRow(Combatant c) {
        Label dot = new Label(c.name.replaceAll("^.* ", "#"));
        dot.setMinWidth(30);
        dot.setStyle("-fx-font-weight: bold;" + (c.defeated ? " -fx-strikethrough: true; -fx-text-fill: #777;" : ""));
        HBox row = hpRow(c, false);
        row.getChildren().add(0, dot);
        row.getChildren().add(downToggle(c));
        row.getChildren().add(menuFor(c));
        VBox box = new VBox(2, row);
        if (!c.conditions.isEmpty()) box.getChildren().add(conditionChips(c));
        return box;
    }

    private static FlowPane conditionChips(Combatant c) {
        FlowPane chips = new FlowPane(4, 4);
        for (String cond : c.conditions) {
            Label chip = new Label(cond);
            String col = dnd.battle.ConditionStyle.colorOf(cond);
            Label swatch = new Label("\u25CF");
            swatch.setStyle("-fx-text-fill: " + col + ";");
            chip.setGraphic(swatch);
            chip.setGraphicTextGap(4);
            chip.setStyle("-fx-background-color: #f1f5f9; -fx-border-color: " + col + "; -fx-border-radius: 8; -fx-padding: 1 7 1 6; -fx-background-radius: 8; -fx-font-size: 12px;");
            chips.getChildren().add(chip);
        }
        return chips;
    }

    /** A "Conditions" menu that sets or clears each standard condition on every creature in {@code targets}. */
    static Menu conditionsMenu(BattleContext ctx, List<Combatant> targets) {
        Menu conds = new Menu("Conditions");
        for (String name : dnd.battle.ConditionStyle.names()) {
            CheckMenuItem ci = new CheckMenuItem(name);
            Label sw = new Label("\u25CF");
            sw.setStyle("-fx-text-fill: " + dnd.battle.ConditionStyle.colorOf(name) + ";");
            ci.setGraphic(sw);
            ci.setSelected(targets.stream().allMatch(x -> x.conditions.stream().anyMatch(y -> y.equalsIgnoreCase(name))));
            ci.setOnAction(e -> {
                for (Combatant x : targets) {
                    boolean has = x.conditions.stream().anyMatch(y -> y.equalsIgnoreCase(name));
                    if (has != ci.isSelected()) BattleEngine.toggleCondition(x, name);
                }
                ctx.changed();
            });
            conds.getItems().add(ci);
        }
        return conds;
    }

    /** A visible button on the card header for setting conditions (so it is not hidden in the ... menu). */
    private MenuButton conditionsButton(List<Combatant> targets) {
        MenuButton mb = new MenuButton("Conditions");
        mb.getItems().addAll(conditionsMenu(ctx, targets).getItems());
        mb.setTooltip(new Tooltip("Set or clear conditions. They show as a coloured ring around the token."));
        return mb;
    }

    private ToggleButton downToggle(Combatant c) {
        ToggleButton down = new ToggleButton("Down");
        down.setSelected(c.defeated);
        down.setTooltip(new Tooltip("Mark as defeated (skipped in the turn order). Healing brings a creature back up."));
        down.setOnAction(e -> {
            c.defeated = down.isSelected();
            ctx.changed();
        });
        return down;
    }

    private MenuButton menuFor(Combatant c) {
        Battle b = ctx.battle();
        MenuButton mb = new MenuButton("...");
        MenuItem rename = new MenuItem("Rename...");
        rename.setOnAction(e -> ask("Rename", "Name:", c.name).ifPresent(v -> {
            c.name = v;
            ctx.changed();
        }));
        MenuItem maxHp = new MenuItem("Set max HP...");
        maxHp.setOnAction(e -> askInt("Max HP", "Maximum hit points:", c.hpMax).ifPresent(v -> {
            c.hpMax = Math.max(1, v);
            c.hpCurrent = Math.min(c.hpCurrent, c.hpMax);
            ctx.changed();
        }));
        MenuItem temp = new MenuItem("Set temporary HP...");
        temp.setOnAction(e -> askInt("Temporary HP", "Temporary hit points:", c.tempHp).ifPresent(v -> {
            BattleEngine.setTempHp(c, v);
            ctx.changed();
        }));
        MenuItem acItem = new MenuItem("Set AC...");
        acItem.setOnAction(e -> askInt("Armor class", "AC:", c.ac).ifPresent(v -> {
            c.ac = Math.max(0, v);
            ctx.changed();
        }));
        Menu conds = conditionsMenu(ctx, List.of(c));
        MenuItem notes = new MenuItem("Notes...");
        notes.setOnAction(e -> ask("Notes", "Notes for " + c.name + ":", c.notes == null ? "" : c.notes).ifPresent(v -> {
            c.notes = v;
            ctx.changed();
        }));
        boolean hasToken = c.tokenId != null && ctx.map().findToken(c.tokenId) != null;
        MenuItem find = new MenuItem("Find token on the map");
        find.setDisable(!hasToken);
        find.setOnAction(e -> ctx.centerOn(c.tokenId));
        MenuItem place = new MenuItem(hasToken ? "Place another token on the map" : "Place token on the map");
        place.setOnAction(e -> ctx.placeToken(c));
        MenuItem remove = new MenuItem("Remove from battle");
        remove.setOnAction(e -> ctx.remove(c));
        mb.getItems().addAll(rename, maxHp, temp, acItem, conds, notes, new SeparatorMenuItem(), find, place,
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
