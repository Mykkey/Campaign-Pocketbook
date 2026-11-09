package dnd.ui;

import dnd.model.BoardObject;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Popup;
import javafx.util.Duration;

/**
 * What happens when the pointer rests on words or an object on a whiteboard: the preview of a link and the text
 * of a note (a popup that follows the pointer), and the small "Link / Dismiss" box offered under a card or spell
 * name found in the text. It reads what it needs from the {@link BoardView} it belongs to.
 */
final class BoardHover {
    private final BoardView view;
    private final Popup tipPopup = new Popup();
    private final Popup hintPopup = new Popup();
    private final PauseTransition hoverDelay = new PauseTransition(Duration.millis(350));
    private final PauseTransition hintHide = new PauseTransition(Duration.millis(700));
    /** What the pointer is over (so the popup is only rebuilt when that changes), or null. */
    private String hoverKey;
    /** True while the pointer is on the hint box (its own window, so the canvas sees the pointer "leave"). */
    private boolean hintHovered;

    BoardHover(BoardView view) {
        this.view = view;
        tipPopup.setAutoFix(true);
        tipPopup.setAutoHide(false);
        hintPopup.setAutoFix(true);
        hintPopup.setAutoHide(true);
        hintHide.setOnFinished(e -> {
            if (hintHovered) return; // the pointer is on the box: leave it open
            hintPopup.hide();
            hoverKey = null;
        });
    }

    /** The pointer left the board. */
    void onExited() {
        hoverDelay.stop();
        hideTip();
        hintHide.playFromStart();
    }

    void clear() {
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

    void onMoved(MouseEvent e) {
        if (view.hoverBlocked()) {
            clear();
            return;
        }
        double px = view.wx(e.getX()), py = view.wy(e.getY());
        BoardView.SpanHit sh = view.spanAt(px, py);
        String key = null, noteText = null, previewId = null;
        dnd.text.NameHints.Hint hint = null;
        boolean linkCursor = false;
        boolean previews = view.ts.linkPreviews.get();
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
                for (dnd.text.NameHints.Hint h : view.hintsFor(sh.o())) {
                    if (sh.seg().plainStart() >= h.start() && sh.seg().plainStart() < h.end()) hint = h;
                }
                if (hint != null) key = "hint|" + sh.o().id + "|" + hint.start();
            }
        }
        if (key == null) {
            BoardObject o = view.hit(px, py);
            // a map preview already shows its map, so it needs no second preview
            boolean preview = o != null && o.link != null && previews && o.type != BoardObject.Type.MAP_PREVIEW;
            if (o != null && (o.hasNote() || preview)) {
                noteText = o.hasNote() ? o.note : null;
                previewId = preview ? o.link : null;
                key = "obj|" + o.id;
            }
        }
        if (linkCursor) view.setCursor(Cursor.HAND);
        else view.refreshCursor();

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
        final BoardView.SpanHit at = sh;
        hoverDelay.setOnFinished(ev -> {
            if (showNote != null || showLink != null) {
                Node preview = ItemPreview.hover(view.store, showNote, showLink);
                preview.setMouseTransparent(true);
                tipPopup.getContent().setAll(preview);
                tipPopup.show(view, sx + 18, sy + 18);
            } else if (showHint != null && at != null) {
                showHintPopup(at, showHint);
            }
        });
        hoverDelay.playFromStart();
    }

    /** The small "Link / Dismiss" box offered under a card or spell name found in the text. */
    private void showHintPopup(BoardView.SpanHit at, dnd.text.NameHints.Hint hint) {
        BoardObject o = at.o();
        String itemName = view.store.itemName(hint.itemId());
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
            view.checkpoint();
            o.dismissedNames.add(hint.name().toLowerCase(java.util.Locale.ROOT));
            view.changed();
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
        javafx.geometry.Point2D p = view.localToScreen(view.board.offsetX + at.left() * view.zoom(), view.board.offsetY + at.bottom() * view.zoom() + 2);
        if (p != null) hintPopup.show(view, p.getX(), p.getY());
    }

    private void applyHint(BoardObject o, dnd.text.NameHints.Hint hint) {
        hintPopup.hide();
        hoverKey = null;
        view.checkpoint();
        view.linkHint(o, hint);
        view.changed();
    }
}
