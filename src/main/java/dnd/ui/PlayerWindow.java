package dnd.ui;

import dnd.model.BattleMap;
import dnd.storage.ProjectStore;
import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
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
 * A separate window showing a battlemap for the players. It can be dragged to another monitor (a
 * "playtable" screen) and made fullscreen with F11. It remembers where it was last time, including the
 * monitor, and keeps updating as the map is edited.
 */
public final class PlayerWindow {
    private static final Preferences PREFS = Preferences.userNodeForPackage(PlayerWindow.class);
    private static final Map<String, PlayerWindow> OPEN = new HashMap<>();

    private final Stage stage = new Stage();
    private final PlayerView view;
    private final AnimationTimer timer;
    private final String mapId;
    // last bounds while windowed (while fullscreen the stage reports the whole screen)
    private double winX, winY, winW, winH;

    /** Opens the player window for a map, or brings the existing one to the front. */
    public static void open(Window owner, BattleMap map, ProjectStore store) {
        PlayerWindow existing = OPEN.get(map.id);
        if (existing != null) {
            existing.stage.toFront();
            existing.stage.requestFocus();
            return;
        }
        new PlayerWindow(owner, map, store).show();
    }

    /** Closes the player window of one map (e.g. because the map was deleted). */
    public static void close(String mapId) {
        PlayerWindow w = OPEN.get(mapId);
        if (w != null) w.stage.close();
    }

    public static void closeAll() {
        for (PlayerWindow w : new ArrayList<>(OPEN.values())) w.stage.close();
    }

    public static boolean isOpen(String mapId) {
        return OPEN.containsKey(mapId);
    }

    private PlayerWindow(Window owner, BattleMap map, ProjectStore store) {
        this.mapId = map.id;
        this.view = new PlayerView(map, store);

        Label hint = new Label("Drag this window to the playtable screen   |   F11 fullscreen   |   scroll to zoom, drag to pan"
                + "   |   F fit   |   G grid   |   I turn order");
        hint.setStyle("-fx-background-color: #000000b0; -fx-text-fill: white; -fx-padding: 6 14 6 14; -fx-background-radius: 14;");
        hint.setMouseTransparent(true);
        StackPane.setAlignment(hint, Pos.BOTTOM_CENTER);
        StackPane.setMargin(hint, new Insets(0, 0, 18, 0));
        StackPane root = new StackPane(view, hint);
        root.setStyle("-fx-background-color: #0d0d0f;");

        Scene scene = new Scene(root, 1280, 800, Color.web("#0d0d0f"));
        scene.setOnKeyPressed(e -> {
            KeyCode k = e.getCode();
            if (k == KeyCode.F11) {
                stage.setFullScreen(!stage.isFullScreen());
            } else if (k == KeyCode.F) {
                view.fit();
            } else if (k == KeyCode.G) {
                view.toggleGrid();
            } else if (k == KeyCode.I) {
                view.toggleInitiative();
            } else if (k == KeyCode.PLUS || k == KeyCode.EQUALS || k == KeyCode.ADD) {
                view.zoomBy(1.2);
            } else if (k == KeyCode.MINUS || k == KeyCode.SUBTRACT) {
                view.zoomBy(1 / 1.2);
            } else {
                return;
            }
            e.consume();
        });

        stage.setTitle(map.name + " - Player view");
        stage.setScene(scene);
        stage.setFullScreenExitHint("Press F11 or Esc to leave fullscreen");
        stage.setFullScreenExitKeyCombination(KeyCombination.keyCombination("Esc"));
        try (InputStream in = PlayerWindow.class.getResourceAsStream("/icon.png")) {
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

        // the shortcut hint fades away after a few seconds
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
            winX = x; winY = y; winW = w; winH = h;
        } else if (owner != null) {
            // first time: appear beside the main window
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
}
