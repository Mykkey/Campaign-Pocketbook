package dnd.ui;

import javafx.beans.property.BooleanProperty;
import javafx.scene.control.ButtonBase;
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
        if (b.getTooltip() != null) { // tooltips set before the icon was added
            speedUp(b.getTooltip());
            b.setAccessibleText(accessibleName(b.getTooltip().getText()));
        }
        return b;
    }

    static <T extends ButtonBase> T decorate(T b, String icon, String tooltip) {
        decorate(b, icon);
        b.setTooltip(tip(tooltip));
        b.setAccessibleText(accessibleName(tooltip));
        return b;
    }

    /** The name a screen reader should say for an icon button: the part of its tooltip before the colon ("Link: ..." becomes "Link"). */
    static String accessibleName(String tooltip) {
        if (tooltip == null) return "";
        int colon = tooltip.indexOf(':');
        String name = colon > 0 && colon <= 40 ? tooltip.substring(0, colon) : tooltip;
        int paren = name.indexOf(" (");
        if (paren > 0) name = name.substring(0, paren); // "Undo (Ctrl+Z)" becomes "Undo"
        return name.length() > 60 ? name.substring(0, 60) : name;
    }

    /** A tooltip that shows quickly (about a sixth of a second), stays up while it is being read, and wraps long text. */
    static Tooltip tip(String text) {
        Tooltip t = new Tooltip(text);
        speedUp(t);
        return t;
    }

    /** How long the pointer must rest before a tooltip shows, from the settings (milliseconds; 150 is fast, 1000 is the usual). */
    static int tooltipDelayMs() {
        return Math.max(0, Math.min(5000, AppSettings.getInt("ui.tooltipDelay", 150)));
    }

    static void speedUp(Tooltip t) {
        t.setShowDelay(javafx.util.Duration.millis(tooltipDelayMs()));
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
}
