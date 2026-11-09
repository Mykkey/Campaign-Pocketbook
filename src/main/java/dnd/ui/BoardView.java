package dnd.ui;

import dnd.model.Board;
import dnd.model.BoardObject;
import dnd.model.BoardObject.Type;
import dnd.storage.Json;
import dnd.storage.ProjectStore;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.input.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * The whiteboard editor for one {@link Board}: an infinite canvas with pan/zoom, selection,
 * drawing tools, in-place text editing, undo/redo and object links.
 */
public class BoardView extends Pane implements ItemView {

    /** What the board needs from the surrounding workspace. */
    public interface Host {
        /** Opens (or focuses) the tab for a whiteboard or battlemap. */
        void openItem(String itemId);

        /** Lets the user pick any whiteboard or battlemap (for links). */
        Optional<String> pickBoard(String title);

        /** Lets the user pick a battlemap only (for inserting previews). */
        Optional<String> pickMap(String title);

        /**
         * Lets the user write or edit a note. Returns the new text, an empty string to remove the note, or nothing
         * if they cancelled.
         */
        default Optional<String> editNote(String title, String current) {
            return NoteDialog.show(title, current);
        }
    }

    @Override public String itemId() { return board.id; }

    @Override
    public void activated() {
        refreshCursor();
        redraw(); // battlemap previews may be out of date after editing a map in another tab
        requestFocus();
    }

    @Override
    public boolean deleteSelection() {
        if (selected.isEmpty()) return false;
        deleteSelected();
        return true;
    }

    @Override
    public boolean selectTool(int index) {
        Tool[] tools = Tool.values();
        if (index < 0 || index >= tools.length) return false;
        ts.tool.set(tools[index]);
        return true;
    }

    private enum Mode { NONE, PAN, MOVE, MARQUEE, RESIZE, DRAW, SHAPE, ERASE }

    private static final Color SELECT_COLOR = Color.web("#2563eb");
    private static final String DEFAULT_STICKY = "#FFF2A8";
    private static final Set<String> IMAGE_EXT = Set.of("png", "jpg", "jpeg", "gif", "bmp");

    // shared between all boards so copy/paste works across tabs
    private static final Map<Path, Image> IMAGES = new HashMap<>();
    private static final Map<String, Color> COLORS = new HashMap<>();
    private static String clipboardJson;
    private static boolean preferInternal;
    private static int pasteCount;

    private final Board board;
    private final ProjectStore store;
    private final ToolState ts;
    private final Host host;

    private final Canvas canvas = new Canvas();
    private final Set<String> selected = new LinkedHashSet<>();
    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();
    private final Map<String, Object[]> wrapCache = new HashMap<>();
    private final ContextMenu menu = new ContextMenu();

    // hover previews (tooltips) and "link this name?" hints
    private final javafx.stage.Popup tipPopup = new javafx.stage.Popup();
    private final javafx.stage.Popup hintPopup = new javafx.stage.Popup();
    private final javafx.animation.PauseTransition hoverDelay = new javafx.animation.PauseTransition(javafx.util.Duration.millis(350));
    private final javafx.animation.PauseTransition hintHide = new javafx.animation.PauseTransition(javafx.util.Duration.millis(700));
    private String hoverKey;
    /** True while the pointer is on the hint box (its own window, so the canvas sees the pointer "leave"). */
    private boolean hintHovered;
    private boolean pickingTarget;
    private java.util.List<dnd.text.NameHints.Entry> nameIndex = java.util.List.of();
    private int nameIndexVersion = -1;
    private final Map<String, Object[]> hintCache = new HashMap<>();

    private Mode mode = Mode.NONE;
    private boolean gestureCheckpointed, dragStarted, spaceDown;
    private double pressSX, pressSY, lastSX, lastSY, pressWX, pressWY, resizeRatio = 1;
    private double mqX0, mqY0, mqX1, mqY1;
    private double ctxWX, ctxWY;
    private BoardObject tmp;

    private TextArea editor;
    private BoardObject editing;
    private boolean editingNew;

    private final javafx.beans.value.ChangeListener<Boolean> themeListener = (o, was, now) -> redraw();

