package dnd.ui;

import javafx.beans.property.BooleanProperty;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Labeled;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;

/** Makes toolbar buttons icon-only: a drawn icon, a tooltip, and the compact flat look from the theme. */
final class IconButtons {
    private IconButtons() { }

    /** Turns an existing button into an icon button (its text goes; keep the wording in the tooltip). */
    static <T extends ButtonBase> T decorate(T b, String icon) {
        b.setText(null);
        b.setGraphic(Icons.of(icon));
        if (!b.getStyleClass().contains("icon-button")) b.getStyleClass().add("icon-button");
        b.setStyle(""); // drop the old bold / size styling meant for text buttons
        b.setFocusTraversable(false);
        if (b.getTooltip() != null) speedUp(b.getTooltip()); // tooltips set before the icon was added
        return b;
    }

    static <T extends ButtonBase> T decorate(T b, String icon, String tooltip) {
        decorate(b, icon);
        b.setTooltip(tip(tooltip));
        return b;
    }

    /** A tooltip that shows quickly (about a sixth of a second), stays up while it is being read, and wraps long text. */
    static Tooltip tip(String text) {
        Tooltip t = new Tooltip(text);
        speedUp(t);
        return t;
    }

    static void speedUp(Tooltip t) {
        t.setShowDelay(javafx.util.Duration.millis(150));
        t.setShowDuration(javafx.util.Duration.seconds(30));
        t.setWrapText(true);
        t.setMaxWidth(380);
    }

    /** An icon toggle that mirrors a true/false setting (it replaces a check box). */
    static ToggleButton switchToggle(String icon, String tooltip, BooleanProperty property) {
        ToggleButton t = decorate(new ToggleButton(), icon, tooltip);
        t.selectedProperty().bindBidirectional(property);
        return t;
    }

    /** An icon toggle with no property behind it yet. */
    static ToggleButton toggle(String icon, String tooltip) {
        return decorate(new ToggleButton(), icon, tooltip);
    }

    /** For labels that sit beside a control: they carry a tooltip but no text. */
    static void quiet(Labeled l) {
        l.setVisible(false);
        l.setManaged(false);
    }
}
