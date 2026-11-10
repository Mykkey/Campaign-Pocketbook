package dnd.ui;

import dnd.map.world.WorldPalette;
import dnd.map.world.WorldTerrainCache;
import dnd.model.PaintLayer;
import dnd.model.WorldBiome;
import dnd.model.WorldMap;
import dnd.model.WorldStyle;
import dnd.model.WorldTerrain;
import dnd.storage.ProjectStore;
import javafx.animation.PauseTransition;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * The picture of a world map with its pan, zoom and painting. One map cell is one unit, so the camera maths is the
 * same as on the whiteboards. Painting changes the layers directly and only the tiles that changed are redrawn;
 * undo keeps just the rectangle each stroke touched.
 */
final class WorldMapView extends Pane {
    /** What one stroke changed, so it can be undone and redone cheaply. */
    private record Patch(boolean biomeLayer, int x, int y, int w, int h, byte[] before, byte[] after) { }

    private enum Mode { NONE, PAN, PAINT }

    private final WorldMap map;
    private final ProjectStore store;
    private final WorldToolState ts;
    private final Consumer<String> status;
    private final Canvas canvas = new Canvas();
    private final Deque<Patch> undoStack = new ArrayDeque<>(), redoStack = new ArrayDeque<>();
    private final PauseTransition cameraSaver = new PauseTransition(Duration.millis(800));

    private Mode mode = Mode.NONE;
    private boolean spaceDown;
    private double lastSX, lastSY, mouseSX = -1, mouseSY = -1;
    // the stroke being painted
    private PaintLayer layer;
    private boolean layerIsBiome;
    private byte[] layerBefore;
    private int minX, minY, maxX, maxY;
    private double lastCx, lastCy;

