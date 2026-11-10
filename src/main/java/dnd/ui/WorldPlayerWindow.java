package dnd.ui;

import dnd.map.AssetLibrary;
import dnd.map.world.WorldComposer;
import dnd.model.WorldMap;
import dnd.storage.ProjectStore;
import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * A separate window showing a world map for the players, to drag onto the table screen. It shows only what the players may
 * know: nothing hidden, and the unexplored parts covered. It has its own pan and zoom, and keeps up with the DM's edits.
 */
public final class WorldPlayerWindow {
    private static final Preferences PREFS = Preferences.userNodeForPackage(WorldPlayerWindow.class).node("worldPlayer");
    private static final Map<String, WorldPlayerWindow> OPEN = new HashMap<>();

    private final Stage stage = new Stage();
    private final View view;
    private final AnimationTimer timer;
    private final String mapId;
    private double winX, winY, winW, winH;

    /** Opens the player window for a map, or brings the existing one to the front. */
    public static void open(Window owner, WorldMap map, ProjectStore store) {
        WorldPlayerWindow existing = OPEN.get(map.id);
        if (existing != null) {
            existing.stage.toFront();
            existing.stage.requestFocus();
            return;
        }
        new WorldPlayerWindow(owner, map, store).show();
    }

    public static void close(String mapId) {
        WorldPlayerWindow w = OPEN.get(mapId);
        if (w != null) w.stage.close();
    }

    public static void closeAll() {
        for (WorldPlayerWindow w : new ArrayList<>(OPEN.values())) w.stage.close();
    }

    public static boolean isOpen(String mapId) {
        return OPEN.containsKey(mapId);
    }