    public BoardView(Board board, ProjectStore store, ToolState ts, Host host) {
        Theme.darkProperty().addListener(new javafx.beans.value.WeakChangeListener<>(themeListener));
        this.board = board;
        this.store = store;
        this.ts = ts;
        this.host = host;

        getChildren().add(canvas);
        setMinSize(0, 0);
        setFocusTraversable(true);
        for (BoardObject o : board.objects) measure(o);

        setOnMousePressed(this::onPressed);
        setOnMouseDragged(this::onDragged);
        setOnMouseReleased(this::onReleased);
        setOnScroll(this::onScroll);
        setOnKeyPressed(this::onKeyPressed);
        setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.SPACE) {
                spaceDown = false;
                refreshCursor();
            }
        });
        setOnContextMenuRequested(this::onContextMenu);
        setOnMouseMoved(this::onHover);
        setOnMouseExited(e -> {
            hoverDelay.stop();
            hideTip();
            hintHide.playFromStart();
        });
        tipPopup.setAutoFix(true);
        tipPopup.setAutoHide(false);
        hintPopup.setAutoFix(true);
        hintPopup.setAutoHide(true);
        hintHide.setOnFinished(e -> {
            if (hintHovered) return; // the pointer is on the box: leave it open
            hintPopup.hide();
            hoverKey = null;
        });
        ts.nameHints.addListener((o, was, now) -> redraw());
        setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) e.acceptTransferModes(TransferMode.COPY);
            e.consume();
        });
        setOnDragDropped(this::onFilesDropped);
        refreshCursor();
    }

    public Board board() { return board; }

    // ================================================================ layout & coordinates

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        canvas.setWidth(Math.max(1, getWidth()));
        canvas.setHeight(Math.max(1, getHeight()));
        redraw();
    }

    private double zoom() { return board.zoom; }

    private double wx(double sx) { return (sx - board.offsetX) / board.zoom; }

    private double wy(double sy) { return (sy - board.offsetY) / board.zoom; }

    private double viewCenterX() { return wx(getWidth() / 2); }

    private double viewCenterY() { return wy(getHeight() / 2); }

    public void refreshCursor() {
        Tool t = ts.tool.get();
        Cursor c;
        if (spaceDown || t == Tool.HAND) c = Cursor.OPEN_HAND;
        else if (t == Tool.TEXT) c = Cursor.TEXT;
        else if (t == Tool.SELECT || t == Tool.STICKY) c = Cursor.DEFAULT;
        else c = Cursor.CROSSHAIR;
        setCursor(c);
    }

    // ================================================================ rendering

    private static Color col(String css) {
        return COLORS.computeIfAbsent(css == null ? "#000000" : css, s -> {
            try {
                return Color.web(s);
            } catch (IllegalArgumentException e) {
                return Color.BLACK;
            }
        });
    }

    static String hex(Color c) {
        return String.format("#%02X%02X%02X%02X",
                (int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255), (int) Math.round(c.getOpacity() * 255));
    }

    static Font fontFor(BoardObject o) {
        return Font.font("System", o.bold ? FontWeight.BOLD : FontWeight.NORMAL,
                o.italic ? FontPosture.ITALIC : FontPosture.REGULAR, o.fontSize);
    }

    /** Recomputes the size of a TEXT object from its content. */
    static void measure(BoardObject o) {
        if (o.type != Type.TEXT) return;
        List<RichLayout.Line> lines = RichLayout.layout(o.text, o, 0);
        o.w = Math.max(10, RichLayout.maxWidth(lines));
        o.h = Math.max(o.fontSize * 1.3, lines.size() * RichLayout.lineHeight(o));
    }

    private static Color linkColor(boolean ok) {
        boolean dark = Theme.isDark();
        return Color.web(ok ? (dark ? "#6aa5ff" : "#2563eb") : (dark ? "#f87171" : "#dc2626"));
    }

    private static Color noteColor() {
        return Color.web(Theme.isDark() ? "#4ade80" : "#16a34a");
    }

    /**
     * Draws laid-out lines (each word in its own bold/italic) starting at (x, y), top-aligned. Words that carry a
     * link are blue and underlined, note-only words are green and underlined, words with both are blue with a
     * small green dot after them, and names of cards and spells that could be linked get a faint dotted underline.
     */
    private void drawLines(GraphicsContext g, List<RichLayout.Line> lines, BoardObject o, double x, double y,
                           double maxY) {
        double lh = RichLayout.lineHeight(o);
        javafx.scene.paint.Paint base = g.getFill();
        double underline = Math.min(lh - 1, o.fontSize * 1.12);
        for (RichLayout.Line line : lines) {
            if (y + lh > maxY) break;
            List<RichLayout.Seg> segs = line.segs();
            for (int i = 0; i < segs.size(); i++) {
                RichLayout.Seg s = segs.get(i);
                dnd.model.SpanRef sr = o.ref(s.ref());
                boolean link = sr != null && sr.link != null, note = sr != null && sr.hasNote();
                Color color = link ? linkColor(store.itemExists(sr.link)) : note ? noteColor() : null;
                g.setFont(RichLayout.font(o, s.bold(), s.italic()));
                g.setFill(color != null ? color : base);
                g.fillText(s.text(), x + s.x(), y);
                if (color != null) {
                    g.setStroke(color);
                    g.setLineWidth(1.3);
                    g.setLineDashes((double[]) null);
                    g.strokeLine(x + s.x(), y + underline, x + s.x() + s.w(), y + underline);
                    boolean last = i + 1 >= segs.size() || segs.get(i + 1).ref() != s.ref();
                    if (link && note && last) { // both: blue words with a small green dot after them
                        g.setFill(noteColor());
                        g.fillOval(x + s.x() + s.w() + 2, y + lh * 0.18, 5, 5);
                    }
                } else if (s.hint() != null) {
                    g.setStroke(Theme.isDark() ? Color.web("#8d95a1") : Color.web("#7a8394"));
                    g.setLineWidth(1.1);
                    g.setLineDashes(2, 3);
                    g.strokeLine(x + s.x(), y + underline, x + s.x() + s.w(), y + underline);
                    g.setLineDashes((double[]) null);
                }
            }
            y += lh;
        }
        g.setFill(base);
    }

    /** Lines of a TEXT object (no wrapping), cached so redraws while panning don't re-measure every word. */
    private List<RichLayout.Line> textLines(BoardObject o) {
        String key = "T|" + o.text + "|" + o.fontSize + o.bold + o.italic + hintKey(o);
        Object[] cached = wrapCache.get(o.id);
        if (cached != null && cached[0].equals(key)) {
            @SuppressWarnings("unchecked") List<RichLayout.Line> l = (List<RichLayout.Line>) cached[1];
            return l;
        }
        List<RichLayout.Line> out = RichLayout.layout(o.text, o, 0, hintsFor(o));
        wrapCache.put(o.id, new Object[]{key, out});
        return out;
    }

    private List<RichLayout.Line> wrappedLines(BoardObject o) {
        double width = o.w - 20;
        String key = o.text + "|" + width + "|" + o.fontSize + o.bold + o.italic + hintKey(o);
        Object[] cached = wrapCache.get(o.id);
        if (cached != null && cached[0].equals(key)) {
            @SuppressWarnings("unchecked") List<RichLayout.Line> l = (List<RichLayout.Line>) cached[1];
            return l;
        }
        List<RichLayout.Line> out = RichLayout.layout(o.text, o, width, hintsFor(o));
        wrapCache.put(o.id, new Object[]{key, out});
        return out;
    }

    // ================================================================ name hints

    /** Changes when the set of card/spell names, the dismissed names or the hint switch changes. */
    private String hintKey(BoardObject o) {
        return ts.nameHints.get() && (o.type == Type.TEXT || o.type == Type.STICKY)
                ? "|h" + store.namesVersion() + o.dismissedNames : "|h-";
    }

    private List<dnd.text.NameHints.Entry> nameIndex() {
        if (nameIndexVersion != store.namesVersion()) {
            List<dnd.text.NameHints.ItemNames> items = new ArrayList<>();
            for (String id : store.allCardIds()) {
                dnd.model.Card c = store.card(id);
                if (c != null) items.add(new dnd.text.NameHints.ItemNames(id, c.name, c.aliases));
            }
            for (String id : store.allSpellIds()) {
                dnd.model.Spell s = store.spell(id);
                if (s != null) items.add(new dnd.text.NameHints.ItemNames(id, s.name, List.of()));
            }
            nameIndex = dnd.text.NameHints.index(dnd.text.NameHints.entriesFor(items));
            nameIndexVersion = store.namesVersion();
            hintCache.clear();
        }
        return nameIndex;
    }

    /** The card and spell names in a text object that could be linked (none while editing or with hints off). */
    private List<dnd.text.NameHints.Hint> hintsFor(BoardObject o) {
        if (!ts.nameHints.get() || o == editing || (o.type != Type.TEXT && o.type != Type.STICKY)) return List.of();
        List<dnd.text.NameHints.Entry> index = nameIndex();
        if (index.isEmpty()) return List.of();
        String key = o.text + "|" + o.dismissedNames;
        Object[] cached = hintCache.get(o.id);
        if (cached != null && cached[0].equals(key)) {
            @SuppressWarnings("unchecked") List<dnd.text.NameHints.Hint> l = (List<dnd.text.NameHints.Hint>) cached[1];
            return l;
        }
        List<dnd.text.NameHints.Hint> found = dnd.text.NameHints.find(dnd.text.RichText.plain(o.text),
                dnd.text.RichText.spans(o.text), index, o.dismissedNames);
        hintCache.put(o.id, new Object[]{key, found});
        return found;
    }

    private Image image(BoardObject o) {
        if (o.image == null) return null;
        Path p = store.imagePath(o.image);
        if (!Files.isRegularFile(p)) return null;
        return IMAGES.computeIfAbsent(p, k -> new Image(k.toUri().toString()));
    }

    public void redraw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double W = canvas.getWidth(), H = canvas.getHeight();
        g.setTransform(1, 0, 0, 1, 0, 0);
        g.setFill(Theme.isDark() ? Color.web("#1c1e22") : Color.web("#fbfaf6"));
        g.fillRect(0, 0, W, H);
        drawGrid(g, W, H);

        g.setTransform(zoom(), 0, 0, zoom(), board.offsetX, board.offsetY);
        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineJoin(StrokeLineJoin.ROUND);

        double vl = wx(0) - 50, vt = wy(0) - 50, vr = wx(W) + 50, vb = wy(H) + 50;
        for (BoardObject o : board.objects) {
            if (o.right() < vl || o.left() > vr || o.bottom() < vt || o.top() > vb) continue;
            drawObject(g, o);
        }
        if (tmp != null) drawObject(g, tmp);

        // selection
        g.setStroke(SELECT_COLOR);
        g.setLineWidth(1.5 / zoom());
        g.setLineDashes(6 / zoom(), 4 / zoom());
        for (String id : selected) {
            BoardObject o = board.find(id);
            if (o == null) continue;
            double pad = 4 / zoom() + (o.type == Type.TEXT || o.type == Type.STICKY || o.type == Type.IMAGE
                    || o.type == Type.MAP_PREVIEW ? 0 : o.strokeWidth / 2);
            g.strokeRect(o.left() - pad, o.top() - pad, o.right() - o.left() + 2 * pad, o.bottom() - o.top() + 2 * pad);
        }
        g.setLineDashes((double[]) null);
        BoardObject single = singleResizable();
        if (single != null) {
            double s = 10 / zoom();
            double hx = handleX(single), hy = handleY(single);
            g.setFill(Color.WHITE);
            g.fillRect(hx - s / 2, hy - s / 2, s, s);
            g.strokeRect(hx - s / 2, hy - s / 2, s, s);
        }
        if (mode == Mode.MARQUEE) {
            g.setFill(Color.web("#2563eb33"));
            g.setStroke(SELECT_COLOR);
            g.setLineWidth(1 / zoom());
            double l = Math.min(mqX0, mqX1), t = Math.min(mqY0, mqY1);
            g.fillRect(l, t, Math.abs(mqX1 - mqX0), Math.abs(mqY1 - mqY0));
            g.strokeRect(l, t, Math.abs(mqX1 - mqX0), Math.abs(mqY1 - mqY0));
        }
    }

    private void drawGrid(GraphicsContext g, double W, double H) {
        double step = 40 * zoom();
        while (step < 14) step *= 2;
        double startX = ((board.offsetX % step) + step) % step;
        double startY = ((board.offsetY % step) + step) % step;
        g.setFill(Theme.isDark() ? Color.web("#34373e") : Color.web("#d6d3c8"));
        for (double x = startX; x < W; x += step) {
            for (double y = startY; y < H; y += step) {
                g.fillRect(x - 1, y - 1, 2, 2);
            }
        }
    }

    private void drawObject(GraphicsContext g, BoardObject o) {
        switch (o.type) {
            case TEXT -> {
                if (o != editing) {
                    g.setFill(Theme.ink(col(o.color)));
                    g.setTextBaseline(VPos.TOP);
                    g.setTextAlign(TextAlignment.LEFT);
                    drawLines(g, textLines(o), o, o.x, o.y, Double.MAX_VALUE);
                }
                if (o.link != null) {
                    g.setStroke(col(linkOk(o) ? "#2563eb" : "#dc2626"));
                    g.setLineWidth(1.5);
                    g.strokeLine(o.x, o.y + o.h, o.x + o.w, o.y + o.h);
                }
                if (o.hasNote()) {
                    g.setStroke(noteColor());
                    g.setLineWidth(1.5);
                    g.setLineDashes(4, 3);
                    double yy = o.y + o.h + (o.link != null ? 3 : 0);
                    g.strokeLine(o.x, yy, o.x + o.w, yy);
                    g.setLineDashes((double[]) null);
                }
            }
            case STICKY -> {
                g.setFill(Color.web("#00000026"));
                g.fillRoundRect(o.x + 3, o.y + 4, o.w, o.h, 6, 6);
                g.setFill(col(o.fill != null ? o.fill : DEFAULT_STICKY));
                g.fillRoundRect(o.x, o.y, o.w, o.h, 6, 6);
                if (o != editing) {
                    g.setFill(col(o.color));
                    g.setTextBaseline(VPos.TOP);
                    g.setTextAlign(TextAlignment.LEFT);
                    drawLines(g, wrappedLines(o), o, o.x + 10, o.y + 10, o.y + o.h);
                }
            }
            case IMAGE -> {
                Image img = image(o);
                if (img != null && img.getWidth() > 0) {
                    g.drawImage(img, o.x, o.y, o.w, o.h);
                } else {
                    g.setFill(Color.web("#e5e7eb"));
                    g.fillRect(o.x, o.y, o.w, o.h);
                    g.setStroke(Color.GRAY);
                    g.setLineWidth(1);
                    g.strokeRect(o.x, o.y, o.w, o.h);
                    g.setFill(Color.GRAY);
                    g.setFont(Font.font(14));
                    g.setTextBaseline(VPos.TOP);
                    g.fillText("(missing image)", o.x + 6, o.y + 6);
                }
            }
            case MAP_PREVIEW -> drawMapPreview(g, o);
            case RECT -> {
                if (o.fill != null) {
                    g.setFill(col(o.fill));
                    g.fillRect(o.x, o.y, o.w, o.h);
                }
                g.setStroke(Theme.ink(col(o.color)));
                g.setLineWidth(o.strokeWidth);
                g.strokeRect(o.x, o.y, o.w, o.h);
            }
            case ELLIPSE -> {
                if (o.fill != null) {
                    g.setFill(col(o.fill));
                    g.fillOval(o.x, o.y, o.w, o.h);
                }
                g.setStroke(Theme.ink(col(o.color)));
                g.setLineWidth(o.strokeWidth);
                g.strokeOval(o.x, o.y, o.w, o.h);
            }
            case LINE, ARROW -> {
                g.setStroke(Theme.ink(col(o.color)));
                g.setLineWidth(o.strokeWidth);
                double ex = o.x + o.w, ey = o.y + o.h;
                g.strokeLine(o.x, o.y, ex, ey);
                if (o.type == Type.ARROW && (o.w != 0 || o.h != 0)) {
                    double ang = Math.atan2(o.h, o.w), len = 10 + 3 * o.strokeWidth;
                    g.strokeLine(ex, ey, ex - len * Math.cos(ang - 0.45), ey - len * Math.sin(ang - 0.45));
                    g.strokeLine(ex, ey, ex - len * Math.cos(ang + 0.45), ey - len * Math.sin(ang + 0.45));
                }
            }
            case STROKE -> {
                int n = o.points.size() / 2;
                if (n > 0) {
                    double[] xs = new double[n], ys = new double[n];
                    for (int i = 0; i < n; i++) {
                        xs[i] = o.points.get(2 * i);
                        ys[i] = o.points.get(2 * i + 1);
                    }
                    g.setStroke(Theme.ink(col(o.color)));
                    g.setLineWidth(o.strokeWidth);
                    g.strokePolyline(xs, ys, n);
                }
            }
        }
        if (o.link != null) drawLinkBadge(g, o);
        if (o.hasNote()) drawNoteBadge(g, o, o.link != null);
    }

    /** A small green "i" badge for an object that carries a note; sits left of the link badge if there is one. */
    private void drawNoteBadge(GraphicsContext g, BoardObject o, boolean besideLink) {
        double z = zoom(), r = 9 / z;
        double cx = o.right() - (besideLink ? 22 / z : 0), cy = o.top();
        g.setFill(noteColor());
        g.fillOval(cx - r, cy - r, 2 * r, 2 * r);
        g.setStroke(Color.WHITE);
        g.setFill(Color.WHITE);
        g.setLineWidth(1.8 / z);
        g.strokeLine(cx, cy - 1 / z, cx, cy + 4 / z);
        g.fillOval(cx - 1.3 / z, cy - 5 / z, 2.6 / z, 2.6 / z);
    }

    private boolean linkOk(BoardObject o) {
        return o.link != null && store.itemExists(o.link);
    }

    private void drawLinkBadge(GraphicsContext g, BoardObject o) {
        double z = zoom(), r = 9 / z;
        double cx = o.right(), cy = o.top();
        boolean ok = linkOk(o);
        g.setFill(col(ok ? "#2563eb" : "#dc2626"));
        g.fillOval(cx - r, cy - r, 2 * r, 2 * r);
        g.setStroke(Color.WHITE);
        g.setLineWidth(1.6 / z);
        if (ok) {
            g.strokeLine(cx - 4 / z, cy, cx + 4 / z, cy);
            g.strokeLine(cx + 1 / z, cy - 3.5 / z, cx + 4 / z, cy);
            g.strokeLine(cx + 1 / z, cy + 3.5 / z, cx + 4 / z, cy);
        } else {
            g.strokeLine(cx - 3 / z, cy - 3 / z, cx + 3 / z, cy + 3 / z);
            g.strokeLine(cx - 3 / z, cy + 3 / z, cx + 3 / z, cy - 3 / z);
        }
    }

    // ================================================================ hit testing

    private BoardObject hit(double px, double py) {
        double tol = 6 / zoom();
        List<BoardObject> l = board.objects;
        for (int i = l.size() - 1; i >= 0; i--) {
            if (hits(l.get(i), px, py, tol)) return l.get(i);
        }
        return null;
    }

    private static boolean hits(BoardObject o, double px, double py, double tol) {
        switch (o.type) {
            case LINE:
            case ARROW:
                return distToSegment(px, py, o.x, o.y, o.x + o.w, o.y + o.h) <= tol + o.strokeWidth / 2;
            case STROKE: {
                List<Double> p = o.points;
                if (p.size() == 2) return Math.hypot(px - p.get(0), py - p.get(1)) <= tol + o.strokeWidth / 2;
                for (int i = 0; i + 3 < p.size(); i += 2) {
                    if (distToSegment(px, py, p.get(i), p.get(i + 1), p.get(i + 2), p.get(i + 3)) <= tol + o.strokeWidth / 2) {
                        return true;
                    }
                }
                return false;
            }
            case RECT: {
                double l = o.left(), t = o.top(), r = o.right(), b = o.bottom();
                boolean outer = px >= l - tol && px <= r + tol && py >= t - tol && py <= b + tol;
                if (!outer) return false;
                if (o.fill != null) return true;
                boolean inner = px > l + tol && px < r - tol && py > t + tol && py < b - tol;
                return !inner;
            }
            case ELLIPSE: {
                double cx = (o.left() + o.right()) / 2, cy = (o.top() + o.bottom()) / 2;
                double a = (o.right() - o.left()) / 2, b = (o.bottom() - o.top()) / 2;
                double outer = sq((px - cx) / (a + tol)) + sq((py - cy) / (b + tol));
                if (outer > 1) return false;
                if (o.fill != null || a - tol <= 0 || b - tol <= 0) return true;
                return sq((px - cx) / (a - tol)) + sq((py - cy) / (b - tol)) >= 1;
            }
            default:
                return px >= o.left() - tol / 2 && px <= o.right() + tol / 2
                        && py >= o.top() - tol / 2 && py <= o.bottom() + tol / 2;
        }
    }

    private static double sq(double v) { return v * v; }

    private static double distToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / len2));
        return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }

    // ---- resize handle

    private BoardObject singleResizable() {
        if (selected.size() != 1 || editor != null) return null;
        BoardObject o = board.find(selected.iterator().next());
        return o == null || o.type == Type.TEXT ? null : o;
    }

    private double handleX(BoardObject o) { return o.lineLike() ? o.x + o.w : o.right(); }

    private double handleY(BoardObject o) { return o.lineLike() ? o.y + o.h : o.bottom(); }

    private boolean onHandle(BoardObject o, double px, double py) {
        double s = 7 / zoom();
        return Math.abs(px - handleX(o)) <= s && Math.abs(py - handleY(o)) <= s;
    }

    // ================================================================ undo / redo

    private void checkpoint() {
        undoStack.addLast(Json.write(board.objects));
        if (undoStack.size() > 200) undoStack.removeFirst();
        redoStack.clear();
    }

    /** Takes a snapshot the first time a gesture is about to change something. */
    private void gestureCheckpoint() {
        if (!gestureCheckpointed) {
            checkpoint();
            gestureCheckpointed = true;
        }
    }

    public void undo() {
        commitEdit();
        if (undoStack.isEmpty()) return;
        redoStack.addLast(Json.write(board.objects));
        restore(undoStack.removeLast());
    }

    public void redo() {
        commitEdit();
        if (redoStack.isEmpty()) return;
        undoStack.addLast(Json.write(board.objects));
        restore(redoStack.removeLast());
    }

    private void restore(String snapshot) {
        board.objects.clear();
        board.objects.addAll(Json.readObjects(snapshot));
        selected.removeIf(id -> board.find(id) == null);
        changed();
    }

    private void changed() {
        store.markDirty(board);
        redraw();
    }

    // ================================================================ mouse

    private boolean insideEditor(MouseEvent e) {
        if (editor == null) return false;
        Object t = e.getTarget();
        Node n = t instanceof Node ? (Node) t : null;
        while (n != null) {
            if (n == editor) return true;
            n = n.getParent();
        }
        return false;
    }

    private void onPressed(MouseEvent e) {
        clearHover();
        if (insideEditor(e)) return;
        menu.hide();
        requestFocus();
        commitEdit();

        lastSX = pressSX = e.getX();
        lastSY = pressSY = e.getY();
        pressWX = wx(pressSX);
        pressWY = wy(pressSY);
        gestureCheckpointed = false;
        dragStarted = false;
        mode = Mode.NONE;

        if (e.getButton() == MouseButton.SECONDARY) return;
        Tool tool = ts.tool.get();
        if (e.getButton() == MouseButton.MIDDLE || (e.getButton() == MouseButton.PRIMARY && spaceDown)) {
            mode = Mode.PAN;
            setCursor(Cursor.CLOSED_HAND);
            return;
        }
        if (e.getButton() != MouseButton.PRIMARY) return;

        if (e.getClickCount() == 2 && (tool == Tool.SELECT || tool == Tool.HAND || tool == Tool.TEXT || tool == Tool.STICKY)) {
            doubleClick(pressWX, pressWY);
            return;
        }

        switch (tool) {
            case HAND -> {
                mode = Mode.PAN;
                setCursor(Cursor.CLOSED_HAND);
            }
            case SELECT -> {
                BoardObject single = singleResizable();
                if (single != null && onHandle(single, pressWX, pressWY)) {
                    mode = Mode.RESIZE;
                    resizeRatio = single.w == 0 ? 1 : single.h / single.w;
                    break;
                }
                BoardObject o = hit(pressWX, pressWY);
                if (o != null) {
                    if (e.isShiftDown()) {
                        if (!selected.remove(o.id)) selected.add(o.id);
                    } else if (!selected.contains(o.id)) {
                        selected.clear();
                        selected.add(o.id);
                    }
                    mode = Mode.MOVE;
                } else {
                    if (!e.isShiftDown()) selected.clear();
                    mode = Mode.MARQUEE;
                    mqX0 = mqX1 = pressWX;
                    mqY0 = mqY1 = pressWY;
                }
            }
            case TEXT -> {
                createText(pressWX, pressWY, "");
                ts.tool.set(Tool.SELECT);
            }
            case STICKY -> {
                createSticky(pressWX, pressWY);
                ts.tool.set(Tool.SELECT);
            }
            case PEN -> {
                tmp = new BoardObject(Type.STROKE);
                tmp.color = hex(ts.color.get());
                tmp.strokeWidth = ts.width.get();
                tmp.points.add(pressWX);
                tmp.points.add(pressWY);
                tmp.refreshStrokeBounds();
                mode = Mode.DRAW;
            }
            case ERASER -> {
                mode = Mode.ERASE;
                eraseAt(pressWX, pressWY);
            }
            case RECT, ELLIPSE, LINE, ARROW -> {
                Type type = Type.valueOf(tool.name());
                tmp = new BoardObject(type);
                tmp.x = pressWX;
                tmp.y = pressWY;
                tmp.color = hex(ts.color.get());
                tmp.strokeWidth = ts.width.get();
                if ((type == Type.RECT || type == Type.ELLIPSE) && ts.useFill.get()) tmp.fill = hex(ts.fill.get());
                mode = Mode.SHAPE;
            }
        }
        redraw();
    }

    private void onDragged(MouseEvent e) {
        if (insideEditor(e) || mode == Mode.NONE) return;
        double sx = e.getX(), sy = e.getY();
        double px = wx(sx), py = wy(sy);
        if (!dragStarted && Math.hypot(sx - pressSX, sy - pressSY) < 3) return;
        dragStarted = true;

        switch (mode) {
            case PAN -> {
                board.offsetX += sx - lastSX;
                board.offsetY += sy - lastSY;
            }
            case MOVE -> {
                gestureCheckpoint();
                double dx = (sx - lastSX) / zoom(), dy = (sy - lastSY) / zoom();
                for (String id : selected) {
                    BoardObject o = board.find(id);
                    if (o != null) o.move(dx, dy);
                }
                store.markDirty(board);
            }
            case MARQUEE -> {
                mqX1 = px;
                mqY1 = py;
                double l = Math.min(mqX0, mqX1), r = Math.max(mqX0, mqX1);
                double t = Math.min(mqY0, mqY1), b = Math.max(mqY0, mqY1);
                selected.clear();
                for (BoardObject o : board.objects) {
                    if (o.right() >= l && o.left() <= r && o.bottom() >= t && o.top() <= b) selected.add(o.id);
                }
            }
            case RESIZE -> resizeSelected(px, py);
            case DRAW -> {
                int n = tmp.points.size();
                double lx = tmp.points.get(n - 2), ly = tmp.points.get(n - 1);
                if (Math.hypot(px - lx, py - ly) > 1.5 / zoom()) {
                    tmp.points.add(px);
                    tmp.points.add(py);
                    tmp.refreshStrokeBounds();
                }
            }
            case SHAPE -> {
                tmp.w = px - tmp.x;
                tmp.h = py - tmp.y;
            }
            case ERASE -> eraseAt(px, py);
            default -> { }
        }
        lastSX = sx;
        lastSY = sy;
        redraw();
    }

    private void onReleased(MouseEvent e) {
        if (insideEditor(e)) return;
        switch (mode) {
            case PAN -> {
                store.markDirty(board);
                refreshCursor();
            }
            case DRAW -> {
                if (tmp != null) {
                    if (tmp.points.size() == 2) {
                        tmp.points.add(tmp.points.get(0));
                        tmp.points.add(tmp.points.get(1));
                    }
                    tmp.refreshStrokeBounds();
                    checkpoint();
                    board.objects.add(tmp);
                    store.markDirty(board);
                }
            }
            case SHAPE -> {
                if (tmp != null && (Math.abs(tmp.w) >= 4 || Math.abs(tmp.h) >= 4)) {
                    if (!tmp.lineLike()) {
                        if (tmp.w < 0) { tmp.x += tmp.w; tmp.w = -tmp.w; }
                        if (tmp.h < 0) { tmp.y += tmp.h; tmp.h = -tmp.h; }
                    }
                    checkpoint();
                    board.objects.add(tmp);
                    selected.clear();
                    selected.add(tmp.id);
                    store.markDirty(board);
                    ts.tool.set(Tool.SELECT);
                }
            }
            default -> { }
        }
        tmp = null;
        mode = Mode.NONE;
        gestureCheckpointed = false;
        redraw();
    }

    private void resizeSelected(double px, double py) {
        BoardObject o = singleResizable();
        if (o == null) return;
        gestureCheckpoint();
        if (o.lineLike()) {
            o.w = px - o.x;
            o.h = py - o.y;
        } else if (o.type == Type.STROKE) {
            double nw = Math.max(10, px - o.x), nh = Math.max(10, py - o.y);
            double sx = o.w > 0 ? nw / o.w : 1, sy = o.h > 0 ? nh / o.h : 1;
            for (int i = 0; i + 1 < o.points.size(); i += 2) {
                o.points.set(i, o.x + (o.points.get(i) - o.x) * sx);
                o.points.set(i + 1, o.y + (o.points.get(i + 1) - o.y) * sy);
            }
            o.w = nw;
            o.h = nh;
        } else if (o.type == Type.IMAGE || o.type == Type.MAP_PREVIEW) {
            o.w = Math.max(20, px - o.x);
            o.h = o.w * resizeRatio;
        } else {
            o.w = Math.max(10, px - o.x);
            o.h = Math.max(10, py - o.y);
        }
        store.markDirty(board);
    }

    private void eraseAt(double px, double py) {
        // The eraser only removes freehand drawings, never text, stickies, images or shapes.
        double tol = 6 / zoom();
        List<BoardObject> l = board.objects;
        for (int i = l.size() - 1; i >= 0; i--) {
            BoardObject o = l.get(i);
            if (o.type == Type.STROKE && hits(o, px, py, tol)) {
                gestureCheckpoint();
                l.remove(i);
                selected.remove(o.id);
                store.markDirty(board);
                return;
            }
        }
    }

    private void onScroll(ScrollEvent e) {
        clearHover();
        if (e.getDeltaY() == 0) return;
        double factor = Math.exp(e.getDeltaY() * 0.0015);
        double nz = Math.max(0.1, Math.min(5, board.zoom * factor));
        double sx = e.getX(), sy = e.getY();
        double px = wx(sx), py = wy(sy);
        board.zoom = nz;
        board.offsetX = sx - px * nz;
        board.offsetY = sy - py * nz;
        store.markDirty(board);
        if (editor != null) commitEdit();
        redraw();
        e.consume();
    }

    private void doubleClick(double px, double py) {
        BoardObject o = hit(px, py);
        if (o == null) {
            createText(px, py, "");
            return;
        }
        selected.clear();
        selected.add(o.id);
        SpanHit span = spanAt(px, py);
        if (span != null) {
            dnd.model.SpanRef sr = span.o().ref(span.seg().ref());
            if (sr != null && sr.link != null) {
                if (store.itemExists(sr.link)) host.openItem(sr.link);
                else new Alert(Alert.AlertType.INFORMATION, "The item these words linked to no longer exists. Select the words while editing and press Link... to pick a new target.").show();
                redraw();
                return;
            }
            if (sr != null && sr.hasNote()) { // words that only carry a note: double-click edits it
                editSpanNote(span);
                redraw();
                return;
            }
        }
        if (o.link == null && o.hasNote() && o.type != Type.TEXT && o.type != Type.STICKY) {
            noteSelected();
        } else if (o.link != null) {
            if (store.itemExists(o.link)) {
                host.openItem(o.link);
            } else {
                Alert a = new Alert(Alert.AlertType.INFORMATION,
                        "The whiteboard this object linked to no longer exists. Pick a new target?",
                        javafx.scene.control.ButtonType.YES, javafx.scene.control.ButtonType.NO);
                a.setHeaderText("Broken link");
                Optional<javafx.scene.control.ButtonType> r = a.showAndWait();
                if (r.isPresent() && r.get() == javafx.scene.control.ButtonType.YES) linkSelected();
            }
        } else if (o.type == Type.TEXT || o.type == Type.STICKY) {
            startEdit(o, false);
        }
        redraw();
    }

    // ================================================================ keyboard & menu

    private void onKeyPressed(KeyEvent e) {
        if (editor != null) return;
        KeyCode k = e.getCode();
        if (k == KeyCode.SPACE) {
            spaceDown = true;
            refreshCursor();
            e.consume();
            return;
        }
        if (e.isShortcutDown()) {
            switch (k) {
                case Z -> { if (e.isShiftDown()) redo(); else undo(); }
                case Y -> redo();
                case C -> copy();
                case X -> { copy(); deleteSelected(); }
                case V -> paste(null, null);
                case D -> { copy(); paste(null, null); }
                case A -> { selected.clear(); for (BoardObject o : board.objects) selected.add(o.id); redraw(); }
                case L -> linkSelected();
                case DIGIT0 -> { board.zoom = 1; store.markDirty(board); redraw(); }
                default -> { return; }
            }
            e.consume();
            return;
        }
        switch (k) {
            case DELETE, BACK_SPACE -> deleteSelected();
            case ESCAPE -> { selected.clear(); ts.tool.set(Tool.SELECT); redraw(); }
            case ENTER, F2 -> {
                BoardObject o = selected.size() == 1 ? board.find(selected.iterator().next()) : null;
                if (o != null && (o.type == Type.TEXT || o.type == Type.STICKY)) startEdit(o, false);
            }
            default -> { return; }
        }
        e.consume();
    }

    private void onContextMenu(ContextMenuEvent e) {
        if (editor != null) return;
        Point p = new Point(e.getX(), e.getY());
        ctxWX = wx(p.x);
        ctxWY = wy(p.y);
        BoardObject o = hit(ctxWX, ctxWY);
        if (o != null && !selected.contains(o.id)) {
            selected.clear();
            selected.add(o.id);
        } else if (o == null) {
            selected.clear();
        }
        redraw();

        boolean has = !selected.isEmpty();
        BoardObject single = selected.size() == 1 ? board.find(selected.iterator().next()) : null;
        boolean anyLink = false;
        for (String id : selected) {
            BoardObject s = board.find(id);
            if (s != null && s.link != null) anyLink = true;
        }
        menu.getItems().clear();
        // words under the pointer that carry a link or a note
        SpanHit words = spanAt(ctxWX, ctxWY);
        dnd.model.SpanRef wordsRef = words == null ? null : words.o().ref(words.seg().ref());
        if (wordsRef != null) {
            final SpanHit w = words;
            final dnd.model.SpanRef wr = wordsRef;
            menu.getItems().add(item(wr.hasNote() ? "Edit note on these words..." : "Add note to these words...", false, () -> editSpanNote(w)));
            if (wr.hasNote()) menu.getItems().add(item("Remove note from these words", false, () -> removeSpanNote(w)));
            if (wr.link != null) menu.getItems().add(item("Open link", !store.itemExists(wr.link), () -> host.openItem(wr.link)));
            menu.getItems().add(new SeparatorMenuItem());
        }
        if (single != null && (single.type == Type.TEXT || single.type == Type.STICKY)) {
            menu.getItems().add(item("Edit text", false, () -> startEdit(single, false)));
        }
        boolean anyNote = false;
        for (String id : selected) {
            BoardObject s = board.find(id);
            if (s != null && s.hasNote()) anyNote = true;
        }
        menu.getItems().add(item("Link to...", !has, this::linkSelected));
        menu.getItems().add(item("Remove link", !anyLink, this::unlinkSelected));
        menu.getItems().add(item(anyNote ? "Edit note..." : "Note...", !has, this::noteSelected));
        menu.getItems().add(item("Remove note", !anyNote, this::removeNoteSelected));
        menu.getItems().add(new SeparatorMenuItem());
        menu.getItems().add(item("Copy", !has, this::copy));
        menu.getItems().add(item("Cut", !has, () -> { copy(); deleteSelected(); }));
        menu.getItems().add(item("Paste here", false, () -> paste(ctxWX, ctxWY)));
        menu.getItems().add(item("Duplicate", !has, () -> { copy(); paste(null, null); }));
        menu.getItems().add(item("Delete", !has, this::deleteSelected));
        menu.getItems().add(new SeparatorMenuItem());
        menu.getItems().add(item("Bring to front", !has, () -> reorder(true)));
        menu.getItems().add(item("Send to back", !has, () -> reorder(false)));
        menu.getItems().add(new SeparatorMenuItem());
        menu.getItems().add(item("Insert battlemap preview...", false, this::insertMapPreview));
        menu.getItems().add(item("Add text here", false, () -> createText(ctxWX, ctxWY, "")));
        menu.getItems().add(item("Add sticky note here", false, () -> createSticky(ctxWX, ctxWY)));
        menu.show(this, e.getScreenX(), e.getScreenY());
        e.consume();
    }

    private record Point(double x, double y) { }

    private static MenuItem item(String text, boolean disabled, Runnable r) {
        MenuItem m = new MenuItem(text);
        m.setDisable(disabled);
        m.setOnAction(e -> r.run());
        return m;
    }

    // ================================================================ editing operations

    private BoardObject createText(double px, double py, String text) {
        BoardObject t = new BoardObject(Type.TEXT);
        t.x = px;
        t.y = py;
        t.text = text;
        t.color = hex(ts.color.get());
        t.fontSize = ts.fontSize.get();
        t.bold = ts.bold.get();
        t.italic = ts.italic.get();
        measure(t);
        checkpoint();
        board.objects.add(t);
        selected.clear();
        selected.add(t.id);
        startEdit(t, true);
        return t;
    }

    /** Drops a finished (non-editing) text object at the centre of the current view. Undoable. */
    public void addTextAtCenter(String text) {
        if (text == null || text.isBlank()) return;
        commitEdit();
        BoardObject t = createText(viewCenterX(), viewCenterY(), text);
        commitEdit(); // closes the editor straight away; the text stays because it is non-blank
        selected.clear();
        selected.add(t.id);
        redraw();
    }

    private void createSticky(double px, double py) {
        BoardObject s = new BoardObject(Type.STICKY);
        s.x = px - 20;
        s.y = py - 20;
        s.w = 220;
        s.h = 170;
        s.fill = hex(ts.fill.get());
        s.color = hex(ts.color.get());
        s.fontSize = Math.min(ts.fontSize.get(), 28);
        s.bold = ts.bold.get();
        s.italic = ts.italic.get();
        checkpoint();
        board.objects.add(s);
        selected.clear();
        selected.add(s.id);
        startEdit(s, true);
    }

    private void startEdit(BoardObject o, boolean isNew) {
        commitEdit();
        editing = o;
        editingNew = isNew;
        TextArea ta = new TextArea(o.text);
        editor = ta;
        double z = zoom();
        ta.setFont(Font.font("System", o.bold ? FontWeight.BOLD : FontWeight.NORMAL,
                o.italic ? FontPosture.ITALIC : FontPosture.REGULAR, Math.max(6, o.fontSize * z)));
        boolean sticky = o.type == Type.STICKY;
        String bg = sticky ? cssRgb(col(o.fill != null ? o.fill : DEFAULT_STICKY)) : Theme.isDark() ? "#1f2227" : "white";
        ta.setStyle("-fx-control-inner-background: " + bg + "; -fx-text-fill: " + cssRgb(sticky ? col(o.color) : Theme.ink(col(o.color))) + ";");
        ta.setWrapText(o.type == Type.STICKY);
        ta.setLayoutX(board.offsetX + o.x * z - 7);
        ta.setLayoutY(board.offsetY + o.y * z - 5);
        if (o.type == Type.STICKY) {
            ta.setPrefSize(o.w * z + 14, o.h * z + 10);
        } else {
            ta.setPrefSize(Math.max(180, o.w * z + 60), Math.max(70, o.h * z + 30));
        }
        ta.focusedProperty().addListener((obs, was, is) -> {
            if (!is && !pickingTarget) commitEdit(); // a picker dialog takes the focus for a moment: not the end of the edit
        });
        ta.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ESCAPE || (ev.getCode() == KeyCode.ENTER && ev.isShortcutDown())) {
                commitEdit();
                ev.consume();
            } else if (ev.isShortcutDown() && !ev.isAltDown() && !ev.isShiftDown()
                    && (ev.getCode() == KeyCode.B || ev.getCode() == KeyCode.I)) {
                styleSelectedWords(ev.getCode() == KeyCode.B);
                ev.consume();
            } else if (ev.isShortcutDown() && !ev.isAltDown() && !ev.isShiftDown() && ev.getCode() == KeyCode.L) {
                linkSelected(); // links the selected words
                ev.consume();
            }
        });
        clearHover();
        getChildren().add(ta);
        redraw();
        Platform.runLater(() -> {
            ta.requestFocus();
            ta.positionCaret(ta.getText().length());
        });
    }

    /** True while a text or sticky note is being typed into. */
    public boolean isEditingText() {
        return editor != null;
    }

    /**
     * Makes the words selected in the text being edited bold (or italic), or plain again if they all already are.
     * Returns false if no text is being edited or nothing is selected.
     */
    public boolean styleSelectedWords(boolean bold) {
        if (editor == null) return false;
        javafx.scene.control.IndexRange sel = editor.getSelection();
        if (sel.getLength() == 0) return false;
        dnd.text.RichText.Edit e = dnd.text.RichText.toggle(editor.getText(), sel.getStart(), sel.getEnd(), bold);
        editor.setText(e.markup());
        editor.selectRange(e.selStart(), e.selEnd());
        editor.requestFocus();
        return true;
    }

    private static String cssRgb(Color c) {
        return String.format("rgb(%d,%d,%d)", (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
    }

    private void commitEdit() {
        if (editor == null) return;
        TextArea ta = editor;
        BoardObject o = editing;
        boolean isNew = editingNew;
        editor = null;
        editing = null;
        getChildren().remove(ta);

        String nt = ta.getText();
        if (nt.isBlank()) {
            if (isNew) {
                undoStack.pollLast(); // discard the snapshot taken when the empty object was created
            } else {
                checkpoint();
            }
            board.objects.remove(o);
            selected.remove(o.id);
        } else {
            String cleaned = cleanSpans(o, nt);
            if (!cleaned.equals(o.text)) {
                if (!isNew) checkpoint();
                o.text = cleaned;
                measure(o);
            }
            pruneRefs(o);
        }
        changed();
        requestFocus();
    }

    /** Takes the span markers out of words whose reference no longer links or previews anything. */
    private static String cleanSpans(BoardObject o, String text) {
        String out = text;
        for (dnd.text.RichText.Span s : dnd.text.RichText.spans(text)) {
            dnd.model.SpanRef sr = o.ref(s.ref());
            if (sr == null || sr.isEmpty()) out = dnd.text.RichText.setSpanRef(out, s.plainStart(), s.plainEnd(), 0).markup();
        }
        return out;
    }

    /** Forgets reference table entries that no span uses any more. */
    private static void pruneRefs(BoardObject o) {
        java.util.Set<Integer> used = dnd.text.RichText.refsUsed(o.text);
        o.refs.entrySet().removeIf(e -> {
            try {
                return !used.contains(Integer.parseInt(e.getKey())) || e.getValue().isEmpty();
            } catch (NumberFormatException ex) {
                return true;
            }
        });
    }

    public void deleteSelected() {
        if (selected.isEmpty()) return;
        checkpoint();
        board.objects.removeIf(o -> selected.contains(o.id));
        selected.clear();
        changed();
    }

    private void reorder(boolean toFront) {
        if (selected.isEmpty()) return;
        checkpoint();
        List<BoardObject> picked = new ArrayList<>();
        for (BoardObject o : board.objects) if (selected.contains(o.id)) picked.add(o);
        board.objects.removeAll(picked);
        if (toFront) board.objects.addAll(picked);
        else board.objects.addAll(0, picked);
        changed();
    }

    // ---- clipboard

    private void copy() {
        List<BoardObject> picked = new ArrayList<>();
        for (BoardObject o : board.objects) if (selected.contains(o.id)) picked.add(o);
        if (picked.isEmpty()) return;
        clipboardJson = Json.write(picked);
        preferInternal = true;
        pasteCount = 0;
    }

    private void paste(Double atX, Double atY) {
        Clipboard cb = Clipboard.getSystemClipboard();
        if (!preferInternal || clipboardJson == null) {
            if (cb.hasImage()) {
                pasteSystemImage(cb.getImage(), atX, atY);
                return;
            }
            if (clipboardJson == null) {
                if (cb.hasString() && !cb.getString().isBlank()) {
                    // text from elsewhere is shown exactly as it was written, asterisks and all
                    createText(atX != null ? atX : viewCenterX(), atY != null ? atY : viewCenterY(),
                            dnd.text.RichText.escape(cb.getString()));
                    commitEdit();
                }
                return;
            }
        }
        List<BoardObject> list = Json.readObjects(clipboardJson);
        if (list.isEmpty()) return;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        for (BoardObject o : list) {
            minX = Math.min(minX, o.left());
            minY = Math.min(minY, o.top());
        }
        pasteCount++;
        double dx = atX != null ? atX - minX : 24.0 * pasteCount;
        double dy = atY != null ? atY - minY : 24.0 * pasteCount;
        checkpoint();
        selected.clear();
        for (BoardObject o : list) {
            o.id = UUID.randomUUID().toString();
            o.move(dx, dy);
            board.objects.add(o);
            selected.add(o.id);
        }
        changed();
    }

    private void pasteSystemImage(Image img, Double atX, Double atY) {
        try {
            Path tmpFile = Files.createTempFile("dndpaste", ".png");
            ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", tmpFile.toFile());
            String name = store.importImage(tmpFile);
            Files.deleteIfExists(tmpFile);
            addImage(name, atX != null ? atX : viewCenterX(), atY != null ? atY : viewCenterY());
        } catch (IOException ex) {
            error("Could not paste image: " + ex.getMessage());
        }
    }

    // ---- battlemap previews

    private void drawMapPreview(GraphicsContext g, BoardObject o) {
        dnd.model.BattleMap m = store.isBattleMap(o.link) ? store.battleMap(o.link) : null;
        if (m == null) {
            g.setFill(Color.web("#fde8e8"));
            g.fillRect(o.x, o.y, o.w, o.h);
            g.setStroke(Color.web("#dc2626"));
            g.setLineWidth(2);
            g.strokeRect(o.x, o.y, o.w, o.h);
            g.setFill(Color.web("#dc2626"));
            g.setFont(Font.font(16));
            g.setTextBaseline(VPos.CENTER);
            g.setTextAlign(TextAlignment.CENTER);
            g.fillText("(battlemap was deleted)", o.x + o.w / 2, o.y + o.h / 2);
            return;
        }
        Image img = dnd.map.MapRenderer.thumbnail(m, store.assets(), o.w * zoom());
        g.drawImage(img, o.x, o.y, o.w, o.h);
        g.setStroke(Color.web("#3a2e28"));
        g.setLineWidth(2);
        g.strokeRect(o.x, o.y, o.w, o.h);
        // caption strip
        double bar = Math.min(26, o.h * 0.25);
        g.setFill(Color.web("#000000a0"));
        g.fillRect(o.x, o.y + o.h - bar, o.w, bar);
        g.setFill(Color.WHITE);
        g.setFont(Font.font("System", FontWeight.BOLD, Math.max(9, bar * 0.55)));
        g.setTextBaseline(VPos.CENTER);
        g.setTextAlign(TextAlignment.LEFT);
        g.fillText(m.name + "  (" + (m.gridType == dnd.model.BattleMap.GridType.HEX ? "hex" : "square") + " battlemap)",
                o.x + 8, o.y + o.h - bar / 2);
    }

    /** Inserts a live preview of a battlemap at the centre of the view; double-clicking it opens the map. */
    public void insertMapPreview() {
        if (store.allMapIds().isEmpty()) {
            Alert a = new Alert(Alert.AlertType.INFORMATION,
                    "There are no battlemaps in this project yet. Create one with + Battlemap in the project tree.");
            a.setHeaderText(null);
            a.showAndWait();
            return;
        }
        host.pickMap("Insert battlemap preview").ifPresent(mapId -> {
            dnd.model.BattleMap m = store.battleMap(mapId);
            if (m == null) return;
            BoardObject o = new BoardObject(Type.MAP_PREVIEW);
            o.link = mapId;
            o.w = 360;
            o.h = o.w / dnd.map.MapRenderer.aspect(m);
            o.x = viewCenterX() - o.w / 2;
            o.y = viewCenterY() - o.h / 2;
            checkpoint();
            board.objects.add(o);
            selected.clear();
            selected.add(o.id);
            changed();
        });
    }

    // ---- images

    public void addImageFromChooser() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Choose an image");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        File f = fc.showOpenDialog(getScene().getWindow());
        if (f != null) importImageFile(f, viewCenterX(), viewCenterY());
    }

    private void importImageFile(File f, double px, double py) {
        try {
            addImage(store.importImage(f.toPath()), px, py);
        } catch (IOException ex) {
            error("Could not import image: " + ex.getMessage());
        }
    }

    private void addImage(String name, double cx, double cy) {
        BoardObject o = new BoardObject(Type.IMAGE);
        o.image = name;
        Image img = image(o);
        double iw = img != null && img.getWidth() > 0 ? img.getWidth() : 300;
        double ih = img != null && img.getHeight() > 0 ? img.getHeight() : 200;
        double scale = Math.min(1, 480 / Math.max(iw, ih));
        o.w = iw * scale;
        o.h = ih * scale;
        o.x = cx - o.w / 2;
        o.y = cy - o.h / 2;
        checkpoint();
        board.objects.add(o);
        selected.clear();
        selected.add(o.id);
        changed();
    }

    private void onFilesDropped(DragEvent e) {
        boolean ok = false;
        if (e.getDragboard().hasFiles()) {
            double px = wx(e.getX()), py = wy(e.getY());
            for (File f : e.getDragboard().getFiles()) {
                String n = f.getName().toLowerCase(Locale.ROOT);
                int dot = n.lastIndexOf('.');
                if (dot >= 0 && IMAGE_EXT.contains(n.substring(dot + 1))) {
                    importImageFile(f, px, py);
                    px += 30;
                    py += 30;
                    ok = true;
                }
            }
        }
        e.setDropCompleted(ok);
        e.consume();
    }

    // ================================================================ links & styling

    /** The words selected in the text being typed, as plain-text positions, or null if there are none. */
    private int[] selectedWords() {
        if (editor == null) return null;
        javafx.scene.control.IndexRange sel = editor.getSelection();
        if (sel.getLength() == 0) return null;
        int[] r = dnd.text.RichText.toPlainRange(editor.getText(), sel.getStart(), sel.getEnd());
        return r[0] < r[1] ? r : null;
    }

    private Optional<String> pickTarget(String title) {
        pickingTarget = true;
        try {
            return host.pickBoard(title);
        } finally {
            pickingTarget = false;
        }
    }

    /** Links the selected words (while typing) or the selected objects to another item. */
    public void linkSelected() {
        if (editor != null) {
            int[] words = selectedWords();
            if (words == null) {
                info("Select the words first, then press Link...");
                return;
            }
            Optional<String> target = pickTarget("Link to...");
            if (target.isEmpty() || editor == null) {
                if (editor != null) editor.requestFocus();
                return;
            }
            setWordsSpan(words, sr -> sr.link = target.get());
            return;
        }
        if (selected.isEmpty()) {
            Alert a = new Alert(Alert.AlertType.INFORMATION, "Select an object first, then link it."
                    + " To link just some words, double-click the text and select them.");
            a.setHeaderText(null);
            a.showAndWait();
            return;
        }
        pickTarget("Link to...").ifPresent(target -> {
            checkpoint();
            for (String id : selected) {
                BoardObject o = board.find(id);
                if (o != null) o.link = target;
            }
            changed();
        });
    }

    /** Writes or edits a note for the selected words (while typing) or the selected objects. */
    public void noteSelected() {
        if (editor != null) {
            int[] words = selectedWords();
            if (words == null) {
                info("Select the words first, then press Note...");
                return;
            }
            String text = editor.getText();
            int ref = wordsRef(text, words);
            dnd.model.SpanRef existing = ref == 0 ? null : editing.ref(ref);
            String plain = dnd.text.RichText.plain(text);
            Optional<String> note = askNote("Note for \"" + shorten(plain.substring(words[0], Math.min(words[1], plain.length()))) + "\"",
                    existing == null ? null : existing.note);
            if (note.isEmpty() || editor == null) {
                if (editor != null) editor.requestFocus();
                return;
            }
            if (note.get().isBlank()) {
                removeNoteSelected(); // "Remove note" in the box
                return;
            }
            setWordsSpan(words, sr -> sr.note = note.get());
            return;
        }
        if (selected.isEmpty()) {
            Alert a = new Alert(Alert.AlertType.INFORMATION, "Select an object first, then add a note to it."
                    + " To add a note to just some words, double-click the text and select them.");
            a.setHeaderText(null);
            a.showAndWait();
            return;
        }
        BoardObject first = board.find(selected.iterator().next());
        askNote(selected.size() == 1 ? "Note for this object" : "Note for the " + selected.size() + " selected objects",
                first == null ? null : first.note).ifPresent(note -> {
            checkpoint();
            for (String id : selected) {
                BoardObject o = board.find(id);
                if (o != null) o.note = note.isBlank() ? null : note;
            }
            changed();
        });
    }

    private Optional<String> askNote(String title, String current) {
        pickingTarget = true; // the dialog takes the focus for a moment: that must not end the edit
        try {
            return host.editNote(title, current);
        } finally {
            pickingTarget = false;
        }
    }

    private void info(String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, message);
        a.setHeaderText(null);
        pickingTarget = true;
        try {
            a.showAndWait();
        } finally {
            pickingTarget = false;
        }
        if (editor != null) editor.requestFocus();
    }

    private static String shorten(String s) {
        String flat = s.replace('\n', ' ').trim();
        return flat.length() > 30 ? flat.substring(0, 27) + "..." : flat;
    }

    /** The reference number when the words are exactly one existing span (so a link and a note share it), else 0. */
    private static int wordsRef(String text, int[] words) {
        int ref = dnd.text.RichText.refAt(text, words[0]);
        for (int k = words[0]; k < words[1] && ref != 0; k++) if (dnd.text.RichText.refAt(text, k) != ref) ref = 0;
        return ref;
    }

    /** Puts a span around the selected words (while typing) and lets {@code apply} fill in its link or note. */
    private void setWordsSpan(int[] words, java.util.function.Consumer<dnd.model.SpanRef> apply) {
        BoardObject o = editing;
        String text = editor.getText();
        checkpoint();
        int ref = wordsRef(text, words);
        if (ref == 0) ref = o.nextRef();
        dnd.model.SpanRef sr = o.refs.computeIfAbsent(String.valueOf(ref), k -> new dnd.model.SpanRef());
        apply.accept(sr);
        dnd.text.RichText.Edit e = dnd.text.RichText.setSpanRef(text, words[0], words[1], ref);
        editor.setText(e.markup());
        editor.selectRange(e.selStart(), e.selEnd());
        editor.requestFocus();
    }

    /** Edits the note on the words under the pointer (not while typing), from the right-click menu or a double-click. */
    private void editSpanNote(SpanHit at) {
        BoardObject o = at.o();
        dnd.model.SpanRef sr = o.ref(at.seg().ref());
        if (sr == null) return;
        String title = "Note for \"" + shorten(spanText(o, at.seg())) + "\"";
        askNote(title, sr.note).ifPresent(note -> {
            checkpoint();
            sr.note = note.isBlank() ? null : note;
            afterSpanChange(o);
        });
    }

    private void removeSpanNote(SpanHit at) {
        BoardObject o = at.o();
        dnd.model.SpanRef sr = o.ref(at.seg().ref());
        if (sr == null || !sr.hasNote()) return;
        checkpoint();
        sr.note = null;
        afterSpanChange(o);
    }

    /** Drops span markers whose words no longer link or carry a note, then saves. */
    private void afterSpanChange(BoardObject o) {
        o.text = cleanSpans(o, o.text);
        pruneRefs(o);
        wrapCache.remove(o.id);
        measure(o);
        changed();
    }

    /** The plain words of the span a laid-out piece belongs to. */
    private static String spanText(BoardObject o, RichLayout.Seg seg) {
        String plain = dnd.text.RichText.plain(o.text);
        for (dnd.text.RichText.Span s : dnd.text.RichText.spans(o.text)) {
            if (s.ref() == seg.ref() && seg.plainStart() >= s.plainStart() && seg.plainStart() < s.plainEnd()) {
                return plain.substring(s.plainStart(), Math.min(s.plainEnd(), plain.length()));
            }
        }
        return seg.text();
    }

    /** Removes the link from the selected words (while typing) or the selected objects. */
    public void unlinkSelected() {
        removeTarget(false);
    }

    /** Removes the note from the selected words (while typing) or the selected objects. */
    public void removeNoteSelected() {
        removeTarget(true);
    }

    private void removeTarget(boolean note) {
        if (editor != null) {
            int[] words = selectedWords();
            if (words == null) return;
            BoardObject o = editing;
            String text = editor.getText();
            String out = text;
            boolean any = false;
            checkpoint();
            for (dnd.text.RichText.Span s : dnd.text.RichText.spans(text)) {
                if (s.plainEnd() <= words[0] || s.plainStart() >= words[1]) continue;
                dnd.model.SpanRef sr = o.ref(s.ref());
                if (sr == null) continue;
                any = true;
                if (note) sr.note = null;
                else sr.link = null;
                if (sr.isEmpty()) out = dnd.text.RichText.setSpanRef(out, s.plainStart(), s.plainEnd(), 0).markup();
            }
            if (any) editor.setText(out);
            editor.requestFocus();
            return;
        }
        boolean any = false;
        for (String id : selected) {
            BoardObject o = board.find(id);
            if (o != null && (note ? o.hasNote() : o.link != null)) any = true;
        }
        if (!any) return;
        checkpoint();
        for (String id : selected) {
            BoardObject o = board.find(id);
            if (o == null) continue;
            if (note) o.note = null;
            else o.link = null;
        }
        changed();
    }

    // ================================================================ hover: tooltips and name hints

    /** The word piece under a point in a text object, with where it sits (map units). */
    private record SpanHit(BoardObject o, RichLayout.Seg seg, double left, double bottom) { }

    private SpanHit spanAt(double px, double py) {
        BoardObject o = hit(px, py);
        if (o == null || (o.type != Type.TEXT && o.type != Type.STICKY) || o == editing) return null;
        List<RichLayout.Line> lines = o.type == Type.TEXT ? textLines(o) : wrappedLines(o);
        double ox = o.type == Type.TEXT ? o.x : o.x + 10, oy = o.type == Type.TEXT ? o.y : o.y + 10;
        double lh = RichLayout.lineHeight(o);
        int li = (int) Math.floor((py - oy) / lh);
        if (li < 0 || li >= lines.size()) return null;
        for (RichLayout.Seg s : lines.get(li).segs()) {
            if (px >= ox + s.x() && px <= ox + s.x() + s.w()) return new SpanHit(o, s, ox + s.x(), oy + (li + 1) * lh);
        }
        return null;
    }

    private void clearHover() {
        hoverDelay.stop();
        hintHide.stop();
        hideTip();
        hintPopup.hide();
        hintHovered = false;
        hoverKey = null;
    }

    private void hideTip() {
        tipPopup.hide();
    }

    private void onHover(MouseEvent e) {
        if (editor != null || mode != Mode.NONE) {
            clearHover();
            return;
        }
        double px = wx(e.getX()), py = wy(e.getY());
        SpanHit sh = spanAt(px, py);
        String key = null, noteText = null, previewId = null;
        dnd.text.NameHints.Hint hint = null;
        boolean linkCursor = false;
        boolean previews = ts.linkPreviews.get();
        if (sh != null) {
            dnd.model.SpanRef sr = sh.o().ref(sh.seg().ref());
            if (sr != null) {
                boolean preview = sr.link != null && previews;
                if (sr.hasNote() || preview) {
                    noteText = sr.hasNote() ? sr.note : null;
                    previewId = preview ? sr.link : null;
                    key = "span|" + sh.o().id + "|" + sh.seg().ref();
                }
                if (sr.link != null) linkCursor = true;
            } else if (sh.seg().hint() != null) {
                for (dnd.text.NameHints.Hint h : hintsFor(sh.o())) {
                    if (sh.seg().plainStart() >= h.start() && sh.seg().plainStart() < h.end()) hint = h;
                }
                if (hint != null) key = "hint|" + sh.o().id + "|" + hint.start();
            }
        }
        if (key == null) {
            BoardObject o = hit(px, py);
            // a map preview already shows its map, so it needs no second preview
            boolean preview = o != null && o.link != null && previews && o.type != Type.MAP_PREVIEW;
            if (o != null && (o.hasNote() || preview)) {
                noteText = o.hasNote() ? o.note : null;
                previewId = preview ? o.link : null;
                key = "obj|" + o.id;
            }
        }
        if (linkCursor) setCursor(Cursor.HAND);
        else refreshCursor();

        if (java.util.Objects.equals(key, hoverKey)) {
            if (key != null && key.startsWith("hint|")) hintHide.stop();
            return;
        }
        boolean wasHint = hoverKey != null && hoverKey.startsWith("hint|");
        hoverKey = key;
        hoverDelay.stop();
        hideTip();
        if (key == null) {
            if (wasHint) hintHide.playFromStart(); // leave time to move onto the hint's buttons
            return;
        }
        hintHide.stop();
        if (!hintHovered) hintPopup.hide();
        final double sx = e.getScreenX(), sy = e.getScreenY();
        final String showNote = noteText, showLink = previewId;
        final dnd.text.NameHints.Hint showHint = hint;
        final SpanHit at = sh;
        hoverDelay.setOnFinished(ev -> {
            if (showNote != null || showLink != null) {
                Node preview = ItemPreview.hover(store, showNote, showLink);
                preview.setMouseTransparent(true);
                tipPopup.getContent().setAll(preview);
                tipPopup.show(this, sx + 18, sy + 18);
            } else if (showHint != null && at != null) {
                showHintPopup(at, showHint);
            }
        });
        hoverDelay.playFromStart();
    }

    /** The small "Link / Dismiss" box offered under a card or spell name found in the text. */
    private void showHintPopup(SpanHit at, dnd.text.NameHints.Hint hint) {
        BoardObject o = at.o();
        String itemName = store.itemName(hint.itemId());
        boolean alias = itemName != null && !itemName.equalsIgnoreCase(hint.name());
        Label name = new Label(alias ? hint.name() + "  \u2192  " + itemName : hint.name()); // "Jasper -> Jasper Egan"
        name.setStyle("-fx-font-weight: bold; -fx-text-fill: -t-text;");
        Button link = new Button("Link");
        link.setTooltip(new Tooltip("Make these words a link that opens " + (itemName == null ? hint.name() : itemName)));
        link.setOnAction(ev -> applyHint(o, hint));
        Button dismiss = new Button("Dismiss");
        dismiss.setTooltip(new Tooltip("Stop underlining this name in this text"));
        dismiss.setOnAction(ev -> {
            hintPopup.hide();
            hoverKey = null;
            checkpoint();
            o.dismissedNames.add(hint.name().toLowerCase(java.util.Locale.ROOT));
            changed();
        });
        HBox row = new HBox(6, name, link, dismiss);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 8, 6, 8));
        row.setStyle("-fx-background-color: -t-surface; -fx-border-color: -t-border; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 8, 0.2, 0, 2);");
        row.setOnMouseEntered(ev -> {
            hintHovered = true;
            hintHide.stop();
        });
        row.setOnMouseExited(ev -> {
            hintHovered = false;
            hintHide.playFromStart();
        });
        hintHovered = false;
        hintPopup.getContent().setAll(row);
        javafx.geometry.Point2D p = localToScreen(board.offsetX + at.left() * zoom(), board.offsetY + at.bottom() * zoom() + 2);
        if (p != null) hintPopup.show(this, p.getX(), p.getY());
    }

    private void applyHint(BoardObject o, dnd.text.NameHints.Hint hint) {
        hintPopup.hide();
        hoverKey = null;
        checkpoint();
        int ref = o.nextRef();
        dnd.model.SpanRef sr = new dnd.model.SpanRef();
        sr.link = hint.itemId();
        o.refs.put(String.valueOf(ref), sr);
        o.text = dnd.text.RichText.setSpanRef(o.text, hint.start(), hint.end(), ref).markup();
        measure(o);
        changed();
    }

    /** Applies one toolbar setting ("color", "fill", "width", "font") to the selected objects. */
    public void applyStyle(String what) {
        if (selected.isEmpty()) return;
        checkpoint();
        for (String id : selected) {
            BoardObject o = board.find(id);
            if (o == null) continue;
            switch (what) {
                case "color" -> o.color = hex(ts.color.get());
                case "fill" -> {
                    if (o.type == Type.STICKY) o.fill = hex(ts.fill.get());
                    else if (o.type == Type.RECT || o.type == Type.ELLIPSE) o.fill = ts.useFill.get() ? hex(ts.fill.get()) : null;
                }
                case "width" -> {
                    if (o.type != Type.TEXT && o.type != Type.STICKY && o.type != Type.IMAGE
                            && o.type != Type.MAP_PREVIEW) o.strokeWidth = ts.width.get();
                }
                case "font" -> {
                    if (o.type == Type.TEXT || o.type == Type.STICKY) {
                        o.fontSize = ts.fontSize.get();
                        o.bold = ts.bold.get();
                        o.italic = ts.italic.get();
                        measure(o);
                    }
                }
                default -> { }
            }
        }
        changed();
    }

    public boolean hasSelection() { return !selected.isEmpty(); }

    /** Selects an object and scrolls the view so it is centred (used by search results & backlinks). */
    public void focusObject(String objectId) {
        BoardObject o = board.find(objectId);
        if (o == null) return;
        selected.clear();
        selected.add(o.id);
        board.offsetX = getWidth() / 2 - (o.left() + o.right()) / 2 * zoom();
        board.offsetY = getHeight() / 2 - (o.top() + o.bottom()) / 2 * zoom();
        store.markDirty(board);
        redraw();
    }

    private void error(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg);
        a.showAndWait();
    }
}
