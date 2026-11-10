package dnd.ui;

import dnd.map.AssetLibrary;
import dnd.map.world.ScatterPlanner;
import dnd.map.world.WorldAssets;
import dnd.map.world.WorldPalette;
import dnd.map.world.WorldStampRenderer;
import dnd.map.world.WorldTerrainCache;
import dnd.model.PaintLayer;
import dnd.model.WorldBiome;
import dnd.model.WorldMap;
import dnd.model.WorldStamp;
import dnd.model.WorldStyle;
import dnd.model.WorldTerrain;
import dnd.storage.Json;
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
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The picture of a world map with its pan, zoom, painting and symbols. One map cell is one unit, so the camera maths
 * is the same as on the whiteboards. Painting changes the layers directly and only the tiles that changed are
 * redrawn; every kind of edit undoes as one small step.
 */
final class WorldMapView extends Pane {
    /** One undoable step. */
    private record Entry(Runnable undo, Runnable redo) { }

    private enum Mode { NONE, PAN, PAINT, SCATTER, MOVE, MARQUEE, RESIZE }

    private final WorldMap map;
    private final ProjectStore store;
    private final WorldToolState ts;
    private final Consumer<String> status;
    private final AssetLibrary lib;
    private final Canvas canvas = new Canvas();
    private final Deque<Entry> undoStack = new ArrayDeque<>(), redoStack = new ArrayDeque<>();
    private final PauseTransition cameraSaver = new PauseTransition(Duration.millis(800));
    private final Set<String> selected = new LinkedHashSet<>();
    private final Random rnd = new Random();

