package dnd.ui;

import dnd.map.AssetLibrary;
import dnd.map.Grid;
import dnd.map.MapRenderer;
import dnd.model.BattleMap;
import dnd.storage.ProjectStore;
import dnd.battle.BattleEngine;
import dnd.map.Fog;
import dnd.model.Battle;
import dnd.model.Combatant;
import dnd.model.MapToken;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;

/**
 * A clean, read-only view of a battlemap for the table: no selection handles, hover ghosts or rulers, and
 * its own pan/zoom so it never moves the DM's view. It redraws whenever the map changes.
 */
public class PlayerView extends Pane {
    private static final double MARGIN = 24;

    private final BattleMap map;
    private final Grid grid;
    private final AssetLibrary lib;
    private final Canvas canvas = new Canvas();

    private double zoom = 1, offX, offY;
    private boolean autoFit = true;
    private boolean showGrid;
    private boolean dirty = true;
    private int drawnRevision = -1;
    private double dragX, dragY;
    private boolean showInitiative = true;
    private final HBox strip = new HBox(6);

    public PlayerView(BattleMap map, ProjectStore store) {
        this.map = map;
        this.grid = Grid.of(map);
        this.lib = store.assets();
        this.showGrid = map.showGrid;
        getChildren().add(canvas);
        strip.setAlignment(Pos.CENTER);
        strip.setMouseTransparent(true);
        strip.setVisible(false);
        getChildren().add(strip);
        setMinSize(0, 0);
        setFocusTraversable(true);

        setOnScroll(e -> {
            if (e.getDeltaY() == 0) return;
            double nz = Math.max(0.05, Math.min(8, zoom * Math.exp(e.getDeltaY() * 0.0015)));
            double wx = (e.getX() - offX) / zoom, wy = (e.getY() - offY) / zoom;
            zoom = nz;
            offX = e.getX() - wx * nz;
            offY = e.getY() - wy * nz;
            autoFit = false;
            dirty = true;
            e.consume();
        });
        setOnMousePressed(e -> {
            requestFocus();
            dragX = e.getX();
            dragY = e.getY();
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) fit();
        });
        setOnMouseDragged(e -> {
            offX += e.getX() - dragX;
            offY += e.getY() - dragY;
            dragX = e.getX();
            dragY = e.getY();
            autoFit = false;
            dirty = true;
        });
    }

    public boolean isGridShown() { return showGrid; }

    /** Shows or hides the turn-order strip along the top (only visible while a battle is running). */
    public void toggleInitiative() {
        showInitiative = !showInitiative;
        dirty = true;
    }

    /** What the players are allowed to know about a creature: nothing hidden and no enemies still in fog. */
    private boolean visibleToPlayers(Combatant c) {
        MapToken t = c.tokenId == null ? null : map.findToken(c.tokenId);
        if (t == null) return c.side != Combatant.Side.ENEMY;
        if (t.hidden) return false;
        if (map.fogEnabled && c.side != Combatant.Side.PLAYER && !Fog.isRevealed(map, t.col, t.row)) return false;
        return true;
    }

    private void rebuildStrip() {
        strip.getChildren().clear();
        Battle b = map.battle;
        if (!showInitiative || b == null || !b.active || b.combatants.isEmpty()) {
            strip.setVisible(false);
            return;
        }
        Label round = new Label("Round " + b.round);
        round.setStyle("-fx-text-fill: #e5e7eb; -fx-font-weight: bold; -fx-font-size: 1.250em; -fx-padding: 0 8 0 4;");
        strip.getChildren().add(round);
        for (BattleEngine.Slot s : BattleEngine.slots(b)) {
            if (s.defeated()) continue;
            java.util.List<Combatant> seen = new java.util.ArrayList<>();
            for (Combatant c : s.members) if (!c.defeated && visibleToPlayers(c)) seen.add(c);
            if (seen.isEmpty()) continue;
            boolean current = b.turnId != null && b.turnId.equals(s.key);
            String name = seen.size() > 1 ? seen.get(0).name.replaceAll("\\s+\\d+$", "") + " x" + seen.size() : seen.get(0).name;
            Label chip = new Label(name);
            chip.setStyle("-fx-padding: 4 12 4 12; -fx-background-radius: 14; -fx-font-size: 1.250em; -fx-font-weight: bold;"
                    + (current ? " -fx-background-color: #fbbf24; -fx-text-fill: #1f1300;" : " -fx-background-color: #374151; -fx-text-fill: #f3f4f6;"));
            strip.getChildren().add(chip);
        }
        strip.setStyle("-fx-background-color: rgba(0,0,0,0.6); -fx-background-radius: 20; -fx-padding: 6 12 6 12;");
        strip.setVisible(strip.getChildren().size() > 1);
        positionStrip();
    }

    private void positionStrip() {
        strip.applyCss();
        strip.autosize();
        strip.relocate(Math.max(0, (getWidth() - strip.getWidth()) / 2), 10);
    }

    public void toggleGrid() {
        showGrid = !showGrid;
        dirty = true;
    }

    /** Zooms so the whole map fills the window, and keeps doing so when the window is resized. */
    public void fit() {
        autoFit = true;
        applyFit();
        dirty = true;
    }

    public void zoomBy(double factor) {
        double cx = getWidth() / 2, cy = getHeight() / 2;
        double wx = (cx - offX) / zoom, wy = (cy - offY) / zoom;
        zoom = Math.max(0.05, Math.min(8, zoom * factor));
        offX = cx - wx * zoom;
        offY = cy - wy * zoom;
        autoFit = false;
        dirty = true;
    }

    private void applyFit() {
        double w = Math.max(50, getWidth()), h = Math.max(50, getHeight());
        double z = Math.min((w - 2 * MARGIN) / Math.max(1, grid.width()), (h - 2 * MARGIN) / Math.max(1, grid.height()));
        zoom = Math.max(0.05, Math.min(8, z));
        offX = (w - grid.width() * zoom) / 2;
        offY = (h - grid.height() * zoom) / 2;
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        canvas.setWidth(Math.max(1, getWidth()));
        canvas.setHeight(Math.max(1, getHeight()));
        if (autoFit) applyFit();
        dirty = true;
        redraw();
        positionStrip();
    }

    /** Called every frame by the window: redraws only if the map or the camera changed. */
    public void tick() {
        if (dirty || map.revision != drawnRevision) redraw();
    }

    public void redraw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setTransform(1, 0, 0, 1, 0, 0);
        g.setFill(Color.web("#0d0d0f"));
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        g.setTransform(zoom, 0, 0, zoom, offX, offY);
        g.setLineCap(StrokeLineCap.ROUND);
        MapRenderer.draw(g, map, grid, lib, zoom, showGrid, MapRenderer.Audience.PLAYERS);
        drawnRevision = map.revision;
        dirty = false;
        rebuildStrip();
    }
}
