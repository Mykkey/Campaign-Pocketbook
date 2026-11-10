package dnd.ui;

import dnd.map.AssetLibrary;
import dnd.map.world.ScatterPlanner;
import dnd.map.world.WorldAssets;
import dnd.map.world.WorldGrid;
import dnd.map.world.WorldLabels;
import dnd.map.world.WorldLegend;
import dnd.map.world.WorldTravel;
import dnd.map.world.WorldPalette;
import dnd.map.world.WorldPathGeometry;
import dnd.map.world.WorldPathRenderer;
import dnd.map.world.WorldStampRenderer;
import dnd.map.world.WorldTerrainCache;
import dnd.model.PaintLayer;
import dnd.model.WorldBiome;
import dnd.model.WorldMap;
import dnd.model.WorldObjects;
import dnd.model.WorldPath;
import dnd.model.WorldRegion;
import dnd.model.WorldStamp;
import dnd.model.WorldStyle;
import dnd.model.WorldTerrain;
import dnd.storage.Json;
import dnd.storage.ProjectStore;
import javafx.animation.PauseTransition;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Popup;
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

    private enum Mode { NONE, PAN, PAINT, SCATTER, MOVE, MARQUEE, RESIZE, DRAWPATH, DRAGPOINT, LEGEND }

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

    /** Opens another project item in a tab (set by the editor). */
    Consumer<String> openItem = id -> { };
    /** Lets a test answer the link-and-note dialog. */
    static java.util.function.Supplier<LinkNoteDialog.Result> linkNoteAnswer;

    private final PauseTransition hoverDelay = new PauseTransition(Duration.millis(450));
    private final Popup tip = new Popup();
    private String hoverKey;

    private Mode mode = Mode.NONE;
    private boolean spaceDown;
    private double lastSX, lastSY, mouseSX = -1, mouseSY = -1;
    private double lastWX, lastWY, mqX0, mqY0, mqX1, mqY1;
    private boolean moved;
    /** Everything placed on the map as it was when a gesture began, for undo. */
    private String stampsBefore;
    // lines and regions being drawn or edited
    private WorldPath drawingPath;
    private WorldRegion drawingRegion;
    private boolean freehand;
    /** The line or region selected (symbols and lines are never selected together), or null. */
    private String selShape;
    private int dragPoint = -1;
    // the ruler, and dragging the legend
    private final List<Double> ruler = new ArrayList<>();
    private boolean rulerDone;
    private Double legendX0, legendY0;
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
            hoverCheck(e);
            redraw();
        });
        setOnMouseExited(e -> {
            mouseSX = -1;
            hideTip();
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
        hideTip();
        if (e.getButton() == MouseButton.SECONDARY && (tool == WorldTool.SELECT || tool == WorldTool.HAND)) {
            contextMenu(e, px, py);
            return;
        }
        if (tool == WorldTool.HAND && e.getButton() == MouseButton.PRIMARY && e.getClickCount() >= 2) {
            String link = linkOf(hitAny(px, py));
            if (link != null && store.itemExists(link)) {
                openItem.accept(link);
                return;
            }
        }
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
                stampsBefore = WorldObjects.capture(map);
                lastCx = px;
                lastCy = py;
                scatterAt(px, py);
            }
            case SELECT -> selectPressed(px, py, e.isShiftDown(), e.getClickCount(), e.isAltDown());
            case PATH -> pathPressed(px, py, e.getClickCount());
            case REGION -> regionPressed(px, py, e.getClickCount());
            case LABEL -> placeLabel(px, py);
            case FILL -> bucket(px, py);
            case RULER -> rulerPressed(px, py, e.getClickCount());
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
            case DRAWPATH -> {
                if (drawingPath != null && Math.hypot(px - lastCx, py - lastCy) >= Math.max(1.5, 7 / zoom())) {
                    drawingPath.add(px, py);
                    lastCx = px;
                    lastCy = py;
                    freehand = true;
                }
            }
            case DRAGPOINT -> {
                moved = true;
                movePoint(px, py);
            }
            case LEGEND -> {
                moved = true;
                double[] b = WorldLegend.bounds(map);
                if (b != null) {
                    map.legendX = b[0] + (px - lastWX);
                    map.legendY = b[1] + (py - lastWY);
                }
            }
            case RESIZE -> {
                WorldStamp s = singleSelected();
                if (s != null) {
                    moved = true;
                    double width = Math.abs(WorldStampRenderer.toLocal(s, px, py)[0]) * 2;
                    s.size = WorldLabels.isLabel(s) ? Math.max(0.6, width / WorldLabels.ratio(s, map.style)) : Math.max(0.5, width);
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
            case MOVE, RESIZE, DRAGPOINT -> {
                if (moved) finishStampEdit();
                if (mode == Mode.RESIZE && moved) rememberSize();
            }
            case LEGEND -> {
                Double ox0 = legendX0, oy0 = legendY0, nx = map.legendX, ny = map.legendY;
                if (moved) {
                    push(new Entry(() -> setLegend(ox0, oy0), () -> setLegend(nx, ny)));
                    store.markWorldMapDirty(map, true);
                }
            }
            case DRAWPATH -> {
                if (freehand) finishPath();
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
        } else if (e.getCode() == KeyCode.ENTER) {
            finishDrawing();
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

    /** Fills the area around a point with what the bucket carries: the same terrain (or biome) as at the point, all connected, becomes the chosen one. */
    private void bucket(double px, double py) {
        int cx = (int) Math.floor(px), cy = (int) Math.floor(py);
        if (cx < 0 || cy < 0 || cx >= map.cols || cy >= map.rows) return;
        boolean biome = ts.fillBiome.get();
        byte value;
        if (biome) {
            WorldBiome b = ts.biome.get();
            value = b == null ? 0 : b.code;
        } else {
            WorldTerrain t = ts.terrain.get();
            value = t == null ? 0 : t.code;
        }
        PaintLayer target = biome ? map.biome() : map.base();
        PaintLayer base = map.base();
        int w = map.cols, h = map.rows;
        byte from = target.get(cx, cy);
        if (from == value) return;
        if (biome && !landOrIce(base, cx, cy)) return;
        layerIsBiome = biome;
        layer = target;
        layerBefore = target.cells.clone();
        minX = Integer.MAX_VALUE;
        minY = Integer.MAX_VALUE;
        maxX = Integer.MIN_VALUE;
        maxY = Integer.MIN_VALUE;
        int[] queue = new int[w * h];
        boolean[] seen = new boolean[w * h];
        int head = 0, tail = 0;
        queue[tail++] = cy * w + cx;
        seen[cy * w + cx] = true;
        while (head < tail) {
            int i = queue[head++], x = i % w, y = i / w;
            target.set(x, y, value);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            int[][] near = {{x - 1, y}, {x + 1, y}, {x, y - 1}, {x, y + 1}};
            for (int[] n : near) {
                if (n[0] < 0 || n[1] < 0 || n[0] >= w || n[1] >= h) continue;
                int j = n[1] * w + n[0];
                if (seen[j] || target.get(n[0], n[1]) != from) continue;
                if (biome && !landOrIce(base, n[0], n[1])) continue;
                seen[j] = true;
                queue[tail++] = j;
            }
        }
        WorldTerrainCache.of(map).invalidateCells(minX, minY, maxX, maxY);
        endStroke();
    }

    private static boolean landOrIce(PaintLayer base, int x, int y) {
        WorldTerrain t = WorldTerrain.of(base.get(x, y));
        return t == WorldTerrain.LAND || t == WorldTerrain.ICE;
    }

    /** After the user resizes a symbol, new symbols come out that size too, until it is changed again. */
    private void rememberSize() {
        WorldStamp s = singleSelected();
        if (s == null || WorldLabels.isLabel(s)) return;
        ts.stampSize.set((int) Math.max(1, Math.min(200, Math.round(s.size))));
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
        String before = WorldObjects.capture(map);
        WorldStamp s = newStamp(f, px, py);
        if (WorldLabels.isBanner(s)) {
            String text = askText("Banner", "Text on the banner", "");
            if (text == null) return;
            s.text = text;
        }
        map.stamps.add(s);
        selected.clear();
        selected.add(s.id);
        stampEdit(before);
    }

    /** Puts a text label where the user clicked, after asking what it says. */
    private void placeLabel(double px, double py) {
        String text = askText("Label", "What should the label say?", "");
        if (text == null || text.isBlank()) return;
        String before = WorldObjects.capture(map);
        WorldStamp s = new WorldStamp(WorldLabels.FEATURE, px, py);
        s.text = text.trim();
        s.labelKind = ts.labelKind.get().name();
        s.bend = ts.labelBend.get();
        s.style = ts.stampStyle.get();
        map.stamps.add(s);
        selected.clear();
        selShape = null;
        selected.add(s.id);
        stampEdit(before);
    }

    /** Changes what a label or banner says. */
    private void editText(WorldStamp s) {
        String t = askText(WorldLabels.isLabel(s) ? "Label" : "Banner", "Text", s.text == null ? "" : s.text);
        if (t == null || (WorldLabels.isLabel(s) && t.isBlank())) return;
        String before = WorldObjects.capture(map);
        s.text = t.trim();
        stampEdit(before);
        redraw();
    }

    /** Gives the selected labels a kind and a bend (the label tool's settings apply to them). */
    void updateSelectedLabels(WorldLabels.Kind kind, double bend) {
        List<WorldStamp> labels = new ArrayList<>();
        for (WorldStamp s : selectedStamps()) if (WorldLabels.isLabel(s)) labels.add(s);
        if (labels.isEmpty()) return;
        String before = WorldObjects.capture(map);
        for (WorldStamp s : labels) {
            if (!kind.name().equals(s.labelKind)) {
                s.labelKind = kind.name();
                s.size = 0;
            }
            s.bend = bend;
        }
        stampEdit(before);
        redraw();
    }

    boolean labelSelected() {
        return selectedStamps().stream().anyMatch(WorldLabels::isLabel);
    }

    /** Saves what a stamp gesture changed as one undo step. */
    private void finishStampEdit() {
        if (stampsBefore != null) stampEdit(stampsBefore);
        stampsBefore = null;
    }

    private void stampEdit(String before) {
        String after = WorldObjects.capture(map);
        if (before.equals(after)) return;
        push(new Entry(() -> restoreStamps(before), () -> restoreStamps(after)));
        store.markWorldMapDirty(map, true);
    }

    private void restoreStamps(String json) {
        WorldObjects.restore(map, json);
        selected.removeIf(id -> map.stamps.stream().noneMatch(s -> s.id.equals(id)));
        if (selShape != null && shapeById(selShape) == null) selShape = null;
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
        double[] h = WorldStampRenderer.handleOf(s, map.style);
        double tol = 8 / zoom();
        return Math.abs(px - h[0]) <= tol && Math.abs(py - h[1]) <= tol;
    }

    private void selectPressed(double px, double py, boolean shift, int clicks, boolean alt) {
        WorldStamp single = singleSelected();
        if (single != null && onHandle(single, px, py)) {
            mode = Mode.RESIZE;
            stampsBefore = WorldObjects.capture(map);
            return;
        }
        // the points of the selected line or region
        Object shape = shapeById(selShape);
        if (shape != null) {
            if (shape instanceof WorldRegion rg && rg.name != null && !rg.name.isBlank()) {
                double[] at = rg.labelAt();
                if (Math.abs(at[0] - px) <= 9 / zoom() && Math.abs(at[1] - py) <= 9 / zoom()) {
                    dragPoint = -2;
                    mode = Mode.DRAGPOINT;
                    stampsBefore = WorldObjects.capture(map);
                    return;
                }
            }
            int idx = pointAt(shape, px, py);
            if (idx >= 0 && alt && pointCount(shape) > (shape instanceof WorldRegion ? 3 : 2)) {
                String before = WorldObjects.capture(map);
                removePoint(shape, idx);
                stampEdit(before);
                return;
            }
            if (idx >= 0) {
                dragPoint = idx;
                mode = Mode.DRAGPOINT;
                stampsBefore = WorldObjects.capture(map);
                return;
            }
            if (clicks >= 2 && shape instanceof WorldPath path && WorldPathGeometry.distance(WorldPathGeometry.smooth(path, 8), px, py) <= 8 / zoom()) {
                String before = WorldObjects.capture(map);
                path.insert(WorldPathGeometry.insertIndex(path, px, py), px, py);
                stampEdit(before);
                return;
            }
            if (clicks >= 2 && shape instanceof WorldRegion region && region.contains(px, py)) {
                renameRegion(region);
                return;
            }
        }
        WorldStamp hit = WorldStampRenderer.hit(map, px, py, true);
        if (hit != null && clicks >= 2 && !WorldLabels.hasText(hit) && hit.link != null && store.itemExists(hit.link)) {
            openItem.accept(hit.link);
            return;
        }
        if (hit != null && clicks >= 2 && WorldLabels.hasText(hit)) {
            selected.clear();
            selected.add(hit.id);
            editText(hit);
            return;
        }
        if (hit != null) {
            selShape = null;
            if (shift) {
                if (!selected.remove(hit.id)) selected.add(hit.id);
            } else if (!selected.contains(hit.id)) {
                selected.clear();
                selected.add(hit.id);
            }
            mode = Mode.MOVE;
            stampsBefore = WorldObjects.capture(map);
            return;
        }
        double[] lb = WorldLegend.bounds(map);
        if (lb != null && px >= lb[0] && px <= lb[0] + lb[2] && py >= lb[1] && py <= lb[1] + lb[3]) {
            selected.clear();
            selShape = null;
            legendX0 = map.legendX;
            legendY0 = map.legendY;
            mode = Mode.LEGEND;
            return;
        }
        WorldPath hp = WorldPathRenderer.hitPath(map, px, py, 6 / zoom(), true);
        WorldRegion hr = hp == null ? WorldPathRenderer.hitRegion(map, px, py, true) : null;
        if (hp != null || hr != null) {
            selected.clear();
            selShape = hp != null ? hp.id : hr.id;
            return;
        }
        selShape = null;
        if (!shift) selected.clear();
        mode = Mode.MARQUEE;
        mqX0 = mqX1 = px;
        mqY0 = mqY1 = py;
    }

    private void setLegend(Double x, Double y) {
        map.legendX = x;
        map.legendY = y;
        store.markWorldMapDirty(map, true);
        redraw();
    }

    // ---- links, notes and hover previews

    /** The symbol, line or region at a point (symbols first), or null. */
    private Object hitAny(double px, double py) {
        WorldStamp s = WorldStampRenderer.hit(map, px, py, true);
        if (s != null) return s;
        WorldPath p = WorldPathRenderer.hitPath(map, px, py, 6 / zoom(), true);
        if (p != null) return p;
        return WorldPathRenderer.hitRegion(map, px, py, true);
    }

    private static String linkOf(Object o) {
        return o instanceof WorldStamp s ? s.link : o instanceof WorldPath p ? p.link : o instanceof WorldRegion r ? r.link : null;
    }

    private static String noteOf(Object o) {
        String n = o instanceof WorldStamp s ? s.note : o instanceof WorldPath p ? p.note : o instanceof WorldRegion r ? r.note : null;
        return n == null || n.isBlank() ? null : n;
    }

    private static String idOf(Object o) {
        return o instanceof WorldStamp s ? s.id : o instanceof WorldPath p ? p.id : o instanceof WorldRegion r ? r.id : null;
    }

    private static String nameOf(Object o) {
        if (o instanceof WorldStamp s) {
            if (WorldLabels.hasText(s) && s.text != null && !s.text.isBlank()) return s.text;
            WorldAssets.Feature f = WorldAssets.get(s.feature);
            return f == null ? "Symbol" : f.name();
        }
        if (o instanceof WorldPath p) return p.name != null && !p.name.isBlank() ? p.name : p.type.label;
        if (o instanceof WorldRegion r) return r.name != null && !r.name.isBlank() ? r.name : "Region";
        return "";
    }

    /** True if the symbol, line or region has a note or a link. */
    static boolean hasLinkOrNote(Object o) {
        return linkOf(o) != null || noteOf(o) != null;
    }

    private void hideTip() {
        hoverDelay.stop();
        tip.hide();
        hoverKey = null;
    }

    private void hoverCheck(MouseEvent e) {
        WorldTool tool = ts.tool.get();
        if (!ts.linkPreviews.get() || mode != Mode.NONE || spaceDown || (tool != WorldTool.SELECT && tool != WorldTool.HAND)) {
            if (hoverKey != null) hideTip();
            return;
        }
        Object o = hitAny(wx(e.getX()), wy(e.getY()));
        String link = linkOf(o);
        if (link != null && !store.itemExists(link)) link = null;
        String note = noteOf(o);
        String key = note == null && link == null ? null : idOf(o);
        if (java.util.Objects.equals(key, hoverKey)) return;
        hideTip();
        hoverKey = key;
        if (key == null) return;
        final String showNote = note, showLink = link;
        final double sx = e.getScreenX(), sy = e.getScreenY();
        hoverDelay.setOnFinished(ev -> {
            javafx.scene.Node preview = ItemPreview.hover(store, showNote, showLink);
            if (preview == null) return;
            preview.setMouseTransparent(true);
            tip.getContent().setAll(preview);
            tip.show(this, sx + 18, sy + 18);
        });
        hoverDelay.playFromStart();
    }

    private void contextMenu(MouseEvent e, double px, double py) {
        Object o = hitAny(px, py);
        if (o == null) return;
        select(o);
        ContextMenu menu = new ContextMenu();
        MenuItem edit = new MenuItem("Link and note...");
        edit.setOnAction(ev -> editLinkNote(o));
        menu.getItems().add(edit);
        String link = linkOf(o);
        if (link != null && store.itemExists(link)) {
            MenuItem open = new MenuItem("Open linked item");
            open.setOnAction(ev -> openItem.accept(link));
            menu.getItems().add(open);
        }
        if (o instanceof WorldStamp s && WorldLabels.hasText(s)) {
            MenuItem text = new MenuItem("Edit text...");
            text.setOnAction(ev -> editText(s));
            menu.getItems().add(text);
        }
        menu.getItems().add(new SeparatorMenuItem());
        MenuItem del = new MenuItem("Delete");
        del.setOnAction(ev -> deleteSelection());
        menu.getItems().add(del);
        menu.show(this, e.getScreenX(), e.getScreenY());
        redraw();
    }

    private void select(Object o) {
        selected.clear();
        selShape = null;
        if (o instanceof WorldStamp s) selected.add(s.id);
        else if (o != null) selShape = idOf(o);
    }

    private Object selectedObject() {
        WorldStamp s = singleSelected();
        return s != null ? s : shapeById(selShape);
    }

    /** True if exactly one symbol, line or region is selected, so it can be given a link or a note. */
    boolean linkable() {
        return selectedObject() != null;
    }

    /** Opens the link-and-note dialog for the selected symbol, line or region. */
    void editSelectedLinkNote() {
        Object o = selectedObject();
        if (o != null) editLinkNote(o);
    }

    private void editLinkNote(Object o) {
        LinkNoteDialog.Result r;
        if (linkNoteAnswer != null) r = linkNoteAnswer.get();
        else {
            r = LinkNoteDialog.show(getScene() == null ? null : getScene().getWindow(), store, nameOf(o), noteOf(o), linkOf(o), openItem).orElse(null);
        }
        if (r == null) return;
        String before = WorldObjects.capture(map);
        if (o instanceof WorldStamp s) {
            s.note = r.note();
            s.link = r.link();
        } else if (o instanceof WorldPath p) {
            p.note = r.note();
            p.link = r.link();
        } else if (o instanceof WorldRegion g) {
            g.note = r.note();
            g.link = r.link();
        }
        stampEdit(before);
        redraw();
    }

    /** Selects an object (found by search) and brings it into view. */
    void focusObject(String id) {
        WorldStamp s = map.stamps.stream().filter(x -> x.id.equals(id)).findFirst().orElse(null);
        Object o = s != null ? s : shapeById(id);
        if (o == null) return;
        select(o);
        double cx, cy;
        if (s != null) {
            cx = s.x;
            cy = s.y;
        } else if (o instanceof WorldPath p) {
            cx = p.x(p.count() / 2);
            cy = p.y(p.count() / 2);
        } else {
            double[] c = ((WorldRegion) o).centre();
            cx = c[0];
            cy = c[1];
        }
        map.offsetX = getWidth() / 2 - cx * map.zoom;
        map.offsetY = getHeight() / 2 - cy * map.zoom;
        cameraChanged();
        redraw();
    }

    /** Small badges on things that have a note (green) or a link (blue). */
    private void drawBadges(GraphicsContext g) {
        WorldTool t = ts.tool.get();
        if (t != WorldTool.SELECT && t != WorldTool.HAND) return;
        double r = 4.5 / zoom();
        for (WorldStamp s : map.stamps) {
            if (!hasLinkOrNote(s)) continue;
            double[][] c = WorldStampRenderer.corners(s, map.style);
            badge(g, c[1][0], c[1][1], r, s.link != null);
        }
        for (WorldPath p : map.paths) if (hasLinkOrNote(p) && p.count() > 0) badge(g, p.x(0), p.y(0), r, p.link != null);
        for (WorldRegion rg : map.regions) {
            if (!hasLinkOrNote(rg)) continue;
            double[] at = rg.labelAt();
            badge(g, at[0], at[1] - 3 / zoom(), r, rg.link != null);
        }
    }

    private void badge(GraphicsContext g, double x, double y, double r, boolean link) {
        g.setFill(Color.WHITE);
        g.fillOval(x - r, y - r, 2 * r, 2 * r);
        g.setFill(Color.web(link ? "#2563eb" : "#16a34a"));
        g.fillOval(x - r * 0.65, y - r * 0.65, 1.3 * r, 1.3 * r);
    }

    // ---- the ruler

    private void rulerPressed(double px, double py, int clicks) {
        if (rulerDone) {
            ruler.clear();
            rulerDone = false;
        }
        if (clicks >= 2) {
            rulerDone = ruler.size() >= 4;
            return;
        }
        ruler.add(px);
        ruler.add(py);
    }

    /** Draws the route being measured and a box with its length and travel times. */
    private void drawRuler(GraphicsContext g) {
        if (ruler.isEmpty()) return;
        List<Double> pts = new ArrayList<>(ruler);
        if (!rulerDone && mouseSX >= 0 && ts.tool.get() == WorldTool.RULER) {
            pts.add(wx(mouseSX));
            pts.add(wy(mouseSY));
        }
        g.setLineDashes(8 / zoom(), 5 / zoom());
        g.setStroke(Color.web("#ffffff"));
        g.setLineWidth(4 / zoom());
        for (int i = 2; i + 1 < pts.size(); i += 2) g.strokeLine(pts.get(i - 2), pts.get(i - 1), pts.get(i), pts.get(i + 1));
        g.setStroke(Color.web("#c2410c"));
        g.setLineWidth(2.2 / zoom());
        for (int i = 2; i + 1 < pts.size(); i += 2) g.strokeLine(pts.get(i - 2), pts.get(i - 1), pts.get(i), pts.get(i + 1));
        g.setLineDashes((double[]) null);
        g.setFill(Color.web("#c2410c"));
        for (int i = 0; i < ruler.size(); i += 2) g.fillOval(ruler.get(i) - 3 / zoom(), ruler.get(i + 1) - 3 / zoom(), 6 / zoom(), 6 / zoom());
        double miles = WorldTravel.length(pts) * map.milesPerCell;
        // the summary box, in screen pixels, beside the end of the route
        g.save();
        g.setTransform(1, 0, 0, 1, 0, 0);
        double ex = map.offsetX + pts.get(pts.size() - 2) * zoom(), ey = map.offsetY + pts.get(pts.size() - 1) * zoom();
        List<String> lines = new ArrayList<>();
        lines.add("Distance: " + WorldTravel.distance(miles));
        for (var e : map.paces.entrySet()) lines.add(e.getKey() + " (" + Math.round(e.getValue()) + " mi/day): " + WorldTravel.duration(miles, e.getValue()));
        double w = 0;
        g.setFont(javafx.scene.text.Font.font("SansSerif", 12));
        for (String l : lines) {
            javafx.scene.text.Text t = new javafx.scene.text.Text(l);
            t.setFont(g.getFont());
            w = Math.max(w, t.getLayoutBounds().getWidth());
        }
        double bw = w + 16, bh = lines.size() * 17 + 10;
        double bx = Math.min(Math.max(6, ex + 14), Math.max(6, getWidth() - bw - 6)), by = Math.min(Math.max(6, ey + 14), Math.max(6, getHeight() - bh - 6));
        g.setFill(Color.web("#ffffff", 0.94));
        g.fillRoundRect(bx, by, bw, bh, 8, 8);
        g.setStroke(Color.web("#c2410c"));
        g.setLineWidth(1.2);
        g.strokeRoundRect(bx, by, bw, bh, 8, 8);
        g.setFill(Color.web("#1f2937"));
        for (int i = 0; i < lines.size(); i++) g.fillText(lines.get(i), bx + 8, by + 18 + i * 17);
        g.restore();
    }

    /** The length in miles of the route being measured, or 0. */
    double rulerMiles() {
        return WorldTravel.length(ruler) * map.milesPerCell;
    }

    // ---- lines and regions

    private Object shapeById(String id) {
        if (id == null) return null;
        for (WorldPath p : map.paths) if (p.id.equals(id)) return p;
        for (WorldRegion r : map.regions) if (r.id.equals(id)) return r;
        return null;
    }

    private int pointCount(Object shape) {
        return shape instanceof WorldPath p ? p.count() : ((WorldRegion) shape).count();
    }

    private double[] pointOf(Object shape, int i) {
        return shape instanceof WorldPath p ? new double[]{p.x(i), p.y(i)} : new double[]{((WorldRegion) shape).x(i), ((WorldRegion) shape).y(i)};
    }

    /** The index of the control point near (px, py), or -1. */
    private int pointAt(Object shape, double px, double py) {
        double tol = 8 / zoom();
        for (int i = 0; i < pointCount(shape); i++) {
            double[] q = pointOf(shape, i);
            if (Math.abs(q[0] - px) <= tol && Math.abs(q[1] - py) <= tol) return i;
        }
        return -1;
    }

    private void removePoint(Object shape, int i) {
        if (shape instanceof WorldPath p) p.remove(i);
        else {
            WorldRegion r = (WorldRegion) shape;
            r.points.remove(2 * i + 1);
            r.points.remove(2 * i);
        }
    }

    private void movePoint(double px, double py) {
        Object shape = shapeById(selShape);
        if (shape instanceof WorldRegion rg && dragPoint == -2) {
            rg.labelX = px;
            rg.labelY = py;
            return;
        }
        if (shape == null || dragPoint < 0) return;
        List<Double> pts = shape instanceof WorldPath p ? p.points : ((WorldRegion) shape).points;
        pts.set(2 * dragPoint, px);
        pts.set(2 * dragPoint + 1, py);
    }

    private void pathPressed(double px, double py, int clicks) {
        if (drawingPath == null) {
            drawingPath = new WorldPath(ts.pathType.get());
            drawingPath.width = ts.pathWidth.get();
            drawingPath.style = ts.stampStyle.get();
            drawingPath.add(px, py);
            stampsBefore = WorldObjects.capture(map);
            freehand = false;
        } else {
            if (clicks >= 2) {
                finishPath();
                return;
            }
            drawingPath.add(px, py);
        }
        lastCx = px;
        lastCy = py;
        mode = Mode.DRAWPATH;
    }

    private void finishPath() {
        WorldPath p = drawingPath;
        drawingPath = null;
        if (p != null && freehand) p.points = WorldPathGeometry.simplify(p.points, Math.max(0.8, 3 / zoom())); // fewer points to edit
        freehand = false;
        if (p == null || p.count() < 2) {
            stampsBefore = null;
            redraw();
            return;
        }
        map.paths.add(p);
        selected.clear();
        selShape = p.id;
        finishStampEdit();
        redraw();
    }

    private void regionPressed(double px, double py, int clicks) {
        if (drawingRegion == null) {
            drawingRegion = new WorldRegion();
            stampsBefore = WorldObjects.capture(map);
        } else if (clicks >= 2) {
            finishRegion();
            return;
        }
        drawingRegion.add(px, py);
    }

    private void finishRegion() {
        WorldRegion r = drawingRegion;
        drawingRegion = null;
        if (r == null || r.count() < 3) {
            stampsBefore = null;
            redraw();
            return;
        }
        r.name = askRegionName("Kingdom");
        map.regions.add(r);
        selected.clear();
        selShape = r.id;
        finishStampEdit();
        redraw();
    }

    /** Lets a test answer the region-name question without a dialog. */
    static java.util.function.UnaryOperator<String> nameAsker;

    private String askRegionName(String initial) {
        String t = askText("Region", "Name this region (leave empty for none)", initial);
        return t == null ? "" : t;
    }

    /** Asks for a line of text; null if the user cancelled. */
    private String askText(String title, String header, String initial) {
        if (nameAsker != null) return nameAsker.apply(initial);
        TextInputDialog d = new TextInputDialog(initial);
        d.setTitle(title);
        d.setHeaderText(header);
        if (getScene() != null) d.initOwner(getScene().getWindow());
        return d.showAndWait().map(String::trim).orElse(null);
    }

    private void renameRegion(WorldRegion r) {
        String before = WorldObjects.capture(map);
        r.name = askRegionName(r.name == null ? "" : r.name);
        stampEdit(before);
        redraw();
    }

    /** Changes the kind and width of the selected line (the line tool's settings apply to it). */
    void updateSelectedPath(WorldPath.Type type, double width) {
        if (!(shapeById(selShape) instanceof WorldPath p)) return;
        String before = WorldObjects.capture(map);
        p.type = type;
        p.width = width;
        stampEdit(before);
        redraw();
    }

    boolean pathSelected() {
        return shapeById(selShape) instanceof WorldPath;
    }

    boolean hasSelection() {
        return !selected.isEmpty();
    }

    /** Removes the selected symbols, line or region (or, while drawing, the last point). Returns false if there was nothing to remove. */
    boolean deleteSelection() {
        if (!ruler.isEmpty() && ts.tool.get() == WorldTool.RULER) {
            ruler.remove(ruler.size() - 1);
            ruler.remove(ruler.size() - 1);
            rulerDone = false;
            redraw();
            return true;
        }
        if (drawingPath != null) {
            if (drawingPath.count() > 1) drawingPath.remove(drawingPath.count() - 1);
            else drawingPath = null;
            redraw();
            return true;
        }
        if (drawingRegion != null) {
            if (drawingRegion.count() > 1) {
                drawingRegion.points.remove(drawingRegion.points.size() - 1);
                drawingRegion.points.remove(drawingRegion.points.size() - 1);
            } else drawingRegion = null;
            redraw();
            return true;
        }
        Object shape = shapeById(selShape);
        if (shape != null) {
            String before = WorldObjects.capture(map);
            if (shape instanceof WorldPath p) map.paths.remove(p);
            else map.regions.remove((WorldRegion) shape);
            selShape = null;
            stampEdit(before);
            redraw();
            return true;
        }
        if (selected.isEmpty()) return false;
        String before = WorldObjects.capture(map);
        map.stamps.removeIf(s -> selected.contains(s.id));
        selected.clear();
        stampEdit(before);
        redraw();
        return true;
    }

    /** Cancels a line or region being drawn, or drops the selection and puts away the armed symbol. Returns true if there was anything to drop. */
    boolean escape() {
        boolean any = !ruler.isEmpty() || !selected.isEmpty() || ts.armed.get() != null || selShape != null || drawingPath != null || drawingRegion != null;
        drawingPath = null;
        drawingRegion = null;
        ruler.clear();
        rulerDone = false;
        stampsBefore = null;
        selected.clear();
        selShape = null;
        ts.armed.set(null);
        redraw();
        return any;
    }

    /** Finishes the line or region being drawn (Enter). */
    void finishDrawing() {
        if (drawingPath != null) finishPath();
        else if (drawingRegion != null) finishRegion();
    }

    void duplicateSelection() {
        if (selected.isEmpty()) return;
        String before = WorldObjects.capture(map);
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
        String before = WorldObjects.capture(map);
        for (WorldStamp s : selectedStamps()) s.rotation = ((s.rotation + degrees) % 360 + 360) % 360;
        stampEdit(before);
        redraw();
    }

    void flipSelection() {
        if (selected.isEmpty()) return;
        String before = WorldObjects.capture(map);
        for (WorldStamp s : selectedStamps()) s.flipX = !s.flipX;
        stampEdit(before);
        redraw();
    }

    /** Gives the selected symbols an art style (null = follow the map's style). */
    void styleSelection(WorldStyle style) {
        if (selected.isEmpty()) return;
        String before = WorldObjects.capture(map);
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

    /** The grid, scale bar, legend or travel speeds changed: save and redraw. */
    void overlaysChanged() {
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
        WorldTool t = ts.tool.get();
        setCursor(spaceDown || t == WorldTool.HAND ? Cursor.OPEN_HAND : t == WorldTool.SELECT ? Cursor.DEFAULT : Cursor.CROSSHAIR);
    }

    private void report(double sx, double sy) {
        double cx = wx(sx), cy = wy(sy);
        if (cx < 0 || cy < 0 || cx >= map.cols || cy >= map.rows) {
            status.accept("");
            return;
        }
        int[] cell = WorldGrid.cellAt(map, cx, cy);
        status.accept(String.format("%,.0f mi east, %,.0f mi south%s    zoom %d%%    %d symbols", cx * map.milesPerCell, cy * map.milesPerCell,
                cell == null ? "" : "    cell " + WorldGrid.name(cell[0], cell[1]), Math.round(map.zoom * 100), map.stamps.size()));
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

        WorldPathRenderer.draw(g, map, zoom(), vx0, vy0, vx1, vy1, true);
        WorldGrid.draw(g, map, zoom(), vx0, vy0, vx1, vy1);
        WorldStampRenderer.draw(g, map, lib, zoom(), vx0, vy0, vx1, vy1, true);
        WorldLegend.draw(g, map, lib, zoom());
        drawShapeOverlays(g);
        drawBadges(g);
        drawRuler(g);

        g.setStroke(Color.web("#000000", 0.55));
        g.setLineWidth(1.5 / zoom());
        g.strokeRect(0, 0, map.cols, map.rows);

        // selection outlines and the resize handle
        g.setStroke(Color.web("#2563eb"));
        g.setLineWidth(1.5 / zoom());
        g.setLineDashes(6 / zoom(), 4 / zoom());
        for (WorldStamp s : selectedStamps()) {
            double[][] c = WorldStampRenderer.corners(s, map.style);
            g.strokePolygon(new double[]{c[0][0], c[1][0], c[2][0], c[3][0]}, new double[]{c[0][1], c[1][1], c[2][1], c[3][1]}, 4);
        }
        g.setLineDashes((double[]) null);
        WorldStamp single = singleSelected();
        if (single != null) {
            double[] h = WorldStampRenderer.handleOf(single, map.style);
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

    /** The selected line or region with its points, and the line or region being drawn. */
    private void drawShapeOverlays(GraphicsContext g) {
        double px = 7 / zoom();
        Object shape = shapeById(selShape);
        if (shape instanceof WorldPath p && p.count() >= 2) {
            List<double[]> pts = WorldPathGeometry.smooth(p, 10);
            g.setStroke(Color.web("#2563eb", 0.45));
            g.setLineWidth(Math.max(p.drawnWidth() + 3 / zoom(), 5 / zoom()));
            g.beginPath();
            g.moveTo(pts.get(0)[0], pts.get(0)[1]);
            for (int i = 1; i < pts.size(); i++) g.lineTo(pts.get(i)[0], pts.get(i)[1]);
            g.stroke();
        } else if (shape instanceof WorldRegion r && r.count() >= 3) {
            double[] xs = new double[r.count()], ys = new double[r.count()];
            for (int i = 0; i < r.count(); i++) {
                xs[i] = r.x(i);
                ys[i] = r.y(i);
            }
            g.setStroke(Color.web("#2563eb", 0.55));
            g.setLineWidth(3 / zoom());
            g.strokePolygon(xs, ys, r.count());
        }
        if (shape instanceof WorldRegion rg && rg.name != null && !rg.name.isBlank()) {
            double[] at = rg.labelAt();
            double d = 6 / zoom();
            g.setFill(Color.web("#f59e0b"));
            g.setStroke(Color.WHITE);
            g.setLineWidth(1.5 / zoom());
            g.fillPolygon(new double[]{at[0], at[0] + d, at[0], at[0] - d}, new double[]{at[1] - d, at[1], at[1] + d, at[1]}, 4);
            g.strokePolygon(new double[]{at[0], at[0] + d, at[0], at[0] - d}, new double[]{at[1] - d, at[1], at[1] + d, at[1]}, 4);
        }
        if (shape != null) {
            g.setFill(Color.WHITE);
            g.setStroke(Color.web("#2563eb"));
            g.setLineWidth(1.5 / zoom());
            for (int i = 0; i < pointCount(shape); i++) {
                double[] q = pointOf(shape, i);
                g.fillRect(q[0] - px / 2, q[1] - px / 2, px, px);
                g.strokeRect(q[0] - px / 2, q[1] - px / 2, px, px);
            }
        }
        if (drawingPath != null) {
            WorldPath tmp = drawingPath.copy();
            if (mouseSX >= 0 && !freehand) tmp.add(wx(mouseSX), wy(mouseSY));
            if (tmp.count() >= 2) WorldPathRenderer.drawOne(g, map, tmp, zoom());
            g.setFill(Color.web("#2563eb"));
            for (int i = 0; i < drawingPath.count(); i++) g.fillOval(drawingPath.x(i) - px / 3, drawingPath.y(i) - px / 3, px * 2 / 3, px * 2 / 3);
        }
        if (drawingRegion != null) {
            int n = drawingRegion.count();
            double[] xs = new double[n + 1], ys = new double[n + 1];
            for (int i = 0; i < n; i++) {
                xs[i] = drawingRegion.x(i);
                ys[i] = drawingRegion.y(i);
            }
            xs[n] = mouseSX >= 0 ? wx(mouseSX) : xs[n - 1];
            ys[n] = mouseSX >= 0 ? wy(mouseSY) : ys[n - 1];
            g.setStroke(Color.web("#2563eb"));
            g.setLineWidth(2 / zoom());
            g.setLineDashes(6 / zoom(), 4 / zoom());
            g.strokePolyline(xs, ys, n + 1);
            g.setLineDashes((double[]) null);
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