    WorldMapView(WorldMap map, ProjectStore store, WorldToolState ts, Consumer<String> status) {
        this.map = map;
        this.store = store;
        this.ts = ts;
        this.status = status;
        getChildren().add(canvas);
        setMinSize(0, 0);
        setFocusTraversable(true);
        cameraSaver.setOnFinished(e -> store.markWorldMapDirty(map, false));

        setOnMousePressed(this::onPressed);
        setOnMouseDragged(this::onDragged);
        setOnMouseReleased(this::onReleased);
        setOnMouseMoved(e -> {
            mouseSX = e.getX();
            mouseSY = e.getY();
            report(e.getX(), e.getY());
            redraw();
        });
        setOnMouseExited(e -> {
            mouseSX = -1;
            redraw();
        });
        setOnScroll(this::onScroll);
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.SPACE) {
                spaceDown = true;
                refreshCursor();
                e.consume();
            }
        });
        setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.SPACE) {
                spaceDown = false;
                refreshCursor();
            }
        });
        ts.tool.addListener((o, a, n) -> refreshCursor());
        ts.brush.addListener((o, a, n) -> redraw());
        Theme.darkProperty().addListener((o, a, n) -> redraw());
        refreshCursor();
        if (map.revision == 0 && map.zoom == 1 && map.offsetX == 40 && map.offsetY == 40) {
            javafx.application.Platform.runLater(this::fitToWindow);
        }
    }

    // ================================================================ camera

    private double zoom() {
        return map.zoom;
    }

    private double wx(double sx) {
        return (sx - map.offsetX) / map.zoom;
    }

    private double wy(double sy) {
        return (sy - map.offsetY) / map.zoom;
    }

    void fitToWindow() {
        double w = getWidth(), h = getHeight();
        if (w < 10 || h < 10) return;
        double z = Math.min(w / map.cols, h / map.rows) * 0.96;
        map.zoom = Math.max(0.05, z);
        map.offsetX = (w - map.cols * map.zoom) / 2;
        map.offsetY = (h - map.rows * map.zoom) / 2;
        cameraChanged();
        redraw();
    }

    private void cameraChanged() {
        cameraSaver.playFromStart();
    }

    @Override
    protected void layoutChildren() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        redraw();
    }

    private void onScroll(ScrollEvent e) {
        if (e.getDeltaY() == 0) return;
        double factor = Math.exp(e.getDeltaY() * 0.0015);
        double nz = Math.max(0.05, Math.min(16, map.zoom * factor));
        double sx = e.getX(), sy = e.getY();
        double px = wx(sx), py = wy(sy);
        map.zoom = nz;
        map.offsetX = sx - px * nz;
        map.offsetY = sy - py * nz;
        cameraChanged();
        redraw();
        e.consume();
    }

    // ================================================================ painting

    private void onPressed(MouseEvent e) {
        requestFocus();
        lastSX = e.getX();
        lastSY = e.getY();
        if (e.getButton() == MouseButton.MIDDLE || (e.getButton() == MouseButton.PRIMARY && (spaceDown || ts.tool.get() == WorldTool.HAND))) {
            mode = Mode.PAN;
            setCursor(Cursor.CLOSED_HAND);
            return;
        }
        if (e.getButton() != MouseButton.PRIMARY) return;
        WorldTool tool = ts.tool.get();
        if (tool == WorldTool.BASE || tool == WorldTool.BIOME) {
            mode = Mode.PAINT;
            beginStroke(tool == WorldTool.BIOME, wx(e.getX()), wy(e.getY()));
            redraw();
        }
    }

    private void onDragged(MouseEvent e) {
        mouseSX = e.getX();
        mouseSY = e.getY();
        if (mode == Mode.PAN) {
            map.offsetX += e.getX() - lastSX;
            map.offsetY += e.getY() - lastSY;
            cameraChanged();
        } else if (mode == Mode.PAINT) {
            continueStroke(wx(e.getX()), wy(e.getY()));
        }
        lastSX = e.getX();
        lastSY = e.getY();
        report(e.getX(), e.getY());
        redraw();
    }

    private void onReleased(MouseEvent e) {
        if (mode == Mode.PAINT) endStroke();
        mode = Mode.NONE;
        refreshCursor();
        redraw();
    }

    private void beginStroke(boolean biome, double cx, double cy) {
        layerIsBiome = biome;
        layer = biome ? map.biome() : map.base();
        layerBefore = layer.cells.clone();
        minX = Integer.MAX_VALUE;
        minY = Integer.MAX_VALUE;
        maxX = Integer.MIN_VALUE;
        maxY = Integer.MIN_VALUE;
        lastCx = cx;
        lastCy = cy;
        stamp(cx, cy);
    }

    /** Paints along the line from the last point so a fast drag leaves no gaps. */
    private void continueStroke(double cx, double cy) {
        double dist = Math.hypot(cx - lastCx, cy - lastCy);
        double step = Math.max(0.5, ts.brush.get() / 4.0);
        int n = (int) Math.ceil(dist / step);
        for (int i = 1; i <= n; i++) {
            double t = (double) i / n;
            stamp(lastCx + (cx - lastCx) * t, lastCy + (cy - lastCy) * t);
        }
        lastCx = cx;
        lastCy = cy;
    }

    /** Paints one round dab of the brush centred on (cx, cy), in map cells. */
    private void stamp(double cx, double cy) {
        double r = Math.max(0.5, ts.brush.get() / 2.0);
        int x0 = (int) Math.floor(cx - r), x1 = (int) Math.ceil(cx + r), y0 = (int) Math.floor(cy - r), y1 = (int) Math.ceil(cy + r);
        byte value;
        if (layerIsBiome) {
            WorldBiome b = ts.biome.get();
            value = b == null ? 0 : b.code;
        } else {
            WorldTerrain t = ts.terrain.get();
            value = t == null ? 0 : t.code;
        }
        PaintLayer base = map.base();
        boolean changed = false;
        for (int y = Math.max(0, y0); y <= Math.min(map.rows - 1, y1); y++) {
            for (int x = Math.max(0, x0); x <= Math.min(map.cols - 1, x1); x++) {
                double dx = x + 0.5 - cx, dy = y + 0.5 - cy;
                if (dx * dx + dy * dy > r * r) continue;
                if (layerIsBiome && value != 0) { // biomes only go on land and ice
                    WorldTerrain t = WorldTerrain.of(base.get(x, y));
                    if (t != WorldTerrain.LAND && t != WorldTerrain.ICE) continue;
                }
                if (layer.set(x, y, value)) changed = true;
            }
        }
        if (!changed) return;
        minX = Math.min(minX, Math.max(0, x0));
        minY = Math.min(minY, Math.max(0, y0));
        maxX = Math.max(maxX, Math.min(map.cols - 1, x1));
        maxY = Math.max(maxY, Math.min(map.rows - 1, y1));
        WorldTerrainCache.of(map).invalidateCells(Math.max(0, x0), Math.max(0, y0), Math.min(map.cols - 1, x1), Math.min(map.rows - 1, y1));
    }

    private void endStroke() {
        if (layer == null) return;
        PaintLayer finished = layer;
        layer = null;
        if (minX > maxX) return; // nothing changed
        int w = maxX - minX + 1, h = maxY - minY + 1;
        byte[] before = new byte[w * h], after = finished.copyRect(minX, minY, w, h);
        for (int j = 0; j < h; j++) System.arraycopy(layerBefore, (minY + j) * finished.w + minX, before, j * w, w);
        undoStack.addLast(new Patch(layerIsBiome, minX, minY, w, h, before, after));
        if (undoStack.size() > 100) undoStack.removeFirst();
        redoStack.clear();
        layerBefore = null;
        store.markWorldMapDirty(map, true);
    }

    void undo() {
        if (undoStack.isEmpty()) return;
        Patch p = undoStack.removeLast();
        apply(p, p.before());
        redoStack.addLast(p);
    }

    void redo() {
        if (redoStack.isEmpty()) return;
        Patch p = redoStack.removeLast();
        apply(p, p.after());
        undoStack.addLast(p);
    }

    private void apply(Patch p, byte[] data) {
        (p.biomeLayer() ? map.biome() : map.base()).putRect(p.x(), p.y(), p.w(), p.h(), data);
        WorldTerrainCache.of(map).invalidateCells(p.x(), p.y(), p.x() + p.w() - 1, p.y() + p.h() - 1);
        store.markWorldMapDirty(map, true);
        redraw();
    }

    /** Forgets everything cached about the painted picture (after the art style changed). */
    void styleChanged() {
        WorldTerrainCache.of(map).invalidateAll();
        store.markWorldMapDirty(map, true);
        redraw();
    }

    // ================================================================ drawing

    private void refreshCursor() {
        if (mode == Mode.PAN) return;
        setCursor(spaceDown || ts.tool.get() == WorldTool.HAND ? Cursor.OPEN_HAND : Cursor.CROSSHAIR);
    }

    private void report(double sx, double sy) {
        double cx = wx(sx), cy = wy(sy);
        if (cx < 0 || cy < 0 || cx >= map.cols || cy >= map.rows) {
            status.accept("");
            return;
        }
        status.accept(String.format("%,.0f mi east, %,.0f mi south    zoom %d%%", cx * map.milesPerCell, cy * map.milesPerCell, Math.round(map.zoom * 100)));
    }

    void redraw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double W = canvas.getWidth(), H = canvas.getHeight();
        g.setTransform(1, 0, 0, 1, 0, 0);
        g.setFill(Theme.isDark() ? Color.web("#17181b") : Color.web("#a79f89"));
        g.fillRect(0, 0, W, H);

        g.setTransform(zoom(), 0, 0, zoom(), map.offsetX, map.offsetY);
        WorldTerrainCache cache = WorldTerrainCache.of(map);
        Image img = cache.image(wx(0), wy(0), wx(W), wy(H));
        g.setImageSmoothing(map.style != WorldStyle.RETRO_PIXEL);
        g.drawImage(img, 0, 0, map.cols, map.rows);

        g.setStroke(Color.web("#000000", 0.55));
        g.setLineWidth(1.5 / zoom());
        g.strokeRect(0, 0, map.cols, map.rows);

        // the brush
        g.setTransform(1, 0, 0, 1, 0, 0);
        WorldTool tool = ts.tool.get();
        if (mouseSX >= 0 && (tool == WorldTool.BASE || tool == WorldTool.BIOME) && !spaceDown) {
            double r = ts.brush.get() / 2.0 * zoom();
            g.setStroke(Color.web("#ffffff", 0.95));
            g.setLineWidth(1.4);
            g.strokeOval(mouseSX - r, mouseSY - r, 2 * r, 2 * r);
            g.setStroke(Color.web("#000000", 0.7));
            g.setLineWidth(0.8);
            g.strokeOval(mouseSX - r - 1, mouseSY - r - 1, 2 * r + 2, 2 * r + 2);
        }
    }

    /** The colour used to show a biome in the toolbar. */
    static Color swatch(WorldStyle style, WorldBiome b) {
        return argb(WorldPalette.of(style).biomeColor(b));
    }

    static Color argb(int c) {
        return Color.rgb((c >> 16) & 255, (c >> 8) & 255, c & 255);
    }
}
