package dnd.ui;

import dnd.map.AssetLibrary;
import dnd.map.Grid;
import dnd.map.MapRenderer;
import dnd.model.BattleMap;
import dnd.model.MapObject;
import dnd.model.MapToken;
import dnd.model.Terrain;
import dnd.storage.Json;
import dnd.storage.ProjectStore;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.*;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.*;

/** The battlemap canvas: pan/zoom, terrain painting, placing and editing pieces, tokens and the ruler. */
public class MapView extends Pane implements ItemView {
    private enum Mode { NONE, PAN, MOVE, MARQUEE, RESIZE, ROTATE, PAINT, RULER }

    private static final Color SELECT_COLOR = Color.web("#2563eb");
    private static String clipboardJson;
    private static int pasteCount;

    private final BattleMap map;
    private final ProjectStore store;
    private final MapToolState ts;
    private final AssetLibrary lib;
    private final Grid grid;

    private final Canvas canvas = new Canvas();
    private final Set<String> selected = new LinkedHashSet<>();
    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();
    private final ContextMenu menu = new ContextMenu();

    private Mode mode = Mode.NONE;
    private boolean gestureCheckpointed, dragStarted, spaceDown;
    private double pressSX, pressSY, lastSX, lastSY, pressWX, pressWY;
    private double hoverWX = Double.NaN, hoverWY = Double.NaN;
    private double lastPaintWX = Double.NaN, lastPaintWY = Double.NaN;
    private double mqX0, mqY0, mqX1, mqY1;
    private double startW, startH;
    private int[] pressCell;
    private final Map<String, double[]> startObj = new HashMap<>();
    private final Map<String, int[]> startTok = new HashMap<>();
    private final Set<String> marqueeBase = new LinkedHashSet<>();
    private double[] rulerA, rulerB;

