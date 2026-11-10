package dnd.ui;

import dnd.battle.BattleEngine;
import dnd.battle.BattleEngine.Slot;
import dnd.model.Battle;
import dnd.model.Combatant;
import dnd.model.Combatant.Side;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.util.List;

/**
 * The turn order along the bottom of the battle screen: one chip per creature (or group), highest initiative
 * first. The chip whose turn it is is large and amber, and the strip scrolls to keep it in view as turns pass.
 * Clicking a chip shows that creature's card.
 */
final class InitiativeStrip extends ScrollPane {
    private final BattleContext ctx;
    private final HBox box = new HBox(10);
    private VBox currentChip;
    private Timeline scroll;

    InitiativeStrip(BattleContext ctx) {
        this.ctx = ctx;
        box.setPadding(new Insets(8, 12, 8, 12));
        box.setAlignment(Pos.CENTER_LEFT);
        setContent(box);
        setFitToHeight(true);
        setVbarPolicy(ScrollBarPolicy.NEVER);
        setHbarPolicy(ScrollBarPolicy.AS_NEEDED);
        setPrefHeight(UiScale.px(112));
        setMinHeight(UiScale.px(112));
        setStyle("-fx-background: #1e293b; -fx-background-color: #1e293b;");
    }

    /** @param inspectedKey slot key of the creature being viewed (outlined in blue), or null */
    void refresh(String inspectedKey) {
        box.getChildren().clear();
        currentChip = null;
        Battle b = ctx.battle();
        List<Slot> slots = BattleEngine.slots(b);
        if (slots.isEmpty()) {
            Label none = new Label("No one is in the battle yet.");
            none.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 1.250em;");
            box.getChildren().add(none);
            return;
        }
        for (Slot s : slots) {
            boolean current = b.active && s.key.equals(b.turnId);
            VBox chip = chip(b, s, current, s.key.equals(inspectedKey));
            if (current) currentChip = chip;
            box.getChildren().add(chip);
        }
        Platform.runLater(this::scrollToCurrent);
    }