    private Mode mode = Mode.NONE;
    private boolean spaceDown;
    private double lastSX, lastSY, mouseSX = -1, mouseSY = -1;
    private double lastWX, lastWY, mqX0, mqY0, mqX1, mqY1;
    private boolean moved;
    /** The stamps as they were when a gesture began, for undo. */
    private String stampsBefore;
    // the paint stroke being drawn
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
        this.lib = store.assets();
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
        setOnKeyPressed(this::onKey);
        setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.SPACE) {
                spaceDown = false;
                refreshCursor();
            }
        });
        ts.tool.addListener((o, a, n) -> {
            refreshCursor();
            redraw();
        });
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

    // ================================================================ mouse

    private void onPressed(MouseEvent e) {
        requestFocus();
        lastSX = e.getX();
        lastSY = e.getY();
        double px = wx(e.getX()), py = wy(e.getY());
        lastWX = px;
        lastWY = py;
        WorldTool tool = ts.tool.get();
        if (e.getButton() == MouseButton.MIDDLE || (e.getButton() == MouseButton.PRIMARY && (spaceDown || tool == WorldTool.HAND))) {
            mode = Mode.PAN;
            setCursor(Cursor.CLOSED_HAND);
            return;
        }
        if (e.getButton() != MouseButton.PRIMARY) return;
        switch (tool) {
            case BASE, BIOME -> {
                mode = Mode.PAINT;
                beginStroke(tool == WorldTool.BIOME, px, py);
            }
            case STAMP -> placeStamp(px, py);
            case SCATTER -> {
                if (ts.armed.get() == null) return;
                mode = Mode.SCATTER;
                stampsBefore = Json.write(map.stamps);
                lastCx = px;
                lastCy = py;
                scatterAt(px, py);
            }
            case SELECT -> selectPressed(px, py, e.isShiftDown());
            default -> { }
        }
        redraw();
    }

    private void onDragged(MouseEvent e) {
        mouseSX = e.getX();
        mouseSY = e.getY();
        double px = wx(e.getX()), py = wy(e.getY());
        switch (mode) {
            case PAN -> {
                map.offsetX += e.getX() - lastSX;
                map.offsetY += e.getY() - lastSY;
                cameraChanged();
            }
            case PAINT -> continueStroke(px, py);
            case SCATTER -> continueScatter(px, py);
            case MOVE -> {
                double dx = px - lastWX, dy = py - lastWY;
                if (dx != 0 || dy != 0) {
                    moved = true;
                    for (WorldStamp s : selectedStamps()) {
                        s.x += dx;
                        s.y += dy;
                    }
                }
            }
            case RESIZE -> {
                WorldStamp s = singleSelected();
                if (s != null) {
                    moved = true;
                    s.size = Math.max(0.5, Math.abs(WorldStampRenderer.toLocal(s, px, py)[0]) * 2);
                }
            }
            case MARQUEE -> {
                mqX1 = px;
                mqY1 = py;
            }
            default -> { }
        }
        lastSX = e.getX();
        lastSY = e.getY();
        lastWX = px;
        lastWY = py;
        report(e.getX(), e.getY());
        redraw();
    }

    private void onReleased(MouseEvent e) {
        switch (mode) {
            case PAINT -> endStroke();
            case SCATTER -> finishStampEdit();
            case MOVE, RESIZE -> {
                if (moved) finishStampEdit();
            }
            case MARQUEE -> {
                double x0 = Math.min(mqX0, mqX1), x1 = Math.max(mqX0, mqX1), y0 = Math.min(mqY0, mqY1), y1 = Math.max(mqY0, mqY1);
                for (WorldStamp s : map.stamps) if (s.x >= x0 && s.x <= x1 && s.y >= y0 && s.y <= y1) selected.add(s.id);
            }
            default -> { }
        }
        mode = Mode.NONE;
        moved = false;
        refreshCursor();
        redraw();
    }

    // ================================================================ keys

    private void onKey(javafx.scene.input.KeyEvent e) {
        if (e.getCode() == KeyCode.SPACE) {
            spaceDown = true;
            refreshCursor();
            e.consume();
        } else if (e.getCode() == KeyCode.OPEN_BRACKET && !e.isShortcutDown()) {
            rotateSelection(-15);
            e.consume();
        } else if (e.getCode() == KeyCode.CLOSE_BRACKET && !e.isShortcutDown()) {
            rotateSelection(15);
            e.consume();
        } else if (e.getCode() == KeyCode.F && !e.isShortcutDown()) {
            flipSelection();
            e.consume();
        }
    }

    // ================================================================ painting

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
        dab(cx, cy);
    }

    /** Paints along the line from the last point so a fast drag leaves no gaps. */
    private void continueStroke(double cx, double cy) {
        double dist = Math.hypot(cx - lastCx, cy - lastCy);
        double step = Math.max(0.5, ts.brush.get() / 4.0);
        int n = (int) Math.ceil(dist / step);
        for (int i = 1; i <= n; i++) {
            double t = (double) i / n;
            dab(lastCx + (cx - lastCx) * t, lastCy + (cy - lastCy) * t);
        }
        lastCx = cx;
        lastCy = cy;
    }

    /** Paints one round dab of the brush centred on (cx, cy), in map cells. */
    private void dab(double cx, double cy) {
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
        boolean biome = layerIsBiome;
        layer = null;
        if (minX > maxX) return; // nothing changed
        int w = maxX - minX + 1, h = maxY - minY + 1;
        byte[] before = new byte[w * h], after = finished.copyRect(minX, minY, w, h);
        for (int j = 0; j < h; j++) System.arraycopy(layerBefore, (minY + j) * finished.w + minX, before, j * w, w);
        layerBefore = null;
        int x = minX, y = minY;
        push(new Entry(() -> applyPatch(biome, x, y, w, h, before), () -> applyPatch(biome, x, y, w, h, after)));
        store.markWorldMapDirty(map, true);
    }

    private void applyPatch(boolean biome, int x, int y, int w, int h, byte[] data) {
        (biome ? map.biome() : map.base()).putRect(x, y, w, h, data);
        WorldTerrainCache.of(map).invalidateCells(x, y, x + w - 1, y + h - 1);
        store.markWorldMapDirty(map, true);
        redraw();
    }

    // ================================================================ symbols

    private WorldStamp newStamp(String feature, double x, double y) {
        WorldStamp s = new WorldStamp(feature, x, y);
        s.size = ts.stampSize.get();
        s.style = ts.stampStyle.get();
        return s;
    }

    private void placeStamp(double px, double py) {
        String f = ts.armed.get();
        if (f == null || WorldAssets.get(f) == null) return;
        String before = Json.write(map.stamps);
        WorldStamp s = newStamp(f, px, py);
        map.stamps.add(s);
        selected.clear();
        selected.add(s.id);
        stampEdit(before);
    }

    /** Saves what a stamp gesture changed as one undo step. */
    private void finishStampEdit() {
        if (stampsBefore != null) stampEdit(stampsBefore);
        stampsBefore = null;
    }

    private void stampEdit(String before) {
        String after = Json.write(map.stamps);
        if (before.equals(after)) return;
        push(new Entry(() -> restoreStamps(before), () -> restoreStamps(after)));
        store.markWorldMapDirty(map, true);
    }

    private void restoreStamps(String json) {
        map.stamps.clear();
        map.stamps.addAll(Json.readStamps(json));
        selected.removeIf(id -> map.stamps.stream().noneMatch(s -> s.id.equals(id)));
        store.markWorldMapDirty(map, true);
        redraw();
    }

    private void continueScatter(double px, double py) {
        double dist = Math.hypot(px - lastCx, py - lastCy);
        double step = Math.max(1, ts.brush.get() / 3.0);
        int n = (int) Math.floor(dist / step);
        for (int i = 1; i <= n; i++) {
            double t = (double) i / n;
            scatterAt(lastCx + (px - lastCx) * t, lastCy + (py - lastCy) * t);
        }
        if (n > 0) {
            lastCx = px;
            lastCy = py;
        }
    }

    /** Plants symbols of the armed kind (and its relatives) inside the brush circle, spread out and on suitable ground. */
    private void scatterAt(double cx, double cy) {
        List<WorldAssets.Feature> variants = WorldAssets.variants(ts.armed.get());
        if (variants.isEmpty()) return;
        double r = Math.max(1, ts.brush.get() / 2.0);
        double w = ts.stampSize.get() > 0 ? ts.stampSize.get() : variants.get(0).w();
        double spacing = Math.max(0.4, w * (1.0 - 0.07 * ts.density.get()));
        List<double[]> existing = new ArrayList<>();
        for (WorldStamp s : map.stamps) {
            if (Math.abs(s.x - cx) < r + spacing && Math.abs(s.y - cy) < r + spacing) existing.add(new double[]{s.x, s.y});
        }
        int attempts = Math.max(3, (int) (r * r * 0.06 * ts.density.get()));
        PaintLayer base = map.base();
        for (double[] p : ScatterPlanner.plan(cx, cy, r, spacing, existing, attempts, rnd)) {
            if (p[0] < 0 || p[1] < 0 || p[0] >= map.cols || p[1] >= map.rows) continue;
            WorldAssets.Feature f = variants.get(rnd.nextInt(variants.size()));
            WorldTerrain t = WorldTerrain.of(base.get((int) p[0], (int) p[1]));
            boolean water = "Water".equals(f.category());
            if (!water && (t == WorldTerrain.OCEAN || t == WorldTerrain.LAKE || t == WorldTerrain.LAVA)) continue; // not in the sea
            WorldStamp s = newStamp(f.id(), p[0], p[1]);
            double baseW = ts.stampSize.get() > 0 ? ts.stampSize.get() : f.w();
            s.size = baseW * (0.82 + 0.36 * rnd.nextDouble());
            s.flipX = rnd.nextBoolean();
            map.stamps.add(s);
        }
    }

    // ---- selecting and editing

    private List<WorldStamp> selectedStamps() {
        List<WorldStamp> out = new ArrayList<>();
        for (WorldStamp s : map.stamps) if (selected.contains(s.id)) out.add(s);
        return out;
    }

    private WorldStamp singleSelected() {
        return selected.size() == 1 ? map.stamps.stream().filter(s -> selected.contains(s.id)).findFirst().orElse(null) : null;
    }

    private boolean onHandle(WorldStamp s, double px, double py) {
        double[] h = WorldStampRenderer.handleOf(s);
        double tol = 8 / zoom();
        return Math.abs(px - h[0]) <= tol && Math.abs(py - h[1]) <= tol;
    }

    private void selectPressed(double px, double py, boolean shift) {
        WorldStamp single = singleSelected();
        if (single != null && onHandle(single, px, py)) {
            mode = Mode.RESIZE;
            stampsBefore = Json.write(map.stamps);
            return;
        }
        WorldStamp hit = WorldStampRenderer.hit(map, px, py, true);
        if (hit != null) {
            if (shift) {
                if (!selected.remove(hit.id)) selected.add(hit.id);
            } else if (!selected.contains(hit.id)) {
                selected.clear();
                selected.add(hit.id);
            }
            mode = Mode.MOVE;
            stampsBefore = Json.write(map.stamps);
        } else {
            if (!shift) selected.clear();
            mode = Mode.MARQUEE;
            mqX0 = mqX1 = px;
            mqY0 = mqY1 = py;
        }
    }

    boolean hasSelection() {
        return !selected.isEmpty();
    }

    /** Removes the selected symbols. Returns false if nothing was selected. */
    boolean deleteSelection() {
        if (selected.isEmpty()) return false;
        String before = Json.write(map.stamps);
        map.stamps.removeIf(s -> selected.contains(s.id));
        selected.clear();
        stampEdit(before);
        redraw();
        return true;
    }

    /** Drops the selection and puts away the armed symbol. Returns true if there was anything to drop. */
    boolean escape() {
        boolean any = !selected.isEmpty() || ts.armed.get() != null;
        selected.clear();
        ts.armed.set(null);
        redraw();
        return any;
    }

    void duplicateSelection() {
        if (selected.isEmpty()) return;
        String before = Json.write(map.stamps);
        Set<String> copies = new LinkedHashSet<>();
        for (WorldStamp s : selectedStamps()) {
            WorldStamp c = s.copy();
            c.x += 3;
            c.y += 3;
            map.stamps.add(c);
            copies.add(c.id);
        }
        selected.clear();
        selected.addAll(copies);
        stampEdit(before);
        redraw();
    }

    void rotateSelection(double degrees) {
        if (selected.isEmpty()) return;
        String before = Json.write(map.stamps);
        for (WorldStamp s : selectedStamps()) s.rotation = ((s.rotation + degrees) % 360 + 360) % 360;
        stampEdit(before);
        redraw();
    }

    void flipSelection() {
        if (selected.isEmpty()) return;
        String before = Json.write(map.stamps);
        for (WorldStamp s : selectedStamps()) s.flipX = !s.flipX;
        stampEdit(before);
        redraw();
    }

    /** Gives the selected symbols an art style (null = follow the map's style). */
    void styleSelection(WorldStyle style) {
        if (selected.isEmpty()) return;
        String before = Json.write(map.stamps);
        for (WorldStamp s : selectedStamps()) s.style = style;
        stampEdit(before);
        redraw();
    }

    // ================================================================ undo

    private void push(Entry e) {
        undoStack.addLast(e);
        if (undoStack.size() > 100) undoStack.removeFirst();
        redoStack.clear();
    }

    void undo() {
        if (undoStack.isEmpty()) return;
        Entry e = undoStack.removeLast();
        e.undo().run();
        redoStack.addLast(e);
    }

    void redo() {
        if (redoStack.isEmpty()) return;
        Entry e = redoStack.removeLast();
        e.redo().run();
        undoStack.addLast(e);
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
        WorldTool t = ts.tool.get();
        setCursor(spaceDown || t == WorldTool.HAND ? Cursor.OPEN_HAND : t == WorldTool.SELECT ? Cursor.DEFAULT : Cursor.CROSSHAIR);
    }

    private void report(double sx, double sy) {
        double cx = wx(sx), cy = wy(sy);
        if (cx < 0 || cy < 0 || cx >= map.cols || cy >= map.rows) {
            status.accept("");
            return;
        }
        status.accept(String.format("%,.0f mi east, %,.0f mi south    zoom %d%%    %d symbols", cx * map.milesPerCell, cy * map.milesPerCell,
                Math.round(map.zoom * 100), map.stamps.size()));
    }

    void redraw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double W = canvas.getWidth(), H = canvas.getHeight();
        g.setTransform(1, 0, 0, 1, 0, 0);
        g.setFill(Theme.isDark() ? Color.web("#17181b") : Color.web("#a79f89"));
        g.fillRect(0, 0, W, H);

        g.setTransform(zoom(), 0, 0, zoom(), map.offsetX, map.offsetY);
        double vx0 = wx(0), vy0 = wy(0), vx1 = wx(W), vy1 = wy(H);
        WorldTerrainCache cache = WorldTerrainCache.of(map);
        Image img = cache.image(vx0, vy0, vx1, vy1);
        g.setImageSmoothing(map.style != WorldStyle.RETRO_PIXEL);
        g.drawImage(img, 0, 0, map.cols, map.rows);

        WorldStampRenderer.draw(g, map, lib, zoom(), vx0, vy0, vx1, vy1, true);

        g.setStroke(Color.web("#000000", 0.55));
        g.setLineWidth(1.5 / zoom());
        g.strokeRect(0, 0, map.cols, map.rows);

        // selection outlines and the resize handle
        g.setStroke(Color.web("#2563eb"));
        g.setLineWidth(1.5 / zoom());
        g.setLineDashes(6 / zoom(), 4 / zoom());
        for (WorldStamp s : selectedStamps()) {
            double[][] c = WorldStampRenderer.corners(s);
            g.strokePolygon(new double[]{c[0][0], c[1][0], c[2][0], c[3][0]}, new double[]{c[0][1], c[1][1], c[2][1], c[3][1]}, 4);
        }
        g.setLineDashes((double[]) null);
        WorldStamp single = singleSelected();
        if (single != null) {
            double[] h = WorldStampRenderer.handleOf(single);
            double s = 9 / zoom();
            g.setFill(Color.WHITE);
            g.fillRect(h[0] - s / 2, h[1] - s / 2, s, s);
            g.strokeRect(h[0] - s / 2, h[1] - s / 2, s, s);
        }
        if (mode == Mode.MARQUEE) {
            g.setFill(Color.web("#2563eb33"));
            double l = Math.min(mqX0, mqX1), t = Math.min(mqY0, mqY1);
            g.fillRect(l, t, Math.abs(mqX1 - mqX0), Math.abs(mqY1 - mqY0));
            g.strokeRect(l, t, Math.abs(mqX1 - mqX0), Math.abs(mqY1 - mqY0));
        }

        // the brush
        g.setTransform(1, 0, 0, 1, 0, 0);
        WorldTool tool = ts.tool.get();
        if (mouseSX >= 0 && (tool == WorldTool.BASE || tool == WorldTool.BIOME || tool == WorldTool.SCATTER) && !spaceDown) {
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