    private WorldPlayerWindow(Window owner, WorldMap map, ProjectStore store) {
        this.mapId = map.id;
        this.view = new View(map, store.assets());

        Label hint = new Label("Drag this window to the playtable screen   |   F11 fullscreen   |   scroll to zoom, drag to pan   |   F fit   |   G grid");
        hint.setStyle("-fx-background-color: #000000b0; -fx-text-fill: white; -fx-padding: 6 14 6 14; -fx-background-radius: 14;");
        hint.setMouseTransparent(true);
        StackPane.setAlignment(hint, Pos.BOTTOM_CENTER);
        StackPane.setMargin(hint, new Insets(0, 0, 18, 0));
        StackPane root = new StackPane(view, hint);
        root.setStyle("-fx-background-color: #0d0d0f;");

        Scene scene = new Scene(root, 1280, 800, Color.web("#0d0d0f"));
        scene.setOnKeyPressed(e -> {
            KeyCode k = e.getCode();
            if (k == KeyCode.F11) stage.setFullScreen(!stage.isFullScreen());
            else if (k == KeyCode.F) view.fit();
            else if (k == KeyCode.G) view.toggleGrid();
            else if (k == KeyCode.PLUS || k == KeyCode.EQUALS || k == KeyCode.ADD) view.zoomBy(1.2);
            else if (k == KeyCode.MINUS || k == KeyCode.SUBTRACT) view.zoomBy(1 / 1.2);
            else return;
            e.consume();
        });

        stage.setTitle(map.name + " - Player view");
        stage.setScene(scene);
        stage.setFullScreenExitHint("Press F11 or Esc to leave fullscreen");
        stage.setFullScreenExitKeyCombination(KeyCombination.keyCombination("Esc"));
        try (InputStream in = WorldPlayerWindow.class.getResourceAsStream("/icon.png")) {
            if (in != null) stage.getIcons().add(new Image(in));
        } catch (java.io.IOException ignored) {
            // the window works without an icon
        }
        restoreBounds(owner);

        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                view.tick();
            }
        };
        stage.xProperty().addListener((o, a, n) -> trackBounds());
        stage.yProperty().addListener((o, a, n) -> trackBounds());
        stage.widthProperty().addListener((o, a, n) -> trackBounds());
        stage.heightProperty().addListener((o, a, n) -> trackBounds());
        stage.setOnHidden(e -> {
            timer.stop();
            saveBounds();
            OPEN.remove(mapId);
        });

        PauseTransition wait = new PauseTransition(Duration.seconds(7));
        FadeTransition fade = new FadeTransition(Duration.seconds(1.5), hint);
        fade.setToValue(0);
        wait.setOnFinished(e -> fade.play());
        wait.play();
    }

    private void show() {
        OPEN.put(mapId, this);
        stage.show();
        timer.start();
        if (PREFS.getBoolean("fullscreen", false)) stage.setFullScreen(true);
        view.requestFocus();
    }

    private void trackBounds() {
        if (stage.isFullScreen() || stage.isIconified()) return;
        winX = stage.getX();
        winY = stage.getY();
        winW = stage.getWidth();
        winH = stage.getHeight();
    }

    private void restoreBounds(Window owner) {
        double x = PREFS.getDouble("x", Double.NaN), y = PREFS.getDouble("y", Double.NaN);
        double w = PREFS.getDouble("w", 1280), h = PREFS.getDouble("h", 800);
        boolean onAScreen = !Double.isNaN(x) && !Double.isNaN(y)
                && !Screen.getScreensForRectangle(x, y, Math.max(100, w), Math.max(100, h)).isEmpty();
        if (onAScreen) {
            stage.setX(x);
            stage.setY(y);
            stage.setWidth(w);
            stage.setHeight(h);
            winX = x;
            winY = y;
            winW = w;
            winH = h;
        } else if (owner != null) {
            stage.setX(owner.getX() + 60);
            stage.setY(owner.getY() + 60);
        }
    }

    private void saveBounds() {
        try {
            if (winW > 0 && winH > 0) {
                PREFS.putDouble("x", winX);
                PREFS.putDouble("y", winY);
                PREFS.putDouble("w", winW);
                PREFS.putDouble("h", winH);
            }
            PREFS.putBoolean("fullscreen", stage.isFullScreen());
        } catch (RuntimeException ignored) {
            // preferences are a convenience only
        }
    }

    /** The picture itself: read-only, with its own pan and zoom. */
    static final class View extends Pane {
        private static final double MARGIN = 24;
        private final WorldMap map;
        private final AssetLibrary lib;
        private final Canvas canvas = new Canvas();
        private double zoom = 1, offX, offY, dragX, dragY;
        private boolean autoFit = true, dirty = true, showGrid;
        private int drawnRevision = -1, drawnReveal = -1;

        View(WorldMap map, AssetLibrary lib) {
            this.map = map;
            this.lib = lib;
            this.showGrid = true;
            getChildren().add(canvas);
            setMinSize(0, 0);
            setFocusTraversable(true);
            setOnScroll(e -> {
                if (e.getDeltaY() == 0) return;
                double nz = Math.max(0.05, Math.min(16, zoom * Math.exp(e.getDeltaY() * 0.0015)));
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

        void toggleGrid() {
            showGrid = !showGrid;
            dirty = true;
        }

        void fit() {
            autoFit = true;
            applyFit();
            dirty = true;
        }

        void zoomBy(double factor) {
            double cx = getWidth() / 2, cy = getHeight() / 2;
            double wx = (cx - offX) / zoom, wy = (cy - offY) / zoom;
            zoom = Math.max(0.05, Math.min(16, zoom * factor));
            offX = cx - wx * zoom;
            offY = cy - wy * zoom;
            autoFit = false;
            dirty = true;
        }

        private void applyFit() {
            double w = Math.max(50, getWidth()), h = Math.max(50, getHeight());
            zoom = Math.max(0.05, Math.min(16, Math.min((w - 2 * MARGIN) / map.cols, (h - 2 * MARGIN) / map.rows)));
            offX = (w - map.cols * zoom) / 2;
            offY = (h - map.rows * zoom) / 2;
        }

        @Override
        protected void layoutChildren() {
            super.layoutChildren();
            canvas.setWidth(Math.max(1, getWidth()));
            canvas.setHeight(Math.max(1, getHeight()));
            if (autoFit) applyFit();
            dirty = true;
            redraw();
        }

        /** Called every frame: redraws only if the map or the camera changed. */
        void tick() {
            if (dirty || map.revision != drawnRevision || map.revealVersion != drawnReveal) redraw();
        }

        void redraw() {
            GraphicsContext g = canvas.getGraphicsContext2D();
            g.setTransform(1, 0, 0, 1, 0, 0);
            g.setFill(Color.web("#0d0d0f"));
            g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
            g.setTransform(zoom, 0, 0, zoom, offX, offY);
            double vx0 = -offX / zoom, vy0 = -offY / zoom, vx1 = (canvas.getWidth() - offX) / zoom, vy1 = (canvas.getHeight() - offY) / zoom;
            WorldComposer.draw(g, map, lib, WorldComposer.Audience.PLAYERS, zoom, vx0, vy0, vx1, vy1, showGrid, true);
            drawnRevision = map.revision;
            drawnReveal = map.revealVersion;
            dirty = false;
        }
    }
}