    private VBox chip(Battle b, Slot s, boolean current, boolean inspected) {
        Combatant first = s.members.get(0);
        boolean has = BattleEngine.hasInitiative(b, first);
        boolean down = s.defeated();

        Label init = new Label(has ? String.valueOf(s.initiative()) : "?");
        init.setStyle("-fx-font-weight: bold; -fx-text-fill: " + (current ? "#78350f" : "-t-text2") + "; -fx-font-size: " + UiScale.em(current ? 18 : 14) + "; -fx-background-color: " + (current ? "#fde68a" : "-t-surface2") + "; -fx-background-radius: 10; -fx-padding: 1 8 1 8;");
        Label dot = new Label("●");
        dot.setStyle("-fx-text-fill: " + sideColor(first.side) + ";");
        Label name = new Label(s.name());
        name.setFont(Font.font("System", FontWeight.BOLD, UiScale.px(current ? 19 : 14)));
        name.setStyle(down ? "-fx-strikethrough: true; -fx-text-fill: -t-text3;" : "-fx-text-fill: " + (current ? "#0f172a" : "-t-text") + ";");
        HBox top = new HBox(6, init, dot, name);
        top.setAlignment(Pos.CENTER_LEFT);
        // a coloured dot per condition that anyone in this slot has (same colours as the ring on the map)
        java.util.LinkedHashSet<String> conds = new java.util.LinkedHashSet<>();
        for (Combatant c : s.members) if (!c.defeated) conds.addAll(c.conditions);
        for (String cond : conds) {
            Label cd = new Label(dnd.battle.ConditionStyle.safeColours() ? dnd.battle.ConditionStyle.codeOf(cond) : "\u25CF");
            cd.setStyle("-fx-text-fill: " + dnd.battle.ConditionStyle.colorOf(cond) + "; -fx-font-size: " + UiScale.em(current ? 16 : 12) + ";");
            Tooltip.install(cd, IconButtons.tip(cond));
            top.getChildren().add(cd);
        }

        for (Combatant c : s.members) {
            if (dnd.battle.CombatStatus.isDying(c)) {
                Label skull = new Label("\u2620");
                skull.setStyle("-fx-text-fill: #b91c1c; -fx-font-size: " + UiScale.em(current ? 18 : 14) + "; -fx-font-weight: bold;");
                Tooltip.install(skull, IconButtons.tip(c.name + " is dying: " + c.deathSuccesses + " successes, " + c.deathFailures + " failures"));
                top.getChildren().add(skull);
            }
        }

        int hp = 0, max = 0;
        for (Combatant c : s.members) {
            if (c.defeated) continue;
            hp += c.hpCurrent;
            max += c.hpMax;
        }
        double ratio = max <= 0 ? 0 : Math.max(0, Math.min(1, (double) hp / max));
        ProgressBar bar = new ProgressBar(down ? 0 : ratio);
        bar.setPrefWidth(current ? 190 : 120);
        bar.setPrefHeight(current ? 12 : 8);
        bar.setStyle("-fx-accent: " + (ratio > 0.5 ? "#16a34a" : ratio > 0.25 ? "#f59e0b" : "#dc2626") + ";");
        Combatant only = s.members.size() == 1 ? first : null;
        String downText = only != null && dnd.battle.CombatStatus.isDead(only) ? "dead" : only != null && dnd.battle.CombatStatus.isStable(only) ? "stable" : "down";
        boolean dyingNow = only != null && dnd.battle.CombatStatus.isDying(only);
        Label sub = new Label(dyingNow ? "dying: " + only.deathSuccesses + " saved, " + only.deathFailures + " failed" : down ? downText : s.members.size() > 1
                ? s.aliveCount() + " of " + s.members.size() + " up  |  " + hp + "/" + max + " HP"
                : hp + "/" + max + " HP  |  AC " + first.ac);
        sub.setStyle("-fx-font-size: " + UiScale.em(current ? 13 : 11) + "; -fx-text-fill: " + (current ? "#334155" : "-t-text2") + ";");

        VBox chip = new VBox(3, top, bar, sub);
        chip.setPadding(current ? new Insets(8, 16, 8, 14) : new Insets(6, 12, 6, 10));
        chip.setMinWidth(current ? 230 : 160);
        chip.setAlignment(Pos.CENTER_LEFT);
        String border = inspected ? "#2563eb" : current ? "#b45309" : "#94a3b8";
        chip.setStyle("-fx-background-color: " + (current ? "#fbbf24" : "-t-slate") + "; -fx-background-radius: 12; -fx-border-radius: 12; "
                + "-fx-border-color: " + border + "; -fx-border-width: " + (current || inspected ? 3 : 1) + ";"
                + (down ? " -fx-opacity: 0.6;" : "")
                + (current ? " -fx-effect: dropshadow(gaussian, rgba(251,191,36,0.9), 18, 0.35, 0, 0);" : ""));
        chip.setCursor(Cursor.HAND);
        Tooltip.install(chip, IconButtons.tip("Click to view " + s.name() + "'s card. Right-click to set conditions."));
        chip.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) ctx.inspect(s.key);
        });
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        chip.setOnContextMenuRequested(e -> {
            menu.getItems().setAll(CombatantCard.conditionsMenu(ctx, new java.util.ArrayList<>(s.members)));
            menu.show(chip, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        return chip;
    }

    /** Smoothly scrolls so that the current chip is in the middle of the strip. */
    private void scrollToCurrent() {
        if (currentChip == null) return;
        applyCss();
        layout();
        double content = box.getWidth();
        double view = getViewportBounds().getWidth();
        if (content <= view || view <= 0) {
            setHvalue(0);
            return;
        }
        double center = currentChip.getBoundsInParent().getMinX() + currentChip.getBoundsInParent().getWidth() / 2;
        double target = Math.max(0, Math.min(1, (center - view / 2) / (content - view)));
        if (scroll != null) scroll.stop();
        if (AppSettings.getBoolean("ui.reduceMotion", false)) { // no gliding: jump straight to the current turn
            setHvalue(target);
            return;
        }
        scroll = new Timeline(new KeyFrame(Duration.millis(260), new KeyValue(hvalueProperty(), target)));
        scroll.play();
    }

    private static String sideColor(Side s) {
        return switch (s) {
            case PLAYER -> "#2b6cb0";
            case ALLY -> "#2f855a";
            case ENEMY -> "#c0392b";
        };
    }

    /** True if a chip is currently highlighted as the active turn (used by tests). */
    boolean hasCurrentChip() {
        return currentChip != null;
    }
}