    public MapView(BattleMap map, ProjectStore store, MapToolState ts) {
        this.map = map;
        this.store = store;
        this.ts = ts;
        this.lib = store.assets();
        this.grid = Grid.of(map);

        getChildren().add(canvas);
        setMinSize(0, 0);
        setFocusTraversable(true);

        setOnMousePressed(this::onPressed);
        setOnMouseDragged(this::onDragged);
        setOnMouseReleased(this::onReleased);
        setOnMouseMoved(e -> {
            hoverWX = wx(e.getX());
            hoverWY = wy(e.getY());
            if (ts.tool.get() == MapTool.ASSET || ts.tool.get() == MapTool.TERRAIN || ts.tool.get() == MapTool.TOKEN) redraw();
        });
        setOnMouseExited(e -> {
            hoverWX = Double.NaN;
            redraw();
        });
        setOnScroll(this::onScroll);
        setOnKeyPressed(this::onKeyPressed);
        setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.SPACE) {
                spaceDown = false;
                refreshCursor();
            }
        });
        setOnContextMenuRequested(this::onContextMenu);
        setOnDragOver(e -> {
            String s = e.getDragboard().hasString() ? e.getDragboard().getString() : "";
            if (s.startsWith("asset:")) e.acceptTransferModes(TransferMode.COPY);
            e.consume();
        });
        setOnDragDropped(e -> {
            String s = e.getDragboard().hasString() ? e.getDragboard().getString() : "";
            boolean ok = false;
            if (s.startsWith("asset:") && lib.get(s.substring(6)) != null) {
                stampAsset(s.substring(6), wx(e.getX()), wy(e.getY()));
                ok = true;
            }
            e.setDropCompleted(ok);
            e.consume();
        });
        ts.tool.addListener((o, a, n) -> {
            rulerA = rulerB = null;
            refreshCursor();
            redraw();
        });
        ts.armedAsset.addListener((o, a, n) -> redraw());
        refreshCursor();
    }

    public BattleMap map() { return map; }

    // ================================================================ ItemView

    @Override public String itemId() { return map.id; }

    @Override
    public void activated() {
        refreshCursor();
        requestFocus();
    }

    @Override
    public boolean selectTool(int index) {
        MapTool[] tools = MapTool.values();
        if (index < 0 || index >= tools.length) return false;
        ts.tool.set(tools[index]);
        return true;
    }

    // ================================================================ layout & coordinates

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        canvas.setWidth(Math.max(1, getWidth()));
        canvas.setHeight(Math.max(1, getHeight()));
        redraw();
    }

    private double zoom() { return map.zoom; }

    private double wx(double sx) { return (sx - map.offsetX) / map.zoom; }

    private double wy(double sy) { return (sy - map.offsetY) / map.zoom; }

    public void refreshCursor() {
        MapTool t = ts.tool.get();
        Cursor c;
        if (spaceDown || t == MapTool.HAND) c = Cursor.OPEN_HAND;
        else if (t == MapTool.SELECT) c = Cursor.DEFAULT;
        else c = Cursor.CROSSHAIR;
        setCursor(c);
    }

    /** Zooms and pans so the whole map is visible. */
    public void fitToWindow() {
        double w = Math.max(100, getWidth()), h = Math.max(100, getHeight());
        double z = Math.min((w - 60) / Math.max(1, grid.width()), (h - 60) / Math.max(1, grid.height()));
        map.zoom = Math.max(0.1, Math.min(4, z));
        map.offsetX = (w - grid.width() * map.zoom) / 2;
        map.offsetY = (h - grid.height() * map.zoom) / 2;
        store.markMapDirty(map);
        redraw();
    }

    // ================================================================ rendering

    public void redraw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double W = canvas.getWidth(), H = canvas.getHeight();
        g.setTransform(1, 0, 0, 1, 0, 0);
        g.setFill(Color.web("#5b574d"));
        g.fillRect(0, 0, W, H);

        double z = zoom();
        g.setTransform(z, 0, 0, z, map.offsetX, map.offsetY);
        g.setLineCap(StrokeLineCap.ROUND);
        MapRenderer.draw(g, map, grid, lib, z);

        drawHover(g);
        drawSelection(g);
        drawRuler(g);
        if (mode == Mode.MARQUEE) {
            g.setFill(Color.web("#2563eb33"));
            g.setStroke(SELECT_COLOR);
            g.setLineWidth(1 / z);
            double l = Math.min(mqX0, mqX1), t = Math.min(mqY0, mqY1);
            g.fillRect(l, t, Math.abs(mqX1 - mqX0), Math.abs(mqY1 - mqY0));
            g.strokeRect(l, t, Math.abs(mqX1 - mqX0), Math.abs(mqY1 - mqY0));
        }
    }

    private void drawHover(GraphicsContext g) {
        if (Double.isNaN(hoverWX) || mode == Mode.PAN) return;
        MapTool t = ts.tool.get();
        double z = zoom();
        if (t == MapTool.ASSET && ts.armedAsset.get() != null) {
            MapObject ghost = newObject(ts.armedAsset.get(), hoverWX, hoverWY);
            if (ghost != null) MapRenderer.drawObject(g, ghost, lib, z, 0.6);
        } else if (t == MapTool.TERRAIN || t == MapTool.TOKEN) {
            int[] c = grid.cellAt(hoverWX, hoverWY);
            if (c == null) return;
            g.setStroke(SELECT_COLOR);
            g.setLineWidth(2 / z);
            int b = t == MapTool.TERRAIN ? ts.brush.get() : 1;
            for (int r = Math.max(0, c[1] - b); r <= Math.min(grid.rows() - 1, c[1] + b); r++) {
                for (int cc = Math.max(0, c[0] - b); cc <= Math.min(grid.cols() - 1, c[0] + b); cc++) {
                    if (grid.distance(c[0], c[1], cc, r) < b) MapRenderer.strokeCell(g, grid, cc, r);
                }
            }
            if (t == MapTool.TOKEN) {
                MapToken ghost = new MapToken();
                ghost.col = c[0];
                ghost.row = c[1];
                ghost.color = MapView.hex(ts.tokenColor.get());
                MapRenderer.drawToken(g, ghost, grid, map.cellSize, 0.55);
            }
        }
    }

    private void drawSelection(GraphicsContext g) {
        double z = zoom();
        g.setStroke(SELECT_COLOR);
        g.setLineWidth(1.5 / z);
        g.setLineDashes(6 / z, 4 / z);
        for (String id : selected) {
            MapObject o = map.findObject(id);
            if (o != null) {
                g.save();
                g.translate(o.x, o.y);
                g.rotate(o.rotation);
                g.strokeRect(-o.w / 2 - 3 / z, -o.h / 2 - 3 / z, o.w + 6 / z, o.h + 6 / z);
                g.restore();
                continue;
            }
            MapToken t = map.findToken(id);
            if (t != null && grid.inside(t.col, t.row)) {
                double[] c = grid.center(t.col, t.row);
                double d = tokenDiameter(t) + 8 / z;
                g.strokeOval(c[0] - d / 2, c[1] - d / 2, d, d);
            }
        }
        g.setLineDashes((double[]) null);
        MapObject single = singleObject();
        if (single != null) {
            double[] rh = rotateHandle(single), sh = resizeHandle(single);
            double s = 10 / z;
            g.setStroke(SELECT_COLOR);
            g.setLineWidth(1.5 / z);
            g.strokeLine(rotateHandleBase(single)[0], rotateHandleBase(single)[1], rh[0], rh[1]);
            g.setFill(Color.WHITE);
            g.fillOval(rh[0] - s / 2, rh[1] - s / 2, s, s);
            g.strokeOval(rh[0] - s / 2, rh[1] - s / 2, s, s);
            g.fillRect(sh[0] - s / 2, sh[1] - s / 2, s, s);
            g.strokeRect(sh[0] - s / 2, sh[1] - s / 2, s, s);
        }
    }

    private void drawRuler(GraphicsContext g) {
        if (rulerA == null || rulerB == null) return;
        double z = zoom();
        g.setStroke(Color.web("#c0392b"));
        g.setLineWidth(3 / z);
        g.strokeLine(rulerA[0], rulerA[1], rulerB[0], rulerB[1]);
        g.setFill(Color.web("#c0392b"));
        double r = 5 / z;
        g.fillOval(rulerA[0] - r, rulerA[1] - r, 2 * r, 2 * r);
        g.fillOval(rulerB[0] - r, rulerB[1] - r, 2 * r, 2 * r);
        int[] a = grid.cellAt(rulerA[0], rulerA[1]), b = grid.cellAt(rulerB[0], rulerB[1]);
        if (a != null && b != null) {
            int d = grid.distance(a[0], a[1], b[0], b[1]);
            String text = d + (d == 1 ? " cell" : " cells") + "  (" + (d * 5) + " ft)";
            double mx = (rulerA[0] + rulerB[0]) / 2, my = (rulerA[1] + rulerB[1]) / 2 - 14 / z;
            g.setFont(Font.font("System", FontWeight.BOLD, 14 / z));
            g.setTextAlign(TextAlignment.CENTER);
            g.setTextBaseline(VPos.CENTER);
            g.setStroke(Color.WHITE);
            g.setLineWidth(4 / z);
            g.strokeText(text, mx, my);
            g.setFill(Color.web("#7b1e12"));
            g.fillText(text, mx, my);
        }
    }

    static String hex(Color c) {
        return String.format("#%02X%02X%02X", (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
    }

    // ================================================================ hit testing & handles

    private double tokenDiameter(MapToken t) {
        return map.cellSize * (0.78 + 0.95 * (Math.max(1, t.size) - 1));
    }

    private MapToken hitToken(double px, double py) {
        for (int i = map.tokens.size() - 1; i >= 0; i--) {
            MapToken t = map.tokens.get(i);
            if (!grid.inside(t.col, t.row)) continue;
            double[] c = grid.center(t.col, t.row);
            if (Math.hypot(px - c[0], py - c[1]) <= tokenDiameter(t) / 2) return t;
        }
        return null;
    }

    /**
     * The piece under the point. Solid pixels win over transparent ones, so clicking the empty corner
     * of an L-shaped wall picks the piece beneath it. If only transparent areas are under the point, the
     * topmost piece whose box contains it is used.
     */
    private MapObject hitObject(double px, double py) {
        for (int i = map.objects.size() - 1; i >= 0; i--) {
            MapObject o = map.objects.get(i);
            if (o.contains(px, py) && opaqueAt(o, px, py)) return o;
        }
        for (int i = map.objects.size() - 1; i >= 0; i--) {
            if (map.objects.get(i).contains(px, py)) return map.objects.get(i);
        }
        return null;
    }

    private boolean opaqueAt(MapObject o, double px, double py) {
        javafx.scene.image.Image img = lib.image(o.assetId, 128);
        if (img == null || img.getPixelReader() == null || img.getWidth() < 1 || img.getHeight() < 1) return true;
        double a = Math.toRadians(-o.rotation);
        double dx = px - o.x, dy = py - o.y;
        double lx = dx * Math.cos(a) - dy * Math.sin(a);
        double ly = dx * Math.sin(a) + dy * Math.cos(a);
        if (o.flipX) lx = -lx;
        int ix = (int) Math.max(0, Math.min(img.getWidth() - 1, (lx + o.w / 2) / o.w * img.getWidth()));
        int iy = (int) Math.max(0, Math.min(img.getHeight() - 1, (ly + o.h / 2) / o.h * img.getHeight()));
        return ((img.getPixelReader().getArgb(ix, iy) >>> 24) & 0xff) > 24;
    }

    private MapObject singleObject() {
        if (selected.size() != 1) return null;
        return map.findObject(selected.iterator().next());
    }

    private static double[] toWorld(MapObject o, double lx, double ly) {
        double a = Math.toRadians(o.rotation);
        return new double[]{o.x + lx * Math.cos(a) - ly * Math.sin(a), o.y + lx * Math.sin(a) + ly * Math.cos(a)};
    }

    private double[] resizeHandle(MapObject o) { return toWorld(o, o.w / 2, o.h / 2); }

    private double[] rotateHandleBase(MapObject o) { return toWorld(o, 0, -o.h / 2); }

    private double[] rotateHandle(MapObject o) { return toWorld(o, 0, -o.h / 2 - 30 / zoom()); }

    private boolean near(double[] p, double px, double py) {
        return Math.hypot(px - p[0], py - p[1]) <= 9 / zoom();
    }

    // ================================================================ snapping

    /** Where an object of the given size ends up when it is dropped near (x, y). */
    private double[] snapCenter(double x, double y, double w, double h) {
        if (!ts.snap.get()) return new double[]{x, y};
        if (map.gridType == BattleMap.GridType.HEX) {
            int[] c = grid.cellAt(x, y);
            return c == null ? new double[]{x, y} : grid.center(c[0], c[1]);
        }
        double s = map.cellSize;
        return new double[]{snap1(x, w, s), snap1(y, h, s)};
    }

    /** Aligns an edge to the grid: centres on a cell boundary if the size covers an even number of cells. */
    private static double snap1(double v, double size, double s) {
        boolean even = Math.round(size / s) % 2 == 0;
        return even ? Math.round(v / s) * s : (Math.floor(v / s) + 0.5) * s;
    }

    // ================================================================ undo / redo

    private String snapshot() {
        BattleMap.Snapshot s = new BattleMap.Snapshot();
        s.terrain = new LinkedHashMap<>(map.terrain);
        s.objects = map.objects;
        s.tokens = map.tokens;
        return Json.write(s);
    }

    private void checkpoint() {
        undoStack.addLast(snapshot());
        if (undoStack.size() > 200) undoStack.removeFirst();
        redoStack.clear();
    }

    private void gestureCheckpoint() {
        if (!gestureCheckpointed) {
            checkpoint();
            gestureCheckpointed = true;
        }
    }

    @Override
    public void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.addLast(snapshot());
        restore(undoStack.removeLast());
    }

    @Override
    public void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.addLast(snapshot());
        restore(redoStack.removeLast());
    }

    private void restore(String json) {
        BattleMap.Snapshot s = Json.read(json, BattleMap.Snapshot.class);
        map.terrain.clear();
        map.terrain.putAll(s.terrain);
        map.objects.clear();
        map.objects.addAll(s.objects);
        map.tokens.clear();
        map.tokens.addAll(s.tokens);
        selected.removeIf(id -> map.findObject(id) == null && map.findToken(id) == null);
        changed();
    }

    private void changed() {
        store.markMapDirty(map);
        redraw();
    }

    // ================================================================ mouse

    private void onPressed(MouseEvent e) {
        menu.hide();
        requestFocus();
        lastSX = pressSX = e.getX();
        lastSY = pressSY = e.getY();
        pressWX = wx(pressSX);
        pressWY = wy(pressSY);
        gestureCheckpointed = false;
        dragStarted = false;
        mode = Mode.NONE;
        lastPaintWX = lastPaintWY = Double.NaN;
        if (e.getButton() == MouseButton.SECONDARY) return;

        MapTool tool = ts.tool.get();
        if (e.getButton() == MouseButton.MIDDLE || (e.getButton() == MouseButton.PRIMARY && spaceDown)) {
            mode = Mode.PAN;
            setCursor(Cursor.CLOSED_HAND);
            return;
        }
        if (e.getButton() != MouseButton.PRIMARY) return;

        switch (tool) {
            case HAND -> {
                mode = Mode.PAN;
                setCursor(Cursor.CLOSED_HAND);
            }
            case SELECT -> pressSelect(e);
            case TERRAIN -> {
                mode = Mode.PAINT;
                paintStroke(pressWX, pressWY);
            }
            case ASSET -> {
                if (ts.armedAsset.get() == null) break;
                stampAsset(ts.armedAsset.get(), pressWX, pressWY);
            }
            case TOKEN -> placeToken(pressWX, pressWY);
            case RULER -> {
                int[] c = grid.cellAt(pressWX, pressWY);
                if (c != null) {
                    rulerA = grid.center(c[0], c[1]);
                    rulerB = rulerA;
                    mode = Mode.RULER;
                }
            }
        }
        redraw();
    }

    private void pressSelect(MouseEvent e) {
        MapObject single = singleObject();
        if (single != null) {
            if (near(rotateHandle(single), pressWX, pressWY)) {
                mode = Mode.ROTATE;
                return;
            }
            if (near(resizeHandle(single), pressWX, pressWY)) {
                mode = Mode.RESIZE;
                startW = single.w;
                startH = single.h;
                return;
            }
        }
        MapToken tok = hitToken(pressWX, pressWY);
        MapObject obj = tok == null ? hitObject(pressWX, pressWY) : null;
        String hitId = tok != null ? tok.id : obj != null ? obj.id : null;
        if (tok != null && e.getClickCount() == 2) {
            selected.clear();
            selected.add(tok.id);
            editTokenLabel(tok);
            return;
        }
        // Ctrl+click or Shift+click toggles exactly the thing under the pointer, keeping the rest selected
        boolean multi = e.isShiftDown() || e.isShortcutDown();
        if (hitId != null) {
            if (multi) {
                if (selected.remove(hitId)) {
                    mode = Mode.NONE; // just deselected it: don't start dragging what's left
                    return;
                }
                selected.add(hitId);
            } else if (!selected.contains(hitId)) {
                selected.clear();
                selected.add(hitId);
            }
            beginMove();
        } else {
            if (!multi) selected.clear();
            marqueeBase.clear();
            marqueeBase.addAll(selected);
            mode = Mode.MARQUEE;
            mqX0 = mqX1 = pressWX;
            mqY0 = mqY1 = pressWY;
        }
    }

    private void beginMove() {
        mode = Mode.MOVE;
        startObj.clear();
        startTok.clear();
        pressCell = grid.cellAt(pressWX, pressWY);
        for (String id : selected) {
            MapObject o = map.findObject(id);
            if (o != null) startObj.put(id, new double[]{o.x, o.y});
            MapToken t = map.findToken(id);
            if (t != null) startTok.put(id, new int[]{t.col, t.row});
        }
    }

    private void onDragged(MouseEvent e) {
        if (mode == Mode.NONE) return;
        double sx = e.getX(), sy = e.getY(), px = wx(sx), py = wy(sy);
        if (!dragStarted && Math.hypot(sx - pressSX, sy - pressSY) < 3) return;
        dragStarted = true;

        switch (mode) {
            case PAN -> {
                map.offsetX += sx - lastSX;
                map.offsetY += sy - lastSY;
            }
            case MOVE -> dragMove(px, py);
            case MARQUEE -> {
                mqX1 = px;
                mqY1 = py;
                double l = Math.min(mqX0, mqX1), r = Math.max(mqX0, mqX1);
                double t = Math.min(mqY0, mqY1), b = Math.max(mqY0, mqY1);
                selected.clear();
                selected.addAll(marqueeBase); // Ctrl/Shift+drag adds to the existing selection
                for (MapObject o : map.objects) if (o.x >= l && o.x <= r && o.y >= t && o.y <= b) selected.add(o.id);
                for (MapToken k : map.tokens) {
                    if (!grid.inside(k.col, k.row)) continue;
                    double[] c = grid.center(k.col, k.row);
                    if (c[0] >= l && c[0] <= r && c[1] >= t && c[1] <= b) selected.add(k.id);
                }
            }
            case RESIZE -> dragResize(e, px, py);
            case ROTATE -> {
                MapObject o = singleObject();
                if (o != null) {
                    gestureCheckpoint();
                    double ang = Math.toDegrees(Math.atan2(py - o.y, px - o.x)) + 90;
                    if (!e.isAltDown()) ang = Math.round(ang / 15.0) * 15.0;
                    o.rotation = ((ang % 360) + 360) % 360;
                    store.markMapDirty(map);
                }
            }
            case PAINT -> paintStroke(px, py);
            case RULER -> {
                int[] c = grid.cellAt(px, py);
                if (c != null) rulerB = grid.center(c[0], c[1]);
            }
            default -> { }
        }
        lastSX = sx;
        lastSY = sy;
        hoverWX = px;
        hoverWY = py;
        redraw();
    }

    private void dragMove(double px, double py) {
        double dx = px - pressWX, dy = py - pressWY;
        gestureCheckpoint();

        // objects: snap using the first selected object as the lead so the group keeps its shape
        double adjX = 0, adjY = 0;
        if (!startObj.isEmpty() && ts.snap.get()) {
            String leadId = startObj.keySet().iterator().next();
            MapObject lead = map.findObject(leadId);
            double[] st = startObj.get(leadId);
            double[] snapped = snapCenter(st[0] + dx, st[1] + dy, lead.w, lead.h);
            adjX = snapped[0] - (st[0] + dx);
            adjY = snapped[1] - (st[1] + dy);
        }
        for (Map.Entry<String, double[]> en : startObj.entrySet()) {
            MapObject o = map.findObject(en.getKey());
            if (o == null) continue;
            double nx = en.getValue()[0] + dx + adjX, ny = en.getValue()[1] + dy + adjY;
            o.move(nx - o.x, ny - o.y);
        }

        // tokens: always snap to cells
        int[] cur = grid.cellAt(px, py);
        if (cur != null && !startTok.isEmpty()) {
            if (startTok.size() == 1) {
                MapToken t = map.findToken(startTok.keySet().iterator().next());
                if (t != null) {
                    t.col = cur[0];
                    t.row = cur[1];
                }
            } else if (pressCell != null) {
                for (Map.Entry<String, int[]> en : startTok.entrySet()) {
                    MapToken t = map.findToken(en.getKey());
                    if (t == null) continue;
                    int nc = en.getValue()[0] + cur[0] - pressCell[0], nr = en.getValue()[1] + cur[1] - pressCell[1];
                    if (grid.inside(nc, nr)) {
                        t.col = nc;
                        t.row = nr;
                    }
                }
            }
        }
        store.markMapDirty(map);
    }

    private void dragResize(MouseEvent e, double px, double py) {
        MapObject o = singleObject();
        if (o == null) return;
        gestureCheckpoint();
        double a = Math.toRadians(-o.rotation);
        double dx = px - o.x, dy = py - o.y;
        double lx = dx * Math.cos(a) - dy * Math.sin(a);
        double ly = dx * Math.sin(a) + dy * Math.cos(a);
        double hx = Math.max(lx, 6), hy = Math.max(ly, 6);
        if (e.isShiftDown()) {
            o.w = hx * 2;
            o.h = hy * 2;
        } else {
            double s = Math.max(hx / (startW / 2), hy / (startH / 2));
            o.w = Math.max(12, startW * s);
            o.h = Math.max(12, startH * s);
        }
        store.markMapDirty(map);
    }

    private void onReleased(MouseEvent e) {
        if (mode == Mode.PAN) {
            store.markMapDirty(map);
            refreshCursor();
        }
        if (mode == Mode.MOVE && ts.snap.get() && dragStarted) {
            // nothing more to do; objects were snapped while dragging
        }
        mode = Mode.NONE;
        gestureCheckpointed = false;
        redraw();
    }

    private void onScroll(ScrollEvent e) {
        if (e.getDeltaY() == 0) return;
        double factor = Math.exp(e.getDeltaY() * 0.0015);
        double nz = Math.max(0.1, Math.min(5, map.zoom * factor));
        double sx = e.getX(), sy = e.getY();
        double px = wx(sx), py = wy(sy);
        map.zoom = nz;
        map.offsetX = sx - px * nz;
        map.offsetY = sy - py * nz;
        store.markMapDirty(map);
        redraw();
        e.consume();
    }

    // ================================================================ operations

    /** Paints every cell on the line from the last painted point to (px, py) so fast drags leave no gaps. */
    private void paintStroke(double px, double py) {
        boolean any = false;
        if (Double.isNaN(lastPaintWX)) {
            any = paintAround(px, py);
        } else {
            double dist = Math.hypot(px - lastPaintWX, py - lastPaintWY);
            int steps = Math.max(1, (int) Math.ceil(dist / (map.cellSize / 3)));
            for (int i = 1; i <= steps; i++) {
                double t = (double) i / steps;
                any |= paintAround(lastPaintWX + (px - lastPaintWX) * t, lastPaintWY + (py - lastPaintWY) * t);
            }
        }
        lastPaintWX = px;
        lastPaintWY = py;
        if (any) store.markMapDirty(map);
    }

    private boolean paintAround(double px, double py) {
        int[] c = grid.cellAt(px, py);
        if (c == null) return false;
        int b = Math.max(1, ts.brush.get());
        Terrain t = ts.terrain.get();
        boolean changed = false;
        for (int r = Math.max(0, c[1] - b); r <= Math.min(grid.rows() - 1, c[1] + b); r++) {
            for (int col = Math.max(0, c[0] - b); col <= Math.min(grid.cols() - 1, c[0] + b); col++) {
                if (grid.distance(c[0], c[1], col, r) >= b) continue;
                String key = BattleMap.key(col, r);
                String cur = map.terrain.get(key);
                if (t == null) {
                    if (cur != null) {
                        gestureCheckpoint();
                        map.terrain.remove(key);
                        changed = true;
                    }
                } else if (!t.id.equals(cur)) {
                    gestureCheckpoint();
                    map.terrain.put(key, t.id);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private MapObject newObject(String assetId, double cx, double cy) {
        AssetLibrary.Asset a = lib.get(assetId);
        if (a == null) return null;
        MapObject o = new MapObject();
        o.assetId = assetId;
        o.w = a.cellsW * map.cellSize;
        o.h = a.cellsH * map.cellSize;
        double[] p = snapCenter(cx, cy, o.w, o.h);
        o.x = p[0];
        o.y = p[1];
        return o;
    }

    private void stampAsset(String assetId, double cx, double cy) {
        MapObject o = newObject(assetId, cx, cy);
        if (o == null) return;
        checkpoint();
        map.objects.add(o);
        selected.clear();
        selected.add(o.id);
        changed();
    }

    private void placeToken(double px, double py) {
        int[] c = grid.cellAt(px, py);
        if (c == null) return;
        checkpoint();
        MapToken t = new MapToken();
        t.col = c[0];
        t.row = c[1];
        t.color = hex(ts.tokenColor.get());
        t.label = String.valueOf(map.tokens.size() + 1);
        map.tokens.add(t);
        selected.clear();
        selected.add(t.id);
        changed();
    }

    private void editTokenLabel(MapToken t) {
        TextInputDialog d = new TextInputDialog(t.label);
        d.setTitle("Token");
        d.setHeaderText("Token label (up to 3 characters show on the token):");
        if (getScene() != null) d.initOwner(getScene().getWindow());
        d.showAndWait().ifPresent(text -> {
            checkpoint();
            t.label = text.trim();
            changed();
        });
    }

    public boolean hasSelection() { return !selected.isEmpty(); }

    /** Applies the toolbar's token colour to selected tokens. */
    public void applyTokenColor() {
        boolean any = false;
        for (String id : selected) if (map.findToken(id) != null) any = true;
        if (!any) return;
        checkpoint();
        for (String id : selected) {
            MapToken t = map.findToken(id);
            if (t != null) t.color = hex(ts.tokenColor.get());
        }
        changed();
    }

    public void deleteSelected() {
        if (selected.isEmpty()) return;
        checkpoint();
        map.objects.removeIf(o -> selected.contains(o.id));
        map.tokens.removeIf(t -> selected.contains(t.id));
        selected.clear();
        changed();
    }

    private void rotateSelected(double degrees) {
        boolean any = false;
        for (String id : selected) if (map.findObject(id) != null) any = true;
        if (!any) return;
        checkpoint();
        for (String id : selected) {
            MapObject o = map.findObject(id);
            if (o != null) o.rotation = (((o.rotation + degrees) % 360) + 360) % 360;
        }
        changed();
    }

    private void flipSelected() {
        boolean any = false;
        for (String id : selected) if (map.findObject(id) != null) any = true;
        if (!any) return;
        checkpoint();
        for (String id : selected) {
            MapObject o = map.findObject(id);
            if (o != null) o.flipX = !o.flipX;
        }
        changed();
    }

    private void reorder(boolean toFront) {
        List<MapObject> picked = new ArrayList<>();
        for (MapObject o : map.objects) if (selected.contains(o.id)) picked.add(o);
        if (picked.isEmpty()) return;
        checkpoint();
        map.objects.removeAll(picked);
        if (toFront) map.objects.addAll(picked);
        else map.objects.addAll(0, picked);
        changed();
    }

    private void setTokenSize(int size) {
        boolean any = false;
        for (String id : selected) if (map.findToken(id) != null) any = true;
        if (!any) return;
        checkpoint();
        for (String id : selected) {
            MapToken t = map.findToken(id);
            if (t != null) t.size = size;
        }
        changed();
    }

    private void copy() {
        BattleMap.Snapshot s = new BattleMap.Snapshot();
        for (MapObject o : map.objects) if (selected.contains(o.id)) s.objects.add(o);
        for (MapToken t : map.tokens) if (selected.contains(t.id)) s.tokens.add(t);
        if (s.objects.isEmpty() && s.tokens.isEmpty()) return;
        clipboardJson = Json.write(s);
        pasteCount = 0;
    }

    private void paste() {
        if (clipboardJson == null) return;
        BattleMap.Snapshot s = Json.read(clipboardJson, BattleMap.Snapshot.class);
        pasteCount++;
        checkpoint();
        selected.clear();
        double off = map.cellSize * pasteCount;
        for (MapObject o : s.objects) {
            MapObject c = o.copy();
            c.x += off;
            c.y += off;
            map.objects.add(c);
            selected.add(c.id);
        }
        for (MapToken t : s.tokens) {
            MapToken c = t.copy();
            c.col = Math.min(grid.cols() - 1, c.col + pasteCount);
            c.row = Math.min(grid.rows() - 1, c.row + pasteCount);
            map.tokens.add(c);
            selected.add(c.id);
        }
        changed();
    }

    // ================================================================ keyboard & menu

    /**
     * Ctrl+C copy, Ctrl+X cut, Ctrl+V paste, Ctrl+D duplicate, Ctrl+A select all, Ctrl+0 fit.
     * Returns true if the key was one of these. The workspace also calls this so the shortcuts work
     * even when focus is on a toolbar control rather than the map.
     */
    @Override
    public boolean shortcut(KeyEvent e) {
        if (!e.isShortcutDown() || e.isAltDown()) return false;
        switch (e.getCode()) {
            case C -> copy();
            case X -> { copy(); deleteSelected(); }
            case V -> paste();
            case D -> { copy(); paste(); }
            case A -> {
                selected.clear();
                for (MapObject o : map.objects) selected.add(o.id);
                for (MapToken t : map.tokens) selected.add(t.id);
                redraw();
            }
            case DIGIT0 -> fitToWindow();
            default -> { return false; }
        }
        return true;
    }

    @Override
    public boolean deleteSelection() {
        if (selected.isEmpty()) return false;
        deleteSelected();
        return true;
    }

    /** Esc: deselect everything, clear the ruler, stop placing pieces and go back to the Select tool. */
    @Override
    public boolean escape() {
        selected.clear();
        rulerA = rulerB = null;
        ts.armedAsset.set(null);
        ts.tool.set(MapTool.SELECT);
        redraw();
        return true;
    }

    private void onKeyPressed(KeyEvent e) {
        KeyCode k = e.getCode();
        if (k == KeyCode.SPACE) {
            spaceDown = true;
            refreshCursor();
            e.consume();
            return;
        }
        if (shortcut(e)) {
            e.consume();
            return;
        }
        if (e.isShortcutDown()) return;
        switch (k) {
            case DELETE, BACK_SPACE -> deleteSelected();
            case ESCAPE -> escape();
            case OPEN_BRACKET -> rotateSelected(-15);
            case CLOSE_BRACKET -> rotateSelected(15);
            case F -> flipSelected();
            default -> { return; }
        }
        e.consume();
    }

    private void onContextMenu(ContextMenuEvent e) {
        double px = wx(e.getX()), py = wy(e.getY());
        MapToken tok = hitToken(px, py);
        MapObject obj = tok == null ? hitObject(px, py) : null;
        String id = tok != null ? tok.id : obj != null ? obj.id : null;
        if (id != null && !selected.contains(id)) {
            selected.clear();
            selected.add(id);
        } else if (id == null) {
            selected.clear();
        }
        redraw();

        boolean has = !selected.isEmpty();
        boolean hasObj = false, hasTok = false;
        for (String s : selected) {
            if (map.findObject(s) != null) hasObj = true;
            if (map.findToken(s) != null) hasTok = true;
        }
        menu.getItems().clear();
        if (hasTok) {
            MapToken first = null;
            for (String s : selected) if (first == null) first = map.findToken(s);
            final MapToken edit = first;
            menu.getItems().add(item("Rename token...", false, () -> editTokenLabel(edit)));
            for (int size = 1; size <= 4; size++) {
                final int sz = size;
                menu.getItems().add(item("Token size: " + size + (size == 1 ? " (medium)" : size == 2 ? " (large)" : size == 3 ? " (huge)" : " (gargantuan)"),
                        false, () -> setTokenSize(sz)));
            }
            menu.getItems().add(new SeparatorMenuItem());
        }
        if (hasObj) {
            menu.getItems().add(item("Rotate left 15°  ( [ )", false, () -> rotateSelected(-15)));
            menu.getItems().add(item("Rotate right 15°  ( ] )", false, () -> rotateSelected(15)));
            menu.getItems().add(item("Flip  ( F )", false, this::flipSelected));
            menu.getItems().add(item("Bring to front", false, () -> reorder(true)));
            menu.getItems().add(item("Send to back", false, () -> reorder(false)));
            menu.getItems().add(new SeparatorMenuItem());
        }
        menu.getItems().add(item("Copy  (Ctrl+C)", !has, this::copy));
        menu.getItems().add(item("Cut  (Ctrl+X)", !has, () -> { copy(); deleteSelected(); }));
        menu.getItems().add(item("Paste  (Ctrl+V)", clipboardJson == null, this::paste));
        menu.getItems().add(item("Duplicate  (Ctrl+D)", !has, () -> { copy(); paste(); }));
        menu.getItems().add(item("Delete  (Del)", !has, this::deleteSelected));
        menu.show(this, e.getScreenX(), e.getScreenY());
        e.consume();
    }

    private static MenuItem item(String text, boolean disabled, Runnable r) {
        MenuItem m = new MenuItem(text);
        m.setDisable(disabled);
        m.setOnAction(ev -> r.run());
        return m;
    }
}
