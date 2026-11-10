package dnd.ui;

import dnd.map.AssetLibrary;
import dnd.map.Grid;
import dnd.map.Fog;
import dnd.map.MapRenderer;
import dnd.model.BattleMap;
import dnd.model.Combatant;
import dnd.model.MapLight;
import dnd.model.MapObject;
import dnd.model.MapPin;
import dnd.model.MapToken;
import dnd.model.Terrain;
import dnd.storage.Json;
import dnd.storage.ProjectStore;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
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
    private enum Mode { NONE, PAN, MOVE, MARQUEE, RESIZE, ROTATE, PAINT, RULER, FOG_BRUSH, FOG_RECT, TEMPLATE_AIM, PIN_MOVE, LIGHT_MOVE }

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
    /** Opens a card in a tab (set by the map editor; used by a token's "Open card" menu item). */
    public java.util.function.Consumer<String> openCard = id -> { };
    /** Called with a token's id when it is clicked (used by the battle screen to show that creature's card). */
    public java.util.function.Consumer<String> onTokenClicked = id -> { };
    private double fogX0, fogY0, fogX1, fogY1;
    private String pendingSnapshot;
    /** The pin or light being dragged, and the pin under the pointer (for its hover label). */
    private String movingId, hoverPinId;
    /** Opens a project item in a tab (set by the map editor; used by a pin's link). */
    public java.util.function.Consumer<String> openItem = id -> { };
    /** Lets a test answer the link-and-note dialog. */
    static java.util.function.Supplier<LinkNoteDialog.Result> linkNoteAnswer;
    private final javafx.animation.PauseTransition hoverDelay = new javafx.animation.PauseTransition(javafx.util.Duration.millis(450));
    private final javafx.stage.Popup tip = new javafx.stage.Popup();
    private String hoverKey;

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
            hoverTip(e);
            MapPin overPin = hitPin(hoverWX, hoverWY);
            String pinId = overPin == null ? null : overPin.id;
            boolean pinChanged = !Objects.equals(pinId, hoverPinId);
            hoverPinId = pinId;
            MapTool tool = ts.tool.get();
            if (tool == MapTool.ASSET || tool == MapTool.TERRAIN || tool == MapTool.TOKEN || pinChanged) redraw();
        });
        setOnMouseExited(e -> {
            hideTip();
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
            refreshCursor();
            redraw();
        });
        ts.armedAsset.addListener((o, a, n) -> redraw());
        // dark map on/off lives in the map file
        ts.darkMap.set(map.darkMap);
        ts.darkMap.addListener((o, a, n) -> {
            map.darkMap = n;
            if (n) for (MapToken t : map.tokens) autoReveal(t); // what the creatures can see clears the fog
            store.markMapDirty(map);
            redraw();
        });
        // changing the shape or size in the toolbar reshapes the area that is already on the map
        ts.templateShape.addListener((o, a, n) -> reshapeTemplate());
        ts.templateFeet.addListener((o, a, n) -> reshapeTemplate());
        ts.rulerShared.addListener((o, a, n) -> {
            if (map.ruler != null) {
                map.ruler.shared = n;
                store.markMapDirty(map);
                redraw();
            }
        });
        ts.templateShared.addListener((o, a, n) -> {
            if (map.template != null) {
                map.template.shared = n;
                store.markMapDirty(map);
                redraw();
            }
        });
        // fog on/off lives in the map file; turning it on clears fog around tokens that can see
        ts.fogEnabled.set(map.fogEnabled);
        ts.fogEnabled.addListener((o, a, n) -> {
            map.fogEnabled = n;
            if (n) for (MapToken t : map.tokens) autoReveal(t);
            store.markMapDirty(map);
            redraw();
        });
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
        MapRenderer.draw(g, map, grid, lib, z, map.showGrid, MapRenderer.Audience.DM);

        drawHover(g);
        drawSelection(g);
        drawFogRect(g);
        drawPinHover(g);
        drawBadges(g);
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
        } else if (t == MapTool.TERRAIN || t == MapTool.TOKEN || (t == MapTool.FOG && !ts.fogRect.get())) {
            int[] c = grid.cellAt(hoverWX, hoverWY);
            if (c == null) return;
            g.setStroke(t == MapTool.FOG ? (ts.fogReveal.get() ? Color.web("#16a34a") : Color.web("#dc2626")) : SELECT_COLOR);
            g.setLineWidth(2 / z);
            int b = t == MapTool.TOKEN ? 1 : ts.brush.get();
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
        s.revealed = new java.util.LinkedHashSet<>(map.revealed);
        s.pins = map.pins;
        s.lights = map.lights;
        return Json.write(s);
    }

    private void checkpoint() {
        undoStack.addLast(snapshot());
        if (undoStack.size() > GeneralSettings.undoDepth(200)) undoStack.removeFirst();
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
        map.revealed.clear();
        map.revealed.addAll(s.revealed);
        map.pins.clear();
        map.pins.addAll(s.pins);
        map.lights.clear();
        map.lights.addAll(s.lights);
        selected.removeIf(id -> map.findObject(id) == null && map.findToken(id) == null);
        dnd.battle.BattleEngine.relinkTokens(map.battle, map);
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
            case TEMPLATE -> pressTemplate();
            case PIN -> pressPin(e);
            case LIGHT -> pressLight(e);
            case FOG -> pressFog();
            case RULER -> {
                int[] c = grid.cellAt(pressWX, pressWY);
                if (c != null) {
                    double[] at = grid.center(c[0], c[1]);
                    map.ruler = new BattleMap.Ruler(at[0], at[1], ts.rulerShared.get());
                    mode = Mode.RULER;
                    store.markMapDirty(map);
                }
            }
        }
        redraw();
    }

    private void pressSelect(MouseEvent e) {
        hideTip();
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
        if (obj != null && e.getClickCount() == 2 && obj.link != null && store.itemExists(obj.link)) {
            openItem.accept(obj.link);
            return;
        }
        if (tok != null && e.getClickCount() == 2) {
            selected.clear();
            selected.add(tok.id);
            editTokenLabel(tok);
            return;
        }
        // Ctrl+click or Shift+click toggles exactly the thing under the pointer, keeping the rest selected
        boolean multi = e.isShiftDown() || e.isShortcutDown();
        if (tok != null && !multi) onTokenClicked.accept(tok.id);
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
            case FOG_BRUSH -> fogStroke(px, py);
            case FOG_RECT -> {
                fogX1 = px;
                fogY1 = py;
            }
            case RULER -> {
                int[] c = grid.cellAt(px, py);
                if (c != null && map.ruler != null) {
                    double[] at = grid.center(c[0], c[1]);
                    map.ruler.bx = at[0];
                    map.ruler.by = at[1];
                    store.markMapDirty(map); // the players' window follows it when shared
                }
            }
            case TEMPLATE_AIM -> aimTemplate(px, py, e.isAltDown());
            case PIN_MOVE -> {
                MapPin pin = findPin(movingId);
                if (pin != null) {
                    gestureCheckpoint();
                    pin.x = px;
                    pin.y = py;
                    store.markMapDirty(map);
                }
            }
            case LIGHT_MOVE -> {
                MapLight l = findLight(movingId);
                if (l != null && l.tokenId == null) {
                    gestureCheckpoint();
                    double[] at = lightSpot(px, py);
                    l.x = at[0];
                    l.y = at[1];
                    store.markMapDirty(map);
                }
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
        for (String id : startTok.keySet()) {
            MapToken t = map.findToken(id);
            if (t != null) autoReveal(t);
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
        if (mode == Mode.FOG_RECT) finishFogRect();
        if (mode == Mode.TEMPLATE_AIM) store.markMapDirty(map); // the players' window follows it when shared
        mode = Mode.NONE;
        movingId = null;
        gestureCheckpointed = false;
        pendingSnapshot = null;
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

    // ================================================================ battle support

    /** The battle state changed (HP, turn, ...): save and redraw so the turn ring and fallen tokens update. */
    public void battleChanged() {
        store.markMapDirty(map);
        redraw();
    }

    private static final java.util.Map<Combatant.Side, String> SIDE_COLORS = java.util.Map.of(
            Combatant.Side.PLAYER, "#2b6cb0", Combatant.Side.ALLY, "#2f855a", Combatant.Side.ENEMY, "#c0392b");

    /** A short token label: "Goblin 2" -> "Go2", "Wolf" -> "Wol". */
    static String tokenLabel(String name) {
        String n = name == null ? "" : name.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(.*?)\\s*(\\d+)$").matcher(n);
        if (m.matches() && !m.group(1).isEmpty()) {
            String base = m.group(1);
            String num = m.group(2);
            return base.substring(0, Math.min(base.length(), num.length() > 1 ? 1 : 2)) + num;
        }
        return n.substring(0, Math.min(3, n.length()));
    }

    private static int tokenSizeFor(String cardSize) {
        if (cardSize == null) return 1;
        switch (cardSize.toLowerCase()) {
            case "large": return 2;
            case "huge": return 3;
            case "gargantuan": return 4;
            default: return 1;
        }
    }

    /**
     * Puts a token for each new combatant on free cells around the middle of the view, linked both ways
     * (combatant.tokenId and token.cardId). Players' tokens clear fog around themselves.
     */
    public void addCombatantTokens(java.util.List<Combatant> added) {
        if (added.isEmpty()) return;
        checkpoint();
        int[] start = grid.cellAt(wx(getWidth() / 2), wy(getHeight() / 2));
        if (start == null) start = new int[]{grid.cols() / 2, grid.rows() / 2};
        final int[] origin = start;
        java.util.Set<String> taken = new java.util.HashSet<>();
        for (MapToken t : map.tokens) taken.add(BattleMap.key(t.col, t.row));
        java.util.List<int[]> cells = new java.util.ArrayList<>();
        for (int r = 0; r < grid.rows(); r++) for (int c = 0; c < grid.cols(); c++) cells.add(new int[]{c, r});
        cells.sort(java.util.Comparator.comparingInt((int[] a) -> grid.distance(origin[0], origin[1], a[0], a[1]))
                .thenComparingInt(a -> a[1]).thenComparingInt(a -> a[0]));
        int next = 0;
        selected.clear();
        for (Combatant co : added) {
            while (next < cells.size() && taken.contains(BattleMap.key(cells.get(next)[0], cells.get(next)[1]))) next++;
            if (next >= cells.size()) break;
            int[] cell = cells.get(next++);
            taken.add(BattleMap.key(cell[0], cell[1]));
            dnd.model.Card card = co.cardId == null ? null : store.card(co.cardId);
            MapToken t = new MapToken();
            t.col = cell[0];
            t.row = cell[1];
            t.label = tokenLabel(co.name);
            t.color = SIDE_COLORS.get(co.side);
            t.size = card == null ? 1 : tokenSizeFor(card.size);
            t.cardId = co.cardId;
            t.revealRadius = co.side == Combatant.Side.PLAYER ? 6 : 0;
            map.tokens.add(t);
            co.tokenId = t.id;
            autoReveal(t);
            selected.add(t.id);
        }
        changed();
    }

    public void removeTokenById(String tokenId) {
        if (tokenId == null) return;
        checkpoint();
        map.tokens.removeIf(t -> t.id.equals(tokenId));
        map.lights.removeIf(l -> tokenId.equals(l.tokenId));
        selected.remove(tokenId);
        changed();
    }

    /** Selects a token and pans the view so it is in the middle. */
    public void centerOnToken(String tokenId) {
        MapToken t = map.findToken(tokenId);
        if (t == null || !grid.inside(t.col, t.row)) return;
        double[] c = grid.center(t.col, t.row);
        selected.clear();
        selected.add(t.id);
        map.offsetX = getWidth() / 2 - c[0] * zoom();
        map.offsetY = getHeight() / 2 - c[1] * zoom();
        store.markMapDirty(map);
        redraw();
    }

    // ================================================================ fog of war

    /** Starts a fog gesture. The state before it is remembered so the first real change undoes as one step. */
    private void pressFog() {
        if (!ts.fogEnabled.get()) ts.fogEnabled.set(true);
        pendingSnapshot = snapshot();
        if (ts.fogRect.get()) {
            mode = Mode.FOG_RECT;
            fogX0 = fogX1 = pressWX;
            fogY0 = fogY1 = pressWY;
        } else {
            mode = Mode.FOG_BRUSH;
            fogStroke(pressWX, pressWY);
        }
    }

    /** Pushes the remembered pre-gesture state onto the undo stack (once), now that something changed. */
    private void commitPending() {
        if (!gestureCheckpointed && pendingSnapshot != null) {
            undoStack.addLast(pendingSnapshot);
            if (undoStack.size() > GeneralSettings.undoDepth(200)) undoStack.removeFirst();
            redoStack.clear();
            gestureCheckpointed = true;
        }
    }

    private void fogStroke(double px, double py) {
        boolean any = false;
        if (Double.isNaN(lastPaintWX)) {
            any = fogAround(px, py);
        } else {
            double dist = Math.hypot(px - lastPaintWX, py - lastPaintWY);
            int steps = Math.max(1, (int) Math.ceil(dist / (map.cellSize / 3)));
            for (int i = 1; i <= steps; i++) {
                double t = (double) i / steps;
                any |= fogAround(lastPaintWX + (px - lastPaintWX) * t, lastPaintWY + (py - lastPaintWY) * t);
            }
        }
        lastPaintWX = px;
        lastPaintWY = py;
        if (any) store.markMapDirty(map);
    }

    private boolean fogAround(double px, double py) {
        int[] c = grid.cellAt(px, py);
        if (c == null) return false;
        int n = Fog.paint(map, grid, c[0], c[1], Math.max(1, ts.brush.get()), ts.fogReveal.get());
        if (n > 0) commitPending();
        return n > 0;
    }

    private void finishFogRect() {
        int n = Fog.setRect(map, grid, fogX0, fogY0, fogX1, fogY1, ts.fogReveal.get());
        if (n > 0) {
            commitPending();
            store.markMapDirty(map);
        }
    }

    private void drawFogRect(GraphicsContext g) {
        if (mode != Mode.FOG_RECT) return;
        Color c = ts.fogReveal.get() ? Color.web("#16a34a") : Color.web("#dc2626");
        double l = Math.min(fogX0, fogX1), t = Math.min(fogY0, fogY1);
        g.setFill(Color.color(c.getRed(), c.getGreen(), c.getBlue(), 0.25));
        g.setStroke(c);
        g.setLineWidth(2 / zoom());
        g.fillRect(l, t, Math.abs(fogX1 - fogX0), Math.abs(fogY1 - fogY0));
        g.strokeRect(l, t, Math.abs(fogX1 - fogX0), Math.abs(fogY1 - fogY0));
    }

    /** Clears fog around a token that reveals fog (called whenever it is placed or moved). */
    private void autoReveal(MapToken t) {
        int cells = dnd.map.LightingModel.sightCells(map, t); // reveal radius, darkvision and a carried light
        if (map.fogEnabled && cells > 0 && grid.inside(t.col, t.row)) {
            Fog.revealRadius(map, grid, t.col, t.row, cells);
        }
    }

    public void revealAllFog() {
        checkpoint();
        Fog.revealAll(map, grid);
        changed();
    }

    public void hideAllFog() {
        checkpoint();
        Fog.hideAll(map);
        for (MapToken t : map.tokens) autoReveal(t); // tokens that see keep their surroundings clear
        changed();
    }

    private void setHiddenForSelection(boolean hidden) {
        boolean any = false;
        for (String id : selected) if (map.findObject(id) != null || map.findToken(id) != null) any = true;
        if (!any) return;
        checkpoint();
        for (String id : selected) {
            MapObject o = map.findObject(id);
            if (o != null) o.hidden = hidden;
            MapToken t = map.findToken(id);
            if (t != null) t.hidden = hidden;
        }
        changed();
    }

    private void setRevealRadiusForSelection(int radius) {
        boolean any = false;
        for (String id : selected) if (map.findToken(id) != null) any = true;
        if (!any) return;
        checkpoint();
        for (String id : selected) {
            MapToken t = map.findToken(id);
            if (t != null) {
                t.revealRadius = radius;
                autoReveal(t);
            }
        }
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
        autoReveal(t);
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
        map.lights.removeIf(l -> l.tokenId != null && selected.contains(l.tokenId));
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
            autoReveal(c);
            selected.add(c.id);
        }
        changed();
    }

    // ================================================================ keyboard & menu

    /** Does what a keyboard shortcut asks (the workspace calls this, so the keys work even when a toolbar button has the focus). */
    @Override
    public boolean perform(String id) {
        switch (id) {
            case "item.copy" -> copy();
            case "item.cut" -> {
                copy();
                deleteSelected();
            }
            case "item.paste" -> paste();
            case "item.duplicate" -> {
                copy();
                paste();
            }
            case "item.selectAll" -> {
                selected.clear();
                for (MapObject o : map.objects) selected.add(o.id);
                for (MapToken t : map.tokens) selected.add(t.id);
                redraw();
            }
            case "item.delete" -> {
                return deleteSelection();
            }
            case "item.escape" -> {
                return escape();
            }
            case "item.fit" -> fitToWindow();
            case "item.actualSize" -> zoomTo(1);
            case "item.zoomIn" -> zoomTo(map.zoom * 1.25);
            case "item.zoomOut" -> zoomTo(map.zoom / 1.25);
            case "item.rotateLeft" -> {
                if (selected.isEmpty()) return false;
                rotateSelected(-15);
            }
            case "item.rotateRight" -> {
                if (selected.isEmpty()) return false;
                rotateSelected(15);
            }
            case "item.flip" -> {
                if (selected.isEmpty()) return false;
                flipSelected();
            }
            case "item.hide" -> {
                return toggleHiddenSelection();
            }
            case "item.link" -> {
                Object piece = selected.size() == 1 ? firstSelectedPiece() : null;
                if (piece == null) return false;
                editLinkNote(piece);
            }
            default -> {
                if (id.startsWith("tool.battle.")) return selectTool(Integer.parseInt(id.substring("tool.battle.".length())) - 1);
                return false;
            }
        }
        return true;
    }

    /** Zooms around the middle of the view (1 = actual size). */
    private void zoomTo(double z) {
        double cx = getWidth() / 2, cy = getHeight() / 2;
        double px = wx(cx), py = wy(cy);
        map.zoom = Math.max(0.1, Math.min(5, z));
        map.offsetX = cx - px * map.zoom;
        map.offsetY = cy - py * map.zoom;
        store.markMapDirty(map);
        redraw();
    }

    /** Hides the selected pieces and tokens from the players, or shows them again if all of them were hidden. */
    private boolean toggleHiddenSelection() {
        if (selected.isEmpty()) return false;
        boolean allHidden = true;
        for (String s : selected) {
            MapObject so = map.findObject(s);
            MapToken st = map.findToken(s);
            if ((so != null && !so.hidden) || (st != null && !st.hidden)) allHidden = false;
        }
        setHiddenForSelection(!allHidden);
        return true;
    }

    @Override
    public dnd.keys.Shortcuts.Context keyContext() {
        return dnd.keys.Shortcuts.Context.BATTLEMAP;
    }

    @Override
    public boolean hasCanvasFocus() {
        return isFocused();
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
        if (map.template != null || map.ruler != null) {
            map.template = null;
            map.ruler = null;
            store.markMapDirty(map);
        }
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
        // every other key is a shortcut the workspace handles (see Shortcuts and perform)
    }

    private void onContextMenu(ContextMenuEvent e) {
        double px = wx(e.getX()), py = wy(e.getY());
        if (pinOrLightMenu(e, px, py)) return;
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
            menu.getItems().add(item("Rotate left 15\u00b0  ( [ )", false, () -> rotateSelected(-15)));
            menu.getItems().add(item("Rotate right 15\u00b0  ( ] )", false, () -> rotateSelected(15)));
            menu.getItems().add(item("Flip  ( F )", false, this::flipSelected));
            menu.getItems().add(item("Bring to front", false, () -> reorder(true)));
            menu.getItems().add(item("Send to back", false, () -> reorder(false)));
            menu.getItems().add(new SeparatorMenuItem());
        }
        Object single = selected.size() == 1 ? firstSelectedPiece() : null;
        if (single != null) {
            menu.getItems().add(item("Link and note...", false, () -> editLinkNote(single)));
            String lk = linkOf(single);
            if (lk != null && store.itemExists(lk)) menu.getItems().add(item("Open linked item", false, () -> openItem.accept(lk)));
        }
        if (has) {
            boolean allHidden = true;
            for (String s : selected) {
                MapObject so = map.findObject(s);
                MapToken st = map.findToken(s);
                if ((so != null && !so.hidden) || (st != null && !st.hidden)) allHidden = false;
            }
            final boolean makeVisible = allHidden;
            menu.getItems().add(item(makeVisible ? "Show to players" : "Hide from players", false, () -> setHiddenForSelection(!makeVisible)));
        }
        if (hasTok) {
            MapToken cardToken = null;
            for (String s : selected) {
                MapToken st = map.findToken(s);
                if (st != null && st.cardId != null && store.isCard(st.cardId)) cardToken = st;
            }
            if (cardToken != null) {
                final String cardId = cardToken.cardId;
                menu.getItems().add(item("Open card", false, () -> openCard.accept(cardId)));
            }
            Menu fogMenu = new Menu("Reveals fog around it");
            for (int rad : new int[]{0, 3, 6, 9, 12}) {
                fogMenu.getItems().add(item(rad == 0 ? "Off" : rad + " cells (" + rad * 5 + " ft)", false, () -> setRevealRadiusForSelection(rad)));
            }
            menu.getItems().add(fogMenu);
            Menu visionMenu = new Menu("Sees in the dark (darkvision)");
            for (int ft : new int[]{0, 30, 60, 90, 120}) {
                visionMenu.getItems().add(item(ft == 0 ? "No darkvision" : ft + " ft", false, () -> setVisionForSelection(ft)));
            }
            menu.getItems().add(visionMenu);
            Menu lightMenu = new Menu("Carries a light");
            lightMenu.getItems().add(item("No light", false, () -> setCarriedLight(null)));
            for (dnd.map.LightPresets.Preset p : dnd.map.LightPresets.ALL) lightMenu.getItems().add(item(p.toString(), false, () -> setCarriedLight(p)));
            menu.getItems().add(lightMenu);
        }
        if (has) menu.getItems().add(new SeparatorMenuItem());
        menu.getItems().add(item("Copy  (Ctrl+C)", !has, this::copy));
        menu.getItems().add(item("Cut  (Ctrl+X)", !has, () -> { copy(); deleteSelected(); }));
        menu.getItems().add(item("Paste  (Ctrl+V)", clipboardJson == null, this::paste));
        menu.getItems().add(item("Duplicate  (Ctrl+D)", !has, () -> { copy(); paste(); }));
        menu.getItems().add(item("Delete  (Del)", !has, this::deleteSelected));
        menu.show(this, e.getScreenX(), e.getScreenY());
        e.consume();
    }

    // ================================================================ links and notes on pieces

    private Object firstSelectedPiece() {
        for (String s : selected) {
            MapToken t = map.findToken(s);
            if (t != null) return t;
            MapObject o = map.findObject(s);
            if (o != null) return o;
        }
        return null;
    }

    private static String linkOf(Object o) {
        return o instanceof MapToken t ? t.link : o instanceof MapObject m ? m.link : null;
    }

    private static String noteOf(Object o) {
        String n = o instanceof MapToken t ? t.note : o instanceof MapObject m ? m.note : null;
        return n == null || n.isBlank() ? null : n;
    }

    private String nameOf(Object o) {
        if (o instanceof MapToken t) return t.label == null || t.label.isBlank() ? "Token" : t.label;
        if (o instanceof MapObject m) {
            dnd.map.AssetLibrary.Asset a = m.assetId == null ? null : lib.get(m.assetId);
            return a == null ? "Piece" : a.name;
        }
        return "";
    }

    private void editLinkNote(Object o) {
        LinkNoteDialog.Result r;
        if (linkNoteAnswer != null) r = linkNoteAnswer.get();
        else r = LinkNoteDialog.show(getScene() == null ? null : getScene().getWindow(), store, nameOf(o), noteOf(o), linkOf(o), openItem).orElse(null);
        if (r == null) return;
        checkpoint();
        if (o instanceof MapToken t) {
            t.note = r.note();
            t.link = r.link();
        } else if (o instanceof MapObject m) {
            m.note = r.note();
            m.link = r.link();
        }
        changed();
    }

    private Object pieceAt(double px, double py) {
        MapToken t = hitToken(px, py);
        return t != null ? t : hitObject(px, py);
    }

    private void hideTip() {
        hoverDelay.stop();
        tip.hide();
        hoverKey = null;
    }

    /** Shows the note and a preview of the link of the piece under the pointer, after a short rest (DM only). */
    private void hoverTip(MouseEvent e) {
        MapTool tool = ts.tool.get();
        if (mode != Mode.NONE || spaceDown || (tool != MapTool.SELECT && tool != MapTool.HAND)) {
            if (hoverKey != null) hideTip();
            return;
        }
        Object o = pieceAt(wx(e.getX()), wy(e.getY()));
        String link = linkOf(o);
        if (link != null && !store.itemExists(link)) link = null;
        String note = noteOf(o);
        String key = o == null || (note == null && link == null) ? null : o instanceof MapToken t ? t.id : ((MapObject) o).id;
        if (Objects.equals(key, hoverKey)) return;
        hideTip();
        hoverKey = key;
        if (key == null) return;
        final String showNote = note, showLink = link;
        final double sx = e.getScreenX(), sy = e.getScreenY();
        hoverDelay.setDuration(javafx.util.Duration.millis(GeneralSettings.hoverDelayMs(450)));
        hoverDelay.setOnFinished(ev -> {
            javafx.scene.Node preview = ItemPreview.hover(store, showNote, showLink);
            if (preview == null) return;
            preview.setMouseTransparent(true);
            tip.getContent().setAll(preview);
            tip.show(this, sx + 18, sy + 18);
        });
        hoverDelay.playFromStart();
    }

    /** Small badges on pieces that have a note (green) or a link (blue); shown to the DM only. */
    private void drawBadges(GraphicsContext g) {
        MapTool t = ts.tool.get();
        if (t != MapTool.SELECT && t != MapTool.HAND) return;
        double r = 4.5 / zoom();
        for (MapObject o : map.objects) {
            if (linkOf(o) == null && noteOf(o) == null) continue;
            badge(g, o.x + o.w / 2, o.y - o.h / 2, r, o.link != null);
        }
        for (MapToken tk : map.tokens) {
            if ((linkOf(tk) == null && noteOf(tk) == null) || !grid.inside(tk.col, tk.row)) continue;
            double[] c = grid.center(tk.col, tk.row);
            double d = tokenDiameter(tk) / 2 * 0.75;
            badge(g, c[0] + d, c[1] - d, r, tk.link != null);
        }
    }

    private static void badge(GraphicsContext g, double x, double y, double r, boolean link) {
        g.setFill(Color.WHITE);
        g.fillOval(x - r, y - r, 2 * r, 2 * r);
        g.setFill(Color.web(link ? "#2563eb" : "#16a34a"));
        g.fillOval(x - r * 0.65, y - r * 0.65, 1.3 * r, 1.3 * r);
    }

    // ================================================================ area templates, pins and lights

    /** A point on the map, snapped to the middle of its cell when snapping is on. */
    private double[] snapPoint(double px, double py) {
        if (ts.snap.get()) {
            int[] c = grid.cellAt(px, py);
            if (c != null) return grid.center(c[0], c[1]);
        }
        return new double[]{px, py};
    }

    private void pressTemplate() {
        double[] o = snapPoint(pressWX, pressWY);
        dnd.model.AreaTemplate t = new dnd.model.AreaTemplate(ts.templateShape.get(), o[0], o[1], ts.templateFeet.get());
        t.shared = ts.templateShared.get();
        map.template = t;
        mode = Mode.TEMPLATE_AIM;
        store.markMapDirty(map);
    }

    private void aimTemplate(double px, double py, boolean free) {
        dnd.model.AreaTemplate t = map.template;
        if (t == null) return;
        if (t.shape == dnd.model.AreaTemplate.Shape.CIRCLE) { // a burst has no direction: it follows the pointer
            double[] o = snapPoint(px, py);
            t.ox = o[0];
            t.oy = o[1];
        } else {
            double dx = px - t.ox, dy = py - t.oy;
            if (Math.hypot(dx, dy) < map.cellSize * 0.2) return;
            double a = Math.atan2(dy, dx);
            if (!free) a = Math.round(a / (Math.PI / 12)) * (Math.PI / 12); // 15 degree steps; Alt for free aim
            t.angle = a;
        }
        store.markMapDirty(map);
    }

    private void reshapeTemplate() {
        dnd.model.AreaTemplate t = map.template;
        if (t == null) return;
        t.shape = ts.templateShape.get();
        t.feet = ts.templateFeet.get();
        store.markMapDirty(map);
        redraw();
    }

    /** Takes the area off the map (toolbar button and Esc). */
    public void clearTemplate() {
        if (map.template == null) return;
        map.template = null;
        store.markMapDirty(map);
        redraw();
    }

    /** Takes the ruler and the area off the map. */
    public void clearMeasures() {
        if (map.template == null && map.ruler == null) return;
        map.template = null;
        map.ruler = null;
        store.markMapDirty(map);
        redraw();
    }

    private double pinRadius() {
        return Math.max(map.cellSize * 0.26, 11 / zoom());
    }

    private MapPin hitPin(double px, double py) {
        for (int i = map.pins.size() - 1; i >= 0; i--) {
            MapPin p = map.pins.get(i);
            if (Math.hypot(px - p.x, py - p.y) <= pinRadius()) return p;
        }
        return null;
    }

    private MapPin findPin(String id) {
        for (MapPin p : map.pins) if (p.id.equals(id)) return p;
        return null;
    }

    private MapLight findLight(String id) {
        for (MapLight l : map.lights) if (l.id.equals(id)) return l;
        return null;
    }

    private MapLight hitLight(double px, double py) {
        double r = Math.max(map.cellSize * 0.3, 12 / zoom());
        for (int i = map.lights.size() - 1; i >= 0; i--) {
            MapLight l = map.lights.get(i);
            double[] at = dnd.map.LightingModel.position(map, grid, l);
            if (at != null && Math.hypot(px - at[0], py - at[1]) <= r) return l;
        }
        return null;
    }

    private double[] lightSpot(double px, double py) {
        return snapPoint(px, py);
    }

    private void pressPin(MouseEvent e) {
        MapPin hit = hitPin(pressWX, pressWY);
        if (hit != null) {
            if (e.getClickCount() == 2) {
                editPin(hit, false);
                return;
            }
            mode = Mode.PIN_MOVE;
            movingId = hit.id;
            return;
        }
        editPin(new MapPin(pressWX, pressWY, ""), true); // not on the map until saved
    }

    private void editPin(MapPin pin, boolean isNew) {
        int number = isNew ? map.pins.size() + 1 : map.pins.indexOf(pin) + 1;
        PinDialog.Result r = PinDialog.show(getScene() == null ? null : getScene().getWindow(), store, pin, number, isNew, openItem);
        switch (r.action()) {
            case SAVE -> {
                checkpoint();
                pin.text = r.text();
                pin.link = r.link();
                if (isNew) map.pins.add(pin);
            }
            case DELETE -> {
                if (!isNew) {
                    checkpoint();
                    map.pins.remove(pin);
                }
            }
            case CANCEL -> { }
        }
        changed();
    }

    private void pressLight(MouseEvent e) {
        MapLight hit = hitLight(pressWX, pressWY);
        if (hit != null) {
            if (hit.tokenId == null) {
                mode = Mode.LIGHT_MOVE;
                movingId = hit.id;
            }
            return;
        }
        dnd.map.LightPresets.Preset p = ts.lightPreset.get();
        if (p == null) return;
        checkpoint();
        double[] at = lightSpot(pressWX, pressWY);
        MapLight l = new MapLight(p.name(), p.brightFeet(), p.dimFeet());
        l.x = at[0];
        l.y = at[1];
        map.lights.add(l);
        changed();
    }

    /** Right-click on a pin or a light: its own menu. Returns true if it handled the event. */
    private boolean pinOrLightMenu(ContextMenuEvent e, double px, double py) {
        MapPin pin = hitPin(px, py);
        MapLight light = pin == null ? hitLight(px, py) : null;
        if (pin == null && light == null) return false;
        menu.getItems().clear();
        if (pin != null) {
            final MapPin p = pin;
            menu.getItems().add(item("Edit pin...", false, () -> editPin(p, false)));
            if (p.link != null && store.itemExists(p.link)) menu.getItems().add(item("Open linked item", false, () -> openItem.accept(p.link)));
            menu.getItems().add(item("Delete pin", false, () -> {
                checkpoint();
                map.pins.remove(p);
                changed();
            }));
        } else {
            final MapLight l = light;
            menu.getItems().add(item("Light: " + l.name + " (" + l.brightFeet + "/" + l.dimFeet + " ft)", true, () -> { }));
            menu.getItems().add(item("Remove light", false, () -> {
                checkpoint();
                map.lights.remove(l);
                changed();
            }));
        }
        menu.show(this, e.getScreenX(), e.getScreenY());
        e.consume();
        return true;
    }

    private void setVisionForSelection(int feet) {
        boolean any = false;
        for (String id : selected) if (map.findToken(id) != null) any = true;
        if (!any) return;
        checkpoint();
        for (String id : selected) {
            MapToken t = map.findToken(id);
            if (t != null) {
                t.visionFeet = feet;
                autoReveal(t);
            }
        }
        changed();
    }

    /** Gives the selected tokens a light to carry (null = none). It moves with them. */
    private void setCarriedLight(dnd.map.LightPresets.Preset preset) {
        boolean any = false;
        for (String id : selected) if (map.findToken(id) != null) any = true;
        if (!any) return;
        checkpoint();
        for (String id : selected) {
            MapToken t = map.findToken(id);
            if (t == null) continue;
            map.lights.removeIf(l -> t.id.equals(l.tokenId));
            if (preset != null) {
                MapLight l = new MapLight(preset.name(), preset.brightFeet(), preset.dimFeet());
                l.tokenId = t.id;
                map.lights.add(l);
            }
            autoReveal(t);
        }
        changed();
    }

    /** The note of the pin under the pointer, shown beside it while the mouse rests on it (DM only). */
    private void drawPinHover(GraphicsContext g) {
        if (hoverPinId == null) return;
        MapPin p = findPin(hoverPinId);
        if (p == null || p.text == null || p.text.isBlank()) return;
        double z = zoom();
        String text = p.text.length() > 140 ? p.text.substring(0, 137) + "..." : p.text;
        javafx.scene.text.Text probe = new javafx.scene.text.Text(text);
        probe.setFont(Font.font("System", 13));
        probe.setWrappingWidth(240);
        double w = probe.getLayoutBounds().getWidth() + 16, h = probe.getLayoutBounds().getHeight() + 12;
        double x = p.x + pinRadius() + 6 / z, y = p.y - h / z / 2;
        g.save();
        g.setFill(Color.web("#1f2937", 0.94));
        g.fillRoundRect(x, y, w / z, h / z, 8 / z, 8 / z);
        g.setFill(Color.WHITE);
        g.setFont(Font.font("System", 13 / z));
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.TOP);
        // simple wrapping at word boundaries
        double lineH = 17 / z, ty = y + 6 / z;
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            javafx.scene.text.Text t2 = new javafx.scene.text.Text(line + (line.length() > 0 ? " " : "") + word);
            t2.setFont(Font.font("System", 13));
            if (t2.getLayoutBounds().getWidth() > 240 && line.length() > 0) {
                g.fillText(line.toString(), x + 8 / z, ty);
                ty += lineH;
                line.setLength(0);
            }
            if (line.length() > 0) line.append(" ");
            line.append(word);
        }
        if (line.length() > 0) g.fillText(line.toString(), x + 8 / z, ty);
        g.restore();
    }

    private static MenuItem item(String text, boolean disabled, Runnable r) {
        MenuItem m = new MenuItem(text);
        m.setDisable(disabled);
        m.setOnAction(ev -> r.run());
        return m;
    }
}
